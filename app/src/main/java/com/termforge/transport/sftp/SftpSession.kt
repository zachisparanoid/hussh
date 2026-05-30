package com.termforge.transport.sftp

import com.termforge.data.repo.KnownHostRepository
import com.termforge.transport.AuthMethod
import com.termforge.transport.ConnectionConfig
import com.termforge.transport.TransportCallbacks
import com.termforge.transport.TransportException
import com.termforge.transport.ssh.KeyTools
import com.termforge.transport.ssh.SecurityProviders
import com.termforge.transport.ssh.performTofuHostKeyCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.sftp.client.SftpClient
import org.apache.sshd.sftp.client.SftpClientFactory
import java.io.InputStream
import java.io.OutputStream
import java.time.Duration
import kotlin.coroutines.coroutineContext

/** One remote directory entry. */
data class RemoteFile(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val isSymlink: Boolean,
    val size: Long,
    val permissions: Int,
    val modifiedEpochSec: Long,
)

/**
 * An SFTP session: its own SSH connection (same TOFU host-key + auth as [SshTransport]) plus a MINA
 * SFTP client. Browse/transfer operations are suspend functions on the IO dispatcher; transfers poll
 * the coroutine for cancellation so they can be aborted from the transfer queue.
 */
class SftpSession(
    private val config: ConnectionConfig,
    private val callbacks: TransportCallbacks,
    private val knownHosts: KnownHostRepository,
    private val keyTools: KeyTools,
) {
    private var client: SshClient? = null
    private var session: ClientSession? = null
    private var sftp: SftpClient? = null

    val isConnected: Boolean get() = sftp?.isOpen == true

    suspend fun connect() = withContext(Dispatchers.IO) {
        SecurityProviders.ensureInstalled()
        val timeout = Duration.ofMillis(config.connectTimeoutMs)
        try {
            val c = SshClient.setUpDefaultClient()
            c.hostConfigEntryResolver = org.apache.sshd.client.config.hosts.HostConfigEntryResolver.EMPTY
            c.serverKeyVerifier = org.apache.sshd.client.keyverifier.ServerKeyVerifier { _, _, key ->
                runBlocking {
                    performTofuHostKeyCheck(
                        config.host, config.port, KeyUtils.getKeyType(key), key,
                        knownHosts, keyTools, callbacks,
                    )
                }
            }
            c.start()
            client = c
            val s = c.connect(config.username, config.host, config.port).verify(timeout).session
            session = s
            for (method in config.authMethods) {
                when (method) {
                    is AuthMethod.Password -> s.addPasswordIdentity(method.password)
                    is AuthMethod.PublicKey ->
                        s.addPublicKeyIdentity(keyTools.parseKeyPair(method.privateKeyPem, method.passphrase))
                    is AuthMethod.KeyboardInteractive -> Unit
                }
            }
            s.auth().verify(timeout)
            sftp = SftpClientFactory.instance().createSftpClient(s)
        } catch (e: Exception) {
            close()
            throw TransportException("SFTP connection failed: ${e.message}", e)
        }
    }

    suspend fun home(): String = withContext(Dispatchers.IO) {
        runCatching { client(); sftp!!.canonicalPath(".") }.getOrDefault("/")
    }

    suspend fun list(path: String): List<RemoteFile> = withContext(Dispatchers.IO) {
        val s = client()
        s.readDir(path).mapNotNull { entry ->
            val name = entry.filename
            if (name == "." || name == "..") return@mapNotNull null
            val attrs = entry.attributes
            RemoteFile(
                name = name,
                path = joinPath(path, name),
                isDirectory = attrs.isDirectory,
                isSymlink = attrs.isSymbolicLink,
                size = attrs.size,
                permissions = attrs.permissions,
                modifiedEpochSec = attrs.modifyTime?.to(java.util.concurrent.TimeUnit.SECONDS) ?: 0L,
            )
        }.sortedWith(compareByDescending<RemoteFile> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    suspend fun mkdir(path: String) = withContext(Dispatchers.IO) { client().mkdir(path) }
    suspend fun deleteFile(path: String) = withContext(Dispatchers.IO) { client().remove(path) }
    suspend fun deleteDir(path: String) = withContext(Dispatchers.IO) { client().rmdir(path) }
    suspend fun rename(from: String, to: String) = withContext(Dispatchers.IO) { client().rename(from, to) }

    suspend fun chmod(path: String, mode: Int) = withContext(Dispatchers.IO) {
        val s = client()
        s.setStat(path, s.stat(path).also { it.permissions = mode })
    }

    /** Download [remotePath] to [out], reporting cumulative bytes. Cancellable. Returns total bytes. */
    suspend fun download(remotePath: String, out: OutputStream, onProgress: (Long) -> Unit): Long =
        withContext(Dispatchers.IO) {
            var total = 0L
            client().read(remotePath).use { input ->
                copyCancellable(input, out) { total = it; onProgress(it) }
            }
            total
        }

    /** Upload [input] to [remotePath], reporting cumulative bytes. Cancellable. Returns total bytes. */
    suspend fun upload(input: InputStream, remotePath: String, onProgress: (Long) -> Unit): Long =
        withContext(Dispatchers.IO) {
            var total = 0L
            client().write(remotePath).use { out ->
                copyCancellable(input, out) { total = it; onProgress(it) }
            }
            total
        }

    private suspend fun copyCancellable(input: InputStream, out: OutputStream, onTotal: (Long) -> Unit) {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            coroutineContext.ensureActive() // honor transfer cancellation
            val n = input.read(buffer)
            if (n == -1) break
            out.write(buffer, 0, n)
            total += n
            onTotal(total)
        }
        out.flush()
    }

    fun close() {
        runCatching { sftp?.close() }
        runCatching { session?.close(false) }
        runCatching { client?.stop() }
        sftp = null; session = null; client = null
    }

    private fun client(): SftpClient = sftp ?: throw TransportException("SFTP session not connected")

    private fun joinPath(dir: String, name: String): String =
        if (dir.endsWith("/")) "$dir$name" else "$dir/$name"
}

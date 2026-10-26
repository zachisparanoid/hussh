package com.hussh.transport.ssh

import com.hussh.data.db.Protocol
import com.hussh.data.repo.KnownHostRepository
import com.hussh.transport.AuthMethod
import com.hussh.transport.ConnectionConfig
import com.hussh.transport.KbPrompt
import com.hussh.transport.TerminalSize
import com.hussh.transport.Transport
import com.hussh.transport.TransportCallbacks
import com.hussh.transport.TransportEvent
import com.hussh.transport.TransportException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.auth.keyboard.UserInteraction
import org.apache.sshd.client.channel.ChannelShell
import org.apache.sshd.client.channel.ClientChannelEvent
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.core.CoreModuleProperties
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.time.Duration
import java.util.EnumSet
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SSH connection via Apache MINA SSHD. Supports password, public-key, and keyboard-interactive auth;
 * Trust-On-First-Use host-key verification with loud mismatch handling; a PTY shell channel that
 * forwards window-change on resize; and heartbeat-based keep-alive.
 */
class SshTransport(
    private val config: ConnectionConfig,
    private val callbacks: TransportCallbacks,
    private val knownHosts: KnownHostRepository,
    private val keyTools: KeyTools,
) : Transport {

    override val protocol: Protocol = Protocol.SSH

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inboundChannel = Channel<ByteArray>(capacity = Channel.UNLIMITED)
    private val outboundChannel = Channel<ByteArray>(capacity = Channel.UNLIMITED)
    private val eventsFlow = MutableSharedFlow<TransportEvent>(replay = 1, extraBufferCapacity = 16)
    private val connected = AtomicBoolean(false)

    private var client: SshClient? = null
    private var session: ClientSession? = null
    private var channel: ChannelShell? = null
    @Volatile private var invertedIn: OutputStream? = null

    override val inbound: Flow<ByteArray> = inboundChannel.receiveAsFlow()
    override val events: Flow<TransportEvent> = eventsFlow
    override val isConnected: Boolean get() = connected.get()

    override suspend fun connect() {
        SecurityProviders.ensureInstalled()
        val timeout = Duration.ofMillis(config.connectTimeoutMs)
        try {
            withContext(Dispatchers.IO) {
                val sshClient = SshClient.setUpDefaultClient()
                // Hussh manages identities + known-hosts in its DB; don't read a desktop ~/.ssh.
                sshClient.hostConfigEntryResolver =
                    org.apache.sshd.client.config.hosts.HostConfigEntryResolver.EMPTY
                sshClient.serverKeyVerifier = org.apache.sshd.client.keyverifier.ServerKeyVerifier {
                        _, _, serverKey ->
                    runBlocking { verifyServerKey(KeyUtils.getKeyType(serverKey), serverKey) }
                }
                sshClient.start()
                client = sshClient

                val sess = sshClient.connect(config.username, config.host, config.port)
                    .verify(timeout).session
                session = sess

                if (config.keepAliveSec > 0) {
                    CoreModuleProperties.HEARTBEAT_INTERVAL.set(
                        sess, Duration.ofSeconds(config.keepAliveSec.toLong()),
                    )
                }
                applyAuth(sess)
                sess.auth().verify(timeout)

                openShell(sess)
                applyPortForwards(sess)
            }
            connected.set(true)
            startWriter()
            eventsFlow.tryEmit(TransportEvent.Connected)
        } catch (e: TransportException) {
            cleanup(); throw e
        } catch (e: Exception) {
            cleanup()
            throw TransportException(mapError(e), e)
        }
    }

    private fun applyAuth(sess: ClientSession) {
        for (method in config.authMethods) {
            when (method) {
                is AuthMethod.Password -> sess.addPasswordIdentity(method.password)
                is AuthMethod.PublicKey -> {
                    val pair = keyTools.parseKeyPair(method.privateKeyPem, method.passphrase)
                    sess.addPublicKeyIdentity(pair)
                }
                is AuthMethod.KeyboardInteractive -> sess.userInteraction = kbInteraction()
            }
        }
        // Always allow keyboard-interactive fallback if the server insists on it.
        if (sess.userInteraction == null) sess.userInteraction = kbInteraction()
    }

    private fun kbInteraction() = object : UserInteraction {
        override fun interactive(
            session: ClientSession, name: String, instruction: String, lang: String,
            prompt: Array<out String>, echo: BooleanArray,
        ): Array<String> {
            val prompts = prompt.mapIndexed { i, p -> KbPrompt(p, echo.getOrElse(i) { true }) }
            val answers = runBlocking {
                callbacks.answerKeyboardInteractive(name, instruction, prompts)
            }
            return answers?.toTypedArray() ?: emptyArray()
        }

        override fun getUpdatedPassword(session: ClientSession, prompt: String, lang: String): String? = null
    }

    private fun openShell(sess: ClientSession) {
        val ch = sess.createShellChannel()
        ch.setPtyType(config.termType)
        ch.setPtyColumns(config.initialSize.cols)
        ch.setPtyLines(config.initialSize.rows)
        ch.setPtyWidth(config.initialSize.widthPx)
        ch.setPtyHeight(config.initialSize.heightPx)
        ch.setEnv("TERM", config.termType)

        val sink = object : OutputStream() {
            override fun write(b: Int) = inboundChannel.trySend(byteArrayOf(b.toByte())).let {}
            override fun write(b: ByteArray, off: Int, len: Int) {
                if (len > 0) inboundChannel.trySend(b.copyOfRange(off, off + len))
            }
        }
        ch.out = sink
        ch.err = sink
        ch.open().verify(Duration.ofMillis(config.connectTimeoutMs))
        invertedIn = ch.invertedIn
        channel = ch

        ch.addCloseFutureListener { finish("Channel closed", null) }

        config.startupCommand?.takeIf { it.isNotBlank() }?.let { cmd ->
            invertedIn?.apply { write((cmd + "\n").toByteArray()); flush() }
        }
    }

    private fun applyPortForwards(sess: ClientSession) {
        for (forward in config.portForwards) {
            runCatching {
                val bind = org.apache.sshd.common.util.net.SshdSocketAddress(forward.bindAddr, forward.bindPort)
                when (forward.kind) {
                    com.hussh.transport.ForwardKind.LOCAL ->
                        sess.startLocalPortForwarding(bind, org.apache.sshd.common.util.net.SshdSocketAddress(forward.destHost, forward.destPort))
                    com.hussh.transport.ForwardKind.REMOTE ->
                        sess.startRemotePortForwarding(bind, org.apache.sshd.common.util.net.SshdSocketAddress(forward.destHost, forward.destPort))
                    com.hussh.transport.ForwardKind.DYNAMIC ->
                        sess.startDynamicPortForwarding(bind)
                }
            }
        }
    }

    private suspend fun verifyServerKey(keyType: String, serverKey: java.security.PublicKey): Boolean =
        performTofuHostKeyCheck(
            config.host, config.port, keyType, serverKey, knownHosts, keyTools, callbacks,
        )

    private fun startWriter() {
        scope.launch {
            try {
                for (chunk in outboundChannel) {
                    invertedIn?.apply { write(chunk); flush() }
                }
            } catch (e: Exception) {
                finish("Write failed: ${e.message}", e)
            }
        }
    }

    override fun send(data: ByteArray, offset: Int, length: Int) {
        if (connected.get()) outboundChannel.trySend(data.copyOfRange(offset, offset + length))
    }

    override fun resize(size: TerminalSize) {
        val ch = channel ?: return
        scope.launch { runCatching { ch.sendWindowChange(size.cols, size.rows) } }
    }

    override suspend fun keepAlive() { /* MINA heartbeat handles this */ }

    override fun disconnect() = finish("Disconnected", null)

    override suspend fun openAuxStream(command: String): com.hussh.transport.AuxStream? {
        val sess = session ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val ch = sess.createExecChannel(command)
                ch.open().verify(Duration.ofMillis(config.connectTimeoutMs))
                val out = ch.invertedIn
                object : com.hussh.transport.AuxStream {
                    override fun write(data: ByteArray, offset: Int, length: Int) {
                        runCatching { out.write(data, offset, length); out.flush() }
                    }
                    override fun close() {
                        runCatching { out.close() }
                        runCatching { ch.close(false) }
                    }
                }
            }.getOrNull()
        }
    }

    override suspend fun openExec(command: String): com.hussh.transport.ExecChannel? {
        val sess = session ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val ch = sess.createExecChannel(command)
                ch.open().verify(Duration.ofMillis(config.connectTimeoutMs))
                android.util.Log.i("TFAgent", "openExec ok: ${command.take(50)}")
                object : com.hussh.transport.ExecChannel {
                    // Lines from the command's stdout. For `tail -F` this never completes until the
                    // channel dies; for one-shot commands it completes at EOF.
                    override val stdout: Flow<String> = channelFlow {
                        val reader = BufferedReader(InputStreamReader(ch.invertedOut, Charsets.UTF_8))
                        try {
                            while (true) {
                                val line = reader.readLine() ?: break
                                trySend(line)
                            }
                        } catch (_: Throwable) { /* channel closed */ }
                    }.flowOn(Dispatchers.IO)

                    override suspend fun awaitExit(): Int = withContext(Dispatchers.IO) {
                        ch.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), 0L) // 0 = wait indefinitely
                        ch.exitStatus ?: -1
                    }

                    // Read-only channel (we never wrote stdin) → no pending write to fail on close.
                    override fun close() {
                        runCatching { ch.close(false) }
                    }
                }
            }.onFailure {
                android.util.Log.w("TFAgent", "openExec FAIL: ${command.take(50)} : ${it.message}")
            }.getOrNull()
        }
    }

    private fun finish(reason: String?, cause: Throwable?) {
        if (!connected.compareAndSet(true, false)) {
            cleanup(); return
        }
        eventsFlow.tryEmit(TransportEvent.Disconnected(reason, cause))
        inboundChannel.close()
        outboundChannel.close()
        cleanup()
    }

    private fun cleanup() {
        runCatching { channel?.close(false) }
        runCatching { session?.close(false) }
        runCatching { client?.stop() }
        scope.cancel()
    }

    private fun mapError(e: Exception): String {
        val msg = e.message ?: e.javaClass.simpleName
        return when {
            msg.contains("Auth", ignoreCase = true) || msg.contains("authentication", true) ->
                "Authentication failed — check your username, password, or key."
            msg.contains("timeout", true) -> "Connection timed out."
            msg.contains("refused", true) -> "Connection refused by ${config.host}:${config.port}."
            else -> "SSH error: $msg"
        }
    }
}

package com.termforge.transport

import com.termforge.data.db.Protocol
import kotlinx.coroutines.flow.Flow

/** Terminal window dimensions, in cells and (optionally) pixels for pty window-change requests. */
data class TerminalSize(val cols: Int, val rows: Int, val widthPx: Int = 0, val heightPx: Int = 0)

/** How to authenticate. Resolved (passwords/keys decrypted) before the transport is created. */
sealed interface AuthMethod {
    data class Password(val password: String) : AuthMethod
    data class PublicKey(val privateKeyPem: String, val passphrase: String?) : AuthMethod
    /** Server-driven challenge/response; answered through [TransportCallbacks]. */
    data object KeyboardInteractive : AuthMethod
}

/** An SSH port-forwarding rule to establish once the session authenticates. */
enum class ForwardKind { LOCAL, REMOTE, DYNAMIC }

data class PortForwardSpec(
    val kind: ForwardKind,
    val bindAddr: String,
    val bindPort: Int,
    val destHost: String = "",
    val destPort: Int = 0,
)

/** Everything needed to open one connection. Immutable; secrets already decrypted in memory. */
data class ConnectionConfig(
    val protocol: Protocol,
    val host: String,
    val port: Int,
    val username: String,
    val termType: String = "xterm-256color",
    val charset: String = "UTF-8",
    val initialSize: TerminalSize = TerminalSize(80, 24),
    val startupCommand: String? = null,
    val keepAliveSec: Int = 60,
    val authMethods: List<AuthMethod> = emptyList(),
    val portForwards: List<PortForwardSpec> = emptyList(),
    val connectTimeoutMs: Long = 20_000,
)

/** Whether a presented host key has been seen before. */
enum class HostKeyStatus { NEW, MATCH, MISMATCH }

data class HostKeyRequest(
    val host: String,
    val port: Int,
    val keyType: String,
    val fingerprintSha256: String,
    val fingerprintMd5: String,
    val publicKeyBase64: String,
    val status: HostKeyStatus,
    /** The previously-trusted fingerprint, present only when [status] is [HostKeyStatus.MISMATCH]. */
    val previousFingerprint: String?,
)

enum class HostKeyDecision { ACCEPT, REJECT }

data class KbPrompt(val prompt: String, val echo: Boolean)

/** Interactive callbacks the transport invokes mid-handshake. Implemented by the session/UI layer. */
interface TransportCallbacks {
    /** TOFU host-key check. ACCEPT stores/updates the trusted key; REJECT aborts the connection. */
    suspend fun verifyHostKey(request: HostKeyRequest): HostKeyDecision

    /** Answer a keyboard-interactive challenge, or null to cancel. */
    suspend fun answerKeyboardInteractive(
        name: String,
        instruction: String,
        prompts: List<KbPrompt>,
    ): List<String>?
}

/** Lifecycle/data events surfaced to the session layer. */
sealed interface TransportEvent {
    data object Connected : TransportEvent
    data class Banner(val text: String) : TransportEvent
    data class Disconnected(val reason: String?, val cause: Throwable? = null) : TransportEvent
}

class TransportException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * A best-effort auxiliary byte sink over the live connection — an extra channel independent of the
 * terminal stream. Used to stream microphone audio to a remote command (the voice feature). Writing
 * is non-blocking-ish; [close] ends the remote command.
 */
interface AuxStream {
    fun write(data: ByteArray, offset: Int, length: Int)
    fun close()
}

/**
 * Protocol-agnostic connection. One instance per live session. The session layer feeds [inbound]
 * bytes into the terminal emulator and routes terminal output to [send]. Implementations:
 * [com.termforge.transport.ssh.SshTransport], [com.termforge.transport.telnet.TelnetTransport],
 * [com.termforge.transport.RawTcpTransport].
 */
interface Transport {
    val protocol: Protocol

    /** Bytes arriving from the remote host. Completes when the connection ends. */
    val inbound: Flow<ByteArray>

    /** Lifecycle events (connected, banner, disconnected). */
    val events: Flow<TransportEvent>

    /** Perform the handshake + authentication. Suspends until ready; throws [TransportException] on failure. */
    suspend fun connect()

    /** Send terminal output (keystrokes, paste, responses) to the remote host. */
    fun send(data: ByteArray, offset: Int, length: Int)

    /** Forward a terminal resize to the remote (SSH window-change / Telnet NAWS). No-op for raw TCP. */
    fun resize(size: TerminalSize)

    /** Send a protocol keep-alive (SSH global request / Telnet NOP). */
    suspend fun keepAlive()

    /** Whether the connection is currently up. */
    val isConnected: Boolean

    /** Tear down the connection and release resources. */
    fun disconnect()

    /**
     * Open an auxiliary channel running [command] on the remote host, returning a sink for the
     * command's stdin. Only SSH supports this; other transports return null.
     */
    suspend fun openAuxStream(command: String): AuxStream? = null
}

fun Transport.send(data: ByteArray) = send(data, 0, data.size)

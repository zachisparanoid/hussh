package com.termforge.session

import com.termforge.data.db.Protocol
import com.termforge.transport.ConnectionConfig
import com.termforge.transport.TerminalSize
import com.termforge.transport.Transport
import com.termforge.transport.TransportCallbacks
import com.termforge.transport.TransportEvent
import com.termforge.transport.TransportException
import com.termforge.transport.TransportFactory
import com.termux.terminal.TerminalNetworkClient
import com.termux.terminal.TerminalSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SessionStatus { CONNECTING, CONNECTED, RECONNECTING, DISCONNECTED, FAILED }

/**
 * One live terminal session: a Termux [TerminalSession] in network mode bridged to a [Transport].
 * Inbound bytes feed the emulator; the emulator's output and resizes go to the transport. Survives in
 * the background (owned by the service's scope, not Compose), and auto-reconnects with exponential
 * backoff when configured.
 */
class TerminalConnection(
    val sessionId: String,
    val hostId: Long,
    val title: String,
    val protocol: Protocol,
    baseConfig: ConnectionConfig,
    scrollbackLines: Int,
    private val transportFactory: TransportFactory,
    private val callbacks: TransportCallbacks,
    private val autoReconnect: Boolean,
    private val onTerminated: (String) -> Unit = {},
    /** Invoked (with this sessionId) when the agent-attention marker is seen in the inbound stream. */
    private val onAttention: (String) -> Unit = {},
    /** Gates scanning: when false, inbound bytes skip the scanner entirely (zero hot-path cost). */
    private val attentionEnabled: () -> Boolean = { false },
) {
    // Safety net: a transport-layer failure must degrade to a failed session, never crash the app.
    private val crashGuard = CoroutineExceptionHandler { _, _ -> _status.value = SessionStatus.FAILED }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + crashGuard)
    private val _status = MutableStateFlow(SessionStatus.CONNECTING)
    val status = _status.asStateFlow()

    /** Strips the agent-attention marker from inbound bytes and flags when one is seen. */
    private val attentionScanner = AttentionMarkerScanner()

    @Volatile var sessionClient: BaseTerminalSessionClient = BaseTerminalSessionClient()
        private set

    @Volatile private var size: TerminalSize = baseConfig.initialSize
    @Volatile private var transport: Transport? = null
    @Volatile private var baseConfig: ConnectionConfig = baseConfig

    private val networkClient = object : TerminalNetworkClient {
        override fun onTerminalOutput(data: ByteArray, offset: Int, count: Int) {
            transport?.send(data, offset, count)
        }
        override fun onTerminalSizeChanged(columns: Int, rows: Int, cellWidthPixels: Int, cellHeightPixels: Int) {
            size = TerminalSize(columns, rows, cellWidthPixels, cellHeightPixels)
            transport?.resize(size)
        }
        override fun onTerminalClosed() = disconnect()
    }

    val terminalSession: TerminalSession =
        TerminalSession(sessionClient, scrollbackLines, networkClient)

    fun start() {
        scope.launch { runLoop() }
    }

    private suspend fun runLoop() {
        var attempt = 0
        while (scope.isActive) {
            val reconnect = runOnce(attempt)
            if (!reconnect) break
            attempt++
            _status.value = SessionStatus.RECONNECTING
            val backoff = (1000L shl attempt.coerceAtMost(5)).coerceAtMost(30_000L)
            postSystem("\r\n[Reconnecting in ${backoff / 1000}s…]\r\n")
            delay(backoff)
        }
        // The session ended on its own (clean exit, or a drop with no reconnect). Reflect that in
        // the status AND notify the owner so it's pruned from the active-sessions list — otherwise
        // the list keeps showing a dead session as "connected".
        if (_status.value != SessionStatus.FAILED) _status.value = SessionStatus.DISCONNECTED
        onTerminated(sessionId)
    }

    /** One connect→serve→disconnect cycle. Returns true if the loop should reconnect. */
    private suspend fun runOnce(attempt: Int): Boolean {
        val t = transportFactory.create(baseConfig.copy(initialSize = size), callbacks)
        transport = t
        terminalSession.setNetworkClient(networkClient)
        val closed = CompletableDeferred<TransportEvent.Disconnected>()

        val inJob = scope.launch {
            t.inbound.collect { bytes ->
                if (attentionEnabled()) {
                    val r = attentionScanner.scan(bytes)
                    terminalSession.onNetworkInput(r.cleaned, 0, r.cleaned.size)
                    if (r.detected) onAttention(sessionId)
                } else {
                    terminalSession.onNetworkInput(bytes, 0, bytes.size)
                }
            }
        }
        val evJob = scope.launch {
            t.events.collect { event ->
                when (event) {
                    TransportEvent.Connected -> _status.value = SessionStatus.CONNECTED
                    is TransportEvent.Banner -> postSystem(event.text)
                    is TransportEvent.Disconnected -> if (!closed.isCompleted) closed.complete(event)
                }
            }
        }
        return try {
            t.connect()
            val end = closed.await()
            inJob.cancel(); evJob.cancel()
            postSystem("\r\n[Disconnected${end.reason?.let { ": $it" } ?: ""}]\r\n")
            // Reconnect only on an unexpected drop (a cause), never on a clean user disconnect.
            autoReconnect && end.cause != null
        } catch (e: CancellationException) {
            inJob.cancel(); evJob.cancel()
            throw e // preserve coroutine cancellation
        } catch (e: Throwable) {
            // Catch Throwable (not just Exception): some failures — e.g. a library class-init
            // ExceptionInInitializerError — are Errors, and must not crash the app.
            inJob.cancel(); evJob.cancel()
            _status.value = SessionStatus.FAILED
            val message = (e as? TransportException)?.message
                ?: "Connection error: ${e.message ?: e.javaClass.simpleName}"
            postSystem("\r\n[$message]\r\n")
            autoReconnect
        }
    }

    /** Swap the Termux client (terminal screen attaches its own; restores base on detach). */
    fun attachClient(client: BaseTerminalSessionClient) {
        sessionClient = client
        terminalSession.updateTerminalSessionClient(client)
    }

    fun detachClient() {
        val base = BaseTerminalSessionClient()
        sessionClient = base
        terminalSession.updateTerminalSessionClient(base)
    }

    fun write(data: ByteArray) = transport?.send(data, 0, data.size)

    /** Open an auxiliary SSH channel (e.g. to stream microphone audio). Null if not an SSH session. */
    suspend fun openAuxStream(command: String) = transport?.openAuxStream(command)

    fun disconnect() {
        _status.value = SessionStatus.DISCONNECTED
        runCatching { transport?.disconnect() }
        runCatching { terminalSession.finishIfRunning() }
        scope.cancel()
    }

    val isLive: Boolean get() = _status.value != SessionStatus.DISCONNECTED

    private suspend fun postSystem(text: String) = withContext(Dispatchers.Main) {
        terminalSession.appendSystemMessage(text)
    }
}

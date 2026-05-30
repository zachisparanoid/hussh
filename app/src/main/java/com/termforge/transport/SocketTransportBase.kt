package com.termforge.transport

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Shared plumbing for the plain-socket transports (raw TCP and Telnet): a blocking [Socket] on
 * [Dispatchers.IO] with a reader coroutine that suspends-sends inbound bytes (natural backpressure)
 * and a writer coroutine that drains outbound bytes in order. Subclasses customize inbound handling
 * and outbound transformation.
 */
abstract class SocketTransportBase : Transport {

    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inboundChannel = Channel<ByteArray>(capacity = 1024)
    private val outboundChannel = Channel<ByteArray>(capacity = Channel.UNLIMITED)
    private val eventsFlow = MutableSharedFlow<TransportEvent>(replay = 1, extraBufferCapacity = 16)
    private val connected = AtomicBoolean(false)

    @Volatile protected var socket: Socket? = null

    final override val inbound: Flow<ByteArray> = inboundChannel.receiveAsFlow()
    final override val events: Flow<TransportEvent> = eventsFlow
    final override val isConnected: Boolean get() = connected.get()

    /** Called right after the TCP connection opens, before the reader starts. Send initial bytes here. */
    protected open suspend fun afterConnect() {}

    /** Process raw bytes read from the socket. Default forwards them all to [emitInbound]. */
    protected open suspend fun handleInbound(buffer: ByteArray, length: Int) {
        emitInbound(buffer.copyOf(length))
    }

    /** Transform user terminal output before it hits the wire (e.g. Telnet IAC escaping). */
    protected open fun transformOutbound(data: ByteArray): ByteArray = data

    protected suspend fun openSocket(host: String, port: Int, timeoutMs: Long) {
        withContext(Dispatchers.IO) {
            val s = Socket()
            s.tcpNoDelay = true
            s.connect(InetSocketAddress(host, port), timeoutMs.toInt())
            socket = s
            connected.set(true)
        }
        afterConnect()
        eventsFlow.tryEmit(TransportEvent.Connected)
        startLoops()
    }

    private fun startLoops() {
        val s = socket ?: return
        scope.launch {
            val input = s.getInputStream()
            val buf = ByteArray(8192)
            try {
                while (isActive) {
                    val n = input.read(buf)
                    if (n == -1) break
                    if (n > 0) handleInbound(buf, n)
                }
                finish("Connection closed by remote host", null)
            } catch (e: Exception) {
                finish("Connection lost: ${e.message}", e)
            }
        }
        scope.launch {
            val output = s.getOutputStream()
            try {
                for (chunk in outboundChannel) {
                    output.write(chunk)
                    output.flush()
                }
            } catch (e: Exception) {
                finish("Write failed: ${e.message}", e)
            }
        }
    }

    /** Push bytes to consumers of [inbound]. */
    protected suspend fun emitInbound(bytes: ByteArray) {
        if (bytes.isNotEmpty()) inboundChannel.send(bytes)
    }

    /** Enqueue bytes to the wire verbatim (used by protocol negotiation that already framed IAC). */
    protected fun enqueueRaw(bytes: ByteArray) {
        outboundChannel.trySend(bytes)
    }

    override fun send(data: ByteArray, offset: Int, length: Int) {
        if (!connected.get()) return
        enqueueRaw(transformOutbound(data.copyOfRange(offset, offset + length)))
    }

    override fun resize(size: TerminalSize) { /* default: no-op (raw TCP) */ }

    override suspend fun keepAlive() { /* default: nothing for raw TCP */ }

    override fun disconnect() {
        finish("Disconnected", null)
    }

    private fun finish(reason: String?, cause: Throwable?) {
        if (!connected.compareAndSet(true, false)) return
        eventsFlow.tryEmit(TransportEvent.Disconnected(reason, cause))
        inboundChannel.close()
        outboundChannel.close()
        runCatching { socket?.close() }
        scope.cancel()
    }
}

package com.termforge.transport.telnet

import com.termforge.data.db.Protocol
import com.termforge.transport.ConnectionConfig
import com.termforge.transport.SocketTransportBase
import com.termforge.transport.TerminalSize
import com.termforge.transport.TransportException

/**
 * Telnet over TCP. Inbound bytes flow through [TelnetNegotiator] (which strips/answers IAC
 * sequences); outbound user bytes get their 0xFF doubled. A non-negotiating server just streams
 * data, which falls straight through — graceful degradation to a dumb line/char terminal.
 */
class TelnetTransport(private val config: ConnectionConfig) : SocketTransportBase() {
    override val protocol: Protocol = Protocol.TELNET

    private val negotiator = TelnetNegotiator(
        termType = config.termType,
        cols = config.initialSize.cols,
        rows = config.initialSize.rows,
    )

    override suspend fun connect() {
        try {
            openSocket(config.host, config.port, config.connectTimeoutMs)
        } catch (e: Exception) {
            throw TransportException("Could not connect to ${config.host}:${config.port}: ${e.message}", e)
        }
    }

    override suspend fun afterConnect() {
        enqueueRaw(negotiator.initialNegotiation())
    }

    override suspend fun handleInbound(buffer: ByteArray, length: Int) {
        val result = negotiator.process(buffer, length)
        if (result.response.isNotEmpty()) enqueueRaw(result.response)
        emitInbound(result.data)
    }

    /** Double every 0xFF so it isn't mistaken for an IAC command. */
    override fun transformOutbound(data: ByteArray): ByteArray {
        if (data.none { it.toInt() and 0xFF == 0xFF }) return data
        val out = ArrayList<Byte>(data.size + 4)
        for (b in data) {
            out.add(b)
            if (b.toInt() and 0xFF == 0xFF) out.add(0xFF.toByte())
        }
        return out.toByteArray()
    }

    override fun resize(size: TerminalSize) {
        if (isConnected) enqueueRaw(negotiator.nawsUpdate(size.cols, size.rows))
    }
}

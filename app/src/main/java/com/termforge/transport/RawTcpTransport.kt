package com.termforge.transport

import com.termforge.data.db.Protocol

/**
 * A plain TCP socket piped straight to the terminal — the "raw" connection type. No protocol framing,
 * no option negotiation: bytes in, bytes out.
 *
 * NOTE: this is NOT the Mosh protocol (which is UDP + SSP + roaming). It is the raw-socket connection
 * a power user reaches for when talking to a serial bridge, a custom daemon, or a text service.
 */
class RawTcpTransport(private val config: ConnectionConfig) : SocketTransportBase() {
    override val protocol: Protocol = Protocol.RAW

    override suspend fun connect() {
        try {
            openSocket(config.host, config.port, config.connectTimeoutMs)
        } catch (e: Exception) {
            throw TransportException("Could not connect to ${config.host}:${config.port}: ${e.message}", e)
        }
    }
}

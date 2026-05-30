package com.termforge.transport

import com.termforge.data.db.Protocol
import com.termforge.data.repo.KnownHostRepository
import com.termforge.transport.sftp.SftpSession
import com.termforge.transport.ssh.KeyTools
import com.termforge.transport.ssh.SshTransport
import com.termforge.transport.telnet.TelnetTransport
import javax.inject.Inject
import javax.inject.Singleton

/** Builds the right [Transport] (or [SftpSession]) for a connection. */
@Singleton
class TransportFactory @Inject constructor(
    private val knownHosts: KnownHostRepository,
    private val keyTools: KeyTools,
) {
    /** Create an interactive terminal transport. SFTP hosts open an SSH shell here too. */
    fun create(config: ConnectionConfig, callbacks: TransportCallbacks): Transport =
        when (config.protocol) {
            Protocol.SSH, Protocol.SFTP -> SshTransport(config, callbacks, knownHosts, keyTools)
            Protocol.TELNET -> TelnetTransport(config)
            Protocol.RAW -> RawTcpTransport(config)
        }

    /** Create an SFTP session for file browsing/transfer. */
    fun createSftp(config: ConnectionConfig, callbacks: TransportCallbacks): SftpSession =
        SftpSession(config, callbacks, knownHosts, keyTools)
}

package com.termforge.transport.ssh

import com.termforge.data.db.entities.KnownHostEntity
import com.termforge.data.repo.KnownHostRepository
import com.termforge.transport.HostKeyDecision
import com.termforge.transport.HostKeyRequest
import com.termforge.transport.HostKeyStatus
import com.termforge.transport.TransportCallbacks
import org.apache.sshd.common.config.keys.PublicKeyEntry
import java.security.PublicKey

/**
 * Shared Trust-On-First-Use host-key check used by both [SshTransport] and the SFTP session. A
 * matching known key is accepted silently; a new or mismatched key prompts the user through
 * [callbacks] and, on acceptance, is stored/updated. Never silently accepts an unknown/changed key.
 */
suspend fun performTofuHostKeyCheck(
    host: String,
    port: Int,
    keyType: String,
    serverKey: PublicKey,
    knownHosts: KnownHostRepository,
    keyTools: KeyTools,
    callbacks: TransportCallbacks,
): Boolean {
    val fp256 = keyTools.fingerprintSha256(serverKey)
    val fpMd5 = keyTools.fingerprintMd5(serverKey)
    val existing = knownHosts.find(host, port, keyType)
    val status = when {
        existing == null -> HostKeyStatus.NEW
        existing.fingerprint == fp256 -> HostKeyStatus.MATCH
        else -> HostKeyStatus.MISMATCH
    }
    if (status == HostKeyStatus.MATCH) return true

    val request = HostKeyRequest(
        host = host, port = port, keyType = keyType,
        fingerprintSha256 = fp256, fingerprintMd5 = fpMd5,
        publicKeyBase64 = PublicKeyEntry.toString(serverKey),
        status = status, previousFingerprint = existing?.fingerprint,
    )
    if (callbacks.verifyHostKey(request) != HostKeyDecision.ACCEPT) return false

    knownHosts.trust(
        KnownHostEntity(
            host = host, port = port, keyType = keyType, fingerprint = fp256,
            publicKeyBase64 = request.publicKeyBase64, addedAt = System.currentTimeMillis(),
        ),
    )
    return true
}

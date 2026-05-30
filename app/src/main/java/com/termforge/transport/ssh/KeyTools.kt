package com.termforge.transport.ssh

import com.termforge.data.db.KeyType
import org.apache.sshd.common.NamedResource
import org.apache.sshd.common.config.keys.FilePasswordProvider
import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.common.config.keys.PublicKeyEntry
import org.apache.sshd.common.digest.BuiltinDigests
import org.apache.sshd.common.util.security.SecurityUtils
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.openssl.jcajce.JcaPKCS8Generator
import java.io.StringWriter
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Result of generating or importing a key: everything an [IdentityEntity] needs. */
data class KeyMaterial(
    val keyType: KeyType,
    val privateKeyPem: String,
    val publicKeyOpenSsh: String,
    val fingerprintSha256: String,
    val fingerprintMd5: String,
)

/**
 * Key generation, import parsing, and fingerprinting. Uses MINA SSHD's key utilities for the
 * SSH-native formats (OpenSSH public keys, SHA256/MD5 fingerprints, multi-format private-key loading)
 * and BouncyCastle for PKCS#8 serialization of generated keys.
 */
@Singleton
class KeyTools @Inject constructor() {

    fun generate(type: KeyType, comment: String, rsaBits: Int = 4096): KeyMaterial {
        SecurityProviders.ensureInstalled()
        val pair = when (type) {
            KeyType.ED25519 -> KeyPairGenerator.getInstance("Ed25519").genKeyPair()
            KeyType.RSA -> KeyPairGenerator.getInstance("RSA").apply { initialize(rsaBits) }.genKeyPair()
            KeyType.ECDSA -> KeyPairGenerator.getInstance("EC")
                .apply { initialize(ECGenParameterSpec("secp256r1")) }.genKeyPair()
        }
        return materialOf(type, pair, comment, toPkcs8Pem(pair))
    }

    /**
     * Validate an imported private key (PEM / PKCS#8 / OpenSSH), deriving its public key and
     * fingerprints. The original [pem] text is preserved verbatim so the user's exact key (and its
     * own passphrase encryption) is kept. Throws if the key is unparseable or the passphrase is wrong.
     */
    fun import(pem: String, passphrase: String?, comment: String): KeyMaterial {
        val pair = parseKeyPair(pem, passphrase)
        val type = detectType(pair.public)
        return materialOf(type, pair, comment, pem)
    }

    /** Parse a private key into a [KeyPair] for authentication. */
    fun parseKeyPair(pem: String, passphrase: String?): KeyPair {
        SecurityProviders.ensureInstalled()
        val loader = SecurityUtils.getKeyPairResourceParser()
        val pwProvider = FilePasswordProvider { _, _, _ -> passphrase ?: "" }
        val pairs = loader.loadKeyPairs(null, NamedResource.ofName("identity"), pwProvider, pem)
        return pairs.firstOrNull()
            ?: throw IllegalArgumentException("No key pair found in the supplied data")
    }

    fun publicKeyToOpenSsh(publicKey: PublicKey, comment: String): String {
        val base = PublicKeyEntry.toString(publicKey)
        return if (comment.isBlank()) base else "$base $comment"
    }

    fun fingerprintSha256(publicKey: PublicKey): String =
        KeyUtils.getFingerPrint(BuiltinDigests.sha256, publicKey)

    fun fingerprintMd5(publicKey: PublicKey): String =
        KeyUtils.getFingerPrint(BuiltinDigests.md5, publicKey)

    private fun materialOf(type: KeyType, pair: KeyPair, comment: String, pem: String) = KeyMaterial(
        keyType = type,
        privateKeyPem = pem,
        publicKeyOpenSsh = publicKeyToOpenSsh(pair.public, comment),
        fingerprintSha256 = fingerprintSha256(pair.public),
        fingerprintMd5 = fingerprintMd5(pair.public),
    )

    private fun detectType(publicKey: PublicKey): KeyType = when {
        publicKey.algorithm.contains("Ed", ignoreCase = true) -> KeyType.ED25519
        publicKey.algorithm.equals("RSA", ignoreCase = true) -> KeyType.RSA
        else -> KeyType.ECDSA
    }

    private fun toPkcs8Pem(pair: KeyPair): String {
        val writer = StringWriter()
        JcaPEMWriter(writer).use { it.writeObject(JcaPKCS8Generator(pair.private, null)) }
        return writer.toString()
    }
}

package com.termforge.data.crypto

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject

/**
 * Encrypts/decrypts the database's `*Enc` columns (private keys, passphrases, saved passwords) with
 * AES-256-GCM under a master key from [keyProvider]. Output layout is `[12-byte IV | ciphertext+tag]`;
 * a fresh random IV per call makes encryption non-deterministic. GCM provides authentication, so a
 * tampered blob fails decryption rather than yielding garbage.
 */
class CredentialCrypto @Inject constructor(
    private val keyProvider: MasterKeyProvider,
) {
    fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // Do NOT supply an IV here: Android Keystore GCM keys mandate that the keystore generate the
        // IV itself (a caller-provided IV throws InvalidAlgorithmParameterException). We read the
        // generated 12-byte IV back out and prepend it. This also works for plain JCE keys.
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider.masterKey())
        val iv = cipher.iv
        return iv + cipher.doFinal(plaintext)
    }

    fun decrypt(blob: ByteArray): ByteArray {
        require(blob.size > IV_BYTES) { "Ciphertext blob too short" }
        val iv = blob.copyOfRange(0, IV_BYTES)
        val ciphertext = blob.copyOfRange(IV_BYTES, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, keyProvider.masterKey(), GCMParameterSpec(TAG_BITS, iv))
        }
        return cipher.doFinal(ciphertext)
    }

    fun encryptString(value: String): String =
        Base64.getEncoder().encodeToString(encrypt(value.toByteArray(Charsets.UTF_8)))

    fun decryptString(value: String): String =
        String(decrypt(Base64.getDecoder().decode(value)), Charsets.UTF_8)

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12      // 96-bit nonce — the recommended size for GCM
        const val TAG_BITS = 128
    }
}

package com.hussh.data.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Exercises the AES-GCM/IV/base64 logic with an in-memory key (no Android Keystore), so it runs as
 * a plain JVM test. The Keystore-backed key source is a separate, device-only concern.
 */
class CredentialCryptoTest {

    private fun newCrypto(): CredentialCrypto {
        val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        return CredentialCrypto(object : MasterKeyProvider {
            override fun masterKey(): SecretKey = key
        })
    }

    @Test
    fun `string round-trips through encrypt then decrypt`() {
        val crypto = newCrypto()
        val secret = "hunter2 🔐 пароль"
        val encrypted = crypto.encryptString(secret)
        assertEquals(secret, crypto.decryptString(encrypted))
    }

    @Test
    fun `same plaintext yields different ciphertext each time (random IV)`() {
        val crypto = newCrypto()
        val secret = "same-input"
        val a = crypto.encryptString(secret)
        val b = crypto.encryptString(secret)
        assertNotEquals(secret, a)
        assertNotEquals(a, b)
        assertEquals(secret, crypto.decryptString(a))
        assertEquals(secret, crypto.decryptString(b))
    }

    @Test
    fun `byte round-trip preserves exact bytes including binary key material`() {
        val crypto = newCrypto()
        val data = ByteArray(257) { it.toByte() }
        assertArrayEquals(data, crypto.decrypt(crypto.encrypt(data)))
    }
}

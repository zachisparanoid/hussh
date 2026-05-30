package com.termforge.data.crypto

import javax.crypto.SecretKey

/**
 * Supplies the symmetric master key used by [CredentialCrypto]. Abstracted so the encryption logic
 * can be unit-tested with an in-memory key, while production uses an Android Keystore-backed key.
 */
interface MasterKeyProvider {
    fun masterKey(): SecretKey
}

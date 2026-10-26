package com.hussh.data.crypto

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides the SQLCipher passphrase used to encrypt the whole database file. A 32-byte random
 * passphrase is generated once, wrapped with the Keystore master key via [CredentialCrypto], and the
 * wrapped form persisted in plain SharedPreferences — safe because the wrapping key is hardware-backed.
 */
@Singleton
class DatabasePassphraseProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val crypto: CredentialCrypto,
) {
    /** Returns the raw passphrase bytes SQLCipher expects. Stable across launches. */
    fun passphrase(): ByteArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_WRAPPED, null)?.let { wrapped ->
            return crypto.decrypt(Base64.getDecoder().decode(wrapped))
        }
        val fresh = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val wrapped = Base64.getEncoder().encodeToString(crypto.encrypt(fresh))
        prefs.edit().putString(KEY_WRAPPED, wrapped).apply()
        return fresh
    }

    private companion object {
        const val PREFS = "hussh_db_secure"
        const val KEY_WRAPPED = "db_passphrase_wrapped"
    }
}

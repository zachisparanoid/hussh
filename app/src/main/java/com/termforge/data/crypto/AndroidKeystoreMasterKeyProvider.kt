package com.termforge.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [MasterKeyProvider] backed by the Android Keystore. The AES-256 key is generated once
 * and never leaves the hardware-backed keystore (TEE/StrongBox where available).
 *
 * Deliberately NOT requiring per-use user authentication: the background [SessionService] must be
 * able to decrypt saved credentials to auto-reconnect while the screen is locked. The optional
 * biometric "app lock" gates the UI layer instead (see settings).
 */
@Singleton
class AndroidKeystoreMasterKeyProvider @Inject constructor() : MasterKeyProvider {

    override fun masterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return generateKey()
    }

    private fun generateKey(): SecretKey {
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "termforge_master_key_v1"
    }
}

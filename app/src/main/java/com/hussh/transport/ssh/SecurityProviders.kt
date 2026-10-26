package com.hussh.transport.ssh

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

/**
 * Installs the full BouncyCastle JCE provider, replacing Android's stripped-down stock `"BC"`
 * (which lacks Ed25519 and ciphers MINA SSHD relies on). Idempotent and safe to call from any
 * connection setup path. Android components use their own BC by class name, not the `"BC"` provider,
 * so removing the stock provider does not affect the platform.
 */
object SecurityProviders {

    @Volatile private var installed = false

    fun ensureInstalled() {
        if (installed) return
        synchronized(this) {
            if (installed) return
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)
                    ?.javaClass != BouncyCastleProvider::class.java
            ) {
                Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
                Security.insertProviderAt(BouncyCastleProvider(), 1)
            }
            installed = true
        }
    }
}

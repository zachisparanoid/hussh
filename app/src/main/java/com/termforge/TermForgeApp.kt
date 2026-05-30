package com.termforge

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. [HiltAndroidApp] triggers Hilt's code generation and creates the
 * application-level dependency container that every other component is rooted in.
 */
@HiltAndroidApp
class TermForgeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        prepareMinaEnvironment()
    }

    /**
     * Apache MINA SSHD statically initializes a default `~/.ssh/config` resolver that reads the
     * `user.home` system property. Android does not set `user.home`, so MINA throws
     * `IllegalArgumentException: No user home` from a class initializer (surfacing as
     * `ExceptionInInitializerError`) the first time `SshClient.setUpDefaultClient()` runs.
     *
     * We point these properties at the app's private storage *before any MINA class loads* so those
     * initializers succeed. MINA then simply finds no config/keys there, which is exactly what we
     * want — TermForge manages its own identities and known-hosts in its encrypted database.
     */
    private fun prepareMinaEnvironment() {
        if (System.getProperty("user.home").isNullOrEmpty()) {
            System.setProperty("user.home", filesDir.absolutePath)
        }
        if (System.getProperty("user.name").isNullOrEmpty()) {
            System.setProperty("user.name", "termforge")
        }
    }
}

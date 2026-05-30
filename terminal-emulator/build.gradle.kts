plugins {
    alias(libs.plugins.android.library)
}

// Vendored from github.com/termux/termux-app (Apache-2.0). The native PTY layer (jni/, JNI.so)
// has been removed: TermForge drives the emulator from a network transport, never a local process.
// See TerminalSession's "network mode" patch.
android {
    namespace = "com.termux.emulator"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    lint {
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.9.0")
    testImplementation(libs.junit)
}

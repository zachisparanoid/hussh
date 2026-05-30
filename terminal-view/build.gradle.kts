plugins {
    alias(libs.plugins.android.library)
}

// Vendored from github.com/termux/termux-app (Apache-2.0). Renders a TerminalEmulator and forwards
// touch/key input. Used inside a Compose AndroidView by TermForge's terminal screen.
android {
    namespace = "com.termux.view"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.9.0")
    api(project(":terminal-emulator"))
    testImplementation(libs.junit)
}

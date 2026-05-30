package com.termforge

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.termforge.data.prefs.AppPreferences
import com.termforge.nav.TermForgeNavGraph
import com.termforge.terminal.FontSizeController
import com.termforge.ui.theme.TermForgeTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The single Activity. Hosts the Compose [TermForgeNavGraph]. Volume-key → terminal-font handling
 * is layered on in the terminal build phase via [onKeyDown]/[onKeyUp].
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var fontSizeController: FontSizeController
    @Inject lateinit var appPreferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val settings by appPreferences.settings.collectAsState(initial = com.termforge.data.prefs.AppSettings())
            val theme = if (settings.dynamicColor) com.termforge.ui.theme.AppTheme.SYSTEM_DYNAMIC else settings.appTheme
            TermForgeTheme(appTheme = theme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val navController = rememberNavController()
                    TermForgeNavGraph(navController)
                }
            }
        }
    }

    /**
     * Volume keys resize the terminal font while a terminal session is foregrounded (toggleable).
     * Consuming the events suppresses the system volume UI. Other screens see normal volume behavior.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (shouldHandleVolume(keyCode)) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> fontSizeController.increase()
                KeyEvent.KEYCODE_VOLUME_DOWN -> fontSizeController.decrease()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (shouldHandleVolume(keyCode)) return true // swallow the up event too
        return super.onKeyUp(keyCode, event)
    }

    private fun shouldHandleVolume(keyCode: Int): Boolean =
        fontSizeController.terminalForegrounded && fontSizeController.volumeKeysEnabled &&
            (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
}

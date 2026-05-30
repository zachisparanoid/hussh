package com.termforge

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.termforge.data.prefs.AppPreferences
import com.termforge.nav.Routes
import com.termforge.nav.TermForgeNavGraph
import com.termforge.session.AttentionNotifier
import com.termforge.session.SessionManager
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
    @Inject lateinit var sessionManager: SessionManager

    // Registered before STARTED (field init) so it's ready to launch from onCreate. Result ignored:
    // a denial simply means notifications stay silent until the user grants it in system settings.
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // Set when an attention notification is tapped (cold start via onCreate's intent, or onNewIntent
    // while alive). The NavGraph below observes it and routes to that session.
    private val pendingSessionId = mutableStateOf<String?>(null)
    private val pendingInstanceKey = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        maybeRequestNotificationPermission()
        pendingSessionId.value = intent.getStringExtra(AttentionNotifier.EXTRA_SESSION_ID)
        pendingInstanceKey.value = intent.getStringExtra(AttentionNotifier.EXTRA_INSTANCE_KEY)

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
                    val pending = pendingSessionId.value
                    LaunchedEffect(pending) {
                        if (pending != null) {
                            // If the session is still live, open it; if the process was killed and it's
                            // gone, fall back to the sessions list rather than a stuck "Connecting…".
                            val route = if (sessionManager.get(pending) != null) {
                                Routes.Terminal.build(pending)
                            } else {
                                Routes.ACTIVE_SESSIONS
                            }
                            navController.navigate(route) { launchSingleTop = true }
                            pendingSessionId.value = null
                        }
                    }
                    val pendingInst = pendingInstanceKey.value
                    LaunchedEffect(pendingInst) {
                        if (pendingInst != null) {
                            val inst = sessionManager.instanceByKey(pendingInst)
                            if (inst != null) {
                                sessionManager.focusInstance(inst) // jump the host's tmux client to it
                                val sid = sessionManager.sessionIdForHost(inst.hostId)
                                val route = sid?.let { Routes.Terminal.build(it) } ?: Routes.AGENTS
                                navController.navigate(route) { launchSingleTop = true }
                            } else {
                                navController.navigate(Routes.AGENTS) { launchSingleTop = true }
                            }
                            pendingInstanceKey.value = null
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(AttentionNotifier.EXTRA_SESSION_ID)?.let { pendingSessionId.value = it }
        intent.getStringExtra(AttentionNotifier.EXTRA_INSTANCE_KEY)?.let { pendingInstanceKey.value = it }
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

    /** On Android 13+, the foreground-service and agent-attention notifications need this runtime grant. */
    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

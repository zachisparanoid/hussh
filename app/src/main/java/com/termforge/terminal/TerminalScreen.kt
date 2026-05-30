package com.termforge.terminal

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.Manifest
import android.content.pm.PackageManager
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.termforge.data.prefs.BellMode
import com.termforge.keybar.KeyBarRow
import com.termforge.nav.Routes
import com.termforge.session.SessionStatus
import com.termforge.session.TerminalConnection
import com.termux.view.TerminalView
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(navController: NavHostController, viewModel: TerminalViewModel = hiltViewModel()) {
    val connection by viewModel.connection.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val layout by viewModel.keyBarLayout.collectAsState()
    val backspaceByte by viewModel.backspaceByte.collectAsState()
    val sessions by viewModel.allSessions.collectAsState()
    val fontSp by viewModel.fontSizeController.sizeSp.collectAsState()
    val hudToken by viewModel.fontSizeController.hudToken.collectAsState()

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var snippetsOpen by remember { mutableStateOf(false) }
    var bellFlash by remember { mutableStateOf(false) }
    var activePrompt by remember { mutableStateOf<com.termforge.session.SessionPrompt?>(null) }
    var terminalView by remember { mutableStateOf<View?>(null) }

    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    // When the keyboard is up, the system back button dismisses it instead of leaving the session.
    BackHandler(enabled = imeVisible) { terminalView?.let(::hideKeyboard) }

    // Push-to-talk voice state.
    val voiceActive by viewModel.voiceController.active.collectAsState()
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val audioPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasAudioPermission = granted }
    LaunchedEffect(Unit) {
        viewModel.voiceController.errors.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    // Mark the terminal foregrounded so MainActivity routes the volume keys to font sizing.
    DisposableEffect(Unit) {
        viewModel.fontSizeController.terminalForegrounded = true
        viewModel.fontSizeController.volumeKeysEnabled = settings.volumeKeysResizeFont
        onDispose { viewModel.fontSizeController.terminalForegrounded = false }
    }
    LaunchedEffect(Unit) { viewModel.prompts.collect { activePrompt = it } }

    val onBell: () -> Unit = {
        when (settings.bellMode) {
            BellMode.VIBRATE -> vibrate(context)
            BellMode.VISUAL -> bellFlash = true
            BellMode.SILENT -> {}
        }
    }
    LaunchedEffect(bellFlash) { if (bellFlash) { delay(120); bellFlash = false } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(connection?.title ?: "Terminal", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        terminalView?.let { if (imeVisible) hideKeyboard(it) else showKeyboard(it) }
                    }) {
                        Icon(
                            if (imeVisible) Icons.Default.KeyboardHide else Icons.Default.Keyboard,
                            if (imeVisible) "Hide keyboard" else "Show keyboard",
                        )
                    }
                    IconButton(onClick = {
                        clipboard.getText()?.text?.let { viewModel.sendText(it) }
                    }) { Icon(Icons.Default.ContentPaste, "Paste") }
                    IconButton(onClick = { viewModel.fontSizeController.decrease() }) {
                        Icon(Icons.Default.TextDecrease, "Smaller font")
                    }
                    IconButton(onClick = { viewModel.fontSizeController.increase() }) {
                        Icon(Icons.Default.TextIncrease, "Larger font")
                    }
                    IconButton(onClick = { snippetsOpen = true }) { Icon(Icons.Default.Code, "Snippets") }
                    connection?.let { conn ->
                        IconButton(onClick = { navController.navigate(Routes.Sftp.build(conn.hostId)) }) {
                            Icon(Icons.Default.Folder, "Files")
                        }
                    }
                    IconButton(onClick = { viewModel.disconnect(); navController.navigateUp() }) {
                        Icon(Icons.Default.PowerSettingsNew, "Disconnect")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (sessions.size > 1) SessionTabs(sessions, viewModel.sessionId, navController, viewModel)
            connection?.let { StatusStrip(it) }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                val conn = connection
                // Track whether this session ever connected, to distinguish "still connecting" from
                // "ended" once the connection is pruned from the session list.
                var hadConnection by remember { mutableStateOf(false) }
                LaunchedEffect(conn) { if (conn != null) hadConnection = true }
                if (conn != null) {
                    TerminalSurface(conn, viewModel, onBell, onViewCreated = { terminalView = it })
                } else if (hadConnection) {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Session ended",
                            Modifier.padding(top = 64.dp),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        androidx.compose.material3.TextButton(onClick = { navController.navigateUp() }) {
                            Text("Back to hosts")
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Connecting…") }
                }
                if (bellFlash) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)))
                }
                FontHud(fontSp, hudToken, Modifier.align(Alignment.TopCenter))
            }

            // The helper bar: one row tall, sitting above the keyboard (or nav bar when hidden).
            // union(ime, navigationBars) avoids double-counting; the background wraps only the row.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (settings.voiceStreamEnabled) {
                    MicButton(
                        recording = voiceActive,
                        onPress = {
                            if (hasAudioPermission) viewModel.voicePress()
                            else audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        onRelease = { if (hasAudioPermission) viewModel.voiceRelease() },
                    )
                }
                KeyBarRow(
                    layout = layout,
                    state = viewModel.keyBarState,
                    appCursor = { viewModel.appCursorMode() },
                    backspaceByte = { backspaceByte },
                    onSend = { viewModel.send(it) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (snippetsOpen) {
        val snippets by viewModel.snippets.collectAsState()
        ModalBottomSheet(onDismissRequest = { snippetsOpen = false }) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                Text("Snippets", Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                if (snippets.isEmpty()) Text("No snippets yet.", Modifier.padding(16.dp))
                LazyRow(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(snippets, key = { it.id }) { s ->
                        FilterChip(
                            selected = false,
                            onClick = { viewModel.sendSnippet(s.content, s.appendNewline); snippetsOpen = false },
                            label = { Text(s.name) },
                        )
                    }
                }
            }
        }
    }

    activePrompt?.let { com.termforge.ui.components.PromptDialog(it) { activePrompt = null } }
}

@Composable
private fun TerminalSurface(
    connection: TerminalConnection,
    viewModel: TerminalViewModel,
    onBell: () -> Unit,
    onViewCreated: (View) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val clipboard = LocalClipboardManager.current
    val fontSp by viewModel.fontSizeController.sizeSp.collectAsState()
    val themeId by viewModel.terminalThemeId.collectAsState()

    DisposableEffect(connection.sessionId) { onDispose { connection.detachClient() } }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val tv = TerminalView(ctx, null)
            tv.keepScreenOn = true
            // Termux's TerminalView only becomes focusable via layout XML; we create it in code, so
            // we must set these or requestFocus() fails and the soft keyboard never appears.
            tv.isFocusable = true
            tv.isFocusableInTouchMode = true
            tv.setTerminalViewClient(
                TermForgeTerminalViewClient(
                    keyBarState = viewModel.keyBarState,
                    fontSizeController = viewModel.fontSizeController,
                    density = density,
                    onSingleTap = { showKeyboard(tv) },
                    onCopyModeChanged = {},
                    postConsume = { action -> tv.post { action() } },
                ),
            )
            tv.attachSession(connection.terminalSession)
            tv.setTextSize((fontSp * density).toInt().coerceAtLeast(4))
            connection.attachClient(
                ScreenTerminalSessionClient(
                    view = tv,
                    onBellEvent = onBell,
                    onClipboardCopy = { clipboard.setText(AnnotatedString(it)) },
                    onClipboardPaste = { clipboard.getText()?.text?.let { txt -> connection.write(txt.toByteArray()) } },
                    onTitle = {},
                ),
            )
            tv.requestFocus()
            onViewCreated(tv)
            tv
        },
        update = { tv ->
            tv.setTextSize((fontSp * density).toInt().coerceAtLeast(4))
            com.termforge.ui.theme.TerminalThemes.byId(themeId).applyTo(tv.currentSession?.emulator)
            tv.onScreenUpdated()
        },
    )
}

@Composable
private fun StatusStrip(connection: TerminalConnection) {
    val status by connection.status.collectAsState()
    val (color, label) = when (status) {
        SessionStatus.CONNECTED -> MaterialTheme.colorScheme.primary to "Connected"
        SessionStatus.CONNECTING -> MaterialTheme.colorScheme.tertiary to "Connecting…"
        SessionStatus.RECONNECTING -> MaterialTheme.colorScheme.tertiary to "Reconnecting…"
        SessionStatus.FAILED -> MaterialTheme.colorScheme.error to "Connection failed"
        SessionStatus.DISCONNECTED -> MaterialTheme.colorScheme.outline to "Disconnected"
    }
    Surface(color = color.copy(alpha = 0.15f), modifier = Modifier.fillMaxWidth()) {
        Text(
            "${connection.protocol.displayName} • $label",
            Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            color = color,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionTabs(
    sessions: List<TerminalConnection>,
    currentId: String,
    navController: NavHostController,
    viewModel: TerminalViewModel,
) {
    LazyRow(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(sessions, key = { it.sessionId }) { s ->
            FilterChip(
                selected = s.sessionId == currentId,
                onClick = { if (s.sessionId != currentId) navController.navigate(Routes.Terminal.build(s.sessionId)) },
                label = { Text(s.title, maxLines = 1) },
                trailingIcon = {
                    Icon(
                        Icons.Default.Close, "Close",
                        Modifier.padding(start = 2.dp).clickable { viewModel.disconnectId(s.sessionId) },
                    )
                },
            )
        }
    }
}

@Composable
private fun FontHud(fontSp: Float, token: Long, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(token) {
        if (token > 0) { visible = true; delay(900); visible = false }
    }
    AnimatedVisibility(visible, modifier = modifier.padding(top = 12.dp)) {
        Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.small) {
            Text(
                "${fontSp.toInt()} sp",
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
        }
    }
}

/** Push-to-talk mic button pinned at the start of the helper bar. Hold to record. */
@Composable
private fun MicButton(recording: Boolean, onPress: () -> Unit, onRelease: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .height(36.dp)
            .widthIn(min = 52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (recording) colors.error else colors.primary.copy(alpha = 0.20f))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onPress()
                    waitForUpOrCancellation()
                    onRelease()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (recording) Icons.Default.Mic else Icons.Default.MicNone,
            contentDescription = "Push to talk",
            tint = if (recording) colors.onError else colors.onSurface,
        )
    }
}

// --- helpers ---

private fun showKeyboard(view: View) {
    view.isFocusable = true
    view.isFocusableInTouchMode = true
    view.requestFocus()
    val imm = view.context.getSystemService(InputMethodManager::class.java)
    imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
}

private fun hideKeyboard(view: View) {
    val imm = view.context.getSystemService(InputMethodManager::class.java)
    imm?.hideSoftInputFromWindow(view.windowToken, 0)
}

private fun vibrate(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(VibratorManager::class.java))?.defaultVibrator
    } else {
        @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
    }
    vibrator?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
}

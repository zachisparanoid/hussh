package com.termforge.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.termforge.data.db.BackspaceMode
import com.termforge.data.prefs.BellMode
import com.termforge.nav.Routes
import com.termforge.ui.components.EnumDropdown
import com.termforge.ui.theme.AppTheme
import com.termforge.ui.theme.TerminalThemes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavHostController, viewModel: SettingsViewModel = hiltViewModel()) {
    val s by viewModel.settings.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = { navController.navigateUp() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section("Appearance")
            ToggleRow("Material You (dynamic color)", s.dynamicColor, viewModel::setDynamicColor)
            EnumDropdown("App theme", s.appTheme, AppTheme.entries.toList(), { it.displayName }, viewModel::setAppTheme, Modifier.fillMaxWidth())
            EnumDropdown("Terminal theme", TerminalThemes.byId(s.terminalThemeId), TerminalThemes.all, { it.name }, { viewModel.setTerminalTheme(it.id) }, Modifier.fillMaxWidth())

            Section("Terminal")
            Text("Font size: ${s.fontSizeSp.toInt()} sp")
            Slider(value = s.fontSizeSp, onValueChange = viewModel::setFontSize, valueRange = 6f..40f)
            ToggleRow("Blink cursor", s.blinkCursor, viewModel::setBlinkCursor)
            ToggleRow("Font ligatures", s.ligatures, viewModel::setLigatures)
            EnumDropdown("Bell", s.bellMode, BellMode.entries.toList(), { it.name.lowercase().replaceFirstChar(Char::uppercase) }, viewModel::setBellMode, Modifier.fillMaxWidth())
            ToggleRow("Volume keys resize font", s.volumeKeysResizeFont, viewModel::setVolumeKeys)
            ToggleRow("Glide / swipe typing (experimental)", s.glideTypingEnabled, viewModel::setGlideTyping)
            EnumDropdown("Backspace sends", s.defaultBackspace, BackspaceMode.entries.toList(), { if (it == BackspaceMode.DEL) "DEL (0x7F)" else "BS (0x08)" }, viewModel::setBackspace, Modifier.fillMaxWidth())
            NumberField("Scrollback lines", s.scrollbackLines, viewModel::setScrollback)

            Section("Key bar")
            OutlinedButton(onClick = { navController.navigate(Routes.KeyBarEditor.build("GLOBAL")) }, Modifier.fillMaxWidth()) { Text("Edit global key bar") }
            OutlinedButton(onClick = { viewModel.resetKeyBar() }, Modifier.fillMaxWidth()) { Text("Reset key bar to default") }
            ToggleRow("Show key bar when keyboard hidden", s.keyBarVisibleWhenKeyboardHidden, viewModel::setKeyBarVisible)

            Section("Background & security")
            NumberField("Default keep-alive (s)", s.defaultKeepAliveSec, viewModel::setKeepAlive)
            ToggleRow("Biometric app lock", s.biometricLock, viewModel::setBiometricLock)
            Button(
                onClick = {
                    val pm = context.getSystemService(PowerManager::class.java)
                    if (pm != null && !pm.isIgnoringBatteryOptimizations(context.packageName)) {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Ignore battery optimizations") }
            Text(
                "TermForge is lightweight. Exempting it from battery optimization keeps your live sessions from being killed in the background.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Section("Voice (push-to-talk)")
            ToggleRow("Stream mic to remote Claude Code", s.voiceStreamEnabled, viewModel::setVoiceStream)
            OutlinedTextField(
                value = s.voiceSinkCommand,
                onValueChange = viewModel::setVoiceSinkCommand,
                label = { Text("Audio sink command (remote)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            NumberField("Tail drain before stop (ms)", s.voiceTailDrainMs, viewModel::setVoiceTailDrainMs)
            Text(
                "Adds a 🎤 hold-to-talk button to the terminal helper bar that streams your voice over " +
                    "the SSH connection into the machine's virtual mic, so Claude Code transcribes it. " +
                    "Requires running resources/server/termforge-voice-setup.sh on that machine and " +
                    "`/voice tap` enabled in Claude Code.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun NumberField(label: String, value: Int, onChange: (Int) -> Unit) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { v -> v.toIntOrNull()?.let(onChange) },
        label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

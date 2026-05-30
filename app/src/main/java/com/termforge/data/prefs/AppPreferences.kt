package com.termforge.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.termforge.data.db.BackspaceMode
import com.termforge.ui.theme.AppTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class BellMode { VISUAL, VIBRATE, SILENT }

/** Immutable snapshot of all app-level (non-sensitive) settings. */
data class AppSettings(
    val appTheme: AppTheme = AppTheme.FORGE_DARK,
    val dynamicColor: Boolean = true,
    val terminalThemeId: String = "dracula",
    val fontSizeSp: Float = 14f,
    val blinkCursor: Boolean = true,
    val ligatures: Boolean = false,
    val bellMode: BellMode = BellMode.VISUAL,
    val volumeKeysResizeFont: Boolean = true,
    val biometricLock: Boolean = false,
    val scrollbackLines: Int = 10_000,
    val defaultKeepAliveSec: Int = 60,
    val keyBarVisibleWhenKeyboardHidden: Boolean = true,
    val defaultBackspace: BackspaceMode = BackspaceMode.DEL,
    val promptedBatteryOptimization: Boolean = false,
    val voiceStreamEnabled: Boolean = false,
    val voiceSinkCommand: String = DEFAULT_VOICE_SINK,
    /** Wait this long after release for the audio pipeline to drain before sending the stop key, so
     *  the remote transcriber receives the tail of your speech (audio travels a slower path than the
     *  keystroke). Raise it if the end of your words gets cut off. */
    val voiceTailDrainMs: Int = 1200,
)

/** The remote command TermForge pipes mic audio into (PulseAudio virtual-mic FIFO). */
const val DEFAULT_VOICE_SINK: String = "cat > /run/user/\$(id -u)/tf_mic.fifo"

private val Context.dataStore by preferencesDataStore(name = "termforge_settings")

/** Reads/writes [AppSettings] via Preferences DataStore. */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val APP_THEME = stringPreferencesKey("app_theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val TERMINAL_THEME = stringPreferencesKey("terminal_theme")
        val FONT_SIZE = floatPreferencesKey("font_size")
        val BLINK_CURSOR = booleanPreferencesKey("blink_cursor")
        val LIGATURES = booleanPreferencesKey("ligatures")
        val BELL_MODE = stringPreferencesKey("bell_mode")
        val VOLUME_RESIZE = booleanPreferencesKey("volume_resize_font")
        val BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock")
        val SCROLLBACK = intPreferencesKey("scrollback_lines")
        val KEEPALIVE = intPreferencesKey("default_keepalive_sec")
        val KEYBAR_VISIBLE = booleanPreferencesKey("keybar_visible_no_keyboard")
        val BACKSPACE = stringPreferencesKey("default_backspace")
        val BATTERY_PROMPTED = booleanPreferencesKey("battery_prompted")
        val VOICE_STREAM = booleanPreferencesKey("voice_stream_enabled")
        val VOICE_SINK = stringPreferencesKey("voice_sink_command")
        val VOICE_TAIL_DRAIN = intPreferencesKey("voice_tail_drain_ms")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            appTheme = p[Keys.APP_THEME]?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() } ?: AppTheme.FORGE_DARK,
            dynamicColor = p[Keys.DYNAMIC_COLOR] ?: true,
            terminalThemeId = p[Keys.TERMINAL_THEME] ?: "dracula",
            fontSizeSp = p[Keys.FONT_SIZE] ?: 14f,
            blinkCursor = p[Keys.BLINK_CURSOR] ?: true,
            ligatures = p[Keys.LIGATURES] ?: false,
            bellMode = p[Keys.BELL_MODE]?.let { runCatching { BellMode.valueOf(it) }.getOrNull() } ?: BellMode.VISUAL,
            volumeKeysResizeFont = p[Keys.VOLUME_RESIZE] ?: true,
            biometricLock = p[Keys.BIOMETRIC_LOCK] ?: false,
            scrollbackLines = p[Keys.SCROLLBACK] ?: 10_000,
            defaultKeepAliveSec = p[Keys.KEEPALIVE] ?: 60,
            keyBarVisibleWhenKeyboardHidden = p[Keys.KEYBAR_VISIBLE] ?: true,
            defaultBackspace = p[Keys.BACKSPACE]?.let { runCatching { BackspaceMode.valueOf(it) }.getOrNull() } ?: BackspaceMode.DEL,
            promptedBatteryOptimization = p[Keys.BATTERY_PROMPTED] ?: false,
            voiceStreamEnabled = p[Keys.VOICE_STREAM] ?: false,
            voiceSinkCommand = p[Keys.VOICE_SINK] ?: DEFAULT_VOICE_SINK,
            voiceTailDrainMs = p[Keys.VOICE_TAIL_DRAIN] ?: 1200,
        )
    }

    suspend fun setAppTheme(theme: AppTheme) = edit { it[Keys.APP_THEME] = theme.name }
    suspend fun setDynamicColor(enabled: Boolean) = edit { it[Keys.DYNAMIC_COLOR] = enabled }
    suspend fun setTerminalTheme(id: String) = edit { it[Keys.TERMINAL_THEME] = id }
    suspend fun setFontSize(sp: Float) = edit { it[Keys.FONT_SIZE] = sp.coerceIn(MIN_FONT_SP, MAX_FONT_SP) }
    suspend fun setBlinkCursor(enabled: Boolean) = edit { it[Keys.BLINK_CURSOR] = enabled }
    suspend fun setLigatures(enabled: Boolean) = edit { it[Keys.LIGATURES] = enabled }
    suspend fun setBellMode(mode: BellMode) = edit { it[Keys.BELL_MODE] = mode.name }
    suspend fun setVolumeKeysResizeFont(enabled: Boolean) = edit { it[Keys.VOLUME_RESIZE] = enabled }
    suspend fun setBiometricLock(enabled: Boolean) = edit { it[Keys.BIOMETRIC_LOCK] = enabled }
    suspend fun setScrollbackLines(lines: Int) = edit { it[Keys.SCROLLBACK] = lines.coerceIn(500, 100_000) }
    suspend fun setDefaultKeepAlive(sec: Int) = edit { it[Keys.KEEPALIVE] = sec.coerceIn(0, 3600) }
    suspend fun setKeyBarVisibleWhenKeyboardHidden(v: Boolean) = edit { it[Keys.KEYBAR_VISIBLE] = v }
    suspend fun setDefaultBackspace(mode: BackspaceMode) = edit { it[Keys.BACKSPACE] = mode.name }
    suspend fun setBatteryPrompted(v: Boolean) = edit { it[Keys.BATTERY_PROMPTED] = v }
    suspend fun setVoiceStream(v: Boolean) = edit { it[Keys.VOICE_STREAM] = v }
    suspend fun setVoiceSinkCommand(v: String) = edit { it[Keys.VOICE_SINK] = v }
    suspend fun setVoiceTailDrainMs(v: Int) = edit { it[Keys.VOICE_TAIL_DRAIN] = v.coerceIn(0, 5000) }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    companion object {
        const val MIN_FONT_SP = 6f
        const val MAX_FONT_SP = 40f
    }
}

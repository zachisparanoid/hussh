package com.hussh.terminal

import com.hussh.data.prefs.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for terminal font size, shared between the terminal screen (which renders
 * it and handles pinch-zoom) and [com.hussh.MainActivity] (which adjusts it via the volume keys).
 * Changes are persisted to [AppPreferences] and clamped to a sane range.
 */
@Singleton
class FontSizeController @Inject constructor(
    private val prefs: AppPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _sizeSp = MutableStateFlow(14f)
    val sizeSp = _sizeSp.asStateFlow()

    /** A brief size HUD is shown while this is > 0 (the epoch millis of the last change). */
    private val _hudToken = MutableStateFlow(0L)
    val hudToken = _hudToken.asStateFlow()

    /** True while a terminal screen is foregrounded — gates the volume-key interception. */
    @Volatile var terminalForegrounded = false

    @Volatile var volumeKeysEnabled = true

    init {
        scope.launch {
            val s = prefs.settings.first()
            _sizeSp.value = s.fontSizeSp
            volumeKeysEnabled = s.volumeKeysResizeFont
        }
    }

    private var persistJob: Job? = null

    fun increase() = set(_sizeSp.value + STEP)
    fun decrease() = set(_sizeSp.value - STEP)

    fun set(sp: Float) {
        val clamped = sp.coerceIn(AppPreferences.MIN_FONT_SP, AppPreferences.MAX_FONT_SP)
        _sizeSp.value = clamped
        _hudToken.value = _hudToken.value + 1
        // Debounce persistence so a pinch gesture (onScale fires per frame) writes once at the end.
        persistJob?.cancel()
        persistJob = scope.launch {
            delay(250)
            prefs.setFontSize(clamped)
        }
    }

    private companion object { const val STEP = 1f }
}

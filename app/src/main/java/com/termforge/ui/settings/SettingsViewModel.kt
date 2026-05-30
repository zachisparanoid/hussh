package com.termforge.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.termforge.data.db.BackspaceMode
import com.termforge.data.prefs.AppPreferences
import com.termforge.data.prefs.BellMode
import com.termforge.data.repo.KeyBarRepository
import com.termforge.ui.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val keyBarRepo: KeyBarRepository,
) : ViewModel() {

    val settings = prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, com.termforge.data.prefs.AppSettings())

    fun setAppTheme(t: AppTheme) = viewModelScope.launch { prefs.setAppTheme(t) }
    fun setDynamicColor(v: Boolean) = viewModelScope.launch { prefs.setDynamicColor(v) }
    fun setTerminalTheme(id: String) = viewModelScope.launch { prefs.setTerminalTheme(id) }
    fun setFontSize(sp: Float) = viewModelScope.launch { prefs.setFontSize(sp) }
    fun setBlinkCursor(v: Boolean) = viewModelScope.launch { prefs.setBlinkCursor(v) }
    fun setLigatures(v: Boolean) = viewModelScope.launch { prefs.setLigatures(v) }
    fun setBellMode(m: BellMode) = viewModelScope.launch { prefs.setBellMode(m) }
    fun setVolumeKeys(v: Boolean) = viewModelScope.launch { prefs.setVolumeKeysResizeFont(v) }
    fun setBiometricLock(v: Boolean) = viewModelScope.launch { prefs.setBiometricLock(v) }
    fun setScrollback(n: Int) = viewModelScope.launch { prefs.setScrollbackLines(n) }
    fun setKeepAlive(n: Int) = viewModelScope.launch { prefs.setDefaultKeepAlive(n) }
    fun setKeyBarVisible(v: Boolean) = viewModelScope.launch { prefs.setKeyBarVisibleWhenKeyboardHidden(v) }
    fun setBackspace(m: BackspaceMode) = viewModelScope.launch { prefs.setDefaultBackspace(m) }
    fun resetKeyBar() = viewModelScope.launch { keyBarRepo.resetGlobalToDefault() }
    fun setVoiceStream(v: Boolean) = viewModelScope.launch { prefs.setVoiceStream(v) }
    fun setVoiceSinkCommand(v: String) = viewModelScope.launch { prefs.setVoiceSinkCommand(v) }
    fun setVoiceTailDrainMs(v: Int) = viewModelScope.launch { prefs.setVoiceTailDrainMs(v) }
    fun setGlideTyping(v: Boolean) = viewModelScope.launch { prefs.setGlideTyping(v) }
}

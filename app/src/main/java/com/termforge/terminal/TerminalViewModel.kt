package com.termforge.terminal

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.termforge.data.db.BackspaceMode
import com.termforge.data.prefs.AppPreferences
import com.termforge.data.repo.HostRepository
import com.termforge.data.repo.KeyBarRepository
import com.termforge.keybar.DefaultKeyBar
import com.termforge.keybar.KeyBarLayout
import com.termforge.keybar.KeyBarState
import com.termforge.nav.Routes
import com.termforge.session.PromptHub
import com.termforge.session.SessionManager
import com.termforge.session.TerminalConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TerminalViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val sessionManager: SessionManager,
    private val hostRepo: HostRepository,
    private val keyBarRepo: KeyBarRepository,
    private val promptHub: PromptHub,
    snippetRepo: com.termforge.data.repo.SnippetRepository,
    val fontSizeController: FontSizeController,
    private val appPrefs: AppPreferences,
) : ViewModel() {

    val snippets = snippetRepo.observeSnippets()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val sessionId: String = savedState[Routes.Terminal.ARG_SESSION] ?: ""
    private val hostIdArg: Long = savedState.get<Long>(Routes.Terminal.ARG_HOST) ?: -1L

    /** The shared modifier state: read by the helper bar AND by the terminal-view client. */
    val keyBarState = KeyBarState()

    val settings = appPrefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, com.termforge.data.prefs.AppSettings())

    val keyBarLayout = MutableStateFlow(DefaultKeyBar.layout)
    val backspaceByte = MutableStateFlow(BackspaceMode.DEL.byte)
    val terminalThemeId = MutableStateFlow("dracula")

    val allSessions = sessionManager.sessions

    val connection = sessionManager.sessions
        .map { list -> list.firstOrNull { it.sessionId == sessionId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, sessionManager.get(sessionId))

    /** Prompts (host-key, password, …) for THIS session only. */
    val prompts = promptHub.prompts.filter { it.sessionId == sessionId }

    init {
        if (hostIdArg > 0) {
            viewModelScope.launch {
                runCatching { sessionManager.startConnection(sessionId, hostIdArg) }
            }
        }
        viewModelScope.launch { loadHostConfig() }
    }

    private suspend fun loadHostConfig() {
        val hostId = if (hostIdArg > 0) hostIdArg else connection.value?.hostId ?: return
        hostRepo.getById(hostId)?.let { host ->
            keyBarLayout.value = keyBarRepo.effectiveLayout(host.customKeyBarId)
            backspaceByte.value = host.backspaceMode.byte
            terminalThemeId.value = host.terminalThemeId ?: settings.value.terminalThemeId
        }
    }

    fun appCursorMode(): Boolean =
        connection.value?.terminalSession?.emulator?.isCursorKeysApplicationMode == true

    fun send(bytes: ByteArray) = connection.value?.write(bytes)

    fun sendText(text: String) = connection.value?.write(text.toByteArray(Charsets.UTF_8))

    fun sendSnippet(content: String, appendNewline: Boolean) =
        sendText(if (appendNewline) content + "\n" else content)

    fun disconnect() = sessionManager.disconnect(sessionId)

    fun disconnectId(id: String) = sessionManager.disconnect(id)

    // --- Push-to-talk voice (streams mic audio to the remote Claude Code over a second SSH channel) ---
    val voiceController = com.termforge.voice.VoiceController(viewModelScope)
    private val voiceTriggerKey = byteArrayOf(0x20) // Space = the /voice (tap mode) key

    fun voicePress() {
        connection.value?.let { voiceController.press(it, settings.value.voiceSinkCommand, voiceTriggerKey) }
    }

    fun voiceRelease() {
        connection.value?.let { voiceController.release(it, voiceTriggerKey, settings.value.voiceTailDrainMs) }
    }

    fun setGlideTyping(enabled: Boolean) = viewModelScope.launch { appPrefs.setGlideTyping(enabled) }
}

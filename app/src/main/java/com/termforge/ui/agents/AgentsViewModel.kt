package com.termforge.ui.agents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.termforge.agent.AgentInstance
import com.termforge.data.prefs.AppPreferences
import com.termforge.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs the Agents dashboard: the live fleet of Claude instances + routing + per-session mute. */
@HiltViewModel
class AgentsViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val appPrefs: AppPreferences,
) : ViewModel() {
    val instances = sessionManager.instances

    /** Per-session mute keys (by [AgentInstance.enableKey]); a muted session fires no notifications. */
    val mutedSessions = appPrefs.settings
        .map { it.mutedAgentSessions }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    fun focus(i: AgentInstance) = sessionManager.focusInstance(i)
    fun approve(i: AgentInstance) = sessionManager.routeApprove(i)
    fun deny(i: AgentInstance) = sessionManager.routeDeny(i)
    fun cont(i: AgentInstance) = sessionManager.routeContinue(i)
    fun reply(i: AgentInstance, text: String) = sessionManager.routeText(i, text)
    fun setMuted(i: AgentInstance, muted: Boolean) =
        viewModelScope.launch { appPrefs.setAgentSessionMuted(i.enableKey, muted) }

    /** sessionId of a live connection to this instance's host, for navigating the terminal to it. */
    fun sessionIdForHost(hostId: Long): String? = sessionManager.sessionIdForHost(hostId)
}

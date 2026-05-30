package com.termforge.ui.agents

import androidx.lifecycle.ViewModel
import com.termforge.agent.AgentInstance
import com.termforge.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Backs the Agents dashboard: the live fleet of Claude instances + routing actions. */
@HiltViewModel
class AgentsViewModel @Inject constructor(
    private val sessionManager: SessionManager,
) : ViewModel() {
    val instances = sessionManager.instances

    fun focus(i: AgentInstance) = sessionManager.focusInstance(i)
    fun approve(i: AgentInstance) = sessionManager.routeApprove(i)
    fun deny(i: AgentInstance) = sessionManager.routeDeny(i)
    fun cont(i: AgentInstance) = sessionManager.routeContinue(i)
    fun reply(i: AgentInstance, text: String) = sessionManager.routeText(i, text)

    /** sessionId of a live connection to this instance's host, for navigating the terminal to it. */
    fun sessionIdForHost(hostId: Long): String? = sessionManager.sessionIdForHost(hostId)
}

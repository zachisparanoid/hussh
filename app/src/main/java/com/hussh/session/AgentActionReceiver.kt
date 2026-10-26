package com.hussh.session

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Handles Approve/Deny taps from an agent-attention notification's action buttons (these also surface
 * on a paired Wear OS watch). Resolves the instance by key and routes the keystroke to its tmux pane.
 *
 * Uses a Hilt [EntryPoint] rather than @AndroidEntryPoint field injection — the latter's
 * `super.onReceive()` injection hook can't be called in Kotlin (onReceive is abstract).
 */
class AgentActionReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Entry {
        fun sessionManager(): SessionManager
    }

    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(AttentionNotifier.EXTRA_INSTANCE_KEY) ?: return
        val sessionManager = EntryPointAccessors
            .fromApplication(context.applicationContext, Entry::class.java)
            .sessionManager()
        val inst = sessionManager.instanceByKey(key) ?: return
        when (intent.action) {
            ACTION_APPROVE -> sessionManager.routeApprove(inst)
            ACTION_DENY -> sessionManager.routeDeny(inst)
        }
    }

    companion object {
        const val ACTION_APPROVE = "com.hussh.action.AGENT_APPROVE"
        const val ACTION_DENY = "com.hussh.action.AGENT_DENY"
    }
}

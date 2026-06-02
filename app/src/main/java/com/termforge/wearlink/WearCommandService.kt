package com.termforge.wearlink

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.termforge.session.SessionManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json

/**
 * Receives commands from the watch over the Data Layer and routes them to the right Claude instance.
 * `replyText` types the transcription WITHOUT Enter (review-before-send); `send` commits Enter.
 */
class WearCommandService : WearableListenerService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Entry {
        fun sessionManager(): SessionManager
    }

    private val json = Json { ignoreUnknownKeys = true }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WearLinkPublisher.CMD_PATH) return
        val cmd = runCatching {
            json.decodeFromString(WearCommand.serializer(), String(event.data, Charsets.UTF_8))
        }.getOrNull() ?: return
        // Drop an identical command repeated within a short window — a safety net against re-sends
        // (e.g. the watch sending to multiple nodes, or a user tapping twice when unsure it worked).
        val sig = "${cmd.action}:${cmd.instanceKey}:${cmd.text}"
        val now = android.os.SystemClock.elapsedRealtime()
        if (sig == lastSig && now - lastAtMs < DEDUP_MS) return
        lastSig = sig
        lastAtMs = now
        val sm = EntryPointAccessors
            .fromApplication(applicationContext, Entry::class.java)
            .sessionManager()
        // Reconnect is host-level (no instance — that's the whole point: the connection is down).
        if (cmd.action == "reconnect") {
            if (cmd.hostId >= 0) sm.reconnectHost(cmd.hostId)
            return
        }
        val inst = sm.instanceByKey(cmd.instanceKey) ?: return
        when (cmd.action) {
            "approve" -> sm.routeApprove(inst)
            "deny" -> sm.routeDeny(inst)
            "send" -> sm.routeContinue(inst)
            "replyText" -> sm.routeType(inst, cmd.text)
            "focus" -> sm.focusInstance(inst)
        }
    }

    companion object {
        // De-dup identical watch commands (static: WearableListenerService may be recreated per message).
        private const val DEDUP_MS = 3000L
        @Volatile private var lastSig = ""
        @Volatile private var lastAtMs = 0L
    }
}

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
        val sm = EntryPointAccessors
            .fromApplication(applicationContext, Entry::class.java)
            .sessionManager()
        val inst = sm.instanceByKey(cmd.instanceKey) ?: return
        when (cmd.action) {
            "approve" -> sm.routeApprove(inst)
            "deny" -> sm.routeDeny(inst)
            "send" -> sm.routeContinue(inst)
            "replyText" -> sm.routeType(inst, cmd.text)
            "focus" -> sm.focusInstance(inst)
        }
    }
}

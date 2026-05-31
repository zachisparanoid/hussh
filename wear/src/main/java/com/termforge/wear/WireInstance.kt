package com.termforge.wear

import kotlinx.serialization.Serializable

/** Mirror of the phone's `com.termforge.agent.WireInstance` — same JSON shape over the Data Layer. */
@Serializable
data class WireInstance(
    val key: String,
    val session: String,
    val reason: String,
    val cwd: String,
    val prompt: List<String> = emptyList(),
    val routable: Boolean = false,
)

@Serializable
data class WireInstanceList(val instances: List<WireInstance> = emptyList())

/** A command from the watch to the phone (mirror of the phone's parser). */
@Serializable
data class WearCommand(val action: String, val instanceKey: String, val text: String = "")

/** Data Layer paths, shared phone↔watch. */
object WearPaths {
    const val AGENTS = "/tf/agents"   // phone → watch: the fleet (DataItem)
    const val CMD = "/tf/cmd"         // watch → phone: a command (Message)
}

/** Command action names (shared phone↔watch). */
object WearActions {
    const val APPROVE = "approve"
    const val DENY = "deny"
    const val SEND = "send"           // commit Enter on the pane
    const val REPLY_TEXT = "replyText" // type transcribed text (no Enter — review on phone)
    const val FOCUS = "focus"         // jump the phone's terminal to this instance
}

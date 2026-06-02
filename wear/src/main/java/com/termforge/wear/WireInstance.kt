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

/** A host the watch cares about + its connection state (so the watch can reconnect it). */
@Serializable
data class WatchHost(
    val hostId: Long,
    val name: String,
    val status: String, // connected | connecting | disconnected
)

/** Everything the phone pushes: the relevant hosts (+ status) and the live instances. */
@Serializable
data class WatchState(
    val hosts: List<WatchHost> = emptyList(),
    val instances: List<WireInstance> = emptyList(),
)

/** A command from the watch to the phone (mirror of the phone's parser). */
@Serializable
data class WearCommand(
    val action: String,
    val instanceKey: String = "",
    val text: String = "",
    val hostId: Long = -1,
)

/** Data Layer paths, shared phone↔watch. */
object WearPaths {
    const val AGENTS = "/tf/agents"   // phone → watch: WatchState (DataItem)
    const val CMD = "/tf/cmd"         // watch → phone: a command (Message)
}

/** Command action names (shared phone↔watch). */
object WearActions {
    const val APPROVE = "approve"
    const val DENY = "deny"
    const val SEND = "send"           // commit Enter on the pane
    const val REPLY_TEXT = "replyText" // type transcribed text (no Enter — review on phone)
    const val FOCUS = "focus"         // jump the phone's terminal to this instance
    const val RECONNECT = "reconnect"  // phone re-opens SSH to a host (host-level)
}

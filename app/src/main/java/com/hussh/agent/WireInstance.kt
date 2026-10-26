package com.hussh.agent

import kotlinx.serialization.Serializable

/**
 * The shape of an agent instance sent over the Wear Data Layer (phone → watch). A trimmed [AgentInstance]
 * — only what the watch needs to display + address. The `:wear` module mirrors these classes.
 */
@Serializable
data class WireInstance(
    val key: String,
    val session: String,
    val reason: String,
    val cwd: String,
    val prompt: List<String>,
    val routable: Boolean,
)

/** A host the watch cares about + its current connection state, so the watch can reconnect it. */
@Serializable
data class WatchHost(
    val hostId: Long,
    val name: String,
    val status: String, // connected | connecting | disconnected
)

/** Everything pushed to the watch: the relevant hosts (+ status) and the live agent instances. */
@Serializable
data class WatchState(
    val hosts: List<WatchHost> = emptyList(),
    val instances: List<WireInstance> = emptyList(),
)

fun AgentInstance.toWire(): WireInstance = WireInstance(
    key = key,
    session = sessionName ?: "claude",
    reason = reason,
    cwd = cwd ?: "",
    prompt = prompt,
    routable = routable,
)

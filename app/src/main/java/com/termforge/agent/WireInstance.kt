package com.termforge.agent

import kotlinx.serialization.Serializable

/**
 * The shape of an agent instance sent over the Wear Data Layer (phone → watch). A trimmed [AgentInstance]
 * — only what the watch needs to display + address. The `:wear` module has a mirror of this class
 * (no shared module; both sides (de)serialize the same JSON).
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

@Serializable
data class WireInstanceList(val instances: List<WireInstance> = emptyList())

fun AgentInstance.toWire(): WireInstance = WireInstance(
    key = key,
    session = sessionName ?: "claude",
    reason = reason,
    cwd = cwd ?: "",
    prompt = prompt,
    routable = routable,
)

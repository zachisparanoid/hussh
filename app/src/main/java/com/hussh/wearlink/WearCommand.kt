package com.hussh.wearlink

import kotlinx.serialization.Serializable

/** A command from the watch (mirror of `com.hussh.wear.WearCommand`). */
@Serializable
data class WearCommand(
    val action: String,
    val instanceKey: String = "",
    val text: String = "",
    val hostId: Long = -1, // for "reconnect"
)

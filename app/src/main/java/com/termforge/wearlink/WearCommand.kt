package com.termforge.wearlink

import kotlinx.serialization.Serializable

/** A command from the watch (mirror of `com.termforge.wear.WearCommand`). */
@Serializable
data class WearCommand(val action: String, val instanceKey: String, val text: String = "")

package com.hussh.wear

import android.content.Context
import kotlinx.serialization.json.Json

/**
 * Persists the latest fleet JSON (from the Data Layer) to SharedPreferences so the Tile can read it
 * synchronously even when the app isn't running. Written by [WearDataListenerService] (background)
 * and [WearAgentClient] (foreground); read by [AgentTileService].
 */
object FleetStore {
    private const val PREFS = "hussh_fleet"
    private const val KEY_JSON = "json"
    private val json = Json { ignoreUnknownKeys = true }

    fun save(context: Context, fleetJson: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_JSON, fleetJson).apply()
    }

    fun load(context: Context): WatchState {
        val s = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_JSON, null)
            ?: return WatchState()
        return runCatching { json.decodeFromString(WatchState.serializer(), s) }.getOrDefault(WatchState())
    }
}

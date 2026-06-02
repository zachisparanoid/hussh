package com.termforge.wear

import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

/**
 * Receives fleet updates from the phone even when the app is closed (the system starts this service
 * on Data Layer changes). Persists the fleet for the Tile and asks the Tile to refresh.
 */
class WearDataListenerService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        var changed = false
        events.forEach { e ->
            if (e.type == DataEvent.TYPE_CHANGED && e.dataItem.uri.path == WearPaths.AGENTS) {
                DataMapItem.fromDataItem(e.dataItem).dataMap.getString("json")?.let {
                    FleetStore.save(this, it)
                    changed = true
                }
            }
        }
        if (changed) {
            runCatching { TileService.getUpdater(this).requestUpdate(AgentTileService::class.java) }
        }
    }
}

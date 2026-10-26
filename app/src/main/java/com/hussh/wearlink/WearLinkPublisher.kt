package com.hussh.wearlink

import android.content.Context
import android.os.SystemClock
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.hussh.agent.WatchState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Pushes the current agent fleet to a paired Wear OS watch via the Data Layer. */
@Singleton
class WearLinkPublisher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { encodeDefaults = true }

    suspend fun publish(state: WatchState) {
        runCatching {
            val payload = json.encodeToString(state)
            val req = PutDataMapRequest.create(PATH).apply {
                dataMap.putString("json", payload)
                // A changing timestamp makes the DataItem differ even when the list is unchanged,
                // so the watch always gets the latest (and "no instances" still propagates).
                dataMap.putLong("ts", SystemClock.elapsedRealtime())
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(req).await()
        }
    }

    companion object {
        const val PATH = "/hussh/agents"
        const val CMD_PATH = "/hussh/cmd"
    }
}

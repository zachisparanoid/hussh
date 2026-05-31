package com.termforge.wear

import android.content.Context
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json

/**
 * Watch-side Data Layer client: follows the phone's agent fleet (DataItem at [WearPaths.AGENTS]) and
 * sends commands back (Messages at [WearPaths.CMD]).
 */
class WearAgentClient(context: Context) {
    private val appContext = context.applicationContext
    private val dataClient = Wearable.getDataClient(appContext)
    private val messageClient = Wearable.getMessageClient(appContext)
    private val nodeClient = Wearable.getNodeClient(appContext)
    private val json = Json { ignoreUnknownKeys = true }

    private val _instances = MutableStateFlow<List<WireInstance>>(emptyList())
    val instances = _instances.asStateFlow()

    private val listener = DataClient.OnDataChangedListener { events ->
        events.forEach { e ->
            if (e.type == DataEvent.TYPE_CHANGED && e.dataItem.uri.path == WearPaths.AGENTS) {
                parse(e.dataItem)
            }
        }
    }

    fun start(scope: CoroutineScope) {
        dataClient.addListener(listener)
        scope.launch {
            runCatching {
                val buffer = dataClient.dataItems.await()
                buffer.firstOrNull { it.uri.path == WearPaths.AGENTS }?.let { parse(it) }
                buffer.release()
            }
        }
    }

    fun stop() {
        runCatching { dataClient.removeListener(listener) }
    }

    /** Fire-and-forget a command to the phone (action + instance key + optional text). */
    fun sendCommand(scope: CoroutineScope, action: String, instanceKey: String, text: String = "") {
        scope.launch {
            runCatching {
                val payload = json.encodeToString(
                    WearCommand.serializer(), WearCommand(action, instanceKey, text),
                ).toByteArray(Charsets.UTF_8)
                val nodes = nodeClient.connectedNodes.await()
                nodes.forEach { node ->
                    messageClient.sendMessage(node.id, WearPaths.CMD, payload).await()
                }
            }
        }
    }

    private fun parse(item: DataItem) {
        val s = DataMapItem.fromDataItem(item).dataMap.getString("json") ?: return
        runCatching {
            _instances.value = json.decodeFromString(WireInstanceList.serializer(), s).instances
        }
    }
}

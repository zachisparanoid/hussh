package com.hussh.wear

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * The watch app. Status-aware: if a watch-host is down it offers **Reconnect** (the phone re-opens
 * SSH and the still-running tmux claudes return); when connected it lists the sessions to read and
 * reply to by voice. Voice replies are typed into Claude WITHOUT submitting (review on phone), and
 * every action confirms delivery so you never re-send blindly.
 */
class WearMainActivity : ComponentActivity() {
    private val client by lazy { WearAgentClient(applicationContext) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WearApp(client, scope) }
    }

    override fun onResume() { super.onResume(); client.start(scope) }
    override fun onPause() { super.onPause(); client.stop() }
    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}

@Composable
private fun WearApp(client: WearAgentClient, scope: CoroutineScope) {
    val instances by client.instances.collectAsState()
    val hosts by client.hosts.collectAsState()
    var selectedKey by remember { mutableStateOf<String?>(null) }
    val selected = instances.firstOrNull { it.key == selectedKey }

    MaterialTheme {
        Scaffold(timeText = { if (selected == null) TimeText() }) {
            if (selected == null) {
                Home(instances, hosts, scope, client, onSelect = { selectedKey = it.key })
            } else {
                AgentDetail(selected, client, scope, onBack = { selectedKey = null })
            }
        }
    }
}

@Composable
private fun Home(
    instances: List<WireInstance>,
    hosts: List<WatchHost>,
    scope: CoroutineScope,
    client: WearAgentClient,
    onSelect: (WireInstance) -> Unit,
) {
    var banner by remember { mutableStateOf("") }
    val down = hosts.filter { it.status != "connected" }

    ScalingLazyColumn(Modifier.fillMaxSize()) {
        item { ListHeader { Text("Agents") } }

        if (banner.isNotEmpty()) {
            item {
                Text(
                    banner,
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.secondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Hosts that aren't up: a reconnect button (or a "connecting…" placeholder).
        down.forEach { host ->
            item {
                if (host.status == "connecting") {
                    Chip(
                        onClick = {},
                        enabled = false,
                        label = { Text(host.name, maxLines = 1) },
                        secondaryLabel = { Text("connecting…") },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Chip(
                        onClick = {
                            banner = "Reconnecting ${host.name}…"
                            client.sendCommand(scope, WearActions.RECONNECT, hostId = host.hostId) { ok ->
                                if (!ok) banner = "✗ Reconnect failed"
                            }
                        },
                        label = { Text("⟳ Reconnect", maxLines = 1) },
                        secondaryLabel = { Text("${host.name} offline", maxLines = 1) },
                        colors = ChipDefaults.primaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        if (instances.isEmpty() && down.isEmpty()) {
            item {
                Text(
                    "No agents.\nEnable a session on your phone.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.caption2,
                )
            }
        }

        instances.forEach { inst ->
            item {
                Chip(
                    onClick = { onSelect(inst) },
                    label = { Text(inst.session, maxLines = 1) },
                    secondaryLabel = { Text(reasonPhrase(inst.reason), maxLines = 1) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun AgentDetail(
    inst: WireInstance,
    client: WearAgentClient,
    scope: CoroutineScope,
    onBack: () -> Unit,
) {
    var status by remember(inst.key) { mutableStateOf("") }
    // Every action goes through here so the UI confirms delivery ("✓ Sent"). Without that confirmation
    // it's unclear whether a voice reply went through, so people re-send → duplicates.
    val act: (String, String) -> Unit = { action, text ->
        status = if (text.isBlank()) "Sending…" else "Sending: \"$text\""
        client.sendCommand(scope, action, inst.key, text) { ok ->
            status = if (ok) (if (text.isBlank()) "✓ Sent" else "✓ Sent: \"$text\"") else "✗ Failed — tap again"
        }
    }
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) act(WearActions.REPLY_TEXT, text)
        }
    }

    ScalingLazyColumn(Modifier.fillMaxSize()) {
        item { ListHeader { Text(inst.session, maxLines = 1) } }
        item {
            Text(
                reasonPhrase(inst.reason),
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.primary,
            )
        }
        if (status.isNotEmpty()) {
            item {
                Text(
                    status,
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.secondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (inst.prompt.isNotEmpty()) {
            item { Text(inst.prompt.joinToString("\n"), style = MaterialTheme.typography.caption2) }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                CompactChip(
                    onClick = {
                        voiceLauncher.launch(
                            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                            ),
                        )
                    },
                    label = { Text("🎤 Talk") },
                )
                CompactChip(onClick = { act(WearActions.SEND, "") }, label = { Text("Send") })
            }
        }
        if (inst.reason == "perm" && inst.routable) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                ) {
                    CompactChip(
                        onClick = { act(WearActions.APPROVE, "") },
                        label = { Text("Approve") },
                        colors = ChipDefaults.primaryChipColors(),
                    )
                    CompactChip(onClick = { act(WearActions.DENY, "") }, label = { Text("Deny") })
                }
            }
        }
        item {
            CompactChip(
                onClick = { act(WearActions.FOCUS, ""); onBack() },
                label = { Text("Open on phone") },
            )
        }
        item { CompactChip(onClick = onBack, label = { Text("← Back") }) }
    }
}

private fun reasonPhrase(reason: String): String = when (reason) {
    "perm" -> "needs permission"
    "input" -> "waiting for input"
    "done" -> "finished"
    "running" -> "running"
    else -> "needs you"
}

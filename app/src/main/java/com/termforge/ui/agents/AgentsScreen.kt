package com.termforge.ui.agents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.termforge.agent.AgentInstance
import com.termforge.nav.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentsScreen(navController: NavHostController, viewModel: AgentsViewModel = hiltViewModel()) {
    val instances by viewModel.instances.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agents") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (instances.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), Alignment.Center) {
                Text(
                    "No Claude sessions detected.\n\nConnect to a host running claude with the " +
                        "attention setup installed (resources/server/termforge-notify-setup.sh), and " +
                        "make sure 'Claude attention notifications' is on in Settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            LazyColumn(
                Modifier.padding(padding).fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(instances, key = { it.key }) { inst ->
                    AgentCard(
                        inst = inst,
                        onFocus = {
                            viewModel.focus(inst)
                            viewModel.sessionIdForHost(inst.hostId)?.let {
                                navController.navigate(Routes.Terminal.build(it)) { launchSingleTop = true }
                            }
                        },
                        onApprove = { viewModel.approve(inst) },
                        onDeny = { viewModel.deny(inst) },
                        onContinue = { viewModel.cont(inst) },
                        onReply = { viewModel.reply(inst, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AgentCard(
    inst: AgentInstance,
    onFocus: () -> Unit,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onContinue: () -> Unit,
    onReply: (String) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    inst.sessionName ?: "claude",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                ReasonChip(inst.reason)
            }
            inst.cwd?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // The captured prompt (permission options / what it's waiting on).
            if (inst.prompt.isNotEmpty()) {
                Text(
                    inst.prompt.joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 5, overflow = TextOverflow.Ellipsis,
                )
            }

            if (!inst.routable) {
                Text(
                    "notify-only — not running in tmux, so it can't be remotely controlled.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onFocus) {
                    Icon(Icons.Default.OpenInFull, null, Modifier.padding(end = 4.dp)); Text("Open")
                }
                if (inst.reason == "perm") {
                    Button(onClick = onApprove) { Text("Approve") }
                    OutlinedButton(onClick = onDeny) { Text("Deny") }
                } else {
                    OutlinedButton(onClick = onContinue) { Text("Continue") }
                }
            }

            var reply by remember(inst.key) { mutableStateOf("") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = reply, onValueChange = { reply = it },
                    label = { Text("Reply") }, singleLine = true, modifier = Modifier.weight(1f),
                )
                Button(onClick = { if (reply.isNotBlank()) { onReply(reply); reply = "" } }) { Text("Send") }
            }
        }
    }
}

@Composable
private fun ReasonChip(reason: String) {
    val (label, color) = when (reason) {
        "perm" -> "needs permission" to MaterialTheme.colorScheme.error
        "input" -> "waiting" to MaterialTheme.colorScheme.tertiary
        "done" -> "finished" to MaterialTheme.colorScheme.primary
        else -> "running" to MaterialTheme.colorScheme.outline
    }
    AssistChip(
        onClick = {}, enabled = false, label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            disabledLabelColor = color,
            disabledContainerColor = color.copy(alpha = 0.12f),
        ),
    )
}

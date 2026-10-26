package com.hussh.ui.hostedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.hussh.data.db.BackspaceMode
import com.hussh.data.db.Protocol
import com.hussh.nav.Routes
import com.hussh.ui.components.EnumDropdown
import androidx.compose.foundation.text.KeyboardOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostEditorScreen(navController: NavHostController, viewModel: HostEditorViewModel = hiltViewModel()) {
    val host by viewModel.draft.collectAsState()
    val password by viewModel.password.collectAsState()
    val identities by viewModel.identities.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val forwards by viewModel.forwards.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isNew) "New host" else "Edit host") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { viewModel.save { navController.navigateUp() } }) {
                        Icon(Icons.Default.Check, "Save")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Connection")
            OutlinedTextField(host.nickname, { v -> viewModel.update { it.copy(nickname = v) } }, label = { Text("Nickname") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(host.address, { v -> viewModel.update { it.copy(address = v) } }, label = { Text("Host / address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EnumDropdown(
                    label = "Protocol",
                    selected = host.protocol,
                    options = Protocol.entries,
                    optionLabel = { it.displayName },
                    onSelected = viewModel::setProtocol,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    host.port.toString(),
                    { v -> viewModel.update { it.copy(port = v.toIntOrNull() ?: it.port) } },
                    label = { Text("Port") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(host.username, { v -> viewModel.update { it.copy(username = v) } }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            EnumDropdown(
                label = "Group",
                selected = groups.firstOrNull { it.id == host.groupId },
                options = listOf<com.hussh.data.db.entities.GroupEntity?>(null) + groups,
                optionLabel = { it?.name ?: "No group" },
                onSelected = { g -> viewModel.update { it.copy(groupId = g?.id) } },
                modifier = Modifier.fillMaxWidth(),
            )

            if (host.protocol.isSsh) {
                SectionTitle("Authentication")
                EnumDropdown(
                    label = "Identity",
                    selected = identities.firstOrNull { it.id == host.identityId },
                    options = listOf<com.hussh.data.db.entities.IdentityEntity?>(null) + identities,
                    optionLabel = { it?.name ?: "None (password)" },
                    onSelected = { idy -> viewModel.update { it.copy(identityId = idy?.id) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { viewModel.password.value = it },
                    label = { Text("Password (optional)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                ToggleRow("Save password (encrypted)", host.savePassword) { v -> viewModel.update { it.copy(savePassword = v) } }
                ToggleRow("Ask each time", host.askEachTime) { v -> viewModel.update { it.copy(askEachTime = v) } }
                ToggleRow("Allow keyboard-interactive", host.useKeyboardInteractive) { v -> viewModel.update { it.copy(useKeyboardInteractive = v) } }
            }

            SectionTitle("Terminal")
            OutlinedTextField(host.termType, { v -> viewModel.update { it.copy(termType = v) } }, label = { Text("Terminal type") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(host.charset, { v -> viewModel.update { it.copy(charset = v) } }, label = { Text("Charset") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(host.startupCommand ?: "", { v -> viewModel.update { it.copy(startupCommand = v.ifBlank { null }) } }, label = { Text("Startup command") }, modifier = Modifier.fillMaxWidth())
            EnumDropdown(
                label = "Backspace sends",
                selected = host.backspaceMode,
                options = BackspaceMode.entries,
                optionLabel = { if (it == BackspaceMode.DEL) "DEL (0x7F)" else "BS (0x08)" },
                onSelected = { m -> viewModel.update { it.copy(backspaceMode = m) } },
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle("Background")
            OutlinedTextField(
                host.keepAliveSec.toString(),
                { v -> viewModel.update { it.copy(keepAliveSec = v.toIntOrNull() ?: it.keepAliveSec) } },
                label = { Text("Keep-alive interval (s)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            ToggleRow("Reconnect automatically", host.autoReconnect) { v -> viewModel.update { it.copy(autoReconnect = v) } }

            if (host.protocol.isSsh) {
                SectionTitle("Port forwarding")
                forwards.forEach { pf ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${pf.type.displayName}  ${pf.bindAddr}:${pf.bindPort} → ${pf.destHost}:${pf.destPort}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = { viewModel.removeForward(pf) }) { Icon(Icons.Default.Delete, "Remove") }
                    }
                }
                PortForwardAdder(onAdd = viewModel::addForward)

                SectionTitle("Key bar")
                if (!viewModel.isNew) {
                    OutlinedButton(onClick = { navController.navigate(Routes.KeyBarEditor.build("HOST", host.id)) }) {
                        Text("Customize key bar for this host")
                    }
                } else {
                    Text("Save the host first to set a custom key bar.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Button(onClick = { viewModel.save { navController.navigateUp() } }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Save")
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PortForwardAdder(onAdd: (com.hussh.data.db.entities.PortForwardEntity) -> Unit) {
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var type by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(com.hussh.data.db.ForwardType.LOCAL) }
    var bindAddr by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("127.0.0.1") }
    var bindPort by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("8080") }
    var destHost by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("localhost") }
    var destPort by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("80") }

    OutlinedButton(onClick = { open = true }) { Text("Add port forward") }

    if (open) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Port forward") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EnumDropdown("Type", type, com.hussh.data.db.ForwardType.entries, { it.displayName }, { type = it }, Modifier.fillMaxWidth())
                    OutlinedTextField(bindAddr, { bindAddr = it }, label = { Text("Bind address") }, singleLine = true)
                    OutlinedTextField(bindPort, { bindPort = it }, label = { Text("Bind port") }, singleLine = true)
                    if (type != com.hussh.data.db.ForwardType.DYNAMIC) {
                        OutlinedTextField(destHost, { destHost = it }, label = { Text("Destination host") }, singleLine = true)
                        OutlinedTextField(destPort, { destPort = it }, label = { Text("Destination port") }, singleLine = true)
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    onAdd(
                        com.hussh.data.db.entities.PortForwardEntity(
                            hostId = 0, type = type, bindAddr = bindAddr,
                            bindPort = bindPort.toIntOrNull() ?: 0,
                            destHost = destHost, destPort = destPort.toIntOrNull() ?: 0,
                        ),
                    )
                    open = false
                }) { Text("Add") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { open = false }) { Text("Cancel") } },
        )
    }
}

package com.hussh.ui.hosts

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.hussh.data.db.entities.HostEntity
import com.hussh.nav.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostListScreen(navController: NavHostController, viewModel: HostListViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val sessions by viewModel.activeSessions.collectAsState()
    var searching by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hussh") },
                actions = {
                    BadgedBox(badge = { if (sessions.isNotEmpty()) Badge { Text("${sessions.size}") } }) {
                        IconButton(onClick = { navController.navigate(Routes.ACTIVE_SESSIONS) }) {
                            Icon(Icons.Default.Dns, "Active sessions")
                        }
                    }
                    IconButton(onClick = { navController.navigate(Routes.AGENTS) }) {
                        Icon(Icons.Default.SmartToy, "Agents")
                    }
                    IconButton(onClick = { searching = !searching }) { Icon(Icons.Default.Search, "Search") }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Identities") }, onClick = { menuOpen = false; navController.navigate(Routes.IDENTITIES) })
                            DropdownMenuItem(text = { Text("Snippets") }, onClick = { menuOpen = false; navController.navigate(Routes.SNIPPETS) })
                            DropdownMenuItem(text = { Text("Settings") }, onClick = { menuOpen = false; navController.navigate(Routes.SETTINGS) })
                            DropdownMenuItem(text = { Text("Sort: Name") }, onClick = { menuOpen = false; viewModel.setSort(HostSort.NAME) })
                            DropdownMenuItem(text = { Text("Sort: Recent") }, onClick = { menuOpen = false; viewModel.setSort(HostSort.RECENT) })
                            DropdownMenuItem(text = { Text("Sort: Group") }, onClick = { menuOpen = false; viewModel.setSort(HostSort.GROUP) })
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navController.navigate(Routes.HostEdit.build(null)) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New host") },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (searching) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    label = { Text("Search hosts") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (state.hosts.isEmpty()) {
                EmptyHosts()
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp)) {
                    items(state.hosts, key = { it.id }) { host ->
                        val groupColor = state.groups.firstOrNull { it.id == host.groupId }?.colorArgb
                        HostRow(
                            host = host,
                            groupColor = groupColor?.let { Color(it) },
                            onConnect = {
                                val sid = viewModel.newSessionId()
                                navController.navigate(Routes.Terminal.build(sid, host.id))
                            },
                            onEdit = { navController.navigate(Routes.HostEdit.build(host.id)) },
                            onDelete = { viewModel.delete(host) },
                            onDuplicate = { viewModel.duplicate(host) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HostRow(
    host: HostEntity,
    groupColor: Color?,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onConnect).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(groupColor ?: MaterialTheme.colorScheme.outline))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(host.nickname.ifBlank { host.address }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val user = if (host.username.isNotBlank()) "${host.username}@" else ""
            Text("$user${host.address}:${host.port}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            host.lastConnected?.let {
                Text(
                    DateUtils.getRelativeTimeSpanString(it).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        AssistChip(onClick = onConnect, label = { Text(host.protocol.displayName) }, modifier = Modifier.padding(end = 4.dp))
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Options") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit() })
                DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; onDuplicate() })
                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

@Composable
private fun EmptyHosts() {
    Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Dns, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Text("No hosts yet", style = MaterialTheme.typography.titleLarge)
            Text("Tap “New host” to add your first connection.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

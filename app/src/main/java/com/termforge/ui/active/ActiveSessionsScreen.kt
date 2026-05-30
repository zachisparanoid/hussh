package com.termforge.ui.active

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavHostController
import com.termforge.nav.Routes
import com.termforge.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ActiveSessionsViewModel @Inject constructor(private val sessionManager: SessionManager) : ViewModel() {
    val sessions = sessionManager.sessions
    fun disconnect(id: String) = sessionManager.disconnect(id)
    fun disconnectAll() = sessionManager.disconnectAll()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveSessionsScreen(navController: NavHostController, viewModel: ActiveSessionsViewModel = hiltViewModel()) {
    val sessions by viewModel.sessions.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Active sessions") },
                navigationIcon = { IconButton(onClick = { navController.navigateUp() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (sessions.isEmpty()) {
                Text("No active sessions.", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(sessions, key = { it.sessionId }) { conn ->
                        val status by conn.status.collectAsState()
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(conn.title, style = MaterialTheme.typography.titleMedium)
                                Text("${conn.protocol.displayName} • $status", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { navController.navigate(Routes.Terminal.build(conn.sessionId)) }) { Icon(Icons.Default.OpenInNew, "Open") }
                            IconButton(onClick = { viewModel.disconnect(conn.sessionId) }) { Icon(Icons.Default.Close, "Disconnect") }
                        }
                    }
                }
                Button(onClick = { viewModel.disconnectAll(); navController.navigateUp() }, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Disconnect all")
                }
            }
        }
    }
}

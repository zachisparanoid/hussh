package com.hussh.ui.keybar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.hussh.keybar.KeyBarRow
import com.hussh.keybar.KeyBarState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun KeyBarEditorScreen(navController: NavHostController, viewModel: KeyBarEditorViewModel = hiltViewModel()) {
    val layout by viewModel.layout.collectAsState()
    val previewState = remember { KeyBarState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isHostScope) "Key bar (this host)" else "Key bar (global)") },
                navigationIcon = { IconButton(onClick = { navController.navigateUp() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { viewModel.save { navController.navigateUp() } }) { Icon(Icons.Default.Check, "Save") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text("Preview", Modifier.padding(start = 16.dp, top = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            KeyBarRow(
                layout = layout, state = previewState,
                appCursor = { false }, backspaceByte = { 0x7F }, onSend = {},
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = { viewModel.resetToDefault() }, modifier = Modifier.padding(16.dp)) {
                Text("Reset order to default")
            }
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(layout.buttons, key = { _, b -> b.id }) { index, button ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(button.label, Modifier.weight(1f))
                        Text(button.type.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        IconButton(onClick = { viewModel.moveUp(index) }, enabled = index > 0) { Icon(Icons.Default.ArrowUpward, "Up") }
                        IconButton(onClick = { viewModel.moveDown(index) }, enabled = index < layout.buttons.lastIndex) { Icon(Icons.Default.ArrowDownward, "Down") }
                        IconButton(onClick = { viewModel.remove(button) }) { Icon(Icons.Default.Delete, "Remove") }
                    }
                }
            }
            val available = viewModel.availableToAdd()
            if (available.isNotEmpty()) {
                Text("Add keys", Modifier.padding(start = 16.dp, top = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                FlowRow(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    available.forEach { b ->
                        AssistChip(onClick = { viewModel.add(b) }, label = { Text(b.label) })
                    }
                }
            }
        }
    }
}

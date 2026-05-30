package com.termforge.ui.snippets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.termforge.data.db.entities.SnippetEntity
import com.termforge.data.repo.SnippetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SnippetsViewModel @Inject constructor(private val repo: SnippetRepository) : ViewModel() {
    val snippets = repo.observeSnippets().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun save(s: SnippetEntity) = viewModelScope.launch { repo.saveSnippet(s) }
    fun delete(s: SnippetEntity) = viewModelScope.launch { repo.deleteSnippet(s) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnippetsScreen(navController: NavHostController, viewModel: SnippetsViewModel = hiltViewModel()) {
    val snippets by viewModel.snippets.collectAsState()
    var editing by remember { mutableStateOf<SnippetEntity?>(null) }
    var dialogOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Snippets") },
                navigationIcon = { IconButton(onClick = { navController.navigateUp() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; dialogOpen = true }) { Icon(Icons.Default.Add, "New snippet") }
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            items(snippets, key = { it.id }) { s ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.name, style = MaterialTheme.typography.titleMedium)
                        Text(s.content, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    }
                    TextButton(onClick = { editing = s; dialogOpen = true }) { Text("Edit") }
                    IconButton(onClick = { viewModel.delete(s) }) { Icon(Icons.Default.Delete, "Delete") }
                }
            }
        }
    }

    if (dialogOpen) {
        SnippetDialog(editing) { result ->
            if (result != null) viewModel.save(result)
            dialogOpen = false
        }
    }
}

@Composable
private fun SnippetDialog(existing: SnippetEntity?, onDone: (SnippetEntity?) -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var content by remember { mutableStateOf(existing?.content ?: "") }
    var newline by remember { mutableStateOf(existing?.appendNewline ?: true) }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text(if (existing == null) "New snippet" else "Edit snippet") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(content, { content = it }, label = { Text("Command") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Checkbox(checked = newline, onCheckedChange = { newline = it })
                    Text("Append newline (run on insert)")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDone((existing ?: SnippetEntity(name = "", content = "")).copy(name = name, content = content, appendNewline = newline))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("Cancel") } },
    )
}

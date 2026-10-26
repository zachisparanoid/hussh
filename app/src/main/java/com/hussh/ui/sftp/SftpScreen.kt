package com.hussh.ui.sftp

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.hussh.transport.sftp.RemoteFile
import com.hussh.ui.components.PromptDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SftpScreen(navController: NavHostController, viewModel: SftpViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var mkdirOpen by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<RemoteFile?>(null) }
    var chmodTarget by remember { mutableStateOf<RemoteFile?>(null) }
    var activePrompt by remember { mutableStateOf<com.hussh.session.SessionPrompt?>(null) }

    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.prompts.collect { activePrompt = it } }

    val uploadPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "upload.bin"
            viewModel.upload(uri, name)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Files", maxLines = 1) },
                navigationIcon = { IconButton(onClick = { navController.navigateUp() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { viewModel.up() }) { Icon(Icons.Default.ArrowUpward, "Up") }
                    IconButton(onClick = { viewModel.refresh() }) { Icon(Icons.Default.Refresh, "Refresh") }
                    IconButton(onClick = { mkdirOpen = true }) { Icon(Icons.Default.CreateNewFolder, "New folder") }
                    IconButton(onClick = { uploadPicker.launch(arrayOf("*/*")) }) { Icon(Icons.Default.Upload, "Upload") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(state.path, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            state.transfer?.let { t ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text("${if (t.isUpload) "Uploading" else "Downloading"} ${t.name} — ${t.bytes / 1024} KB", style = MaterialTheme.typography.labelSmall)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    TextButton(onClick = { viewModel.cancelTransfer() }) { Text("Cancel") }
                }
            }
            when {
                state.connecting -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(Modifier.padding(top = 64.dp)); Text("Connecting…", Modifier.padding(8.dp))
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.files, key = { it.path }) { file ->
                        SftpRow(file, onOpen = { viewModel.open(file) }, onDownload = { viewModel.download(file) }, onRename = { renameTarget = file }, onDelete = { viewModel.delete(file) }, onChmod = { chmodTarget = file })
                    }
                }
            }
        }
    }

    if (mkdirOpen) TextPromptDialog("New folder", "Name") { name -> if (name != null && name.isNotBlank()) viewModel.mkdir(name); mkdirOpen = false }
    renameTarget?.let { f -> TextPromptDialog("Rename", f.name, initial = f.name) { name -> if (name != null && name.isNotBlank()) viewModel.rename(f, name); renameTarget = null } }
    chmodTarget?.let { f -> TextPromptDialog("chmod (octal)", "e.g. 644", initial = Integer.toOctalString(f.permissions and 0x1FF)) { v -> v?.toIntOrNull(8)?.let { viewModel.chmod(f, it) }; chmodTarget = null } }
    state.error?.let { msg ->
        AlertDialog(onDismissRequest = viewModel::clearError, confirmButton = { TextButton(onClick = viewModel::clearError) { Text("OK") } }, title = { Text("Error") }, text = { Text(msg) })
    }
    activePrompt?.let { PromptDialog(it) { activePrompt = null } }
}

@Composable
private fun SftpRow(file: RemoteFile, onOpen: () -> Unit, onDownload: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit, onChmod: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { if (file.isDirectory) onOpen() else onDownload() }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (file.isDirectory) Icons.Default.Folder else Icons.Default.Description, null)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val size = if (file.isDirectory) "dir" else "${file.size / 1024} KB"
            Text("$size • ${Integer.toOctalString(file.permissions and 0x1FF)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Options") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (!file.isDirectory) DropdownMenuItem(text = { Text("Download") }, onClick = { menu = false; onDownload() })
                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text("chmod") }, onClick = { menu = false; onChmod() })
                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

@Composable
private fun TextPromptDialog(title: String, label: String, initial: String = "", onDone: (String?) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text(title) },
        text = { OutlinedTextField(value, { value = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onDone(value) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("Cancel") } },
    )
}

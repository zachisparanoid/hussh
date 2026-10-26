package com.hussh.ui.identities

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.hussh.data.db.KeyType
import com.hussh.ui.components.EnumDropdown

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentityScreen(navController: NavHostController, viewModel: IdentityViewModel = hiltViewModel()) {
    val identities by viewModel.identities.collectAsState()
    val error by viewModel.error.collectAsState()
    var addOpen by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Identities") },
                navigationIcon = { IconButton(onClick = { navController.navigateUp() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { addOpen = true }) { Icon(Icons.Default.Add, "Add identity") } },
    ) { padding ->
        if (identities.isEmpty()) {
            Column(Modifier.padding(padding).fillMaxSize().padding(32.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.VpnKey, null, Modifier.padding(top = 64.dp))
                Text("No identities", style = MaterialTheme.typography.titleLarge)
                Text("Generate or import an SSH key to use key-based auth.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                items(identities, key = { it.id }) { id ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(id.name, style = MaterialTheme.typography.titleMedium)
                            Text(id.fingerprintSha256, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AssistChip(onClick = {}, label = { Text(id.keyType.displayName) })
                                if (id.passphraseEnc != null) AssistChip(onClick = {}, label = { Text("passphrase saved") })
                            }
                        }
                        IconButton(onClick = { clipboard.setText(AnnotatedString(id.publicKey)) }) { Icon(Icons.Default.ContentCopy, "Copy public key") }
                        IconButton(onClick = {
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, id.publicKey) }, "Share public key"))
                        }) { Icon(Icons.Default.Share, "Share public key") }
                        IconButton(onClick = { viewModel.delete(id) }) { Icon(Icons.Default.Delete, "Delete") }
                    }
                }
            }
        }
    }

    if (addOpen) AddIdentityDialog(viewModel) { addOpen = false }
    error?.let { msg ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            confirmButton = { TextButton(onClick = viewModel::clearError) { Text("OK") } },
            title = { Text("Heads up") }, text = { Text(msg) },
        )
    }
}

@Composable
private fun AddIdentityDialog(viewModel: IdentityViewModel, onDismiss: () -> Unit) {
    var importing by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(KeyType.ED25519) }
    var pem by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var remember0 by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (importing) "Import key" else "Generate key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !importing, onClick = { importing = false }, label = { Text("Generate") })
                    FilterChip(selected = importing, onClick = { importing = true }, label = { Text("Import") })
                }
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (importing) {
                    OutlinedTextField(pem, { pem = it }, label = { Text("Paste private key (PEM/OpenSSH)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(passphrase, { passphrase = it }, label = { Text("Passphrase (if encrypted)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(checked = remember0, onCheckedChange = { remember0 = it })
                        Text("Remember passphrase (encrypted)")
                    }
                } else {
                    EnumDropdown("Type", type, KeyType.entries, { it.displayName }, { type = it }, Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (importing) viewModel.import(name, pem, passphrase.ifBlank { null }, remember0)
                else viewModel.generate(name, type, null)
                onDismiss()
            }) { Text(if (importing) "Import" else "Generate") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

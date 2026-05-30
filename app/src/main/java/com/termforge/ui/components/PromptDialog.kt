package com.termforge.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.termforge.session.SessionPrompt
import com.termforge.transport.HostKeyDecision
import com.termforge.transport.HostKeyStatus

/**
 * Renders the dialog for an interactive [SessionPrompt] and completes its deferred directly — so any
 * screen (terminal, SFTP) can host prompts without a dedicated ViewModel method.
 */
@Composable
fun PromptDialog(prompt: SessionPrompt, onDismiss: () -> Unit) {
    when (prompt) {
        is SessionPrompt.HostKey -> HostKeyDialog(prompt, onDismiss)
        is SessionPrompt.Password -> SecretDialog(
            "Password", prompt.label,
            { prompt.response.complete(it); onDismiss() },
            { prompt.response.complete(null); onDismiss() },
        )
        is SessionPrompt.Passphrase -> SecretDialog(
            "Key passphrase", "Unlock “${prompt.keyName}”",
            { prompt.response.complete(it); onDismiss() },
            { prompt.response.complete(null); onDismiss() },
        )
        is SessionPrompt.KeyboardInteractive -> KbiDialog(prompt, onDismiss)
    }
}

@Composable
private fun HostKeyDialog(prompt: SessionPrompt.HostKey, onDismiss: () -> Unit) {
    val req = prompt.request
    val mismatch = req.status == HostKeyStatus.MISMATCH
    AlertDialog(
        onDismissRequest = { prompt.response.complete(HostKeyDecision.REJECT); onDismiss() },
        title = { Text(if (mismatch) "⚠ HOST KEY CHANGED" else "Unknown host key") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (mismatch) {
                    Text(
                        "The host key for ${req.host}:${req.port} does NOT match the one you previously " +
                            "trusted. This may indicate a man-in-the-middle attack.",
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text("Previous: ${req.previousFingerprint}", Modifier.padding(top = 8.dp))
                } else {
                    Text("First connection to ${req.host}:${req.port}. Verify the fingerprint before trusting it.")
                }
                Text("Type: ${req.keyType}", Modifier.padding(top = 8.dp))
                Text("SHA256: ${req.fingerprintSha256}")
                Text("MD5:    ${req.fingerprintMd5}")
            }
        },
        confirmButton = {
            TextButton(onClick = { prompt.response.complete(HostKeyDecision.ACCEPT); onDismiss() }) {
                Text(if (mismatch) "Update & connect" else "Trust & connect")
            }
        },
        dismissButton = {
            TextButton(onClick = { prompt.response.complete(HostKeyDecision.REJECT); onDismiss() }) { Text("Abort") }
        },
    )
}

@Composable
private fun SecretDialog(title: String, label: String, onConfirm: (String) -> Unit, onCancel: () -> Unit) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column {
                Text(label, Modifier.padding(bottom = 8.dp))
                OutlinedTextField(
                    value, { value = it }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun KbiDialog(prompt: SessionPrompt.KeyboardInteractive, onDismiss: () -> Unit) {
    val answers = remember { mutableStateListOf(*Array(prompt.prompts.size) { "" }) }
    AlertDialog(
        onDismissRequest = { prompt.response.complete(null); onDismiss() },
        title = { Text(prompt.name.ifBlank { "Authentication" }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (prompt.instruction.isNotBlank()) Text(prompt.instruction, Modifier.padding(bottom = 8.dp))
                prompt.prompts.forEachIndexed { i, p ->
                    OutlinedTextField(
                        answers[i], { answers[i] = it }, label = { Text(p.prompt) }, singleLine = true,
                        visualTransformation = if (p.echo) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { prompt.response.complete(answers.toList()); onDismiss() }) { Text("Submit") } },
        dismissButton = { TextButton(onClick = { prompt.response.complete(null); onDismiss() }) { Text("Cancel") } },
    )
}

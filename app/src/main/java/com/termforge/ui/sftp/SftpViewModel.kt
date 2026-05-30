package com.termforge.ui.sftp

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.termforge.data.db.TransferDirection
import com.termforge.data.db.TransferStatus
import com.termforge.data.db.entities.TransferLogEntity
import com.termforge.data.repo.TransferLogRepository
import com.termforge.nav.Routes
import com.termforge.session.SessionManager
import com.termforge.transport.HostKeyRequest
import com.termforge.transport.KbPrompt
import com.termforge.transport.TransportCallbacks
import com.termforge.transport.TransportFactory
import com.termforge.transport.sftp.RemoteFile
import com.termforge.transport.sftp.SftpSession
import com.termforge.session.PromptHub
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class TransferProgress(val name: String, val bytes: Long, val isUpload: Boolean)

data class SftpUiState(
    val connecting: Boolean = true,
    val path: String = "/",
    val files: List<RemoteFile> = emptyList(),
    val error: String? = null,
    val transfer: TransferProgress? = null,
)

@HiltViewModel
class SftpViewModel @Inject constructor(
    savedState: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
    private val transportFactory: TransportFactory,
    private val promptHub: PromptHub,
    private val transferLog: TransferLogRepository,
) : ViewModel() {

    private val hostId: Long = savedState.get<Long>(Routes.Sftp.ARG_HOST) ?: -1L
    val sessionId: String = sessionManager.newSessionId()
    val prompts = promptHub.prompts.filter { it.sessionId == sessionId }

    private val callbacks = object : TransportCallbacks {
        override suspend fun verifyHostKey(request: HostKeyRequest) = promptHub.requestHostKey(sessionId, request)
        override suspend fun answerKeyboardInteractive(name: String, instruction: String, prompts: List<KbPrompt>) =
            promptHub.requestKeyboardInteractive(sessionId, name, instruction, prompts)
    }

    private var session: SftpSession? = null
    private var transferJob: Job? = null

    val state = MutableStateFlow(SftpUiState())

    init { connect() }

    private fun connect() = viewModelScope.launch {
        runCatching {
            val config = sessionManager.buildConfig(hostId, sessionId)
            val s = transportFactory.createSftp(config, callbacks)
            s.connect()
            session = s
            val home = s.home()
            state.value = state.value.copy(connecting = false, path = home, files = s.list(home))
        }.onFailure { state.value = state.value.copy(connecting = false, error = it.message) }
    }

    fun open(file: RemoteFile) {
        if (file.isDirectory) navigateTo(file.path)
    }

    fun navigateTo(path: String) = viewModelScope.launch {
        val s = session ?: return@launch
        runCatching { s.list(path) }
            .onSuccess { state.value = state.value.copy(path = path, files = it, error = null) }
            .onFailure { state.value = state.value.copy(error = it.message) }
    }

    fun up() {
        val parent = state.value.path.trimEnd('/').substringBeforeLast('/', "")
        navigateTo(if (parent.isEmpty()) "/" else parent)
    }

    fun refresh() = navigateTo(state.value.path)

    fun mkdir(name: String) = viewModelScope.launch {
        val s = session ?: return@launch
        runCatching { s.mkdir(join(state.value.path, name)) }.onSuccess { refresh() }.onFailure { state.value = state.value.copy(error = it.message) }
    }

    fun delete(file: RemoteFile) = viewModelScope.launch {
        val s = session ?: return@launch
        runCatching { if (file.isDirectory) s.deleteDir(file.path) else s.deleteFile(file.path) }
            .onSuccess { refresh() }.onFailure { state.value = state.value.copy(error = it.message) }
    }

    fun rename(file: RemoteFile, newName: String) = viewModelScope.launch {
        val s = session ?: return@launch
        runCatching { s.rename(file.path, join(state.value.path, newName)) }.onSuccess { refresh() }.onFailure { state.value = state.value.copy(error = it.message) }
    }

    fun chmod(file: RemoteFile, mode: Int) = viewModelScope.launch {
        val s = session ?: return@launch
        runCatching { s.chmod(file.path, mode) }.onSuccess { refresh() }.onFailure { state.value = state.value.copy(error = it.message) }
    }

    fun download(file: RemoteFile) {
        val s = session ?: return
        transferJob = viewModelScope.launch {
            val dir = File(context.getExternalFilesDir(null), "downloads").apply { mkdirs() }
            val target = File(dir, file.name)
            runCatching {
                target.outputStream().use { out ->
                    s.download(file.path, out) { bytes ->
                        state.value = state.value.copy(transfer = TransferProgress(file.name, bytes, false))
                    }
                }
                log(TransferDirection.DOWNLOAD, file.path, file.name, target.length(), TransferStatus.DONE)
            }.onFailure {
                log(TransferDirection.DOWNLOAD, file.path, file.name, 0, TransferStatus.FAILED)
                state.value = state.value.copy(error = "Download failed: ${it.message}")
            }
            state.value = state.value.copy(transfer = null)
        }
    }

    fun upload(uri: Uri, name: String) {
        val s = session ?: return
        transferJob = viewModelScope.launch {
            val remote = join(state.value.path, name)
            runCatching {
                (context.contentResolver.openInputStream(uri) ?: error("Cannot open file")).use { input ->
                    s.upload(input, remote) { bytes ->
                        state.value = state.value.copy(transfer = TransferProgress(name, bytes, true))
                    }
                }
                log(TransferDirection.UPLOAD, remote, name, 0, TransferStatus.DONE)
                refresh()
            }.onFailure {
                log(TransferDirection.UPLOAD, remote, name, 0, TransferStatus.FAILED)
                state.value = state.value.copy(error = "Upload failed: ${it.message}")
            }
            state.value = state.value.copy(transfer = null)
        }
    }

    fun cancelTransfer() { transferJob?.cancel(); state.value = state.value.copy(transfer = null) }

    fun clearError() { state.value = state.value.copy(error = null) }

    private suspend fun log(dir: TransferDirection, remote: String, local: String, bytes: Long, status: TransferStatus) {
        transferLog.record(TransferLogEntity(hostId = hostId, direction = dir, remotePath = remote, localName = local, bytes = bytes, status = status, timestamp = System.currentTimeMillis()))
    }

    private fun join(dir: String, name: String) = if (dir.endsWith("/")) "$dir$name" else "$dir/$name"

    override fun onCleared() {
        session?.close()
    }
}

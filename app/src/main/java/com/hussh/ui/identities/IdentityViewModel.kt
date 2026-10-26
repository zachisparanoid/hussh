package com.hussh.ui.identities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hussh.data.db.KeyType
import com.hussh.data.db.entities.IdentityEntity
import com.hussh.data.repo.IdentityRepository
import com.hussh.transport.ssh.KeyTools
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class IdentityViewModel @Inject constructor(
    private val repo: IdentityRepository,
    private val keyTools: KeyTools,
) : ViewModel() {

    val identities = repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val error = MutableStateFlow<String?>(null)
    fun clearError() { error.value = null }

    /** Generate a new key pair and store it. Heavy crypto runs off the main thread. */
    fun generate(name: String, type: KeyType, rememberPassphrase: String?) = viewModelScope.launch {
        runCatching {
            val material = withContext(Dispatchers.Default) { keyTools.generate(type, comment = name) }
            repo.save(
                name = name.ifBlank { "${type.displayName} key" },
                keyType = material.keyType,
                privateKeyPem = material.privateKeyPem,
                publicKey = material.publicKeyOpenSsh,
                fingerprintSha256 = material.fingerprintSha256,
                fingerprintMd5 = material.fingerprintMd5,
                passphrase = rememberPassphrase?.ifBlank { null },
                createdAt = System.currentTimeMillis(),
            )
        }.onFailure { error.value = "Could not generate key: ${it.message}" }
    }

    /** Import a pasted private key, deriving its public key + fingerprints. */
    fun import(name: String, pem: String, passphrase: String?, rememberPassphrase: Boolean) = viewModelScope.launch {
        runCatching {
            val material = withContext(Dispatchers.Default) { keyTools.import(pem, passphrase, comment = name) }
            repo.save(
                name = name.ifBlank { "Imported key" },
                keyType = material.keyType,
                privateKeyPem = material.privateKeyPem,
                publicKey = material.publicKeyOpenSsh,
                fingerprintSha256 = material.fingerprintSha256,
                fingerprintMd5 = material.fingerprintMd5,
                passphrase = if (rememberPassphrase) passphrase else null,
                createdAt = System.currentTimeMillis(),
            )
        }.onFailure { error.value = "Could not import key: ${it.message}" }
    }

    fun delete(identity: IdentityEntity) = viewModelScope.launch {
        if (repo.hostsUsing(identity.id) > 0) {
            error.value = "This identity is used by ${repo.hostsUsing(identity.id)} host(s)."
        } else {
            repo.delete(identity)
        }
    }
}

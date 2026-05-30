package com.termforge.ui.hostedit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.termforge.data.db.Protocol
import com.termforge.data.db.entities.HostEntity
import com.termforge.data.db.entities.PortForwardEntity
import com.termforge.data.repo.GroupRepository
import com.termforge.data.repo.HostRepository
import com.termforge.data.repo.IdentityRepository
import com.termforge.data.repo.PortForwardRepository
import com.termforge.nav.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HostEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val hostRepo: HostRepository,
    private val identityRepo: IdentityRepository,
    private val portForwardRepo: PortForwardRepository,
    groupRepo: GroupRepository,
) : ViewModel() {

    private val hostId: Long? = savedState.get<String>(Routes.HostEdit.ARG_ID)?.toLongOrNull()
    val isNew: Boolean = hostId == null

    val draft = MutableStateFlow(HostEntity(nickname = "", address = "", port = 22, protocol = Protocol.SSH))
    val password = MutableStateFlow("")
    val forwards = MutableStateFlow<List<PortForwardEntity>>(emptyList())

    val identities = identityRepo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val groups = groupRepo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        if (hostId != null) viewModelScope.launch {
            hostRepo.getById(hostId)?.let { draft.value = it }
            forwards.value = portForwardRepo.getForHost(hostId)
        }
    }

    fun update(transform: (HostEntity) -> HostEntity) { draft.value = transform(draft.value) }

    /** Switching protocol auto-fills the conventional port if the user hasn't customized it. */
    fun setProtocol(p: Protocol) {
        val cur = draft.value
        val portWasDefault = Protocol.entries.any { it.defaultPort == cur.port }
        draft.value = cur.copy(protocol = p, port = if (portWasDefault) p.defaultPort else cur.port)
    }

    fun addForward(pf: PortForwardEntity) { forwards.value = forwards.value + pf }
    fun removeForward(pf: PortForwardEntity) { forwards.value = forwards.value - pf }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        val id = hostRepo.save(draft.value, password.value.ifBlank { null })
        // Re-persist this host's forwards under the (possibly new) id.
        val existing = portForwardRepo.getForHost(id)
        existing.forEach { portForwardRepo.delete(it) }
        forwards.value.forEach { portForwardRepo.save(it.copy(id = 0, hostId = id)) }
        onDone()
    }
}

package com.hussh.ui.hosts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hussh.data.db.entities.GroupEntity
import com.hussh.data.db.entities.HostEntity
import com.hussh.data.repo.GroupRepository
import com.hussh.data.repo.HostRepository
import com.hussh.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HostSort { NAME, RECENT, GROUP }

data class HostListUiState(
    val hosts: List<HostEntity> = emptyList(),
    val groups: List<GroupEntity> = emptyList(),
    val query: String = "",
    val sort: HostSort = HostSort.NAME,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HostListViewModel @Inject constructor(
    private val hostRepo: HostRepository,
    private val groupRepo: GroupRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(HostSort.NAME)

    val activeSessions = sessionManager.sessions

    val uiState = combine(
        query.flatMapLatest { hostRepo.search(it) },
        groupRepo.observeAll(),
        query,
        sort,
    ) { hosts, groups, q, s ->
        val sorted = when (s) {
            HostSort.NAME -> hosts.sortedBy { it.nickname.lowercase() }
            HostSort.RECENT -> hosts.sortedByDescending { it.lastConnected ?: 0 }
            HostSort.GROUP -> hosts.sortedBy { it.groupId ?: Long.MAX_VALUE }
        }
        HostListUiState(sorted, groups, q, s)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HostListUiState())

    fun setQuery(q: String) { query.value = q }
    fun setSort(s: HostSort) { sort.value = s }

    /** Create a fresh session id for navigating to the terminal (the terminal VM does the connect). */
    fun newSessionId(): String = sessionManager.newSessionId()

    fun delete(host: HostEntity) = viewModelScope.launch { hostRepo.delete(host.id) }

    fun duplicate(host: HostEntity) = viewModelScope.launch {
        hostRepo.save(host.copy(id = 0, nickname = "${host.nickname} (copy)", savePassword = false, inlinePasswordEnc = null))
    }
}

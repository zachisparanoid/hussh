package com.hussh.ui.keybar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hussh.data.repo.HostRepository
import com.hussh.data.repo.KeyBarRepository
import com.hussh.keybar.DefaultKeyBar
import com.hussh.keybar.KeyBarButtonDef
import com.hussh.keybar.KeyBarLayout
import com.hussh.nav.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class KeyBarEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val keyBarRepo: KeyBarRepository,
    private val hostRepo: HostRepository,
) : ViewModel() {

    val isHostScope: Boolean = savedState.get<String>(Routes.KeyBarEditor.ARG_SCOPE) == "HOST"
    private val hostId: Long = savedState.get<Long>(Routes.KeyBarEditor.ARG_HOST_ID) ?: -1L

    val layout = MutableStateFlow(DefaultKeyBar.layout)

    init {
        viewModelScope.launch {
            layout.value = if (isHostScope) {
                keyBarRepo.getHostLayout(hostId) ?: keyBarRepo.effectiveLayout(null)
            } else {
                keyBarRepo.effectiveLayout(null)
            }
        }
    }

    /** Buttons in the palette that aren't already on the bar. */
    fun availableToAdd(): List<KeyBarButtonDef> {
        val present = layout.value.buttons.map { it.id }.toSet()
        return DefaultKeyBar.palette.filter { it.id !in present }
    }

    fun moveUp(index: Int) = reorder(index, index - 1)
    fun moveDown(index: Int) = reorder(index, index + 1)

    private fun reorder(from: Int, to: Int) {
        val list = layout.value.buttons.toMutableList()
        if (from in list.indices && to in list.indices) {
            list.add(to, list.removeAt(from))
            layout.value = KeyBarLayout(list)
        }
    }

    fun remove(button: KeyBarButtonDef) {
        layout.value = KeyBarLayout(layout.value.buttons.filterNot { it.id == button.id })
    }

    fun add(button: KeyBarButtonDef) {
        layout.value = KeyBarLayout(layout.value.buttons + button)
    }

    fun resetToDefault() { layout.value = DefaultKeyBar.layout }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        if (isHostScope) {
            val id = keyBarRepo.saveHostLayout(hostId, layout.value)
            hostRepo.getById(hostId)?.let { hostRepo.save(it.copy(customKeyBarId = id)) }
        } else {
            keyBarRepo.saveGlobal(layout.value)
        }
        onDone()
    }
}

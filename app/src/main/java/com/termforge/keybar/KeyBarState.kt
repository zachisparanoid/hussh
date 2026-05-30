package com.termforge.keybar

import androidx.compose.runtime.mutableStateMapOf

/** Visual/behavioral state of a sticky modifier. */
enum class ModState { OFF, ONE_SHOT, LATCHED }

/**
 * Holds the live modifier state for one terminal session's helper bar. Backed by a Compose snapshot
 * map, so the bar recomposes when states change. The three states map directly to the spec's
 * required visuals: off / one-shot-armed / latched.
 *
 * Transition rules:
 * - **tap**: OFF → ONE_SHOT, ONE_SHOT → OFF (cancel), LATCHED → OFF.
 * - **longPress** (the ≥350 ms hold, detected upstream): toggles LATCHED.
 * - **consumeAfterKey**: clears ONE_SHOT modifiers after a literal key is sent; latched persist.
 */
class KeyBarState {
    private val states = mutableStateMapOf<ModifierKey, ModState>()

    fun stateOf(mod: ModifierKey): ModState = states[mod] ?: ModState.OFF

    fun tap(mod: ModifierKey) {
        states[mod] = if (stateOf(mod) == ModState.OFF) ModState.ONE_SHOT else ModState.OFF
    }

    fun longPress(mod: ModifierKey) {
        states[mod] = if (stateOf(mod) == ModState.LATCHED) ModState.OFF else ModState.LATCHED
    }

    fun consumeAfterKey() {
        for (mod in ModifierKey.entries) {
            if (states[mod] == ModState.ONE_SHOT) states[mod] = ModState.OFF
        }
    }

    fun modifiers(): ModifierSet = ModifierSet(
        ctrl = stateOf(ModifierKey.CTRL) != ModState.OFF,
        alt = stateOf(ModifierKey.ALT) != ModState.OFF,
        shift = stateOf(ModifierKey.SHIFT) != ModState.OFF,
        meta = stateOf(ModifierKey.SUPER) != ModState.OFF,
    )

    fun reset() = states.clear()
}

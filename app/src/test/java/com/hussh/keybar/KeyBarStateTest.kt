package com.hussh.keybar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The signature-feature touch semantics. Timing (350 ms hold) is the gesture layer's job; here we
 * verify the OFF / ONE_SHOT / LATCHED transitions, one-shot consumption, and latch persistence.
 */
class KeyBarStateTest {

    @Test fun `modifiers start OFF`() {
        val s = KeyBarState()
        assertEquals(ModState.OFF, s.stateOf(ModifierKey.CTRL))
        val m = s.modifiers()
        assertFalse(m.ctrl || m.alt || m.shift || m.meta)
    }

    @Test fun `tap arms one-shot`() {
        val s = KeyBarState()
        s.tap(ModifierKey.CTRL)
        assertEquals(ModState.ONE_SHOT, s.stateOf(ModifierKey.CTRL))
        assertTrue(s.modifiers().ctrl)
    }

    @Test fun `tapping an armed modifier cancels it`() {
        val s = KeyBarState()
        s.tap(ModifierKey.CTRL)
        s.tap(ModifierKey.CTRL)
        assertEquals(ModState.OFF, s.stateOf(ModifierKey.CTRL))
    }

    @Test fun `long press latches`() {
        val s = KeyBarState()
        s.longPress(ModifierKey.SHIFT)
        assertEquals(ModState.LATCHED, s.stateOf(ModifierKey.SHIFT))
    }

    @Test fun `long press again releases a latch`() {
        val s = KeyBarState()
        s.longPress(ModifierKey.SHIFT)
        s.longPress(ModifierKey.SHIFT)
        assertEquals(ModState.OFF, s.stateOf(ModifierKey.SHIFT))
    }

    @Test fun `one-shot is consumed after a key is sent`() {
        val s = KeyBarState()
        s.tap(ModifierKey.CTRL)
        s.consumeAfterKey()
        assertEquals(ModState.OFF, s.stateOf(ModifierKey.CTRL))
    }

    @Test fun `latched modifier survives key sends`() {
        val s = KeyBarState()
        s.longPress(ModifierKey.ALT)
        s.consumeAfterKey()
        s.consumeAfterKey()
        assertEquals(ModState.LATCHED, s.stateOf(ModifierKey.ALT))
        assertTrue(s.modifiers().alt)
    }

    @Test fun `multiple modifiers compose`() {
        val s = KeyBarState()
        s.tap(ModifierKey.CTRL)
        s.longPress(ModifierKey.SHIFT)
        val m = s.modifiers()
        assertTrue(m.ctrl && m.shift)
        assertFalse(m.alt)
    }

    @Test fun `SUPER maps to the meta bit`() {
        val s = KeyBarState()
        s.tap(ModifierKey.SUPER)
        assertTrue(s.modifiers().meta)
    }

    @Test fun `reset clears everything`() {
        val s = KeyBarState()
        s.tap(ModifierKey.CTRL)
        s.longPress(ModifierKey.ALT)
        s.reset()
        assertEquals(ModState.OFF, s.stateOf(ModifierKey.CTRL))
        assertEquals(ModState.OFF, s.stateOf(ModifierKey.ALT))
    }
}

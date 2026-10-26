package com.hussh.keybar

import org.junit.Assert.assertArrayEquals
import org.junit.Test

private const val ESC = 0x1B

private fun b(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
private fun csi(vararg tail: Int) = b(ESC, '['.code, *tail)

class AnsiComposerTest {
    private val c = AnsiComposer()
    private fun mods(ctrl: Boolean = false, alt: Boolean = false, shift: Boolean = false, meta: Boolean = false) =
        ModifierSet(ctrl, alt, shift, meta)

    // --- Text + Ctrl/Alt ---
    @Test fun `ctrl c is 0x03`() =
        assertArrayEquals(b(0x03), c.composeText("c", mods(ctrl = true)))

    @Test fun `ctrl a is 0x01`() =
        assertArrayEquals(b(0x01), c.composeText("a", mods(ctrl = true)))

    @Test fun `ctrl open-bracket is ESC`() =
        assertArrayEquals(b(0x1B), c.composeText("[", mods(ctrl = true)))

    @Test fun `alt x is ESC then x`() =
        assertArrayEquals(b(ESC, 'x'.code), c.composeText("x", mods(alt = true)))

    @Test fun `plain text passes through`() =
        assertArrayEquals(b('a'.code), c.composeText("a", mods()))

    @Test fun `shift does not alter a literal character`() =
        assertArrayEquals(b('/'.code), c.composeText("/", mods(shift = true)))

    // --- Arrows ---
    @Test fun `up arrow in normal mode is CSI A`() =
        assertArrayEquals(csi('A'.code), c.composeSpecial(SpecialKey.ARROW_UP, mods(), appCursor = false))

    @Test fun `up arrow in application-cursor mode is SS3 A`() =
        assertArrayEquals(b(ESC, 'O'.code, 'A'.code), c.composeSpecial(SpecialKey.ARROW_UP, mods(), appCursor = true))

    @Test fun `ctrl up is CSI 1 5 A`() =
        assertArrayEquals(csi('1'.code, ';'.code, '5'.code, 'A'.code),
            c.composeSpecial(SpecialKey.ARROW_UP, mods(ctrl = true), appCursor = true))

    @Test fun `shift up is CSI 1 2 A`() =
        assertArrayEquals(csi('1'.code, ';'.code, '2'.code, 'A'.code),
            c.composeSpecial(SpecialKey.ARROW_UP, mods(shift = true), appCursor = false))

    // --- Tab / Esc / Enter ---
    @Test fun `tab is 0x09`() =
        assertArrayEquals(b(0x09), c.composeSpecial(SpecialKey.TAB, mods(), appCursor = false))

    @Test fun `shift tab is CSI Z`() =
        assertArrayEquals(csi('Z'.code), c.composeSpecial(SpecialKey.TAB, mods(shift = true), appCursor = false))

    @Test fun `esc is 0x1B`() =
        assertArrayEquals(b(0x1B), c.composeSpecial(SpecialKey.ESC, mods(), appCursor = false))

    @Test fun `enter is carriage return`() =
        assertArrayEquals(b(0x0D), c.composeSpecial(SpecialKey.ENTER, mods(), appCursor = false))

    // --- Home / End / Page / Delete ---
    @Test fun `home normal is CSI H, app is SS3 H`() {
        assertArrayEquals(csi('H'.code), c.composeSpecial(SpecialKey.HOME, mods(), appCursor = false))
        assertArrayEquals(b(ESC, 'O'.code, 'H'.code), c.composeSpecial(SpecialKey.HOME, mods(), appCursor = true))
    }

    @Test fun `end normal is CSI F`() =
        assertArrayEquals(csi('F'.code), c.composeSpecial(SpecialKey.END, mods(), appCursor = false))

    @Test fun `page up is CSI 5 tilde`() =
        assertArrayEquals(csi('5'.code, '~'.code), c.composeSpecial(SpecialKey.PAGE_UP, mods(), appCursor = false))

    @Test fun `ctrl page up is CSI 5 5 tilde`() =
        assertArrayEquals(csi('5'.code, ';'.code, '5'.code, '~'.code),
            c.composeSpecial(SpecialKey.PAGE_UP, mods(ctrl = true), appCursor = false))

    @Test fun `delete is CSI 3 tilde`() =
        assertArrayEquals(csi('3'.code, '~'.code), c.composeSpecial(SpecialKey.DELETE, mods(), appCursor = false))

    // --- Backspace (configurable byte) ---
    @Test fun `backspace defaults to DEL`() =
        assertArrayEquals(b(0x7F), c.composeSpecial(SpecialKey.BACKSPACE, mods(), appCursor = false))

    @Test fun `backspace honors BS byte override`() =
        assertArrayEquals(b(0x08), c.composeSpecial(SpecialKey.BACKSPACE, mods(), appCursor = false, backspaceByte = 0x08))
}

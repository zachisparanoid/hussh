package com.hussh.session

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scanner is OSC-777-aware rather than a fixed-string match: it triggers on any
 * `ESC ] 777 ; … hussh-attn … <BEL|ST>` sequence. That makes detection robust to whichever exact form
 * Claude Code's hook `terminalSequence` ends up emitting (bare tag vs. the standard
 * `notify;title;body` form, BEL- vs. ST-terminated) — all contain the unique `hussh-attn` tag.
 */
class AttentionMarkerScannerTest {
    private val ESC = ""
    private val BEL = ""
    private val ST = "\\" // String Terminator: ESC backslash
    private fun bytes(s: String) = s.toByteArray(Charsets.US_ASCII)

    /** The canonical marker the server hook emits (standard OSC 777 notify form). */
    private val canonical = "$ESC]777;notify;Hussh;hussh-attn$BEL"

    @Test fun `plain output passes through untouched and undetected`() {
        val r = AttentionMarkerScanner().scan(bytes("ubuntu@vm:~$ ls -la"))
        assertEquals("ubuntu@vm:~$ ls -la", String(r.cleaned, Charsets.US_ASCII))
        assertFalse(r.detected)
    }

    @Test fun `canonical marker alone is detected and fully stripped`() {
        val r = AttentionMarkerScanner().scan(bytes(canonical))
        assertEquals(0, r.cleaned.size)
        assertTrue(r.detected)
    }

    @Test fun `bare-tag form is also detected (robust to body format)`() {
        val r = AttentionMarkerScanner().scan(bytes("$ESC]777;hussh-attn$BEL"))
        assertEquals(0, r.cleaned.size)
        assertTrue(r.detected)
    }

    @Test fun `ST-terminated marker is detected (robust to terminator)`() {
        val r = AttentionMarkerScanner().scan(bytes("$ESC]777;notify;X;hussh-attn$ST"))
        assertEquals(0, r.cleaned.size)
        assertTrue(r.detected)
    }

    @Test fun `marker embedded in output is stripped, surrounding bytes kept in order`() {
        val r = AttentionMarkerScanner().scan(bytes("done$canonical\$ "))
        assertArrayEquals(bytes("done\$ "), r.cleaned)
        assertTrue(r.detected)
    }

    @Test fun `marker split across two chunks is still caught`() {
        val s = AttentionMarkerScanner()
        val whole = bytes(canonical)
        val cut = 10 // mid-introducer/body boundary
        val first = s.scan(bytes("x") + whole.copyOfRange(0, cut))
        assertArrayEquals(bytes("x"), first.cleaned)
        assertFalse(first.detected)
        val second = s.scan(whole.copyOfRange(cut, whole.size) + bytes("y"))
        assertArrayEquals(bytes("y"), second.cleaned)
        assertTrue(second.detected)
    }

    @Test fun `a different OSC 777 passes through and is not detected`() {
        val other = bytes("$ESC]777;notify;OtherApp;hello$BEL")
        val r = AttentionMarkerScanner().scan(other)
        assertArrayEquals(other, r.cleaned)
        assertFalse(r.detected)
    }

    @Test fun `a normal CSI escape is untouched`() {
        val csi = bytes("$ESC[31mred$ESC[0m")
        val r = AttentionMarkerScanner().scan(csi)
        assertArrayEquals(csi, r.cleaned)
        assertFalse(r.detected)
    }

    @Test fun `unterminated overlong OSC 777 does not hang and eventually flushes`() {
        val s = AttentionMarkerScanner()
        // No terminator ever arrives; far longer than the cap → must not be held forever.
        val junk = bytes("$ESC]777;" + "A".repeat(400))
        val r = s.scan(junk)
        assertTrue(r.cleaned.size > 300) // flushed, not swallowed
        assertFalse(r.detected)
    }
}

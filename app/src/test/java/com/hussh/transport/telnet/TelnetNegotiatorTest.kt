package com.hussh.transport.telnet

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

private const val IAC = 0xFF
private const val DO = 0xFD
private const val DONT = 0xFE
private const val WILL = 0xFB
private const val WONT = 0xFC
private const val SB = 0xFA
private const val SE = 0xF0
private const val OPT_ECHO = 0x01
private const val OPT_SGA = 0x03
private const val OPT_TTYPE = 0x18
private const val OPT_NAWS = 0x1F
private const val TT_IS = 0x00
private const val TT_SEND = 0x01

private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

class TelnetNegotiatorTest {

    private fun negotiator() = TelnetNegotiator(termType = "xterm", cols = 80, rows = 24)

    @Test
    fun `plain data passes through untouched`() {
        val r = negotiator().process("hi".toByteArray(), 2)
        assertEquals("hi", String(r.data))
        assertEquals(0, r.response.size)
    }

    @Test
    fun `escaped IAC IAC collapses to a single 0xFF data byte`() {
        val r = negotiator().process(bytes(IAC, IAC, 'A'.code), 3)
        assertArrayEquals(bytes(0xFF, 'A'.code), r.data)
        assertEquals(0, r.response.size)
    }

    @Test
    fun `DO TTYPE is answered with WILL TTYPE`() {
        val r = negotiator().process(bytes(IAC, DO, OPT_TTYPE), 3)
        assertArrayEquals(bytes(IAC, WILL, OPT_TTYPE), r.response)
        assertEquals(0, r.data.size)
    }

    @Test
    fun `DO of an unsupported option is refused with WONT`() {
        val r = negotiator().process(bytes(IAC, DO, 0x63), 3)
        assertArrayEquals(bytes(IAC, WONT, 0x63), r.response)
    }

    @Test
    fun `repeated DO TTYPE only responds once (no negotiation loop)`() {
        val n = negotiator()
        val first = n.process(bytes(IAC, DO, OPT_TTYPE), 3)
        val second = n.process(bytes(IAC, DO, OPT_TTYPE), 3)
        assertArrayEquals(bytes(IAC, WILL, OPT_TTYPE), first.response)
        assertEquals(0, second.response.size)
    }

    @Test
    fun `TTYPE SEND subnegotiation returns the terminal type`() {
        val n = negotiator()
        val r = n.process(bytes(IAC, SB, OPT_TTYPE, TT_SEND, IAC, SE), 6)
        val expected = bytes(IAC, SB, OPT_TTYPE, TT_IS) +
            "xterm".toByteArray() + bytes(IAC, SE)
        assertArrayEquals(expected, r.response)
    }

    @Test
    fun `DO NAWS triggers WILL NAWS plus a window-size subnegotiation`() {
        val r = negotiator().process(bytes(IAC, DO, OPT_NAWS), 3)
        val expected = bytes(IAC, WILL, OPT_NAWS) +
            bytes(IAC, SB, OPT_NAWS, 0, 80, 0, 24, IAC, SE)
        assertArrayEquals(expected, r.response)
    }

    @Test
    fun `server WILL ECHO is accepted with DO ECHO`() {
        val r = negotiator().process(bytes(IAC, WILL, OPT_ECHO), 3)
        assertArrayEquals(bytes(IAC, DO, OPT_ECHO), r.response)
    }

    @Test
    fun `a command split across two chunks is still parsed correctly`() {
        val n = negotiator()
        val a = n.process(bytes(IAC), 1)
        val b = n.process(bytes(DO, OPT_SGA), 2)
        assertEquals(0, a.response.size)
        assertArrayEquals(bytes(IAC, WILL, OPT_SGA), b.response)
    }

    @Test
    fun `nawsUpdate emits a window-size subnegotiation for the new size`() {
        val out = negotiator().nawsUpdate(132, 43)
        assertArrayEquals(bytes(IAC, SB, OPT_NAWS, 0, 132, 0, 43, IAC, SE), out)
    }
}

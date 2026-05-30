package com.termforge.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentEventParserTest {
    private val p = AgentEventParser()

    @Test fun `parses a full perm event`() {
        val e = p.parse(
            """{"v":1,"ts":100,"event":"notification","reason":"perm","instance":"%42",""" +
                """"socket":"/tmp/tmux-1000/default","target":"orchestrator:0.0","session":"orchestrator",""" +
                """"window":"0","window_name":"orch","cwd":"/p","cmd":"claude","prompt":["Allow?","1. Yes","2. No"]}""",
        )!!
        assertEquals("perm", e.reason)
        assertEquals("%42", e.instance)
        assertEquals("orchestrator", e.session)
        assertEquals("/tmp/tmux-1000/default", e.socket)
        assertEquals(3, e.prompt.size)
        assertEquals(100L, e.tsSec)
    }

    @Test fun `parses a non-tmux event with null instance`() {
        val e = p.parse(
            """{"v":1,"ts":5,"event":"Stop","reason":"done","instance":null,"socket":null,"target":null,""" +
                """"session":null,"window":null,"window_name":null,"cwd":"/x","cmd":"claude","prompt":[]}""",
        )!!
        assertEquals("done", e.reason)
        assertNull(e.instance)
        assertNull(e.socket)
        assertTrue(e.prompt.isEmpty())
    }

    @Test fun `parses a list running entry`() {
        val e = p.parse(
            """{"v":1,"reason":"running","instance":"%13","socket":"/s","target":"palli-claude:0.0",""" +
                """"session":"palli-claude","window":"0","window_name":"p","cwd":"/o","cmd":"claude","prompt":[]}""",
        )!!
        assertEquals("running", e.reason)
        assertEquals("%13", e.instance)
        assertEquals(0L, e.tsSec)
    }

    @Test fun `returns null on a half-written or non-json line`() {
        assertNull(p.parse("""{"v":1,"reason":"per"""))
        assertNull(p.parse(""))
        assertNull(p.parse("   "))
        assertNull(p.parse("not json at all"))
    }

    @Test fun `returns null when reason is missing`() {
        assertNull(p.parse("""{"v":1,"instance":"%1","cwd":"/c"}"""))
    }

    @Test fun `tolerates unknown fields`() {
        val e = p.parse(
            """{"reason":"input","instance":"%1","socket":"/s","target":"t:0.0","session":"s",""" +
                """"window_name":"w","cwd":"/c","cmd":"claude","prompt":[],"futureField":true}""",
        )!!
        assertEquals("input", e.reason)
        assertEquals("%1", e.instance)
    }
}

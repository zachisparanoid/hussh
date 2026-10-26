package com.hussh.agent

import com.hussh.transport.ExecChannel

/**
 * Routes an action to a specific tmux pane by running `hussh-route.sh` over a short-lived exec channel.
 * Constructed per-call bound to a live connection's [openExec]. Args are single-quoted so a cwd or
 * reply text with spaces/specials can't break out of the command.
 */
class AgentRouter(private val openExec: suspend (String) -> ExecChannel?) {

    suspend fun focus(i: AgentInstance): Boolean = run("sh $ROUTE focus ${q(i.socket)} ${q(i.paneId)}")
    suspend fun sendText(i: AgentInstance, text: String): Boolean =
        run("sh $ROUTE send ${q(i.socket)} ${q(i.paneId)} ${q(text)}")
    /** Type text into the pane WITHOUT Enter — lands in Claude's input for review before sending. */
    suspend fun typeText(i: AgentInstance, text: String): Boolean =
        run("sh $ROUTE type ${q(i.socket)} ${q(i.paneId)} ${q(text)}")
    suspend fun sendKey(i: AgentInstance, key: String): Boolean =
        run("sh $ROUTE sendkey ${q(i.socket)} ${q(i.paneId)} ${q(key)}")

    /** Claude's standard permission prompt: 1 = yes, Escape = cancel, Enter = accept default. */
    suspend fun approve(i: AgentInstance): Boolean = sendKey(i, "1")
    suspend fun deny(i: AgentInstance): Boolean = sendKey(i, "Escape")
    suspend fun cont(i: AgentInstance): Boolean = sendKey(i, "Enter")

    private suspend fun run(cmd: String): Boolean {
        val ch = openExec(cmd) ?: return false
        return runCatching { ch.awaitExit() == 0 }.getOrDefault(false)
    }

    private fun q(s: String?): String = "'" + (s ?: "").replace("'", "'\\''") + "'"

    private companion object {
        const val ROUTE = "~/.hussh/hussh-route.sh"
    }
}

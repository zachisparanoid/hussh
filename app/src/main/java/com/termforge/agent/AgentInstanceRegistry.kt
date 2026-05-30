package com.termforge.agent

import com.termforge.transport.ExecChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** A single Claude instance discovered on a host, addressed by its tmux pane. */
data class AgentInstance(
    val hostId: Long,
    val socket: String?,
    val paneId: String?,
    val target: String?,
    val sessionName: String?,
    val windowName: String?,
    val cwd: String?,
    val reason: String,
    val prompt: List<String>,
    val lastSeenMs: Long,
) {
    /** Routable only if it lives in a tmux pane (we can `send-keys` to it). */
    val routable: Boolean get() = paneId != null && socket != null
    val key: String get() = "$hostId:${socket ?: "_"}:${paneId ?: cwd ?: "_"}"
}

/**
 * Follows one host's `~/.termforge/attention.jsonl` over a long-lived exec channel and maintains the
 * set of live agent instances for that host. Seeds with `tf-route.sh list` + a tail of the feed, then
 * follows live appends. Invokes [onAttention] when an instance transitions into a "needs you" reason.
 *
 * The feed channel is read-only, so re-opening/closing it never risks the MINA write-cycle teardown
 * that bit the voice feature.
 */
class AgentInstanceRegistry(
    private val hostId: Long,
    private val scope: CoroutineScope,
    private val nowMs: () -> Long,
    private val openExec: suspend (String) -> ExecChannel?,
    private val onAttention: (AgentInstance) -> Unit,
) {
    private val parser = AgentEventParser()
    private val _instances = MutableStateFlow<Map<String, AgentInstance>>(emptyMap())
    val instances = _instances.asStateFlow()

    @Volatile private var feed: ExecChannel? = null
    @Volatile private var running = false

    private fun toInstance(e: AgentEvent): AgentInstance = AgentInstance(
        hostId = hostId, socket = e.socket, paneId = e.instance, target = e.target,
        sessionName = e.session, windowName = e.windowName, cwd = e.cwd,
        reason = e.reason, prompt = e.prompt, lastSeenMs = nowMs(),
    )

    fun start() {
        if (running) return
        running = true
        scope.launch { runCatching { seed() } }
        scope.launch { follow() }
        scope.launch { reconcileLoop() }
    }

    /** One-shot: enumerate current panes + replay recent feed lines (no attention fired for these). */
    private suspend fun seed() {
        openExec("sh $ROUTE list 2>/dev/null; tail -n 50 $FEED 2>/dev/null")?.let { ch ->
            ch.stdout.collect { line -> ingest(line, fireAttention = false) }
        }
    }

    /** Long-lived: follow new feed appends; auto-reopen with backoff if the channel drops. */
    private suspend fun follow() {
        while (scope.isActive && running) {
            val ch = runCatching { openExec("touch $FEED 2>/dev/null; tail -n0 -F $FEED 2>/dev/null") }.getOrNull()
            feed = ch
            if (ch == null) { delay(3000); continue }
            runCatching { ch.stdout.collect { line -> ingest(line, fireAttention = true) } }
            delay(2000) // channel ended; loop re-opens
        }
    }

    private fun ingest(line: String, fireAttention: Boolean) {
        val e = parser.parse(line) ?: return
        if (e.reason == "running" && e.instance == null) return
        val inst = toInstance(e)
        val prev = _instances.value[inst.key]
        _instances.value = _instances.value + (inst.key to inst)
        if (fireAttention && inst.reason in NEEDS && prev?.reason != inst.reason) onAttention(inst)
    }

    /** Periodically prune instances whose panes have died. */
    private suspend fun reconcileLoop() {
        while (scope.isActive && running) {
            delay(30_000)
            val live = mutableSetOf<String>()
            runCatching {
                openExec("sh $ROUTE list 2>/dev/null")?.let { ch ->
                    ch.stdout.collect { line -> parser.parse(line)?.let { live += toInstance(it).key } }
                }
            }
            if (live.isNotEmpty()) {
                _instances.value = _instances.value.filterKeys { it in live }
            }
        }
    }

    fun stop() {
        running = false
        runCatching { feed?.close() }
        feed = null
        _instances.value = emptyMap()
    }

    private companion object {
        const val ROUTE = "~/.termforge/tf-route.sh"
        const val FEED = "~/.termforge/attention.jsonl"
        val NEEDS = setOf("perm", "input", "done")
    }
}

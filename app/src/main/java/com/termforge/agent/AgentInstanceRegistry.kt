package com.termforge.agent

import android.util.Log
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

    /** Stable per-session identity for the notify mute toggle — keyed by tmux session name (or cwd),
     *  so it survives the pane id changing when a Claude is restarted in the same session. */
    val enableKey: String get() = "$hostId:${sessionName ?: cwd ?: paneId ?: "_"}"
}

/**
 * Maintains the set of live Claude instances on one host. Two cooperating loops over SSH exec channels:
 *  - [follow]: `tail -F` the feed for real-time reason transitions (+ fires attention).
 *  - [syncLoop]: `tf-route.sh list` for *presence* — so EVERY running session shows even before it
 *    fires an event — adding new panes, pruning dead ones, without clobbering an event-derived reason.
 * Both retry until the freshly-authenticated session can open exec channels (it can't in the first
 * instant after connect). Read-only channels, so re-opening never risks MINA's write-cycle teardown.
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
        Log.i(TAG, "registry start host=$hostId")
        scope.launch { follow() }
        scope.launch { syncLoop() }
    }

    /** Real-time feed events (reason transitions + attention). Auto-reopens; retries until ready. */
    private suspend fun follow() {
        delay(SETTLE_MS) // the just-authenticated session can't open exec channels for a beat
        while (scope.isActive && running) {
            val ch = runCatching { openExec("touch $FEED 2>/dev/null; tail -n0 -F $FEED 2>/dev/null") }.getOrNull()
            feed = ch
            Log.i(TAG, "follow host=$hostId channel=${ch != null}")
            if (ch == null) { delay(RETRY_MS); continue }
            runCatching { ch.stdout.collect { line -> ingest(line, fireAttention = true) } }
            Log.i(TAG, "follow host=$hostId channel ended; reopening")
            delay(RETRY_MS)
        }
    }

    /**
     * Presence: list live claude panes (add new, prune dead) every pass; on the first pass also replay
     * recent feed reasons. List entries carry reason="running" and must NOT overwrite a real
     * event-derived reason (perm/input/done) on an instance we already know about.
     */
    private suspend fun syncLoop() {
        delay(SETTLE_MS)
        var first = true
        while (scope.isActive && running) {
            val cmd = if (first) "sh $ROUTE list 2>/dev/null; tail -n 50 $FEED 2>/dev/null"
            else "sh $ROUTE list 2>/dev/null"
            val ch = runCatching { openExec(cmd) }.getOrNull()
            if (ch == null) {
                Log.i(TAG, "sync host=$hostId list channel failed; retrying")
                delay(RETRY_MS)
                continue
            }
            val live = mutableSetOf<String>()
            runCatching {
                ch.stdout.collect { line ->
                    val e = parser.parse(line) ?: return@collect
                    if (e.instance == null) return@collect
                    if (e.reason == "running") {
                        // A live pane (presence). Add if new; never clobber an existing reason.
                        val inst = toInstance(e)
                        live += inst.key
                        if (inst.key !in _instances.value) _instances.value = _instances.value + (inst.key to inst)
                    } else {
                        // A recent feed event replayed on the first pass — set its reason, no ping.
                        ingest(line, fireAttention = false)
                    }
                }
            }
            // The list succeeded (ch != null), so its membership is authoritative: prune anything not
            // live (clears genuinely-dead panes, even down to zero).
            _instances.value = _instances.value.filterKeys { it in live }
            Log.i(TAG, "sync host=$hostId live=${live.size} kept=${_instances.value.size}")
            first = false
            delay(SYNC_MS)
        }
    }

    private fun ingest(line: String, fireAttention: Boolean) {
        val e = parser.parse(line) ?: return
        if (e.reason == "running" && e.instance == null) return
        val inst = toInstance(e)
        val prev = _instances.value[inst.key]
        _instances.value = _instances.value + (inst.key to inst)
        if (fireAttention && inst.reason in NEEDS && prev?.reason != inst.reason) onAttention(inst)
        Log.i(TAG, "ingest host=$hostId key=${inst.key} reason=${inst.reason} total=${_instances.value.size}")
    }

    fun stop() {
        running = false
        runCatching { feed?.close() }
        feed = null
        _instances.value = emptyMap()
    }

    private companion object {
        const val TAG = "TFAgent"
        const val ROUTE = "~/.termforge/tf-route.sh"
        const val FEED = "~/.termforge/attention.jsonl"
        const val SETTLE_MS = 400L  // let the session settle before the first exec channel
        const val RETRY_MS = 1500L  // re-open a failed/ended channel
        const val SYNC_MS = 15_000L // presence refresh interval
        val NEEDS = setOf("perm", "input", "done")
    }
}

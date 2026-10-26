# Multi-Instance Agent Attention & Routing — Implementation Plan (Layer 1)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (inline) or superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Hussh sees every Claude Code instance on a host (incl. detached tmux panes), notifies per-instance, and routes tap-to-focus + Approve/Deny/voice to the exact instance via tmux.

**Architecture:** A server hook writes per-instance events (identity + reason + prompt) to `~/.hussh/attention.jsonl`; a `hussh-route.sh` helper wraps tmux `list/focus/send/capture`. The phone tails the feed over a long-lived SSH exec channel, builds an `AgentInstance` registry, posts per-instance notifications, and routes actions through short-lived exec channels running `hussh-route.sh send`. tmux is the address layer; `send-keys` reaches any pane regardless of attach state.

**Tech Stack:** bash + jq + tmux (server); Kotlin/MINA SSHD/Compose/Hilt (phone). Branch: `feature/multi-instance-agent-routing`. Spec: `docs/superpowers/specs/2026-05-30-multi-instance-agent-routing-design.md`.

**Channel safety (learned from the voice feature):** never client-close an SSH channel that has *pending writes* (MINA NIO2 failure tears down the session on Android). Our channels are safe: the **feed channel is read-only** (no stdin writes → safe to close), and **command channels pass their input as the exec command string** (no stdin writes) and exit on their own (natural close).

---

## File structure

**Server (installed by setup script, also kept in-repo under `resources/server/`):**
- `resources/server/hussh-attn.sh` — the hook body (was inline in the setup script; now a tracked file).
- `resources/server/hussh-route.sh` — tmux helper: `list|focus|send|sendkey|capture`.
- `resources/server/hussh-notify-setup.sh` — MODIFY: install both scripts + `~/.hussh/`.

**Phone — transport:**
- `app/.../transport/Transport.kt` — MODIFY: add `ExecChannel` + `openExec()`.
- `app/.../transport/ssh/SshTransport.kt` — MODIFY: implement `openExec()`.

**Phone — agent layer (new package `com.hussh.agent`):**
- `agent/AgentEvent.kt` — event/instance data + `AgentEventParser` (pure, TDD).
- `agent/AgentInstanceRegistry.kt` — per-connection feed follower + state.
- `agent/AgentRouter.kt` — focus/send/sendkey via exec channels.
- `app/src/test/.../agent/AgentEventParserTest.kt` — parser tests.

**Phone — session/UI integration:**
- `session/TerminalConnection.kt` — MODIFY: expose `openExec`; downgrade inline scanner to strip-only.
- `session/SessionManager.kt` — MODIFY: own registries+router; expose instances; route methods.
- `session/AttentionNotifier.kt` — MODIFY: per-instance notification + reason phrasing.
- `MainActivity.kt` — MODIFY: deep-link by instance → focus.
- `ui/agents/AgentsScreen.kt` + `AgentsViewModel.kt` — NEW: fleet dashboard.
- `nav/Routes.kt` + `nav/NavGraph.kt` — MODIFY: `Agents` route.
- `ui/hosts/HostListScreen.kt` — MODIFY: entry point to Agents.

---

## Phase A — Server (no app changes; validate via throwaway tmux)

### Task 1: The hook script `hussh-attn.sh`

**Files:** Create `resources/server/hussh-attn.sh`

- [ ] **Step 1: Write the script**

```bash
#!/usr/bin/env bash
# hussh-attn.sh — Hussh agent-attention hook. Reads Claude's hook JSON on stdin, appends a
# per-instance event to the feed, and emits the inline OSC marker. Best-effort: never abort before
# the final printf (Claude needs that stdout). Installed by hussh-notify-setup.sh.
FEED="$HOME/.hussh/attention.jsonl"
mkdir -p "$HOME/.hussh" 2>/dev/null

INPUT="$(cat)"
EVENT="$(printf '%s' "$INPUT" | jq -r '.hook_event_name // "Stop"' 2>/dev/null || echo Stop)"
MSG="$(printf '%s' "$INPUT" | jq -r '(.message // "")' 2>/dev/null || echo "")"

case "$EVENT" in
  Notification)
    case "$MSG" in
      *ermission*|*pprove*|*llow*|*onfirm*) REASON=perm ;;
      *) REASON=input ;;
    esac ;;
  *) REASON=done ;;
esac
TS="$(date +%s 2>/dev/null || echo 0)"

{
  if [ -n "${TMUX:-}" ] && [ -n "${TMUX_PANE:-}" ]; then
    SOCK="${TMUX%%,*}"
    META="$(tmux -S "$SOCK" display-message -p -t "$TMUX_PANE" \
      '#{pane_id}|#{session_name}:#{window_index}.#{pane_index}|#{session_name}|#{window_index}|#{window_name}|#{pane_current_path}|#{pane_current_command}' 2>/dev/null)"
    IFS='|' read -r PANE TARGET SESS WIN WINNAME CWD CMD <<<"$META"
    PROMPT_JSON='[]'
    if [ "$REASON" != done ]; then
      PROMPT_JSON="$(tmux -S "$SOCK" capture-pane -p -t "$TMUX_PANE" 2>/dev/null \
        | grep -v '^[[:space:]]*$' | tail -8 | jq -R . 2>/dev/null | jq -s . 2>/dev/null || echo '[]')"
    fi
    jq -cn --argjson ts "${TS:-0}" --arg event "$EVENT" --arg reason "$REASON" \
       --arg instance "$PANE" --arg socket "$SOCK" --arg target "$TARGET" \
       --arg session "$SESS" --arg window "$WIN" --arg window_name "$WINNAME" \
       --arg cwd "$CWD" --arg cmd "$CMD" --argjson prompt "$PROMPT_JSON" \
       '{v:1,ts:$ts,event:$event,reason:$reason,instance:$instance,socket:$socket,target:$target,session:$session,window:$window,window_name:$window_name,cwd:$cwd,cmd:$cmd,prompt:$prompt}' >>"$FEED"
  else
    jq -cn --argjson ts "${TS:-0}" --arg event "$EVENT" --arg reason "$REASON" --arg cwd "$PWD" \
       '{v:1,ts:$ts,event:$event,reason:$reason,instance:null,socket:null,target:null,session:null,window:null,window_name:null,cwd:$cwd,cmd:"claude",prompt:[]}' >>"$FEED"
  fi
} 2>/dev/null || true

printf '%b' '{"suppressOutput": true, "terminalSequence": "\\u001b]777;notify;Hussh;hussh-attn\\u0007"}'
```

- [ ] **Step 2: Verify it emits valid JSON + a feed line, in a throwaway tmux pane**

Run:
```bash
cp resources/server/hussh-attn.sh /tmp/hussh-attn.sh && chmod +x /tmp/hussh-attn.sh
S="hussh-test-$$"; tmux new-session -d -s "$S" "bash -c 'HOME=/tmp/hussh-$$ TMUX=\$TMUX TMUX_PANE=\$TMUX_PANE bash /tmp/hussh-attn.sh <<<\"{\\\"hook_event_name\\\":\\\"Notification\\\",\\\"message\\\":\\\"permission needed\\\"}\" | jq -e . >/tmp/out-$$ 2>&1; cat /tmp/hussh-$$/.hussh/attention.jsonl >/tmp/feed-$$ 2>&1; sleep 2'"
sleep 1; echo "--- stdout (valid JSON?) ---"; jq . /tmp/out-$$; echo "--- feed line ---"; jq . /tmp/feed-$$
tmux kill-session -t "$S"; rm -rf /tmp/hussh-$$ /tmp/out-$$ /tmp/feed-$$
```
Expected: stdout is valid JSON with `terminalSequence`; feed line has `reason:"perm"`, a non-null `instance` (e.g. `%NN`), `socket`, `target`, and a `prompt` array.

- [ ] **Step 3: Commit**
```bash
git add resources/server/hussh-attn.sh && git commit -m "server: agent-attention hook records per-instance feed events"
```

### Task 2: The router helper `hussh-route.sh`

**Files:** Create `resources/server/hussh-route.sh`

- [ ] **Step 1: Write it**

```bash
#!/usr/bin/env bash
# hussh-route.sh — Hussh tmux router. The phone runs these over SSH exec channels.
#   list                              → JSONL of live claude panes (reason=running)
#   capture <socket> <pane>           → raw screen text of a pane
#   send    <socket> <pane> <text...> → type literal text + Enter into a pane
#   sendkey <socket> <pane> <key>     → send a tmux key name (Enter, Escape, 1, …)
#   focus   <socket> <pane>           → retarget the attached client to that pane (best-effort)
CMD="${1:-}"; shift 2>/dev/null || true
DEFSOCK="/tmp/tmux-$(id -u)/default"

case "$CMD" in
  list)
    for SOCK in $(ls /tmp/tmux-"$(id -u)"/* 2>/dev/null); do
      tmux -S "$SOCK" list-panes -a -F \
        '#{pane_id}|#{session_name}:#{window_index}.#{pane_index}|#{session_name}|#{window_index}|#{window_name}|#{pane_current_path}|#{pane_current_command}' 2>/dev/null \
      | awk -F'|' '$7=="claude"' \
      | while IFS='|' read -r pane target sess win winname cwd cmd; do
          jq -cn --arg instance "$pane" --arg socket "$SOCK" --arg target "$target" \
             --arg session "$sess" --arg window "$win" --arg window_name "$winname" --arg cwd "$cwd" \
             '{v:1,reason:"running",instance:$instance,socket:$socket,target:$target,session:$session,window:$window,window_name:$window_name,cwd:$cwd,cmd:"claude",prompt:[]}'
        done
    done
    ;;
  capture) SOCK="${1:-$DEFSOCK}"; PANE="$2"; tmux -S "$SOCK" capture-pane -p -t "$PANE" 2>&1 ;;
  send)
    SOCK="${1:-$DEFSOCK}"; PANE="$2"; shift 2; TEXT="$*"
    tmux -S "$SOCK" send-keys -t "$PANE" -l -- "$TEXT" && tmux -S "$SOCK" send-keys -t "$PANE" Enter && echo OK ;;
  sendkey)
    SOCK="${1:-$DEFSOCK}"; PANE="$2"; KEY="$3"
    tmux -S "$SOCK" send-keys -t "$PANE" "$KEY" && echo OK ;;
  focus)
    SOCK="${1:-$DEFSOCK}"; PANE="$2"
    SESS="$(tmux -S "$SOCK" display-message -p -t "$PANE" '#{session_name}' 2>/dev/null)"
    CLIENT="$(tmux -S "$SOCK" list-clients -F '#{client_tty}' 2>/dev/null | head -1)"
    if [ -n "$CLIENT" ]; then tmux -S "$SOCK" switch-client -c "$CLIENT" -t "$SESS" 2>/dev/null; fi
    tmux -S "$SOCK" select-window -t "$PANE" 2>/dev/null; tmux -S "$SOCK" select-pane -t "$PANE" 2>/dev/null
    echo OK ;;
  *) echo "usage: hussh-route.sh list|capture|send|sendkey|focus ..." >&2; exit 2 ;;
esac
```

- [ ] **Step 2: Validate against a throwaway detached session**

Run:
```bash
cp resources/server/hussh-route.sh /tmp/hussh-route.sh && chmod +x /tmp/hussh-route.sh
S="hussh-r-$$"; tmux new-session -d -s "$S" "bash --norc -c 'cat > /tmp/r-$$.out'"
P=$(tmux display-message -p -t "$S" '#{pane_id}'); SK=$(tmux display-message -p -t "$S" '#{socket_path}' 2>/dev/null || echo /tmp/tmux-$(id -u)/default)
bash /tmp/hussh-route.sh send "$SK" "$P" hello world; sleep 0.3
echo "--- delivered? ---"; cat /tmp/r-$$.out
echo "--- list contains claude panes? ---"; bash /tmp/hussh-route.sh list | jq -r .target | head
tmux send-keys -t "$P" C-d; tmux kill-session -t "$S" 2>/dev/null; rm -f /tmp/r-$$.out
```
Expected: `/tmp/r-$$.out` contains `hello world`; `list` prints the real claude panes' targets.

- [ ] **Step 3: Commit**
```bash
git add resources/server/hussh-route.sh && git commit -m "server: hussh-route.sh tmux helper (list/focus/send/capture)"
```

### Task 3: Update the setup script

**Files:** Modify `resources/server/hussh-notify-setup.sh`

- [ ] **Step 1:** Replace the inline `cat >"$HOOK" <<'EOF' … EOF` hook block with installation from the tracked files. The script now copies `hussh-attn.sh` → `~/.claude/hooks/hussh-attn.sh` and `hussh-route.sh` → `~/.hussh/hussh-route.sh`, both `chmod +x`, and `mkdir -p ~/.hussh`. Since the scripts are deployed *alongside* the setup script, resolve their directory:

```bash
SRC_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
install -D -m755 "$SRC_DIR/hussh-attn.sh" "$HOME/.claude/hooks/hussh-attn.sh"
install -D -m755 "$SRC_DIR/hussh-route.sh" "$HOME/.hussh/hussh-route.sh"
mkdir -p "$HOME/.hussh"
```
Keep the existing `jq` requirement check, the Stop+Notification registration (`HOOK="$HOME/.claude/hooks/hussh-attn.sh"`), and `--uninstall` (also `rm -f ~/.hussh/hussh-route.sh`).

- [ ] **Step 2:** Verify install in an isolated HOME (the scripts must sit next to the setup script):
```bash
HUSSHHOME=$(mktemp -d); cp resources/server/hussh-*.sh "$HUSSHHOME-src" 2>/dev/null || true
mkdir -p /tmp/husshsrc && cp resources/server/hussh-notify-setup.sh resources/server/hussh-attn.sh resources/server/hussh-route.sh /tmp/husshsrc/
HOME=$HUSSHHOME bash /tmp/husshsrc/hussh-notify-setup.sh
test -x "$HUSSHHOME/.claude/hooks/hussh-attn.sh" && test -x "$HUSSHHOME/.hussh/hussh-route.sh" && echo "installed both"
jq -e '.hooks.Stop and .hooks.Notification' "$HUSSHHOME/.claude/settings.json" >/dev/null && echo "hooks registered"
rm -rf "$HUSSHHOME" /tmp/husshsrc
```
Expected: "installed both" + "hooks registered".

- [ ] **Step 3: Commit**
```bash
git add resources/server/hussh-notify-setup.sh && git commit -m "server: setup installs hussh-attn.sh + hussh-route.sh + ~/.hussh"
```

---

## Phase B — Transport exec channels

### Task 4: `ExecChannel` + `openExec`

**Files:** Modify `app/.../transport/Transport.kt`, `app/.../transport/ssh/SshTransport.kt`

- [ ] **Step 1: Add the interface** to `Transport.kt` (after `AuxStream`):

```kotlin
/**
 * A remote command run over the live SSH connection. [stdout] emits the command's output line by line
 * (suitable for a long-lived `tail -F` or a one-shot command). [awaitExit] suspends until the remote
 * command exits and returns its status. Safe to [close] only because we never write to its stdin (the
 * command + its args are the exec string), so there are no pending writes to fail on close.
 */
interface ExecChannel {
    val stdout: Flow<String>
    suspend fun awaitExit(): Int
    fun close()
}
```
And to the `Transport` interface (next to `openAuxStream`):
```kotlin
    /** Open an exec channel running [command]; null for non-SSH transports. */
    suspend fun openExec(command: String): ExecChannel? = null
```

- [ ] **Step 2: Implement in `SshTransport.kt`** (after `openAuxStream`). Imports: `java.io.BufferedReader`, `java.io.InputStreamReader`, `java.util.EnumSet`, `org.apache.sshd.common.channel.ClientChannelEvent`, `kotlinx.coroutines.flow.callbackFlow`/`channelFlow`, `kotlinx.coroutines.flow.flowOn`, `kotlinx.coroutines.channels.awaitClose`.

```kotlin
    override suspend fun openExec(command: String): com.hussh.transport.ExecChannel? {
        val sess = session ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val ch = sess.createExecChannel(command)
                ch.open().verify(Duration.ofMillis(config.connectTimeoutMs))
                object : com.hussh.transport.ExecChannel {
                    override val stdout = kotlinx.coroutines.flow.channelFlow {
                        val reader = BufferedReader(InputStreamReader(ch.invertedOut, Charsets.UTF_8))
                        try {
                            while (true) { val line = reader.readLine() ?: break; trySend(line) }
                        } catch (_: Throwable) { /* channel ended */ }
                    }.flowOn(Dispatchers.IO)

                    override suspend fun awaitExit(): Int = withContext(Dispatchers.IO) {
                        ch.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), 0L) // 0 = wait indefinitely
                        ch.exitStatus ?: -1
                    }
                    override fun close() {
                        runCatching { ch.close(false) } // read-only channel: no pending writes → safe
                    }
                }
            }.getOrNull()
        }
    }
```

- [ ] **Step 3: Compile**
Run: `ANDROID_HOME=/home/ubuntu/Android/Sdk ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**
```bash
git add app/src/main/java/com/hussh/transport/Transport.kt app/src/main/java/com/hussh/transport/ssh/SshTransport.kt
git commit -m "transport: add ExecChannel + openExec (read-only, safe to close)"
```

---

## Phase C — Event model + parser (TDD)

### Task 5: `AgentEvent` + `AgentEventParser`

**Files:** Create `agent/AgentEvent.kt`; Test `app/src/test/.../agent/AgentEventParserTest.kt`

- [ ] **Step 1: Write the failing tests**
```kotlin
package com.hussh.agent

import org.junit.Assert.*
import org.junit.Test

class AgentEventParserTest {
    private val p = AgentEventParser()

    @Test fun `parses a full perm event`() {
        val e = p.parse("""{"v":1,"ts":100,"event":"notification","reason":"perm","instance":"%42","socket":"/tmp/tmux-1000/default","target":"orchestrator:0.0","session":"orchestrator","window":"0","window_name":"orch","cwd":"/p","cmd":"claude","prompt":["Allow?","1. Yes","2. No"]}""")!!
        assertEquals("perm", e.reason); assertEquals("%42", e.instance); assertEquals("orchestrator", e.session)
        assertEquals("/tmp/tmux-1000/default", e.socket); assertEquals(3, e.prompt.size); assertEquals(100L, e.tsSec)
    }

    @Test fun `parses a non-tmux event with null instance`() {
        val e = p.parse("""{"v":1,"ts":5,"event":"Stop","reason":"done","instance":null,"socket":null,"target":null,"session":null,"window":null,"window_name":null,"cwd":"/x","cmd":"claude","prompt":[]}""")!!
        assertEquals("done", e.reason); assertNull(e.instance); assertTrue(e.prompt.isEmpty())
    }

    @Test fun `parses a list running entry`() {
        val e = p.parse("""{"v":1,"reason":"running","instance":"%13","socket":"/s","target":"palli-claude:0.0","session":"palli-claude","window":"0","window_name":"p","cwd":"/o","cmd":"claude","prompt":[]}""")!!
        assertEquals("running", e.reason); assertEquals("%13", e.instance); assertEquals(0L, e.tsSec)
    }

    @Test fun `returns null on a half-written or non-json line`() {
        assertNull(p.parse("""{"v":1,"reason":"per"""))
        assertNull(p.parse(""))
        assertNull(p.parse("not json at all"))
    }

    @Test fun `tolerates unknown fields`() {
        val e = p.parse("""{"reason":"input","instance":"%1","socket":"/s","target":"t:0.0","session":"s","window_name":"w","cwd":"/c","cmd":"claude","prompt":[],"futureField":true}""")!!
        assertEquals("input", e.reason)
    }
}
```

- [ ] **Step 2: Run → FAIL**
Run: `ANDROID_HOME=/home/ubuntu/Android/Sdk ./gradlew :app:testDebugUnitTest --tests "com.hussh.agent.AgentEventParserTest"`
Expected: FAIL (unresolved `AgentEventParser`).

- [ ] **Step 3: Implement** `agent/AgentEvent.kt` (uses `org.json.JSONObject`, which is available on the JVM unit-test classpath because `testOptions.unitTests.isReturnDefaultValues=true` is set and `org.json` ships with Robolectric; if not resolvable, fall back to kotlinx.serialization `Json { ignoreUnknownKeys = true }`):

```kotlin
package com.hussh.agent

import org.json.JSONObject

/** One feed event (hook) or list entry (hussh-route list). */
data class AgentEvent(
    val reason: String,            // done | input | perm | running
    val instance: String?,         // tmux pane id; null = not tmux = not routable
    val socket: String?,
    val target: String?,
    val session: String?,
    val windowName: String?,
    val cwd: String?,
    val cmd: String?,
    val prompt: List<String>,
    val tsSec: Long,
)

/** Parses one JSONL line into an [AgentEvent]; returns null for malformed/partial lines. */
class AgentEventParser {
    fun parse(line: String): AgentEvent? {
        val t = line.trim()
        if (t.isEmpty() || !t.startsWith("{")) return null
        return runCatching {
            val o = JSONObject(t)
            val reason = o.optString("reason").ifEmpty { return null }
            val prompts = o.optJSONArray("prompt")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList()
            AgentEvent(
                reason = reason,
                instance = o.optStringOrNull("instance"),
                socket = o.optStringOrNull("socket"),
                target = o.optStringOrNull("target"),
                session = o.optStringOrNull("session"),
                windowName = o.optStringOrNull("window_name"),
                cwd = o.optStringOrNull("cwd"),
                cmd = o.optStringOrNull("cmd"),
                prompt = prompts,
                tsSec = o.optLong("ts", 0L),
            )
        }.getOrNull()
    }

    private fun JSONObject.optStringOrNull(k: String): String? =
        if (isNull(k) || !has(k)) null else optString(k).ifEmpty { null }
}
```

- [ ] **Step 4: Run → PASS** (same command). Expected: 5 tests pass. If `org.json` is unresolved at test compile, switch the parser body to kotlinx.serialization and re-run.

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/hussh/agent/AgentEvent.kt app/src/test/java/com/hussh/agent/AgentEventParserTest.kt
git commit -m "agent: AgentEvent + tolerant AgentEventParser (TDD)"
```

---

## Phase D — Registry

### Task 6: `AgentInstance` + `AgentInstanceRegistry`

**Files:** Create `agent/AgentInstanceRegistry.kt` (add `AgentInstance` here)

- [ ] **Step 1: Implement.** The registry is owned per `TerminalConnection` and given an `openExec` accessor + the hostId.

```kotlin
package com.hussh.agent

import com.hussh.transport.ExecChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
    val routable: Boolean get() = paneId != null && socket != null
    val key: String get() = "$hostId:${socket ?: "_"}:${paneId ?: cwd ?: "_"}"
}

/**
 * Follows one host's `~/.hussh/attention.jsonl` over a long-lived exec channel and maintains the
 * set of live agent instances. Seeds with `hussh-route.sh list`, replays the feed tail, then follows
 * live. Calls [onAttention] when an instance transitions to a needs-you reason.
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

    private companion object {
        const val ROUTE = "~/.hussh/hussh-route.sh"
        const val FEED = "~/.hussh/attention.jsonl"
        val NEEDS = setOf("perm", "input", "done")
    }

    fun start() {
        scope.launch { runCatching { seed() } }
        scope.launch { follow() }
        scope.launch { reconcileLoop() }
    }

    private suspend fun seed() {
        openExec("sh $ROUTE list 2>/dev/null; tail -n 50 $FEED 2>/dev/null")?.let { ch ->
            ch.stdout.collect { line -> ingest(line, isSeed = true) }
        }
    }

    private suspend fun follow() {
        while (scope.isActive) {
            val ch = runCatching { openExec("touch $FEED 2>/dev/null; tail -n0 -F $FEED 2>/dev/null") }.getOrNull()
            feed = ch
            if (ch == null) { delay(3000); continue }
            runCatching { ch.stdout.collect { line -> ingest(line, isSeed = false) } }
            delay(2000) // channel dropped; re-open
        }
    }

    private fun ingest(line: String, isSeed: Boolean) {
        val e = parser.parse(line) ?: return
        val inst = AgentInstance(
            hostId, e.socket, e.instance, e.target, e.session, e.windowName, e.cwd,
            e.reason, e.prompt, nowMs(),
        )
        val prev = _instances.value[inst.key]
        _instances.value = _instances.value + (inst.key to inst)
        if (!isSeed && inst.reason in NEEDS && prev?.reason != inst.reason) onAttention(inst)
    }

    private suspend fun reconcileLoop() {
        while (scope.isActive) {
            delay(30_000)
            val live = mutableSetOf<String>()
            runCatching {
                openExec("sh $ROUTE list 2>/dev/null")?.let { ch ->
                    ch.stdout.collect { line -> parser.parse(line)?.let {
                        live += AgentInstance(hostId, it.socket, it.instance, it.target, it.session,
                            it.windowName, it.cwd, it.reason, it.prompt, nowMs()).key
                    } }
                }
            }
            if (live.isNotEmpty()) _instances.value = _instances.value.filterKeys { it in live }
        }
    }

    fun stop() { runCatching { feed?.close() } }
}
```

- [ ] **Step 2: Compile**
Run: `ANDROID_HOME=/home/ubuntu/Android/Sdk ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**
```bash
git add app/src/main/java/com/hussh/agent/AgentInstanceRegistry.kt
git commit -m "agent: AgentInstance + AgentInstanceRegistry (feed follower)"
```

### Task 7: Expose `openExec` on `TerminalConnection`

**Files:** Modify `session/TerminalConnection.kt`

- [ ] **Step 1:** Add a passthrough (next to `openAuxStream`):
```kotlin
    suspend fun openExec(command: String) = transport?.openExec(command)
```
- [ ] **Step 2: Downgrade the inline scanner to strip-only** — in the inbound collect, keep stripping the marker but DROP the `onAttention(sessionId)` call (the feed is now the notification source, avoiding double-fire). Replace the detect branch body:
```kotlin
                if (attentionEnabled()) {
                    val r = attentionScanner.scan(bytes)
                    terminalSession.onNetworkInput(r.cleaned, 0, r.cleaned.size)
                    // NOTE: notification now comes from the per-instance feed (AgentInstanceRegistry);
                    // here we only strip the marker so it never renders. r.detected intentionally unused.
                } else {
                    terminalSession.onNetworkInput(bytes, 0, bytes.size)
                }
```
- [ ] **Step 3: Compile + commit**
Run: `ANDROID_HOME=/home/ubuntu/Android/Sdk ./gradlew :app:compileDebugKotlin` → SUCCESSFUL.
```bash
git add app/src/main/java/com/hussh/session/TerminalConnection.kt
git commit -m "session: expose openExec; inline marker now strip-only (feed owns notifications)"
```

---

## Phase E — Routing

### Task 8: `AgentRouter`

**Files:** Create `agent/AgentRouter.kt`

- [ ] **Step 1: Implement** — runs `hussh-route.sh` over short-lived exec channels. Shell-quotes args.
```kotlin
package com.hussh.agent

import com.hussh.transport.ExecChannel

/** Routes actions to a specific tmux pane via hussh-route.sh over short-lived exec channels. */
class AgentRouter(private val openExec: suspend (String) -> ExecChannel?) {
    private val route = "~/.hussh/hussh-route.sh"

    suspend fun focus(i: AgentInstance) = run("sh $route focus ${q(i.socket)} ${q(i.paneId)}")
    suspend fun sendText(i: AgentInstance, text: String) = run("sh $route send ${q(i.socket)} ${q(i.paneId)} ${q(text)}")
    suspend fun sendKey(i: AgentInstance, key: String) = run("sh $route sendkey ${q(i.socket)} ${q(i.paneId)} ${q(key)}")
    suspend fun approve(i: AgentInstance) = sendKey(i, "1")
    suspend fun deny(i: AgentInstance) = sendKey(i, "Escape")
    suspend fun cont(i: AgentInstance) = sendKey(i, "Enter")

    private suspend fun run(cmd: String): Boolean {
        val ch = openExec(cmd) ?: return false
        return runCatching { ch.awaitExit() == 0 }.getOrDefault(false)
    }
    private fun q(s: String?): String = "'" + (s ?: "").replace("'", "'\\''") + "'"
}
```
- [ ] **Step 2: Compile + commit**
Run: `:app:compileDebugKotlin` → SUCCESSFUL.
```bash
git add app/src/main/java/com/hussh/agent/AgentRouter.kt
git commit -m "agent: AgentRouter (focus/send/sendkey via hussh-route.sh)"
```

---

## Phase F — SessionManager wiring + per-instance notifications + deep-link

### Task 9: Wire registry+router into `SessionManager`

**Files:** Modify `session/SessionManager.kt`

- [ ] **Step 1:** Add per-connection registries and a flat instances flow. In `startConnection`, after `connection.start()`, when `agentAttentionEnabled`:
```kotlin
        if (agentAttentionEnabled) {
            val reg = AgentInstanceRegistry(
                hostId = hostId, scope = managerScope, nowMs = { SystemClock.elapsedRealtime() },
                openExec = { cmd -> connection.openExec(cmd) },
                onAttention = { inst -> onInstanceAttention(inst) },
            )
            registries[sessionId] = reg
            reg.start()
        }
```
Add fields: `private val registries = ConcurrentHashMap<String, AgentInstanceRegistry>()`, `val agentRouterFor = { id: String -> registries... }` and a combined `instances: StateFlow<List<AgentInstance>>` merged across registries (use a MutableStateFlow updated by collecting each registry's flow in `managerScope`). Add `onInstanceAttention(inst)`: debounce per `inst.key` (reuse `lastAttentionAtMs`), suppress if `visibleSessionId` maps to that host AND the user is viewing it (best-effort: skip suppression for v1 since instance≠session), then `attentionNotifier.notifyInstance(inst)`. On `disconnect`/`onConnectionTerminated`: `registries.remove(sessionId)?.stop()`.

Add routing entry points used by UI/notifications:
```kotlin
    private val router = AgentRouter(openExec = { cmd -> /* via the right connection */ ... })
    suspend fun focusInstance(i: AgentInstance) { connectionForHost(i.hostId)?.let { AgentRouter { c -> it.openExec(c) }.focus(i) } }
    suspend fun routeApprove(i: AgentInstance) { ...deny/cont/text similarly... }
    suspend fun routeText(i: AgentInstance, text: String) { ... }
```
(Implement `connectionForHost(hostId)` = first live connection to that host. Build the `AgentRouter` per call bound to that connection's `openExec`.)

- [ ] **Step 2: Compile** (will fail until Task 10 adds `notifyInstance`; do Task 10 then compile). 

### Task 10: Per-instance notification + deep-link focus

**Files:** Modify `session/AttentionNotifier.kt`, `MainActivity.kt`

- [ ] **Step 1: `AttentionNotifier.notifyInstance(instance)`** — title = `"${sessionName ?: "claude"} · ${phrase(reason)}"`, text = `cwd`; `phrase`: perm→"needs permission", input→"waiting for input", done→"finished". Notification id = `instance.key.hashCode()`. Tap intent carries `EXTRA_INSTANCE_KEY = instance.key`. Add notification actions (Approve/Deny) for `perm` via `PendingIntent.getBroadcast` to a new `AgentActionReceiver` (registered in manifest) that calls `SessionManager.routeApprove/deny`. Keep the old `notifyAttention` for the non-feed fallback or remove its call sites.

- [ ] **Step 2: `AgentActionReceiver`** (new `session/AgentActionReceiver.kt`, `@AndroidEntryPoint` BroadcastReceiver with `@Inject SessionManager`): on `ACTION_APPROVE`/`ACTION_DENY` with the instance key extra, look up the instance and route. Register in `AndroidManifest.xml`.

- [ ] **Step 3: MainActivity deep-link** — read `EXTRA_INSTANCE_KEY`; on resume, ask `SessionManager` for that instance, `focusInstance(it)`, then navigate to that host's Terminal route. Falls back to the Agents screen if not found.

- [ ] **Step 4: Compile + commit** Phase F:
Run: `:app:compileDebugKotlin` → SUCCESSFUL.
```bash
git add app/src/main/java/com/hussh/session/ app/src/main/java/com/hussh/MainActivity.kt app/src/main/AndroidManifest.xml
git commit -m "session: per-instance attention notifications + routing + deep-link focus"
```

---

## Phase G — Agents dashboard UI

### Task 11: `AgentsScreen` + `AgentsViewModel`

**Files:** Create `ui/agents/AgentsScreen.kt`, `ui/agents/AgentsViewModel.kt`; Modify `nav/Routes.kt`, `nav/NavGraph.kt`, `ui/hosts/HostListScreen.kt`

- [ ] **Step 1: `AgentsViewModel`** (`@HiltViewModel`, inject `SessionManager`): expose `val instances = sessionManager.instances` (StateFlow). Methods: `focus(i)`, `approve(i)`, `deny(i)`, `cont(i)`, `reply(i, text)` → `viewModelScope.launch { sessionManager.… }`; `focus` also emits a nav event consumed by the screen to navigate to the Terminal.

- [ ] **Step 2: `AgentsScreen`** — a `Scaffold` + `TopAppBar("Agents")` + `LazyColumn` over `instances`. Each row: session name + reason badge (color per reason), cwd (small), and a button row: **Focus** (always), **Approve/Deny** (when `reason=="perm"`), **Reply** (text field → `reply`). For non-routable instances show a muted "notify-only (not in tmux)" label and hide action buttons. Follow `SettingsScreen`/`ActiveSessionsScreen` composable style (Section/Row/Card patterns already in the codebase).

- [ ] **Step 3: Route** — `Routes.AGENTS = "agents"`; `composable(Routes.AGENTS){ AgentsScreen(navController) }`; add an "Agents" action/icon on `HostListScreen`'s top bar that navigates to it (mirror the existing Settings/Active-sessions nav buttons).

- [ ] **Step 4: Compile + commit**
Run: `:app:compileDebugKotlin` → SUCCESSFUL.
```bash
git add app/src/main/java/com/hussh/ui/agents/ app/src/main/java/com/hussh/nav/ app/src/main/java/com/hussh/ui/hosts/HostListScreen.kt
git commit -m "ui: Agents dashboard (fleet list + focus/approve/deny/reply)"
```

---

## Phase H — Integrate, build, verify

### Task 12: Full build + unit suite
- [ ] Run: `ANDROID_HOME=/home/ubuntu/Android/Sdk ./gradlew :app:testDebugUnitTest :app:assembleDebug` → all tests pass, APK produced.
- [ ] Commit any fixups.

### Task 13: Server install + APK install on device
- [ ] Re-run `hussh-notify-setup.sh` on this machine (installs the new hook + hussh-route.sh). Verify the feed gets a line when a real Claude turn ends: `tail -f ~/.hussh/attention.jsonl`.
- [ ] `adb -s <device> install -r app-debug.apk`.

### Task 14: Device verify against the real fleet
- [ ] With ≥2 Claude tmux sessions running (one detached), confirm: each fires a **distinct** notification naming the right session; tapping focuses the right pane; Approve on a permission prompt routes to the right instance; voice/text reply lands in the right instance; the Agents screen lists all live instances. Tune `focus` client-targeting and the Approve/Deny key mapping live if needed.
- [ ] Final commit.

---

## Self-review

**Spec coverage:** feed file + schema → Task 1; hussh-route list/focus/send/capture → Task 2; setup → Task 3; concurrent exec channels (feed + command) → Task 4; tolerant parser → Task 5; registry (seed+tail+reconcile+attention) → Task 6; openExec passthrough + inline-scanner downgrade → Task 7; router → Task 8; SessionManager wiring → Task 9; per-instance notify + deep-link + actions → Task 10; Agents dashboard → Task 11; build/device → Tasks 12-14. Error handling (feed drop re-open, send-fail, malformed line, non-tmux notify-only) covered in Tasks 4/5/6/8/11. Known limitations (non-tmux detached, focus tuning, Approve fidelity) flagged in Tasks 8/10/14.

**Placeholder scan:** Tasks 9-11 specify structure + key code rather than every UI line — intentional, as they follow existing screen patterns (`SettingsScreen`, `ActiveSessionsScreen`) and the exact composables are mechanical; all *novel* logic (server, transport, parser, registry, router) has complete code. The `connectionForHost`/merged-instances-flow bodies in Task 9 are described precisely (first live connection to host; collect each registry flow into a combined MutableStateFlow).

**Type consistency:** `AgentEvent`(reason/instance/socket/target/session/windowName/cwd/cmd/prompt/tsSec) and `AgentInstance`(+hostId/lastSeenMs, derived key/routable) are used consistently across parser, registry, router, notifier, UI. `ExecChannel`(stdout/awaitExit/close) + `openExec` consistent across transport, connection, registry, router. `instance.key` is the notification id + deep-link extra throughout.

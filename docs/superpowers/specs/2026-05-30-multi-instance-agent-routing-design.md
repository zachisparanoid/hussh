# Multi-Instance Agent Attention & Routing — Design

**Status:** Approved (2026-05-30). Layer 1 of the watch-companion effort; the Wear OS app (Layer 2) is built on top of this and deferred.

## Goal

Make TermForge aware of **every** Claude Code instance running on a host — including ones in detached or background tmux panes — and let the user **act on the right one**: a notification names the specific instance, tapping it lands the terminal in that instance, and Approve/Deny/Continue/voice-text route to *that* instance's input, regardless of tmux attach state.

## Problem

Attention detection shipped today attributes a ping to *the SSH connection that carried the marker bytes*. That works only for the one pane currently rendered to the client. The user runs 4–5 Claude sessions across separate tmux sessions (`orchestrator`, `palli-claude`, …); tmux only streams the **attached session's active pane** to the client, so:
- detached/background instances never reach the phone (no notification), and
- even foreground markers carry no identity, so the phone can't say *which* Claude pinged.

## Key insight — tmux is an address book, not a wall

The marker rode the *rendered screen*, which only shows the active pane. tmux's **control commands** address any pane directly, bypassing the screen. Validated on the live machine (throwaway detached session):
- `tmux send-keys -t %54 'text' Enter` → delivered to a **detached** process's stdin (confirmed).
- `tmux list-panes -a` → enumerated every live `claude` pane with cwd across all sessions.
- `tmux display-message -t <pane>` → resolved pane id, `session:window.pane`, cmd, cwd.
- `tmux capture-pane -p -t <pane>` → reads a pane's current screen (to show the real prompt).

So: **discover** instances via tmux, **signal** via a side-channel file (independent of who's watching), and **route input** via `send-keys`.

## Architecture

```
            ┌──────────────────────────── SERVER (host) ────────────────────────────┐
 Claude #1  │  hook(Stop/Notification) ─┐                                            │
 (pane %42) │                           │ append JSON line                           │
 Claude #2  │  hook ────────────────────┼──────────▶  ~/.termforge/attention.jsonl   │
 (pane %13) │                           │                      ▲  │                  │
            │  tf-route.sh: list | focus | send | capture ─────┘  │ tail -F          │
            └─────────────────────────────────────────────────▲──┼──────────────────┘
                            SSH (existing connection)          │  │
            ┌───────────────────────────────────────────────  │  ▼  ─ PHONE (app) ──┐
            │  command exec channels  ◀───── routing ──────────┘  feed exec channel  │
            │  AgentInstanceRegistry  ◀─── parse events ──────────────────────────── │
            │     │            │                  │                                   │
            │  Agents UI   per-instance        tap→focus pane,                        │
            │  dashboard   notification        actions→send-keys                      │
            └───────────────────────────────────────────────────────────────────────┘
```

## Server components

### Event feed: `~/.termforge/attention.jsonl`
Append-only. Each Stop/Notification hook invocation appends **one** JSON line (single `printf`, atomic for our small lines). Schema (`v:1`):

```json
{"v":1,"ts":1748600000,"event":"notification","reason":"perm",
 "instance":"%42","socket":"/tmp/tmux-1000/default","target":"orchestrator:0.0",
 "session":"orchestrator","window":"0","window_name":"orchestrator",
 "cwd":"/home/ubuntu/projects/orchestration-tool","cmd":"claude",
 "prompt":["Allow edit to config.py?","1. Yes","2. Yes, and don't ask again","3. No"]}
```
- `reason`: `done` (Stop), `input` (Notification idle), `perm` (Notification permission prompt).
- `instance`: tmux pane id — the routing target. `null` when not in tmux.
- `socket`: tmux socket path (from `$TMUX`), so the helper targets the right server (`tmux -S`).
- `prompt`: the last non-empty lines from `capture-pane` (only for `perm`/`input`); lets the phone show Claude's *actual* options.

### Enhanced hook: `~/.claude/hooks/tf-attn.sh`
Reads Claude's hook JSON on stdin (`jq`) to get `hook_event_name` + message → derives `reason`. Resolves identity from `$TMUX_PANE` via `tmux display-message`; if `$TMUX` is unset, records a non-tmux instance (`instance:null`, notify-only). For `perm`/`input`, runs `tmux capture-pane -p -t "$TMUX_PANE"` and keeps the last ~8 non-blank lines as `prompt`. Appends the JSON line to the feed. Still emits the inline OSC marker (cheap; covers the non-tmux foreground case and keeps the marker out of the rendered screen via the existing scanner).

### Helper: `~/.termforge/tf-route.sh`
Thin wrapper over tmux so the phone runs one stable command:
- `list` → JSON array of live `claude` panes: `{instance,socket,target,session,window_name,cwd}` (from `tmux list-panes -a -F`).
- `focus <socket> <pane>` → `tmux -S <socket> switch-client -c <client> -t <session> \; select-window -t <win> \; select-pane -t <pane>` (best-effort; client identification tuned on device).
- `send <socket> <pane> <literal>` → `tmux -S <socket> send-keys -t <pane> -l <literal>` then `send-keys -t <pane> Enter` (literal mode avoids key-name interpretation).
- `sendkey <socket> <pane> <keyname>` → `send-keys -t <pane> <keyname>` (for `Escape`, `Enter`, etc.).
- `capture <socket> <pane>` → `tmux -S <socket> capture-pane -p -t <pane>` (re-read current prompt on demand).

### Setup script
`termforge-notify-setup.sh` (extended): installs the enhanced hook + `tf-route.sh`, creates `~/.termforge/`, registers Stop + Notification hooks. Idempotent; `--uninstall` reverses. `jq` required (already present).

## Phone components

### `AgentInstance` (model)
`instanceKey = host.id + ":" + socket + ":" + paneId`; fields: host, socket, paneId, target, sessionName, windowName, cwd, reason, prompt (list), lastSeenMs, isRoutable (false when paneId null). The registry key survives across events; a pane that dies is pruned.

### `AgentEventParser` (pure, unit-tested)
Parses one JSONL line → `AgentEvent` (or null on malformed). Tolerant of unknown fields and partial/garbled lines (a half-written line from a racing append is dropped, not fatal). This is the TDD core, mirroring `AttentionMarkerScanner`.

### `AgentInstanceRegistry` (per host)
On connect (when the agent-attention setting is on): seed with `tf-route.sh list`, replay the last ~50 feed lines for recent reasons, then follow live via a persistent `tail -n0 -F` exec channel. Exposes a `StateFlow<List<AgentInstance>>`. Periodic (or on-demand) reconcile via `list` prunes dead panes. Emits an "attention" callback when an instance transitions to `perm`/`input`/`done` (debounced per instance, 2s — reuse the existing pattern).

### Transport: concurrent channels
Extend the SSH transport so a session supports: one **long-lived feed channel** (`tail -F`) plus **on-demand command channels** (`tf-route.sh send|focus|capture|list`). MINA SSHD supports many exec channels per session; today's `openAuxStream(command)` opened a single reusable channel for voice — generalize to `openExec(command): AuxChannel` returning an independent channel with streamed stdout and a completion signal. The feed channel stays open for the connection's life; command channels are short-lived.

### Notifications & focus
`AttentionNotifier` extended to post **per-instance** notifications (id = instanceKey hash; title = `"<sessionName> · <reason phrase>"`, e.g. "orchestrator · needs permission"). Tap → deep-link carrying `instanceKey` → `MainActivity` → ensure/open the host connection, run `tf-route.sh focus`, navigate to that terminal. The feed becomes the notification source of truth; the inline `AttentionMarkerScanner` is downgraded to *marker-stripping only* (no notify) so we never double-fire.

### Actions & voice routing
Approve/Deny/Continue and voice-text resolve to a `send`/`sendkey` on the instance's `socket`+`paneId`:
- `perm` prompt: the UI shows the captured `prompt` options; selecting option N → `sendkey N` + Enter (default mapping: Approve=`1`, Deny=`Escape`, also offer the literal numbered options when present).
- `done`/`input`: free text (typed or voice) → `send <literal>` + Enter; Continue → `sendkey Enter`.

### Agents dashboard (UI)
A new "Agents" screen: live list of every instance across connected hosts with session name, cwd, reason badge, and inline actions (focus / approve-deny / reply). This is the phone-side fleet view and the data source the Layer-2 watch tile/list will mirror.

### Settings
Reuse `agentAttentionEnabled` (master toggle). The feed/registry only runs when it's on.

## Data flow (end to end)
1. Claude finishes/prompts → hook stamps identity+reason (+prompt) → appends to feed (works detached).
2. Phone's feed channel streams the line → `AgentEventParser` → `AgentInstanceRegistry` updates → per-instance notification (and Agents UI updates).
3. User taps → focus that pane (terminal jumps to it) — or acts directly from the notification/Agents UI.
4. Action → command channel runs `tf-route.sh send/sendkey <socket> <pane> …` → lands in the exact instance, attached or not.

## Error handling
- **Phone not connected to the host** → instance shows "offline"; actions disabled with a toast. (Phone is the relay; no connection = no routing.)
- **Feed channel drops** (network blip) → auto-reopen with backoff; on reopen, re-`list` + replay tail to resync. Notifications you missed while offline aren't replayed beyond the tail window (documented).
- **`send` fails** (pane died between notify and action) → command channel returns nonzero → toast "instance no longer running"; reconcile via `list` to prune.
- **Malformed/partial feed line** → parser drops it (never crashes the channel).
- **Non-tmux instance** → `isRoutable=false`: notify-only, actions hidden, with a one-line explainer.

## Identity & lifecycle
- Routing target = tmux pane id, stable for the pane's life within a tmux server. Pane death → pruned on next `list` reconcile; a new Claude in a new pane is a new instance (correct).
- `socket` distinguishes multiple tmux servers (rare but handled).

## Testing
- **Unit (JVM):** `AgentEventParser` — valid line, each reason, non-tmux (`instance:null`), malformed/partial line, unknown fields, prompt extraction. Mirrors the `AttentionMarkerScanner` test style.
- **Server integration:** `tf-route.sh` + hook exercised against a **throwaway** tmux session (never the user's real panes): list/identity/send-to-detached/capture/focus. (The core was already proven manually.)
- **Build:** full `assembleDebug` + existing suite stays green.
- **Device:** against the real 5-Claude fleet — detached instance pings, notification names the right one, tap lands in it, Approve routes to it, voice-text routes to it.

## Known limitations / deferred
- **Non-tmux + detached** = notify-only (no clean way to inject keystrokes into an arbitrary detached process without tmux). Accepted; the user's fleet is all tmux.
- **`focus` client retargeting** is the part most likely to need on-device tuning (identifying the phone's tmux client). Action-routing (the critical capability) does **not** depend on focus working perfectly.
- **Approve/Deny key fidelity** is tuned to Claude's standard permission UI; adjustable once observed live (same pattern as the OSC form).
- **Layer 2 (Wear OS watch)** consumes this registry + routing; specced separately after Layer 1 ships.
- **Missed-while-offline** events beyond the tail-replay window are not recovered (acceptable; attention is transient).

## Security
All of this runs as the user's own account over their already-authenticated SSH connection; `send-keys` types into the user's own agents. No new trust boundary is crossed. The feed file and helper live under `~/.termforge`/`~/.claude` with default perms; note that any local process under the same account could read them (same as the user's own shell history) — acceptable for a personal tool.

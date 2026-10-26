# Agent Attention Notifications — Design

**Date:** 2026-05-30
**Status:** Drafted for review.

A single, opt-in push notification — **"Claude needs you"** — fired when Claude Code (running in a
remote shell over SSH) finishes its turn or asks for input/permission, so the user can pocket the
phone while an agent works and get pulled in only when it matters. Tapping the notification opens that
session, where voice + swipe input are already available.

This is the keystone of the broader "agent cockpit / glanceability" direction; a Wear OS tile and
inline reply actions are explicitly **future surfaces built on this same signal** (out of scope here).

## Scope

**In:** one notification type ("Claude needs you · <host>"); detection via Claude Code hooks; tap →
open that session; opt-in (off by default) with a one-time remote setup; suppress when the user is
already viewing that session in the foreground.

**Deliberately deferred (not this spec):**
- Inline notification actions (approve / deny / voice-reply). v1 is *dumb notification → smart
  destination*: tap opens the session.
- Wear OS tile and the watch→phone→agent reply relay.
- Generic non-Claude "long task finished" pings (would be a later idle-detection add-on).
- Distinguishing *why* Claude wants you (done vs permission vs error). One signal covers all — the
  user opens the session regardless.

## Mechanism (verified against Claude Code docs)

Claude Code hooks **cannot** write to the terminal directly — a hook's stdout is captured by Claude
Code, and hook subprocesses have no controlling TTY on the SSH PTY. **But** a hook may return JSON
`{"suppressOutput": true, "terminalSequence": "<ESC seq>"}`, and Claude Code emits that escape to the
PTY itself. **OSC 777** (a notification sequence) is on Claude Code's whitelist. So:

```
Claude finishes / needs input
  → Stop / Notification hook fires
  → hook prints JSON with terminalSequence = an OSC 777 marker carrying a Hussh token
  → Claude Code emits that OSC to the PTY
  → bytes travel the existing SSH shell channel to Hussh
  → Hussh scans the inbound stream, detects the token, STRIPS the marker (so nothing renders),
    and posts the notification.
```

No new connection, no screen-scraping, no polling. The marker rides the stream Hussh already owns.

## Components

### 1. Server — `resources/server/hussh-notify-setup.sh` (one-time, opt-in)
Idempotent script that:
- Writes a hook script `~/.claude/hooks/hussh-attn.sh` that emits:
  `{"suppressOutput": true, "terminalSequence": "\033]777;hussh-attn\007"}`
  (exact OSC 777 payload to be confirmed against Claude Code's `terminalSequence` sanitizer during
  implementation — may require the `notify;` subform, e.g. `\033]777;notify;Hussh;hussh-attn\007`;
  the Hussh token `hussh-attn` is what the app scans for either way.)
- Merges into `~/.claude/settings.json` (via `jq`, preserving existing hooks):
  - `Stop` hook, matcher `""` → run `hussh-attn.sh`
  - `Notification` hook, matcher `""` → run `hussh-attn.sh`
- Prints how to enable the toggle in Hussh and how to remove it.

### 2. App — `AttentionMarkerScanner` (pure logic, unit-tested)
- `fun scan(chunk: ByteArray): Result(cleaned: ByteArray, detected: Boolean)`.
- Detects the OSC 777 + `hussh-attn` token; removes the full OSC sequence (`ESC ] … BEL`/`ESC \`) from
  the bytes so the emulator never renders it.
- Maintains a small **carry buffer** (the trailing bytes that could be the start of a split marker)
  across calls, so a marker spanning two network reads is still caught. Carry is bounded (≤ marker
  length); non-marker bytes always pass through unchanged and in order.

### 3. App — wire into `TerminalConnection.onNetworkInput`
- Before feeding the emulator: `val (cleaned, hit) = scanner.scan(bytes)`; feed `cleaned` to
  `terminalSession.onNetworkInput`; if `hit`, invoke `onAttention(sessionId)`.
- Scanner runs only when the feature is enabled (cheap no-op otherwise).

### 4. App — notification (reuses `SessionService` infra)
- New low-importance channel "Agent attention". On `onAttention(sessionId)`:
  - **Suppress** if Hussh is foreground AND the active terminal screen is that session.
  - **Debounce**: ignore a marker within ~2 s of the last notification for that session (Stop +
    Notification can both fire when Claude goes idle).
  - Post "Claude needs you · <nickname>"; tap → `PendingIntent` → MainActivity → navigate to
    `Routes.Terminal.build(sessionId)`.

### 5. App — opt-in setting
- `AppPreferences.agentAttentionEnabled` (default false) + a Settings switch ("Claude attention
  notifications") with a one-line pointer to the setup script. Off ⇒ scanner/notifier inert.

## Data flow
`Claude hook → OSC 777 → SSH stream → TerminalConnection.onNetworkInput → AttentionMarkerScanner
(strip + detect) → [cleaned bytes → emulator] + [detected → SessionManager.notifyAttention →
SessionService notification → tap → Terminal route]`.

## Error handling / edge cases
- **Marker split across reads** → carry buffer (Component 2).
- **Duplicate markers** (Stop + Notification) → per-session debounce.
- **Already looking** → foreground+active-session suppression.
- **Hook not installed / feature off** → simply no markers / inert scanner; raw terminal untouched.
- **Multiple sessions** → marker arrives on its own session's stream; notification names and
  deep-links to that session.
- **Non-SSH transports / non-Claude shells** → no markers ever emitted; nothing happens.

## Testing
- **Unit (JVM):** `AttentionMarkerScannerTest` — detects the marker; strips it exactly; passes other
  bytes through unchanged; handles a marker split across two chunks; ignores a lookalike that isn't
  our token; debounce logic.
- **Device:** install the hook via the setup script, run Claude Code on the remote, confirm a real
  turn-completion fires one notification that opens the session, and that nothing renders in the
  terminal. (Server-side: confirm `hussh-attn.sh` emits valid JSON and the hook is registered.)

## Future surfaces (out of scope, built on this signal)
Wear OS tile showing attention/status; reply-from-watch relayed phone→agent; inline approve/voice
actions on the notification; generic long-task idle pings.

# Agent Attention Notifications — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An opt-in "Claude needs you · <host>" notification, fired when Claude Code (over SSH) finishes a turn or needs input, that opens the session on tap.

**Architecture:** A Claude Code hook returns a `terminalSequence` (OSC 777 marker); Claude emits it to the PTY; it rides the existing SSH stream; `TerminalConnection` runs a stateful scanner over inbound bytes that strips the marker and signals attention; `SessionManager` debounces + suppresses-when-visible and posts a notification via `AttentionNotifier`; tapping deep-links to the terminal route. Feature is off by default and gated behind a setting + a one-time remote hook setup.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, AndroidX notifications, JUnit. Branch: `feature/agent-attention-notifications`.

Spec: `docs/superpowers/specs/2026-05-30-agent-attention-notifications-design.md`.

---

## File structure
- Create `app/src/main/java/com/termforge/session/AttentionMarkerScanner.kt` — pure marker scan/strip (unit-tested).
- Create `app/src/test/java/com/termforge/session/AttentionMarkerScannerTest.kt` — its tests.
- Create `app/src/main/java/com/termforge/session/AttentionNotifier.kt` — notification channel + post + deep-link intent.
- Modify `app/src/main/java/com/termforge/data/prefs/AppPreferences.kt` — `agentAttentionEnabled` flag.
- Modify `app/src/main/java/com/termforge/session/TerminalConnection.kt` — run scanner in inbound path; `onAttention` ctor param.
- Modify `app/src/main/java/com/termforge/session/SessionManager.kt` — `visibleSessionId`; `onConnectionAttention` (debounce + suppress); pass `onAttention`; pass enabled flag.
- Modify `app/src/main/java/com/termforge/MainActivity.kt` — handle the deep-link intent → navigate to the terminal route.
- Modify `app/src/main/java/com/termforge/terminal/TerminalScreen.kt` — set/clear `SessionManager.visibleSessionId` on lifecycle resume/pause.
- Modify `app/src/main/java/com/termforge/ui/settings/SettingsScreen.kt` + `SettingsViewModel.kt` — toggle.
- Create `resources/server/termforge-notify-setup.sh` — installs the Claude Code hook.

**The marker (shared contract between the hook and the scanner):** `ESC ] 777 ; tf-attn BEL` = Kotlin `"]777;tf-attn"`. If Claude Code's `terminalSequence` sanitizer rejects that exact body, switch BOTH the hook output and `AttentionMarkerScanner.MARKER` to the `notify;` subform `"]777;notify;TermForge;tf-attn"` — they must match byte-for-byte.

---

### Task 1: AttentionMarkerScanner (TDD)

**Files:**
- Create: `app/src/main/java/com/termforge/session/AttentionMarkerScanner.kt`
- Test: `app/src/test/java/com/termforge/session/AttentionMarkerScannerTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.termforge.session

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttentionMarkerScannerTest {
    private val M = "]777;tf-attn".toByteArray(Charsets.US_ASCII)
    private fun bytes(s: String) = s.toByteArray(Charsets.US_ASCII)

    @Test fun `plain output passes through untouched and undetected`() {
        val r = AttentionMarkerScanner().scan(bytes("ubuntu@vm:~$ "))
        assertEquals("ubuntu@vm:~$ ", String(r.cleaned, Charsets.US_ASCII))
        assertFalse(r.detected)
    }

    @Test fun `a marker alone is detected and stripped`() {
        val r = AttentionMarkerScanner().scan(M)
        assertEquals(0, r.cleaned.size)
        assertTrue(r.detected)
    }

    @Test fun `marker embedded in output is stripped, surrounding bytes kept in order`() {
        val r = AttentionMarkerScanner().scan(bytes("a") + M + bytes("b"))
        assertArrayEquals(bytes("ab"), r.cleaned)
        assertTrue(r.detected)
    }

    @Test fun `marker split across two chunks is still caught`() {
        val s = AttentionMarkerScanner()
        val first = s.scan(bytes("x") + M.copyOfRange(0, 8))   // partial marker at end
        assertArrayEquals(bytes("x"), first.cleaned)
        assertFalse(first.detected)
        val second = s.scan(M.copyOfRange(8, M.size) + bytes("y"))
        assertArrayEquals(bytes("y"), second.cleaned)
        assertTrue(second.detected)
    }

    @Test fun `a different OSC 777 is not our marker and passes through`() {
        val other = bytes("]777;notify;Other;hi")
        val r = AttentionMarkerScanner().scan(other)
        assertArrayEquals(other, r.cleaned)
        assertFalse(r.detected)
    }
}
```

- [ ] **Step 2: Run the tests, verify they FAIL (unresolved AttentionMarkerScanner)**

Run: `ANDROID_HOME=/home/ubuntu/Android/Sdk ./gradlew :app:testDebugUnitTest --tests "com.termforge.session.AttentionMarkerScannerTest"`
Expected: FAIL — compile error / `AttentionMarkerScanner` unresolved.

- [ ] **Step 3: Implement the scanner**

```kotlin
package com.termforge.session

/**
 * Scans an inbound terminal byte stream for the TermForge attention marker (an OSC 777 sequence
 * Claude Code emits via a hook's terminalSequence). Strips the marker so the emulator never renders
 * it, and reports whether one was seen. Stateful: holds back a trailing partial marker so a marker
 * split across two network reads is still caught.
 */
class AttentionMarkerScanner {
    class Result(val cleaned: ByteArray, val detected: Boolean)

    private var carry = ByteArray(0)

    fun scan(input: ByteArray): Result {
        val buf = carry + input
        val out = java.io.ByteArrayOutputStream(buf.size)
        var detected = false
        var i = 0
        while (i < buf.size) {
            if (matchesAt(buf, i)) {
                detected = true
                i += MARKER.size
            } else {
                // If a proper prefix of the marker starts here and runs to the end of buf, hold it.
                val tail = buf.size - i
                if (tail < MARKER.size && isPrefixOfMarker(buf, i)) break
                out.write(buf[i].toInt())
                i++
            }
        }
        carry = if (i < buf.size) buf.copyOfRange(i, buf.size) else ByteArray(0)
        return Result(out.toByteArray(), detected)
    }

    private fun matchesAt(buf: ByteArray, at: Int): Boolean {
        if (at + MARKER.size > buf.size) return false
        for (k in MARKER.indices) if (buf[at + k] != MARKER[k]) return false
        return true
    }

    /** True if buf[at until end] equals the first (size) bytes of the marker (a partial prefix). */
    private fun isPrefixOfMarker(buf: ByteArray, at: Int): Boolean {
        for (k in 0 until (buf.size - at)) if (buf[at + k] != MARKER[k]) return false
        return true
    }

    companion object {
        val MARKER: ByteArray = "]777;tf-attn".toByteArray(Charsets.US_ASCII)
    }
}
```

- [ ] **Step 4: Run the tests, verify they PASS**

Run: `ANDROID_HOME=/home/ubuntu/Android/Sdk ./gradlew :app:testDebugUnitTest --tests "com.termforge.session.AttentionMarkerScannerTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/termforge/session/AttentionMarkerScanner.kt app/src/test/java/com/termforge/session/AttentionMarkerScannerTest.kt
git commit -m "Add AttentionMarkerScanner (strip + detect OSC 777 marker)"
```

---

### Task 2: Setting — `agentAttentionEnabled`

**Files:** Modify `app/src/main/java/com/termforge/data/prefs/AppPreferences.kt`

- [ ] **Step 1: Add the field to `AppSettings`** (next to `glideTypingEnabled`):

```kotlin
    val agentAttentionEnabled: Boolean = false,
```

- [ ] **Step 2: Add the key** (in the `Keys` object, next to `GLIDE_TYPING`):

```kotlin
        val AGENT_ATTENTION = booleanPreferencesKey("agent_attention_enabled")
```

- [ ] **Step 3: Map it** (in the `settings` flow `map { p -> AppSettings(...) }`, next to `glideTypingEnabled`):

```kotlin
            agentAttentionEnabled = p[Keys.AGENT_ATTENTION] ?: false,
```

- [ ] **Step 4: Add the setter** (next to `setGlideTyping`):

```kotlin
    suspend fun setAgentAttention(v: Boolean) = edit { it[Keys.AGENT_ATTENTION] = v }
```

- [ ] **Step 5: Compile + commit**

Run: `ANDROID_HOME=/home/ubuntu/Android/Sdk ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.
```bash
git add app/src/main/java/com/termforge/data/prefs/AppPreferences.kt
git commit -m "Add agentAttentionEnabled preference"
```

---

### Task 3: AttentionNotifier

**Files:** Create `app/src/main/java/com/termforge/session/AttentionNotifier.kt`

- [ ] **Step 1: Implement it**

```kotlin
package com.termforge.session

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.termforge.MainActivity
import com.termforge.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Posts the opt-in "Claude needs you" notification; tap deep-links to the session. */
@Singleton
class AttentionNotifier @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
) {
    fun notifyAttention(sessionId: String, title: String) {
        ensureChannel()
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(EXTRA_SESSION_ID, sessionId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pending = PendingIntent.getActivity(
            context, sessionId.hashCode(), tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Claude needs you")
            .setContentText(title)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        // POST_NOTIFICATIONS is already requested by the app; guard for the lint check.
        runCatching { NotificationManagerCompat.from(context).notify(sessionId.hashCode(), n) }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Agent attention", NotificationManager.IMPORTANCE_HIGH)
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    companion object {
        const val CHANNEL_ID = "termforge_agent_attention"
        const val EXTRA_SESSION_ID = "com.termforge.extra.SESSION_ID"
    }
}
```

- [ ] **Step 2: Compile + commit**

Run: `ANDROID_HOME=/home/ubuntu/Android/Sdk ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.
```bash
git add app/src/main/java/com/termforge/session/AttentionNotifier.kt
git commit -m "Add AttentionNotifier (channel + deep-link notification)"
```

---

### Task 4: SessionManager — attention handling + visible-session tracking

**Files:** Modify `app/src/main/java/com/termforge/session/SessionManager.kt`

- [ ] **Step 1: Inject AttentionNotifier + AppPreferences** — add to the constructor (it already injects `appPrefs`? it injects `AppPreferences` — confirm; add `attentionNotifier`):

```kotlin
    private val attentionNotifier: AttentionNotifier,
```

- [ ] **Step 2: Add visible-session tracking + debounce state** (as class fields):

```kotlin
    /** The session the user is actively viewing (set by the terminal screen on resume). */
    @Volatile var visibleSessionId: String? = null
    private val lastAttentionAtMs = java.util.concurrent.ConcurrentHashMap<String, Long>()
```

- [ ] **Step 3: Add the attention handler** (near `onConnectionTerminated`). Uses `android.os.SystemClock.elapsedRealtime()` (monotonic; `System.currentTimeMillis` is fine too):

```kotlin
    private fun onConnectionAttention(sessionId: String) {
        val conn = _sessions.value.firstOrNull { it.sessionId == sessionId } ?: return
        if (visibleSessionId == sessionId) return // already looking at it
        val now = android.os.SystemClock.elapsedRealtime()
        val last = lastAttentionAtMs[sessionId] ?: 0L
        if (now - last < 2000L) return // debounce Stop + Notification double-fire
        lastAttentionAtMs[sessionId] = now
        attentionNotifier.notifyAttention(sessionId, conn.title)
    }
```

- [ ] **Step 4: Pass `onAttention` + enabled flag when creating the connection** — in `connect`/`startConnection`, where `TerminalConnection(...)` is built (currently passes `onTerminated = ::onConnectionTerminated`), add:

```kotlin
            onAttention = { id -> onConnectionAttention(id) },
            attentionEnabled = { runBlocking { appPrefs.settings.first().agentAttentionEnabled } },
```

(If `appPrefs`/`settings.first()` is awkward there, read the flag once into a local from the already-fetched `settings` in `startConnection` and pass `attentionEnabled = { settingsSnapshot }`. Keep it a lambda so a later toggle is respected without reconnect.)

- [ ] **Step 5: Compile + commit**

Run: `ANDROID_HOME=/home/ubuntu/Android/Sdk ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (after Task 5 adds the ctor params, this fully resolves — if compiling Task 4 alone errors on the new ctor args, do Task 5 first then compile).
```bash
git add app/src/main/java/com/termforge/session/SessionManager.kt
git commit -m "SessionManager: attention handling + visible-session tracking"
```

---

### Task 5: TerminalConnection — run the scanner + onAttention

**Files:** Modify `app/src/main/java/com/termforge/session/TerminalConnection.kt`

- [ ] **Step 1: Add ctor params** (after `onTerminated`):

```kotlin
    private val onAttention: (String) -> Unit = {},
    private val attentionEnabled: () -> Boolean = { false },
```

- [ ] **Step 2: Add a scanner field** (near the other fields):

```kotlin
    private val attentionScanner = AttentionMarkerScanner()
```

- [ ] **Step 3: Run it in the inbound path** — replace the inbound collect (currently `t.inbound.collect { bytes -> terminalSession.onNetworkInput(bytes, 0, bytes.size) }`) with:

```kotlin
        val inJob = scope.launch {
            t.inbound.collect { bytes ->
                if (attentionEnabled()) {
                    val r = attentionScanner.scan(bytes)
                    terminalSession.onNetworkInput(r.cleaned, 0, r.cleaned.size)
                    if (r.detected) onAttention(sessionId)
                } else {
                    terminalSession.onNetworkInput(bytes, 0, bytes.size)
                }
            }
        }
```

- [ ] **Step 4: Compile + commit**

Run: `ANDROID_HOME=/home/ubuntu/Android/Sdk ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.
```bash
git add app/src/main/java/com/termforge/session/TerminalConnection.kt app/src/main/java/com/termforge/session/SessionManager.kt
git commit -m "Wire AttentionMarkerScanner into the inbound stream"
```

---

### Task 6: Deep-link + visible-session lifecycle

**Files:** Modify `app/src/main/java/com/termforge/MainActivity.kt`, `app/src/main/java/com/termforge/terminal/TerminalScreen.kt`

- [ ] **Step 1: MainActivity — expose the tapped session id as state and navigate.** Hold the session id from the launch/new intent and, once the NavController exists, navigate to the terminal route. Add near `onCreate`:

```kotlin
    private val pendingSessionId = mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(com.termforge.session.AttentionNotifier.EXTRA_SESSION_ID)
            ?.let { pendingSessionId.value = it }
    }
```

In `onCreate`, seed it from the launch intent: `pendingSessionId.value = intent.getStringExtra(AttentionNotifier.EXTRA_SESSION_ID)`. Inside `setContent`, after `rememberNavController()`:

```kotlin
                    LaunchedEffect(pendingSessionId.value) {
                        pendingSessionId.value?.let { sid ->
                            navController.navigate(com.termforge.nav.Routes.Terminal.build(sid))
                            pendingSessionId.value = null
                        }
                    }
```

(`mutableStateOf` import: `androidx.compose.runtime.mutableStateOf`; `LaunchedEffect`: `androidx.compose.runtime.LaunchedEffect`.)

- [ ] **Step 2: TerminalScreen — set/clear visibleSessionId on lifecycle.** Inject access to the SessionManager via the ViewModel (add a passthrough `fun setVisible(visible: Boolean)` on `TerminalViewModel` that sets `sessionManager.visibleSessionId = if (visible) sessionId else null`). In `TerminalScreen`, add:

```kotlin
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            when (e) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> viewModel.setVisible(true)
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> viewModel.setVisible(false)
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs); viewModel.setVisible(false) }
    }
```

Add to `TerminalViewModel`:

```kotlin
    fun setVisible(visible: Boolean) { sessionManager.visibleSessionId = if (visible) sessionId else null }
```

- [ ] **Step 3: Compile + commit**

Run: `ANDROID_HOME=/home/ubuntu/Android/Sdk ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.
```bash
git add app/src/main/java/com/termforge/MainActivity.kt app/src/main/java/com/termforge/terminal/TerminalScreen.kt app/src/main/java/com/termforge/terminal/TerminalViewModel.kt
git commit -m "Deep-link attention notification to its session + track visible session"
```

---

### Task 7: Settings toggle

**Files:** Modify `app/src/main/java/com/termforge/ui/settings/SettingsViewModel.kt`, `SettingsScreen.kt`

- [ ] **Step 1: ViewModel setter** (next to `setGlideTyping`):

```kotlin
    fun setAgentAttention(v: Boolean) = viewModelScope.launch { prefs.setAgentAttention(v) }
```

- [ ] **Step 2: Settings UI** — under the "Voice (push-to-talk)" section (or a new "Agent" line):

```kotlin
            Section("Agent")
            ToggleRow("Claude attention notifications", s.agentAttentionEnabled, viewModel::setAgentAttention)
            Text(
                "Notifies you when Claude Code on a connected host finishes or needs you. Requires " +
                    "running resources/server/termforge-notify-setup.sh on that machine.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
```

- [ ] **Step 3: Compile + commit**

Run: `ANDROID_HOME=/home/ubuntu/Android/Sdk ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.
```bash
git add app/src/main/java/com/termforge/ui/settings/SettingsViewModel.kt app/src/main/java/com/termforge/ui/settings/SettingsScreen.kt
git commit -m "Settings: agent attention notification toggle"
```

---

### Task 8: Server hook setup script

**Files:** Create `resources/server/termforge-notify-setup.sh`

- [ ] **Step 1: Write the script** (idempotent; needs `jq`):

```bash
#!/usr/bin/env bash
# TermForge agent-attention setup. Adds a Claude Code hook that emits an OSC 777 marker when Claude
# finishes a turn or needs you; TermForge watches the SSH stream for it and notifies your phone.
set -euo pipefail

HOOK="$HOME/.claude/hooks/tf-attn.sh"
SETTINGS="$HOME/.claude/settings.json"
mkdir -p "$HOME/.claude/hooks"

cat > "$HOOK" <<'EOF'
#!/usr/bin/env bash
# Emit the TermForge attention marker to the terminal via Claude Code's terminalSequence.
printf '%s' '{"suppressOutput": true, "terminalSequence": "]777;tf-attn"}'
EOF
chmod +x "$HOOK"

command -v jq >/dev/null || { echo "Please install jq (sudo apt install jq)"; exit 1; }
[ -f "$SETTINGS" ] || echo '{}' > "$SETTINGS"
tmp="$(mktemp)"
jq --arg cmd "$HOOK" '
  .hooks = (.hooks // {})
  | .hooks.Stop = [{"matcher":"","hooks":[{"type":"command","command":$cmd}]}]
  | .hooks.Notification = [{"matcher":"","hooks":[{"type":"command","command":$cmd}]}]
' "$SETTINGS" > "$tmp" && mv "$tmp" "$SETTINGS"

echo "Installed. Now enable 'Claude attention notifications' in TermForge Settings."
echo "Verify the hook emits valid JSON:  bash $HOOK ; echo"
```

- [ ] **Step 2: Verify the script is valid bash + the hook emits valid JSON**

Run: `bash -n resources/server/termforge-notify-setup.sh && echo "syntax ok"`
Expected: `syntax ok`.

- [ ] **Step 3: Commit**

```bash
chmod +x resources/server/termforge-notify-setup.sh
git add resources/server/termforge-notify-setup.sh
git commit -m "Add server setup script for agent-attention hook"
```

---

### Task 9: Integrate, build APK, device-verify

- [ ] **Step 1: Full build**

Run: `ANDROID_HOME=/home/ubuntu/Android/Sdk ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL; APK at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 2: Run the full unit suite** (scanner + existing 44)

Run: `ANDROID_HOME=/home/ubuntu/Android/Sdk ./gradlew :app:testDebugUnitTest`
Expected: all pass.

- [ ] **Step 3: Confirm the exact OSC 777 form against Claude Code** — run the installed hook script and confirm Claude Code accepts the `terminalSequence` (sanity: `bash ~/.claude/hooks/tf-attn.sh` prints valid JSON). If Claude rejects the body, switch the marker in both `AttentionMarkerScanner.MARKER` and the hook to the `notify;` subform (see file-structure note) and re-run Task 1 tests.

- [ ] **Step 4: Device verify** (when a device is reachable/unlocked): install; run `termforge-notify-setup.sh` on the host; enable the toggle; connect; start `claude`; let it finish a turn → confirm exactly one "Claude needs you" notification fires, tapping opens the session, and nothing renders in the terminal; confirm no notification when you're already viewing that session.

- [ ] **Step 5: Finalize**

```bash
git add -A && git commit -m "Agent attention notifications: integrate + verify"
```

---

## Self-review
- **Spec coverage:** hook mechanism (Tasks 7) ✓; scan+strip (1) ✓; one notification + tap-to-open (3, 6) ✓; opt-in setting (2, 7) ✓; suppress-when-visible (4, 6) ✓; debounce (4) ✓; marker-split handling (1) ✓; multi-session (per-session callback + per-session debounce/notify id) ✓. Deferred items (inline actions, Wear OS, generic long-task) correctly absent.
- **Placeholders:** none — every step has concrete code/commands. The one deferred detail (exact OSC body) has an explicit resolution path (Task 9 Step 3 + the file-structure note), keyed off a single `MARKER` constant the hook must match.
- **Type consistency:** `AttentionMarkerScanner.scan` → `Result(cleaned, detected)` used consistently (Tasks 1, 5); `onConnectionAttention`/`onAttention`/`notifyAttention`/`visibleSessionId`/`EXTRA_SESSION_ID`/`setAgentAttention` names consistent across tasks.

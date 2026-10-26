# Watch Companion (Layer 2) — Design + Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: executing-plans (inline). Builds on the merged Layer-1 multi-instance routing engine. Design was approved in brainstorming (hybrid); this folds in the later refinements.

**Goal:** A Wear OS app that mirrors the per-session "Claude needs you / responded" signal to the wrist, lets you read the response, and reply **by voice**, routed to the exact Claude instance.

**Approved design (hybrid) + refinements:**
- **Notification on the wrist** — already free: the per-instance phone notification (Layer 1) auto-bridges to a paired watch, *with* the Approve/Deny actions we built. No watch code needed for that.
- **Watch app** adds what notifications can't: a glanceable **Tile**, an instance **list** to read Claude's latest output, **push-and-hold voice reply**, and a **transparent toggle button bar hugging the lower bezel** (hide/show like the phone keybar).
- **Voice = on-watch STT → text (NOT Claude's `/voice`).** Verified: `/voice tap` auto-submits at 3+ words, so it can't give review-before-send. Instead the watch uses Android `RecognizerIntent` to transcribe locally, sends the **text** to the phone, and the phone routes it via Layer-1 `AgentRouter.sendText` **without Enter** — so it lands in Claude's input and waits. An explicit **Send** sends Enter; otherwise you review/correct on the phone first.
- **Per-session watch-enable** — separate from the phone mute. A `watchEnabledSessions` set; only those sessions push to the watch.

**Architecture (two hops, reusing Layer 1):**
```
PHONE (:app, Layer-1 engine)                        WATCH (:wear, new)
  AgentInstance registry ──serialize JSON──▶ DataClient "/hussh/agents" ──▶ instance list UI
  SessionManager.routeText/approve/focus ◀── MessageClient "/hussh/cmd" ◀── tap / Send / voice-text
            (WearBridge: WearableListenerService + DataClient)
```
- **No shared module:** instances are serialized to JSON and pushed as a DataItem; commands are JSON `MessageClient` messages. The watch has its own tiny DTO.
- **Data Layer requires same applicationId + signing** on both apps → the `:wear` module uses `applicationId "com.hussh"` with the same `.debug` suffix and the default debug keystore.

---

## Phase W1: Module scaffold (the riskiest infra — compile gate)

**Files:** `settings.gradle.kts` (+`include(":wear")`), `gradle/libs.versions.toml` (+wear deps), `wear/build.gradle.kts`, `wear/src/main/AndroidManifest.xml`, `wear/src/main/java/com/hussh/wear/WearMainActivity.kt`, `wear/src/main/res/values/strings.xml`.

- [ ] **Add deps to `libs.versions.toml`** under `[versions]`: `playServicesWearable = "18.2.0"`, `wearCompose = "1.4.1"`, `wearTiles = "1.4.1"`, `wearComplications = "1.2.1"`, `horologist = "0.6.17"` (optional, skip if unused). Under `[libraries]`:
  - `play-services-wearable = { group = "com.google.android.gms", name = "play-services-wearable", version.ref = "playServicesWearable" }`
  - `androidx-wear-compose-material = { group = "androidx.wear.compose", name = "compose-material", version.ref = "wearCompose" }`
  - `androidx-wear-compose-foundation = { group = "androidx.wear.compose", name = "compose-foundation", version.ref = "wearCompose" }`
  - `androidx-wear-tiles = { group = "androidx.wear.tiles", name = "tiles", version.ref = "wearTiles" }`
  - `androidx-wear-tiles-material = { group = "androidx.wear.tiles", name = "tiles-material", version.ref = "wearTiles" }`

- [ ] **`wear/build.gradle.kts`:** plugins `android.application`, `kotlin.android`, `kotlin.compose`, `kotlin.serialization`. `namespace = "com.hussh.wear"`, `compileSdk 35`, `minSdk 30`, `targetSdk 34`, `applicationId "com.hussh"`, debug `applicationIdSuffix ".debug"`. `buildFeatures.compose = true`. Java 17. Deps: compose BOM (platform), `androidx.activity.compose`, wear-compose-material/foundation, `play-services-wearable`, `kotlinx-serialization-json`, `kotlinx-coroutines-android`, `kotlinx-coroutines-play-services` (for `.await()` on Tasks).

- [ ] **`wear/src/main/AndroidManifest.xml`:** `<uses-feature android:name="android.hardware.type.watch"/>`, `RECORD_AUDIO` + `INTERNET` perms; `<application>` with `WearMainActivity` (exported, LAUNCHER + the `android.intent.category.LEANBACK_LAUNCHER`? no — wear uses normal LAUNCHER). Standalone meta: `<meta-data android:name="com.google.android.wearable.standalone" android:value="true"/>`.

- [ ] **`WearMainActivity.kt`:** minimal Wear Compose activity showing "Hussh" placeholder (Scaffold from `androidx.wear.compose.material`). Confirms the toolchain.

- [ ] **Compile gate:** `ANDROID_HOME=/home/ubuntu/Android/Sdk ./gradlew :wear:assembleDebug` → BUILD SUCCESSFUL. Commit.

## Phase W2: Phone→watch instance push + watch list

- [ ] **Shared JSON shape:** phone serializes `List<AgentInstance>` → JSON (reuse a `@Serializable WireInstance` dto: key, session, reason, cwd, prompt, routable). Push via `DataClient.putDataItem` at path `/hussh/agents` whenever `SessionManager.instances` changes AND the session is `watchEnabled`.
- [ ] **`WearBridge` (phone, `:app`):** a class (held by SessionManager or a small Hilt singleton) that collects `instances`, filters to watch-enabled, serializes, and `Wearable.getDataClient(context).putDataItem(...)`. Coroutines via `.await()`.
- [ ] **Watch:** a `WearableListenerService` (or `DataClient.addListener` in the activity's VM) parses `/hussh/agents` → `StateFlow<List<WireInstance>>`; the activity renders a `ScalingLazyColumn` of cards (session + reason + last prompt line). Compile (`:app` + `:wear`). Commit.

## Phase W3: Watch → phone commands (focus/approve/deny) + per-session watch-enable

- [ ] **Watch command send:** tapping a card / action → `MessageClient.sendMessage(nodeId, "/hussh/cmd", json{action,instanceKey,text})` to the phone node (resolve via `Wearable.getNodeClient`).
- [ ] **Phone `WearBridge` receive:** a `WearableListenerService` (manifest-registered, path `/hussh/cmd`) → parse → `SessionManager.instanceByKey` → route `approve/deny/focus/sendText`.
- [ ] **Per-session watch-enable:** `AppPreferences.watchEnabledSessions: Set<String>` + a toggle on each Agents row (phone) next to the mute toggle ("show on watch"). `WearBridge` only pushes enabled sessions. Compile. Commit.

## Phase W4: Voice reply (STT → text, review-before-send)

- [ ] **Push-and-hold mic** on the watch: a button that on press launches `RecognizerIntent` (ACTION_RECOGNIZE_SPEECH) / or `SpeechRecognizer` for hold semantics; on result, sends `/hussh/cmd` `{action:"replyText", instanceKey, text}`.
- [ ] **Phone:** `replyText` → `SessionManager.routeText(inst, text)` which uses `AgentRouter.sendText` **without Enter** (verify: split `sendText` into `typeText` (no Enter) + `send` (Enter); watch voice uses `typeText`; an explicit Send/`cont` sends Enter). Add `AgentRouter.typeText` and `SessionManager.routeType`.
- [ ] **Watch Send action:** a "Send" button → `/hussh/cmd {action:"send"}` → `SessionManager.routeContinue` (Enter). Compile. Commit.

## Phase W5: Transparent toggle button bar + Tile (glance)

- [ ] **Bezel-hugging button bar:** a translucent `Row` pinned to the bottom of the watch UI (Approve / Deny / Mic / Send), toggled hidden/shown by a tap on the body (mirror the phone keybar's tap-to-toggle).
- [ ] **Tile (`TileService`):** a glanceable tile listing the top needs-you instance(s) with a tap to open the app. (Most API-finicky; include if it compiles cleanly, else scaffold minimal.) Compile. Commit.

## Phase W6: Build, install, device-verify (needs watch ADB)
- [ ] `:wear:assembleDebug` → wear APK. `adb -s <watch> install -r wear-debug.apk`.
- [ ] Verify on the paired watch: instances appear; tap focuses on phone; voice transcribes + lands in Claude input (no auto-send); Send commits; bar toggles. Tune.

---

## Notes / risks
- **Untestable until the watch ADB arrives** — every phase compiles, but Data Layer pairing, voice, and tile rendering need the device. Flag clearly.
- **Data Layer pairing** needs same applicationId + signing key on phone+watch (handled via matching `.debug` suffix + default debug keystore).
- The notification-on-wrist + Approve/Deny already work via Layer 1's auto-bridged notification — so even before the watch app is verified, the wrist gets the core ping.

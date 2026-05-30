# TermForge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a production-grade native Android terminal client (SSH/SFTP/SCP/Telnet/raw-TCP) — a JuiceSSH successor — that compiles against the local toolchain.

**Architecture:** Single-activity Jetpack Compose app (`:app`) + two vendored Termux library modules (`:terminal-emulator`, `:terminal-view`). A `Transport` interface hides each protocol; a foreground `SessionService` owns live sessions and drives a `TerminalEmulator` from network bytes. Hilt DI, Room+SQLCipher persistence, Keystore-backed credential crypto, DataStore prefs.

**Tech Stack:** Kotlin 2.0.21, AGP 8.7.x, Gradle 8.9, Compose (BOM), Material 3, Hilt, Room, `sqlcipher-android`, Apache MINA SSHD, BouncyCastle, androidx.biometric, androidx.navigation-compose.

**Test strategy:** JVM unit tests (JUnit) for pure logic — `AnsiComposer`, `KeyBarState`, `TelnetNegotiator`, `CredentialCrypto` (via a pluggable cipher), fingerprinting. Compose UI + device-bound transport (real SSH handshake, biometric, FG-service longevity) are verified by **compilation + reasoning**, marked needs-device in `STATUS.md`. No fake "it runs on a device" claims.

---

## File structure

See `docs/superpowers/specs/2026-05-28-termforge-design.md §1`. Locked in there.

---

## Task sequence (maps to tracked tasks #2–#11; compile checkpoint ⛳ after each phase)

### Task 2 — Scaffold (⛳ empty app assembles)
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `local.properties`, `gradle.properties`, `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/src/main/AndroidManifest.xml`, `TermForgeApp`, `MainActivity`, `nav/Routes.kt` + `nav/NavGraph.kt`, `ui/theme/*`, placeholder screens.
- Generate Gradle wrapper from the local 8.9 dist.
- ⛳ `assembleDebug` (placeholder NavHost).

### Task 3 — Vendor Termux modules (⛳ modules compile)
- `git clone --depth 1 termux-app` to /tmp; copy `terminal-emulator/` and `terminal-view/` source + their `build.gradle` (convert to our versions); add to `settings.gradle.kts`.
- Identify the `TerminalView` ↔ session contract; plan the network adapter (no PTY fork).

### Task 4 — Data layer (⛳ compiles; crypto unit-tested)
- Entities, DAOs, converters, `TermForgeDatabase`, SQLCipher `SupportFactory`, `CredentialCrypto`, `KeyTools` (BouncyCastle), `AppPreferences` (DataStore), repositories.
- TDD: `CredentialCryptoTest` (encrypt→decrypt round-trip with an injectable cipher), `FingerprintTest` (SHA256 base64).

### Task 5 — Transport (⛳ compiles; negotiator/composer unit-tested)
- `Transport` + `TransportFactory`; `SshTransport` (MINA), `TelnetTransport` + `TelnetNegotiator`, `RawTcpTransport`; `sftp/SftpClient`; `hostkey/HostKeyVerifier` (TOFU); `forward/PortForwarder`.
- TDD: `TelnetNegotiatorTest` (IAC DO TTYPE → WILL TTYPE + subneg; NAWS), `AnsiComposerTest` lives in keybar but verify arrow/app-cursor here too.

### Task 6 — SessionService (⛳ compiles)
- Foreground service, `SessionManager`, `TerminalSession` (emulator bridge), keep-alive, backoff reconnect, notification, battery-opt prompt, persisted session descriptors for process-death recovery.

### Task 7 — Keyboard helper bar (⛳ compiles; semantics + ANSI unit-tested)
- `KeyBarModel`, `AnsiComposer`, `KeyBarState` (350ms latch, one-shot, hold-release, autorepeat 400→50ms + accel), `KeyBarRow`, `KeyBarEditor`.
- TDD: `KeyBarStateTest` (tap=one-shot consumed by next key; hold≥350ms=latched; latched persists; hold-again releases; repeatable accel), `AnsiComposerTest` (Ctrl+C=0x03, Alt-x=ESC x, ↑ normal=ESC[A / app=ESC OA, etc.).

### Task 8 — Terminal screen (⛳ compiles)
- `TerminalScreen`, `AndroidView` bridge, toolbar, status strip, copy-mode, paste, volume+pinch font (persist), session tabs, URL tap, bell, snippets sheet.

### Task 9 — CRUD + browser screens (⛳ compiles)
- Hosts list (search/group/reorder/import/export), Host editor, Identity manager (keygen/import/fingerprint/export), Snippets, SFTP browser + transfer queue, Active sessions sheet, ViewModels, nav wiring.

### Task 10 — Theming + settings (⛳ compiles)
- App themes + terminal themes; Settings screen; bundled monospace font.

### Task 11 — Integrate + document (⛳ full `assembleDebug` green)
- Close DI graph; iterate to green; `README.md` + `STATUS.md` mapping every DoD item to implemented/partial/needs-device.

---

## Self-review notes
- Spec coverage: all 8 routes, all entities, signature keybar semantics, volume-font, TOFU, themes, SFTP, FG service — each maps to a task above. RDP/VNC explicitly absent.
- TDD applied where it pays (logic), honestly skipped where it can't run here (device/UI).
- No placeholders in shipped code; `STATUS.md` is the single source of truth for completion claims.

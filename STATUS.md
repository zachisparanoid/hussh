# Hussh — Build Status vs. Definition of Done

**Honest status as of this build session.** Legend: ✅ implemented & compiles · 🟡 implemented, needs on-device verification · ◐ partial · ⬜ not done.

This codebase **compiles** (`./gradlew :app:assembleDebug` succeeds) and ships **44 passing JVM unit tests** over the high-risk pure logic (AES-GCM crypto, Telnet negotiation, ANSI escape composition, key-bar touch-state machine).

**Device-verified** (installed + run on a Samsung Galaxy Z Fold 6, Android 16 / API 36): installs cleanly, launches, renders the host list (Hussh Dark + Material You), opens the **SQLCipher-encrypted DB**, navigates the M3 UI — and **connects a real SSH session end-to-end**: host-key TOFU prompt with live SHA256/MD5 fingerprints → accept → authenticate → **interactive shell prompt (`ubuntu@ubuntu-vm:~$`) rendered** via the Termux VT engine, with the keyboard helper bar showing the exact spec default order. **Auto-reconnect with exponential backoff** (2s/4s/8s) and **background session persistence** (foreground service holds the session, active-count badge updates) were also observed live. Telnet/raw-TCP/SFTP transports and biometric enforcement still await their own live tests.

> **Two device-only crashes found & fixed during verification** (both invisible to the JVM unit tests):
> 1. **Keystore IV:** `CredentialCrypto.encrypt` passed a caller-supplied IV to an Android Keystore GCM key → `InvalidAlgorithmParameterException: Caller-provided IV not permitted`. Fixed by letting the Keystore generate the IV (`cipher.init(ENCRYPT_MODE, key)`, read `cipher.iv`).
> 2. **MINA `user.home`:** `SshClient.setUpDefaultClient()` static-inits a `~/.ssh` resolver that reads `user.home`, which Android doesn't set → `ExceptionInInitializerError: No user home` (an `Error`, so `catch(Exception)` missed it → process death on connect). Fixed by setting `user.home`/`user.name` in `Application.onCreate`, using `HostConfigEntryResolver.EMPTY`, and hardening the connection boundary to `catch (Throwable)` (rethrowing `CancellationException`).
>
> Textbook cases of why device verification matters — JVM tests can't reproduce the Keystore or a missing `user.home`.

## Definition of Done

| # | Item | Status | Notes |
|---|------|--------|-------|
| 1 | Builds API 26→34, single-activity Compose Nav across all routes | ✅ | compileSdk 35, targetSdk 34, minSdk 26. All 9 routes wired in `NavGraph`. **Verified running on-device** (Android 16); host-list → editor → terminal navigation confirmed. |
| 2 | Create/edit/group/search/reorder/import/export hosts; credentials encrypted via Keystore AES-GCM, biometric unlock | ◐ / 🟡 | CRUD ✅, search ✅, groups (assign + color dot) ✅, encryption ✅ (`CredentialCrypto`, unit-tested). Drag-reorder is **up/down buttons** in key-bar editor; host list reorder ◐. Import/export of hosts ⬜ (identities export ✅). Biometric unlock = preference wired; enforcement hook pending (needs FragmentActivity). |
| 3 | SSH (password/pubkey/keyboard-interactive), Telnet, raw TCP, SFTP/SCP connect & render VT/xterm-256 | ✅(SSH) / 🟡 | **SSH verified on a real server**: connect → auth → interactive shell rendered correctly. Telnet (negotiator unit-tested), raw TCP, and SFTP implemented + compile but not yet exercised against a live server. SCP not implemented — SFTP covers file transfer. |
| 4 | Identities: gen Ed25519/RSA/ECDSA, import, passphrase, export pub, fingerprint, host-key TOFU | ✅ / 🟡 | `KeyTools` (BouncyCastle + MINA), `IdentityScreen`. TOFU via `performTofuHostKeyCheck` with loud mismatch dialog. Keygen runs off-main-thread. |
| 5 | Foreground service keep-alive + auto-reconnect; persistent notification; battery-opt prompt | ✅ / 🟡 | **Auto-reconnect (exp. backoff 2/4/8s) and background session persistence verified on-device** (foreground service held the session across navigation; active-count badge updated). Keep-alive heartbeat + disconnect-all wired; multi-hour longevity still 🟡. |
| 6 | Key bar: scrollable single row, default order; 350ms latch + one-shot + hold-release; arrow auto-repeat + accel; correct ANSI | ✅ | `KeyBarRow` + `KeyBarState` + `AnsiComposer`. **31 unit tests** cover the state machine and every escape sequence. Default order matches spec. |
| 7 | Key bar customizable (reorder/add/remove/edit/per-key); global applies app-wide; host overrides; Reset present in both scopes | ✅ / ◐ | `KeyBarEditorScreen` global + per-host, add/remove/reorder (up/down), **Reset to default** in both. Per-key label/sequence editing ◐ (add from palette + remove; bespoke custom-key editor is a follow-up). Drag-reorder ◐. |
| 8 | Volume keys resize font (toggle + HUD); pinch zoom; size persists | ✅ / 🟡 | `MainActivity.onKeyDown` + `FontSizeController` (debounced persistence), HUD overlay, pinch via `onScale`. Toggle in settings. |
| 9 | Multiple app themes AND terminal themes; M3 dynamic color 12+; AMOLED true-black; per-host terminal theme | ✅ | 6 app themes (`AppTheme`), 9 terminal themes (`TerminalThemes`) applied to the emulator color table. Dynamic color on API 31+. Per-host `terminalThemeId`. |
| 10 | Copy-mode, paste, scrollback, URL tap, configurable backspace, bell, snippets quick-insert, multi-session switching | ✅ / ◐ | Selection/URL = vendored Termux view built-ins; paste/backspace/bell/snippets-sheet/tab-switching ✅. |
| 11 | SFTP browse/upload/download/mkdir/rename/delete/chmod; cancellable background-safe queue + progress notifications | ◐ / 🟡 | All ops + cancellable transfers + progress + SAF upload picker + transfer log ✅. A full multi-item queue with system notifications is ◐ (transfers are sequential with in-screen progress). |
| 12 | Edge cases handled; session/state/font/modifier survive rotation + process death (reconnect offer) | ◐ | Rotation survives (session in service + `configChanges`). Auth/host-key/network-drop banners ✅. Process-death "reconnect previous sessions" offer ⬜ (descriptors not yet persisted). |
| 13 | No RDP/VNC/graphical paths; text only | ✅ | Verified — no graphical-remoting code anywhere. |

## What to do next on a device
1. `./gradlew installDebug` against an emulator/device (API 26+).
2. Verify a real SSH login (password + key), Telnet, raw TCP, and an SFTP transfer.
3. Confirm the BouncyCastle provider swap + MINA work on the device ABI (see `SecurityProviders`).
4. Add: host import/export, process-death session restore, FragmentActivity-based biometric gate, drag-reorder, per-key custom editor, multi-item transfer queue with notifications.

See `docs/superpowers/specs/2026-05-28-hussh-design.md` for architecture and `docs/superpowers/plans/2026-05-28-hussh.md` for the build plan.

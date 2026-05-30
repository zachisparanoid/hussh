# TermForge — Design & Architecture

**Date:** 2026-05-28
**Status:** Approved (standing approval — user directed "build end to end, go with your recommendations, don't ask questions").

A native Android terminal client (SSH / SFTP / SCP / Telnet / raw‑TCP) — spiritual successor to JuiceSSH, text‑protocols only (no RDP/VNC). This doc does **not** restate the full product spec; it records the **architecture**, the **decisions I resolved** where the spec left latitude, and an **honest build/verification plan**.

---

## 1. Module & package architecture

Single Gradle build, three modules:

```
TermForge/
├── settings.gradle.kts        gradle/libs.versions.toml (version catalog)
├── app/                       :app  — the application
├── terminal-emulator/         :terminal-emulator  (vendored Termux, Apache-2.0)
└── terminal-view/             :terminal-view       (vendored Termux, Apache-2.0)
```

`:app` packages (single-activity Compose):

```
com.termforge
├── TermForgeApp                 @HiltAndroidApp
├── MainActivity                 single activity; NavHost; volume-key font interception
├── di/                          Hilt modules (Db, Crypto, Repo, Transport, Prefs)
├── data/
│   ├── db/                      Room: entities, DAOs, converters, TermForgeDatabase
│   ├── crypto/                  CredentialCrypto (Keystore AES-GCM), KeyTools (BouncyCastle)
│   ├── prefs/                   DataStore AppPreferences
│   └── repo/                    HostRepository, IdentityRepository, … (expose Flow)
├── transport/                   Transport interface + SshTransport, TelnetTransport,
│                                RawTcpTransport, telnet/ option negotiator, sftp/ client,
│                                hostkey/ TOFU verifier, forward/ port-forwarding
├── session/                     SessionService (foreground), SessionManager, TerminalSession,
│                                keepalive, reconnect (backoff), notifications
├── keybar/                      KeyBarModel, KeyBarState (touch semantics), AnsiComposer,
│                                KeyBarRow (Compose), KeyBarEditor
├── terminal/                    TerminalScreen, TerminalAndroidView bridge, font/zoom, copy-mode
├── ui/
│   ├── theme/                   app themes + terminal themes, Type, Color, dynamic color
│   ├── hosts/ hostedit/ identities/ snippets/ settings/ sftp/ active/   screen + ViewModel
│   └── components/              shared composables, empty states
└── nav/                         Routes, NavGraph
```

**Why this shape:** each unit has one purpose and a narrow interface. Transports hide their protocol behind one `Transport` contract so the session layer is protocol‑agnostic and the SSH engine is swappable. The keybar is pure logic + a thin Compose layer so its tricky touch semantics are unit‑testable without a device.

---

## 2. Decisions resolved (deviations / clarifications)

| Topic | Spec said | Decision & rationale |
|---|---|---|
| Terminal engine | Integrate Termux `terminal-view`+`terminal-emulator` | **Vendor both as local modules.** Termux's `TerminalSession` forks a local PTY via JNI — we do **not** want that. We drive `TerminalEmulator` directly from network bytes and render via `TerminalView` through a thin adapter (`TerminalSessionClient`), minimally patching only what's needed to avoid process spawning. |
| SQLCipher artifact | `net.zetetic:android-database-sqlcipher` | Use current **`net.zetetic:sqlcipher-android`** (old coords deprecated) + Room `SupportFactory`. |
| Telnet/raw‑TCP IO | `java.nio` | **Blocking `java.net.Socket` on `Dispatchers.IO`** with a read‑loop coroutine. Simpler, correct for the handful of concurrent sessions a phone holds; avoids selector complexity. Hand‑rolled IAC/DO/DONT/WILL/WONT + NAWS + terminal‑type negotiator still applies. |
| "Mosh‑style raw TCP" | raw TCP socket sessions | Implemented as **plain TCP piped to the terminal**. We do **not** implement the real Mosh protocol (UDP + SSP + roaming) — that needs a native mosh client, out of scope. Documented as a limitation; RAW is the JuiceSSH "raw" connection equivalent. |
| Keystore auth | master key unlocked by biometric | Keystore AES‑256‑GCM master key **without** per‑use auth, so the background service can auto‑reconnect unattended. The **biometric "app lock" gates the UI** (toggleable) instead. Defense‑in‑depth: SQLCipher encrypts the whole DB **and** `CredentialCrypto` column‑encrypts the most sensitive blobs (private keys, passphrases, saved passwords). |
| SSH engine | Apache MINA SSHD | Kept per spec, behind the `Transport` interface. |
| Backspace, charset, term‑type | per host | Per‑host columns; UTF‑8 fallback on charset error. |

**Additions (the "anything useful" latitude, kept tasteful):** quick‑connect from `ssh://user@host:port` URIs; in‑terminal scrollback search; snippet `${var}` prompts; export connection log via FileProvider; "open SFTP on this live SSH session"; duplicate‑host action.

---

## 3. Key data flows

**Connect:** Host row → `SessionManager.connect(host)` → builds the right `Transport` (resolving identity/password via `CredentialCrypto`, prompting for "ask each time"/passphrase) → `Transport.connect()` returns an inbound `Flow<ByteArray>` + an outbound `sink`. `TerminalSession` wires inbound bytes → `TerminalEmulator.append()` and `TerminalView` keystrokes/keybar output → `sink`. Service holds the session; the Compose screen binds and renders the shared emulator buffer.

**Keybar keystroke:** touch → `KeyBarState` resolves modifier composition (latched/one‑shot) → `AnsiComposer` turns (key, modifiers, app‑cursor‑mode) into bytes (Ctrl+C→0x03, Alt→ESC prefix, arrows → `ESC[A`/`ESC OA`) → `TerminalSession.write()`.

**Credentials at rest:** plaintext secret → `CredentialCrypto.encrypt()` (Keystore AES‑GCM, IV prepended) → base64 → `*Enc` Room column inside the SQLCipher DB. Decrypt is the inverse, gated by the app‑lock biometric when enabled.

**Host‑key TOFU:** on connect, fingerprint the server key → look up `KnownHostEntity` → unknown ⇒ prompt + store on accept; mismatch ⇒ blocking warning (abort / update); never silently accept.

---

## 4. Error handling & edge cases

Network drop → inline banner + reconnect per host setting, scrollback preserved (buffer lives in the service, not Compose). Auth failure → specific message, host preserved, re‑enter. Passphrase‑protected key, none stored → prompt; cancel aborts cleanly. Process death → on relaunch, offer to reconnect previously‑active sessions (persisted session descriptors). Biometric unavailable → device‑credential fallback; refuse ⇒ no saved‑credential access but manual entry still allowed. Telnet non‑negotiating server → degrade to line/char mode. Rotation/multi‑window → session+buffer+modifier+font survive (state in service + ViewModel/`rememberSaveable`). Keybar overflow → horizontal scroll only, Reset always reachable.

---

## 5. Build & verification plan (honest)

Toolchain present locally: Java 17, Gradle 8.9 (`/home/ubuntu/gradle-dist`), Android SDK android‑34 / build‑tools 34.0.0, Maven Central + GitHub reachable. Target: compileSdk/targetSdk 34, minSdk 26, Kotlin 2.0.x, AGP 8.7.x.

I will **compile at checkpoints** (`assembleDebug`) and only claim what I verify. This product is realistically weeks of work and 100+ files; in this session I build a **coherent, compiling foundation** with the signature features (keyboard helper bar, transport abstraction, session service, encrypted data layer, theming, navigation, core screens) implemented in depth, and I maintain a `STATUS.md` mapping every Definition‑of‑Done item to **implemented / partial / needs‑device**. I will not assert "builds and runs on a device" or "connects to real servers" unless actually verified here — device‑dependent items (biometric, foreground‑service longevity, real SSH handshakes) are marked needs‑device with the code paths in place.

---

## 6. Definition of done

The product spec's DoD checklist is the acceptance criteria; `STATUS.md` tracks live progress against it.

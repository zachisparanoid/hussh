# Hussh

A native Android terminal client — SSH, SFTP, Telnet, and raw-TCP — built as a spiritual successor
to JuiceSSH. **Text protocols only**: there is deliberately no RDP, VNC, or graphical remoting.

Kotlin 2.0 · Jetpack Compose · Material 3 · Hilt · Room+SQLCipher · Apache MINA SSHD · BouncyCastle ·
a vendored Termux VT/xterm-256color engine.

## Highlights

- **Signature keyboard helper bar** — a single scrollable row of modifier/arrow/literal/macro keys.
  Modifiers latch on a ≥350 ms hold and arm one-shot on a tap; arrows auto-repeat with acceleration;
  every key composes the correct ANSI/xterm escape (Ctrl→C0 byte, Alt→ESC prefix, DECCKM-aware
  arrows, `ESC[1;5A`-style modified keys). Fully customizable, global + per-host layouts.
- **Persistent background sessions** — a foreground service owns live connections; keep-alive +
  exponential-backoff auto-reconnect; persistent notification with "Disconnect all".
- **Real security** — SQLCipher encrypts the whole DB; the most sensitive columns are additionally
  AES-256-GCM encrypted under a hardware-backed Android Keystore key. Host-key TOFU with loud
  mismatch warnings. In-app Ed25519/RSA/ECDSA key generation + import.
- **Deep theming** — 6 app (Material 3, dynamic color, AMOLED) + 9 terminal color schemes
  (Dracula, Solarized, Gruvbox, Monokai, Nord, …), per-host overridable.
- **Power-user terminal** — volume-key & pinch font sizing, copy/paste, scrollback, snippets,
  multi-session tabs, configurable backspace, bell (visual/vibrate/silent).

## Module layout

```
:app                  the application (single-activity Compose)
:terminal-emulator    vendored Termux VT engine (Apache-2.0), patched for network sessions
:terminal-view        vendored Termux renderer (Apache-2.0)
```

`:app` is organized by feature: `data/` (db, crypto, prefs, repo), `transport/` (ssh, telnet, sftp),
`session/` (foreground service + manager), `keybar/`, `terminal/`, `ui/` (screens + theme), `nav/`.

See `docs/superpowers/specs/2026-05-28-hussh-design.md` for the full architecture and the
decisions/deviations from the original spec.

## Building

Requires JDK 17 and the Android SDK (platform 34/35, build-tools 34+).

```bash
# Point local.properties at your SDK (already set for the dev machine):
echo "sdk.dir=$ANDROID_HOME" > local.properties

./gradlew :app:assembleDebug      # build the APK
./gradlew :app:testDebugUnitTest  # run the 44 unit tests (crypto, telnet, ANSI, key-bar)
./gradlew installDebug            # install on a connected device/emulator (API 26+)
```

The first build downloads the full dependency graph (AGP, Compose, Hilt, Room, MINA, BouncyCastle,
SQLCipher) and is slow; subsequent builds are cached.

## Status

This is a from-scratch build. It compiles and the pure-logic test suite is green. Device-dependent
behavior (live handshakes, Keystore, biometrics, foreground-service longevity) has code paths in
place but is **not yet verified on hardware** — see `STATUS.md` for an honest item-by-item map
against the Definition of Done.

## License

The vendored `terminal-emulator` / `terminal-view` modules are from the
[Termux](https://github.com/termux/termux-app) project under Apache-2.0.

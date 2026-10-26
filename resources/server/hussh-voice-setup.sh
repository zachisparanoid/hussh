#!/usr/bin/env bash
# Hussh voice bridge — server side.
#
# Creates a PulseAudio virtual microphone backed by a FIFO. Claude Code records the ALSA default
# device (which routes to PulseAudio on this host), so whatever Hussh streams into the FIFO
# becomes the microphone Claude Code hears. No kernel module, no root, nothing installed.
#
# Idempotent: safe to re-run. Run once after login (and it persists across PulseAudio restarts).
set -euo pipefail

FIFO="/run/user/$(id -u)/hussh_mic.fifo"

echo "Hussh voice bridge setup"
echo "----------------------------"

# Defensive: a stray snd-aloop loopback card (e.g. from earlier experiments) gets auto-detected by
# PulseAudio and can steal the default-source slot, so Claude Code would record silence from it
# instead of hussh_mic. Remove any such card here so re-running this script self-heals that state.
pactl list short modules 2>/dev/null | awk '/snd_aloop/ {print $1}' | while read -r m; do
  pactl unload-module "$m" 2>/dev/null || true
done

# Drop any previous instance so re-running is clean.
pactl list short modules 2>/dev/null | awk '/module-pipe-source/ {print $1}' | while read -r m; do
  pactl unload-module "$m" 2>/dev/null || true
done
rm -f "$FIFO"

pactl load-module module-pipe-source source_name=hussh_mic file="$FIFO" \
  format=s16le rate=16000 channels=1 >/dev/null
pactl set-default-source hussh_mic

echo "  virtual mic : hussh_mic  (now the default PulseAudio source)"
echo "  fifo        : $FIFO"
echo "  format      : 16000 Hz, mono, s16le"

# Persist across PulseAudio restarts / reboots.
PA_DIR="$HOME/.config/pulse"
PA="$PA_DIR/default.pa"
mkdir -p "$PA_DIR"
[ -f "$PA" ] || printf '.include /etc/pulse/default.pa\n' > "$PA"
if ! grep -q 'source_name=hussh_mic' "$PA" 2>/dev/null; then
  {
    printf '\n# Hussh voice bridge\n'
    printf 'load-module module-pipe-source source_name=hussh_mic file=%s format=s16le rate=16000 channels=1\n' "$FIFO"
    printf 'set-default-source hussh_mic\n'
  } >> "$PA"
  echo "  persisted   : $PA"
else
  echo "  persisted   : already present in $PA"
fi

echo
echo "Done. Make sure Claude Code has voice TAP mode on:  /voice tap"
echo "Quick self-test (run in two shells):"
echo "  A:  arecord -D default -f S16_LE -r 16000 -c 1 -d 3 /tmp/t.wav"
echo "  B:  cat /dev/urandom > $FIFO    # ctrl-C after a moment"
echo "  then: t.wav should contain noise (not silence)."

#!/usr/bin/env bash
#
# TermForge — agent attention setup
# ---------------------------------
# Installs a Claude Code hook that emits a tiny OSC 777 marker whenever Claude finishes a turn
# (Stop) or needs your input (Notification). TermForge watches each session's SSH stream for that
# marker, strips it (you never see it), and pops a "Claude needs you" notification on your phone.
#
# Run this ONCE on the machine where Claude Code runs, then enable
# "Claude attention notifications" in TermForge → Settings.
#
# Safe to re-run: it's idempotent and won't duplicate or clobber your other hooks.
#
# Remove it later with:  termforge-notify-setup.sh --uninstall
set -euo pipefail

HOOK_DIR="$HOME/.claude/hooks"
HOOK="$HOOK_DIR/tf-attn.sh"
SETTINGS="$HOME/.claude/settings.json"

require_jq() {
    if ! command -v jq >/dev/null 2>&1; then
        echo "This script needs 'jq'. Install it first:  sudo apt install jq   (or: brew install jq)" >&2
        exit 1
    fi
}

uninstall() {
    require_jq
    rm -f "$HOOK"
    if [ -f "$SETTINGS" ]; then
        local tmp
        tmp="$(mktemp)"
        jq --arg cmd "$HOOK" '
            if (.hooks | type) == "object" then
                .hooks.Stop = ((.hooks.Stop // []) | map(select((.hooks // []) | any(.command == $cmd) | not)))
                | .hooks.Notification = ((.hooks.Notification // []) | map(select((.hooks // []) | any(.command == $cmd) | not)))
            else . end
        ' "$SETTINGS" >"$tmp" && mv "$tmp" "$SETTINGS"
    fi
    echo "Removed the TermForge attention hook."
    exit 0
}

[ "${1:-}" = "--uninstall" ] && uninstall

require_jq
mkdir -p "$HOOK_DIR"

# Write the hook. The quoted heredoc keeps everything literal, so the file ends up containing the
# JSON-escape TEXT  …  (backslash-u, six chars each) — NOT raw control bytes. `printf %s`
# emits the argument verbatim, so the hook prints valid JSON; Claude Code then unescapes  → ESC
# and  → BEL when it writes the bytes to the PTY:
#     ESC ] 777 ; notify ; TermForge ; tf-attn BEL
# The unique "tf-attn" tag is what TermForge's AttentionMarkerScanner keys on. suppressOutput hides
# the hook's stdout from the transcript; terminalSequence is still written to the terminal.
cat >"$HOOK" <<'EOF'
#!/usr/bin/env bash
# TermForge agent-attention marker. Installed by termforge-notify-setup.sh.
printf '%b' '{"suppressOutput": true, "terminalSequence": "\\u001b]777;notify;TermForge;tf-attn\\u0007"}'
EOF
chmod +x "$HOOK"

# Register the hook for both Stop (turn complete) and Notification (needs input), appending without
# duplicating and preserving any other hooks you already have.
[ -f "$SETTINGS" ] || echo '{}' >"$SETTINGS"
tmp="$(mktemp)"
jq --arg cmd "$HOOK" '
    .hooks //= {}
    | .hooks.Stop //= []
    | .hooks.Notification //= []
    | (if ([.hooks.Stop[]?.hooks[]?.command] | index($cmd)) then .
       else .hooks.Stop += [{"matcher": "", "hooks": [{"type": "command", "command": $cmd}]}] end)
    | (if ([.hooks.Notification[]?.hooks[]?.command] | index($cmd)) then .
       else .hooks.Notification += [{"matcher": "", "hooks": [{"type": "command", "command": $cmd}]}] end)
' "$SETTINGS" >"$tmp" && mv "$tmp" "$SETTINGS"

echo "Installed the TermForge attention hook → $HOOK"
echo "Registered Stop + Notification hooks in $SETTINGS"
echo
echo "Verify it emits valid JSON:"
echo "    bash \"$HOOK\" | jq ."
echo
echo "Then enable 'Claude attention notifications' in TermForge → Settings, connect, and run claude."

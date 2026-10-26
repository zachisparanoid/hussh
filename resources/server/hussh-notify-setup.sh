#!/usr/bin/env bash
#
# Hussh — agent attention setup
# ---------------------------------
# Installs the per-instance attention hook + tmux router so Hussh can see every Claude Code
# instance on this machine (including detached tmux panes) and route Approve/Deny/voice to the right
# one. Run this ONCE on the machine where Claude Code runs, then enable "Claude attention
# notifications" in Hussh → Settings.
#
# Installs:
#   ~/.claude/hooks/hussh-attn.sh     (Stop + Notification hook → writes the event feed + OSC marker)
#   ~/.hussh/hussh-route.sh       (tmux router the phone calls: list/focus/send/capture)
#   ~/.hussh/attention.jsonl   (the event feed, created on first hook fire)
#
# Safe to re-run (idempotent). Remove with:  hussh-notify-setup.sh --uninstall
set -euo pipefail

SRC_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HOOK_DIR="$HOME/.claude/hooks"
HOOK="$HOOK_DIR/hussh-attn.sh"
HUSSH_DIR="$HOME/.hussh"
ROUTE="$HUSSH_DIR/hussh-route.sh"
SETTINGS="$HOME/.claude/settings.json"

require_jq() {
    if ! command -v jq >/dev/null 2>&1; then
        echo "This script needs 'jq'. Install it first:  sudo apt install jq   (or: brew install jq)" >&2
        exit 1
    fi
}

uninstall() {
    require_jq
    rm -f "$HOOK" "$ROUTE"
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
    echo "Removed the Hussh attention hook + router. (Left ~/.hussh/attention.jsonl in place.)"
    exit 0
}

[ "${1:-}" = "--uninstall" ] && uninstall

require_jq

# Install the hook + router from the files shipped alongside this script.
[ -f "$SRC_DIR/hussh-attn.sh" ] || { echo "Missing $SRC_DIR/hussh-attn.sh — keep the resources/server files together." >&2; exit 1; }
[ -f "$SRC_DIR/hussh-route.sh" ] || { echo "Missing $SRC_DIR/hussh-route.sh — keep the resources/server files together." >&2; exit 1; }
install -D -m 0755 "$SRC_DIR/hussh-attn.sh" "$HOOK"
install -D -m 0755 "$SRC_DIR/hussh-route.sh" "$ROUTE"
mkdir -p "$HUSSH_DIR"

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

echo "Installed:"
echo "  hook   → $HOOK"
echo "  router → $ROUTE"
echo "Registered Stop + Notification hooks in $SETTINGS"
echo
echo "Verify the hook emits valid JSON:  bash \"$HOOK\" </dev/null | jq ."
echo "Then enable 'Claude attention notifications' in Hussh → Settings, connect, and run claude."

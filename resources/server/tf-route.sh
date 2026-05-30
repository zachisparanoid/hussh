#!/usr/bin/env bash
# tf-route.sh — TermForge tmux router. The phone runs these over SSH exec channels to address a
# specific Claude instance by tmux pane id, regardless of attach state.
#
#   list                              → JSONL of live claude panes (reason=running), across sockets
#   capture <socket> <pane>           → raw screen text of a pane
#   send    <socket> <pane> <text...> → type literal text + Enter into a pane
#   sendkey <socket> <pane> <key>     → send a tmux key name (Enter, Escape, 1, …)
#   focus   <socket> <pane>           → retarget the attached client to that pane (best-effort)
CMD="${1:-}"; shift 2>/dev/null || true
DEFSOCK="/tmp/tmux-$(id -u)/default"

case "$CMD" in
  list)
    for SOCK in /tmp/tmux-"$(id -u)"/*; do
      [ -S "$SOCK" ] || continue
      tmux -S "$SOCK" list-panes -a -F \
        '#{pane_id}|#{session_name}:#{window_index}.#{pane_index}|#{session_name}|#{window_index}|#{window_name}|#{pane_current_path}|#{pane_current_command}' 2>/dev/null \
      | awk -F'|' '$7=="claude"' \
      | while IFS='|' read -r pane target sess win winname cwd cmd; do
          jq -cn --arg instance "$pane" --arg socket "$SOCK" --arg target "$target" \
             --arg session "$sess" --arg window "$win" --arg window_name "$winname" --arg cwd "$cwd" \
             '{v:1,reason:"running",instance:$instance,socket:$socket,target:$target,session:$session,window:$window,window_name:$window_name,cwd:$cwd,cmd:"claude",prompt:[]}'
        done
    done
    ;;
  capture)
    SOCK="${1:-$DEFSOCK}"; PANE="$2"
    tmux -S "$SOCK" capture-pane -p -t "$PANE" 2>&1 ;;
  send)
    SOCK="${1:-$DEFSOCK}"; PANE="$2"; shift 2 2>/dev/null || true; TEXT="$*"
    tmux -S "$SOCK" send-keys -t "$PANE" -l -- "$TEXT" 2>/dev/null \
      && tmux -S "$SOCK" send-keys -t "$PANE" Enter 2>/dev/null && echo OK || { echo FAIL; exit 1; } ;;
  sendkey)
    SOCK="${1:-$DEFSOCK}"; PANE="$2"; KEY="$3"
    tmux -S "$SOCK" send-keys -t "$PANE" "$KEY" 2>/dev/null && echo OK || { echo FAIL; exit 1; } ;;
  focus)
    SOCK="${1:-$DEFSOCK}"; PANE="$2"
    SESS="$(tmux -S "$SOCK" display-message -p -t "$PANE" '#{session_name}' 2>/dev/null)"
    CLIENT="$(tmux -S "$SOCK" list-clients -F '#{client_tty}' 2>/dev/null | head -1)"
    [ -n "$CLIENT" ] && [ -n "$SESS" ] && tmux -S "$SOCK" switch-client -c "$CLIENT" -t "$SESS" 2>/dev/null
    tmux -S "$SOCK" select-window -t "$PANE" 2>/dev/null
    tmux -S "$SOCK" select-pane -t "$PANE" 2>/dev/null
    echo OK ;;
  *)
    echo "usage: tf-route.sh list|capture|send|sendkey|focus ..." >&2; exit 2 ;;
esac

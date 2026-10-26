#!/usr/bin/env bash
# hussh-attn.sh — Hussh agent-attention hook.
#
# Reads Claude Code's hook JSON on stdin, appends a per-instance event to the feed
# (~/.hussh/attention.jsonl) so the phone can attribute attention to the exact tmux pane, and
# emits the inline OSC marker on stdout. Best-effort: the feed write must NEVER abort the hook before
# the final printf, because Claude needs that stdout. Installed by hussh-notify-setup.sh.
FEED="$HOME/.hussh/attention.jsonl"
mkdir -p "$HOME/.hussh" 2>/dev/null

INPUT="$(cat)"
EVENT="$(printf '%s' "$INPUT" | jq -r '.hook_event_name // "Stop"' 2>/dev/null || echo Stop)"
MSG="$(printf '%s' "$INPUT" | jq -r '(.message // "")' 2>/dev/null || echo "")"

# done = turn finished (Stop); perm = permission prompt; input = otherwise waiting (Notification).
case "$EVENT" in
  Notification)
    case "$MSG" in
      *ermission*|*pprove*|*llow*|*onfirm*) REASON=perm ;;
      *) REASON=input ;;
    esac ;;
  *) REASON=done ;;
esac
TS="$(date +%s 2>/dev/null || echo 0)"

{
  if [ -n "${TMUX:-}" ] && [ -n "${TMUX_PANE:-}" ]; then
    SOCK="${TMUX%%,*}"   # $TMUX = socket,pid,session
    META="$(tmux -S "$SOCK" display-message -p -t "$TMUX_PANE" \
      '#{pane_id}|#{session_name}:#{window_index}.#{pane_index}|#{session_name}|#{window_index}|#{window_name}|#{pane_current_path}|#{pane_current_command}' 2>/dev/null)"
    IFS='|' read -r PANE TARGET SESS WIN WINNAME CWD CMD <<<"$META"
    PROMPT_JSON='[]'
    if [ "$REASON" != done ]; then
      PROMPT_JSON="$(tmux -S "$SOCK" capture-pane -p -t "$TMUX_PANE" 2>/dev/null \
        | grep -v '^[[:space:]]*$' | tail -8 | jq -R . 2>/dev/null | jq -s . 2>/dev/null || echo '[]')"
    fi
    jq -cn --argjson ts "${TS:-0}" --arg event "$EVENT" --arg reason "$REASON" \
       --arg instance "$PANE" --arg socket "$SOCK" --arg target "$TARGET" \
       --arg session "$SESS" --arg window "$WIN" --arg window_name "$WINNAME" \
       --arg cwd "$CWD" --arg cmd "$CMD" --argjson prompt "$PROMPT_JSON" \
       '{v:1,ts:$ts,event:$event,reason:$reason,instance:$instance,socket:$socket,target:$target,session:$session,window:$window,window_name:$window_name,cwd:$cwd,cmd:$cmd,prompt:$prompt}' >>"$FEED"
  else
    jq -cn --argjson ts "${TS:-0}" --arg event "$EVENT" --arg reason "$REASON" --arg cwd "$PWD" \
       '{v:1,ts:$ts,event:$event,reason:$reason,instance:null,socket:null,target:null,session:null,window:null,window_name:null,cwd:$cwd,cmd:"claude",prompt:[]}' >>"$FEED"
  fi
} 2>/dev/null || true

printf '%b' '{"suppressOutput": true, "terminalSequence": "\\u001b]777;notify;Hussh;hussh-attn\\u0007"}'

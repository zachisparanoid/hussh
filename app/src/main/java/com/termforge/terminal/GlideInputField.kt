package com.termforge.terminal

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.widget.EditText
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * EditText that reports a backspace pressed against an EMPTY buffer. Soft keyboards delete via the
 * InputConnection ([deleteSurroundingText]) rather than a KEYCODE_DEL key event, so when the glide
 * buffer is already empty the [TextWatcher] never sees a change and the backspace is silently lost.
 * Intercepting it here lets us still forward a backspace to the terminal.
 */
private class GlideEditText(context: Context) : EditText(context) {
    var onEmptyBackspace: (() -> Unit)? = null

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val base = super.onCreateInputConnection(outAttrs) ?: return null
        return object : InputConnectionWrapper(base, true) {
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength > 0 && afterLength == 0 && text.isNullOrEmpty()) {
                    onEmptyBackspace?.invoke()
                    return true
                }
                return super.deleteSurroundingText(beforeLength, afterLength)
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN &&
                    event.keyCode == KeyEvent.KEYCODE_DEL && text.isNullOrEmpty()
                ) {
                    onEmptyBackspace?.invoke()
                    return true
                }
                return super.sendKeyEvent(event)
            }
        }
    }
}

/**
 * Glide-typing capture field. A 1-px, fully transparent [EditText] that advertises a normal,
 * composing-capable text input so the keyboard offers swipe typing, suggestions, and autocorrect —
 * none of which the raw terminal input connection allows. Whatever the keyboard writes here (a tapped
 * character, a swiped word, an autocorrected revision, a deletion) is diffed against the previous
 * contents and the change is mirrored to the terminal: added text is sent verbatim, removed text
 * becomes that many backspaces, and a newline is sent as carriage-return then the field resets. A
 * backspace against the already-empty buffer is forwarded as a single terminal backspace too.
 *
 * This lives entirely off to the side — it never shows a box; the user just sees text land in the
 * terminal. It is only present while glide mode is on, so raw-mode terminal input is untouched.
 */
@Composable
fun GlideInputField(
    backspaceByte: Byte,
    onForward: (ByteArray) -> Unit,
    onViewCreated: (View) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier.size(1.dp),
        factory = { ctx ->
            GlideEditText(ctx).apply {
                // A plain text field (NO "no suggestions" flag) so glide typing is offered.
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_EXTRACT_UI
                isFocusable = true
                isFocusableInTouchMode = true
                isCursorVisible = false
                alpha = 0f
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                onEmptyBackspace = { onForward(byteArrayOf(backspaceByte)) }

                var last = ""
                var suppress = false
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(e: Editable?) {
                        if (suppress) return
                        val now = e?.toString() ?: ""
                        if (now == last) return

                        // Diff by common prefix (chat edits land at the end). Everything after the
                        // first difference in `last` is removed; everything after it in `now` is added.
                        var p = 0
                        val minLen = minOf(last.length, now.length)
                        while (p < minLen && last[p] == now[p]) p++
                        val removed = last.length - p
                        val added = now.substring(p)

                        if (removed > 0) onForward(ByteArray(removed) { backspaceByte })
                        if (added.isNotEmpty()) {
                            onForward(added.replace('\n', '\r').toByteArray(Charsets.UTF_8))
                        }

                        if (added.contains('\n')) {
                            // A line was submitted — reset the buffer for the next one.
                            suppress = true
                            setText("")
                            suppress = false
                            last = ""
                        } else {
                            last = now
                        }
                    }
                })
                onViewCreated(this)
            }
        },
    )
}

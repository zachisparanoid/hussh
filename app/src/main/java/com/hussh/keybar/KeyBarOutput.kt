package com.hussh.keybar

/**
 * Resolves the bytes a non-modifier button should send, composing in the current modifier state.
 * Pure (no mutation) — the caller sends the result and then calls [KeyBarState.consumeAfterKey].
 * Returns null for STICKY buttons (which only toggle modifier state, emitting nothing).
 */
object KeyBarOutput {
    private val composer = AnsiComposer()

    fun bytesFor(
        button: KeyBarButtonDef,
        state: KeyBarState,
        appCursor: Boolean,
        backspaceByte: Byte,
    ): ByteArray? {
        val mods = state.modifiers()
        return when (button.type) {
            KeyBarButtonType.STICKY -> null
            KeyBarButtonType.MACRO -> unescape(button.text.orEmpty())
            KeyBarButtonType.LITERAL, KeyBarButtonType.REPEATABLE, KeyBarButtonType.MOMENTARY -> {
                button.specialKey?.let { composer.composeSpecial(it, mods, appCursor, backspaceByte) }
                    ?: button.text?.let { composer.composeText(it, mods) }
                    ?: ByteArray(0)
            }
        }
    }

    /** Interpret common backslash escapes in macro text so users can embed control characters. */
    private fun unescape(s: String): ByteArray {
        if ('\\' !in s) return s.toByteArray(Charsets.UTF_8)
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    'e' -> sb.append(27.toChar()) // ESC
                    '\\' -> sb.append('\\')
                    '0' -> sb.append(0.toChar())  // NUL
                    else -> { sb.append(c); sb.append(s[i + 1]) }
                }
                i += 2
            } else {
                sb.append(c); i++
            }
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }
}

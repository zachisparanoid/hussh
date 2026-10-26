package com.hussh.keybar

/** Active modifier state passed to the composer. */
data class ModifierSet(
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
    val meta: Boolean = false,
) {
    val any: Boolean get() = ctrl || alt || shift || meta
}

/**
 * Turns a key + active modifiers into the exact byte sequence a VT/xterm-256color terminal expects.
 *
 * - Ctrl + letter → the C0 control byte (`Ctrl+C` = 0x03), via `code and 0x1F`.
 * - Alt → an ESC prefix (the classic "meta sends escape").
 * - Cursor/Home/End keys honor DECCKM application-cursor mode (`ESC[A` vs `ESC O A`).
 * - Modified keys use the xterm CSI form `ESC [ 1 ; <mod> <final>`, where
 *   `<mod> = 1 + shift + 2·alt + 4·ctrl + 8·meta`.
 */
class AnsiComposer {

    fun composeText(text: String, mods: ModifierSet): ByteArray {
        if (text.isEmpty()) return ByteArray(0)
        val out = ArrayList<Byte>(text.length + 1)
        if (mods.alt) out.add(ESC)
        if (mods.ctrl) {
            out.add((text[0].code and 0x1F).toByte())
        } else {
            for (byte in text.toByteArray(Charsets.UTF_8)) out.add(byte)
        }
        return out.toByteArray()
    }

    fun composeSpecial(
        key: SpecialKey,
        mods: ModifierSet,
        appCursor: Boolean,
        backspaceByte: Byte = 0x7F,
    ): ByteArray {
        val code = 1 + (if (mods.shift) 1 else 0) + (if (mods.alt) 2 else 0) +
            (if (mods.ctrl) 4 else 0) + (if (mods.meta) 8 else 0)
        val hasMods = code > 1
        return when (key) {
            SpecialKey.ARROW_UP -> cursor('A', hasMods, code, appCursor)
            SpecialKey.ARROW_DOWN -> cursor('B', hasMods, code, appCursor)
            SpecialKey.ARROW_RIGHT -> cursor('C', hasMods, code, appCursor)
            SpecialKey.ARROW_LEFT -> cursor('D', hasMods, code, appCursor)
            SpecialKey.HOME -> cursor('H', hasMods, code, appCursor)
            SpecialKey.END -> cursor('F', hasMods, code, appCursor)
            SpecialKey.INSERT -> tilde(2, hasMods, code)
            SpecialKey.DELETE -> tilde(3, hasMods, code)
            SpecialKey.PAGE_UP -> tilde(5, hasMods, code)
            SpecialKey.PAGE_DOWN -> tilde(6, hasMods, code)
            SpecialKey.TAB -> if (mods.shift) csi("Z") else maybeAlt(mods.alt, byteArrayOf(0x09))
            SpecialKey.ESC -> maybeAlt(mods.alt, byteArrayOf(0x1B))
            SpecialKey.ENTER -> maybeAlt(mods.alt, byteArrayOf(0x0D))
            SpecialKey.BACKSPACE -> maybeAlt(mods.alt, byteArrayOf(backspaceByte))
            SpecialKey.F1 -> ss3OrCsi('P', hasMods, code)
            SpecialKey.F2 -> ss3OrCsi('Q', hasMods, code)
            SpecialKey.F3 -> ss3OrCsi('R', hasMods, code)
            SpecialKey.F4 -> ss3OrCsi('S', hasMods, code)
            SpecialKey.F5 -> tilde(15, hasMods, code)
            SpecialKey.F6 -> tilde(17, hasMods, code)
            SpecialKey.F7 -> tilde(18, hasMods, code)
            SpecialKey.F8 -> tilde(19, hasMods, code)
            SpecialKey.F9 -> tilde(20, hasMods, code)
            SpecialKey.F10 -> tilde(21, hasMods, code)
            SpecialKey.F11 -> tilde(23, hasMods, code)
            SpecialKey.F12 -> tilde(24, hasMods, code)
        }
    }

    private fun cursor(letter: Char, hasMods: Boolean, code: Int, appCursor: Boolean): ByteArray = when {
        hasMods -> csi("1;$code$letter")
        appCursor -> byteArrayOf(ESC, 'O'.code.toByte(), letter.code.toByte())
        else -> csi("$letter")
    }

    private fun tilde(num: Int, hasMods: Boolean, code: Int): ByteArray =
        if (hasMods) csi("$num;$code~") else csi("$num~")

    private fun ss3OrCsi(letter: Char, hasMods: Boolean, code: Int): ByteArray =
        if (hasMods) csi("1;$code$letter") else byteArrayOf(ESC, 'O'.code.toByte(), letter.code.toByte())

    private fun csi(body: String): ByteArray =
        byteArrayOf(ESC, '['.code.toByte()) + body.toByteArray(Charsets.US_ASCII)

    private fun maybeAlt(alt: Boolean, bytes: ByteArray): ByteArray =
        if (alt) byteArrayOf(ESC) + bytes else bytes

    private companion object {
        const val ESC: Byte = 0x1B
    }
}

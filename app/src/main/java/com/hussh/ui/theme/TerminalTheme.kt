package com.hussh.ui.theme

import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TextStyle

/**
 * A terminal color theme: 16 ANSI colors + foreground/background/cursor/selection. Separate from the
 * app's Material chrome theme ([AppTheme]). Applied to the Termux emulator's color table.
 */
data class TerminalTheme(
    val id: String,
    val name: String,
    val ansi: IntArray,        // 16 entries (0-7 normal, 8-15 bright), ARGB
    val foreground: Int,
    val background: Int,
    val cursor: Int,
    val selection: Int,
) {
    /** Push these colors into a live emulator's color table and trigger a repaint upstream. */
    fun applyTo(emulator: TerminalEmulator?) {
        val colors = emulator?.mColors ?: return
        for (i in 0 until 16) colors.mCurrentColors[i] = ansi[i]
        colors.mCurrentColors[TextStyle.COLOR_INDEX_FOREGROUND] = foreground
        colors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND] = background
        colors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] = cursor
    }
}

object TerminalThemes {
    private fun argb(hex: Long) = (0xFF000000L or hex).toInt()

    private fun theme(id: String, name: String, bg: Long, fg: Long, cur: Long, sel: Long, vararg ansi: Long) =
        TerminalTheme(id, name, IntArray(16) { argb(ansi[it]) }, argb(fg), argb(bg), argb(cur), argb(sel))

    val dracula = theme(
        "dracula", "Dracula", 0x282A36, 0xF8F8F2, 0xF8F8F2, 0x44475A,
        0x21222C, 0xFF5555, 0x50FA7B, 0xF1FA8C, 0xBD93F9, 0xFF79C6, 0x8BE9FD, 0xF8F8F2,
        0x6272A4, 0xFF6E6E, 0x69FF94, 0xFFFFA5, 0xD6ACFF, 0xFF92DF, 0xA4FFFF, 0xFFFFFF,
    )
    val solarizedDark = theme(
        "solarized_dark", "Solarized Dark", 0x002B36, 0x839496, 0x93A1A1, 0x073642,
        0x073642, 0xDC322F, 0x859900, 0xB58900, 0x268BD2, 0xD33682, 0x2AA198, 0xEEE8D5,
        0x002B36, 0xCB4B16, 0x586E75, 0x657B83, 0x839496, 0x6C71C4, 0x93A1A1, 0xFDF6E3,
    )
    val solarizedLight = theme(
        "solarized_light", "Solarized Light", 0xFDF6E3, 0x657B83, 0x586E75, 0xEEE8D5,
        0x073642, 0xDC322F, 0x859900, 0xB58900, 0x268BD2, 0xD33682, 0x2AA198, 0xEEE8D5,
        0x002B36, 0xCB4B16, 0x586E75, 0x657B83, 0x839496, 0x6C71C4, 0x93A1A1, 0xFDF6E3,
    )
    val gruvbox = theme(
        "gruvbox", "Gruvbox", 0x282828, 0xEBDBB2, 0xEBDBB2, 0x504945,
        0x282828, 0xCC241D, 0x98971A, 0xD79921, 0x458588, 0xB16286, 0x689D6A, 0xA89984,
        0x928374, 0xFB4934, 0xB8BB26, 0xFABD2F, 0x83A598, 0xD3869B, 0x8EC07C, 0xEBDBB2,
    )
    val monokai = theme(
        "monokai", "Monokai", 0x272822, 0xF8F8F2, 0xF8F8F0, 0x49483E,
        0x272822, 0xF92672, 0xA6E22E, 0xF4BF75, 0x66D9EF, 0xAE81FF, 0xA1EFE4, 0xF8F8F2,
        0x75715E, 0xF92672, 0xA6E22E, 0xF4BF75, 0x66D9EF, 0xAE81FF, 0xA1EFE4, 0xF9F8F5,
    )
    val tango = theme(
        "tango", "Tango", 0x000000, 0xD3D7CF, 0xFFFFFF, 0x555753,
        0x000000, 0xCC0000, 0x4E9A06, 0xC4A000, 0x3465A4, 0x75507B, 0x06989A, 0xD3D7CF,
        0x555753, 0xEF2929, 0x8AE234, 0xFCE94F, 0x729FCF, 0xAD7FA8, 0x34E2E2, 0xEEEEEC,
    )
    val nord = theme(
        "nord", "Nord", 0x2E3440, 0xD8DEE9, 0xD8DEE9, 0x434C5E,
        0x3B4252, 0xBF616A, 0xA3BE8C, 0xEBCB8B, 0x81A1C1, 0xB48EAD, 0x88C0D0, 0xE5E9F0,
        0x4C566A, 0xBF616A, 0xA3BE8C, 0xEBCB8B, 0x81A1C1, 0xB48EAD, 0x8FBCBB, 0xECEFF4,
    )
    val greenOnBlack = theme(
        "green", "Green on Black", 0x000000, 0x00FF00, 0x00FF00, 0x003300,
        0x000000, 0x00CC00, 0x00FF00, 0x00CC00, 0x00AA00, 0x00CC00, 0x00FFAA, 0x00FF00,
        0x008800, 0x00FF00, 0x00FF00, 0x00FF00, 0x00FF88, 0x00FFAA, 0x00FFCC, 0x00FF00,
    )
    val amberCrt = theme(
        "amber", "Amber CRT", 0x1A1000, 0xFFB000, 0xFFB000, 0x553A00,
        0x1A1000, 0xCC7000, 0xFFB000, 0xFFC000, 0xCC8000, 0xCC7000, 0xFFC040, 0xFFB000,
        0x885000, 0xFFB000, 0xFFC000, 0xFFD000, 0xFFC040, 0xFFC040, 0xFFD060, 0xFFE090,
    )

    val all = listOf(dracula, solarizedDark, solarizedLight, gruvbox, monokai, tango, nord, greenOnBlack, amberCrt)

    fun byId(id: String?): TerminalTheme = all.firstOrNull { it.id == id } ?: dracula
}

package com.hussh.keybar

import kotlinx.serialization.Serializable

/** The four modifier keys the bar can latch. SUPER (Win) ships off by default. */
enum class ModifierKey { CTRL, ALT, SHIFT, SUPER }

/** Non-printing keys whose byte sequence depends on modifiers / application-cursor mode. */
enum class SpecialKey {
    ARROW_UP, ARROW_DOWN, ARROW_LEFT, ARROW_RIGHT,
    TAB, ESC, ENTER, BACKSPACE, DELETE, INSERT,
    HOME, END, PAGE_UP, PAGE_DOWN,
    F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12,
}

/** Button behavior. See the spec's STICKY/MOMENTARY/REPEATABLE/LITERAL/MACRO taxonomy. */
enum class KeyBarButtonType {
    /** A modifier (Ctrl/Alt/Shift/Super): tap = one-shot, long-press = latch/release. */
    STICKY,
    /** An action key that sends once; never latches or repeats (e.g. Fn toggle, Esc-like actions). */
    MOMENTARY,
    /** Auto-repeats while held (arrows, Tab, Backspace). */
    REPEATABLE,
    /** Sends a character or a [SpecialKey] sequence, composed with active modifiers. */
    LITERAL,
    /** Sends a stored string / snippet. */
    MACRO,
}

/**
 * One button in the helper bar. Flat (not a sealed hierarchy) so it serializes to/from the
 * `orderedButtonsJson` column trivially. The meaning of the optional fields depends on [type]:
 * STICKY uses [modifier]; LITERAL/REPEATABLE use [specialKey] OR [text]; MACRO uses [text].
 */
@Serializable
data class KeyBarButtonDef(
    val id: String,
    val label: String,
    val type: KeyBarButtonType,
    val modifier: ModifierKey? = null,
    val specialKey: SpecialKey? = null,
    val text: String? = null,
    val repeatable: Boolean = false,
    /** Optional secondary glyph/hint shown small on the key. */
    val hint: String? = null,
)

/** A complete ordered bar layout (what gets serialized). */
@Serializable
data class KeyBarLayout(val buttons: List<KeyBarButtonDef>)

/** The default helper-bar order from the spec. */
object DefaultKeyBar {
    private fun mod(id: String, label: String, m: ModifierKey) =
        KeyBarButtonDef(id, label, KeyBarButtonType.STICKY, modifier = m)

    private fun special(id: String, label: String, key: SpecialKey, repeatable: Boolean = false) =
        KeyBarButtonDef(id, label, if (repeatable) KeyBarButtonType.REPEATABLE else KeyBarButtonType.LITERAL, specialKey = key, repeatable = repeatable)

    private fun text(id: String, label: String, t: String) =
        KeyBarButtonDef(id, label, KeyBarButtonType.LITERAL, text = t)

    val layout: KeyBarLayout = KeyBarLayout(
        listOf(
            mod("ctrl", "Ctrl", ModifierKey.CTRL),
            mod("alt", "Alt", ModifierKey.ALT),
            mod("shift", "Shift", ModifierKey.SHIFT),
            special("left", "←", SpecialKey.ARROW_LEFT, repeatable = true),
            special("up", "↑", SpecialKey.ARROW_UP, repeatable = true),
            special("down", "↓", SpecialKey.ARROW_DOWN, repeatable = true),
            special("right", "→", SpecialKey.ARROW_RIGHT, repeatable = true),
            special("tab", "Tab", SpecialKey.TAB),
            special("esc", "Esc", SpecialKey.ESC),
            text("dash", "-", "-"),
            text("slash", "/", "/"),
            text("tilde", "~", "~"),
            text("pipe", "|", "|"),
            special("home", "Home", SpecialKey.HOME),
            special("end", "End", SpecialKey.END),
            special("pgup", "PgUp", SpecialKey.PAGE_UP),
            special("pgdn", "PgDn", SpecialKey.PAGE_DOWN),
            KeyBarButtonDef("fn", "Fn", KeyBarButtonType.MOMENTARY),
        ),
    )

    /** Win/Super button, available in the palette but off the default bar. */
    val superButton = KeyBarButtonDef("super", "Super", KeyBarButtonType.STICKY, modifier = ModifierKey.SUPER)

    /** Full palette a user can add from in the editor. */
    val palette: List<KeyBarButtonDef> = layout.buttons + superButton + listOf(
        special("del", "Del", SpecialKey.DELETE),
        special("ins", "Ins", SpecialKey.INSERT),
        special("bksp", "⌫", SpecialKey.BACKSPACE, repeatable = true),
        special("enter", "⏎", SpecialKey.ENTER),
        special("f1", "F1", SpecialKey.F1),
        special("f2", "F2", SpecialKey.F2),
        special("f3", "F3", SpecialKey.F3),
        special("f4", "F4", SpecialKey.F4),
        special("f5", "F5", SpecialKey.F5),
        text("colon", ":", ":"),
        text("star", "*", "*"),
        text("amp", "&", "&"),
        text("dollar", "$", "$"),
        text("grave", "`", "`"),
    )
}

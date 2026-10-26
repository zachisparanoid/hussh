package com.hussh.terminal

import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import com.hussh.keybar.KeyBarState
import com.hussh.keybar.ModifierKey
import com.hussh.keybar.ModState
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalViewClient

/**
 * Connects the Termux [com.termux.view.TerminalView] to Hussh. Crucially, the view asks this
 * client for the current modifier state ([readControlKey] etc.) when processing typed keys — so the
 * helper bar's latched/one-shot modifiers compose with soft-keyboard input. One-shots are cleared
 * after each consumed key. Pinch gestures drive [FontSizeController].
 */
class HusshTerminalViewClient(
    private val keyBarState: KeyBarState,
    private val fontSizeController: FontSizeController,
    private val density: Float,
    private val onSingleTap: () -> Unit,
    private val onCopyModeChanged: (Boolean) -> Unit,
    private val postConsume: (() -> Unit) -> Unit,
) : TerminalViewClient {

    override fun onScale(scale: Float): Float {
        if (scale != 1.0f) {
            fontSizeController.set(fontSizeController.sizeSp.value * scale)
        }
        return fontSizeController.sizeSp.value * density
    }

    override fun onSingleTapUp(e: MotionEvent) = onSingleTap()

    override fun shouldBackButtonBeMappedToEscape(): Boolean = false
    override fun shouldEnforceCharBasedInput(): Boolean = true
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
    override fun isTerminalViewSelected(): Boolean = true
    override fun copyModeChanged(copyMode: Boolean) = onCopyModeChanged(copyMode)

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean {
        postConsume { keyBarState.consumeAfterKey() }
        return false // let TerminalView/KeyHandler process it, reading our modifier state
    }

    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false
    override fun onLongPress(event: MotionEvent): Boolean = false

    override fun readControlKey(): Boolean = keyBarState.stateOf(ModifierKey.CTRL) != ModState.OFF
    override fun readAltKey(): Boolean = keyBarState.stateOf(ModifierKey.ALT) != ModState.OFF
    override fun readShiftKey(): Boolean = keyBarState.stateOf(ModifierKey.SHIFT) != ModState.OFF
    override fun readFnKey(): Boolean = false

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean {
        postConsume { keyBarState.consumeAfterKey() }
        return false // let the view write the code point, then clear one-shot modifiers
    }

    override fun onEmulatorSet() {}

    override fun logError(tag: String?, message: String?) { Log.e(tag ?: TAG, message ?: "") }
    override fun logWarn(tag: String?, message: String?) { Log.w(tag ?: TAG, message ?: "") }
    override fun logInfo(tag: String?, message: String?) { Log.i(tag ?: TAG, message ?: "") }
    override fun logDebug(tag: String?, message: String?) { Log.d(tag ?: TAG, message ?: "") }
    override fun logVerbose(tag: String?, message: String?) { Log.v(tag ?: TAG, message ?: "") }
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) { Log.e(tag ?: TAG, message, e) }
    override fun logStackTrace(tag: String?, e: Exception?) { Log.e(tag ?: TAG, "", e) }

    private companion object { const val TAG = "HusshView" }
}

package com.termforge.terminal

import com.termforge.session.BaseTerminalSessionClient
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView

/**
 * The [com.termux.terminal.TerminalSessionClient] used while the terminal screen is visible. Its key
 * job: repaint the [TerminalView] whenever the emulator buffer changes. Also routes bell and
 * clipboard events out to the Compose layer. Installed via `connection.attachClient` and removed on
 * dispose, so a backgrounded session falls back to the no-op base client.
 */
class ScreenTerminalSessionClient(
    private val view: TerminalView,
    private val onBellEvent: () -> Unit,
    private val onClipboardCopy: (String) -> Unit,
    private val onClipboardPaste: () -> Unit,
    private val onTitle: (String?) -> Unit,
) : BaseTerminalSessionClient() {

    override fun onTextChanged(changedSession: TerminalSession) = view.onScreenUpdated()
    override fun onColorsChanged(session: TerminalSession) = view.onScreenUpdated()
    override fun onTitleChanged(changedSession: TerminalSession) = onTitle(changedSession.title)
    override fun onBell(session: TerminalSession) = onBellEvent()
    override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {
        if (text != null) onClipboardCopy(text)
    }
    override fun onPasteTextFromClipboard(session: TerminalSession?) = onClipboardPaste()
}

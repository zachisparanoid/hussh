package com.termux.terminal;

/**
 * Hussh addition (not part of upstream Termux).
 *
 * The outbound seam for a network-backed {@link TerminalSession}. Where upstream Termux writes
 * terminal output to a local pseudo-terminal file descriptor, Hussh forwards it to a remote
 * host over SSH / Telnet / raw-TCP through this interface, and feeds inbound bytes back in via
 * {@link TerminalSession#onNetworkInput(byte[], int, int)}.
 */
public interface TerminalNetworkClient {

    /** Bytes the terminal wants to send to the remote host (keystrokes, responses, paste). */
    void onTerminalOutput(byte[] data, int offset, int count);

    /**
     * The terminal window changed size. Implementations forward this to the remote end:
     * an SSH window-change request, a Telnet NAWS sub-negotiation, etc.
     */
    void onTerminalSizeChanged(int columns, int rows, int cellWidthPixels, int cellHeightPixels);

    /** The terminal asked to close the connection (e.g. the user hit "disconnect"). */
    void onTerminalClosed();
}

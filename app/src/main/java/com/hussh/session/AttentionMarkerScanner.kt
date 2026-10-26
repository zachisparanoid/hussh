package com.hussh.session

import java.io.ByteArrayOutputStream

/**
 * Scans an inbound terminal byte stream for the Hussh "agent attention" marker — an OSC 777
 * sequence Claude Code emits via a hook's `terminalSequence`. Strips the whole sequence so the
 * emulator never renders it, and reports whether one was seen.
 *
 * It is **OSC-777-aware** rather than a fixed-string match: it locates the `ESC ] 777 ;` introducer,
 * reads to the `BEL` (0x07) or `ST` (`ESC \`) terminator, and triggers when the unique tag [TAG]
 * appears anywhere in the body. That makes detection robust to whichever exact form the hook ends up
 * emitting (a bare `ESC]777;hussh-attn BEL`, or the standard `ESC]777;notify;title;hussh-attn BEL`, BEL- or
 * ST-terminated) — every form contains `hussh-attn`. Other OSC 777 sequences (a real desktop notify) and
 * all normal escapes pass through untouched.
 *
 * Stateful: a marker (or even its introducer) split across two network reads is still caught, because
 * any trailing bytes that could begin a marker are carried into the next [scan] call.
 */
class AttentionMarkerScanner {
    class Result(val cleaned: ByteArray, val detected: Boolean)

    private var carry = ByteArray(0)

    fun scan(input: ByteArray): Result {
        val buf = if (carry.isEmpty()) input else carry + input
        carry = ByteArray(0)
        val out = ByteArrayOutputStream(buf.size)
        var detected = false
        var i = 0
        while (i < buf.size) {
            if (buf[i] != ESC) {
                out.write(buf[i].toInt())
                i++
                continue
            }
            // buf[i] == ESC: could be our OSC 777 introducer, another escape, or a split introducer.
            val introLen = commonPrefixLen(buf, i, INTRO)
            if (introLen < INTRO.size) {
                if (i + introLen == buf.size) {
                    // Ran out mid-introducer — might complete next chunk. Hold it.
                    carry = buf.copyOfRange(i, buf.size)
                    break
                }
                // Diverged (e.g. ESC[ CSI): this ESC isn't our marker. Emit it, scan on.
                out.write(buf[i].toInt())
                i++
                continue
            }
            // Full introducer present. Find the OSC terminator (BEL or ST).
            val term = findTerminator(buf, i + INTRO.size)
            if (term == null) {
                if (buf.size - i > MAX_OSC) {
                    // Pathologically long with no terminator → not our (short) marker. Don't hold it
                    // hostage; emit this byte and resume normal scanning so it all flushes.
                    out.write(buf[i].toInt())
                    i++
                    continue
                }
                carry = buf.copyOfRange(i, buf.size)
                break
            }
            val (termStart, termLen) = term
            val seqEnd = termStart + termLen
            if (containsTag(buf, i + INTRO.size, termStart)) {
                detected = true // strip the whole sequence (introducer..terminator)
            } else {
                out.write(buf, i, seqEnd - i) // a different OSC 777 — keep it verbatim
            }
            i = seqEnd
        }
        return Result(out.toByteArray(), detected)
    }

    /** Number of leading bytes of [pattern] that match buf starting at [at] (bounded by buf end). */
    private fun commonPrefixLen(buf: ByteArray, at: Int, pattern: ByteArray): Int {
        var k = 0
        while (at + k < buf.size && k < pattern.size && buf[at + k] == pattern[k]) k++
        return k
    }

    /**
     * From [from], find the OSC terminator: BEL (1 byte) or ST = ESC `\` (2 bytes).
     * Returns (terminatorStartIndex, terminatorLength), or null if none is present yet — including a
     * trailing lone ESC (which could be the start of an ST once more bytes arrive).
     */
    private fun findTerminator(buf: ByteArray, from: Int): Pair<Int, Int>? {
        var k = from
        while (k < buf.size) {
            when {
                buf[k] == BEL -> return k to 1
                buf[k] == ESC -> {
                    if (k + 1 >= buf.size) return null // ambiguous — wait for more
                    if (buf[k + 1] == BACKSLASH) return k to 2
                    // stray ESC inside body (unexpected for our printable marker) — treat as a body byte
                }
            }
            k++
        }
        return null
    }

    private fun containsTag(buf: ByteArray, start: Int, end: Int): Boolean {
        if (end - start < TAG.size) return false
        var i = start
        while (i <= end - TAG.size) {
            var k = 0
            while (k < TAG.size && buf[i + k] == TAG[k]) k++
            if (k == TAG.size) return true
            i++
        }
        return false
    }

    companion object {
        /** OSC 777 introducer: `ESC ] 777 ;`. */
        private val INTRO: ByteArray = "]777;".toByteArray(Charsets.US_ASCII)

        /** The unique tag whose presence inside an OSC 777 marks it as ours. */
        private val TAG: ByteArray = "hussh-attn".toByteArray(Charsets.US_ASCII)

        private const val ESC: Byte = 0x1b
        private const val BEL: Byte = 0x07
        private const val BACKSLASH: Byte = 0x5c

        /** Beyond this with no terminator, an `ESC]777;` run can't be our (short) marker. */
        private const val MAX_OSC = 256

        /** The canonical marker the server hook emits — kept in sync with `hussh-notify-setup.sh`. */
        const val CANONICAL_MARKER = "]777;notify;Hussh;hussh-attn"
    }
}

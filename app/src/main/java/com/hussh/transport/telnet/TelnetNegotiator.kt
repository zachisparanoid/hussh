package com.hussh.transport.telnet

import java.io.ByteArrayOutputStream

// Telnet command bytes (RFC 854) and options (RFC 1073/1091/858).
private const val IAC = 0xFF
private const val DONT = 0xFE
private const val DO = 0xFD
private const val WONT = 0xFC
private const val WILL = 0xFB
private const val SB = 0xFA
private const val SE = 0xF0
private const val OPT_ECHO = 0x01
private const val OPT_SGA = 0x03
private const val OPT_TTYPE = 0x18
private const val OPT_NAWS = 0x1F
private const val TT_IS = 0x00
private const val TT_SEND = 0x01

/**
 * A hand-rolled Telnet IAC option negotiator. Stateful across [process] calls so commands split
 * across TCP reads parse correctly. It separates user-visible data from in-band negotiation and
 * produces the bytes to send back. Per-option state prevents negotiation loops (RFC 1143 spirit).
 *
 * We offer WILL {TTYPE, NAWS, SGA} and request DO {ECHO, SGA} — i.e. character mode with server echo,
 * the right behavior for an interactive terminal. Servers that don't negotiate simply stream data,
 * which falls straight through to [Result.data].
 */
class TelnetNegotiator(
    private val termType: String,
    private var cols: Int,
    private var rows: Int,
) {
    class Result(val data: ByteArray, val response: ByteArray)

    private enum class State { NORMAL, IAC_SEEN, OPTION, SUBNEG, SUBNEG_IAC }

    private var state = State.NORMAL
    private var pendingCommand = 0
    private val subnegBuffer = ByteArrayOutputStream()

    private val weWill = HashSet<Int>()
    private val weWont = HashSet<Int>()
    private val weDo = HashSet<Int>()
    private val weDont = HashSet<Int>()

    private val supportedWill = setOf(OPT_TTYPE, OPT_NAWS, OPT_SGA)
    private val desiredDo = setOf(OPT_ECHO, OPT_SGA)

    fun process(input: ByteArray, length: Int): Result {
        val data = ByteArrayOutputStream()
        val resp = ByteArrayOutputStream()
        for (i in 0 until length) {
            val b = input[i].toInt() and 0xFF
            when (state) {
                State.NORMAL -> if (b == IAC) state = State.IAC_SEEN else data.write(b)
                State.IAC_SEEN -> when (b) {
                    IAC -> { data.write(IAC); state = State.NORMAL }   // escaped literal 0xFF
                    SB -> { subnegBuffer.reset(); state = State.SUBNEG }
                    WILL, WONT, DO, DONT -> { pendingCommand = b; state = State.OPTION }
                    else -> state = State.NORMAL                       // NOP / data-mark / etc.
                }
                State.OPTION -> { handleCommand(pendingCommand, b, resp); state = State.NORMAL }
                State.SUBNEG -> if (b == IAC) state = State.SUBNEG_IAC else subnegBuffer.write(b)
                State.SUBNEG_IAC -> if (b == SE) {
                    handleSubnegotiation(subnegBuffer.toByteArray(), resp); state = State.NORMAL
                } else {
                    subnegBuffer.write(b); state = State.SUBNEG       // IAC IAC inside subneg
                }
            }
        }
        return Result(data.toByteArray(), resp.toByteArray())
    }

    private fun handleCommand(command: Int, option: Int, resp: ByteArrayOutputStream) {
        when (command) {
            DO -> if (option in supportedWill) {
                val first = weWill.add(option)
                if (first) reply(resp, WILL, option)
                if (option == OPT_NAWS) resp.write(nawsUpdate(cols, rows))
            } else if (weWont.add(option)) {
                reply(resp, WONT, option)
            }
            DONT -> if (weWill.remove(option) || weDont.add(option)) {
                reply(resp, WONT, option)
            }
            WILL -> if (option in desiredDo) {
                if (weDo.add(option)) reply(resp, DO, option)
            } else if (weDont.add(option)) {
                reply(resp, DONT, option)
            }
            WONT -> if (weDo.remove(option) || weWont.add(option)) {
                reply(resp, DONT, option)
            }
        }
    }

    private fun handleSubnegotiation(sb: ByteArray, resp: ByteArrayOutputStream) {
        if (sb.size >= 2 && (sb[0].toInt() and 0xFF) == OPT_TTYPE && (sb[1].toInt() and 0xFF) == TT_SEND) {
            resp.write(IAC); resp.write(SB); resp.write(OPT_TTYPE); resp.write(TT_IS)
            resp.write(termType.toByteArray())
            resp.write(IAC); resp.write(SE)
        }
    }

    private fun reply(resp: ByteArrayOutputStream, command: Int, option: Int) {
        resp.write(IAC); resp.write(command); resp.write(option)
    }

    /** Build a NAWS window-size subnegotiation, escaping any 0xFF size byte as required. */
    fun nawsUpdate(cols: Int, rows: Int): ByteArray {
        this.cols = cols
        this.rows = rows
        val out = ByteArrayOutputStream()
        out.write(IAC); out.write(SB); out.write(OPT_NAWS)
        writeEscaped(out, (cols shr 8) and 0xFF)
        writeEscaped(out, cols and 0xFF)
        writeEscaped(out, (rows shr 8) and 0xFF)
        writeEscaped(out, rows and 0xFF)
        out.write(IAC); out.write(SE)
        return out.toByteArray()
    }

    private fun writeEscaped(out: ByteArrayOutputStream, b: Int) {
        out.write(b)
        if (b == IAC) out.write(IAC) // a literal 0xFF inside a subneg must be doubled
    }

    /** Proactive options to send immediately after the TCP connection opens. */
    fun initialNegotiation(): ByteArray {
        val out = ByteArrayOutputStream()
        for (opt in intArrayOf(OPT_TTYPE, OPT_NAWS, OPT_SGA)) {
            if (weWill.add(opt)) reply(out, WILL, opt)
        }
        for (opt in intArrayOf(OPT_SGA, OPT_ECHO)) {
            if (weDo.add(opt)) reply(out, DO, opt)
        }
        return out.toByteArray()
    }
}

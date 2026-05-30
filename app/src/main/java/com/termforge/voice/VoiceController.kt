package com.termforge.voice

import com.termforge.session.TerminalConnection
import com.termforge.transport.AuxStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Push-to-talk over SSH. On press it opens a second SSH channel that pipes microphone audio into the
 * remote virtual mic, then "taps" the /voice key so the remote Claude Code starts recording. On
 * release it taps again (stop + submit) and closes the channel. Claude Code transcribes via Anthropic
 * exactly as if you'd spoken into a local mic.
 */
class VoiceController(private val scope: CoroutineScope) {
    private val recorder = VoiceRecorder()
    private val mutex = Mutex()
    @Volatile private var stream: AuxStream? = null

    private val _active = MutableStateFlow(false)
    val active = _active.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val errors = _errors.asSharedFlow()

    fun press(connection: TerminalConnection, sinkCommand: String, triggerKey: ByteArray) {
        scope.launch {
            mutex.withLock {
                if (_active.value) return@withLock
                // Open the audio channel ONCE and keep it for the whole session. We must NOT close a
                // second SSH channel mid-session: on Android, a pending async write against the
                // closing channel triggers a NIO2 write failure that MINA escalates to a session
                // error, tearing down the terminal. So the channel is opened lazily and reused; it
                // dies naturally only when the whole connection ends.
                if (stream == null) stream = connection.openAuxStream(sinkCommand)
                val s = stream
                if (s == null) {
                    _errors.tryEmit("Voice streaming needs a live SSH session.")
                    return@withLock
                }
                val ok = recorder.start { bytes, n -> s.write(bytes, 0, n) }
                if (!ok) {
                    _errors.tryEmit("Microphone unavailable or permission denied.")
                    return@withLock
                }
                _active.value = true
                connection.write(triggerKey) // /voice tap → start recording on the remote
            }
        }
    }

    fun release(connection: TerminalConnection, triggerKey: ByteArray, tailDrainMs: Int) {
        scope.launch {
            mutex.withLock {
                if (!_active.value) return@withLock
                _active.value = false
                recorder.stop() // stop capturing new audio
                // The audio we already captured is still draining through net → FIFO → PulseAudio →
                // the remote transcriber, which lags the keystroke channel. Wait for it to arrive
                // before sending the stop key, so the tail of speech isn't cut off.
                if (tailDrainMs > 0) delay(tailDrainMs.toLong())
                connection.write(triggerKey) // /voice tap → stop + submit on the remote
                // Deliberately DO NOT close the aux channel — keep it open for the next hold.
            }
        }
    }
}

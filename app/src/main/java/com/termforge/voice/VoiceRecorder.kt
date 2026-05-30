package com.termforge.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.concurrent.thread

/**
 * Captures 16 kHz mono PCM-16 from the device microphone and delivers raw bytes via [onPcm]. The
 * format matches the server's virtual mic (`tf_mic`, 16000/mono/s16le) byte-for-byte, so no codec is
 * involved. Caller must hold RECORD_AUDIO before calling [start].
 */
class VoiceRecorder {
    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var running = false
    private var worker: Thread? = null

    @SuppressLint("MissingPermission")
    fun start(onPcm: (ByteArray, Int) -> Unit): Boolean {
        if (running) return true
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf <= 0) return false
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, SAMPLE_RATE * 2), // ~1s headroom
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return false }
        recorder = rec
        running = true
        rec.startRecording()
        worker = thread(name = "tf-voice-rec") {
            val buf = ByteArray(2048)
            while (running) {
                val n = rec.read(buf, 0, buf.size)
                if (n > 0) onPcm(buf, n)
            }
        }
        return true
    }

    fun stop() {
        running = false
        worker?.join(300)
        worker = null
        recorder?.let { runCatching { it.stop() }; runCatching { it.release() } }
        recorder = null
    }

    private companion object { const val SAMPLE_RATE = 16000 }
}

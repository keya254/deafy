package tech.briankeya.opencaption.phone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlin.math.max

/**
 * One microphone stream shared by captions and sound alerts.
 *
 * The microphone is opened once, while at least one listener is subscribed. Every 100 ms chunk
 * is sent to all listeners as 16 kHz mono floats. Listeners must not modify the array; copy it
 * first if you need to change it. Listeners are called one after another on the capture thread
 * and must return quickly.
 */
object MicStream {
    const val SAMPLE_RATE = 16000
    private const val CHUNK_FRAMES = 1600 // 100 ms
    private const val TAG = "MicStream"

    fun interface Listener {
        fun onAudio(mono: FloatArray)
    }

    private val lock = Any()
    private val listeners = LinkedHashSet<Listener>() // guarded by lock
    private var recorder: AudioRecord? = null          // guarded by lock
    private var thread: Thread? = null                 // guarded by lock
    @Volatile private var capturing = false

    /** Number of microphones in use (1 = mono). Valid while the stream is open. */
    @Volatile var channels = 1
        private set

    /**
     * Subscribes [listener] and opens the microphone if it is not open yet.
     * Returns false if permission is missing or the microphone cannot be opened.
     */
    fun acquire(context: Context, listener: Listener): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) return false

        synchronized(lock) {
            listeners.add(listener)
            if (recorder != null) return true
            return try {
                val rec = openRecorder(Settings.useAllMics)
                recorder = rec
                capturing = true
                rec.startRecording()
                val ch = channels
                thread = Thread({ readLoop(rec, ch) }, "opencaption-mic").also { it.start() }
                true
            } catch (e: Exception) {
                Log.e(TAG, "microphone failed to open", e)
                listeners.remove(listener)
                false
            }
        }
    }

    /** Unsubscribes [listener]. When the last listener leaves, the microphone is closed. */
    fun release(listener: Listener) {
        var rec: AudioRecord? = null
        var captureThread: Thread? = null
        synchronized(lock) {
            // Once removed under the lock, the listener gets no more calls (the capture thread
            // dispatches under the same lock), so it is safe to free its resources afterwards.
            listeners.remove(listener)
            if (listeners.isNotEmpty() || recorder == null) return
            capturing = false
            rec = recorder
            recorder = null
            captureThread = thread
            thread = null
        }
        try { rec?.stop() } catch (_: Exception) {}
        captureThread?.join(1000)
        rec?.release()
    }

    private fun readLoop(rec: AudioRecord, ch: Int) {
        val pcm = ShortArray(CHUNK_FRAMES * ch)
        while (capturing) {
            val read = rec.read(pcm, 0, pcm.size)
            if (read < 0) {
                Log.w(TAG, "microphone read failed: $read")
                break
            }
            if (read == 0) continue

            val frames = read / ch
            val mono = FloatArray(frames)
            for (f in 0 until frames) {
                var sum = 0f
                for (c in 0 until ch) sum += pcm[f * ch + c]
                mono[f] = sum / ch / 32768f
            }

            synchronized(lock) {
                for (listener in listeners) {
                    try {
                        listener.onAudio(mono)
                    } catch (e: Exception) {
                        Log.w(TAG, "listener failed", e)
                    }
                }
            }
        }
    }

    /** Tries the widest capture first (front + back mics, then stereo, then mono). */
    private fun openRecorder(useAllMics: Boolean): AudioRecord {
        val candidates = if (useAllMics) {
            buildList {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(AudioFormat.CHANNEL_IN_FRONT or AudioFormat.CHANNEL_IN_BACK)
                add(AudioFormat.CHANNEL_IN_STEREO)
                add(AudioFormat.CHANNEL_IN_MONO)
            }
        } else {
            listOf(AudioFormat.CHANNEL_IN_MONO)
        }
        for (mask in candidates) {
            val minBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE, mask, AudioFormat.ENCODING_PCM_16BIT)
            if (minBytes <= 0) continue
            val ch = Integer.bitCount(mask)
            val rec = AudioRecord(
                // VOICE_RECOGNITION avoids the phone's voice-call noise suppression on most devices.
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                mask,
                AudioFormat.ENCODING_PCM_16BIT,
                max(minBytes * 2, SAMPLE_RATE * ch * 2 / 2),
            )
            if (rec.state == AudioRecord.STATE_INITIALIZED) {
                channels = ch
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    // Point beamforming-capable mics at the user's mouth when the device supports it.
                    rec.setPreferredMicrophoneDirection(MIC_DIRECTION_TOWARDS_USER)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    rec.setPreferredMicrophoneFieldDimension(1.0f)
                }
                return rec
            }
            rec.release()
        }
        throw IllegalStateException("Microphone could not be opened")
    }

    private const val MIC_DIRECTION_TOWARDS_USER = 1
}

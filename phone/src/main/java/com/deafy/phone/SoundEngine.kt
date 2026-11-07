package com.deafy.phone

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.deafy.shared.SoundLabels
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifier
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifierResult
import com.google.mediapipe.tasks.audio.core.RunningMode
import com.google.mediapipe.tasks.components.containers.AudioData
import com.google.mediapipe.tasks.core.BaseOptions
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Live sound-event detection on the phone with a bundled YAMNet model (assets/yamnet.tflite).
 *
 * Pipeline: classify a rolling window ~every 500 ms -> keep only enabled labels above the
 * user's threshold -> require the same label in 2 consecutive windows -> per-label cooldown
 * -> emit an alert. Audio is processed in memory and never written to disk.
 */
class SoundEngine(
    private val context: Context,
    private val onStatus: (String) -> Unit,
    private val onAlert: (SoundAlert) -> Unit,
    private val onCaptionEvent: (String) -> Unit,
    private val alertsEnabled: Boolean,
) {
    private val main = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private var classifier: AudioClassifier? = null
    private var recorder: android.media.AudioRecord? = null
    private var worker: Thread? = null

    // Smoothing state (only touched from the classifier callback thread).
    private val consecutiveHits = HashMap<String, Int>()
    private val lastFiredMs = HashMap<String, Long>()
    private val lastEventMs = HashMap<String, Long>()

    fun start(): Boolean {
        if (!running.compareAndSet(false, true)) return true
        return try {
            val options = AudioClassifier.AudioClassifierOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL_ASSET).build())
                .setRunningMode(RunningMode.AUDIO_STREAM)
                .setScoreThreshold(0.05f) // coarse pre-filter; real threshold applied in onResult
                .setMaxResults(MAX_RESULTS)
                .setResultListener { result -> onResult(result) }
                .setErrorListener { e -> Log.w(TAG, "classifier error", e) }
                .build()

            val c = AudioClassifier.createFromOptions(context, options)
            classifier = c
            val rec = c.createAudioRecord()
            recorder = rec
            // One YAMNet window: 0.975 s at 16 kHz mono = 15600 samples.
            val audio = AudioData.create(rec.format, WINDOW_SAMPLES)
            rec.startRecording()

            worker = Thread({
                while (running.get()) {
                    try {
                        audio.load(rec)
                        c.classifyAsync(audio, SystemClock.uptimeMillis())
                        Thread.sleep(INTERVAL_MS)
                    } catch (e: InterruptedException) {
                        break
                    } catch (e: Exception) {
                        Log.w(TAG, "sound loop", e)
                        break
                    }
                }
            }, "deafy-sound").also { it.start() }

            onStatus("Sound alerts on")
            true
        } catch (e: Exception) {
            Log.e(TAG, "sound engine failed to start", e)
            running.set(false)
            onStatus("Sound alerts unavailable: ${e.message ?: e.javaClass.simpleName}")
            release()
            false
        }
    }

    fun stop() {
        running.set(false)
        worker?.interrupt()
        worker?.join(1000)
        worker = null
        release()
    }

    private fun release() {
        try { recorder?.stop() } catch (_: Exception) {}
        recorder?.release()
        recorder = null
        classifier?.close()
        classifier = null
    }

    private fun onResult(result: AudioClassifierResult) {
        if (!running.get()) return
        val now = SystemClock.uptimeMillis()
        val alertLabels = if (alertsEnabled) Settings.enabledLabels else emptySet()
        val eventLabels = Settings.captionEvents
        val threshold = Settings.threshold

        val categories = result.classificationResults().firstOrNull()
            ?.classifications()?.firstOrNull()?.categories().orEmpty()

        val wanted = alertLabels + eventLabels
        val active = categories.filter { it.categoryName() in wanted && it.score() >= threshold }
        val activeNames = active.map { it.categoryName() }.toSet()

        // A label must be seen in consecutive windows; anything that drops out resets.
        consecutiveHits.keys.retainAll(activeNames)

        for (category in active) {
            val name = category.categoryName()
            val hits = (consecutiveHits[name] ?: 0) + 1
            consecutiveHits[name] = hits
            if (hits < REQUIRED_CONSECUTIVE) continue

            if (name in eventLabels) {
                val last = lastEventMs[name] ?: Long.MIN_VALUE / 2
                if (now - last >= EVENT_COOLDOWN_MS) {
                    lastEventMs[name] = now
                    val tag = "[${SoundLabels.shortName(name)}]"
                    main.post { if (running.get()) onCaptionEvent(tag) }
                }
            }
            if (name in alertLabels) {
                val last = lastFiredMs[name] ?: Long.MIN_VALUE / 2
                if (now - last >= COOLDOWN_MS) {
                    lastFiredMs[name] = now
                    val alert = SoundAlert(
                        label = SoundLabels.shortName(name),
                        confidence = category.score(),
                        ts = System.currentTimeMillis(),
                    )
                    main.post { if (running.get()) onAlert(alert) }
                }
            }
        }
    }

    companion object {
        private const val TAG = "SoundEngine"
        private const val MODEL_ASSET = "yamnet.tflite"
        private const val INTERVAL_MS = 500L
        private const val WINDOW_SAMPLES = 15600
        private const val MAX_RESULTS = 10
        private const val REQUIRED_CONSECUTIVE = 2
        private const val COOLDOWN_MS = 10_000L
        private const val EVENT_COOLDOWN_MS = 4_000L
    }
}

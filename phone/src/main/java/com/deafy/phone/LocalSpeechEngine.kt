package com.deafy.phone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.deafy.shared.CaptionMsg
import com.deafy.shared.Protocol
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSpeechDenoiser
import com.k2fsa.sherpa.onnx.OfflineSpeechDenoiserConfig
import com.k2fsa.sherpa.onnx.OfflineSpeechDenoiserGtcrnModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeechDenoiserModelConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Common surface for caption backends so the service can use either one. */
interface CaptionSource {
    fun start(): Boolean
    fun stop()
}

/**
 * Offline captions with a multilingual Whisper model (sherpa-onnx), running entirely on the phone.
 *
 * Pipeline:
 *   microphone (16 kHz, all channels averaged, software mic boost)
 *   -> Silero VAD finds speech segments (ends at a short pause)
 *   -> Whisper decodes each segment (language auto-detected for Swahili/English mixing)
 *   -> filtered text is shown on the phone and sent to the watch.
 *
 * Audio stays in memory and is never written to disk or sent over the network.
 */
class LocalSpeechEngine(
    private val context: Context,
    private val onStatus: (String) -> Unit,
) : CaptionSource {
    private val main = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private var recognizer: OfflineRecognizer? = null
    private var denoiser: OfflineSpeechDenoiser? = null
    private var vad: Vad? = null
    private var recorder: AudioRecord? = null
    private var captureThread: Thread? = null
    private var decoder: ExecutorService? = null

    private var gain = 1f
    private var channelsInUse = 1

    override fun start(): Boolean {
        val missing = ModelFiles.missing(context)
        if (missing.isNotEmpty()) {
            onStatus("Offline model not installed (${missing.size} files missing). Using Android speech.")
            return false
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            onStatus("Microphone permission is needed for captions.")
            return false
        }
        if (!running.compareAndSet(false, true)) return true

        return try {
            loadModels()
            val rec = openRecorder()
            recorder = rec
            decoder = Executors.newSingleThreadExecutor { r -> Thread(null, r, "deafy-whisper", DECODE_STACK_BYTES) }
            rec.startRecording()
            captureThread = Thread({ captureLoop(rec) }, "deafy-capture").also { it.start() }

            val languageName = Settings.captionLanguage.title
            val cleaning = if (denoiser != null) " · noise cleaning on" else ""
            onStatus("Offline captions · $languageName · ${channelsInUse} mic${if (channelsInUse > 1) "s" else ""}$cleaning")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "local speech failed to start", e)
            running.set(false)
            release()
            onStatus("Offline captions failed: ${e.message ?: e.javaClass.simpleName}")
            false
        }
    }

    override fun stop() {
        running.set(false)
        captureThread?.join(2000)
        captureThread = null
        release()
    }

    private fun loadModels() {
        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
            modelConfig = OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = ModelFiles.file(context, ModelFiles.WHISPER_ENCODER).path,
                    decoder = ModelFiles.file(context, ModelFiles.WHISPER_DECODER).path,
                    // "" = auto-detect language per segment. Best for Swahili/English mixing.
                    language = Settings.captionLanguage.whisperCode,
                    task = "transcribe",
                ),
                tokens = ModelFiles.file(context, ModelFiles.WHISPER_TOKENS).path,
                numThreads = 2,
                debug = false,
                provider = "cpu",
                modelType = "whisper",
            ),
            decodingMethod = "greedy_search",
        )
        recognizer = OfflineRecognizer(null, config)

        // Optional noise reduction: applied per speech segment before Whisper.
        denoiser = if (Settings.cleanAudio && ModelFiles.isDenoiserInstalled(context)) {
            OfflineSpeechDenoiser(
                null,
                OfflineSpeechDenoiserConfig(
                    model = OfflineSpeechDenoiserModelConfig(
                        gtcrn = OfflineSpeechDenoiserGtcrnModelConfig(
                            model = ModelFiles.file(context, ModelFiles.DENOISER).path,
                        ),
                        numThreads = 1,
                        provider = "cpu",
                    ),
                ),
            )
        } else {
            null
        }

        vad = Vad(
            null,
            VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = ModelFiles.file(context, ModelFiles.VAD).path,
                    threshold = VAD_THRESHOLD,
                    minSilenceDuration = 0.8f,   // a pause this long ends a phrase (longer = fewer mid-word cuts)
                    minSpeechDuration = 0.25f,
                    windowSize = VAD_WINDOW,
                    maxSpeechDuration = 28f,     // Whisper works on 30 s windows; cut just under that
                ),
                sampleRate = SAMPLE_RATE,
                numThreads = 1,
                provider = "cpu",
                debug = false,
            ),
        )
    }

    /**
     * Opens the microphone. With "use all microphones" on, request a stereo (or
     * front/back) capture so the phone's second microphone is included, then average the
     * channels. Falls back to mono if the device does not provide it.
     */
    private fun openRecorder(): AudioRecord {
        val wantMulti = Settings.useAllMics
        val candidates = if (wantMulti) {
            buildList {
                // Front + back microphones (Android 10+). Falls back to stereo, then mono.
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
            val channels = Integer.bitCount(mask)
            val rec = AudioRecord(
                // VOICE_RECOGNITION avoids the phone's voice-call noise suppression on most devices.
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                mask,
                AudioFormat.ENCODING_PCM_16BIT,
                max(minBytes * 2, SAMPLE_RATE * channels * 2 / 2),
            )
            if (rec.state == AudioRecord.STATE_INITIALIZED) {
                channelsInUse = channels
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

    private fun captureLoop(rec: AudioRecord) {
        val channels = channelsInUse
        val pcm = ShortArray(CHUNK_FRAMES * channels)
        var lastSpeaking = false

        while (running.get()) {
            val read = rec.read(pcm, 0, pcm.size)
            if (read <= 0) continue
            val frames = read / channels

            // Downmix all channels to mono.
            val mono = FloatArray(frames)
            for (f in 0 until frames) {
                var sum = 0f
                for (c in 0 until channels) sum += pcm[f * channels + c]
                mono[f] = sum / channels / 32768f
            }

            applyBoost(mono)

            val v = vad ?: break
            v.acceptWaveform(mono)
            while (!v.empty()) {
                val segment = v.front()
                v.pop()
                submit(segment.samples)
            }

            val speaking = v.isSpeechDetected()
            if (speaking != lastSpeaking) {
                lastSpeaking = speaking
                main.post {
                    if (running.get()) AppState.update { it.copy(partial = if (speaking) "Hearing speech…" else "") }
                }
            }
        }
    }

    /**
     * Automatic gain: quiet input is raised toward a target level, up to the user's boost
     * setting. Near-silence is not boosted so the noise floor does not explode.
     */
    private fun applyBoost(samples: FloatArray) {
        val maxGain = Settings.micBoost
        var sumSq = 0.0
        for (s in samples) sumSq += s * s
        val rms = sqrt(sumSq / max(1, samples.size)).toFloat()

        val desired = if (rms > SILENCE_RMS) (TARGET_RMS / rms).coerceIn(1f, maxGain) else 1f
        gain += (desired - gain) * GAIN_SMOOTHING
        for (i in samples.indices) {
            samples[i] = (samples[i] * gain).coerceIn(-1f, 1f)
        }
    }

    private fun submit(samples: FloatArray) {
        val exec = decoder ?: return
        exec.execute {
            val rec = recognizer ?: return@execute
            val stream = rec.createStream()
            try {
                val denoised = denoiser?.let { d -> d.run(samples, SAMPLE_RATE).samples } ?: samples
                stream.acceptWaveform(condition(denoised), SAMPLE_RATE)
                rec.decode(stream)
                val text = clean(rec.getResult(stream).text)
                if (text != null) {
                    main.post {
                        if (!running.get()) return@post
                        val now = System.currentTimeMillis()
                        AppState.addCaption(text, now)
                        WatchLink.send(Protocol.PATH_CAPTION, CaptionMsg(text, true, now).toBytes())
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "decode failed", e)
            } finally {
                stream.release()
            }
        }
    }

    /**
     * Prepares a speech segment for recognition: removes rumble below speech with a
     * high-pass filter (~80 Hz), then raises quiet speech to a steady peak level. The gain is
     * capped so that near-silence is not amplified into noise.
     */
    private fun condition(input: FloatArray): FloatArray {
        val rc = 1f / (2f * PI.toFloat() * HIGH_PASS_HZ)
        val alpha = rc / (rc + 1f / SAMPLE_RATE)
        val out = FloatArray(input.size)
        var prevIn = 0f
        var prevOut = 0f
        for (i in input.indices) {
            val x = input[i]
            val y = alpha * (prevOut + x - prevIn)
            out[i] = y
            prevIn = x
            prevOut = y
        }
        var peak = 0f
        for (v in out) peak = max(peak, abs(v))
        if (peak > SILENT_PEAK) {
            val gain = min(TARGET_PEAK / peak, MAX_NORMALIZE_GAIN)
            for (i in out.indices) out[i] = (out[i] * gain).coerceIn(-1f, 1f)
        }
        return out
    }

    /**
     * Keeps captions to English and Swahili. Whisper's auto-detect sometimes emits other
     * scripts, Spanish, or repeated filler; those segments are dropped.
     */
    private fun clean(raw: String): String? {
        val text = raw.replace(Regex("\\[[^\\]]*\\]|\\([^)]*\\)"), " ").replace(Regex("\\s+"), " ").trim()
        if (text.length < 2) return null
        if (HALLUCINATIONS.any { text.equals(it, ignoreCase = true) }) return null
        if (NON_LATIN.containsMatchIn(text)) return null
        val words = text.lowercase().split(Regex("[^\\p{L}']+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return null
        // Repetition loop, e.g. "kwa kwa kwa kwa".
        if (words.size >= 4 && words.distinct().size <= words.size / 3) return null
        // Spanish-looking output with no English or Swahili cues.
        val spanish = words.count { it in SPANISH_WORDS }
        val englishOrSwahili = words.count { it in ENGLISH_WORDS || it in SWAHILI_WORDS }
        if (spanish >= 2 && englishOrSwahili == 0) return null
        return text
    }

    private fun release() {
        try { recorder?.stop() } catch (_: Exception) {}
        recorder?.release()
        recorder = null
        decoder?.shutdownNow()
        decoder = null
        vad?.release()
        vad = null
        recognizer?.release()
        recognizer = null
        denoiser?.release()
        denoiser = null
        AppState.update { if (it.partial.isNotBlank()) it.copy(partial = "") else it }
    }

    companion object {
        private const val TAG = "LocalSpeech"
        private const val DECODE_STACK_BYTES = 16L * 1024 * 1024
        private const val SAMPLE_RATE = 16000
        private const val VAD_WINDOW = 512
        private const val CHUNK_FRAMES = 1600        // 100 ms
        private const val VAD_THRESHOLD = 0.5f
        private const val TARGET_RMS = 0.08f   // lower target keeps loud speech from clipping
        private const val SILENCE_RMS = 0.002f
        private const val GAIN_SMOOTHING = 0.05f
        private const val HIGH_PASS_HZ = 80f
        private const val TARGET_PEAK = 0.9f
        private const val MAX_NORMALIZE_GAIN = 6f
        private const val SILENT_PEAK = 1e-3f
        private const val MIC_DIRECTION_TOWARDS_USER = 1

        private val NON_LATIN = Regex("[\\p{IsHan}\\p{IsCyrillic}\\p{IsArabic}\\p{IsHiragana}\\p{IsKatakana}\\p{IsHangul}\\p{IsThai}\\p{IsDevanagari}]")

        private val ENGLISH_WORDS = setOf("the", "and", "is", "are", "i", "you", "we", "they", "it", "to", "of", "in", "that", "this", "what", "with", "for", "have", "was", "be", "not", "my", "your", "so", "but", "on")
        private val SWAHILI_WORDS = setOf("na", "ya", "wa", "ni", "kwa", "sana", "habari", "sawa", "hapana", "ndiyo", "nina", "yako", "yangu", "lakini", "pia", "hii", "hiyo", "kama", "bado", "sijui", "tafadhali", "karibu", "pole", "twende", "nataka", "unaendeleaje", "mimi", "wewe", "huyu", "kuna")
        private val SPANISH_WORDS = setOf("que", "el", "los", "las", "por", "para", "pero", "una", "como", "est\u00e1", "muy", "gracias", "hola", "s\u00ed", "del", "con")

        private val HALLUCINATIONS = setOf(
            "you", "thank you", "thank you.", "thanks for watching", "thanks for watching!",
            "subtitles by the amara.org community",
        )

        /** Used by the service to choose a backend. */
        fun isInstalled(context: Context): Boolean = ModelFiles.isInstalled(context)
    }
}

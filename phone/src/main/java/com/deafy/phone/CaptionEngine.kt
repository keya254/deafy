package com.deafy.phone

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.deafy.shared.CaptionMsg
import com.deafy.shared.Protocol

/**
 * Live captions using Android's SpeechRecognizer.
 *
 * Quality notes (see README "Captions"):
 * - The language is the user's choice (English Kenya / Kiswahili Kenya). One recognizer
 *   session handles one language, so mixed Swahili-English is best served by choosing the
 *   base language that matches the speaker's dominant language.
 * - Results below [MIN_CONFIDENCE] are dropped when the recognizer reports a score.
 * - On-device recognition is preferred. Network recognition is used only if the user allowed it.
 * - Each utterance is a short session that restarts automatically.
 */
class CaptionEngine(
    private val context: Context,
    private val onStatus: (String) -> Unit,
) : CaptionSource {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var running = false
    private var useOnDevice = true
    private var language = Settings.captionLanguage.androidTag
    private var failures = 0

    /** Returns false if captions cannot start (status explains why). */
    override fun start(): Boolean {
        val onDevice = SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        if (!onDevice && !Settings.allowNetworkCaptions) {
            onStatus("On-device speech is not available. Allow network captions in Settings to use them.")
            return false
        }
        useOnDevice = onDevice
        running = true
        main.post { listenOnce() }
        return true
    }

    override fun stop() {
        running = false
        main.post {
            recognizer?.cancel()
            recognizer?.destroy()
            recognizer = null
        }
    }

    private fun listenOnce() {
        if (!running) return
        recognizer?.destroy()
        val rec = if (useOnDevice) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        rec.setRecognitionListener(listener)
        recognizer = rec
        rec.startListening(recognizerIntent())
    }

    private fun scheduleRestart(delayMs: Long) {
        main.postDelayed({ listenOnce() }, delayMs)
    }

    private fun recognizerIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, useOnDevice)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            failures = 0
            val where = if (useOnDevice) "on-device" else "network"
            onStatus("Listening · ${Settings.captionLanguage.title} · $where")
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = best(partialResults) ?: return
            AppState.update { it.copy(partial = text) }
            WatchLink.send(Protocol.PATH_CAPTION, CaptionMsg(text, false, System.currentTimeMillis()).toBytes())
        }

        override fun onResults(results: Bundle?) {
            val text = best(results)
            if (!text.isNullOrBlank()) {
                val now = System.currentTimeMillis()
                AppState.addCaption(text, now)
                WatchLink.send(Protocol.PATH_CAPTION, CaptionMsg(text, true, now).toBytes())
            }
            if (running) scheduleRestart(300)
        }

        override fun onError(error: Int) {
            if (!running) return
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    running = false
                    onStatus("Microphone permission is needed for captions.")
                }
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
                    // Device lacks this locale's model: fall back to standard English and say so.
                    if (language != FALLBACK_LANGUAGE) {
                        language = FALLBACK_LANGUAGE
                        onStatus("Kenyan model not installed on this phone. Using English (US) for now.")
                        scheduleRestart(500)
                    } else {
                        running = false
                        onStatus("No speech model available. Install an offline English model in Google settings.")
                    }
                }
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    // Silence: normal, just listen again.
                    scheduleRestart(300)
                }
                else -> {
                    failures++
                    if (failures >= 5) {
                        running = false
                        onStatus("Captions stopped after repeated errors ($error).")
                    } else {
                        onStatus("Recognizer error $error, retrying")
                        scheduleRestart(1000L * failures)
                    }
                }
            }
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    /** Top hypothesis, or null if it is too uncertain. Scores are optional on some devices. */
    private fun best(bundle: Bundle?): String? {
        val text = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
            ?: return null
        val score = bundle.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)?.firstOrNull()
        if (score != null && score >= 0f && score < MIN_CONFIDENCE) return null
        return text.ifBlank { null }
    }

    companion object {
        private const val FALLBACK_LANGUAGE = "en-US"
        private const val MIN_CONFIDENCE = 0.25f
    }
}

package tech.briankeya.opencaption.phone

import android.content.Context
import android.content.SharedPreferences
import tech.briankeya.opencaption.shared.SoundLabels
import tech.briankeya.opencaption.shared.VibePattern

/**
 * Caption language. MIXED lets the Whisper model detect Swahili or English per phrase,
 * which suits Kenyan speech that switches between the two.
 */
enum class CaptionLanguage(val whisperCode: String, val androidTag: String, val title: String, val hint: String) {
    MIXED("", "en-KE", "Mixed Swahili + English", "Recommended. Detects each phrase's language."),
    ENGLISH("en", "en-KE", "English (Kenya)", "Mostly English speech"),
    SWAHILI("sw", "sw-KE", "Kiswahili (Kenya)", "Mostly Swahili speech"),
    ;

    companion object {
        fun fromName(name: String?): CaptionLanguage = entries.firstOrNull { it.name == name } ?: MIXED
    }
}

/**
 * After how many seconds an older caption line is faded (never removed: the full
 * conversation stays scrollable). 0 means never fade.
 */
enum class HoldTime(val seconds: Int, val title: String) {
    SHORT(6, "Fade after 6 s"),
    NORMAL(12, "Fade after 12 s"),
    SLOW(30, "Fade after 30 s"),
    KEEP(0, "Never fade"),
    ;

    companion object {
        fun fromSeconds(s: Int): HoldTime = entries.firstOrNull { it.seconds == s } ?: NORMAL
    }
}

/**
 * User preferences, persisted in SharedPreferences.
 * Call [init] once from Application before reading anything.
 */
object Settings {
    private const val FILE = "opencaption_settings"

    private const val KEY_TEXT_SCALE = "caption_text_scale"
    private const val KEY_HOLD = "caption_hold_seconds"
    private const val KEY_RTL = "caption_rtl"
    private const val KEY_LANGUAGE = "caption_language_mode"
    private const val KEY_MIC_BOOST = "mic_boost"
    private const val KEY_ALL_MICS = "use_all_mics"
    private const val KEY_CLEAN_AUDIO = "clean_audio"
    private const val KEY_EVENTS = "caption_events"
    private const val KEY_NETWORK = "network_captions"
    private const val KEY_START_CAPTIONS = "start_captions_on_open"
    private const val KEY_PROFILE_NOTE = "profile_note"

    // v2: new traffic defaults. A new key means existing installs pick up the new defaults.
    private const val KEY_LABELS = "sound_labels_v2"
    // v2: the 0.3 default. Older saved values (often 0.5+) were set while detection was broken
    // and hid sirens and horns. A new key means existing installs pick up the new default.
    private const val KEY_THRESHOLD = "sound_threshold_v2"
    private const val KEY_PATTERN_PREFIX = "pattern_"

    private const val KEY_VIBRATE_PHONE = "vibrate_phone"
    private const val KEY_VIBRATE_WATCH = "vibrate_watch"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }

    private fun p(): SharedPreferences = checkNotNull(prefs) { "Settings.init not called" }

    // ---- Captions -------------------------------------------------------

    /** 0.8 .. 1.8. Scales caption text on phone and watch. */
    var captionTextScale: Float
        get() = p().getFloat(KEY_TEXT_SCALE, 1.0f)
        set(value) = p().edit().putFloat(KEY_TEXT_SCALE, value.coerceIn(0.8f, 1.8f)).apply()

    var holdTime: HoldTime
        get() = HoldTime.fromSeconds(p().getInt(KEY_HOLD, HoldTime.NORMAL.seconds))
        set(value) = p().edit().putInt(KEY_HOLD, value.seconds).apply()

    var captionRightToLeft: Boolean
        get() = p().getBoolean(KEY_RTL, false)
        set(value) = p().edit().putBoolean(KEY_RTL, value).apply()

    var captionLanguage: CaptionLanguage
        get() = CaptionLanguage.fromName(p().getString(KEY_LANGUAGE, null))
        set(value) = p().edit().putString(KEY_LANGUAGE, value.name).apply()

    /**
     * Software gain applied to the microphone signal, 1x (off) to 8x. Raises quiet speech
     * (phone in a bag, across a room). Very high values also amplify background noise.
     */
    var micBoost: Float
        get() = p().getFloat(KEY_MIC_BOOST, 2.0f)
        set(value) = p().edit().putFloat(KEY_MIC_BOOST, value.coerceIn(1f, 8f)).apply()

    /** Non-speech sounds to show as caption tags, e.g. [Clapping]. Independent of sound alerts. */
    var captionEvents: Set<String>
        get() = p().getStringSet(KEY_EVENTS, SoundLabels.DEFAULT_EVENTS)?.toSet() ?: SoundLabels.DEFAULT_EVENTS
        set(value) = p().edit().putStringSet(KEY_EVENTS, value.toSet()).apply()

    /** Start captions when the app opens. Set by an imported accessibility profile, or by the user. */
    var startCaptionsOnOpen: Boolean
        get() = p().getBoolean(KEY_START_CAPTIONS, false)
        set(value) = p().edit().putBoolean(KEY_START_CAPTIONS, value).apply()

    /** What the last imported accessibility profile changed, shown in Settings. Empty if none. */
    var profileNote: String
        get() = p().getString(KEY_PROFILE_NOTE, "") ?: ""
        set(value) = p().edit().putString(KEY_PROFILE_NOTE, value).apply()

    /** Remove background noise from each speech segment before recognition (needs denoiser model). */
    var cleanAudio: Boolean
        get() = p().getBoolean(KEY_CLEAN_AUDIO, true)
        set(value) = p().edit().putBoolean(KEY_CLEAN_AUDIO, value).apply()

    /** Capture from all available microphones (stereo/multi-channel) and combine them. */
    var useAllMics: Boolean
        get() = p().getBoolean(KEY_ALL_MICS, true)
        set(value) = p().edit().putBoolean(KEY_ALL_MICS, value).apply()

    /** Allow Google's network speech recognizer when no on-device model is available. */
    var allowNetworkCaptions: Boolean
        get() = p().getBoolean(KEY_NETWORK, false)
        set(value) = p().edit().putBoolean(KEY_NETWORK, value).apply()

    // ---- Sound alerts ----------------------------------------------------

    /** Raw AudioSet label names the user wants alerts for. */
    var enabledLabels: Set<String>
        get() = p().getStringSet(KEY_LABELS, SoundLabels.DEFAULT_ENABLED)?.toSet() ?: SoundLabels.DEFAULT_ENABLED
        set(value) = p().edit().putStringSet(KEY_LABELS, value.toSet()).apply()

    /** Minimum model score (0.2..0.9) before a sound is considered. Street sounds score lower than clean recordings. */
    var threshold: Float
        get() = p().getFloat(KEY_THRESHOLD, 0.3f)
        set(value) = p().edit().putFloat(KEY_THRESHOLD, value.coerceIn(0.2f, 0.9f)).apply()

    fun patternFor(rawLabel: String): VibePattern {
        val stored = p().getString(KEY_PATTERN_PREFIX + rawLabel, null)
        return if (stored != null) VibePattern.fromName(stored)
        else SoundLabels.DEFAULT_PATTERN[rawLabel] ?: VibePattern.SHORT
    }

    fun setPattern(rawLabel: String, pattern: VibePattern) {
        p().edit().putString(KEY_PATTERN_PREFIX + rawLabel, pattern.name).apply()
    }

    // ---- Vibration -------------------------------------------------------

    /** Vibrate this phone for an alert. */
    var vibratePhone: Boolean
        get() = p().getBoolean(KEY_VIBRATE_PHONE, true)
        set(value) = p().edit().putBoolean(KEY_VIBRATE_PHONE, value).apply()

    /** Send alerts to the watch so it vibrates (only if a watch is connected). */
    var vibrateWatch: Boolean
        get() = p().getBoolean(KEY_VIBRATE_WATCH, true)
        set(value) = p().edit().putBoolean(KEY_VIBRATE_WATCH, value).apply()
}

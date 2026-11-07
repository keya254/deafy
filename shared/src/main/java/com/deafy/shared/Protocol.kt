package com.deafy.shared

import org.json.JSONObject

/**
 * Wire protocol between the phone (sensing, settings) and the watch (display + haptics).
 * Sent with the Wearable MessageClient over Bluetooth as small UTF-8 JSON payloads.
 * Nothing here carries raw audio: only text, sound-event summaries and display settings.
 */
object Protocol {
    /** Capability advertised by the watch app (res/values/wear.xml on the watch). */
    const val WATCH_CAPABILITY = "deafy_watch_app"

    /** Capability advertised by the phone app (res/values/wear.xml on the phone). */
    const val PHONE_CAPABILITY = "deafy_phone_app"

    const val PATH_CAPTION = "/deafy/caption"       // phone -> watch
    const val PATH_ALERT = "/deafy/alert"           // phone -> watch
    const val PATH_STATUS = "/deafy/status"         // phone -> watch
    const val PATH_STYLE = "/deafy/style"           // phone -> watch (text size, hold time)
    const val PATH_COMMAND = "/deafy/command"       // watch -> phone
    const val PATH_HISTORY = "/deafy/history"       // phone -> watch: one page of older captions
    const val PREFIX = "/deafy/"
}

/** A caption snippet. `final=false` means a partial hypothesis that may still change. */
data class CaptionMsg(val text: String, val final: Boolean, val ts: Long) {
    fun toBytes(): ByteArray = JSONObject()
        .put("text", text)
        .put("final", final)
        .put("ts", ts)
        .toString().toByteArray(Charsets.UTF_8)

    companion object {
        fun from(bytes: ByteArray): CaptionMsg {
            val o = JSONObject(String(bytes, Charsets.UTF_8))
            return CaptionMsg(o.getString("text"), o.getBoolean("final"), o.getLong("ts"))
        }
    }
}

/** A sound-event alert. [label] is the short display name; [pattern] is a [VibePattern] name. */
data class AlertMsg(val label: String, val confidence: Float, val ts: Long, val pattern: String) {
    fun toBytes(): ByteArray = JSONObject()
        .put("label", label)
        .put("confidence", confidence.toDouble())
        .put("ts", ts)
        .put("pattern", pattern)
        .toString().toByteArray(Charsets.UTF_8)

    companion object {
        fun from(bytes: ByteArray): AlertMsg {
            val o = JSONObject(String(bytes, Charsets.UTF_8))
            return AlertMsg(
                label = o.getString("label"),
                confidence = o.getDouble("confidence").toFloat(),
                ts = o.getLong("ts"),
                pattern = o.optString("pattern", VibePattern.SHORT.name),
            )
        }
    }
}

/** What the phone is currently doing. */
data class StatusMsg(val captions: Boolean, val sound: Boolean, val note: String) {
    fun toBytes(): ByteArray = JSONObject()
        .put("captions", captions)
        .put("sound", sound)
        .put("note", note)
        .toString().toByteArray(Charsets.UTF_8)

    companion object {
        fun from(bytes: ByteArray): StatusMsg {
            val o = JSONObject(String(bytes, Charsets.UTF_8))
            return StatusMsg(o.getBoolean("captions"), o.getBoolean("sound"), o.getString("note"))
        }
    }
}

/**
 * Caption display settings the watch should mirror from the phone.
 * [textScale] 1.0 = normal; [holdSeconds] 0 = keep lines until cleared.
 */
data class StyleMsg(val textScale: Float, val holdSeconds: Int, val rightToLeft: Boolean) {
    fun toBytes(): ByteArray = JSONObject()
        .put("textScale", textScale.toDouble())
        .put("holdSeconds", holdSeconds)
        .put("rtl", rightToLeft)
        .toString().toByteArray(Charsets.UTF_8)

    companion object {
        fun from(bytes: ByteArray): StyleMsg {
            val o = JSONObject(String(bytes, Charsets.UTF_8))
            return StyleMsg(
                o.getDouble("textScale").toFloat(),
                o.getInt("holdSeconds"),
                o.optBoolean("rtl", false),
            )
        }
    }
}

/** Commands the watch can send to the phone. */
object Command {
    const val START_CAPTIONS = "start_captions"
    const val STOP_CAPTIONS = "stop_captions"
    const val START_SOUND = "start_sound"
    const val STOP_SOUND = "stop_sound"
    const val STOP_ALL = "stop_all"

    /** Watch asks the phone to resend captions newer than [since] (catch-up after a gap). */
    const val SYNC = "sync"

    fun encode(command: String): ByteArray =
        JSONObject().put("command", command).toString().toByteArray(Charsets.UTF_8)

    fun encodeSync(since: Long): ByteArray =
        JSONObject().put("command", SYNC).put("since", since).toString().toByteArray(Charsets.UTF_8)

    fun decode(bytes: ByteArray): String =
        JSONObject(String(bytes, Charsets.UTF_8)).getString("command")

    fun decodeSince(bytes: ByteArray): Long =
        JSONObject(String(bytes, Charsets.UTF_8)).optLong("since", 0L)

    /** Watch asks for up to [limit] captions older than [before] (epoch ms). */
    const val HISTORY = "history"

    fun encodeHistory(before: Long, limit: Int): ByteArray =
        JSONObject().put("command", HISTORY).put("before", before).put("limit", limit)
            .toString().toByteArray(Charsets.UTF_8)

    fun decodeHistory(bytes: ByteArray): Pair<Long, Int> {
        val o = JSONObject(String(bytes, Charsets.UTF_8))
        return o.optLong("before", Long.MAX_VALUE) to o.optInt("limit", 50)
    }
}

/**
 * Vibration patterns a user can assign to a sound. Waveforms are [delay, on, off, on, ...] ms.
 */
enum class VibePattern(val title: String, val waveform: LongArray) {
    GENTLE("Gentle", longArrayOf(0, 250)),
    SHORT("Two short taps", longArrayOf(0, 180, 150, 180)),
    LONG("One long buzz", longArrayOf(0, 700)),
    URGENT("Urgent pulses", longArrayOf(0, 600, 200, 600, 200, 600)),
    HEARTBEAT("Heartbeat", longArrayOf(0, 120, 120, 300));

    companion object {
        fun fromName(name: String?): VibePattern =
            entries.firstOrNull { it.name == name } ?: SHORT
    }
}

/**
 * Sound classes the phone may alert on. Keys are exact YAMNet / AudioSet label names
 * (verified against the bundled yamnet.tflite); values are the short names shown on the watch.
 */
object SoundLabels {
    val ALL: Map<String, String> = linkedMapOf(
        "Vehicle horn, car horn, honking" to "Car horn",
        "Siren" to "Siren",
        "Smoke detector, smoke alarm" to "Smoke alarm",
        "Fire alarm" to "Fire alarm",
        "Alarm" to "Alarm",
        "Doorbell" to "Doorbell",
        "Baby cry, infant cry" to "Baby crying",
        "Bicycle bell" to "Bicycle bell",
        "Knock" to "Knock",
    )

    /** Enabled by default on first run. */
    val DEFAULT_ENABLED: Set<String> = setOf(
        "Vehicle horn, car horn, honking",
        "Siren",
        "Smoke detector, smoke alarm",
        "Fire alarm",
        "Doorbell",
        "Baby cry, infant cry",
    )

    /** Default vibration pattern per sound (raw AudioSet name). */
    val DEFAULT_PATTERN: Map<String, VibePattern> = mapOf(
        "Vehicle horn, car horn, honking" to VibePattern.URGENT,
        "Siren" to VibePattern.URGENT,
        "Smoke detector, smoke alarm" to VibePattern.URGENT,
        "Fire alarm" to VibePattern.URGENT,
        "Alarm" to VibePattern.URGENT,
        "Doorbell" to VibePattern.SHORT,
        "Baby cry, infant cry" to VibePattern.HEARTBEAT,
        "Bicycle bell" to VibePattern.SHORT,
        "Knock" to VibePattern.SHORT,
    )

    /**
     * Non-speech events shown in the caption stream as [tags] (e.g. [Clapping]) instead of alerts.
     * Useful in meetings and crowds, where applause or laughter gives context.
     */
    val EVENTS: Map<String, String> = linkedMapOf(
        "Clapping" to "Clapping",
        "Applause" to "Applause",
        "Cheering" to "Cheering",
        "Laughter" to "Laughter",
        "Cough" to "Cough",
        "Sneeze" to "Sneeze",
        "Throat clearing" to "Throat clearing",
    )

    val DEFAULT_EVENTS: Set<String> = setOf("Clapping", "Applause", "Cheering", "Laughter", "Cough")

    fun shortName(rawLabel: String): String = ALL[rawLabel] ?: EVENTS[rawLabel] ?: rawLabel
}


/** One page of caption history sent from the phone to the watch, oldest first. */
data class HistoryPageMsg(val lines: List<StoredLine>, val hasMore: Boolean) {
    fun toBytes(): ByteArray {
        val arr = org.json.JSONArray()
        for (l in lines) arr.put(JSONObject().put("t", l.text).put("ts", l.ts))
        return JSONObject().put("lines", arr).put("hasMore", hasMore)
            .toString().toByteArray(Charsets.UTF_8)
    }

    companion object {
        fun from(bytes: ByteArray): HistoryPageMsg {
            val o = JSONObject(String(bytes, Charsets.UTF_8))
            val arr = o.getJSONArray("lines")
            val lines = (0 until arr.length()).map {
                val item = arr.getJSONObject(it)
                StoredLine(item.getString("t"), item.getLong("ts"))
            }
            return HistoryPageMsg(lines, o.optBoolean("hasMore", false))
        }
    }
}

/** A stored caption line, shared by the phone database and the history pages. */
data class StoredLine(val text: String, val ts: Long)

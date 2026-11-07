package com.deafy.wear

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WatchLine(val text: String, val ts: Long)

data class WearUi(
    val phoneConnected: Boolean = false,
    val captionsOn: Boolean = false,
    val soundOn: Boolean = false,
    val lines: List<WatchLine> = emptyList(),
    val partial: String = "",
    val textScale: Float = 1.0f,
    val holdSeconds: Int = 12,
    val rightToLeft: Boolean = false,
    val alertLabel: String? = null,
    val alertConfidence: Float = 0f,
    val alertTs: Long = 0L,
    val note: String = "Open Caption Flow on your phone",
    /** The phone has older captions to send when the user scrolls up. */
    val hasMoreOlder: Boolean = true,
    val loadingOlder: Boolean = false,
)

/** Watch-side state, updated by [PhoneDataService] and read by the UI. */
object WearState {
    private val _state = MutableStateFlow(WearUi())
    val state: StateFlow<WearUi> = _state.asStateFlow()

    fun update(block: (WearUi) -> WearUi) {
        _state.value = block(_state.value)
    }

    /** Recent window kept in memory. Older captions are fetched from the phone on demand. */
    private const val MAX_LINES = 300

    /** Adds a finished line unless it is already known (catch-up can resend it). */
    fun addLine(text: String, ts: Long) = update {
        if (it.lines.any { line -> line.ts == ts }) return@update it.copy(partial = "")
        it.copy(lines = (it.lines + WatchLine(text, ts)).sortedBy { line -> line.ts }.takeLast(MAX_LINES), partial = "")
    }

    /** Puts a page of older captions in front of the window. */
    fun prependPage(page: List<WatchLine>, hasMore: Boolean) = update { s ->
        val known = s.lines.map { it.ts }.toSet()
        val fresh = page.filter { it.ts !in known }
        s.copy(
            lines = (fresh + s.lines).sortedBy { it.ts }.takeLast(MAX_LINES * 2),
            hasMoreOlder = hasMore,
            loadingOlder = false,
        )
    }
}

package tech.briankeya.opencaption.phone

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CaptionLine(val text: String, val ts: Long)
data class SoundAlert(val label: String, val confidence: Float, val ts: Long)

data class UiState(
    val captionsOn: Boolean = false,
    val soundOn: Boolean = false,
    val watchConnected: Boolean = false,
    val watchName: String? = null,
    val partial: String = "",
    val captions: List<CaptionLine> = emptyList(),
    val alerts: List<SoundAlert> = emptyList(),
    val status: String = "Ready",
)

/** In-memory UI state shared by the service engines and the screens. */
object AppState {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun update(block: (UiState) -> UiState) {
        _state.value = block(_state.value)
    }

    fun addCaption(text: String, ts: Long) {
        History.append(text, ts)
        update { it.copy(captions = (it.captions + CaptionLine(text, ts)).takeLast(MAX_LINES), partial = "") }
    }

    /** Loads saved captions when the app starts. */
    fun restoreCaptions(lines: List<CaptionLine>) = update {
        it.copy(captions = lines.takeLast(MAX_LINES))
    }

    fun addAlert(alert: SoundAlert) = update {
        it.copy(alerts = (listOf(alert) + it.alerts).take(MAX_ALERTS))
    }

    /** Clears the on-screen captions and deletes the saved history. */
    fun clearCaptions() {
        History.clear()
        update { it.copy(captions = emptyList(), partial = "") }
    }

    fun clearAlerts() = update { it.copy(alerts = emptyList()) }

    private const val MAX_LINES = 300
    private const val MAX_ALERTS = 100
}

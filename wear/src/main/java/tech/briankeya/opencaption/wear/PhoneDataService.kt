package tech.briankeya.opencaption.wear

import tech.briankeya.opencaption.shared.AlertMsg
import tech.briankeya.opencaption.shared.CaptionMsg
import tech.briankeya.opencaption.shared.Haptics
import tech.briankeya.opencaption.shared.HistoryPageMsg
import tech.briankeya.opencaption.shared.Protocol
import tech.briankeya.opencaption.shared.StatusMsg
import tech.briankeya.opencaption.shared.StyleMsg
import tech.briankeya.opencaption.shared.VibePattern
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/**
 * Receives captions, alerts, status and style from the phone over Bluetooth
 * (Wearable Data Layer) and updates the watch UI. Alerts vibrate using the
 * pattern the user chose on the phone.
 */
class PhoneDataService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            Protocol.PATH_CAPTION -> {
                val msg = CaptionMsg.from(event.data)
                if (msg.final) {
                    WearState.addLine(msg.text, msg.ts)
                } else {
                    WearState.update { it.copy(phoneConnected = true, partial = msg.text) }
                }
            }
            Protocol.PATH_ALERT -> {
                val msg = AlertMsg.from(event.data)
                WearState.update {
                    it.copy(
                        phoneConnected = true,
                        alertLabel = msg.label,
                        alertConfidence = msg.confidence,
                        alertTs = msg.ts,
                    )
                }
                Haptics.play(this, VibePattern.fromName(msg.pattern))
            }
            Protocol.PATH_HISTORY -> {
                val page = HistoryPageMsg.from(event.data)
                WearState.prependPage(
                    page.lines.map { WatchLine(it.text, it.ts) },
                    page.hasMore,
                )
            }
            Protocol.PATH_STATUS -> {
                val msg = StatusMsg.from(event.data)
                WearState.update {
                    it.copy(
                        phoneConnected = true,
                        captionsOn = msg.captions,
                        soundOn = msg.sound,
                        note = msg.note,
                    )
                }
            }
            Protocol.PATH_STYLE -> {
                val msg = StyleMsg.from(event.data)
                WearState.update {
                    it.copy(
                        phoneConnected = true,
                        textScale = msg.textScale,
                        holdSeconds = msg.holdSeconds,
                        rightToLeft = msg.rightToLeft,
                    )
                }
            }
        }
    }
}

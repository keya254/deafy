package tech.briankeya.opencaption.phone

import tech.briankeya.opencaption.shared.CaptionMsg
import tech.briankeya.opencaption.shared.Command
import tech.briankeya.opencaption.shared.HistoryPageMsg
import tech.briankeya.opencaption.shared.Protocol
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking

/** Receives start/stop commands sent from the watch. */
class PhoneCommandService : WearableListenerService() {
    private fun sendCaption(nodeId: String, text: String, ts: Long) {
        Wearable.getMessageClient(this).sendMessage(
            nodeId,
            Protocol.PATH_CAPTION,
            CaptionMsg(text, true, ts).toBytes(),
        )
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != Protocol.PATH_COMMAND) return

        val command = Command.decode(event.data)

        // Watch catch-up after a gap: resend captions newer than the watch's last one.
        if (command == Command.SYNC) {
            if (!History.isReady()) return
            val since = Command.decodeSince(event.data)
            runBlocking {
                History.after(since, 500).forEach { line ->
                    sendCaption(event.sourceNodeId, line.text, line.ts)
                }
            }
            return
        }

        // Watch scrollback: one page of older captions.
        if (command == Command.HISTORY) {
            if (!History.isReady()) return
            val (before, limit) = Command.decodeHistory(event.data)
            runBlocking {
                val (lines, hasMore) = History.page(before, limit)
                Wearable.getMessageClient(this@PhoneCommandService).sendMessage(
                    event.sourceNodeId,
                    Protocol.PATH_HISTORY,
                    HistoryPageMsg(lines, hasMore).toBytes(),
                )
            }
            return
        }

        val state = AppState.state.value
        val captions = state.captionsOn
        val sound = state.soundOn
        when (Command.decode(event.data)) {
            Command.START_CAPTIONS -> ListenService.request(this, true, sound)
            Command.STOP_CAPTIONS -> ListenService.request(this, false, sound)
            Command.START_SOUND -> ListenService.request(this, captions, true)
            Command.STOP_SOUND -> ListenService.request(this, captions, false)
            Command.STOP_ALL -> ListenService.request(this, false, false)
        }
    }
}

package tech.briankeya.opencaption.phone

import android.content.Context
import tech.briankeya.opencaption.shared.Protocol
import tech.briankeya.opencaption.shared.StyleMsg
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable

/**
 * The watch is an extension of this app. It is reached over the Wearable Data Layer
 * (Bluetooth when paired and in range). When no watch is reachable, [isConnected] is false
 * and the phone handles everything itself (phone-only mode).
 */
object WatchLink {
    @Volatile private var appContext: Context? = null
    @Volatile private var nodes: List<Node> = emptyList()

    val isConnected: Boolean get() = nodes.isNotEmpty()

    fun init(context: Context) {
        appContext = context.applicationContext
        val client = Wearable.getCapabilityClient(context)
        client.addListener(
            { info: CapabilityInfo -> applyNodes(info.nodes) },
            Protocol.WATCH_CAPABILITY,
        )
        client.getCapability(Protocol.WATCH_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
            .addOnSuccessListener { info -> applyNodes(info.nodes) }
            .addOnFailureListener { applyNodes(emptySet()) }
    }

    /** Asks the Wearable API again, e.g. from the "Retry" button. */
    fun refresh() {
        val ctx = appContext ?: return
        Wearable.getCapabilityClient(ctx)
            .getCapability(Protocol.WATCH_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
            .addOnSuccessListener { info -> applyNodes(info.nodes) }
            .addOnFailureListener { applyNodes(emptySet()) }
    }

    private fun applyNodes(found: Set<Node>) {
        val wasConnected = isConnected
        nodes = found.toList()
        AppState.update {
            it.copy(
                watchConnected = found.isNotEmpty(),
                watchName = found.firstOrNull()?.displayName,
            )
        }
        // Mirror caption style as soon as a watch appears.
        if (!wasConnected && isConnected) pushStyle()
    }

    fun send(path: String, payload: ByteArray) {
        val ctx = appContext ?: return
        val client = Wearable.getMessageClient(ctx)
        for (node in nodes) {
            client.sendMessage(node.id, path, payload)
        }
    }

    /** Sends the current caption text size and hold time so the watch matches the phone. */
    fun pushStyle() {
        val holdSeconds = Settings.holdTime.seconds
        send(
            Protocol.PATH_STYLE,
            StyleMsg(Settings.captionTextScale, holdSeconds, Settings.captionRightToLeft).toBytes(),
        )
    }
}

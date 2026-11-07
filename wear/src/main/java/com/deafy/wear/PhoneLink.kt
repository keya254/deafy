package com.deafy.wear

import android.content.Context
import com.deafy.shared.Command
import com.deafy.shared.Protocol
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.Wearable

/**
 * Watch-to-phone link over Bluetooth (Wearable Data Layer).
 * Tracks whether the phone app is reachable and sends commands to it.
 */
object PhoneLink {
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        val client = Wearable.getCapabilityClient(context)
        client.addListener(
            { info: CapabilityInfo -> apply(info) },
            Protocol.PHONE_CAPABILITY,
        )
        client.getCapability(Protocol.PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
            .addOnSuccessListener { apply(it) }
            .addOnFailureListener { markUnreachable() }
    }

    private fun apply(info: CapabilityInfo) {
        if (info.nodes.isEmpty()) {
            markUnreachable()
            return
        }
        val wasConnected = WearState.state.value.phoneConnected
        WearState.update { it.copy(phoneConnected = true, note = "Connected to phone") }
        if (!wasConnected) onConnected()
    }

    /** Back in range: fill any gap since the last line the watch has, or load the latest page. */
    private fun onConnected() {
        val ctx = appContext ?: return
        val last = WearState.state.value.lines.lastOrNull()?.ts
        if (last == null) requestHistory(ctx, null) else requestCatchUp(ctx, last)
    }

    private fun markUnreachable() {
        WearState.update {
            it.copy(
                phoneConnected = false,
                captionsOn = false,
                soundOn = false,
                note = "Phone not nearby. Bring it close and open Caption Flow.",
            )
        }
    }

    /** Sends a command to the phone. Returns false if no phone node is reachable. */
    fun sendCommand(context: Context, command: String) {
        val messages = Wearable.getMessageClient(context)
        Wearable.getCapabilityClient(context)
            .getCapability(Protocol.PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
            .addOnSuccessListener { info ->
                if (info.nodes.isEmpty()) {
                    markUnreachable()
                    return@addOnSuccessListener
                }
                info.nodes.forEach { node ->
                    messages.sendMessage(node.id, Protocol.PATH_COMMAND, Command.encode(command))
                }
            }
            .addOnFailureListener { markUnreachable() }
    }

    /** Asks the phone for one page of captions older than [before] (or the newest, if null). */
    fun requestHistory(context: Context, before: Long?, limit: Int = 60) {
        WearState.update { it.copy(loadingOlder = true) }
        val messages = Wearable.getMessageClient(context)
        Wearable.getCapabilityClient(context)
            .getCapability(Protocol.PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
            .addOnSuccessListener { info ->
                if (info.nodes.isEmpty()) {
                    markUnreachable()
                    return@addOnSuccessListener
                }
                info.nodes.forEach { node ->
                    messages.sendMessage(
                        node.id,
                        Protocol.PATH_COMMAND,
                        Command.encodeHistory(before ?: Long.MAX_VALUE, limit),
                    )
                }
            }
            .addOnFailureListener {
                WearState.update { s -> s.copy(loadingOlder = false) }
                markUnreachable()
            }
    }

    /** After a gap, asks for captions newer than the last one the watch holds. */
    fun requestCatchUp(context: Context, since: Long) {
        val messages = Wearable.getMessageClient(context)
        Wearable.getCapabilityClient(context)
            .getCapability(Protocol.PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
            .addOnSuccessListener { info ->
                info.nodes.forEach { node ->
                    messages.sendMessage(node.id, Protocol.PATH_COMMAND, Command.encodeSync(since))
                }
            }
    }
}

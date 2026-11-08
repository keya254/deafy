package tech.briankeya.opencaption.phone

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import tech.briankeya.opencaption.shared.AlertMsg
import tech.briankeya.opencaption.shared.Haptics
import tech.briankeya.opencaption.shared.Protocol
import tech.briankeya.opencaption.shared.SoundLabels
import tech.briankeya.opencaption.shared.VibePattern

/**
 * Routes a sound alert according to Settings:
 * - always kept in the Alerts history and posted as a silent notification;
 * - the phone vibrates if [Settings.vibratePhone] is on;
 * - the watch vibrates if [Settings.vibrateWatch] is on and a watch is connected.
 */
object Alerter {
    private const val CHANNEL_ID = "opencaption_alerts"

    fun raise(context: Context, alert: SoundAlert) {
        AppState.addAlert(alert)
        val pattern = Settings.patternFor(rawLabelFor(alert.label))

        if (Settings.vibrateWatch && WatchLink.isConnected) {
            WatchLink.send(
                Protocol.PATH_ALERT,
                AlertMsg(alert.label, alert.confidence, alert.ts, pattern.name).toBytes(),
            )
        }
        if (Settings.vibratePhone) {
            Haptics.play(context, pattern)
        }
        notify(context, alert)
    }

    /** Plays a pattern on the phone and, if connected, on the watch. Used by "Test" buttons. */
    fun test(context: Context, rawLabel: String, pattern: VibePattern) {
        Haptics.play(context, pattern)
        if (WatchLink.isConnected) {
            WatchLink.send(
                Protocol.PATH_ALERT,
                AlertMsg(SoundLabels.shortName(rawLabel), 1f, System.currentTimeMillis(), pattern.name).toBytes(),
            )
        }
    }

    private fun rawLabelFor(shortName: String): String =
        SoundLabels.ALL.entries.firstOrNull { it.value == shortName }?.key ?: shortName

    private fun notify(context: Context, alert: SoundAlert) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            // Vibration is handled by Alerter, so the channel itself stays silent and does not vibrate.
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Sound alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    enableVibration(false)
                    setSound(null, null)
                }
            )
        }
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val percent = (alert.confidence * 100).toInt()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Possible ${alert.label.lowercase()}")
            .setContentText("Confidence about $percent%")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setSilent(true)
            .build()
        NotificationManagerCompat.from(context).notify(alert.ts.toInt(), notification)
    }
}

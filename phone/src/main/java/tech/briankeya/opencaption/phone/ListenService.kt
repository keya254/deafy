package tech.briankeya.opencaption.phone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import tech.briankeya.opencaption.shared.CaptionMsg
import tech.briankeya.opencaption.shared.Protocol
import tech.briankeya.opencaption.shared.StatusMsg

/**
 * Foreground service that owns the microphone for captions and/or sound alerts.
 * Runs only while the user has turned one of them on (from the phone or the watch).
 */
class ListenService : Service() {
    private var captions: CaptionSource? = null
    private var sound: SoundEngine? = null
    private var soundAlertsRunning = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val wantCaptions = intent?.getBooleanExtra(EXTRA_CAPTIONS, false) ?: false
        val wantSound = intent?.getBooleanExtra(EXTRA_SOUND, false) ?: false
        val reload = intent?.getBooleanExtra(EXTRA_RELOAD, false) ?: false

        // Must call startForeground promptly after startForegroundService().
        showForeground(wantCaptions, wantSound)
        apply(wantCaptions, wantSound, reload)
        return START_NOT_STICKY
    }

    private fun apply(wantCaptions: Boolean, wantSound: Boolean, reload: Boolean) {
        if (reload) {
            captions?.stop(); captions = null
            sound?.stop(); sound = null
        }

        if (!wantCaptions) {
            captions?.stop(); captions = null
        } else if (captions == null) {
            val status: (String) -> Unit = { msg -> AppState.update { it.copy(status = msg) } }
            // Prefer the offline Whisper model (works with no internet). Fall back to Android's recognizer.
            val engine: CaptionSource = if (LocalSpeechEngine.isInstalled(this)) {
                LocalSpeechEngine(this, status)
            } else {
                CaptionEngine(this, status)
            }
            if (engine.start()) captions = engine
        }

        // Sound engine runs for alerts, and also for caption events while captions are on.
        val needSound = wantSound || (wantCaptions && Settings.captionEvents.isNotEmpty())
        if (!needSound) {
            sound?.stop(); sound = null; soundAlertsRunning = false
        } else if (sound == null || soundAlertsRunning != wantSound) {
            sound?.stop()
            val engine = SoundEngine(
                context = this,
                onStatus = { msg -> AppState.update { it.copy(status = msg) } },
                onAlert = { alert -> Alerter.raise(this, alert) },
                onCaptionEvent = { tag -> addEventToCaptions(tag) },
                alertsEnabled = wantSound,
            )
            sound = if (engine.start()) engine else null
            soundAlertsRunning = wantSound
        }

        val captionsOn = captions != null
        val soundOn = sound != null && wantSound
        AppState.update { it.copy(captionsOn = captionsOn, soundOn = soundOn) }
        WatchLink.send(
            Protocol.PATH_STATUS,
            StatusMsg(captionsOn, soundOn, if (captionsOn || soundOn) "Listening" else "Off").toBytes(),
        )

        if (!captionsOn && !soundOn) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            showForeground(captionsOn, soundOn)
        }
    }

    /** Shows a sound event such as [Clapping] in the caption stream and on the watch. */
    private fun addEventToCaptions(tag: String) {
        val now = System.currentTimeMillis()
        AppState.addCaption(tag, now)
        WatchLink.send(Protocol.PATH_CAPTION, CaptionMsg(tag, true, now).toBytes())
    }

    private fun showForeground(captionsOn: Boolean, soundOn: Boolean) {
        ensureChannel()
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, ListenService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val parts = listOfNotNull(
            "Captions".takeIf { captionsOn },
            "Sound alerts".takeIf { soundOn },
        )
        val text = if (parts.isEmpty()) "Starting" else parts.joinToString(" + ") + " on"
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Open Caption is listening")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, "Stop", stopIntent)
            .build()
        ServiceCompat.startForeground(
            this, NOTIF_ID, notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Listening", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onDestroy() {
        captions?.stop(); captions = null
        sound?.stop(); sound = null
        AppState.update { it.copy(captionsOn = false, soundOn = false, partial = "") }
        WatchLink.send(Protocol.PATH_STATUS, StatusMsg(false, false, "Off").toBytes())
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CAPTIONS = "captions"
        const val EXTRA_SOUND = "sound"
        const val EXTRA_RELOAD = "reload"
        const val ACTION_STOP = "tech.briankeya.opencaption.action.STOP"
        private const val CHANNEL_ID = "opencaption_listen"
        private const val NOTIF_ID = 1

        /**
         * Sets which features should be running. Both false stops the service.
         * [reload] restarts engines so new settings (labels, threshold) take effect.
         */
        fun request(context: Context, captions: Boolean, sound: Boolean, reload: Boolean = false) {
            if (!captions && !sound) {
                context.stopService(Intent(context, ListenService::class.java))
                return
            }
            val intent = Intent(context, ListenService::class.java)
                .putExtra(EXTRA_CAPTIONS, captions)
                .putExtra(EXTRA_SOUND, sound)
                .putExtra(EXTRA_RELOAD, reload)
            context.startForegroundService(intent)
        }
    }
}

package ai.pairo.voice

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder

/** Jembatan kecil antara layanan notifikasi dan layar utama. */
object Bus {
    @Volatile
    var onStop: (() -> Unit)? = null
    @Volatile
    var onStart: (() -> Unit)? = null
}

class MicService : android.app.Service() {

    companion object {
        const val CHANNEL = "pairo_voice"
        const val ACT_STOP = "ai.pairo.voice.action.STOP"
        const val ACT_TALK = "ai.pairo.voice.action.TALK"
        private const val NOTIF_ID = 41

        fun start(ctx: Context) {
            val i = Intent(ctx, MicService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, MicService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(CHANNEL, "P.A.I.R.O", NotificationManager.IMPORTANCE_LOW)
            ch.description = "Status pendengaran mikrofon"
            ch.setShowBadge(false)
            nm?.createNotificationChannel(ch)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACT_STOP) {
            Bus.onStop?.invoke()
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACT_TALK) {
            Bus.onStart?.invoke()
        }

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, MicService::class.java).setAction(ACT_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val talk = PendingIntent.getService(
            this, 2,
            Intent(this, MicService::class.java).setAction(ACT_TALK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            android.app.Notification.Builder(this, CHANNEL)
        else
            android.app.Notification.Builder(this)

        val notif = builder
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(
                android.app.Notification.Action.Builder(
                    android.R.drawable.ic_btn_speak_now, getString(R.string.notif_talk), talk
                ).build()
            )
            .addAction(
                android.app.Notification.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.notif_stop), stop
                ).build()
            )
            .build()

        startForeground(NOTIF_ID, notif)
        return START_STICKY
    }

    override fun onDestroy() {
        Bus.onStop = null
        super.onDestroy()
    }
}

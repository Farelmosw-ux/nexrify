package id.nexrify.music.service

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import id.nexrify.music.MainActivity
import id.nexrify.music.R
import id.nexrify.music.data.call.CallManager

/**
 * Layanan foreground bertipe mikrofon selama panggilan suara. Tanpa ini, Android memutus akses mikrofon begitu
 * aplikasi pindah ke latar belakang. Logika panggilan sendiri ada di [CallManager]; layanan ini hanya menjaga proses hidup.
 */
class CallService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        CallManager.ensureChannels(applicationContext)
        val name = intent?.getStringExtra("name").orEmpty()
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(this, CallManager.CH_ONGOING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Panggilan suara")
            .setContentText(if (name.isBlank()) "Sedang berlangsung" else "Bersama $name")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(open)
            .build()
        if (Build.VERSION.SDK_INT >= 30) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(NOTIF_ID, n)
        return START_NOT_STICKY
    }

    private companion object {
        const val NOTIF_ID = 4311
    }
}

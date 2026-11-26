package id.nexrify.music.push

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import id.nexrify.music.MainActivity
import id.nexrify.music.R

/** Notifikasi sederhana (surat dan undangan Musik Bareng) yang dibuat dari pesan push saat aplikasi sedang berjalan. */
object PushNotifier {
    const val CH_MAIL = "nexrify_mail_v2"
    const val CH_MUBAR = "nexrify_mubar"

    fun show(context: Context, channel: String, tag: String, id: Int, title: String, body: String, timeoutMs: Long = 0L, extras: Map<String, String> = emptyMap()) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).also { i -> extras.forEach { (k, v) -> i.putExtra(k, v) } },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
        if (timeoutMs > 0) b.setTimeoutAfter(timeoutMs)
        try { manager.notify(tag, id, b.build()) } catch (_: SecurityException) {}
    }
}

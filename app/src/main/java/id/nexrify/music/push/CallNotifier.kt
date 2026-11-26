package id.nexrify.music.push

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import id.nexrify.music.MainActivity
import id.nexrify.music.R
import id.nexrify.music.data.call.CallManager

/**
 * Notifikasi panggilan masuk ala telepon: dering berulang, muncul penuh di layar kunci, dan punya tombol
 * Jawab / Tolak. Di Android 12+ memakai gaya panggilan bawaan sistem (tombol hijau dan merah), di bawahnya tombol biasa.
 *
 * - Tolak: dikirim ke [CallActionReceiver], tanpa membuka aplikasi.
 * - Jawab: membuka [MainActivity] dengan extra [EXTRA_CALL], lalu layar panggilan meminta izin mikrofon dan menjawab.
 */
object CallNotifier {
    const val EXTRA_CALL = "nx_call"
    const val CALL_ANSWER = "answer"
    const val CALL_OPEN = "open"
    const val ACTION_DECLINE = "id.nexrify.music.CALL_DECLINE"
    const val NOTIF_ID = 4312
    private const val TAG = "incoming_call"
    private const val RING_TIMEOUT_MS = 45_000L

    fun showIncoming(context: Context, name: String, callId: String? = null) {
        val app = context.applicationContext
        val manager = NotificationManagerCompat.from(app)
        if (!manager.areNotificationsEnabled()) return
        CallManager.ensureChannels(app)

        val who = name.ifBlank { "Nexrify" }
        val openIntent = PendingIntent.getActivity(
            app, 41,
            Intent(app, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_CALL, CALL_OPEN),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val answerIntent = PendingIntent.getActivity(
            app, 42,
            Intent(app, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_CALL, CALL_ANSWER),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val declineIntent = PendingIntent.getBroadcast(
            app, 43,
            Intent(app, CallActionReceiver::class.java).setAction(ACTION_DECLINE).putExtra("id", callId.orEmpty()),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val person = Person.Builder().setName(who).setImportant(true).build()
        val b = NotificationCompat.Builder(app, CallManager.CH_INCOMING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(who)
            .setContentText(app.getString(R.string.app_name) + " · " + incomingLabel())
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(false)
            .setTimeoutAfter(RING_TIMEOUT_MS)
            .setContentIntent(openIntent)
            .setFullScreenIntent(openIntent, true)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(person, declineIntent, answerIntent))
        val n = b.build()
        // Dering terus sampai dijawab, ditolak, atau waktunya habis (bukan sekali bunyi seperti notifikasi biasa).
        n.flags = n.flags or Notification.FLAG_INSISTENT
        try { manager.notify(TAG, NOTIF_ID, n) } catch (_: SecurityException) {}
    }

    fun cancel(context: Context) {
        runCatching { NotificationManagerCompat.from(context.applicationContext).cancel(TAG, NOTIF_ID) }
    }

    private fun incomingLabel(): String =
        if (id.nexrify.music.i18n.currentStrings().english) "Incoming voice call" else "Panggilan suara masuk"
}

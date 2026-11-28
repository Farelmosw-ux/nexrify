package id.nexrify.music.data.chat

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import id.nexrify.music.MainActivity
import id.nexrify.music.R
import id.nexrify.music.data.remote.HttpClientProvider
import id.nexrify.music.i18n.currentStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Notifikasi HP untuk Global Chat dan sosial. Satu notifikasi per jenis tiap putaran (tidak spam), dengan:
 *  - saluran terpisah per jenis supaya bisa diatur di pengaturan Android,
 *  - foto profil pengikut sebagai ikon besar,
 *  - pengikut banyak digabung (daftar nama) dan mengetuknya membuka pusat notifikasi,
 *  - dikelompokkan: chat (mention, balasan, pesan) dan sosial (pengikut, teman, reaksi).
 */
object ChatNotifier {
    const val EXTRA_OPEN_CHAT = "nexrify.open_chat"
    const val EXTRA_SEQ = "nexrify.chat_seq"
    const val EXTRA_OPEN_DM = "nexrify.open_dm"
    const val EXTRA_PEER = "nexrify.dm_peer"
    const val EXTRA_OPEN_PROFILE = "nexrify.open_profile"
    const val EXTRA_OPEN_NOTIFS = "nexrify.open_notifs"

    private data class Kind(val code: String, val channel: String, val id: Int, val group: String)

    private val KINDS = listOf(
        Kind("m", "chat_mention", 4101, "nexrify_chat"),
        Kind("r", "chat_reply", 4102, "nexrify_chat"),
        Kind("x", "chat_reaction", 4103, "nexrify_social"),
        Kind("a", "chat_announce", 4104, "nexrify_news"),
        Kind("d", "chat_dm_v2", 4105, "nexrify_chat"),
        Kind("w", "chat_warn", 4106, "nexrify_news"),
        Kind("f", "chat_follow", 4107, "nexrify_social"),
        Kind("F", "chat_follow", 4108, "nexrify_social"),
        Kind("R", "chat_role", 4109, "nexrify_news")
    )

    /**
     * Buat semua saluran notifikasi sejak aplikasi dibuka. Pesan push yang ditampilkan sistem saat aplikasi tertutup
     * hanya bisa memakai saluran yang sudah ada, jadi saluran tidak boleh baru dibuat saat notifikasi pertama datang.
     * Saluran surat memakai id baru (v2) dengan prioritas tinggi karena tingkat penting saluran lama tidak bisa diubah.
     */
    fun ensureChannels(context: Context) {
        val s = currentStrings()
        val system = context.getSystemService(NotificationManager::class.java)
        fun ch(id: String, name: String, importance: Int) = system.createNotificationChannel(NotificationChannel(id, name, importance))
        ch("chat_mention", s.chatNotifChannelMention, NotificationManager.IMPORTANCE_HIGH)
        ch("chat_reply", s.chatNotifChannelReply, NotificationManager.IMPORTANCE_HIGH)
        ch("chat_reaction", s.chatNotifChannelReaction, NotificationManager.IMPORTANCE_LOW)
        ch("chat_announce", s.chatNotifChannelAnnounce, NotificationManager.IMPORTANCE_HIGH)
        // Saluran DM memakai nada pesan Nexrify (id baru karena suara saluran lama tidak bisa diubah).
        system.deleteNotificationChannel("chat_dm")
        system.createNotificationChannel(NotificationChannel("chat_dm_v2", s.chatNotifChannelDm, NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(
                android.net.Uri.parse("android.resource://" + context.packageName + "/" + R.raw.pesan),
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            )
        })
        DmNotifier.ensureChannels(context)
        ch("chat_warn", s.chatNotifChannelWarn, NotificationManager.IMPORTANCE_HIGH)
        ch("chat_follow", s.notifChannelFollow, NotificationManager.IMPORTANCE_HIGH)
        ch("chat_role", s.notifChannelRole, NotificationManager.IMPORTANCE_HIGH)
        ch(id.nexrify.music.push.PushNotifier.CH_MAIL, s.mailChannel, NotificationManager.IMPORTANCE_HIGH)
        ch(id.nexrify.music.push.PushNotifier.CH_MUBAR, s.mubarNotifChannel, NotificationManager.IMPORTANCE_HIGH)
    }

    suspend fun show(context: Context, items: List<ChatNotif>) {
        if (items.isEmpty()) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val s = currentStrings()
        val system = context.getSystemService(NotificationManager::class.java)
        for (kind in KINDS) {
            val group = items.filter { it.kind == kind.code }
            if (group.isEmpty()) continue
            if (kind.code == "d") { group.forEach { DmNotifier.show(context, it) }; continue }
            val last = group.last()
            val follow = kind.code == "f" || kind.code == "F"
            val channelName = when (kind.code) {
                "m" -> s.chatNotifChannelMention
                "r" -> s.chatNotifChannelReply
                "d" -> s.chatNotifChannelDm
                "w" -> s.chatNotifChannelWarn
                "x" -> s.chatNotifChannelReaction
                "f", "F" -> s.notifChannelFollow
                "R" -> s.notifChannelRole
                else -> s.chatNotifChannelAnnounce
            }
            val importance = if (kind.code == "x") NotificationManager.IMPORTANCE_LOW else NotificationManager.IMPORTANCE_HIGH
            system.createNotificationChannel(NotificationChannel(kind.channel, channelName, importance))

            // Pengikut: teks "uid|nama tampilan". Yang lain: teks pesan.
            val people = if (follow) group.map { it.from to it.text.substringAfter('|', it.from).ifBlank { it.from } }.distinctBy { it.first } else emptyList()
            val many = follow && people.size > 1
            val title = when (kind.code) {
                "m" -> s.chatNotifMentionTitle(last.from)
                "r" -> s.chatNotifReplyTitle(last.from)
                "x" -> s.chatNotifReactionTitle(last.from)
                "w" -> s.chatNotifWarnTitle
                "d" -> if (group.map { it.from }.distinct().size > 1) s.dmNotifTitleMany else s.dmNotifTitle(last.from)
                "f" -> if (many) s.notifFollowManyTitle(people.size) else s.notifFollowTitle(people.first().second)
                "F" -> if (many) s.notifFollowManyTitle(people.size) else s.notifFriendTitle(people.first().second)
                "R" -> s.notifRoleTitle(last.text)
                else -> s.chatNotifAnnounceTitle
            }
            val body = when (kind.code) {
                "x" -> s.chatNotifReactionBody(last.text)
                "f" -> if (many) people.joinToString(", ") { it.second } else s.notifFollowBody
                "F" -> if (many) people.joinToString(", ") { it.second } else s.notifFriendBody
                "R" -> s.notifRoleBody(last.text)
                else -> last.text
            }
            val more = if (!follow && group.size > 1) "\n" + s.chatNotifMore(group.size - 1) else ""

            val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            when {
                kind.code == "d" -> {
                    val sameSender = group.map { it.from }.distinct().size == 1
                    intent.putExtra(EXTRA_OPEN_DM, true).putExtra(EXTRA_PEER, if (sameSender) last.from else "")
                }
                follow && many -> intent.putExtra(EXTRA_OPEN_NOTIFS, true)
                follow -> intent.putExtra(EXTRA_OPEN_PROFILE, people.first().first)
                kind.code == "R" -> intent.putExtra(EXTRA_OPEN_PROFILE, id.nexrify.music.data.account.AccountManager.getInstance().user.value?.username.orEmpty())
                else -> intent.putExtra(EXTRA_OPEN_CHAT, true).putExtra(EXTRA_SEQ, last.seq)
            }
            val open = PendingIntent.getActivity(context, kind.id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

            val b = NotificationCompat.Builder(context, kind.channel)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(open)
                .setGroup(kind.group)
                .setWhen(last.ts.takeIf { it > 0 } ?: System.currentTimeMillis())
                .setShowWhen(true)
                .setCategory(if (follow || kind.code == "x") NotificationCompat.CATEGORY_SOCIAL else if (kind.code == "a" || kind.code == "w") NotificationCompat.CATEGORY_STATUS else NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(if (kind.code == "x") NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            if (many) {
                val st = NotificationCompat.InboxStyle().setBigContentTitle(title)
                people.take(6).forEach { st.addLine(it.second) }
                if (people.size > 6) st.setSummaryText(s.chatNotifMore(people.size - 6))
                b.setStyle(st)
            } else {
                b.setStyle(NotificationCompat.BigTextStyle().bigText(body + more))
            }
            if (follow && !many) {
                b.addAction(0, s.notifActionProfile, open)
                avatarBitmap(last.text.substringBefore('|'))?.let { b.setLargeIcon(it) }
            }
            try { manager.notify(kind.id, b.build()) } catch (_: SecurityException) {}
        }
    }

    /** Foto profil dibulatkan untuk ikon besar. Null kalau tidak ada atau gagal diunduh (notifikasi tetap tampil). */
    internal suspend fun avatarBitmap(uid: String): Bitmap? = withContext(Dispatchers.IO) {
        if (uid.length != 8) return@withContext null
        try {
            val client = HttpClientProvider.client.newBuilder().callTimeout(4, TimeUnit.SECONDS).build()
            client.newCall(Request.Builder().url(ChatApi.avatarUrl(uid, "n")).get().build()).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val src = BitmapFactory.decodeStream(r.body?.byteStream()) ?: return@use null
                val size = minOf(src.width, src.height).coerceAtMost(256)
                val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val c = Canvas(out)
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                c.drawCircle(size / 2f, size / 2f, size / 2f, p)
                p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                val sx = (src.width - minOf(src.width, src.height)) / 2
                val sy = (src.height - minOf(src.width, src.height)) / 2
                c.drawBitmap(src, Rect(sx, sy, sx + minOf(src.width, src.height), sy + minOf(src.width, src.height)), Rect(0, 0, size, size), p)
                out
            }
        } catch (_: Throwable) { null }
    }
}

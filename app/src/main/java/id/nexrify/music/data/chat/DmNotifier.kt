package id.nexrify.music.data.chat

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import id.nexrify.music.MainActivity
import id.nexrify.music.R
import id.nexrify.music.data.account.AccountManager
import id.nexrify.music.i18n.currentStrings
import org.json.JSONObject

/**
 * Notifikasi pesan pribadi ala aplikasi chat: satu notifikasi per percakapan (gaya percakapan dengan foto profil),
 * pesan baru ditumpuk di notifikasi yang sama, dan tiga tombol: Balas (ketik langsung di notifikasi),
 * Tandai dibaca, dan Senyapkan. Beberapa percakapan dikelompokkan di bawah satu ringkasan.
 */
object DmNotifier {
    const val ACTION_REPLY = "id.nexrify.music.DM_REPLY"
    const val ACTION_READ = "id.nexrify.music.DM_READ"
    const val ACTION_MUTE = "id.nexrify.music.DM_MUTE"
    const val ACTION_UNMUTE = "id.nexrify.music.DM_UNMUTE"
    const val KEY_REPLY = "nx_dm_reply"
    const val CH_DM = "chat_dm_v2"
    const val CH_INFO = "chat_dm_info"

    private const val ID_CHAT = 4300
    private const val ID_SUMMARY = 4301
    private const val ID_INFO = 4302
    private const val TAG_SUMMARY = "dsum"
    private const val GROUP = "nexrify_dm"
    private const val ACCENT = 0xFF3B3FD9.toInt()

    private val names = HashMap<String, Pair<String, String>>() // username(lowercase) -> (uid, display)

    private fun tagOf(uid: String) = "d_$uid"

    fun ensureChannels(context: Context) {
        val s = currentStrings()
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CH_INFO, s.dmNotifInfoChannel, NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
    }

    /** Tampilkan (atau perbarui) notifikasi percakapan. [n.from] adalah username pengirim; uid dan nama tampilan dicari kalau belum ada. */
    suspend fun show(context: Context, n: ChatNotif) {
        val nmc = NotificationManagerCompat.from(context)
        if (!nmc.areNotificationsEnabled()) return
        var uid = n.uid
        var display = n.display
        if (uid.isBlank()) {
            val cached = names[n.from.lowercase()]
            if (cached != null) { uid = cached.first; display = display.ifBlank { cached.second } }
            else {
                val p = runCatching { ChatManager.getInstance().profile(n.from.lowercase()) }.getOrNull()
                if (p != null) { uid = p.uid; display = display.ifBlank { p.display } }
            }
        }
        if (uid.isBlank()) return // tanpa uid tombol aksi tak bisa bekerja; ChatNotifier lama tidak dipakai untuk DM
        display = display.ifBlank { n.from }
        names[n.from.lowercase()] = uid to display
        val avatar = ChatNotifier.avatarBitmap(uid)
        post(context, uid, n.from, display, avatar) { style, sender ->
            style.addMessage(n.text, n.ts.takeIf { it > 0 } ?: System.currentTimeMillis(), sender)
        }
    }

    /**
     * Susun ulang notifikasi percakapan [uid]: pakai isi yang sudah tampil (supaya pesan sebelumnya tetap ada), lalu
     * [add] menambah pesan baru. [quiet] = true untuk pembaruan setelah membalas (tanpa bunyi dan getar).
     */
    private fun post(
        context: Context, uid: String, username: String, display: String, avatar: Bitmap?,
        quiet: Boolean = false, add: (NotificationCompat.MessagingStyle, Person) -> Unit
    ) {
        val s = currentStrings()
        val nmc = NotificationManagerCompat.from(context)
        val sys = context.getSystemService(NotificationManager::class.java)
        val tag = tagOf(uid)
        val existing = runCatching { sys.activeNotifications.firstOrNull { it.tag == tag && it.id == ID_CHAT } }.getOrNull()
        val me = Person.Builder().setName(s.dmNotifYou).build()
        val sender = Person.Builder().setName(display).setKey(uid)
            .apply { if (avatar != null) setIcon(IconCompat.createWithBitmap(avatar)) }.build()
        val style = existing?.let { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it.notification) }
            ?: NotificationCompat.MessagingStyle(me)
        add(style, sender)

        val open = PendingIntent.getActivity(
            context, uid.hashCode(),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(ChatNotifier.EXTRA_OPEN_DM, true).putExtra(ChatNotifier.EXTRA_PEER, username),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val mutable = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0

        fun action(act: String, code: Int, flags: Int): PendingIntent = PendingIntent.getBroadcast(
            context, uid.hashCode() * 8 + code,
            Intent(context, DmActionReceiver::class.java).setAction(act).putExtra("uid", uid).putExtra("user", username).putExtra("name", display),
            PendingIntent.FLAG_UPDATE_CURRENT or flags
        )

        val reply = NotificationCompat.Action.Builder(0, s.dmNotifReply, action(ACTION_REPLY, 1, mutable))
            .addRemoteInput(RemoteInput.Builder(KEY_REPLY).setLabel(s.dmNotifReplyHint).build())
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .setAllowGeneratedReplies(true)
            .build()
        val read = NotificationCompat.Action.Builder(0, s.dmNotifMarkRead, action(ACTION_READ, 2, PendingIntent.FLAG_IMMUTABLE))
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()
        val mute = NotificationCompat.Action.Builder(0, s.dmNotifMute, action(ACTION_MUTE, 3, PendingIntent.FLAG_IMMUTABLE))
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MUTE)
            .setShowsUserInterface(false)
            .build()

        val b = NotificationCompat.Builder(context, CH_DM)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ACCENT)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOnlyAlertOnce(quiet)
            .setGroup(GROUP)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setContentIntent(open)
            .addAction(reply)
            .addAction(read)
            .addAction(mute)
        if (avatar != null) b.setLargeIcon(avatar)
        try { nmc.notify(tag, ID_CHAT, b.build()) } catch (_: SecurityException) { return }
        refreshSummary(context)
    }

    /** Ringkasan kelompok: muncul hanya kalau ada dua percakapan atau lebih; hilang sendiri kalau tinggal satu. */
    private fun refreshSummary(context: Context) {
        val nmc = NotificationManagerCompat.from(context)
        val sys = context.getSystemService(NotificationManager::class.java)
        val chats = runCatching { sys.activeNotifications.filter { it.id == ID_CHAT && it.tag?.startsWith("d_") == true } }.getOrDefault(emptyList())
        if (chats.size < 2) { nmc.cancel(TAG_SUMMARY, ID_SUMMARY); return }
        val s = currentStrings()
        val open = PendingIntent.getActivity(
            context, ID_SUMMARY,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(ChatNotifier.EXTRA_OPEN_DM, true).putExtra(ChatNotifier.EXTRA_PEER, ""),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val sum = NotificationCompat.Builder(context, CH_DM)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ACCENT)
            .setContentTitle(s.dmNotifSummary(chats.size))
            .setStyle(NotificationCompat.InboxStyle().setSummaryText(s.dmNotifSummary(chats.size)))
            .setGroup(GROUP)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try { nmc.notify(TAG_SUMMARY, ID_SUMMARY, sum) } catch (_: SecurityException) { }
    }

    fun cancel(context: Context, uid: String) {
        val nmc = NotificationManagerCompat.from(context)
        nmc.cancel(tagOf(uid), ID_CHAT)
        refreshSummary(context)
    }

    /** Dipanggil saat percakapan dibuka di aplikasi: notifikasinya tidak perlu tampil lagi. */
    fun cancelForUsername(context: Context, username: String) {
        names[username.lowercase()]?.first?.let { cancel(context, it) }
    }

    /* ---------------- aksi dari tombol ---------------- */

    private fun token(): String? = AccountManager.getInstance().token()

    /** Kirim balasan dari notifikasi, lalu perbarui notifikasi (balasan muncul di percakapan, spinner berhenti). */
    suspend fun reply(context: Context, uid: String, username: String, name: String, text: String) {
        val s = currentStrings()
        val t = token()
        var ok = false
        if (t != null) {
            ok = try {
                ChatApi.post(t, JSONObject().put("action", "dm_send").put("to", uid).put("text", text))
                runCatching { ChatApi.post(t, JSONObject().put("action", "dm_read").put("to", uid)) }
                true
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                false
            }
        }
        post(context, uid, username, name, null, quiet = true) { style, _ ->
            // sender null = diri sendiri (Person "Kamu")
            style.addMessage(if (ok) text else s.dmNotifSendFailed(text), System.currentTimeMillis(), null as Person?)
        }
    }

    suspend fun markRead(context: Context, uid: String) {
        val t = token()
        if (t != null) runCatching { ChatApi.post(t, JSONObject().put("action", "dm_read").put("to", uid)) }
        cancel(context, uid)
    }

    /** Senyapkan percakapan: notifikasinya hilang dan tidak muncul lagi sampai diaktifkan. Konfirmasi singkat dengan tombol Aktifkan lagi. */
    suspend fun mute(context: Context, uid: String, username: String, name: String) {
        val s = currentStrings()
        val t = token()
        var ok = false
        if (t != null) ok = runCatching { ChatApi.post(t, JSONObject().put("action", "dm_mute").put("to", uid).put("on", true)) }.isSuccess
        cancel(context, uid)
        if (!ok) return
        ensureChannels(context)
        val undo = PendingIntent.getBroadcast(
            context, uid.hashCode() * 8 + 4,
            Intent(context, DmActionReceiver::class.java).setAction(ACTION_UNMUTE).putExtra("uid", uid).putExtra("user", username).putExtra("name", name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(context, CH_INFO)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ACCENT)
            .setContentTitle(s.dmNotifMutedTitle(name))
            .setContentText(s.dmNotifMutedBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(s.dmNotifMutedBody))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setTimeoutAfter(10_000L)
            .addAction(0, s.dmNotifUnmute, undo)
            .build()
        try { NotificationManagerCompat.from(context).notify("dmute_$uid", ID_INFO, n) } catch (_: SecurityException) { }
    }

    suspend fun unmute(context: Context, uid: String, name: String) {
        val s = currentStrings()
        val t = token()
        val ok = t != null && runCatching { ChatApi.post(t, JSONObject().put("action", "dm_mute").put("to", uid).put("on", false)) }.isSuccess
        val nmc = NotificationManagerCompat.from(context)
        nmc.cancel("dmute_$uid", ID_INFO)
        if (!ok) return
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, CH_INFO)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ACCENT)
            .setContentTitle(s.dmNotifUnmutedTitle(name))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setTimeoutAfter(4_000L)
            .build()
        try { nmc.notify("dmute_$uid", ID_INFO, n) } catch (_: SecurityException) { }
    }
}

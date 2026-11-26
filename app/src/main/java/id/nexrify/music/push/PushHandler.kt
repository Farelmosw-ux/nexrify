package id.nexrify.music.push

import android.content.Context
import id.nexrify.music.data.account.AccountManager
import id.nexrify.music.data.chat.ChatManager
import id.nexrify.music.data.chat.ChatNav
import id.nexrify.music.data.chat.ChatNotif
import id.nexrify.music.data.mailbox.MailboxManager

/**
 * Pesan push yang masuk saat proses aplikasi hidup (aplikasi terbuka, atau musik sedang diputar), dan penerjemah
 * data push menjadi navigasi saat notifikasi sistem diketuk. Saat aplikasi benar-benar tertutup, notifikasi
 * ditampilkan langsung oleh sistem dari isi pesan FCM, jadi kode ini tidak dipanggil.
 */
object PushHandler {

    suspend fun handle(context: Context, kind: String, d: Map<String, String>) {
        if (!AccountManager.getInstance().isLoggedIn()) return
        val title = d["nx_title"].orEmpty()
        val body = d["nx_body"].orEmpty()
        when (kind) {
            "m", "r", "x", "a", "d", "w", "f", "F", "R" -> {
                val from = if (kind == "a") "Nexrify" else d["nx_user"].orEmpty()
                val text = when (kind) {
                    "x" -> d["nx_emoji"].orEmpty()
                    "f", "F" -> d["nx_uid"].orEmpty() + "|" + d["nx_name"].orEmpty()
                    "R" -> d["nx_role"].orEmpty()
                    else -> body
                }
                val seq = d["nx_seq"]?.toLongOrNull() ?: 0L
                ChatManager.getInstance().onPush(ChatNotif(kind, seq, from, text, System.currentTimeMillis()))
            }
            "c" -> id.nexrify.music.data.call.CallManager.getInstance().checkIncomingNow()
            "mail" -> {
                PushNotifier.show(context, PushNotifier.CH_MAIL, "mail_" + d["nx_id"].orEmpty(), 4208, title, body, extras = mapOf("nx_kind" to "mail"))
                runCatching { MailboxManager.getInstance().refresh() }
            }
            "mb" -> {
                PushNotifier.show(context, PushNotifier.CH_MUBAR, "mb_" + d["nx_code"].orEmpty(), 4209, title, body, timeoutMs = 10 * 60_000L, extras = mapOf("nx_kind" to "mb", "nx_code" to d["nx_code"].orEmpty()))
                runCatching { id.nexrify.music.data.mubar.MubarManager.getInstance().refresh() }
            }
        }
    }

    /**
     * Dipanggil MainActivity: notifikasi yang ditampilkan sistem saat aplikasi tertutup membawa data pesan sebagai extra Intent.
     * Hasilnya diarahkan ke layar yang sama dengan notifikasi buatan aplikasi.
     */
    fun route(intent: android.content.Intent?) {
        if (intent == null) return
        val kind = intent.getStringExtra("nx_kind") ?: return
        val user = intent.getStringExtra("nx_user").orEmpty()
        val seq = intent.getStringExtra("nx_seq")?.toLongOrNull() ?: 0L
        when (kind) {
            "m", "r", "x", "a" -> ChatNav.request.value = seq
            "w" -> ChatNav.request.value = 0L
            "d" -> ChatNav.dmRequest.value = user
            "f", "F", "R" -> if (user.isNotBlank()) ChatNav.profileRequest.value = user
            "c" -> runCatching { id.nexrify.music.data.call.CallManager.getInstance().checkIncomingNow() }
            "mail" -> ChatNav.mailboxRequest.value = true
            "mb" -> ChatNav.mubarRequest.value = intent.getStringExtra("nx_code").orEmpty().ifBlank { "-" }
        }
        listOf("nx_kind", "nx_user", "nx_seq", "nx_title", "nx_body", "nx_uid", "nx_name", "nx_emoji", "nx_id", "nx_link", "nx_code", "nx_role")
            .forEach { intent.removeExtra(it) }
    }
}

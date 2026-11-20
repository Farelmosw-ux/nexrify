package id.nexrify.music.push

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.messaging.FirebaseMessaging
import id.nexrify.music.data.account.AccountApi
import id.nexrify.music.data.account.AccountError
import id.nexrify.music.data.account.AccountManager
import id.nexrify.music.data.chat.ChatNotifier
import id.nexrify.music.data.chat.ChatSettings
import id.nexrify.music.data.settings.SettingsManager
import id.nexrify.music.util.CrashReporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * Notifikasi push (Firebase Cloud Messaging). Tugasnya:
 *  - mendaftarkan token perangkat ke server Nexrify (per akun) setiap login, token berganti, atau pengaturan notifikasi/bahasa berubah,
 *  - membuat semua saluran notifikasi sejak aplikasi dibuka (FCM butuh salurannya sudah ada saat aplikasi sedang ditutup),
 *  - memberi tahu bagian lain apakah push aktif ([isActive]) supaya pengecekan berkala tidak menampilkan notifikasi ganda.
 */
object PushManager {
    private const val PREFS = "nexrify_push"
    private const val K_FCM = "fcm_token"
    private const val K_SIG = "sig"
    private const val K_AT = "synced_at"
    private const val K_READY = "ready"
    private const val RESYNC_MS = 24L * 3600_000L

    private lateinit var app: Context
    private lateinit var prefs: SharedPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CrashReporter.handler)

    @Volatile private var ready = false

    /** True kalau server sudah memastikan FCM aktif dan token perangkat ini terdaftar. Selain itu notifikasi tetap dari pengecekan berkala. */
    fun isActive(): Boolean = ready

    fun init(context: Context) {
        app = context.applicationContext
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        ready = prefs.getBoolean(K_READY, false)
        runCatching { ChatNotifier.ensureChannels(app) }

        scope.launch {
            try {
                val s = ChatSettings.getInstance()
                val flags = listOf(s.notifAll, s.notifMention, s.notifReply, s.notifReaction, s.notifAnnounce, s.notifDm, s.notifFollow)
                val sig = combine(
                    AccountManager.getInstance().user.map { it?.id.orEmpty() },
                    SettingsManager.getInstance().language.map { SettingsManager.getInstance().isEnglish() },
                    combine(flags) { arr -> arr.joinToString("") { if (it) "1" else "0" } }
                ) { uid, en, f -> "$uid|$en|$f" }
                // Tunggu sebentar supaya beberapa perubahan beruntun (misalnya login lalu sinkron akun) jadi satu pendaftaran.
                sig.collectLatest {
                    delay(1500)
                    // Kesalahan satu sinkron tidak boleh menghentikan pemantauan berikutnya
                    try { syncNow() } catch (e: CancellationException) { throw e } catch (e: Throwable) { CrashReporter.logNonFatal(e) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                CrashReporter.logNonFatal(e)
            }
        }
    }

    /** Dipanggil FCM saat token baru terbit. */
    fun onNewToken(token: String) {
        if (!::prefs.isInitialized) return
        prefs.edit().putString(K_FCM, token).remove(K_SIG).apply()
        scope.launch { syncNow(force = true) }
    }

    /** Daftar jenis notifikasi yang dimatikan pengguna, supaya server tidak mengirimnya sama sekali. */
    private fun offKinds(): List<String> {
        val s = ChatSettings.getInstance()
        if (!s.notifAll.value) return listOf("m", "r", "x", "a", "d", "w", "f", "F")
        return buildList {
            if (!s.notifMention.value) add("m")
            if (!s.notifReply.value) add("r")
            if (!s.notifReaction.value) add("x")
            if (!s.notifAnnounce.value) add("a")
            if (!s.notifDm.value) add("d")
            if (!s.notifFollow.value) { add("f"); add("F") }
        }
    }

    private suspend fun fetchToken(): String? = suspendCancellableCoroutine { cont ->
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { t ->
                if (cont.isActive) cont.resume(if (t.isSuccessful) t.result?.takeIf { it.isNotBlank() } else null)
            }
        } catch (e: Throwable) {
            if (cont.isActive) cont.resume(null)
        }
    }

    suspend fun syncNow(force: Boolean = false) {
        if (!::prefs.isInitialized) return
        val acc = AccountManager.getInstance()
        val session = acc.token() ?: return
        if (!acc.isLoggedIn()) return
        val fcm = fetchToken() ?: return
        prefs.edit().putString(K_FCM, fcm).apply()
        val body = JSONObject()
            .put("action", "push_register")
            .put("token", fcm)
            .put("lang", if (SettingsManager.getInstance().isEnglish()) "en" else "id")
            .put("off", JSONArray(offKinds()))
        val sig = acc.userId() + "|" + body.toString().hashCode()
        val fresh = System.currentTimeMillis() - prefs.getLong(K_AT, 0L) < RESYNC_MS
        if (!force && fresh && prefs.getString(K_SIG, "") == sig) return
        try {
            val r = AccountApi.auth(body, session)
            ready = r.optBoolean("ready", false)
            prefs.edit().putString(K_SIG, sig).putLong(K_AT, System.currentTimeMillis()).putBoolean(K_READY, ready).apply()
        } catch (e: CancellationException) {
            throw e
        } catch (e: AccountError) {
            // Gagal jaringan atau server: coba lagi di sinkron berikutnya. Status ready dibiarkan.
            if (e.code != "network") CrashReporter.logNonFatal(e)
        }
    }

    /** Akun keluar: lepaskan token perangkat ini dari akun lama di server, dan kembali ke pengecekan berkala. */
    fun onLoggedOut(oldSession: String?) {
        if (!::prefs.isInitialized) return
        ready = false
        val fcm = prefs.getString(K_FCM, null)
        prefs.edit().remove(K_SIG).remove(K_AT).putBoolean(K_READY, false).apply()
        if (oldSession.isNullOrBlank() || fcm.isNullOrBlank()) return
        scope.launch {
            runCatching { AccountApi.auth(JSONObject().put("action", "push_unregister").put("token", fcm), oldSession) }
        }
    }
}

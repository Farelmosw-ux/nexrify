package id.nexrify.music.data.notif

import android.content.Context
import id.nexrify.music.data.chat.ChatNotif
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Satu baris di pusat notifikasi. kind: m, r, x, a, d, w, f (pengikut baru), F (jadi teman). uid hanya untuk f/F. */
data class NotifItem(val id: String, val kind: String, val from: String, val display: String, val uid: String, val text: String, val seq: Long, val ts: Long, val read: Boolean)

/**
 * Riwayat notifikasi di perangkat (maks 100, bertahan setelah aplikasi ditutup). Diisi oleh ChatManager setiap kali
 * server mengirim item baru, jadi pengguna tetap bisa melihatnya walau notifikasi HP sudah dibuang atau dimatikan.
 */
class NotifCenter private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("nexrify_notif_center", Context.MODE_PRIVATE)
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<NotifItem>> = _items.asStateFlow()
    private val _unread = MutableStateFlow(_items.value.count { !it.read })
    val unread: StateFlow<Int> = _unread.asStateFlow()

    @Synchronized
    fun add(list: List<ChatNotif>) {
        if (list.isEmpty()) return
        val have = _items.value.map { it.id }.toHashSet()
        val fresh = list.mapNotNull { n ->
            val follow = n.kind == "f" || n.kind == "F"
            val parts = if (follow) n.text.split("|", limit = 2) else emptyList()
            val id = "${n.kind}|${n.seq}|${n.from}|${n.ts}"
            if (id in have) return@mapNotNull null
            NotifItem(
                id = id, kind = n.kind, from = n.from,
                display = if (follow) parts.getOrNull(1)?.ifBlank { n.from } ?: n.from else n.from,
                uid = if (follow) parts.getOrNull(0).orEmpty() else "",
                text = if (follow) "" else n.text, seq = n.seq, ts = n.ts, read = false
            )
        }
        if (fresh.isEmpty()) return
        save((fresh + _items.value).sortedByDescending { it.ts }.take(MAX))
    }

    @Synchronized fun markAllRead() { save(_items.value.map { it.copy(read = true) }) }
    @Synchronized fun markRead(id: String) { save(_items.value.map { if (it.id == id) it.copy(read = true) else it }) }
    @Synchronized fun clear() { save(emptyList()) }

    private fun save(list: List<NotifItem>) {
        _items.value = list
        _unread.value = list.count { !it.read }
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("i", it.id).put("k", it.kind).put("f", it.from).put("d", it.display).put("u", it.uid).put("t", it.text).put("s", it.seq).put("ts", it.ts).put("r", it.read))
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun load(): List<NotifItem> = try {
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(NotifItem(o.optString("i"), o.optString("k"), o.optString("f"), o.optString("d"), o.optString("u"), o.optString("t"), o.optLong("s"), o.optLong("ts"), o.optBoolean("r")))
            }
        }
    } catch (_: Throwable) { emptyList() }

    companion object {
        private const val KEY = "items"
        private const val MAX = 100
        @Volatile private var instance: NotifCenter? = null
        fun init(context: Context): NotifCenter = instance ?: synchronized(this) { instance ?: NotifCenter(context.applicationContext).also { instance = it } }
        fun getInstance(): NotifCenter = instance ?: throw IllegalStateException("NotifCenter belum diinisialisasi")
    }
}

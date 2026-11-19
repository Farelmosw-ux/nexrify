package id.nexrify.music.data.mubar

import id.nexrify.music.data.account.AccountManager
import id.nexrify.music.data.chat.ChatApi
import id.nexrify.music.data.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class MubarMember(val uid: String, val username: String, val display: String, val av: String)

data class MubarTrack(val id: String, val title: String, val artist: String, val thumb: String, val url: String, val durationSec: Int)

data class MubarRoom(
    val code: String,
    val hostUid: String,
    val turnUid: String,
    val members: List<MubarMember>,
    val track: MubarTrack?,
    /** Waktu server (ms) lagu mulai diputar. */
    val startedAt: Long,
    val turnAt: Long,
    val lockMs: Long,
    val maxMembers: Int
)

data class MubarReply(val seq: Long, val username: String, val text: String, val deleted: Boolean)

data class MubarMsg(
    val seq: Long,
    val uid: String,
    val username: String,
    val display: String,
    val av: String,
    val text: String,
    val ts: Long,
    val deleted: Boolean,
    val reply: MubarReply?,
    /** emoji → daftar uid yang bereaksi */
    val reactions: Map<String, List<String>>
)

data class MubarInvite(val code: String, val host: String, val display: String, val ts: Long)

/**
 * Musik Bareng: ruangan, giliran memilih lagu, dan chat grup. Semua keadaan datang dari server lewat polling
 * (3 detik di dalam ruangan, 12 detik di luar ruangan untuk menunggu undangan). Waktu diputar lagu dihitung
 * dari waktu server supaya semua anggota mendengar posisi yang sama.
 */
class MubarManager private constructor() {
    companion object {
        @Volatile private var instance: MubarManager? = null
        fun getInstance(): MubarManager = instance ?: synchronized(this) { instance ?: MubarManager().also { instance = it } }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _room = MutableStateFlow<MubarRoom?>(null)
    val room: StateFlow<MubarRoom?> = _room.asStateFlow()
    private val _messages = MutableStateFlow<List<MubarMsg>>(emptyList())
    val messages: StateFlow<List<MubarMsg>> = _messages.asStateFlow()
    private val _invites = MutableStateFlow<List<MubarInvite>>(emptyList())
    val invites: StateFlow<List<MubarInvite>> = _invites.asStateFlow()
    private val _sheetOpen = MutableStateFlow(false)
    val sheetOpen: StateFlow<Boolean> = _sheetOpen.asStateFlow()
    private val _ended = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Alasan ruangan berakhir: host_left, host_gone, timeout, closed. */
    val ended: SharedFlow<String> = _ended.asSharedFlow()

    @Volatile private var offsetMs = 0L
    private var ev = 0L
    private var foreground = false
    private var pollJob: Job? = null

    fun openSheet() { _sheetOpen.value = true }
    fun closeSheet() { _sheetOpen.value = false }

    /** Waktu server saat ini (perkiraan). */
    fun serverNowMs(): Long = System.currentTimeMillis() + offsetMs

    private fun token(): String? = AccountManager.getInstance().token()

    /* ---------------- polling ---------------- */

    fun setForeground(visible: Boolean) {
        foreground = visible
        if (visible) {
            if (pollJob?.isActive == true) return
            pollJob = scope.launch {
                while (true) {
                    if (token() != null) runCatching { refresh() }
                    delay(if (_room.value != null) 3_000L else 12_000L)
                }
            }
        } else {
            pollJob?.cancel()
            pollJob = null
        }
    }

    suspend fun refresh() {
        val t = token() ?: return
        val o = ChatApi.get(t, mapOf("mubar" to "1", "code" to (_room.value?.code ?: ""), "after" to ev.toString()))
        apply(o)
    }

    private suspend fun act(body: JSONObject): JSONObject {
        val t = token() ?: throw IllegalStateException("not logged in")
        val o = ChatApi.post(t, body)
        if (o.has("room") || o.has("invites")) apply(o)
        return o
    }

    private fun apply(o: JSONObject) {
        o.optLong("serverNow").takeIf { it > 0 }?.let { offsetMs = it - System.currentTimeMillis() }
        val rj = o.optJSONObject("room")
        val wasIn = _room.value != null
        if (rj == null) {
            _room.value = null
            _messages.value = emptyList()
            ev = 0
            _invites.value = parseInvites(o.optJSONArray("invites"))
            val why = o.optString("ended")
            if (wasIn || why.isNotEmpty()) {
                if (why.isNotEmpty()) _ended.tryEmit(why)
                _sheetOpen.value = false
            }
            return
        }
        val room = parseRoom(rj)
        o.optLong("serverNow").takeIf { it > 0 }?.let { offsetMs = it - System.currentTimeMillis() }
        // Ruangan lain (mis. pindah) membersihkan chat lama.
        if (_room.value?.code != room.code) { _messages.value = emptyList(); ev = 0 }
        _room.value = room
        _invites.value = emptyList()
        val incoming = parseMessages(o.optJSONArray("messages"))
        if (incoming.isNotEmpty()) {
            val map = LinkedHashMap<Long, MubarMsg>()
            _messages.value.forEach { map[it.seq] = it }
            incoming.forEach { map[it.seq] = it }
            _messages.value = map.values.sortedBy { it.seq }.takeLast(300)
        }
        ev = maxOf(ev, o.optLong("ev"))
    }

    private fun parseRoom(o: JSONObject): MubarRoom {
        val ms = o.optJSONArray("members") ?: JSONArray()
        val members = buildList {
            for (i in 0 until ms.length()) {
                val m = ms.optJSONObject(i) ?: continue
                add(MubarMember(m.optString("uid"), m.optString("username"), m.optString("display"), m.optString("av")))
            }
        }
        val tj = o.optJSONObject("track")
        val track = tj?.let { MubarTrack(it.optString("id"), it.optString("title"), it.optString("artist"), it.optString("thumb"), it.optString("url"), it.optInt("durationSec")) }
        return MubarRoom(
            code = o.optString("code"), hostUid = o.optString("hostUid"), turnUid = o.optString("turnUid"), members = members,
            track = track, startedAt = o.optLong("startedAt"), turnAt = o.optLong("turnAt"),
            lockMs = o.optLong("lockMs", 40_000L), maxMembers = o.optInt("maxMembers", 4)
        )
    }

    private fun parseMessages(arr: JSONArray?): List<MubarMsg> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) add(parseMsg(arr.optJSONObject(i) ?: continue))
        }
    }

    private fun parseMsg(o: JSONObject): MubarMsg {
        val rj = o.optJSONObject("reply")
        val rx = o.optJSONObject("reactions")
        val reactions = buildMap<String, List<String>> {
            rx?.keys()?.forEach { k ->
                val a = rx.optJSONArray(k) ?: return@forEach
                put(k, List(a.length()) { a.optString(it) })
            }
        }
        return MubarMsg(
            seq = o.optLong("seq"), uid = o.optString("uid"), username = o.optString("username"), display = o.optString("display"),
            av = o.optString("av"), text = o.optString("text"), ts = o.optLong("ts"), deleted = o.optBoolean("deleted"),
            reply = rj?.let { MubarReply(it.optLong("seq"), it.optString("username"), it.optString("text"), it.optBoolean("deleted")) },
            reactions = reactions
        )
    }

    private fun parseInvites(arr: JSONArray?): List<MubarInvite> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(MubarInvite(o.optString("code"), o.optString("host"), o.optString("display"), o.optLong("ts")))
            }
        }
    }

    /* ---------------- aksi (melempar AccountError dari server) ---------------- */

    /** Buat ruangan. Kalau [current] ada, lagu itu langsung dipasang untuk giliran pertama. */
    suspend fun create(current: Track?) {
        act(JSONObject().put("action", "mb_create"))
        if (current != null && current.durationSeconds >= 20) runCatching { pick(current) }
    }

    suspend fun invite(username: String) {
        val code = _room.value?.code ?: return
        act(JSONObject().put("action", "mb_invite").put("code", code).put("username", username))
    }

    suspend fun accept(inv: MubarInvite) {
        act(JSONObject().put("action", "mb_accept").put("code", inv.code))
        _sheetOpen.value = true
    }

    suspend fun decline(inv: MubarInvite) {
        _invites.value = _invites.value.filter { it.code != inv.code }
        act(JSONObject().put("action", "mb_decline").put("code", inv.code))
    }

    /** Keluar. Kalau kamu host, ruangan bubar untuk semua orang. */
    suspend fun leave() {
        act(JSONObject().put("action", "mb_leave"))
        _room.value = null
        _messages.value = emptyList()
        ev = 0
        _sheetOpen.value = false
    }

    suspend fun pick(track: Track) {
        val code = _room.value?.code ?: return
        val tj = JSONObject().put("id", track.id).put("title", track.title).put("artist", track.artist)
            .put("thumb", track.thumbnailUrl).put("url", track.url).put("durationSec", track.durationSeconds)
        act(JSONObject().put("action", "mb_pick").put("code", code).put("track", tj))
    }

    suspend fun send(text: String, replyTo: Long?) {
        val code = _room.value?.code ?: return
        val o = ChatApi.post(token() ?: return, JSONObject().put("action", "mb_send").put("code", code).put("text", text).apply { if (replyTo != null) put("replyTo", replyTo) })
        o.optJSONObject("message")?.let { m ->
            val msg = parseMsg(m)
            val map = LinkedHashMap<Long, MubarMsg>()
            _messages.value.forEach { map[it.seq] = it }
            map[msg.seq] = msg
            _messages.value = map.values.sortedBy { it.seq }.takeLast(300)
        }
    }

    suspend fun react(seq: Long, emoji: String) {
        val code = _room.value?.code ?: return
        act(JSONObject().put("action", "mb_react").put("code", code).put("seq", seq).put("emoji", emoji))
        refreshAfterChange(seq)
    }

    suspend fun delete(seq: Long) {
        val code = _room.value?.code ?: return
        act(JSONObject().put("action", "mb_delete").put("code", code).put("seq", seq))
        refreshAfterChange(seq)
    }

    /** Aksi chat mengubah pesan lama, jadi ambil ulang perubahan terbaru segera. */
    private suspend fun refreshAfterChange(@Suppress("UNUSED_PARAMETER") seq: Long) {
        try { refresh() } catch (e: CancellationException) { throw e } catch (_: Throwable) {}
    }
}

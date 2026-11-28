package id.nexrify.music.data.chat

import android.content.Context
import id.nexrify.music.data.account.AccountError
import id.nexrify.music.data.account.AccountManager
import id.nexrify.music.util.CrashReporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * Pesan langsung (DM) satu lawan satu. Kotak masuk dicek tiap 8 detik hanya saat layar chat atau pesan terlihat,
 * percakapan yang sedang dibuka dicek tiap 2,5 detik dan melambat saat sepi.
 */
class DmManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CrashReporter.handler)
    private val settings = ChatSettings.getInstance()

    /* ---------------- kotak masuk ---------------- */

    private val _inbox = MutableStateFlow<List<DmConversation>>(emptyList())
    val inbox: StateFlow<List<DmConversation>> = _inbox.asStateFlow()
    private val _unread = MutableStateFlow(0)
    /** Total pesan langsung yang belum dibaca (badge). */
    val unread: StateFlow<Int> = _unread.asStateFlow()
    private val _inboxLoaded = MutableStateFlow(false)
    val inboxLoaded: StateFlow<Boolean> = _inboxLoaded.asStateFlow()

    private var inboxRefs = 0
    private var inboxJob: Job? = null

    private fun token(): String? = AccountManager.getInstance().token()
    private fun myId(): String = AccountManager.getInstance().user.value?.id.orEmpty()

    @Synchronized
    fun watchInbox(on: Boolean) {
        if (on) inboxRefs++ else inboxRefs = maxOf(0, inboxRefs - 1)
        if (inboxRefs > 0 && inboxJob == null) {
            inboxJob = scope.launch {
                while (true) {
                    runCatching { refreshInbox() }
                    delay(8_000L)
                }
            }
        } else if (inboxRefs == 0) {
            inboxJob?.cancel()
            inboxJob = null
        }
    }

    suspend fun refreshInbox() {
        val t = token() ?: return
        try {
            val (unread, list) = ChatApi.dmInbox(t)
            val before = _unread.value
            _inbox.value = list
            _unread.value = list.sumOf { if (it.muted || it.archived || it.request || isChatLocked(it.uid)) 0 else it.unread }
            // Pesan baru masuk saat kotak masuk terbuka (tidak sedang di dalam percakapan): bunyikan nada pesan.
            if (_inboxLoaded.value && openUid == null && _unread.value > before) id.nexrify.music.util.NexSounds.playMessage(context)
            _inboxLoaded.value = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // diam saja: dicoba lagi pada putaran berikutnya
        }
    }

    /* ---------------- percakapan ---------------- */

    private val server = MutableStateFlow<Map<Long, DmMessage>>(emptyMap())
    private val outbox = MutableStateFlow<List<DmMessage>>(emptyList())
    private val pendingData = HashMap<String, Pair<String, Long>>()
    private val pendingSong = HashMap<String, DmSong>()
    private var localCounter = 0L

    val messages: StateFlow<List<DmMessage>> = combine(server, outbox) { s, p ->
        s.values.sortedBy { it.seq } + p
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _peer = MutableStateFlow<DmPeer?>(null)
    val peer: StateFlow<DmPeer?> = _peer.asStateFlow()
    private val _typing = MutableStateFlow(false)
    val typing: StateFlow<Boolean> = _typing.asStateFlow()
    private val _peerRead = MutableStateFlow(0L)
    val peerRead: StateFlow<Long> = _peerRead.asStateFlow()
    private val _blocked = MutableStateFlow(false)
    val blocked: StateFlow<Boolean> = _blocked.asStateFlow()
    private val _stars = MutableStateFlow<Set<Long>>(emptySet())
    /** Nomor pesan yang kuberi bintang di percakapan terbuka. */
    val stars: StateFlow<Set<Long>> = _stars.asStateFlow()
    private val _disappear = MutableStateFlow(0L)
    /** Detik sebelum pesan baru hilang sendiri (0 = mati). */
    val disappear: StateFlow<Long> = _disappear.asStateFlow()
    private val _translated = MutableStateFlow<Map<Long, String>>(emptyMap())
    /** Terjemahan pesan di percakapan terbuka (nomor pesan -> teks). Hanya di memori. */
    val translated: StateFlow<Map<Long, String>> = _translated.asStateFlow()
    private val _request = MutableStateFlow(false)
    /** Percakapan terbuka adalah permintaan pesan dari orang yang belum jadi temanku. */
    val request: StateFlow<Boolean> = _request.asStateFlow()
    private val _pending = MutableStateFlow(false)
    /** Pesanku masih menunggu diterima lawan bicara. */
    val pending: StateFlow<Boolean> = _pending.asStateFlow()
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()
    private val _offline = MutableStateFlow(false)
    val offline: StateFlow<Boolean> = _offline.asStateFlow()
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    @Volatile private var openUid: String? = null
    private var threadJob: Job? = null
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var lastTypingSent = 0L
    private var lastReadMarked = 0L

    fun openUsername(): String? = _peer.value?.takeIf { openUid != null }?.username

    suspend fun peerFromUsername(username: String): DmPeer? {
        val p = ChatManager.getInstance().profile(username) ?: return null
        return DmPeer(p.uid, p.username, p.display, p.av)
    }

    fun openThread(peer: DmPeer) {
        if (openUid == peer.uid && threadJob?.isActive == true) return
        threadJob?.cancel()
        server.value = emptyMap()
        outbox.value = emptyList()
        pendingData.clear()
        pendingSong.clear()
        _typing.value = false
        _peerRead.value = 0L
        _stars.value = emptySet()
        _disappear.value = 0L
        _request.value = false
        _pending.value = false
        _translated.value = emptyMap()
        _blocked.value = false
        _loaded.value = false
        _offline.value = false
        _peer.value = peer
        openUid = peer.uid
        threadJob = scope.launch { threadLoop(peer.uid) }
    }

    fun closeThread() {
        threadJob?.cancel()
        threadJob = null
        openUid = null
        _typing.value = false
    }

    private fun kick() { wake.trySend(Unit) }

    private suspend fun threadLoop(uid: String) {
        var ev = 0L
        var idle = 0
        var failures = 0
        var first = true
        var maxPeerSeq = 0L
        while (openUid == uid) {
            val t = token() ?: break
            var changed = false
            try {
                val data = ChatApi.dmThread(t, uid, ev)
                _peer.value = data.peer
                var incoming = false
                if (data.messages.isNotEmpty()) {
                    val me = myId()
                    server.update { old -> old + data.messages.associateBy { it.seq } }
                    incoming = data.messages.any { it.uid != me && !it.deleted }
                    // Nada pesan hanya untuk pesan lawan bicara yang benar-benar baru (bukan riwayat awal, ubahan, atau reaksi).
                    val newest = data.messages.filter { it.uid != me && !it.deleted && it.call == null }.maxOfOrNull { it.seq } ?: 0L
                    if (!first && newest > maxPeerSeq) id.nexrify.music.util.NexSounds.playMessage(context)
                    if (newest > maxPeerSeq) maxPeerSeq = newest
                    changed = true
                }
                ev = maxOf(ev, data.ev)
                if (data.typing != _typing.value) changed = true
                _typing.value = data.typing
                _peerRead.value = data.peerRead
                _blocked.value = data.blocked
                _stars.value = data.stars
                _disappear.value = data.disappear
                _request.value = data.request
                _pending.value = data.pending
                _loaded.value = true
                _offline.value = false
                failures = 0
                if (incoming || first) markRead()
                first = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: AccountError) {
                failures++
                _offline.value = true
                if (e.code == "auth") break
            } catch (e: Throwable) {
                failures++
                _offline.value = true
                CrashReporter.logNonFatal(e)
            }
            idle = if (changed) 0 else idle + 1
            val wait = when {
                failures > 0 -> minOf(30_000L, 3_000L * failures)
                idle < 6 -> 2_500L
                idle < 20 -> 5_000L
                else -> 8_000L
            }
            if (withTimeoutOrNull(wait) { wake.receive() } != null) idle = 0
        }
    }

    /** Tandai percakapan terbuka sudah dibaca (dibatasi tiap 2 detik) dan kosongkan badge-nya. */
    fun markRead() {
        val uid = openUid ?: return
        val now = System.currentTimeMillis()
        if (now - lastReadMarked < 2_000L) return
        lastReadMarked = now
        runCatching { DmNotifier.cancel(context, uid) }
        _inbox.update { list -> list.map { if (it.uid == uid) it.copy(unread = 0) else it } }
        _unread.value = _inbox.value.sumOf { if (it.muted || it.archived || it.request || isChatLocked(it.uid)) 0 else it.unread }
        scope.launch { runCatching { token()?.let { ChatApi.post(it, JSONObject().put("action", "dm_read").put("to", uid)) } } }
    }

    fun send(text: String, replyTo: DmMessage?) {
        val clean = text.trim()
        val uid = openUid ?: return
        if (clean.isEmpty()) return
        localCounter++
        val localId = "D$localCounter"
        val msg = DmMessage(
            seq = -localCounter, uid = myId(), text = ChatFilter.censor(clean.take(CHAT_MAX_LENGTH)), ts = System.currentTimeMillis(),
            edited = 0, deleted = false, ev = 0, reply = replyTo?.let { DmReply(it.seq, it.uid, it.text.take(100)) },
            status = SendStatus.SENDING, localId = localId
        )
        pendingData[localId] = clean to (replyTo?.seq ?: 0L)
        outbox.update { it + msg }
        scope.launch { deliver(localId, uid) }
    }

    /** Kirim kartu lagu ke lawan bicara yang sedang dibuka. */
    fun sendSong(song: DmSong) {
        val uid = openUid ?: return
        localCounter++
        val localId = "D$localCounter"
        val msg = DmMessage(
            seq = -localCounter, uid = myId(), text = "", ts = System.currentTimeMillis(),
            edited = 0, deleted = false, ev = 0, reply = null,
            status = SendStatus.SENDING, localId = localId, song = song
        )
        pendingData[localId] = "" to 0L
        pendingSong[localId] = song
        outbox.update { it + msg }
        scope.launch { deliver(localId, uid) }
    }

    fun retry(localId: String) {
        val uid = openUid ?: return
        outbox.update { list -> list.map { if (it.localId == localId) it.copy(status = SendStatus.SENDING) else it } }
        scope.launch { deliver(localId, uid) }
    }

    fun discardPending(localId: String) {
        pendingData.remove(localId)
        pendingSong.remove(localId)
        outbox.update { list -> list.filterNot { it.localId == localId } }
    }

    private suspend fun deliver(localId: String, uid: String) {
        val (text, replySeq) = pendingData[localId] ?: return
        val t = token() ?: return
        try {
            val body = JSONObject().put("action", "dm_send").put("to", uid).put("text", text)
            if (replySeq > 0) body.put("replyTo", replySeq)
            pendingSong[localId]?.let { sg ->
                body.put("song", JSONObject().put("id", sg.id).put("title", sg.title).put("artist", sg.artist)
                    .put("thumb", sg.thumb).put("url", sg.url).put("dur", sg.dur))
            }
            val r = ChatApi.post(t, body)
            val m = ChatApi.parseDm(r.getJSONObject("message"))
            pendingData.remove(localId)
            pendingSong.remove(localId)
            server.update { it + (m.seq to m) }
            outbox.update { list -> list.filterNot { it.localId == localId } }
            kick()
            scope.launch { refreshInbox() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AccountError) {
            val permanent = e.code in setOf("dm_blocked", "dm_you_blocked", "chat_banned", "rate_limited", "empty", "not_found", "invalid")
            if (permanent) { discardPending(localId); _errors.tryEmit(e.code) } else markFailed(localId)
        } catch (e: Throwable) {
            markFailed(localId)
        }
    }

    private fun markFailed(localId: String) {
        outbox.update { list -> list.map { if (it.localId == localId) it.copy(status = SendStatus.FAILED) else it } }
    }

    suspend fun edit(seq: Long, text: String): Boolean {
        val uid = openUid ?: return false
        val t = token() ?: return false
        return try {
            val r = ChatApi.post(t, JSONObject().put("action", "dm_edit").put("to", uid).put("seq", seq).put("text", text.trim()))
            val m = ChatApi.parseDm(r.getJSONObject("message"))
            server.update { it + (m.seq to m) }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: AccountError) {
            _errors.tryEmit(e.code); false
        } catch (e: Throwable) {
            _errors.tryEmit("network"); false
        }
    }

    suspend fun delete(seq: Long): Boolean {
        val uid = openUid ?: return false
        val t = token() ?: return false
        return try {
            ChatApi.post(t, JSONObject().put("action", "dm_delete").put("to", uid).put("seq", seq))
            server.update { map ->
                val m = map[seq] ?: return@update map
                map + (seq to m.copy(deleted = true, text = ""))
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _errors.tryEmit("network"); false
        }
    }

    fun notifyTyping() {
        val uid = openUid ?: return
        val now = System.currentTimeMillis()
        if (now - lastTypingSent < 3_000L) return
        lastTypingSent = now
        scope.launch { runCatching { token()?.let { ChatApi.post(it, JSONObject().put("action", "dm_typing").put("to", uid)) } } }
    }

    suspend fun setBlock(uid: String, on: Boolean): Boolean {
        val t = token() ?: return false
        return try {
            ChatApi.post(t, JSONObject().put("action", "dm_block").put("to", uid).put("on", on))
            if (openUid == uid) _blocked.value = on
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            false
        }
    }

    /** Reaksi emoji pada pesan. Emoji yang sama ditekan lagi = batal. */
    suspend fun react(seq: Long, emoji: String): Boolean {
        val uid = openUid ?: return false
        val t = token() ?: return false
        return try {
            val r = ChatApi.post(t, JSONObject().put("action", "dm_react").put("to", uid).put("seq", seq).put("emoji", emoji))
            val m = ChatApi.parseDm(r.getJSONObject("message"))
            server.update { it + (m.seq to m) }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: AccountError) {
            _errors.tryEmit(e.code); false
        } catch (e: Throwable) {
            _errors.tryEmit("network"); false
        }
    }

    /** Aksi kotak masuk: dm_pin, dm_mute (pakai on), dm_unread, dm_hide. Mengembalikan kode galat atau null kalau berhasil. */
    suspend fun inboxAction(action: String, uid: String, on: Boolean = true): String? {
        val t = token() ?: return "auth"
        return try {
            ChatApi.post(t, JSONObject().put("action", action).put("to", uid).put("on", on))
            refreshInbox()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: AccountError) {
            e.code
        } catch (e: Throwable) {
            "network"
        }
    }

    /** Kirim pesan suara ke lawan bicara yang sedang dibuka. Mengembalikan kode galat atau null kalau berhasil. */
    suspend fun sendVoice(clip: id.nexrify.music.util.VoiceClip, replyTo: DmMessage?): String? {
        val uid = openUid ?: return "network"
        val t = token() ?: return "auth"
        return try {
            val b64 = kotlinx.coroutines.withContext(Dispatchers.IO) { android.util.Base64.encodeToString(clip.file.readBytes(), android.util.Base64.NO_WRAP) }
            val body = JSONObject().put("action", "dm_send_voice").put("to", uid).put("audio", b64).put("durMs", clip.durMs).put("wave", clip.wave)
            if (replyTo != null && replyTo.seq > 0) body.put("replyTo", replyTo.seq)
            val r = ChatApi.post(t, body)
            val m = ChatApi.parseDm(r.getJSONObject("message"))
            server.update { it + (m.seq to m) }
            runCatching { clip.file.delete() }
            wake.trySend(Unit)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: AccountError) {
            e.code
        } catch (e: Throwable) {
            "network"
        }
    }

    private val imgCache = object : android.util.LruCache<String, android.graphics.Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: android.graphics.Bitmap): Int = value.byteCount
    }

    /** Foto di percakapan yang sedang dibuka. Disimpan di memori dan cache disk; null kalau kedaluwarsa atau gagal. */
    suspend fun loadImage(id: String): android.graphics.Bitmap? {
        imgCache.get(id)?.let { return it }
        val uid = openUid ?: return null
        return try {
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                val file = java.io.File(java.io.File(context.cacheDir, "dmimg"), "$id.jpg")
                if (!file.exists() || file.length() < 400L) {
                    val t = token() ?: return@withContext null
                    ChatApi.downloadDmImage(t, id, uid, file)
                }
                val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                if (bmp != null) imgCache.put(id, bmp)
                bmp
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            null
        }
    }

    /** Kirim foto (JPEG hasil kompres) dengan keterangan opsional. Mengembalikan kode galat atau null kalau berhasil. */
    suspend fun sendImage(jpeg: ByteArray, w: Int, h: Int, caption: String, replyTo: DmMessage?, once: Boolean = false): String? {
        val uid = openUid ?: return "network"
        val t = token() ?: return "auth"
        return try {
            val b64 = kotlinx.coroutines.withContext(Dispatchers.IO) { android.util.Base64.encodeToString(jpeg, android.util.Base64.NO_WRAP) }
            val body = JSONObject().put("action", "dm_send_image").put("to", uid).put("image", b64).put("w", w).put("h", h).put("text", caption).put("once", once)
            if (replyTo != null && replyTo.seq > 0) body.put("replyTo", replyTo.seq)
            val r = ChatApi.post(t, body)
            val m = ChatApi.parseDm(r.getJSONObject("message"))
            server.update { it + (m.seq to m) }
            wake.trySend(Unit)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: AccountError) {
            e.code
        } catch (e: Throwable) {
            "network"
        }
    }

    /** Terjemahkan satu pesan ke "id" atau "en". Mengembalikan kode galat atau null kalau berhasil. */
    suspend fun translate(seq: Long, lang: String): String? {
        val uid = openUid ?: return "network"
        val t = token() ?: return "auth"
        return try {
            val r = ChatApi.post(t, JSONObject().put("action", "dm_translate").put("to", uid).put("seq", seq).put("lang", lang))
            _translated.update { it + (seq to r.optString("text")) }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: AccountError) {
            e.code
        } catch (e: Throwable) {
            "network"
        }
    }

    fun hideTranslation(seq: Long) { _translated.update { it - seq } }

    /* ---------------- kunci PIN layar pesan (hanya di perangkat ini) ---------------- */

    @Volatile private var lastActive = 0L

    fun lockEnabled(): Boolean = localPrefs.contains("pin_hash")

    private fun hashPin(salt: String, pin: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray()).joinToString("") { "%02x".format(it) }

    fun setPin(pin: String) {
        val salt = java.util.UUID.randomUUID().toString()
        localPrefs.edit().putString("pin_salt", salt).putString("pin_hash", hashPin(salt, pin)).apply()
        lastActive = System.currentTimeMillis()
    }

    fun checkPin(pin: String): Boolean {
        val salt = localPrefs.getString("pin_salt", "").orEmpty()
        return hashPin(salt, pin) == localPrefs.getString("pin_hash", "")
    }

    fun removePin() {
        localPrefs.edit().remove("pin_hash").remove("pin_salt").remove("locked_chats").apply()
        _localVersion.update { it + 1 }
    }

    /* ---------------- kunci per percakapan (folder "Chat terkunci", butuh PIN) ---------------- */

    private fun lockedEntries(): Set<String> = localPrefs.getStringSet("locked_chats", emptySet()).orEmpty()

    fun isChatLocked(uid: String): Boolean = lockedEntries().any { it.substringBefore('|') == uid }

    /** Uid percakapan terkunci milik [username], atau null. Dipakai saat percakapan dibuka lewat notifikasi/profil sebelum kotak masuk termuat. */
    fun lockedUidForUsername(username: String): String? =
        if (!lockEnabled()) null
        else lockedEntries().firstOrNull { it.substringAfter('|', "").equals(username, true) }?.substringBefore('|')

    fun setChatLocked(uid: String, username: String, on: Boolean) {
        val set = lockedEntries().filterNot { it.substringBefore('|') == uid }.toMutableSet()
        if (on) set.add("$uid|$username")
        localPrefs.edit().putStringSet("locked_chats", set).apply()
        _unread.value = _inbox.value.sumOf { if (it.muted || it.archived || it.request || isChatLocked(it.uid)) 0 else it.unread }
        _localVersion.update { it + 1 }
    }

    private val chatUnlockedAt = HashMap<String, Long>()

    /** Percakapan terkunci yang baru saja dibuka PIN-nya tidak meminta PIN lagi selama 2 menit. */
    fun chatUnlocked(uid: String): Boolean =
        System.currentTimeMillis() - (synchronized(chatUnlockedAt) { chatUnlockedAt[uid] } ?: 0L) < 120_000L

    fun markChatUnlocked(uid: String) { synchronized(chatUnlockedAt) { chatUnlockedAt[uid] = System.currentTimeMillis() } }

    /** Tandai layar pesan sedang dipakai; dipanggil berkala selama terbuka. */
    fun touchLock() { lastActive = System.currentTimeMillis() }

    /** Perlu PIN kalau kunci aktif dan layar pesan sudah lebih dari 1 menit tidak dipakai. */
    fun shouldLock(): Boolean = lockEnabled() && System.currentTimeMillis() - lastActive > 60_000L

    /**
     * Buka foto sekali lihat: server menandai terbuka dan hanya mengizinkan satu kali unduhan. Mengembalikan id foto,
     * atau null (galat dikirim ke [errors]; "viewed" berarti sudah pernah dibuka).
     */
    suspend fun openOnce(seq: Long): String? {
        val uid = openUid ?: return null
        val t = token() ?: return null
        return try {
            ChatApi.post(t, JSONObject().put("action", "dm_open").put("to", uid).put("seq", seq)).optString("id").ifBlank { null }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AccountError) {
            _errors.tryEmit(e.code); null
        } catch (e: Throwable) {
            _errors.tryEmit("network"); null
        }
    }

    /** Unduh foto sekali lihat langsung ke memori: tidak disimpan di cache disk maupun memori. */
    suspend fun loadOnceImage(id: String): android.graphics.Bitmap? {
        val uid = openUid ?: return null
        val t = token() ?: return null
        return try {
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                val file = java.io.File(context.cacheDir, "dmonce_$id.jpg")
                try {
                    ChatApi.downloadDmImage(t, id, uid, file)
                    android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                } finally {
                    runCatching { file.delete() }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            null
        }
    }

    private val previewCache = HashMap<String, LinkPreview?>()

    /** Pratinjau tautan (di-cache di memori selama proses hidup). */
    suspend fun linkPreview(url: String): LinkPreview? {
        synchronized(previewCache) { if (previewCache.containsKey(url)) return previewCache[url] }
        val t = token() ?: return null
        val p = try {
            ChatApi.linkPreview(t, url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return null
        }
        synchronized(previewCache) { previewCache[url] = p }
        return p
    }

    /** Mood dan lagu yang sedang didengar [uid]; null kalau gagal. Hanya terisi untuk teman. */
    suspend fun peerExtra(uid: String): PeerExtra? {
        val t = token() ?: return null
        return try { ChatApi.peerExtra(t, uid) } catch (e: CancellationException) { throw e } catch (e: Throwable) { null }
    }

    fun myMoodEmoji(): String = localPrefs.getString("my_mood_e", "").orEmpty()
    fun myMoodText(): String = localPrefs.getString("my_mood_t", "").orEmpty()

    /** Atur status mood sendiri (kosong = hapus). Hilang sendiri setelah 24 jam di server. Mengembalikan kode galat atau null. */
    suspend fun setMood(emoji: String, text: String): String? {
        val t = token() ?: return "auth"
        return try {
            ChatApi.post(t, JSONObject().put("action", "mood_set").put("emoji", emoji).put("text", text))
            localPrefs.edit().putString("my_mood_e", emoji).putString("my_mood_t", text).apply()
            _localVersion.update { it + 1 }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: AccountError) {
            e.code
        } catch (e: Throwable) {
            "network"
        }
    }

    /** Bagikan lagu yang sedang diputar ke teman? Bawaan: mati (privasi). */
    fun shareNowPlaying(): Boolean = localPrefs.getBoolean("share_np", false)

    fun setShareNowPlaying(on: Boolean) {
        localPrefs.edit().putBoolean("share_np", on).apply()
        _localVersion.update { it + 1 }
    }

    private var npPublished = false

    /** Umumkan lagu yang sedang diputar ([track] null = hapus). Server menyimpannya 5,5 menit, jadi panggil ulang tiap 4 menit. */
    suspend fun publishNowPlaying(track: id.nexrify.music.data.model.Track?) {
        if (track == null && !npPublished) return
        val t = token() ?: return
        try {
            val body = JSONObject().put("action", "np_set")
            if (track != null) body.put("track", JSONObject().put("title", track.title).put("artist", track.artist))
            ChatApi.post(t, body)
            npPublished = track != null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // dicoba lagi pada pembaruan berikutnya
        }
    }

    /** Kirim teks dan/atau kartu lagu ke percakapan mana pun (dipakai tombol "Kirim ke teman"). Mengembalikan kode galat atau null. */
    suspend fun sendTo(toUid: String, text: String, song: DmSong? = null): String? {
        val t = token() ?: return "auth"
        return try {
            val body = JSONObject().put("action", "dm_send").put("to", toUid).put("text", text)
            song?.let { sg ->
                body.put("song", JSONObject().put("id", sg.id).put("title", sg.title).put("artist", sg.artist)
                    .put("thumb", sg.thumb).put("url", sg.url).put("dur", sg.dur))
            }
            ChatApi.post(t, body)
            refreshInbox()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: AccountError) {
            e.code
        } catch (e: Throwable) {
            "network"
        }
    }

    /** Kirim pesan teks sekali lihat. Isinya tidak pernah muncul di daftar pesan, hanya lewat [openOnceText]. Kode galat atau null. */
    suspend fun sendOnceText(text: String): String? {
        val uid = openUid ?: return "network"
        val t = token() ?: return "auth"
        return try {
            val r = ChatApi.post(t, JSONObject().put("action", "dm_send").put("to", uid).put("text", text).put("once", true))
            val m = ChatApi.parseDm(r.getJSONObject("message"))
            server.update { it + (m.seq to m) }
            wake.trySend(Unit)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: AccountError) {
            e.code
        } catch (e: Throwable) {
            "network"
        }
    }

    /** Buka pesan teks sekali lihat: mengembalikan isinya satu kali saja, atau null (galat dikirim ke [errors]). */
    suspend fun openOnceText(seq: Long): String? {
        val uid = openUid ?: return null
        val t = token() ?: return null
        return try {
            ChatApi.post(t, JSONObject().put("action", "dm_open").put("to", uid).put("seq", seq)).optString("text").ifBlank { null }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AccountError) {
            _errors.tryEmit(e.code); null
        } catch (e: Throwable) {
            _errors.tryEmit("network"); null
        }
    }

    /** Terima permintaan pesan. Kode galat, atau null kalau berhasil. */
    suspend fun acceptRequest(uid: String): String? {
        val err = inboxAction("dm_accept", uid)
        if (err == null && openUid == uid) _request.value = false
        return err
    }

    /** Tolak permintaan pesan: hilang dari kotak masuk dan pengirim tidak bisa mengirim lagi selama 7 hari. */
    suspend fun declineRequest(uid: String): String? = inboxAction("dm_decline", uid)

    /* ---------------- pengaturan lokal (hanya di perangkat ini) ---------------- */

    private val localPrefs = context.getSharedPreferences("dm_local", Context.MODE_PRIVATE)
    private val _localVersion = MutableStateFlow(0)
    /** Naik tiap nama panggilan atau tema berubah, supaya layar ikut tersusun ulang. */
    val localVersion: StateFlow<Int> = _localVersion.asStateFlow()

    fun nickname(uid: String): String = localPrefs.getString("nick_$uid", "").orEmpty()

    fun setNickname(uid: String, name: String) {
        val e = localPrefs.edit()
        if (name.isBlank()) e.remove("nick_$uid") else e.putString("nick_$uid", name.trim().take(24))
        e.apply()
        _localVersion.update { it + 1 }
    }

    /** Indeks warna gelembungku di percakapan ini; 0 = bawaan. */
    fun theme(uid: String): Int = localPrefs.getInt("theme_$uid", 0)

    fun setTheme(uid: String, index: Int) {
        localPrefs.edit().putInt("theme_$uid", index).apply()
        _localVersion.update { it + 1 }
    }

    /** Bintangi atau lepas bintang pesan (hanya terlihat olehku). */
    suspend fun star(seq: Long, on: Boolean): Boolean {
        val uid = openUid ?: return false
        val t = token() ?: return false
        return try {
            ChatApi.post(t, JSONObject().put("action", "dm_star").put("to", uid).put("seq", seq).put("on", on))
            _stars.update { if (on) it + seq else it - seq }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: AccountError) {
            _errors.tryEmit(e.code); false
        } catch (e: Throwable) {
            _errors.tryEmit("network"); false
        }
    }

    suspend fun starred(): List<DmMessage> {
        val uid = openUid ?: return emptyList()
        val t = token() ?: return emptyList()
        return try { ChatApi.dmStars(t, uid) } catch (e: CancellationException) { throw e } catch (e: Throwable) { emptyList() }
    }

    /** Atur pesan hilang sendiri: 0, 86400 (24 jam), atau 604800 (7 hari). Berlaku untuk pesan baru. */
    suspend fun setDisappear(secs: Long): Boolean {
        val uid = openUid ?: return false
        val t = token() ?: return false
        return try {
            ChatApi.post(t, JSONObject().put("action", "dm_disappear").put("to", uid).put("secs", secs))
            _disappear.value = secs
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: AccountError) {
            _errors.tryEmit(e.code); false
        } catch (e: Throwable) {
            _errors.tryEmit("network"); false
        }
    }

    /** Teruskan pesan (teks atau kartu lagu) ke percakapan lain. Mengembalikan kode galat atau null kalau berhasil. */
    suspend fun forward(toUid: String, msg: DmMessage): String? {
        val t = token() ?: return "auth"
        return try {
            val body = JSONObject().put("action", "dm_send").put("to", toUid).put("text", msg.text).put("fwd", true)
            msg.song?.let { sg ->
                body.put("song", JSONObject().put("id", sg.id).put("title", sg.title).put("artist", sg.artist)
                    .put("thumb", sg.thumb).put("url", sg.url).put("dur", sg.dur))
            }
            ChatApi.post(t, body)
            refreshInbox()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: AccountError) {
            e.code
        } catch (e: Throwable) {
            "network"
        }
    }

    /** Cari kata di percakapan yang sedang dibuka. */
    suspend fun search(q: String): List<DmMessage> {
        val uid = openUid ?: return emptyList()
        val t = token() ?: return emptyList()
        return try {
            ChatApi.dmSearch(t, uid, q)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            emptyList()
        }
    }

    companion object {
        @Volatile private var instance: DmManager? = null
        fun init(context: Context): DmManager =
            instance ?: synchronized(this) { instance ?: DmManager(context.applicationContext).also { instance = it } }
        fun getInstance(): DmManager = instance ?: throw IllegalStateException("DmManager belum diinisialisasi")
    }
}

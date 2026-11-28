package id.nexrify.music.data.call

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.content.ContextCompat
import id.nexrify.music.data.account.AccountError
import id.nexrify.music.data.account.AccountManager
import id.nexrify.music.data.chat.ChatApi
import id.nexrify.music.capsule.AppVisibility
import id.nexrify.music.push.CallNotifier
import id.nexrify.music.service.CallService
import id.nexrify.music.util.NexSounds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.PeerConnection

enum class CallPhase { IDLE, OUTGOING, INCOMING, CONNECTING, ACTIVE }

data class CallPeer(val uid: String, val username: String, val display: String, val av: String)

/** Peserta panggilan untuk ditampilkan. [state]: calling (memanggil), ringing (berdering), busy, connecting, connected. */
data class CallMember(val peer: CallPeer, val state: String)

/** Permintaan dari notifikasi: tombol Jawab ditekan. Layar panggilan menjawab begitu panggilan masuk sudah terbaca. */
object CallNav {
    val answerRequested = MutableStateFlow(false)
}

/**
 * Panggilan suara. Semua logika berjalan di thread utama (Dispatchers.Main.immediate); panggilan jaringan
 * pindah thread sendiri lewat ChatApi, dan callback WebRTC dipindahkan balik ke sini lewat [scope].
 *
 * Satu "sambungan" (Leg) = satu pasang pengguna dengan satu PeerConnection. Panggilan biasa hanya punya satu sambungan.
 * Panggilan grup = jaringan mesh: setiap peserta punya sambungan ke setiap peserta lain. Peserta yang baru diundang
 * (yang menerima undangan) yang menelepon otomatis semua peserta lama; peserta lama menjawab otomatis.
 */
class CallManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _phase = MutableStateFlow(CallPhase.IDLE)
    val phase: StateFlow<CallPhase> = _phase.asStateFlow()
    private val _peer = MutableStateFlow<CallPeer?>(null)
    val peer: StateFlow<CallPeer?> = _peer.asStateFlow()
    private val _members = MutableStateFlow<List<CallMember>>(emptyList())
    /** Semua peserta selain kita beserta statusnya masing-masing. */
    val members: StateFlow<List<CallMember>> = _members.asStateFlow()
    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()
    private val _speaker = MutableStateFlow(false)
    val speaker: StateFlow<Boolean> = _speaker.asStateFlow()
    private val _seconds = MutableStateFlow(0)
    val seconds: StateFlow<Int> = _seconds.asStateFlow()
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Kode galat/keterangan untuk ditampilkan UI: call_busy, call_declined, call_unanswered, call_failed, call_noaudio, network, dst. */
    val errors: SharedFlow<String> = _errors.asSharedFlow()
    /** Rincian teknis galat terakhir (untuk diagnosis), mis. "network: Unable to resolve host". */
    @Volatile var lastDetail: String = ""

    private inner class Leg(val peer: CallPeer, val isCaller: Boolean, var id: String?, val offer: String?, val auto: Boolean) {
        var rtc: CallRtc? = null
        var job: Job? = null
        var st: String = "connecting"
        var connected = false
        var dead = false
        var grp: String? = null
        val iceOut = ArrayList<String>()
    }

    private val legs = ArrayList<Leg>()
    private var primary: Leg? = null
    private var groupId: String? = null
    private var iAmJoiner = false
    private var knownMembers: List<CallPeer> = emptyList()
    private var ringWatch: Job? = null
    private var timerJob: Job? = null
    private var fgJob: Job? = null
    private var autoJob: Job? = null
    private var audioOn = false
    private var focusReq: AudioFocusRequest? = null

    private fun token(): String? = AccountManager.getInstance().token()
    private fun myId(): String? = AccountManager.getInstance().user.value?.id

    private fun publish() {
        val list = ArrayList<CallMember>()
        legs.forEach { list.add(CallMember(it.peer, it.st)) }
        val me = myId()
        knownMembers.forEach { m -> if (m.uid != me && legs.none { it.peer.uid == m.uid }) list.add(CallMember(m, "connecting")) }
        _members.value = list
    }

    /* ------------------------------ memulai & menjawab ------------------------------ */

    /** Telepon [p]. Izin mikrofon harus sudah diberikan sebelum memanggil ini. */
    fun startCall(p: CallPeer) {
        if (_phase.value != CallPhase.IDLE) return
        val t = token()
        if (t == null) { _errors.tryEmit("auth"); return }
        reset()
        val leg = Leg(p, true, null, null, false).also { it.st = "calling" }
        primary = leg
        legs.add(leg)
        _peer.value = p
        _phase.value = CallPhase.OUTGOING
        publish()
        leg.job = scope.launch { runOutgoing(t, leg, null) }
    }

    /** Tambah peserta ke panggilan yang sedang berlangsung (jadi panggilan grup). */
    fun addParticipant(p: CallPeer) {
        if (_phase.value != CallPhase.ACTIVE) { _errors.tryEmit("call_add_wait"); return }
        val t = token() ?: return
        if (legs.any { it.peer.uid == p.uid }) return
        if (legs.size >= 5) { _errors.tryEmit("call_full"); return }
        val base = groupId ?: legs.firstOrNull { it.connected }?.id ?: return
        val leg = Leg(p, true, null, null, false).also { it.st = "calling" }
        legs.add(leg)
        publish()
        leg.job = scope.launch { runOutgoing(t, leg, base) }
    }

    private suspend fun runOutgoing(t: String, leg: Leg, grp: String?) {
        try {
            val r = newRtc(leg, loadIce(t))
            beginAudio()
            val offer = r.createOffer()
            val body = JSONObject().put("action", "call_start").put("to", leg.peer.uid).put("offer", offer)
            if (grp != null) body.put("grp", grp)
            // Tanpa NonCancellable, pembatalan saat permintaan berjalan bisa meninggalkan panggilan "hantu" di sisi server.
            val resp = withContext(NonCancellable) { ChatApi.post(t, body) }
            val id = resp.optString("id")
            resp.optString("grp").takeIf { it.isNotBlank() }?.let { groupId = it }
            if (leg.dead || !legs.contains(leg)) {
                runCatching { ChatApi.post(t, JSONObject().put("action", "call_end").put("id", id)) }
                return
            }
            leg.id = id
            if (!leg.auto) leg.st = if (resp.optBoolean("peerOnline")) "ringing" else "calling"
            if (leg === primary && _phase.value == CallPhase.OUTGOING) NexSounds.startRingtone(context, incoming = false)
            publish()
            pollLeg(t, leg)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AccountError) {
            lastDetail = "${e.code}: ${e.message}"
            legFailed(leg, e.code)
        } catch (e: Throwable) {
            lastDetail = "${e.javaClass.simpleName}: ${e.message}"
            legFailed(leg, "call_failed")
        }
    }

    /** Jawab panggilan masuk. Izin mikrofon harus sudah diberikan. */
    fun accept() {
        if (_phase.value != CallPhase.INCOMING) return
        val t = token() ?: return
        val leg = primary ?: return
        if (leg.id == null || leg.offer == null) return
        ringWatch?.cancel(); ringWatch = null
        stopRinging()
        CallNotifier.cancel(context)
        CallNav.answerRequested.value = false
        iAmJoiner = leg.grp != null
        groupId = leg.grp
        leg.st = "connecting"
        _phase.value = CallPhase.CONNECTING
        publish()
        leg.job = scope.launch { runIncomingLeg(t, leg) }
    }

    private suspend fun runIncomingLeg(t: String, leg: Leg) {
        try {
            val r = newRtc(leg, loadIce(t))
            beginAudio()
            val answer = r.acceptOffer(leg.offer.orEmpty())
            ChatApi.post(t, JSONObject().put("action", "call_answer").put("id", leg.id).put("answer", answer))
            pollLeg(t, leg)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AccountError) {
            lastDetail = "${e.code}: ${e.message}"
            legFailed(leg, e.code)
        } catch (e: Throwable) {
            lastDetail = "${e.javaClass.simpleName}: ${e.message}"
            legFailed(leg, "call_failed")
        }
    }

    /** Tolak panggilan masuk, batalkan panggilan keluar, atau akhiri panggilan yang berlangsung (semua sambungan). */
    fun hangup() {
        if (_phase.value == CallPhase.IDLE) return
        val ids = legs.mapNotNull { it.id }
        val t = token()
        teardown()
        if (t != null) ids.forEach { id ->
            scope.launch { runCatching { ChatApi.post(t, JSONObject().put("action", "call_end").put("id", id)) } }
        }
    }

    fun decline() = hangup()

    fun toggleMute() {
        val m = !_muted.value
        _muted.value = m
        legs.forEach { it.rtc?.setMuted(m) }
    }

    fun toggleSpeaker() {
        val s = !_speaker.value
        _speaker.value = s
        applySpeaker(s)
    }

    /* ------------------------------ panggilan masuk ------------------------------ */

    /** Aplikasi di depan: cek panggilan masuk tiap 5 detik. Di latar belakang, push FCM yang memberi tahu. */
    fun setForeground(on: Boolean) {
        fgJob?.cancel(); fgJob = null
        if (!on) return
        fgJob = scope.launch {
            while (true) {
                if (AccountManager.getInstance().isLoggedIn()) checkIncoming()
                delay(5000)
            }
        }
    }

    /** [fromPush]: notifikasi sudah ditampilkan dari data push, jadi kalau ternyata tidak ada panggilan (sudah dibatalkan) cabut lagi. */
    fun checkIncomingNow(fromPush: Boolean = false) {
        scope.launch { checkIncoming(fromPush) }
    }

    /** Tombol Tolak di notifikasi: tolak lewat id panggilan; kalau id belum diketahui, cari panggilan masuk dulu. */
    suspend fun declineFromNotification(callIdArg: String) {
        if (_phase.value == CallPhase.INCOMING) { hangup(); return }
        val t = token() ?: return
        var id = callIdArg
        if (id.isBlank()) {
            id = try {
                ChatApi.get(t, mapOf("callin" to "1")).optJSONObject("call")?.optString("id").orEmpty()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                ""
            }
        }
        if (id.isNotBlank()) runCatching { ChatApi.post(t, JSONObject().put("action", "call_end").put("id", id)) }
    }

    private suspend fun checkIncoming(fromPush: Boolean = false) {
        if (_phase.value != CallPhase.IDLE) return
        val t = token() ?: return
        val r = try {
            ChatApi.get(t, mapOf("callin" to "1"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return
        }
        val c = r.optJSONObject("call")
        if (c == null) {
            if (fromPush) CallNotifier.cancel(context)
            return
        }
        val p = c.optJSONObject("peer") ?: return
        if (_phase.value != CallPhase.IDLE) return
        reset()
        val peer = CallPeer(p.optString("uid"), p.optString("username"), p.optString("display"), p.optString("av"))
        val leg = Leg(peer, false, c.optString("id"), c.optString("offer"), false)
        leg.grp = c.optString("grp").takeIf { it.isNotBlank() }
        primary = leg
        legs.add(leg)
        _peer.value = peer
        _phase.value = CallPhase.INCOMING
        publish()
        // Aplikasi tidak tampil: dering dan tombol Jawab/Tolak lewat notifikasi layar penuh. Tampil: dering dan layar panggilan di dalam aplikasi.
        if (AppVisibility.inForeground) {
            CallNotifier.cancel(context)
            startRinging()
        } else {
            CallNotifier.showIncoming(context, p.optString("display").ifBlank { p.optString("username") }, leg.id)
        }
        val left = c.optLong("msLeft", 45_000L)
        ringWatch = scope.launch { watchRinging(t, left) }
    }

    /** Selama berdering: cek tiap 2 detik apakah pemanggil sudah membatalkan, dan menyerah kalau waktunya habis. */
    private suspend fun watchRinging(t: String, leftMs: Long) {
        val until = System.currentTimeMillis() + leftMs + 2000
        while (_phase.value == CallPhase.INCOMING) {
            val id = primary?.id ?: return
            try {
                val r = ChatApi.get(t, mapOf("call" to id, "since" to "0"))
                if (r.optString("state") != "ringing") { teardown(); return }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AccountError) {
                if (e.code == "not_found") { teardown(); return }
            } catch (e: Throwable) {
                // koneksi sesaat putus: coba lagi di putaran berikutnya
            }
            if (System.currentTimeMillis() > until) { teardown(); return }
            delay(2000)
        }
    }

    /* ------------------------------ polling saat menyambung & berlangsung ------------------------------ */

    private suspend fun pollLeg(t: String, leg: Leg) {
        var since = 0
        var fails = 0
        var lastCode = "network"
        var answered = !leg.isCaller
        var deadline = if (answered) System.currentTimeMillis() + CONNECT_TIMEOUT_MS else 0L
        while (_phase.value != CallPhase.IDLE && legs.contains(leg)) {
            val id = leg.id ?: return
            flushIce(t, leg)
            var ok = false
            try {
                val r = ChatApi.get(t, mapOf("call" to id, "since" to since.toString()))
                ok = true
                fails = 0
                if (r.optString("state") == "ended") { legEndedRemote(leg, r.optString("why")); return }
                if (!answered) {
                    val ans = r.optString("answer")
                    if (ans.isNotBlank()) {
                        leg.rtc?.setRemoteAnswer(ans)
                        answered = true
                        deadline = System.currentTimeMillis() + CONNECT_TIMEOUT_MS
                        if (!leg.connected) leg.st = "connecting"
                        if (leg === primary && _phase.value == CallPhase.OUTGOING) {
                            NexSounds.stopRingtone()
                            _phase.value = CallPhase.CONNECTING
                        }
                        publish()
                    }
                }
                val arr = r.optJSONArray("ice")
                if (arr != null) for (i in 0 until arr.length()) leg.rtc?.addRemoteIce(arr.optString(i))
                since = r.optInt("next", since)
                handleGroup(t, r)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AccountError) {
                if (e.code == "not_found") { legEndedRemote(leg, "ended"); return }
                fails++
                lastCode = e.code
                lastDetail = "poll ${e.code}: ${e.message}"
            } catch (e: Throwable) {
                fails++
                lastCode = "network"
                lastDetail = "poll ${e.javaClass.simpleName}: ${e.message}"
            }
            if (!leg.connected && deadline > 0 && System.currentTimeMillis() > deadline) {
                // Sinyal sudah bertukar tapi audio tak kunjung tersambung: biasanya jaringan butuh TURN.
                lastDetail = "ice timeout (tidak tersambung dalam ${CONNECT_TIMEOUT_MS / 1000} detik)"
                legFailed(leg, "call_noaudio")
                return
            }
            if (!ok) {
                // Audio mengalir langsung antar perangkat (WebRTC), jadi saat panggilan sudah berlangsung
                // gangguan server sesaat tidak boleh memutus panggilan; cukup coba lagi pelan-pelan.
                if (_phase.value != CallPhase.ACTIVE && fails >= 25) {
                    legFailed(leg, if (lastCode == "network") "network" else "call_server")
                    return
                }
                delay(minOf(1000L + fails * 500L, 5000L))
            } else {
                delay(if (_phase.value == CallPhase.ACTIVE) 2000L else 1000L)
            }
        }
    }

    /** Daftar peserta grup dari server. Peserta yang baru bergabung menyambung otomatis ke semua peserta lama. */
    private fun handleGroup(t: String, r: JSONObject) {
        val g = r.optString("grp")
        if (g.isNotBlank()) groupId = g
        val arr = r.optJSONArray("members") ?: return
        val list = ArrayList<CallPeer>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            list.add(CallPeer(o.optString("uid"), o.optString("username"), o.optString("display"), o.optString("av")))
        }
        knownMembers = list
        if (iAmJoiner && groupId != null) {
            val me = myId()
            list.forEach { m ->
                if (m.uid != me && m.uid.isNotBlank() && legs.none { it.peer.uid == m.uid }) startAutoLeg(t, m)
            }
        }
        startAutoWatch()
        publish()
    }

    private fun startAutoLeg(t: String, m: CallPeer) {
        val g = groupId ?: return
        val leg = Leg(m, true, null, null, true)
        legs.add(leg)
        leg.job = scope.launch { runOutgoing(t, leg, g) }
    }

    /** Selama dalam panggilan grup: terima sambungan otomatis dari peserta yang baru bergabung. */
    private fun startAutoWatch() {
        if (autoJob != null || groupId == null) return
        autoJob = scope.launch {
            while (_phase.value != CallPhase.IDLE) {
                delay(2000)
                val ph = _phase.value
                if (ph != CallPhase.ACTIVE && ph != CallPhase.CONNECTING) continue
                val t = token() ?: continue
                val r = try {
                    ChatApi.get(t, mapOf("callin" to "1"))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    continue
                }
                val arr = r.optJSONArray("auto") ?: continue
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id")
                    val p = o.optJSONObject("peer") ?: continue
                    val peer = CallPeer(p.optString("uid"), p.optString("username"), p.optString("display"), p.optString("av"))
                    if (legs.any { it.id == id || it.peer.uid == peer.uid }) continue
                    val leg = Leg(peer, false, id, o.optString("offer"), true)
                    legs.add(leg)
                    publish()
                    leg.job = scope.launch { runIncomingLeg(t, leg) }
                }
            }
        }
    }

    private suspend fun flushIce(t: String, leg: Leg) {
        val id = leg.id ?: return
        if (leg.iceOut.isEmpty()) return
        val batch = leg.iceOut.take(20)
        try {
            ChatApi.post(t, JSONObject().put("action", "call_ice").put("id", id).put("cands", JSONArray(batch)))
            repeat(batch.size) { if (leg.iceOut.isNotEmpty()) leg.iceOut.removeAt(0) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // dicoba lagi di putaran berikutnya
        }
    }

    /* ------------------------------ WebRTC & audio ------------------------------ */

    private fun newRtc(leg: Leg, ice: List<PeerConnection.IceServer>): CallRtc {
        val r = CallRtc(
            context, ice,
            onLocalIce = { j -> scope.launch { leg.iceOut.add(j) } },
            onState = { ok -> scope.launch { if (ok) onLegConnected(leg) else if (legs.contains(leg)) legFailed(leg, "call_failed") } }
        )
        r.setMuted(_muted.value)
        leg.rtc = r
        return r
    }

    private suspend fun loadIce(t: String): List<PeerConnection.IceServer> {
        val list = ArrayList<PeerConnection.IceServer>()
        try {
            val arr = ChatApi.get(t, mapOf("callcfg" to "1")).optJSONArray("iceServers")
            if (arr != null) for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val urls = ArrayList<String>()
                val raw = o.opt("urls")
                if (raw is JSONArray) { for (k in 0 until raw.length()) urls.add(raw.optString(k)) } else if (raw is String) urls.add(raw)
                if (urls.isEmpty()) continue
                val b = PeerConnection.IceServer.builder(urls)
                if (o.has("username")) b.setUsername(o.optString("username"))
                if (o.has("credential")) b.setPassword(o.optString("credential"))
                list.add(b.createIceServer())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // pakai cadangan di bawah
        }
        if (list.isEmpty()) {
            list.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
            list.add(
                PeerConnection.IceServer.builder(listOf("turn:openrelay.metered.ca:80", "turn:openrelay.metered.ca:443", "turn:openrelay.metered.ca:443?transport=tcp"))
                    .setUsername("openrelayproject").setPassword("openrelayproject").createIceServer()
            )
        }
        return list
    }

    private fun onLegConnected(leg: Leg) {
        if (!legs.contains(leg)) return
        leg.connected = true
        leg.st = "connected"
        publish()
        val ph = _phase.value
        if (ph == CallPhase.ACTIVE || ph == CallPhase.IDLE || ph == CallPhase.INCOMING) return
        // Sudah diangkat/tersambung: dering berhenti.
        NexSounds.stopRingtone()
        _phase.value = CallPhase.ACTIVE
        _seconds.value = 0
        timerJob?.cancel()
        timerJob = scope.launch {
            while (true) {
                delay(1000)
                _seconds.value = _seconds.value + 1
            }
        }
    }

    /** Ambil fokus audio dan masuk mode panggilan. Fokus sementara ini otomatis menjeda pemutar musik aplikasi. */
    private fun beginAudio() {
        if (audioOn) return
        audioOn = true
        val am = context.getSystemService(AudioManager::class.java) ?: return
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener { }
            .build()
        am.requestAudioFocus(req)
        focusReq = req
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        applySpeaker(_speaker.value)
        // Mikrofon harus tetap aktif saat aplikasi di latar belakang, jadi jalankan layanan foreground.
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, CallService::class.java).putExtra("name", _peer.value?.display.orEmpty())
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun applySpeaker(on: Boolean) {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        am.isSpeakerphoneOn = on
    }

    private fun endAudio() {
        audioOn = false
        val am = context.getSystemService(AudioManager::class.java) ?: return
        @Suppress("DEPRECATION")
        am.isSpeakerphoneOn = false
        am.mode = AudioManager.MODE_NORMAL
        focusReq?.let { am.abandonAudioFocusRequest(it) }
        focusReq = null
    }

    /** Dering panggilan masuk (aplikasi terbuka): nada dering + getar. */
    private fun startRinging() {
        NexSounds.startRingtone(context, incoming = true)
        val vib = context.getSystemService(Vibrator::class.java) ?: return
        vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 900), 0))
    }

    private fun stopRinging() {
        NexSounds.stopRingtone()
        context.getSystemService(Vibrator::class.java)?.cancel()
    }

    /* ------------------------------ mengakhiri ------------------------------ */

    /** Sambungan berakhir karena lawan bicara (selesai/ditolak/tak terjawab). Panggilan grup lanjut selama masih ada sambungan lain. */
    private fun legEndedRemote(leg: Leg, why: String) {
        if (!legs.contains(leg)) return
        if (legs.size <= 1) { finishRemote(why); return }
        dropLeg(leg, notifyServer = false)
    }

    private fun finishRemote(why: String) {
        val wasCaller = primary?.isCaller == true
        val wasActive = _phase.value == CallPhase.ACTIVE
        teardown()
        if (!wasActive) {
            when (why) {
                "missed" -> if (wasCaller) _errors.tryEmit("call_unanswered")
                "declined" -> if (wasCaller) _errors.tryEmit("call_declined")
            }
        }
    }

    /** Satu sambungan gagal. Kalau itu satu-satunya, panggilan berakhir; kalau tidak, hanya sambungan itu yang dilepas. */
    private fun legFailed(leg: Leg, code: String) {
        if (!legs.contains(leg)) return
        if (code == "call_busy" && legs.size == 1 && leg === primary) {
            // Lawan bicara sedang dalam panggilan lain: tampilkan statusnya sebentar lalu tutup.
            leg.st = "busy"
            NexSounds.stopRingtone()
            publish()
            scope.launch {
                delay(2800)
                if (primary === leg && _phase.value != CallPhase.IDLE) teardown()
            }
            return
        }
        if (legs.size <= 1) { fail(code); return }
        if (!leg.auto && leg.isCaller) _errors.tryEmit(code)
        dropLeg(leg, notifyServer = true)
    }

    private fun dropLeg(leg: Leg, notifyServer: Boolean) {
        if (!legs.remove(leg)) return
        leg.dead = true
        leg.job?.cancel(); leg.job = null
        leg.rtc?.close(); leg.rtc = null
        val id = leg.id
        val t = token()
        if (notifyServer && id != null && t != null) {
            scope.launch { runCatching { ChatApi.post(t, JSONObject().put("action", "call_end").put("id", id)) } }
        }
        if (primary === leg) {
            primary = legs.firstOrNull()
            _peer.value = primary?.peer
        }
        publish()
    }

    private fun fail(code: String) {
        val ids = legs.mapNotNull { it.id }
        val t = token()
        teardown()
        _errors.tryEmit(code)
        if (t != null) ids.forEach { id ->
            scope.launch { runCatching { ChatApi.post(t, JSONObject().put("action", "call_end").put("id", id)) } }
        }
    }

    private fun reset() {
        legs.clear()
        primary = null
        groupId = null
        iAmJoiner = false
        knownMembers = emptyList()
        _members.value = emptyList()
        _muted.value = false
        _speaker.value = false
        _seconds.value = 0
    }

    private fun teardown() {
        val prev = _phase.value
        legs.forEach { it.dead = true; it.job?.cancel(); it.rtc?.close(); it.rtc = null }
        ringWatch?.cancel(); ringWatch = null
        timerJob?.cancel(); timerJob = null
        autoJob?.cancel(); autoJob = null
        stopRinging()
        CallNotifier.cancel(context)
        CallNav.answerRequested.value = false
        endAudio()
        runCatching { context.stopService(Intent(context, CallService::class.java)) }
        reset()
        _peer.value = null
        _phase.value = CallPhase.IDLE
        // Nada akhir panggilan, hanya kalau panggilan memang sempat dilakukan/tersambung (bukan sekadar menolak panggilan masuk).
        if (prev == CallPhase.OUTGOING || prev == CallPhase.CONNECTING || prev == CallPhase.ACTIVE) NexSounds.playEndCall(context)
    }

    companion object {
        const val CH_INCOMING = "nexrify_call_v2"
        private const val CONNECT_TIMEOUT_MS = 35_000L
        const val CH_ONGOING = "nexrify_call_ongoing"

        @Volatile private var instance: CallManager? = null

        fun init(context: Context): CallManager {
            ensureChannels(context.applicationContext)
            return instance ?: synchronized(this) { instance ?: CallManager(context.applicationContext).also { instance = it } }
        }

        fun getInstance(): CallManager = instance ?: throw IllegalStateException("CallManager belum diinisialisasi")

        /** Channel untuk push panggilan masuk (berdering) dan notifikasi panggilan berlangsung (senyap). */
        fun ensureChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CH_INCOMING) == null) {
                val ch = NotificationChannel(CH_INCOMING, "Panggilan masuk", NotificationManager.IMPORTANCE_HIGH)
                nm.deleteNotificationChannel("nexrify_call")
                ch.setSound(
                    android.net.Uri.parse("android.resource://" + context.packageName + "/" + id.nexrify.music.R.raw.ringtone),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                )
                ch.enableVibration(true)
                ch.vibrationPattern = longArrayOf(0, 700, 900, 700, 900)
                nm.createNotificationChannel(ch)
            }
            if (nm.getNotificationChannel(CH_ONGOING) == null) {
                nm.createNotificationChannel(NotificationChannel(CH_ONGOING, "Panggilan berlangsung", NotificationManager.IMPORTANCE_LOW))
            }
        }
    }
}

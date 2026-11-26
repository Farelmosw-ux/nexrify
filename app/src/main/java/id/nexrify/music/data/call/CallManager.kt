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

/** Permintaan dari notifikasi: tombol Jawab ditekan. Layar panggilan menjawab begitu panggilan masuk sudah terbaca. */
object CallNav {
    val answerRequested = MutableStateFlow(false)
}

/**
 * Panggilan suara 1-lawan-1. Semua logika berjalan di thread utama (Dispatchers.Main.immediate); panggilan jaringan
 * pindah thread sendiri lewat ChatApi, dan callback WebRTC dipindahkan balik ke sini lewat [scope].
 *
 * Alur: pemanggil membuat offer -> call_start -> polling sampai answer datang; penerima mendeteksi panggilan lewat
 * polling ringan (aplikasi terbuka) atau push FCM (aplikasi tertutup), lalu membuat answer -> call_answer.
 * Kandidat ICE dipertukarkan lewat server, lalu audio mengalir langsung antar perangkat.
 */
class CallManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _phase = MutableStateFlow(CallPhase.IDLE)
    val phase: StateFlow<CallPhase> = _phase.asStateFlow()
    private val _peer = MutableStateFlow<CallPeer?>(null)
    val peer: StateFlow<CallPeer?> = _peer.asStateFlow()
    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()
    private val _speaker = MutableStateFlow(false)
    val speaker: StateFlow<Boolean> = _speaker.asStateFlow()
    private val _seconds = MutableStateFlow(0)
    val seconds: StateFlow<Int> = _seconds.asStateFlow()
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Kode galat/keterangan untuk ditampilkan UI: call_busy, call_declined, call_unanswered, call_failed, network, dst. */
    val errors: SharedFlow<String> = _errors.asSharedFlow()
    /** Rincian teknis galat terakhir (untuk diagnosis), mis. "network: Unable to resolve host". */
    @Volatile var lastDetail: String = ""

    private var callId: String? = null
    private var isCaller = false
    private var pendingOffer: String? = null
    private var rtc: CallRtc? = null
    private var job: Job? = null
    private var ringWatch: Job? = null
    private var timerJob: Job? = null
    private var fgJob: Job? = null
    private val iceOut = ArrayList<String>()
    private var focusReq: AudioFocusRequest? = null

    private fun token(): String? = AccountManager.getInstance().token()

    /* ------------------------------ memulai & menjawab ------------------------------ */

    /** Telepon [p]. Izin mikrofon harus sudah diberikan sebelum memanggil ini. */
    fun startCall(p: CallPeer) {
        if (_phase.value != CallPhase.IDLE) return
        val t = token()
        if (t == null) { _errors.tryEmit("auth"); return }
        reset()
        isCaller = true
        _peer.value = p
        _phase.value = CallPhase.OUTGOING
        job = scope.launch {
            try {
                val r = newRtc(loadIce(t))
                beginAudio()
                val offer = r.createOffer()
                // Tanpa NonCancellable, pembatalan saat permintaan berjalan bisa meninggalkan panggilan "hantu" di sisi server.
                val resp = withContext(NonCancellable) {
                    ChatApi.post(t, JSONObject().put("action", "call_start").put("to", p.uid).put("offer", offer))
                }
                val id = resp.optString("id")
                if (_phase.value == CallPhase.IDLE) {
                    runCatching { ChatApi.post(t, JSONObject().put("action", "call_end").put("id", id)) }
                    return@launch
                }
                callId = id
                poll(t)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AccountError) {
                lastDetail = "${e.code}: ${e.message}"
                fail(e.code)
            } catch (e: Throwable) {
                lastDetail = "${e.javaClass.simpleName}: ${e.message}"
                fail("call_failed")
            }
        }
    }

    /** Jawab panggilan masuk. Izin mikrofon harus sudah diberikan. */
    fun accept() {
        if (_phase.value != CallPhase.INCOMING) return
        val t = token() ?: return
        val id = callId ?: return
        val offer = pendingOffer ?: return
        ringWatch?.cancel(); ringWatch = null
        stopRinging()
        CallNotifier.cancel(context)
        CallNav.answerRequested.value = false
        _phase.value = CallPhase.CONNECTING
        job = scope.launch {
            try {
                val r = newRtc(loadIce(t))
                beginAudio()
                val answer = r.acceptOffer(offer)
                ChatApi.post(t, JSONObject().put("action", "call_answer").put("id", id).put("answer", answer))
                poll(t)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AccountError) {
                lastDetail = "${e.code}: ${e.message}"
                fail(e.code)
            } catch (e: Throwable) {
                lastDetail = "${e.javaClass.simpleName}: ${e.message}"
                fail("call_failed")
            }
        }
    }

    /** Tolak panggilan masuk, batalkan panggilan keluar, atau akhiri panggilan yang berlangsung. */
    fun hangup() {
        if (_phase.value == CallPhase.IDLE) return
        val id = callId
        val t = token()
        teardown()
        if (id != null && t != null) {
            scope.launch { runCatching { ChatApi.post(t, JSONObject().put("action", "call_end").put("id", id)) } }
        }
    }

    fun decline() = hangup()

    fun toggleMute() {
        val m = !_muted.value
        _muted.value = m
        rtc?.setMuted(m)
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
        isCaller = false
        callId = c.optString("id")
        pendingOffer = c.optString("offer")
        _peer.value = CallPeer(p.optString("uid"), p.optString("username"), p.optString("display"), p.optString("av"))
        _phase.value = CallPhase.INCOMING
        // Aplikasi tidak tampil: dering dan tombol Jawab/Tolak lewat notifikasi layar penuh. Tampil: dering dan layar panggilan di dalam aplikasi.
        if (AppVisibility.inForeground) {
            CallNotifier.cancel(context)
            startRinging()
        } else {
            CallNotifier.showIncoming(context, p.optString("display").ifBlank { p.optString("username") }, callId)
        }
        val left = c.optLong("msLeft", 45_000L)
        ringWatch = scope.launch { watchRinging(t, left) }
    }

    /** Selama berdering: cek tiap 2 detik apakah pemanggil sudah membatalkan, dan menyerah kalau waktunya habis. */
    private suspend fun watchRinging(t: String, leftMs: Long) {
        val until = System.currentTimeMillis() + leftMs + 2000
        while (_phase.value == CallPhase.INCOMING) {
            val id = callId ?: return
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

    private suspend fun poll(t: String) {
        var since = 0
        var fails = 0
        var lastCode = "network"
        var answered = !isCaller
        while (_phase.value != CallPhase.IDLE) {
            val id = callId ?: return
            flushIce(t, id)
            var ok = false
            try {
                val r = ChatApi.get(t, mapOf("call" to id, "since" to since.toString()))
                ok = true
                fails = 0
                if (r.optString("state") == "ended") { finishRemote(r.optString("why")); return }
                if (!answered) {
                    val ans = r.optString("answer")
                    if (ans.isNotBlank()) {
                        rtc?.setRemoteAnswer(ans)
                        answered = true
                        if (_phase.value == CallPhase.OUTGOING) _phase.value = CallPhase.CONNECTING
                    }
                }
                val arr = r.optJSONArray("ice")
                if (arr != null) for (i in 0 until arr.length()) rtc?.addRemoteIce(arr.optString(i))
                since = r.optInt("next", since)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AccountError) {
                if (e.code == "not_found") { finishRemote("ended"); return }
                fails++
                lastCode = e.code
                lastDetail = "poll ${e.code}: ${e.message}"
            } catch (e: Throwable) {
                fails++
                lastCode = "network"
                lastDetail = "poll ${e.javaClass.simpleName}: ${e.message}"
            }
            if (!ok) {
                // Audio mengalir langsung antar perangkat (WebRTC), jadi saat panggilan sudah berlangsung
                // gangguan server sesaat tidak boleh memutus panggilan; cukup coba lagi pelan-pelan.
                // Saat masih menyambung beri kesempatan lebih lama (kira-kira 40 detik) sebelum menyerah.
                if (_phase.value != CallPhase.ACTIVE && fails >= 25) {
                    fail(if (lastCode == "network") "network" else "call_server")
                    return
                }
                delay(minOf(1000L + fails * 500L, 5000L))
            } else {
                delay(if (_phase.value == CallPhase.ACTIVE) 2000L else 1000L)
            }
        }
    }

    private suspend fun flushIce(t: String, id: String) {
        if (iceOut.isEmpty()) return
        val batch = iceOut.take(20)
        try {
            ChatApi.post(t, JSONObject().put("action", "call_ice").put("id", id).put("cands", JSONArray(batch)))
            repeat(batch.size) { if (iceOut.isNotEmpty()) iceOut.removeAt(0) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // dicoba lagi di putaran berikutnya
        }
    }

    /* ------------------------------ WebRTC & audio ------------------------------ */

    private fun newRtc(ice: List<PeerConnection.IceServer>): CallRtc {
        val r = CallRtc(
            context, ice,
            onLocalIce = { j -> scope.launch { iceOut.add(j) } },
            onState = { ok -> scope.launch { if (ok) onConnected() else if (_phase.value != CallPhase.IDLE) fail("call_failed") } }
        )
        rtc = r
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
            // pakai STUN bawaan di bawah
        }
        if (list.isEmpty()) list.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        return list
    }

    private fun onConnected() {
        val ph = _phase.value
        if (ph == CallPhase.ACTIVE || ph == CallPhase.IDLE || ph == CallPhase.INCOMING) return
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
        val am = context.getSystemService(AudioManager::class.java) ?: return
        @Suppress("DEPRECATION")
        am.isSpeakerphoneOn = false
        am.mode = AudioManager.MODE_NORMAL
        focusReq?.let { am.abandonAudioFocusRequest(it) }
        focusReq = null
    }

    private fun startRinging() {
        val vib = context.getSystemService(Vibrator::class.java) ?: return
        vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 900), 0))
    }

    private fun stopRinging() {
        context.getSystemService(Vibrator::class.java)?.cancel()
    }

    /* ------------------------------ mengakhiri ------------------------------ */

    private fun finishRemote(why: String) {
        val wasCaller = isCaller
        val wasActive = _phase.value == CallPhase.ACTIVE
        teardown()
        if (!wasActive) {
            when (why) {
                "missed" -> if (wasCaller) _errors.tryEmit("call_unanswered")
                "declined" -> if (wasCaller) _errors.tryEmit("call_declined")
            }
        }
    }

    private fun fail(code: String) {
        val id = callId
        val t = token()
        teardown()
        _errors.tryEmit(code)
        if (id != null && t != null) {
            scope.launch { runCatching { ChatApi.post(t, JSONObject().put("action", "call_end").put("id", id)) } }
        }
    }

    private fun reset() {
        callId = null
        pendingOffer = null
        iceOut.clear()
        _muted.value = false
        _speaker.value = false
        _seconds.value = 0
    }

    private fun teardown() {
        job?.cancel(); job = null
        ringWatch?.cancel(); ringWatch = null
        timerJob?.cancel(); timerJob = null
        stopRinging()
        CallNotifier.cancel(context)
        CallNav.answerRequested.value = false
        rtc?.close(); rtc = null
        endAudio()
        runCatching { context.stopService(Intent(context, CallService::class.java)) }
        callId = null
        pendingOffer = null
        iceOut.clear()
        _muted.value = false
        _speaker.value = false
        _seconds.value = 0
        _peer.value = null
        _phase.value = CallPhase.IDLE
    }

    companion object {
        const val CH_INCOMING = "nexrify_call"
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
                ch.setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
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

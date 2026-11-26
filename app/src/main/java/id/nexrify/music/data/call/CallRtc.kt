package id.nexrify.music.data.call

import android.content.Context
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Pengamat SDP dengan isi kosong, supaya tiap pemanggil cukup menimpa yang dibutuhkan. */
private open class SdpAdapter : SdpObserver {
    override fun onCreateSuccess(d: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(e: String?) {}
    override fun onSetFailure(e: String?) {}
}

/**
 * Pembungkus WebRTC untuk satu panggilan suara (hanya audio, tanpa video). Satu objek = satu sambungan.
 * [onLocalIce] dipanggil untuk tiap kandidat jaringan milik kita (JSON: m = mid, i = indeks, c = kandidat).
 * [onState] true saat audio tersambung, false saat sambungan gagal.
 * Callback datang dari thread WebRTC, jadi pemakai harus memindahkannya ke thread utama sendiri.
 */
class CallRtc(
    context: Context,
    iceServers: List<PeerConnection.IceServer>,
    private val onLocalIce: (String) -> Unit,
    private val onState: (Boolean) -> Unit
) {
    private val adm: JavaAudioDeviceModule
    private val factory: PeerConnectionFactory
    private val pc: PeerConnection
    private val source: AudioSource
    private val track: AudioTrack
    private val lock = Any()
    private var remoteSet = false
    private var closed = false
    private val pendingIce = ArrayList<IceCandidate>()

    private val observer = object : PeerConnection.Observer {
        override fun onSignalingChange(s: PeerConnection.SignalingState?) {}
        override fun onIceConnectionChange(s: PeerConnection.IceConnectionState?) {
            when (s) {
                PeerConnection.IceConnectionState.CONNECTED, PeerConnection.IceConnectionState.COMPLETED -> onState(true)
                PeerConnection.IceConnectionState.FAILED -> onState(false)
                else -> Unit
            }
        }
        override fun onIceConnectionReceivingChange(b: Boolean) {}
        override fun onIceGatheringChange(s: PeerConnection.IceGatheringState?) {}
        override fun onIceCandidate(c: IceCandidate?) {
            if (c == null) return
            onLocalIce(JSONObject().put("m", c.sdpMid ?: "").put("i", c.sdpMLineIndex).put("c", c.sdp).toString())
        }
        override fun onIceCandidatesRemoved(c: Array<out IceCandidate>?) {}
        override fun onAddStream(s: MediaStream?) {}
        override fun onRemoveStream(s: MediaStream?) {}
        override fun onDataChannel(d: DataChannel?) {}
        override fun onRenegotiationNeeded() {}
    }

    init {
        ensureInit(context.applicationContext)
        adm = JavaAudioDeviceModule.builder(context.applicationContext)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()
        factory = PeerConnectionFactory.builder().setAudioDeviceModule(adm).createPeerConnectionFactory()
        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        pc = factory.createPeerConnection(config, observer) ?: throw IllegalStateException("peer connection")
        source = factory.createAudioSource(MediaConstraints())
        track = factory.createAudioTrack("nx_audio", source)
        pc.addTrack(track, listOf("nx_stream"))
    }

    private fun audioConstraints() = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
    }

    /** Pemanggil: buat offer dan pasang sebagai deskripsi lokal. Mengembalikan teks SDP untuk dikirim ke server. */
    suspend fun createOffer(): String = suspendCancellableCoroutine { cont ->
        pc.createOffer(object : SdpAdapter() {
            override fun onCreateSuccess(d: SessionDescription?) {
                if (d == null) { cont.resumeWithException(IllegalStateException("offer")); return }
                pc.setLocalDescription(object : SdpAdapter() {
                    override fun onSetSuccess() { cont.resume(d.description) }
                    override fun onSetFailure(e: String?) { cont.resumeWithException(IllegalStateException(e ?: "setLocal")) }
                }, d)
            }
            override fun onCreateFailure(e: String?) { cont.resumeWithException(IllegalStateException(e ?: "createOffer")) }
        }, audioConstraints())
    }

    /** Penerima: pasang offer lawan bicara, buat answer, dan kembalikan teks SDP answer. */
    suspend fun acceptOffer(offer: String): String = suspendCancellableCoroutine { cont ->
        pc.setRemoteDescription(object : SdpAdapter() {
            override fun onSetSuccess() {
                markRemoteSet()
                pc.createAnswer(object : SdpAdapter() {
                    override fun onCreateSuccess(d: SessionDescription?) {
                        if (d == null) { cont.resumeWithException(IllegalStateException("answer")); return }
                        pc.setLocalDescription(object : SdpAdapter() {
                            override fun onSetSuccess() { cont.resume(d.description) }
                            override fun onSetFailure(e: String?) { cont.resumeWithException(IllegalStateException(e ?: "setLocal")) }
                        }, d)
                    }
                    override fun onCreateFailure(e: String?) { cont.resumeWithException(IllegalStateException(e ?: "createAnswer")) }
                }, audioConstraints())
            }
            override fun onSetFailure(e: String?) { cont.resumeWithException(IllegalStateException(e ?: "setRemote")) }
        }, SessionDescription(SessionDescription.Type.OFFER, offer))
    }

    /** Pemanggil: pasang answer dari penerima. */
    fun setRemoteAnswer(answer: String) {
        pc.setRemoteDescription(object : SdpAdapter() {
            override fun onSetSuccess() { markRemoteSet() }
            override fun onSetFailure(e: String?) { onState(false) }
        }, SessionDescription(SessionDescription.Type.ANSWER, answer))
    }

    /** Kandidat ICE dari lawan bicara (JSON hasil [onLocalIce] di sisi sana). Ditahan dulu kalau deskripsi remote belum terpasang. */
    fun addRemoteIce(json: String) {
        val c = try {
            val o = JSONObject(json)
            IceCandidate(o.optString("m"), o.optInt("i"), o.optString("c"))
        } catch (e: Throwable) {
            return
        }
        synchronized(lock) {
            if (closed) return
            if (remoteSet) pc.addIceCandidate(c) else pendingIce.add(c)
        }
    }

    private fun markRemoteSet() {
        synchronized(lock) {
            if (closed) return
            remoteSet = true
            pendingIce.forEach { pc.addIceCandidate(it) }
            pendingIce.clear()
        }
    }

    fun setMuted(muted: Boolean) {
        track.setEnabled(!muted)
    }

    fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
        }
        runCatching { track.dispose() }
        runCatching { source.dispose() }
        runCatching { pc.close() }
        runCatching { pc.dispose() }
        runCatching { factory.dispose() }
        runCatching { adm.release() }
    }

    companion object {
        @Volatile private var inited = false

        private fun ensureInit(app: Context) {
            if (inited) return
            synchronized(this) {
                if (inited) return
                PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(app).createInitializationOptions())
                inited = true
            }
        }
    }
}

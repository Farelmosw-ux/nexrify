package id.nexrify.music.data.call

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
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
 */
class CallRtc(
    context: Context,
    iceServers: List<PeerConnection.IceServer>,
    private val onLocalIce: (String) -> Unit,
    private val onState: (Boolean) -> Unit
) {
    private val factory: PeerConnectionFactory
    private val pc: PeerConnection
    private val source: AudioSource
    private val track: AudioTrack
    private val lock = Any()
    private var remoteSet = false
    private var closed = false
    private var notifiedConnected = false
    private val pendingIce = ArrayList<IceCandidate>()

    private fun fireConnected() {
        synchronized(lock) {
            if (closed || notifiedConnected) return
            notifiedConnected = true
        }
        onState(true)
    }

    private val observer = object : PeerConnection.Observer {
        override fun onSignalingChange(s: PeerConnection.SignalingState?) {}
        override fun onIceConnectionChange(s: PeerConnection.IceConnectionState?) {
            when (s) {
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED -> fireConnected()
                PeerConnection.IceConnectionState.FAILED -> onState(false)
                else -> Unit
            }
        }
        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
            when (newState) {
                PeerConnection.PeerConnectionState.CONNECTED -> fireConnected()
                PeerConnection.PeerConnectionState.FAILED -> onState(false)
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
        override fun onAddStream(s: MediaStream?) {
            s?.audioTracks?.forEach { remoteTrack ->
                runCatching {
                    remoteTrack.setEnabled(true)
                    remoteTrack.setVolume(10.0)
                }
            }
        }
        override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
            val remoteTrack = receiver?.track() as? AudioTrack
            runCatching {
                remoteTrack?.setEnabled(true)
                remoteTrack?.setVolume(10.0)
            }
        }
        override fun onRemoveStream(s: MediaStream?) {}
        override fun onDataChannel(d: DataChannel?) {}
        override fun onRenegotiationNeeded() {}
    }

    init {
        factory = acquire(context.applicationContext)
        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            iceTransportsType = PeerConnection.IceTransportsType.ALL
        }
        pc = factory.createPeerConnection(config, observer) ?: run { release(); throw IllegalStateException("peer connection") }
        val audioSourceConstraints = MediaConstraints().apply {
            optional.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            optional.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            optional.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            optional.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        }
        source = factory.createAudioSource(audioSourceConstraints)
        track = factory.createAudioTrack("nx_audio", source).apply {
            setEnabled(true)
            setVolume(10.0)
        }
        pc.addTrack(track, listOf("nx_stream"))
    }

    private fun audioConstraints() = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
    }

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

    fun setRemoteAnswer(answer: String) {
        pc.setRemoteDescription(object : SdpAdapter() {
            override fun onSetSuccess() { markRemoteSet() }
            override fun onSetFailure(e: String?) { onState(false) }
        }, SessionDescription(SessionDescription.Type.ANSWER, answer))
    }

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
        runCatching { track.setEnabled(!muted) }
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
        release()
    }

    companion object {
        @Volatile private var inited = false
        private val sharedLock = Any()
        private var sharedFactory: PeerConnectionFactory? = null
        private var sharedAdm: JavaAudioDeviceModule? = null
        private var refs = 0

        private fun ensureInit(app: Context) {
            if (inited) return
            synchronized(this) {
                if (inited) return
                PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(app).createInitializationOptions())
                inited = true
            }
        }

        private fun acquire(app: Context): PeerConnectionFactory = synchronized(sharedLock) {
            ensureInit(app)
            val existing = sharedFactory
            if (existing != null) { refs++; return existing }
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val adm = JavaAudioDeviceModule.builder(app)
                .setAudioAttributes(attrs)
                .setUseHardwareAcousticEchoCanceler(true)
                .setUseHardwareNoiseSuppressor(true)
                .setSamplesReadyCallback { }
                .createAudioDeviceModule()
            adm.setMicrophoneMute(false)
            adm.setSpeakerMute(false)
            val f = PeerConnectionFactory.builder().setAudioDeviceModule(adm).createPeerConnectionFactory()
            sharedAdm = adm
            sharedFactory = f
            refs = 1
            f
        }

        private fun release() = synchronized(sharedLock) {
            if (refs > 0) refs--
            if (refs == 0) {
                runCatching { sharedFactory?.dispose() }
                runCatching { sharedAdm?.release() }
                sharedFactory = null
                sharedAdm = null
            }
        }
    }
}

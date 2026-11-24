package id.nexrify.music.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import id.nexrify.music.data.account.AccountError
import id.nexrify.music.data.account.AccountManager
import id.nexrify.music.data.chat.ChatApi
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
import java.io.File

/**
 * Pemutar pesan suara: satu VN aktif sekaligus. Audio diunduh sekali ke cache lalu diputar dari berkas.
 * Selama VN berjalan, lagu yang sedang diputar dijeda lewat audio focus dan lanjut otomatis setelah VN selesai.
 */
object VoicePlayer {
    data class State(val id: String? = null, val loading: Boolean = false, val playing: Boolean = false, val posMs: Int = 0, val durMs: Int = 0, val speed: Float = 1f)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** "vn_gone", "network", atau "server". */
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private var mp: MediaPlayer? = null
    private var ticker: Job? = null
    private var loadJob: Job? = null
    private var focus: AudioFocusRequest? = null
    private var audio: AudioManager? = null

    /** Ketuk tombol putar: mulai, jeda, atau lanjut. */
    fun toggle(context: Context, id: String) {
        val s = _state.value
        if (s.id == id && !s.loading) {
            val p = mp ?: return
            if (p.isPlaying) { p.pause(); _state.value = s.copy(playing = false); dropFocus() }
            else { takeFocus(context); p.start(); _state.value = s.copy(playing = true); startTicker() }
            return
        }
        play(context.applicationContext, id)
    }

    private fun play(ctx: Context, id: String) {
        stop()
        _state.value = State(id = id, loading = true)
        loadJob = scope.launch {
            try {
                val file = File(File(ctx.cacheDir, "vn"), "$id.m4a")
                if (!file.exists() || file.length() < 600L) {
                    val t = AccountManager.getInstance().token() ?: throw AccountError("auth", "no token")
                    ChatApi.downloadVoice(t, id, file)
                }
                val p = MediaPlayer()
                p.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                p.setDataSource(file.absolutePath)
                p.setOnCompletionListener { finish() }
                p.setOnErrorListener { _, _, _ -> finish(); _errors.tryEmit("server"); true }
                p.prepare()
                mp = p
                takeFocus(ctx)
                p.start()
                _state.value = State(id = id, playing = true, posMs = 0, durMs = p.duration)
                startTicker()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AccountError) {
                _state.value = State()
                _errors.tryEmit(if (e.code == "vn_gone" || e.code == "network") e.code else "server")
            } catch (_: Throwable) {
                stop()
                _errors.tryEmit("server")
            }
        }
    }

    fun seekTo(fraction: Float) {
        val p = mp ?: return
        val s = _state.value
        val to = (p.duration * fraction.coerceIn(0f, 1f)).toInt()
        p.seekTo(to)
        _state.value = s.copy(posMs = to)
    }

    /** Putar bergantian 1x → 1.5x → 2x. */
    fun cycleSpeed() {
        val p = mp ?: return
        val s = _state.value
        val next = when { s.speed < 1.2f -> 1.5f; s.speed < 1.8f -> 2f; else -> 1f }
        runCatching {
            p.playbackParams = p.playbackParams.setSpeed(next)
            if (!p.isPlaying) p.pause()
        }
        _state.value = s.copy(speed = next)
    }

    private fun finish() {
        ticker?.cancel()
        runCatching { mp?.release() }
        mp = null
        dropFocus()
        _state.value = State()
    }

    fun stop() {
        loadJob?.cancel()
        ticker?.cancel()
        runCatching { mp?.stop() }
        runCatching { mp?.release() }
        mp = null
        dropFocus()
        _state.value = State()
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (true) {
                val p = mp ?: break
                val pos = runCatching { p.currentPosition }.getOrDefault(0)
                _state.value = _state.value.copy(posMs = pos)
                delay(80L)
            }
        }
    }

    private fun takeFocus(ctx: Context) {
        val am = audio ?: (ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager).also { audio = it }
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS) scope.launch { stop() }
            }
            .build()
        focus = req
        am.requestAudioFocus(req)
    }

    private fun dropFocus() {
        focus?.let { audio?.abandonAudioFocusRequest(it) }
        focus = null
    }
}

package id.nexrify.music.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import kotlin.math.sqrt

/** Hasil rekaman siap kirim. wave = digit 0-9 (maks 40 batang). */
data class VoiceClip(val file: File, val durMs: Long, val wave: String)

/**
 * Perekam pesan suara: AAC mono 16 kHz 24 kbps dalam wadah M4A (60 detik ≈ 180 KB, di bawah batas 300 KB server).
 * Saat merekam, lagu yang sedang diputar dijeda lewat audio focus supaya tidak bocor ke mikrofon, lalu lanjut sendiri setelah selesai.
 */
class VoiceRecorder(private val context: Context) {
    companion object { const val MAX_MS = 60_000L; const val MIN_MS = 800L }

    private var rec: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L
    private val amps = ArrayList<Int>()
    private val audio by lazy { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private var focus: AudioFocusRequest? = null

    val isRecording: Boolean get() = rec != null
    fun elapsedMs(): Long = if (rec == null) 0L else System.currentTimeMillis() - startedAt

    fun start(): Boolean {
        if (rec != null) return true
        val f = File(context.cacheDir, "vn_rec_${System.currentTimeMillis()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        return try {
            takeFocus()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(16_000)
            r.setAudioEncodingBitRate(24_000)
            r.setMaxDuration(MAX_MS.toInt())
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            rec = r; file = f; startedAt = System.currentTimeMillis(); amps.clear()
            true
        } catch (e: Throwable) {
            runCatching { r.release() }
            runCatching { f.delete() }
            dropFocus()
            false
        }
    }

    /** Level suara saat ini 0..9; dipanggil berkala oleh UI (dan dipakai untuk gelombang akhir). */
    fun sample(): Int {
        val r = rec ?: return 0
        val a = runCatching { r.maxAmplitude }.getOrDefault(0)
        val lvl = (sqrt(a / 32767.0) * 9.0).toInt().coerceIn(0, 9)
        amps.add(lvl)
        return lvl
    }

    /** Selesai merekam. Null kalau terlalu pendek atau gagal. */
    fun stop(): VoiceClip? {
        val r = rec ?: return null
        val f = file
        val dur = System.currentTimeMillis() - startedAt
        rec = null; file = null
        val ok = try { r.stop(); true } catch (_: Throwable) { false }
        runCatching { r.release() }
        dropFocus()
        if (!ok || f == null || dur < MIN_MS || !f.exists() || f.length() < 600L) { runCatching { f?.delete() }; return null }
        return VoiceClip(f, dur.coerceAtMost(MAX_MS), buildWave())
    }

    fun cancel() {
        val r = rec
        val f = file
        rec = null; file = null
        if (r != null) { runCatching { r.stop() }; runCatching { r.release() } }
        runCatching { f?.delete() }
        dropFocus()
    }

    private fun buildWave(): String {
        if (amps.isEmpty()) return ""
        val n = minOf(40, amps.size)
        val sb = StringBuilder()
        for (i in 0 until n) {
            val from = i * amps.size / n
            val to = maxOf(from + 1, (i + 1) * amps.size / n)
            var mx = 0
            for (k in from until minOf(to, amps.size)) mx = maxOf(mx, amps[k])
            sb.append(mx.coerceIn(1, 9))
        }
        return sb.toString()
    }

    private fun takeFocus() {
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener { }
            .build()
        focus = req
        audio.requestAudioFocus(req)
    }

    private fun dropFocus() {
        focus?.let { audio.abandonAudioFocusRequest(it) }
        focus = null
    }
}

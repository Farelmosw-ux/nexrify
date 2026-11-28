package id.nexrify.music.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import id.nexrify.music.R

/**
 * Suara aplikasi: nada sambung/dering panggilan (berulang), nada akhir panggilan, dan nada pesan masuk.
 * Semua dipanggil dari thread utama.
 */
object NexSounds {
    private var ring: MediaPlayer? = null
    private var lastMsgAt = 0L

    /** Dering berulang. Dipakai pemanggil (menunggu diangkat) dan penerima (panggilan masuk, aplikasi terbuka). */
    fun startRingtone(context: Context, incoming: Boolean = false) {
        if (ring != null) return
        ring = runCatching {
            MediaPlayer.create(
                context.applicationContext, R.raw.ringtone,
                AudioAttributes.Builder()
                    .setUsage(if (incoming) AudioAttributes.USAGE_NOTIFICATION_RINGTONE else AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                android.media.AudioManager.AUDIO_SESSION_ID_GENERATE
            )?.apply { isLooping = true; start() }
        }.getOrNull()
    }

    fun stopRingtone() {
        ring?.let { runCatching { it.stop() }; runCatching { it.release() } }
        ring = null
    }

    /** Nada akhir panggilan, sekali putar. */
    fun playEndCall(context: Context) = playOnce(context, R.raw.endcall, AudioAttributes.USAGE_NOTIFICATION_EVENT)

    /** Nada pesan masuk (diberi jeda supaya banjir pesan tidak berisik). */
    fun playMessage(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastMsgAt < 1200) return
        lastMsgAt = now
        playOnce(context, R.raw.pesan, AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
    }

    private fun playOnce(context: Context, res: Int, usage: Int) {
        runCatching {
            val mp = MediaPlayer.create(
                context.applicationContext, res,
                AudioAttributes.Builder().setUsage(usage).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                android.media.AudioManager.AUDIO_SESSION_ID_GENERATE
            ) ?: return
            mp.setOnCompletionListener { runCatching { it.release() } }
            mp.setOnErrorListener { p, _, _ -> runCatching { p.release() }; true }
            mp.start()
        }
    }
}

package id.nexrify.music

import android.app.Activity
import android.os.Bundle
import id.nexrify.music.service.PlaybackService

/**
 * Perantara tembus pandang untuk tombol Putar di widget. Activity boleh dimulai dari klik widget (broadcast
 * tidak boleh membuka aplikasi dari latar belakang), jadi tombol ini selalu bereaksi:
 *  - pemutar hidup dan sudah punya lagu -> putar / jeda langsung, tanpa membuka aplikasi;
 *  - pemutar mati -> buka aplikasi dan lanjutkan lagu terakhir.
 * Activity langsung menutup diri dan tidak tampil di layar.
 */
class WidgetActionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val player = PlaybackService.playerRef
            if (player != null && player.currentMediaItem != null) {
                if (player.isPlaying) {
                    player.pause()
                } else {
                    when (player.playbackState) {
                        androidx.media3.common.Player.STATE_IDLE -> player.prepare()
                        androidx.media3.common.Player.STATE_ENDED -> player.seekToDefaultPosition()
                    }
                    player.play()
                }
                NexrifyWidgetProvider.refresh(applicationContext)
            } else {
                NexrifyWidgetProvider.openAppToPlay(applicationContext)
            }
        } catch (_: Throwable) {
        }
        finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        const val ACTION_PLAY = "id.nexrify.music.WIDGET_PLAY"
    }
}

package id.nexrify.music.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import id.nexrify.music.MainActivity
import id.nexrify.music.NexrifyWidgetProvider
import id.nexrify.music.R
import id.nexrify.music.i18n.currentStrings
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class PlaybackService : MediaSessionService() {

    private var player: Player? = null
    private var mediaSession: MediaSession? = null
    private var capsule: id.nexrify.music.capsule.MusicCapsuleController? = null
    private val serviceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main + id.nexrify.music.util.CrashReporter.handler)
    private var favoriteJob: kotlinx.coroutines.Job? = null
    private var widgetLikeJob: kotlinx.coroutines.Job? = null
    private var listenJob: kotlinx.coroutines.Job? = null

    /** Lapor waktu mendengarkan ke server (title Pendengar Terlama): start saat lagu diputar, tick tiap 30 detik, stop saat berhenti. */
    private fun reportListening(playing: Boolean) {
        listenJob?.cancel()
        listenJob = null
        val chat = runCatching { id.nexrify.music.data.chat.ChatManager.getInstance() }.getOrNull() ?: return
        if (playing) {
            chat.listenReport("start")
            listenJob = serviceScope.launch {
                while (true) {
                    kotlinx.coroutines.delay(30_000L)
                    chat.listenReport("tick")
                }
            }
        } else chat.listenReport("stop")
    }

    /** Tombol di notifikasi dan kontrol media: suka dan tampilkan/sembunyikan kapsul. */
    @OptIn(UnstableApi::class)
    private fun controlsLayout(
        liked: Boolean = PlaybackBridge.isFavorite.value,
        capsuleOn: Boolean = id.nexrify.music.data.settings.SettingsManager.getInstance().isCapsuleEnabled()
    ): com.google.common.collect.ImmutableList<androidx.media3.session.CommandButton> {
        val strings = currentStrings()
        return com.google.common.collect.ImmutableList.of(
            androidx.media3.session.CommandButton.Builder()
                .setDisplayName(if (liked) strings.unlikeAction else strings.likeAction)
                .setSessionCommand(androidx.media3.session.SessionCommand(ACTION_TOGGLE_LIKE, android.os.Bundle.EMPTY))
                .setIconResId(if (liked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline)
                .build(),
            androidx.media3.session.CommandButton.Builder()
                .setDisplayName(if (capsuleOn) strings.capsuleHideAction else strings.capsuleShowAction)
                .setSessionCommand(androidx.media3.session.SessionCommand(ACTION_TOGGLE_CAPSULE, android.os.Bundle.EMPTY))
                .setIconResId(if (capsuleOn) R.drawable.ic_capsule_on else R.drawable.ic_capsule_off)
                .build()
        )
    }

    // Tombol headset: 1x ketuk = putar/jeda, 2x = lagu berikutnya, 3x = lagu sebelumnya.
    private val clickHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var headsetClicks = 0
    private val headsetRunnable = Runnable {
        val n = headsetClicks
        headsetClicks = 0
        when {
            n >= 3 -> sendBroadcast(Intent(ACTION_PREV).setPackage(packageName))
            n == 2 -> sendBroadcast(Intent(ACTION_NEXT).setPackage(packageName))
            n == 1 -> mediaSession?.player?.let { p -> if (p.isPlaying) p.pause() else p.play() }
        }
    }

    @OptIn(UnstableApi::class)
    private val sessionCallback = object : MediaSession.Callback {
        override fun onMediaButtonEvent(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            intent: Intent
        ): Boolean {
            @Suppress("DEPRECATION")
            val event = intent.getParcelableExtra<android.view.KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return false
            when (event.keyCode) {
                android.view.KeyEvent.KEYCODE_HEADSETHOOK, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    if (event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                        headsetClicks++
                        clickHandler.removeCallbacks(headsetRunnable)
                        clickHandler.postDelayed(headsetRunnable, 380L)
                    }
                    return true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    if (event.action == android.view.KeyEvent.ACTION_DOWN) sendBroadcast(Intent(ACTION_NEXT).setPackage(packageName))
                    return true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    if (event.action == android.view.KeyEvent.ACTION_DOWN) sendBroadcast(Intent(ACTION_PREV).setPackage(packageName))
                    return true
                }
            }
            return false
        }

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(androidx.media3.session.SessionCommand(ACTION_TOGGLE_LIKE, android.os.Bundle.EMPTY))
                .add(androidx.media3.session.SessionCommand(ACTION_TOGGLE_CAPSULE, android.os.Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .setCustomLayout(controlsLayout())
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: androidx.media3.session.SessionCommand,
            args: android.os.Bundle
        ): com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.SessionResult> {
            if (customCommand.customAction == ACTION_TOGGLE_LIKE) {
                PlaybackBridge.onToggleFavorite?.invoke()
                return com.google.common.util.concurrent.Futures.immediateFuture(
                    androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS)
                )
            }
            if (customCommand.customAction == ACTION_TOGGLE_CAPSULE) {
                val settings = id.nexrify.music.data.settings.SettingsManager.getInstance()
                settings.setCapsuleEnabled(!settings.isCapsuleEnabled())
                return com.google.common.util.concurrent.Futures.immediateFuture(
                    androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS)
                )
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }
    private val artworkExecutor = Executors.newSingleThreadExecutor()
    @Volatile private var latestArtworkUrl: String? = null
    @Volatile private var latestArtworkTitle: String? = null

    companion object {
        const val CHANNEL_ID = "nexrify_playback_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_NEXT = "id.nexrify.music.ACTION_NEXT"
        const val ACTION_TOGGLE_LIKE = "id.nexrify.music.ACTION_TOGGLE_LIKE"
        const val ACTION_TOGGLE_CAPSULE = "id.nexrify.music.ACTION_TOGGLE_CAPSULE"
        const val ACTION_PREV = "id.nexrify.music.ACTION_PREV"

        /** Pemutar milik layanan ini, dipakai widget untuk jeda/putar langsung. Null kalau layanan belum/tidak hidup. */
        @Volatile var playerRef: Player? = null
            private set

        /** True selama layanan pemutar hidup di proses ini. Otomatis false lagi kalau proses dimatikan sistem. */
        @Volatile var alive: Boolean = false
            private set

        @Volatile private var instance: PlaybackService? = null

        /** Nyalakan / matikan gema lembut (dipakai gaya suara "Slowed + reverb"). Aman dipanggil kapan saja. */
        fun setReverb(on: Boolean) {
            val svc = instance ?: return
            svc.mainHandler.post { svc.applyReverb(on) }
        }
    }

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var exo: ExoPlayer? = null
    private var reverb: android.media.audiofx.EnvironmentalReverb? = null
    private var reverbWanted = false

    private fun releaseReverb() {
        runCatching { reverb?.enabled = false }
        runCatching { reverb?.release() }
        reverb = null
    }

    /** Pasang gema pada sesi audio pemutar. Kalau perangkat tidak mendukung, diam-diam dilewati (suara tetap diputar). */
    private fun applyReverb(on: Boolean = reverbWanted) {
        reverbWanted = on
        releaseReverb()
        val sessionId = exo?.audioSessionId ?: return
        if (!on || sessionId == C.AUDIO_SESSION_ID_UNSET) return
        runCatching {
            reverb = android.media.audiofx.EnvironmentalReverb(0, sessionId).apply {
                roomLevel = -1500
                roomHFLevel = -1000
                decayTime = 2800
                decayHFRatio = 700
                reflectionsLevel = -1200
                reflectionsDelay = 15
                reverbLevel = -300
                reverbDelay = 40
                diffusion = 1000
                density = 1000
                enabled = true
            }
        }.onFailure { Log.w("NexrifyPlaybackService", "Reverb tidak tersedia: ${it.message}") }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // 1. Ensure Notification Channel exists to prevent Android ANR / crash on startForeground
        createNotificationChannel()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)

        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setMp4ExtractorFlags(androidx.media3.extractor.mp4.Mp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS)
            .setFragmentedMp4ExtractorFlags(androidx.media3.extractor.mp4.FragmentedMp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS)

        // DefaultDataSource memilih sendiri sumbernya: http untuk streaming dan file untuk lagu hasil unduhan offline.
        // Sebelumnya hanya http, jadi lagu offline (file://) gagal diputar.
        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(this, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)

        // Ultra-low latency buffering with robust fMP4/AAC headroom
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 30_000,
                /* maxBufferMs = */ 90_000,
                /* bufferForPlaybackMs = */ 800,
                /* bufferForPlaybackAfterRebufferMs = */ 2_500
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            // Tanpa ini CPU dan Wi-Fi ikut tidur saat layar mati, streaming putus, lagu berhenti tiba-tiba.
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        exo = exoPlayer
        instance = this
        exoPlayer.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (reverbWanted) applyReverb()
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("NexrifyPlaybackService", "ExoPlayer Error: ${error.message}", error)
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                updateHomeWidget(exoPlayer)
            }

            override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                updateHomeWidget(exoPlayer)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateHomeWidget(exoPlayer)
                reportListening(isPlaying)
            }
        })

        val forwardingPlayer = object : androidx.media3.common.ForwardingPlayer(exoPlayer) {
            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
                    else -> super.isCommandAvailable(command)
                }
            }

            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
            }

            override fun seekToNext() {
                sendBroadcast(Intent(ACTION_NEXT).setPackage(packageName))
            }

            override fun seekToNextMediaItem() {
                sendBroadcast(Intent(ACTION_NEXT).setPackage(packageName))
            }

            override fun seekToPrevious() {
                sendBroadcast(Intent(ACTION_PREV).setPackage(packageName))
            }

            override fun seekToPreviousMediaItem() {
                sendBroadcast(Intent(ACTION_PREV).setPackage(packageName))
            }
        }

        player = forwardingPlayer
        playerRef = forwardingPlayer
        alive = true

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(CHANNEL_ID)
            .setChannelName(R.string.app_name)
            .setNotificationId(NOTIFICATION_ID)
            .build()
        notificationProvider.setSmallIcon(R.drawable.ic_notification)

        setMediaNotificationProvider(PersistentNotificationProvider(notificationProvider))

        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setSessionActivity(pendingIntent)
            .setCallback(sessionCallback)
            .setCustomLayout(controlsLayout())
            .build()

        // Ikon hati di notifikasi ikut berubah saat lagu disuka atau batal disuka.
        favoriteJob = serviceScope.launch {
            kotlinx.coroutines.flow.combine(
                PlaybackBridge.isFavorite,
                id.nexrify.music.data.settings.SettingsManager.getInstance().capsuleEnabled
            ) { liked, capsuleOn -> liked to capsuleOn }.collect { (liked, capsuleOn) ->
                mediaSession?.setCustomLayout(controlsLayout(liked, capsuleOn))
            }
        }
        // Ikon suka di widget besar ikut berubah.
        widgetLikeJob = serviceScope.launch {
            PlaybackBridge.isFavorite.collect { liked ->
                NexrifyWidgetProvider.saveLiked(this@PlaybackService, liked)
                NexrifyWidgetProvider.refresh(this@PlaybackService)
            }
        }

        // Panel musik kapsul di atas layar (butuh izin tampil di atas aplikasi lain, diatur dari Pengaturan).
        capsule = id.nexrify.music.capsule.MusicCapsuleController(this, forwardingPlayer).also { it.start() }
    }

    private fun updateHomeWidget(player: ExoPlayer) {
        val metadata = player.mediaMetadata
        val title = metadata.title?.toString().orEmpty()
        val artist = metadata.artist?.toString().orEmpty()
        val artworkUrl = metadata.artworkUri?.toString().orEmpty()
        NexrifyWidgetProvider.save(this, title, artist, player.isPlaying)
        NexrifyWidgetProvider.refresh(this)

        // RemoteViews cannot load a network image itself. Fetch artwork off the main thread
        // and only apply it if it still belongs to the current track.
        if (artworkUrl.isNotBlank() && (artworkUrl != latestArtworkUrl || title != latestArtworkTitle)) {
            latestArtworkUrl = artworkUrl
            latestArtworkTitle = title
            artworkExecutor.execute {
                var connection: HttpURLConnection? = null
                try {
                    val bitmap = if (artworkUrl.startsWith("file:")) {
                        BitmapFactory.decodeFile(android.net.Uri.parse(artworkUrl).path)
                    } else {
                        connection = (URL(artworkUrl).openConnection() as HttpURLConnection).apply {
                            connectTimeout = 8_000
                            readTimeout = 8_000
                            instanceFollowRedirects = true
                        }
                        connection.inputStream.use { BitmapFactory.decodeStream(it) }
                    }
                    if (bitmap != null && latestArtworkUrl == artworkUrl) {
                        NexrifyWidgetProvider.updateArtwork(this, bitmap)
                    }
                } catch (e: Exception) {
                    Log.w("NexrifyPlaybackService", "Unable to load widget artwork", e)
                } finally {
                    connection?.disconnect()
                }
            }
        }
    }

    /** Saat musik diputar, notifikasi selalu dipasang sebagai foreground (ongoing) supaya tidak bisa dihapus. */
    @OptIn(UnstableApi::class)
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        // Paksa foreground hanya kalau memang ada lagu yang diputar/dimuat. Kalau antrean kosong atau player idle,
        // Media3 tidak memasang notifikasi, dan startForegroundService tanpa startForeground = crash di Android 8-11.
        val p = session.player
        val active = p.playWhenReady && p.mediaItemCount > 0 &&
            (p.playbackState == Player.STATE_READY || p.playbackState == Player.STATE_BUFFERING)
        super.onUpdateNotification(session, startInForegroundRequired || active)
    }

    /**
     * Android mewajibkan startForeground() maksimal 5 detik setelah startForegroundService(). Kalau Media3 belum
     * sempat (atau memutuskan tidak memasang notifikasi), aplikasi crash. Jadi begitu service dimulai, langsung pasang
     * notifikasi sementara dengan ID yang sama (nanti diganti notifikasi media), dan lepas lagi kalau tidak ada yang diputar.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runCatching {
            createNotificationChannel()
            val n = androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.app_name))
                .setOngoing(true)
                .setSilent(true)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
                .setCategory(androidx.core.app.NotificationCompat.CATEGORY_TRANSPORT)
                .build()
            startForeground(NOTIFICATION_ID, n)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                val pl = player
                // Jangan lepas status foreground kalau lagu baru masih disiapkan (cari URL bisa > 4 detik)
                // atau pengguna sudah menekan putar; kalau dilepas, sistem bisa mematikan proses dan lagu mati sendiri.
                val busy = PlaybackBridge.isLoading.value || pl?.playWhenReady == true
                val idle = !busy && (pl == null || pl.mediaItemCount == 0 ||
                    pl.playbackState == Player.STATE_IDLE || pl.playbackState == Player.STATE_ENDED)
                if (alive && idle) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }, 4_000)
        }.onFailure { Log.w("NexrifyPlaybackService", "startForeground gagal: ${it.message}") }
        return super.onStartCommand(intent, flags, startId)
    }

    /** Keep the media session alive when the user swipes the app task away during playback. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (player?.isPlaying != true) super.onTaskRemoved(rootIntent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = currentStrings().playbackChannelDesc
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        alive = false
        playerRef = null
        NexrifyWidgetProvider.save(this, "", "", false)
        NexrifyWidgetProvider.refresh(this)
        capsule?.destroy()
        capsule = null
        favoriteJob?.cancel()
        widgetLikeJob?.cancel()
        listenJob?.cancel()
        runCatching { id.nexrify.music.data.chat.ChatManager.getInstance().listenReport("stop") }
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        player = null
        releaseReverb()
        exo = null
        instance = null
        artworkExecutor.shutdownNow()
        super.onDestroy()
    }
}

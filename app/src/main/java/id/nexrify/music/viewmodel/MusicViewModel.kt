package id.nexrify.music.viewmodel

import id.nexrify.music.service.PlaybackBridge

import android.app.Application
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import id.nexrify.music.data.model.Track
import id.nexrify.music.data.remote.MusicRepository
import id.nexrify.music.service.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository()
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _isLoadingTrack = MutableStateFlow(false)
    val isLoadingTrack = _isLoadingTrack.asStateFlow()

    private val _playbackPosition = MutableStateFlow(0L)
    val playbackPosition = _playbackPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration = _duration.asStateFlow()

    private val _isPlayerExpanded = MutableStateFlow(false)
    val isPlayerExpanded = _isPlayerExpanded.asStateFlow()

    private val _homeSections = MutableStateFlow<List<id.nexrify.music.data.model.HomeSection>>(emptyList())
    val homeSections = _homeSections.asStateFlow()

    private val _trendingTracks = MutableStateFlow<List<Track>>(emptyList())
    val trendingTracks = _trendingTracks.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Track>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions = _suggestions.asStateFlow()

    private val favPrefs = getApplication<android.app.Application>().getSharedPreferences("nexrify_favorites", android.content.Context.MODE_PRIVATE)

    private val _favorites = MutableStateFlow<List<Track>>(loadStoredFavorites())
    val favorites = _favorites.asStateFlow()

    private fun loadStoredFavorites(): List<Track> {
        val jsonStr = favPrefs.getString("fav_list", null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(jsonStr)
            val list = mutableListOf<Track>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    Track(
                        id = obj.getString("id"),
                        url = obj.getString("url"),
                        title = obj.getString("title"),
                        artist = obj.getString("artist"),
                        thumbnailUrl = obj.getString("thumbnailUrl"),
                        durationSeconds = obj.optLong("durationSeconds", 0L),
                        isFavorite = true
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveStoredFavorites(list: List<Track>) {
        try {
            val arr = org.json.JSONArray()
            list.forEach { t ->
                val obj = org.json.JSONObject().apply {
                    put("id", t.id)
                    put("url", t.url)
                    put("title", t.title)
                    put("artist", t.artist)
                    put("thumbnailUrl", t.thumbnailUrl)
                    put("durationSeconds", t.durationSeconds)
                }
                arr.put(obj)
            }
            favPrefs.edit().putString("fav_list", arr.toString()).apply()
        } catch (e: Exception) {
            android.util.Log.e("NexrifyVM", "Save favorites error: ${e.message}")
        }
    }

    private val _isLoadingTrending = MutableStateFlow(false)
    val isLoadingTrending = _isLoadingTrending.asStateFlow()


    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle = _isShuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode = _repeatMode.asStateFlow()

    val authState = id.nexrify.music.data.auth.AuthManager.getInstance().authState

    val settingsManager = id.nexrify.music.data.settings.SettingsManager.getInstance()
    val themeMode = settingsManager.themeMode
    val themeStyle = settingsManager.themeStyle
    val audioQuality = settingsManager.audioQuality

    val audioOutputManager = id.nexrify.music.data.audio.AudioOutputManager.getInstance(getApplication())
    val audioDeviceState = audioOutputManager.deviceState
    fun openAudioOutputSettings() = audioOutputManager.openAudioSettings()

    val tasteManager = id.nexrify.music.data.recommendation.TasteManager.getInstance(getApplication())
    /** Streak api: 1 menit mendengarkan per hari menyalakan api. */
    val streakManager = id.nexrify.music.data.streak.StreakManager.getInstance(getApplication())
    private var lastStreakTick = 0L
    val recentlyPlayed = tasteManager.recentlyPlayed
    val onRepeatTracks = tasteManager.onRepeatTracks
    /** id lagu -> 1 suka / -1 tidak suka (tombol jempol di beranda). */
    val reactions = tasteManager.reactions

    /**
     * Suka / tidak suka dari beranda. Tidak suka: lagu langsung hilang dari rak dan jarang muncul lagi,
     * begitu juga lagu lain dari artis itu. Suka: lagu dan artisnya makin sering muncul di rak berikutnya.
     */
    fun reactToTrack(track: Track, reaction: Int) {
        val before = tasteManager.reactionOf(track.id)
        tasteManager.react(track, reaction)
        val now = tasteManager.reactionOf(track.id)
        val st = id.nexrify.music.i18n.currentStrings()
        if (now == -1) {
            _homeSections.value = _homeSections.value
                .map { it.copy(tracks = it.tracks.filter { t -> t.id != track.id }) }
                .filter { it.tracks.isNotEmpty() }
            _trendingTracks.value = _trendingTracks.value.filter { it.id != track.id }
            id.nexrify.music.ui.components.NexNotifier.info(st.reactDislikedMsg, st.reactTitle)
            if (_currentTrack.value?.id == track.id) skipNext()
        } else if (now == 1) {
            id.nexrify.music.ui.components.NexNotifier.success(st.reactLikedMsg, st.reactTitle)
            // Segarkan rak selera di latar supaya lagu serupa langsung muncul.
            viewModelScope.launch { refreshTasteShelves() }
        } else if (before != 0) {
            id.nexrify.music.ui.components.NexNotifier.info(st.reactClearedMsg, st.reactTitle)
        }
    }

    private suspend fun buildTasteShelves(): List<id.nexrify.music.data.model.HomeSection> {
        val st = id.nexrify.music.i18n.currentStrings()
        val shelves = mutableListOf<id.nexrify.music.data.model.HomeSection>()
        val liked = tasteManager.likedTracks()
        val likedIds = liked.mapTo(HashSet()) { it.id }
        if (liked.isNotEmpty()) {
            val seeds = liked.shuffled().take(2)
            val similar = seeds.flatMap { runCatching { repository.getRadioTracks(it.id) }.getOrDefault(emptyList()) }
                .filter { it.id !in likedIds }.distinctBy { it.id }
            val ranked = tasteManager.personalize(similar).take(14)
            if (ranked.isNotEmpty()) {
                shelves.add(id.nexrify.music.data.model.HomeSection(st.similarShelfTitle, st.similarShelfSub, ranked))
            }
        }
        val artists = tasteManager.favoriteArtists(4).shuffled().take(2)
        for (artist in artists) {
            val shelf = repository.getDiscoverShelf(artist, st.moreFromArtist(artist), st.moreFromArtistSub)
            if (shelf != null) {
                val clean = tasteManager.personalize(shelf.tracks).filter { it.id !in likedIds }
                if (clean.isNotEmpty()) shelves.add(shelf.copy(tracks = clean))
            }
        }
        return shelves
    }

    private suspend fun refreshTasteShelves() = addExtraShelves(withDiscover = false)

    /** Sisipkan rak selera (dan rak penemuan acak) tepat setelah rak pertama; rak lama jenis yang sama diganti. */
    private suspend fun addExtraShelves(withDiscover: Boolean) {
        val st = id.nexrify.music.i18n.currentStrings()
        val fresh = buildTasteShelves().toMutableList()
        if (withDiscover) {
            val q = DISCOVER_QUERIES.shuffled().first()
            repository.getDiscoverShelf(q.first, q.second, st.discoverSub)?.let { sh ->
                val ranked = tasteManager.personalize(sh.tracks)
                if (ranked.isNotEmpty()) fresh.add(sh.copy(tracks = ranked))
            }
        }
        if (fresh.isEmpty()) return
        val discoverTitles = DISCOVER_QUERIES.mapTo(HashSet()) { it.second }
        val kept = _homeSections.value.filterNot {
            it.title == st.similarShelfTitle || it.title.startsWith(st.moreFromArtistPrefix) ||
                (withDiscover && it.title in discoverTitles)
        }
        val insertAt = minOf(kept.size, if (kept.firstOrNull()?.title == "Lagi Sering Kamu Putar") 2 else 1)
        _homeSections.value = kept.toMutableList().also { it.addAll(insertAt, fresh) }
    }

    val searchHistoryManager = id.nexrify.music.data.search.SearchHistoryManager(application)
    val searchHistory = searchHistoryManager.history
    val topSearches = searchHistoryManager.topSearches
    fun removeSearchHistory(query: String) = searchHistoryManager.remove(query)
    fun clearSearchHistory() = searchHistoryManager.clear()

    private val _initialLoadDone = MutableStateFlow(false)
    /** True setelah katalog beranda selesai dimuat pertama kali (berhasil atau gagal). Dipakai layar loading. */
    val initialLoadDone = _initialLoadDone.asStateFlow()

    val playlistManager = id.nexrify.music.data.playlist.PlaylistManager.getInstance(getApplication())
    val playlists = playlistManager.playlists

    fun createPlaylist(name: String) = playlistManager.createPlaylist(name)
    fun deletePlaylist(id: String) = playlistManager.deletePlaylist(id)
    fun renamePlaylist(id: String, name: String) = playlistManager.renamePlaylist(id, name)
    fun setPlaylistCover(id: String, coverUrl: String) = playlistManager.setCover(id, coverUrl)
    fun setPlaylistShareId(id: String, shareId: String) = playlistManager.setShareId(id, shareId)
    fun importSharedPlaylist(p: id.nexrify.music.data.playlist.SharedPlaylist) = playlistManager.importPlaylist(p.name, p.tracks, p.coverUrl)
    fun saveCoverFromUri(id: String, uri: android.net.Uri): String? = playlistManager.saveCoverFromUri(id, uri)
    fun addTrackToPlaylist(id: String, track: Track) = playlistManager.addTrackToPlaylist(id, track)
    fun removeTrackFromPlaylist(id: String, trackId: String) = playlistManager.removeTrackFromPlaylist(id, trackId)

    // ---- Import playlist Spotify -> YouTube Music/Nexrify ----
    data class SpotifyImportState(
        val running: Boolean = false,
        val done: Int = 0,
        val total: Int = 0,
        val error: String? = null,
        val playlistId: String? = null,
        val playlistName: String? = null,
        val matchedCount: Int = 0,
        val notFound: List<String> = emptyList(),
        /** Info yang tampil selama proses: nama + sampul Spotify, lagu yang sedang dicocokkan, jumlah cocok sejauh ini. */
        val sourceName: String = "",
        val coverUrl: String = "",
        val current: String = "",
        val matchedSoFar: Int = 0,
        val retrying: Boolean = false
    )

    private val _spotifyImport = MutableStateFlow(SpotifyImportState())
    val spotifyImport = _spotifyImport.asStateFlow()
    private var spotifyImportJob: Job? = null

    fun importSpotifyPlaylist(link: String) {
        if (_spotifyImport.value.running) return
        _spotifyImport.value = SpotifyImportState(running = true)
        spotifyImportJob = viewModelScope.launch {
            try {
                val res = id.nexrify.music.data.importer.SpotifyImporter.import(
                    repository, link,
                    onMeta = { m ->
                        _spotifyImport.value = _spotifyImport.value.copy(sourceName = m.name, coverUrl = m.coverUrl, total = m.total)
                    },
                    onProgress = { d, t, cur, hit ->
                        _spotifyImport.value = _spotifyImport.value.copy(
                            done = d, total = t, current = cur, matchedSoFar = hit, retrying = cur.startsWith("↻")
                        )
                    }
                )
                if (res.matched.isEmpty()) {
                    _spotifyImport.value = SpotifyImportState(error = "Tidak ada lagu yang berhasil dicocokkan")
                    return@launch
                }
                // Sampul Spotify ikut dibawa; kalau kosong playlist memakai kolase lagu seperti biasa.
                val pl = playlistManager.createPlaylistWithTracks(res.playlistName, res.matched, res.coverUrl)
                _spotifyImport.value = SpotifyImportState(
                    playlistId = pl.id,
                    playlistName = pl.name,
                    coverUrl = pl.coverUrl,
                    sourceName = pl.name,
                    done = res.matched.size + res.notFound.size,
                    total = res.matched.size + res.notFound.size,
                    matchedCount = res.matched.size,
                    notFound = res.notFound.map { (it.artist + " - " + it.title).trim(' ', '-') }
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                _spotifyImport.value = SpotifyImportState()
                throw e
            } catch (e: id.nexrify.music.data.importer.SpotifyImporter.ImportException) {
                _spotifyImport.value = SpotifyImportState(error = e.message)
            } catch (e: Exception) {
                _spotifyImport.value = SpotifyImportState(error = "Gagal import: " + (e.message ?: "kesalahan jaringan"))
            }
        }
    }

    fun cancelSpotifyImport() { spotifyImportJob?.cancel() }
    fun resetSpotifyImport() { _spotifyImport.value = SpotifyImportState() }

    fun playPlaylist(playlist: id.nexrify.music.data.playlist.Playlist, startTrack: Track? = null, shuffle: Boolean = false) {
        if (playlist.tracks.isEmpty()) return
        val list = if (shuffle) playlist.tracks.shuffled() else playlist.tracks
        val initialTrack = startTrack ?: list.first()
        playTrack(initialTrack, list)
    }

    val lyricsRepository = id.nexrify.music.data.lyrics.LyricsRepository(getApplication())

    private val _currentLyrics = MutableStateFlow<id.nexrify.music.data.lyrics.LyricsData?>(null)
    val currentLyrics = _currentLyrics.asStateFlow()

    private val _isLoadingLyrics = MutableStateFlow(false)
    val isLoadingLyrics = _isLoadingLyrics.asStateFlow()

    private val playlistQueue = java.util.Collections.synchronizedList(mutableListOf<Track>())
    private var currentIndex = -1

    private val offline = id.nexrify.music.data.download.OfflineManager.getInstance()
    private val network = id.nexrify.music.data.network.NetworkMonitor.getInstance()
    val isOnline = network.online

    private var consecutiveErrors = 0
    private var forceNetworkOnce: String? = null

    private val _openOfflineTab = MutableStateFlow(false)
    val openOfflineTab = _openOfflineTab.asStateFlow()
    fun requestOfflineTab() { _openOfflineTab.value = true }
    fun consumeOfflineTab() { _openOfflineTab.value = false }

    /** Indeks lagu berikutnya yang sudah diunduh, dipakai saat tidak ada internet. -1 kalau tidak ada. */
    private fun nextOfflineIndex(): Int {
        val q = synchronized(playlistQueue) { playlistQueue.toList() }
        val playable = q.indices.filter { it != currentIndex && offline.isDownloaded(q[it].id) }
        if (playable.isEmpty()) return -1
        if (_isShuffle.value) return playable.random()
        playable.firstOrNull { it > currentIndex }?.let { return it }
        return if (_repeatMode.value == Player.REPEAT_MODE_ALL) playable.first() else -1
    }

    private fun prevOfflineIndex(): Int {
        val q = synchronized(playlistQueue) { playlistQueue.toList() }
        val playable = q.indices.filter { it != currentIndex && offline.isDownloaded(q[it].id) }
        if (playable.isEmpty()) return -1
        return playable.lastOrNull { it < currentIndex } ?: playable.last()
    }
    val offlineTracks = offline.offlineTracks
    val downloads = offline.downloads
    fun downloadTrack(track: Track) = offline.enqueue(track, settingsManager.downloadQuality.value)
    fun retryDownload(trackId: String) = offline.retry(trackId, settingsManager.downloadQuality.value)
    fun cancelDownload(trackId: String) = offline.cancel(trackId)
    fun deleteOffline(trackId: String) = offline.delete(trackId)
    fun deleteAllOffline() = offline.deleteAll()
    fun offlineThumb(trackId: String): java.io.File? = offline.localThumb(trackId)

    private val _queueFlow = MutableStateFlow<List<Track>>(emptyList())
    /** Salinan antrian untuk ditampilkan di UI. */
    val queue = _queueFlow.asStateFlow()
    private val _queueIndex = MutableStateFlow(-1)
    val queueIndex = _queueIndex.asStateFlow()

    private fun publishQueue() {
        _queueFlow.value = synchronized(playlistQueue) { playlistQueue.toList() }
        _queueIndex.value = currentIndex
    }

    fun playFromQueue(index: Int) {
        if (mubarBlocked()) return
        val track = synchronized(playlistQueue) { playlistQueue.getOrNull(index) } ?: return
        currentIndex = index
        playTrack(track)
    }

    fun removeFromQueue(index: Int) {
        synchronized(playlistQueue) {
            if (index !in playlistQueue.indices || index == currentIndex) return
            playlistQueue.removeAt(index)
            if (index < currentIndex) currentIndex--
        }
        publishQueue()
        saveSnapshot()
    }

    fun moveInQueue(from: Int, to: Int) {
        synchronized(playlistQueue) {
            if (from !in playlistQueue.indices || to !in playlistQueue.indices || from == to) return
            val item = playlistQueue.removeAt(from)
            playlistQueue.add(to, item)
            currentIndex = when {
                currentIndex == from -> to
                from < currentIndex && to >= currentIndex -> currentIndex - 1
                from > currentIndex && to <= currentIndex -> currentIndex + 1
                else -> currentIndex
            }
        }
        publishQueue()
        saveSnapshot()
    }

    fun clearUpcoming() {
        synchronized(playlistQueue) {
            if (currentIndex < 0 || currentIndex >= playlistQueue.size) return
            while (playlistQueue.size > currentIndex + 1) playlistQueue.removeAt(playlistQueue.size - 1)
        }
        publishQueue()
        saveSnapshot()
    }

    /** Sisipkan lagu tepat setelah lagu yang sedang diputar. Kalau belum ada yang diputar, langsung diputar. */
    fun playNext(track: Track) {
        if (mubarBlocked()) return
        val current = _currentTrack.value
        if (current == null || synchronized(playlistQueue) { playlistQueue.isEmpty() }) {
            playTrackFromSearch(track)
            return
        }
        if (current.id == track.id) return
        synchronized(playlistQueue) {
            val existing = playlistQueue.indexOfFirst { it.id == track.id }
            if (existing >= 0) {
                playlistQueue.removeAt(existing)
                if (existing < currentIndex) currentIndex--
            }
            playlistQueue.add((currentIndex + 1).coerceIn(0, playlistQueue.size), track.copy(streamUrl = null))
        }
        publishQueue()
        saveSnapshot()
    }

    /** Tambahkan lagu ke akhir antrian (dilewati kalau sudah ada). */
    fun addToQueue(track: Track) {
        if (_currentTrack.value == null || synchronized(playlistQueue) { playlistQueue.isEmpty() }) {
            playTrackFromSearch(track)
            return
        }
        synchronized(playlistQueue) {
            if (playlistQueue.none { it.id == track.id }) playlistQueue.add(track.copy(streamUrl = null))
        }
        publishQueue()
        saveSnapshot()
    }

    // Lanjut dari terakhir: antrian dan posisi dipulihkan dari penyimpanan, lagu baru diputar setelah pengguna menekan play.
    private val playbackStore = id.nexrify.music.data.playback.PlaybackStateStore(application)
    private var restoredPending = false
    private var restoredPositionMs = 0L
    private var progressJob: Job? = null
    private var suggestJob: Job? = null
    private var playJob: Job? = null
    private var lyricsJob: Job? = null
    private var lyricsRefreshedFor: String? = null

    /** Muat lirik untuk [track]. [forceRefresh] melewati cache (dipakai tombol "Cari lirik lagi"). */
    private fun loadLyrics(track: Track, forceRefresh: Boolean) {
        lyricsJob?.cancel()
        lyricsJob = viewModelScope.launch(Dispatchers.IO) {
            _isLoadingLyrics.value = true
            if (!forceRefresh) _currentLyrics.value = null
            try {
                val data = lyricsRepository.getLyrics(
                    trackId = track.id,
                    title = track.title,
                    artist = track.artist,
                    durationSeconds = track.durationSeconds,
                    autoTranslate = false,
                    forceRefresh = forceRefresh
                )
                // Lagu sudah ganti selagi lirik dimuat: buang hasil lama.
                if (_currentTrack.value?.id != track.id) return@launch
                _currentLyrics.value = data
                _isLoadingLyrics.value = false

                if (data != null && data.lines.isNotEmpty() && settingsManager.autoTranslate.value) {
                    val translated = lyricsRepository.translateLyrics(data)
                    if (_currentTrack.value?.id == track.id) _currentLyrics.value = translated
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("NexrifyVM", "Lyrics load error: ${e.message}")
                if (_currentTrack.value?.id == track.id) {
                    _currentLyrics.value = null
                    _isLoadingLyrics.value = false
                }
            } finally {
                // Pekerjaan ini dibatalkan oleh lagu lain? Biarkan pekerjaan baru yang mengatur indikator.
                if (_currentTrack.value?.id == track.id) _isLoadingLyrics.value = false
            }
        }
    }

    /** Tombol "Cari lirik lagi": abaikan cache dan cari ulang untuk lagu yang sedang diputar. */
    fun retryLyrics() {
        val track = _currentTrack.value ?: return
        loadLyrics(track, forceRefresh = true)
    }
    private var searchJob: Job? = null
    private var pendingTrackToPlay: Track? = null
    private var hasRetriedFallback = false
    private var trackStartTime = 0L
    private var lastRecordedPlaybackStart = -1L

    private val mediaActionReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            when (intent?.action) {
                PlaybackService.ACTION_NEXT -> skipNext()
                PlaybackService.ACTION_PREV -> skipPrevious()
            }
        }
    }

    private fun restoreSnapshot() {
        val snap = playbackStore.load() ?: return
        playlistQueue.clear()
        playlistQueue.addAll(snap.queue)
        currentIndex = snap.index.coerceIn(0, snap.queue.lastIndex)
        val track = snap.queue[currentIndex].let { t -> t.copy(isFavorite = _favorites.value.any { it.id == t.id }) }
        _currentTrack.value = track
        _playbackPosition.value = snap.positionMs
        _duration.value = if (track.durationSeconds > 0) track.durationSeconds * 1000L else 0L
        _isShuffle.value = snap.shuffle
        _repeatMode.value = snap.repeatMode
        restoredPositionMs = snap.positionMs
        restoredPending = true
        publishQueue()
    }

    private fun saveSnapshot() {
        val track = _currentTrack.value ?: return
        val queue = synchronized(playlistQueue) { playlistQueue.toList() }.ifEmpty { listOf(track) }
        val index = queue.indexOfFirst { it.id == track.id }.takeIf { it >= 0 } ?: currentIndex.coerceIn(0, queue.lastIndex)
        playbackStore.save(
            id.nexrify.music.data.playback.PlaybackStateStore.Snapshot(
                queue = queue,
                index = index,
                positionMs = if (restoredPending) restoredPositionMs else _playbackPosition.value,
                shuffle = _isShuffle.value,
                repeatMode = _repeatMode.value
            )
        )
    }

    private fun resumeRestored(startMs: Long = restoredPositionMs) {
        val track = _currentTrack.value ?: return
        restoredPending = false
        playTrack(track, startPositionMs = startMs)
    }

    /** Suka atau batal suka lagu yang sedang diputar (dipakai notifikasi dan kapsul). */
    fun toggleCurrentFavorite() {
        _currentTrack.value?.let { toggleFavorite(it) }
    }

    /** Muat ulang data dari penyimpanan setelah data akun diterapkan atau dihapus. */
    fun reloadFromStorage() {
        _favorites.value = loadStoredFavorites()
        tasteManager.reload()
        playlistManager.reload()
        searchHistoryManager.reload()
        settingsManager.reloadFromPrefs()
        id.nexrify.music.data.mailbox.MailboxManager.getInstance().reload()
    }

    init {
        id.nexrify.music.data.account.AccountSync.getInstance().addReloadHook { reloadFromStorage() }
        PlaybackBridge.onToggleFavorite = { toggleCurrentFavorite() }
        viewModelScope.launch { _isLoadingTrack.collect { PlaybackBridge.isLoading.value = it } }
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(_currentTrack, _favorites) { track, favs ->
                track != null && favs.any { it.id == track.id }
            }.collect { PlaybackBridge.isFavorite.value = it }
        }
        // Tombol Putar di widget saat pemutar belum hidup: lanjutkan lagu terakhir.
        viewModelScope.launch {
            PlaybackBridge.widgetPlay.collect { want ->
                if (!want) return@collect
                PlaybackBridge.widgetPlay.value = false
                var waited = 0
                while (mediaController == null && waited < 50) { delay(100L); waited++ }
                if (_currentTrack.value != null && !_isPlaying.value) togglePlayPause()
            }
        }
        restoreSnapshot()
        viewModelScope.launch {
            while (true) {
                delay(5_000L)
                if (_isPlaying.value) saveSnapshot()
            }
        }
        initializeMediaController()
        loadTrending()
        try {
            val filter = android.content.IntentFilter().apply {
                addAction(PlaybackService.ACTION_NEXT)
                addAction(PlaybackService.ACTION_PREV)
            }
            androidx.core.content.ContextCompat.registerReceiver(
                getApplication(),
                mediaActionReceiver,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (_: Exception) {}
    }

    private fun initializeMediaController() {
        val sessionToken = SessionToken(
            getApplication(),
            ComponentName(getApplication(), PlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(getApplication(), sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                pendingTrackToPlay?.let { pending ->
                    pendingTrackToPlay = null
                    playTrack(pending)
                }
                mediaController?.addListener(object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        syncTrackFromController()
                    }

                    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                        syncTrackFromController()
                    }

                    override fun onIsPlayingChanged(playing: Boolean) {
                        _isPlaying.value = playing
                        if (playing) consecutiveErrors = 0
                        saveSnapshot()
                        if (playing) {
                            _isLoadingTrack.value = false
                            startProgressTracking()
                            // Record a play when playback actually starts, not only when the song ends.
                            val startedAt = trackStartTime
                            val track = _currentTrack.value
                            if (track != null && startedAt > 0L && startedAt != lastRecordedPlaybackStart) {
                                lastRecordedPlaybackStart = startedAt
                                tasteManager.recordTrackPlayed(track)
                            }
                        } else {
                            stopProgressTracking()
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> {
                                _isLoadingTrack.value = true
                            }
                            Player.STATE_READY -> {
                                _isLoadingTrack.value = false
                                isSeeking = false
                                mediaController?.let { controller ->
                                    val d = controller.duration
                                    if (d > 0) {
                                        _duration.value = d
                                        val currentLrc = _currentLyrics.value
                                        val currentTrk = _currentTrack.value
                                        if (currentTrk != null && (currentLrc == null || !currentLrc.isSynced) && lyricsRefreshedFor != currentTrk.id) {
                                            val realDurSec = d / 1000L
                                            if (realDurSec > 0 && kotlin.math.abs(realDurSec - currentTrk.durationSeconds) > 1) {
                                                // Durasi asli lagu berbeda dari data pencarian: cari lirik ber-timestamp sekali lagi dengan durasi yang benar.
                                                lyricsRefreshedFor = currentTrk.id
                                                lyricsJob?.cancel()
                                                lyricsJob = viewModelScope.launch(Dispatchers.IO) {
                                                    val refreshed = lyricsRepository.getLyrics(
                                                        trackId = currentTrk.id,
                                                        title = currentTrk.title,
                                                        artist = currentTrk.artist,
                                                        durationSeconds = realDurSec,
                                                        autoTranslate = false,
                                                        forceRefresh = true
                                                    )
                                                    if (refreshed != null && _currentTrack.value?.id == currentTrk.id) {
                                                        _currentLyrics.value = if (settingsManager.autoTranslate.value) lyricsRepository.translateLyrics(refreshed) else refreshed
                                                    }
                                                    if (_currentTrack.value?.id == currentTrk.id) _isLoadingLyrics.value = false
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Player.STATE_ENDED -> {
                                _isLoadingTrack.value = false
                                // Di Musik Bareng giliran berikutnya diatur server, jangan lompat ke lagu antrian.
                                if (!mubarLocked) skipNext()
                            }
                            Player.STATE_IDLE -> {
                                _isLoadingTrack.value = false
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("NexrifyMusicViewModel", "Player error: ${error.message}", error)
                        consecutiveErrors++
                        val failing = _currentTrack.value
                        // Terlalu banyak gagal beruntun: berhenti, jangan loncat terus dari lagu ke lagu.
                        if (consecutiveErrors >= 4) {
                            consecutiveErrors = 0
                            _isLoadingTrack.value = false
                            _isPlaying.value = false
                            mediaController?.pause()
                            return
                        }
                        // Lagu hasil unduhan bermasalah: jangan loncat ke lagu lain.
                        // Kalau ada internet putar lewat streaming, kalau tidak ada berhenti saja.
                        if (failing != null && failing.streamUrl?.startsWith("file:") == true) {
                            _isLoadingTrack.value = false
                            _isPlaying.value = false
                            if (network.isOnline()) {
                                forceNetworkOnce = failing.id
                                playTrack(failing)
                            } else {
                                mediaController?.pause()
                            }
                            return
                        }
                        // Streaming gagal karena tidak ada internet: pindah ke lagu offline kalau ada, bukan lagu streaming lain.
                        if (!network.isOnline()) {
                            _isLoadingTrack.value = false
                            _isPlaying.value = false
                            val next = nextOfflineIndex()
                            if (next >= 0) {
                                currentIndex = next
                                playTrack(playlistQueue[next])
                            } else {
                                mediaController?.pause()
                            }
                            return
                        }
                        val track = _currentTrack.value
                        if (track != null && track.streamUrl != null && !hasRetriedFallback) {
                            hasRetriedFallback = true
                            viewModelScope.launch(Dispatchers.IO) {
                                repository.invalidateStream(track.url)
                                val fallback = repository.getFallbackStream(track.url, failedUrl = track.streamUrl)
                                val fallbackUrl = fallback?.url
                                if (fallbackUrl != null && fallbackUrl != track.streamUrl) {
                                    val fallbackMime = fallback?.mimeType
                                    val fallbackTrack = track.copy(streamUrl = fallbackUrl, mimeType = fallbackMime)
                                    withContext(Dispatchers.Main) {
                                        _currentTrack.value = fallbackTrack
                                        mediaController?.let { ctrl ->
                                            val meta = MediaMetadata.Builder()
                                                .setTitle(fallbackTrack.title)
                                                .setArtist(fallbackTrack.artist)
                                                .setArtworkUri(Uri.parse(fallbackTrack.highResThumbnailUrl))
                                                .setDisplayTitle(fallbackTrack.title)
                                                .build()

                                            val item = MediaItem.Builder()
                                                .setMediaId(fallbackTrack.id)
                                                .setUri(fallbackUrl)
                                                .apply { if (fallbackMime != null) setMimeType(fallbackMime) }
                                                .setMediaMetadata(meta)
                                                .build()

                                            ctrl.setMediaItem(item)
                                            ctrl.prepare()
                                            ctrl.play()
                                        }
                                    }
                                    return@launch
                                }
                                withContext(Dispatchers.Main) {
                                    _isLoadingTrack.value = false
                                    _isPlaying.value = false
                                    skipNext()
                                }
                            }
                        } else {
                            hasRetriedFallback = false
                            _isLoadingTrack.value = false
                            _isPlaying.value = false
                            skipNext()
                        }
                    }
                })
                // The service can outlive the Activity after it is swiped from Recents.
                // Rehydrate UI state immediately from its live MediaSession instead of
                // assuming that an empty ViewModel means playback has stopped.
                syncTrackFromController()
                mediaController?.let { controller ->
                    _isPlaying.value = controller.isPlaying
                    _playbackPosition.value = controller.currentPosition.coerceAtLeast(0L)
                    if (controller.duration > 0L) _duration.value = controller.duration
                    if (controller.isPlaying) startProgressTracking()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun syncTrackFromController() {
        val controller = mediaController ?: return
        val item = controller.currentMediaItem ?: return
        val metadata = item.mediaMetadata
        val title = metadata.title?.toString().orEmpty()
        val artist = metadata.artist?.toString().orEmpty()
        if (title.isBlank() && artist.isBlank()) return
        val mediaId = item.mediaId.takeIf { it.isNotBlank() } ?: item.localConfiguration?.uri?.toString().orEmpty()
        val artwork = metadata.artworkUri?.toString().orEmpty()
        val existing = _currentTrack.value
        val recovered = if (existing != null && existing.id == mediaId) {
            existing.copy(
                title = title.ifBlank { existing.title },
                artist = artist.ifBlank { existing.artist },
                thumbnailUrl = artwork.ifBlank { existing.thumbnailUrl },
                streamUrl = item.localConfiguration?.uri?.toString() ?: existing.streamUrl,
                durationSeconds = if (controller.duration > 0L) controller.duration / 1000L else existing.durationSeconds
            )
        } else {
            Track(
                id = mediaId,
                url = mediaId,
                title = title.ifBlank { id.nexrify.music.i18n.currentStrings().unknownTrack },
                artist = artist.ifBlank { id.nexrify.music.i18n.currentStrings().unknownArtist },
                thumbnailUrl = artwork,
                durationSeconds = if (controller.duration > 0L) controller.duration / 1000L else 0L,
                streamUrl = item.localConfiguration?.uri?.toString(),
                isFavorite = _favorites.value.any { it.id == mediaId }
            )
        }
        restoredPending = false
        _currentTrack.value = recovered
        _isPlaying.value = controller.isPlaying
        _playbackPosition.value = controller.currentPosition.coerceAtLeast(0L)
        if (controller.duration > 0L) _duration.value = controller.duration
        if (controller.playbackState == Player.STATE_BUFFERING) _isLoadingTrack.value = true
        else if (controller.playbackState == Player.STATE_READY) _isLoadingTrack.value = false
    }

    private var isSeeking = false
    private var lastSeekTimestamp = 0L

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                if (isSeeking && (now - lastSeekTimestamp < 1200L)) {
                    // Grace period: allow ExoPlayer to complete async seek over network without bouncing back
                } else {
                    isSeeking = false
                    mediaController?.let { controller ->
                        val pos = controller.currentPosition.coerceAtLeast(0)
                        _playbackPosition.value = pos
                        val realDuration = controller.duration
                        if (realDuration > 0) {
                            _duration.value = realDuration
                        }
                    }
                }
                // Hitung lama mendengarkan nyata (hanya saat lagu benar-benar jalan) untuk streak.
                val tick = System.currentTimeMillis()
                if (_isPlaying.value && lastStreakTick > 0L) {
                    streakManager.addListening((tick - lastStreakTick).coerceIn(0L, 1_000L))
                }
                lastStreakTick = tick
                delay(250)
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
    }

    fun loadTrending(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _isLoadingTrending.value = true
            try {
            // Lagu yang sering dilewati tidak ikut ditampilkan lagi di beranda.
            // Lagu/artis yang di-dislike disaring, yang disukai naik, sisanya diacak halus tiap muat.
            val rawShelves = repository.getHomeShelves(forceRefresh)
                .map { it.copy(tracks = tasteManager.personalize(it.tracks)) }
                .filter { it.tracks.isNotEmpty() }
                .toMutableList()
            val onRepeat = tasteManager.onRepeatTracks.value
            if (onRepeat.isNotEmpty()) {
                rawShelves.add(0, id.nexrify.music.data.model.HomeSection(
                    title = "Lagi Sering Kamu Putar",
                    subtitle = "Berdasarkan selera musikmu",
                    tracks = onRepeat
                ))
            }
            _homeSections.value = rawShelves
            val tracks = if (rawShelves.isNotEmpty()) {
                rawShelves.flatMap { it.tracks }.distinctBy { it.id }
            } else {
                repository.getTrendingTracks()
            }
            _trendingTracks.value = tracks
            _isLoadingTrending.value = false
            _initialLoadDone.value = true

            // Rak selera + penemuan acak dimuat di belakang layar, supaya beranda utama tidak tertahan.
            launch { runCatching { addExtraShelves(withDiscover = true) } }

            // Pre-warm audio URLs for top 2 tracks in background with chosen quality
            launch(Dispatchers.IO) {
                delay(2000)
                tracks.take(2).forEach {
                    repository.getAudioStreamUrl(it.url, settingsManager.audioQuality.value)
                }
            }
            } catch (e: Exception) {
                Log.e("NexrifyVM", "Gagal memuat beranda", e)
            } finally {
                _isLoadingTrending.value = false
                _initialLoadDone.value = true
            }
        }
    }

    /**
     * Dipanggil tiap huruf diketik: saran muncul cepat, hasil lagunya ikut dicari langsung (tanpa tekan enter),
     * jadi daftar saran dan hasil tampil bersamaan.
     */
    fun loadSuggestions(query: String) {
        suggestJob?.cancel()
        searchJob?.cancel()
        if (query.isBlank()) {
            _suggestions.value = emptyList()
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }
        suggestJob = viewModelScope.launch {
            delay(150)
            _suggestions.value = repository.getSuggestions(query).take(6)
        }
        searchJob = viewModelScope.launch {
            delay(350)
            _isSearching.value = true
            try {
                _searchResults.value = repository.searchTracks(query)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("NexrifyVM", "Pencarian langsung gagal", e)
            } finally {
                _isSearching.value = false
            }
        }
    }

    /** Pencarian yang sedang tren: nama artis dari lagu trending di beranda. */
    fun trendingSearchTerms(): List<String> =
        _trendingTracks.value.map { it.artist.trim() }
            .filter { it.isNotBlank() && it.length <= 30 }
            .distinctBy { it.lowercase() }.take(8)

    /** Catat kata yang diketik ke riwayat (dipakai saat pengguna memutar lagu dari hasil pencarian). */
    fun rememberSearch(query: String) = searchHistoryManager.add(query)

    /** Pencarian yang diketik atau dipilih pengguna: dicatat ke riwayat. */
    fun searchAndRemember(query: String) {
        searchHistoryManager.add(query)
        search(query)
    }

    fun clearSearchResults() {
        searchJob?.cancel()
        _isSearching.value = false
        _suggestions.value = emptyList()
        _searchResults.value = emptyList()
    }

    fun search(query: String, forceRefresh: Boolean = false) {
        searchJob?.cancel()
        _suggestions.value = emptyList()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            try {
                val results = repository.searchTracks(query, forceRefresh)
                _searchResults.value = results

                // Pre-warm audio URLs for top 2 search results in background
                launch(Dispatchers.IO) {
                    delay(1500)
                    results.take(2).forEach {
                        repository.getAudioStreamUrl(it.url, settingsManager.audioQuality.value)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("NexrifyVM", "Pencarian gagal", e)
                _searchResults.value = emptyList()
            } finally {
                // Kalau dibatalkan karena pencarian baru, biarkan pencarian baru yang mengatur indikator.
                if (kotlinx.coroutines.currentCoroutineContext()[Job]?.isActive == true) _isSearching.value = false
            }
        }
    }

    fun playTrackFromSearch(track: Track) {
        if (mubarBlocked()) return
        playlistQueue.clear()
        playlistQueue.add(track)
        currentIndex = 0
        playTrack(track, playlistQueue)
    }

    fun playTrack(track: Track, newQueue: List<Track> = emptyList(), startPositionMs: Long = 0L) {
        // Tanpa internet hanya lagu yang sudah diunduh yang bisa diputar.
        if (!offline.isDownloaded(track.id) && !network.isOnline()) {
            val st = id.nexrify.music.i18n.currentStrings()
            id.nexrify.music.ui.components.NexNotifier.warning(st.offlineNotDownloaded, st.noteOfflineTitle)
            _isLoadingTrack.value = false
            return
        }
        playJob?.cancel()
        restoredPending = false
        playJob = viewModelScope.launch {
            _currentTrack.value = track
            _isLoadingTrack.value = true
            _playbackPosition.value = startPositionMs
            _duration.value = if (track.durationSeconds > 0) track.durationSeconds * 1000L else 0L

            synchronized(playlistQueue) {
                if (newQueue.isNotEmpty() && newQueue !== playlistQueue) {
                    val copy = newQueue.toList()
                    playlistQueue.clear()
                    playlistQueue.addAll(copy)
                    currentIndex = playlistQueue.indexOfFirst { it.id == track.id }
                    if (currentIndex == -1) {
                        // Lagu yang diputar tidak ada di antrian baru: jangan biarkan indeks -1.
                        playlistQueue.add(0, track)
                        currentIndex = 0
                    }
                } else if (currentIndex == -1 || playlistQueue.getOrNull(currentIndex)?.id != track.id) {
                    val idx = playlistQueue.indexOfFirst { it.id == track.id }
                    if (idx != -1) {
                        currentIndex = idx
                    } else if (playlistQueue.isEmpty()) {
                        playlistQueue.add(track)
                        currentIndex = 0
                    }
                }
            }

            publishQueue()
            saveSnapshot()

            // Automix Radio: If queue is small (<= 2 tracks, e.g. played from search), fetch 50 radio tracks!
            if (playlistQueue.size <= 2 && network.isOnline()) {
                launch(Dispatchers.IO) {
                    try {
                        val radioTracks = repository.getRadioTracks(track.id)
                        // Lagu sudah ganti selagi radio dimuat: jangan tempelkan radio lagu lama ke antrian baru.
                        if (radioTracks.isNotEmpty() && _currentTrack.value?.id == track.id) {
                            synchronized(playlistQueue) {
                                val existingIds = playlistQueue.map { it.id }.toSet()
                                playlistQueue.addAll(tasteManager.personalize(radioTracks).filter { it.id !in existingIds })
                            }
                            publishQueue()
                            val second = synchronized(playlistQueue) { playlistQueue.getOrNull(1) }
                            if (second != null) {
                                repository.getAudioStreamUrl(second.url, settingsManager.audioQuality.value)
                            }
                        }
                    } catch (e: Exception) {
                        Log.d("NexrifyVM", "Automix radio fetch: ${e.message}")
                    }
                }
            }

                // Lirik + terjemahan dimuat di latar belakang supaya lagu langsung bisa diputar.
                loadLyrics(track, forceRefresh = false)

                // Resolve direct audio stream URL via NewPipeExtractor with user chosen Audio Quality!
            hasRetriedFallback = false
            trackStartTime = System.currentTimeMillis()
            // Lagu yang sudah diunduh diputar dari file di aplikasi, tanpa internet.
            val localFile = if (forceNetworkOnce == track.id) null else offline.localFile(track.id)
            if (forceNetworkOnce == track.id) forceNetworkOnce = null
            val resolvedStream = if (localFile != null) {
                id.nexrify.music.data.remote.ResolvedStream(Uri.fromFile(localFile).toString(), offline.mimeOf(track.id))
            } else {
                repository.getAudioStream(track.url, settingsManager.audioQuality.value)
            }
            val bestThumbnail = if (localFile != null) {
                offline.localThumb(track.id)?.let { Uri.fromFile(it).toString() } ?: track.thumbnailUrl
            } else {
                repository.resolveBestThumbnail(track)
            }
            if (resolvedStream != null) {
                val updatedTrack = track.copy(
                    streamUrl = resolvedStream.url,
                    mimeType = resolvedStream.mimeType,
                    thumbnailUrl = bestThumbnail
                )
                _currentTrack.value = updatedTrack

                val controller = mediaController
                if (controller != null) {
                    val mediaMetadata = MediaMetadata.Builder()
                        .setTitle(updatedTrack.title)
                        .setArtist(updatedTrack.artist)
                        .setArtworkUri(Uri.parse(updatedTrack.highResThumbnailUrl))
                        .setDisplayTitle(updatedTrack.title)
                        .build()

                    val mediaItemBuilder = MediaItem.Builder()
                        .setMediaId(updatedTrack.id)
                        .setUri(resolvedStream.url)
                        .setMediaMetadata(mediaMetadata)

                    if (!resolvedStream.mimeType.isNullOrBlank()) {
                        mediaItemBuilder.setMimeType(resolvedStream.mimeType)
                    }

                    controller.setMediaItem(mediaItemBuilder.build(), startPositionMs)
                    controller.prepare()
                    controller.play()
                } else {
                    pendingTrackToPlay = updatedTrack
                }

                // Pre-fetch next track in background for instantaneous next play!
                if (playlistQueue.isNotEmpty() && currentIndex + 1 < playlistQueue.size) {
                    val nextTrack = playlistQueue[currentIndex + 1]
                    launch(Dispatchers.IO) {
                        repository.getAudioStreamUrl(nextTrack.url, settingsManager.audioQuality.value)
                    }
                }
            } else {
                _isLoadingTrack.value = false
                Log.e("NexrifyMusicViewModel", "Failed to resolve stream URL for ${track.title}")
                // Auto skip to next available song if this track cannot be resolved!
                if (playlistQueue.isNotEmpty() && currentIndex + 1 < playlistQueue.size) {
                    skipNext()
                }
            }
        }
    }

    fun togglePlayPause() {
        // Aplikasi baru dibuka lagi: lagu terakhir belum dimuat ke pemutar, mulai dari posisi tersimpan.
        if (restoredPending && mediaController?.currentMediaItem == null) {
            resumeRestored()
            return
        }
        mediaController?.let { controller ->
            if (controller.isPlaying) {
                controller.pause()
            } else {
                controller.play()
            }
        }
    }

    /** Jeda musik untuk pengingat Maghrib. True kalau tadinya sedang diputar. */
    fun pauseForPrayer(): Boolean {
        val controller = mediaController ?: return false
        val wasPlaying = controller.isPlaying || controller.playWhenReady
        if (wasPlaying) controller.pause()
        return wasPlaying
    }

    /** Lanjutkan musik setelah popup Maghrib ditutup. */
    fun resumeAfterPrayer() {
        mediaController?.play()
    }

    /** Hentikan musik saat pengguna keluar dari aplikasi lewat popup Maghrib. Tidak diputar lagi otomatis. */
    fun stopForExit() {
        mediaController?.pause()
        _isPlaying.value = false
    }

    fun getProgress(): Float {
        val d = if (_duration.value > 0) _duration.value else ((_currentTrack.value?.durationSeconds ?: 0) * 1000L)
        return if (d > 0) (_playbackPosition.value.toFloat() / d.toFloat()).coerceIn(0f, 1f) else 0f
    }

    fun seekTo(positionMs: Long) {
        if (mubarBlocked()) return
        val target = positionMs.coerceAtLeast(0L)
        if (restoredPending && mediaController?.currentMediaItem == null) {
            resumeRestored(target)
            return
        }
        _playbackPosition.value = target
        lastSeekTimestamp = System.currentTimeMillis()
        isSeeking = true
        mediaController?.let { controller ->
            controller.seekTo(target)
            if (!controller.isPlaying) {
                controller.play()
            }
        }
    }

    fun getPositionMs(): Long = _playbackPosition.value

    fun skipNext() {
        if (mubarBlocked()) return
        if (playlistQueue.isEmpty()) return
        val current = _currentTrack.value
        if (current != null && trackStartTime > 0L) {
            val playedSec = (System.currentTimeMillis() - trackStartTime) / 1000L
            // Putaran lagu sudah dicatat saat pemutaran dimulai (onIsPlayingChanged); di sini cukup catat skip.
            if (playedSec in 1..14) {
                tasteManager.recordTrackSkipped(current)
            }
        }
        if (_repeatMode.value == Player.REPEAT_MODE_ONE) {
            _currentTrack.value?.let { playTrack(it) }
            return
        }
        val nextIndex = if (!network.isOnline()) {
            nextOfflineIndex()
        } else if (_isShuffle.value) {
            // Acak tapi jangan memilih lagu yang sedang diputar.
            val others = (0 until playlistQueue.size).filter { it != currentIndex }
            if (others.isNotEmpty()) others.random()
            else if (_repeatMode.value == Player.REPEAT_MODE_ALL) 0 else -1
        } else if (currentIndex + 1 < playlistQueue.size) {
            currentIndex + 1
        } else if (_repeatMode.value == Player.REPEAT_MODE_ALL) {
            0
        } else {
            -1
        }

        if (nextIndex in 0 until playlistQueue.size) {
            currentIndex = nextIndex
            playTrack(playlistQueue[nextIndex])

            // When nearing the end of the queue, auto-replenish with more radio tracks!
            if (playlistQueue.size - nextIndex <= 3 && playlistQueue.isNotEmpty() && network.isOnline()) {
                val seed = playlistQueue[nextIndex]
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val freshRadio = repository.getRadioTracks(seed.id)
                        if (_currentTrack.value?.id == seed.id) {
                            synchronized(playlistQueue) {
                                val existingIds = playlistQueue.map { it.id }.toSet()
                                playlistQueue.addAll(tasteManager.personalize(freshRadio).filter { it.id !in existingIds })
                            }
                            publishQueue()
                        }
                    } catch (_: Exception) {}
                }
            }
        } else {
            _isPlaying.value = false
            mediaController?.pause()
        }
    }

    fun skipPrevious() {
        if (mubarBlocked()) return
        if (playlistQueue.isEmpty()) return
        val prevIndex = if (!network.isOnline()) {
            prevOfflineIndex()
        } else if (currentIndex - 1 >= 0) {
            currentIndex - 1
        } else {
            playlistQueue.size - 1
        }
        if (prevIndex < 0) return
        currentIndex = prevIndex
        playTrack(playlistQueue[prevIndex])
    }

    fun toggleShuffle() {
        _isShuffle.value = !_isShuffle.value
        saveSnapshot()
    }

    fun toggleRepeat() {
        val next = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        _repeatMode.value = next
        saveSnapshot()
        mediaController?.repeatMode = if (next == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        if (next == Player.REPEAT_MODE_ONE) {
            _currentTrack.value?.let { tasteManager.recordTrackRepeated(it) }
        }
    }

    fun toggleFavorite(track: Track) {
        val currentFavs = _favorites.value.toMutableList()
        val exists = currentFavs.any { it.id == track.id }
        if (exists) {
            currentFavs.removeAll { it.id == track.id }
            tasteManager.recordTrackUnliked(track)
        } else {
            currentFavs.add(track.copy(isFavorite = true))
            tasteManager.recordTrackLiked(track)
        }
        _favorites.value = currentFavs
        saveStoredFavorites(currentFavs)
        val noteStrings = id.nexrify.music.i18n.currentStrings()
        id.nexrify.music.ui.components.NexNotifier.success(
            if (exists) noteStrings.favRemoved(track.title) else noteStrings.favAdded(track.title),
            if (exists) noteStrings.favTitleRemoved else noteStrings.favTitleAdded
        )

        if (_currentTrack.value?.id == track.id) {
            _currentTrack.value = _currentTrack.value?.copy(isFavorite = !exists)
        }
    }

    // ---- Timer tidur & kecepatan putar ----
    private var sleepJob: kotlinx.coroutines.Job? = null
    /** Sisa waktu timer tidur dalam detik, null kalau mati. -1 berarti "sampai lagu ini selesai". */
    private val _sleepRemainingSec = MutableStateFlow<Int?>(null)
    val sleepRemainingSec = _sleepRemainingSec.asStateFlow()
    private var sleepEndOfTrackId: String? = null

    fun startSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        sleepEndOfTrackId = null
        var left = minutes * 60
        _sleepRemainingSec.value = left
        sleepJob = viewModelScope.launch {
            while (left > 0) {
                kotlinx.coroutines.delay(1000L)
                left -= 1
                _sleepRemainingSec.value = left
            }
            finishSleepTimer()
        }
    }

    fun startSleepUntilEndOfTrack() {
        sleepJob?.cancel()
        sleepEndOfTrackId = _currentTrack.value?.id
        _sleepRemainingSec.value = -1
        sleepJob = viewModelScope.launch {
            // Pantau sampai lagu berganti atau selesai, lalu jeda.
            while (_currentTrack.value?.id == sleepEndOfTrackId) {
                kotlinx.coroutines.delay(500L)
            }
            finishSleepTimer()
        }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        sleepEndOfTrackId = null
        _sleepRemainingSec.value = null
    }

    private fun finishSleepTimer() {
        sleepJob = null
        sleepEndOfTrackId = null
        _sleepRemainingSec.value = null
        mediaController?.pause()
        val st = id.nexrify.music.i18n.currentStrings()
        id.nexrify.music.ui.components.NexNotifier.info(st.sleepFired, st.noteSleepTitle)
    }

    private val _playbackSpeed = MutableStateFlow(1f)
    val playbackSpeed = _playbackSpeed.asStateFlow()

    /* ---------------- Musik Bareng: kontrol pemutar dikunci selama di dalam ruangan ---------------- */

    @Volatile var mubarLocked: Boolean = false
        private set

    fun setMubarLocked(on: Boolean) { mubarLocked = on }

    private var lastMubarToast = 0L

    private fun mubarBlocked(): Boolean {
        if (!mubarLocked) return false
        val now = System.currentTimeMillis()
        if (now - lastMubarToast > 4_000L) {
            lastMubarToast = now
            val st = id.nexrify.music.i18n.currentStrings()
            id.nexrify.music.ui.components.NexNotifier.info(st.mubarControlsLocked, st.mubarTitle)
        }
        return true
    }

    /** Dipanggil sinkronisasi Musik Bareng: loncat ke posisi bersama tanpa terhalang kunci. */
    fun mubarSeek(positionMs: Long) {
        val target = positionMs.coerceAtLeast(0L)
        _playbackPosition.value = target
        mediaController?.let { c ->
            c.seekTo(target)
            if (!c.isPlaying) c.play()
        }
    }

    /** Lagu dihentikan oleh pemegang giliran: berhenti, kontrol tetap terkunci. */
    fun mubarPause() { mediaController?.pause() }

    /** Ruangan bubar / keluar: lagu bersama berhenti dan kontrol dibuka lagi. */
    fun mubarStop() {
        mubarLocked = false
        mediaController?.pause()
    }

    private val _voiceStyle = MutableStateFlow(VoiceStyle.NORMAL)
    val voiceStyle = _voiceStyle.asStateFlow()

    /** Gaya suara: mengatur kecepatan, nada, dan gema sekaligus. Kembali ke Normal tiap aplikasi dibuka ulang, sama seperti kecepatan. */
    fun setVoiceStyle(style: VoiceStyle) {
        _voiceStyle.value = style
        _playbackSpeed.value = style.speed
        mediaController?.playbackParameters = androidx.media3.common.PlaybackParameters(style.speed, style.pitch)
        id.nexrify.music.service.PlaybackService.setReverb(style.reverb)
    }

    fun setPlaybackSpeed(speed: Float) {
        // Memilih kecepatan manual berarti keluar dari gaya suara: nada dikembalikan normal dan gema dimatikan.
        if (_voiceStyle.value != VoiceStyle.NORMAL) {
            _voiceStyle.value = VoiceStyle.NORMAL
            id.nexrify.music.service.PlaybackService.setReverb(false)
        }
        _playbackSpeed.value = speed
        mediaController?.playbackParameters = androidx.media3.common.PlaybackParameters(speed, 1f)
    }

    fun expandPlayer(expand: Boolean) {
        _isPlayerExpanded.value = expand
    }



    fun logout() {
        id.nexrify.music.data.auth.AuthManager.getInstance().logout()
    }

    /**
     * Keluar dari akun Nexrify: hentikan musik yang sedang diputar dan kosongkan semua status pemutar
     * (antrian, lagu aktif, snapshot, timer tidur, widget) supaya tidak terbawa ke akun berikutnya.
     * Dipanggil sebelum data akun dihapus dari perangkat.
     */
    fun resetSession() {
        playJob?.cancel()
        sleepJob?.cancel()
        sleepEndOfTrackId = null
        _sleepRemainingSec.value = null
        stopProgressTracking()
        mediaController?.let { c ->
            runCatching {
                c.pause()
                c.stop()
                c.clearMediaItems()
                c.playbackParameters = androidx.media3.common.PlaybackParameters(1f, 1f)
                id.nexrify.music.service.PlaybackService.setReverb(false)
            }
        }
        synchronized(playlistQueue) { playlistQueue.clear() }
        currentIndex = -1
        restoredPending = false
        restoredPositionMs = 0L
        _currentTrack.value = null
        _isPlaying.value = false
        _isLoadingTrack.value = false
        _playbackPosition.value = 0L
        _duration.value = 0L
        _isPlayerExpanded.value = false
        _isShuffle.value = false
        _repeatMode.value = Player.REPEAT_MODE_OFF
        _playbackSpeed.value = 1f
        _voiceStyle.value = VoiceStyle.NORMAL
        _currentLyrics.value = null
        _isLoadingLyrics.value = false
        _searchResults.value = emptyList()
        _suggestions.value = emptyList()
        publishQueue()
        playbackStore.clear()
        PlaybackBridge.isFavorite.value = false
        PlaybackBridge.isLoading.value = false
        id.nexrify.music.NexrifyWidgetProvider.reset(getApplication<android.app.Application>())
    }

    /** Muat ulang beranda untuk akun yang baru masuk, bukan sisa data akun sebelumnya. */
    fun refreshAfterLogin() {
        _homeSections.value = emptyList()
        loadTrending(forceRefresh = true)
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<android.app.Application>().unregisterReceiver(mediaActionReceiver)
        } catch (_: Exception) {}
        saveSnapshot()
        PlaybackBridge.onToggleFavorite = null
        stopProgressTracking()
        suggestJob?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }

    private val DISCOVER_QUERIES = listOf(
        "lagu galau indonesia terbaru" to "Teman Galau",
        "lagu santai sore hari" to "Santai Sore",
        "dj viral tiktok slow bass" to "Lagi Viral",
        "lagu semangat pagi" to "Semangat Pagi",
        "lagu indie indonesia" to "Indie Pilihan",
        "lagu pop barat terbaru" to "Pop Barat Terbaru",
        "lagu akustik cafe" to "Akustik Cafe",
        "lagu jawa campursari terbaru" to "Campursari",
        "lagu k-pop terbaru" to "K-Pop Hari Ini",
        "lagu lofi belajar" to "Lo-Fi Fokus",
        "lagu rock klasik" to "Rock Klasik",
        "lagu anime opening" to "Anime Pilihan",
        "lagu religi islami" to "Religi",
        "lagu dangdut koplo" to "Dangdut Koplo"
    )
}

/** Gaya suara pemutar. Nada ikut berubah bersama kecepatan, seperti versi speed up / slowed di internet. */
enum class VoiceStyle(val speed: Float, val pitch: Float, val reverb: Boolean) {
    NORMAL(1f, 1f, false),
    SPEED_UP(1.25f, 1.25f, false),
    SLOWED_REVERB(0.85f, 0.85f, true)
}

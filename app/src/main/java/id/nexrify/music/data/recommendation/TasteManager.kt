package id.nexrify.music.data.recommendation

import android.content.Context
import android.content.SharedPreferences
import id.nexrify.music.data.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class TasteManager private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val _recentlyPlayed = MutableStateFlow<List<Track>>(emptyList())
    val recentlyPlayed: StateFlow<List<Track>> = _recentlyPlayed.asStateFlow()

    private val _onRepeatTracks = MutableStateFlow<List<Track>>(emptyList())
    val onRepeatTracks: StateFlow<List<Track>> = _onRepeatTracks.asStateFlow()

    // Observable statistics let the UI update while the statistics dialog is open.
    private val _totalPlays = MutableStateFlow(0)
    val totalPlays: StateFlow<Int> = _totalPlays.asStateFlow()
    private val _uniqueArtistsCount = MutableStateFlow(0)
    val uniqueArtistsCount: StateFlow<Int> = _uniqueArtistsCount.asStateFlow()
    private val _topArtists = MutableStateFlow<List<Pair<String, Int>>>(emptyList())
    val topArtists: StateFlow<List<Pair<String, Int>>> = _topArtists.asStateFlow()

    // In-memory caches to eliminate repeated JSON serialization/deserialization
    private val scoresMap = ConcurrentHashMap<String, Int>()
    private val playsMap = ConcurrentHashMap<String, Int>()
    private val trackDataMap = ConcurrentHashMap<String, Track>()

    // Reaksi eksplisit pengguna: 1 = suka, -1 = tidak suka. Dipisah dari skor putar supaya tidak ikut terpangkas.
    private val reactionMap = ConcurrentHashMap<String, Int>()
    private val reactedTracks = ConcurrentHashMap<String, Track>()
    private val artistLike = ConcurrentHashMap<String, Int>()
    private val artistDislike = ConcurrentHashMap<String, Int>()
    private val _reactions = MutableStateFlow<Map<String, Int>>(emptyMap())
    /** id lagu -> 1 (suka) / -1 (tidak suka). Dipakai UI untuk menyalakan tombol. */
    val reactions: StateFlow<Map<String, Int>> = _reactions.asStateFlow()

    init {
        loadDataFromPrefs()
    }

    @Synchronized
    fun recordTrackPlayed(track: Track) {
        updateTrackScore(track, deltaScore = PLAY_DELTA, isPlay = true)
        addToRecents(track)
        refreshOnRepeat()
        refreshStatistics()
    }

    @Synchronized
    fun recordTrackLiked(track: Track) {
        updateTrackScore(track, deltaScore = LIKE_DELTA, isPlay = false)
        refreshOnRepeat()
        refreshStatistics()
    }

    @Synchronized
    fun recordTrackRepeated(track: Track) {
        updateTrackScore(track, deltaScore = 4, isPlay = true)
        refreshOnRepeat()
        refreshStatistics()
    }

    /** Batal suka: mencabut bonus suka supaya skor tidak naik terus. */
    @Synchronized
    fun recordTrackUnliked(track: Track) {
        updateTrackScore(track, deltaScore = -LIKE_DELTA, isPlay = false)
        refreshOnRepeat()
        refreshStatistics()
    }

    /**
     * Lagu dilewati sebelum sempat didengar. Putaran sudah dihitung (+PLAY_DELTA) saat pemutaran
     * dimulai, jadi penalti harus lebih besar dari bonus itu; kalau tidak, skip malah menambah skor.
     * Skor boleh negatif: lagu yang berulang kali dilewati dianggap tidak disukai.
     */
    @Synchronized
    fun recordTrackSkipped(track: Track) {
        updateTrackScore(track, deltaScore = SKIP_DELTA, isPlay = false)
        // Lagu yang dilewati bukan putaran sungguhan: jangan menggelembungkan statistik.
        val plays = playsMap[track.id] ?: 0
        if (plays > 0) playsMap[track.id] = plays - 1
        removeFromRecents(track.id)
        refreshOnRepeat()
        refreshStatistics()
    }

    private fun artistKey(artist: String) = artist.trim().lowercase()

    fun reactionOf(id: String): Int = reactionMap[id] ?: 0

    /**
     * Suka / tidak suka dari tombol di beranda. Mengetuk tombol yang sama lagi membatalkannya.
     * Suka: lagu naik tinggi dan artisnya ikut sering muncul. Tidak suka: lagu diblokir dari rekomendasi,
     * artisnya diturunkan (lagu lain dari artis itu jarang muncul).
     */
    @Synchronized
    fun react(track: Track, reaction: Int) {
        val prev = reactionMap[track.id] ?: 0
        val next = if (prev == reaction) 0 else reaction
        val key = artistKey(track.artist)

        // Cabut efek reaksi sebelumnya.
        when (prev) {
            1 -> {
                updateTrackScore(track, deltaScore = -REACT_LIKE_DELTA, isPlay = false)
                if (key.isNotEmpty()) artistLike.merge(key, -ARTIST_LIKE_STEP) { a, b -> maxOf(0, a + b) }
            }
            -1 -> {
                scoresMap[track.id] = 0
                if (key.isNotEmpty()) artistDislike.merge(key, -1) { a, b -> maxOf(0, a + b) }
            }
        }
        // Terapkan yang baru.
        when (next) {
            1 -> {
                updateTrackScore(track, deltaScore = REACT_LIKE_DELTA, isPlay = false)
                if (key.isNotEmpty()) artistLike.merge(key, ARTIST_LIKE_STEP) { a, b -> minOf(ARTIST_LIKE_MAX, a + b) }
                reactionMap[track.id] = 1
                reactedTracks[track.id] = track
            }
            -1 -> {
                scoresMap[track.id] = MIN_SCORE
                trackDataMap[track.id] = track
                if (key.isNotEmpty()) artistDislike.merge(key, 1) { a, b -> minOf(ARTIST_DISLIKE_MAX, a + b) }
                reactionMap[track.id] = -1
                reactedTracks[track.id] = track
                removeFromRecents(track.id)
            }
            else -> { reactionMap.remove(track.id); reactedTracks.remove(track.id) }
        }
        _reactions.value = HashMap(reactionMap)
        refreshOnRepeat()
        refreshStatistics()
        persistTasteDataAsync()
    }

    /** Lagu yang pernah di-like lewat tombol, terbaru tidak dijamin urutannya. */
    fun likedTracks(): List<Track> = reactionMap.filterValues { it == 1 }.keys.mapNotNull { reactedTracks[it] }

    /** Artis yang disukai, paling kuat dulu. */
    fun favoriteArtists(limit: Int = 5): List<String> =
        artistLike.entries.filter { it.value > 0 }.sortedByDescending { it.value }
            .map { e ->
                // Pakai penulisan asli artis dari lagu yang disimpan.
                (reactedTracks.values + trackDataMap.values).firstOrNull { artistKey(it.artist) == e.key }?.artist ?: e.key
            }.distinct().take(limit)

    /** Seberapa kuat artis ini disukai (+) atau tidak disukai (-). */
    fun artistAffinity(artist: String): Int {
        val k = artistKey(artist)
        return (artistLike[k] ?: 0) - (artistDislike[k] ?: 0) * 2
    }

    /** Skor lagu (0 kalau belum pernah dinilai). Negatif berarti cenderung tidak disukai. */
    fun scoreOf(id: String): Int = scoresMap[id] ?: 0

    fun isDisliked(id: String): Boolean = reactionMap[id] == -1 || scoreOf(id) <= DISLIKE_THRESHOLD

    /**
     * Saring rekomendasi. Lagu yang di-dislike hampir tidak pernah lolos (peluang kecil, "muncul tapi jarang"),
     * lagu lain dari artis yang di-dislike juga jarang. Hasil acak per panggilan, jadi tidak selalu sama.
     */
    fun withoutDisliked(tracks: List<Track>, random: kotlin.random.Random = kotlin.random.Random.Default): List<Track> =
        tracks.filter { t ->
            when {
                reactionMap[t.id] == -1 -> random.nextFloat() < BLOCKED_TRACK_CHANCE
                scoreOf(t.id) <= DISLIKE_THRESHOLD -> random.nextFloat() < BLOCKED_TRACK_CHANCE
                (artistDislike[artistKey(t.artist)] ?: 0) > 0 -> random.nextFloat() < DISLIKED_ARTIST_CHANCE
                else -> true
            }
        }

    /** Saring lalu urutkan: lagu/artis yang disukai naik, sisanya diacak halus supaya tiap muat berbeda. */
    fun personalize(tracks: List<Track>, random: kotlin.random.Random = kotlin.random.Random.Default): List<Track> =
        withoutDisliked(tracks, random)
            .map { t -> t to (scoreOf(t.id) + artistAffinity(t.artist) * 3 + random.nextInt(0, JITTER)) }
            .sortedByDescending { it.second }
            .map { it.first }

    private fun updateTrackScore(track: Track, deltaScore: Int, isPlay: Boolean) {
        val currentScore = scoresMap[track.id] ?: 0
        scoresMap[track.id] = (currentScore + deltaScore).coerceIn(MIN_SCORE, MAX_SCORE)

        if (isPlay) {
            val currentPlays = playsMap[track.id] ?: 0
            playsMap[track.id] = currentPlays + 1
        }

        trackDataMap[track.id] = track

        // Prune entries if exceeding max limit to prevent unbounded memory/disk growth
        if (trackDataMap.size > MAX_TRACK_ENTRIES) {
            // Buang yang paling "netral" (skor mendekati 0). Skor sangat negatif ikut dijaga
            // supaya lagu yang tidak disukai tidak lupa dan muncul lagi.
            val sortedByScore = scoresMap.entries.filter { it.key != track.id && !reactionMap.containsKey(it.key) }
                .sortedBy { kotlin.math.abs(it.value) }
            val toRemoveCount = trackDataMap.size - MAX_TRACK_ENTRIES
            for (i in 0 until toRemoveCount) {
                val entry = sortedByScore.getOrNull(i) ?: break
                scoresMap.remove(entry.key)
                playsMap.remove(entry.key)
                trackDataMap.remove(entry.key)
            }
        }

        persistTasteDataAsync()
    }

    private fun removeFromRecents(id: String) {
        val current = _recentlyPlayed.value
        if (current.none { it.id == id }) return
        val trimmed = current.filter { it.id != id }
        _recentlyPlayed.value = trimmed
        persistRecents(trimmed)
    }

    private fun persistRecents(list: List<Track>) {
        val arr = JSONArray()
        for (t in list) {
            arr.put(JSONObject().apply {
                put("id", t.id)
                put("url", t.url)
                put("title", t.title)
                put("artist", t.artist)
                put("thumbnailUrl", t.thumbnailUrl)
                put("durationSeconds", t.durationSeconds)
            })
        }
        prefs.edit().putString(KEY_RECENTS, arr.toString()).apply()
    }

    private fun addToRecents(track: Track) {
        val current = _recentlyPlayed.value.toMutableList()
        current.removeAll { it.id == track.id }
        current.add(0, track)

        val trimmed = if (current.size > MAX_RECENTS) current.take(MAX_RECENTS) else current
        _recentlyPlayed.value = trimmed

        // Persist recents
        val arr = JSONArray()
        for (t in trimmed) {
            val obj = JSONObject().apply {
                put("id", t.id)
                put("url", t.url)
                put("title", t.title)
                put("artist", t.artist)
                put("thumbnailUrl", t.thumbnailUrl)
                put("durationSeconds", t.durationSeconds)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_RECENTS, arr.toString()).apply()
    }

    private fun refreshOnRepeat() {
        val sorted = scoresMap.entries
            .filter { it.value > 3 }
            .sortedByDescending { it.value }
            .take(15)

        val list = sorted.mapNotNull { trackDataMap[it.key] }
        _onRepeatTracks.value = list
    }

    private fun persistTasteDataAsync() {
        val scoresJson = JSONObject()
        scoresMap.forEach { (k, v) -> scoresJson.put(k, v) }

        val playsJson = JSONObject()
        playsMap.forEach { (k, v) -> playsJson.put(k, v) }

        val trackDataJson = JSONObject()
        trackDataMap.forEach { (k, t) ->
            val obj = JSONObject().apply {
                put("id", t.id)
                put("url", t.url)
                put("title", t.title)
                put("artist", t.artist)
                put("thumbnailUrl", t.thumbnailUrl)
                put("durationSeconds", t.durationSeconds)
            }
            trackDataJson.put(k, obj)
        }

        val reactJson = JSONObject()
        reactionMap.forEach { (k, v) -> reactJson.put(k, v) }
        val reactTracks = JSONObject()
        reactedTracks.forEach { (k, t) ->
            reactTracks.put(k, JSONObject().apply {
                put("id", t.id); put("url", t.url); put("title", t.title); put("artist", t.artist)
                put("thumbnailUrl", t.thumbnailUrl); put("durationSeconds", t.durationSeconds)
            })
        }
        val likeJson = JSONObject(); artistLike.forEach { (k, v) -> likeJson.put(k, v) }
        val dislikeJson = JSONObject(); artistDislike.forEach { (k, v) -> dislikeJson.put(k, v) }

        prefs.edit()
            .putString(KEY_REACTIONS, reactJson.toString())
            .putString(KEY_REACT_TRACKS, reactTracks.toString())
            .putString(KEY_ARTIST_LIKE, likeJson.toString())
            .putString(KEY_ARTIST_DISLIKE, dislikeJson.toString())
            .putString(KEY_SCORES, scoresJson.toString())
            .putString(KEY_PLAYS, playsJson.toString())
            .putString(KEY_TRACK_DATA, trackDataJson.toString())
            .apply()
    }

    /** Muat ulang dari penyimpanan setelah data akun diterapkan atau dihapus. */
    @Synchronized
    fun reload() {
        scoresMap.clear(); playsMap.clear(); trackDataMap.clear()
        reactionMap.clear(); reactedTracks.clear(); artistLike.clear(); artistDislike.clear()
        loadDataFromPrefs()
    }

    private fun loadDataFromPrefs() {
        // Tiap bagian dimuat terpisah: satu bagian rusak tidak boleh menggagalkan yang lain.
        try {
            // 1. Load recents
            val recentsRaw = prefs.getString(KEY_RECENTS, "[]") ?: "[]"
            val recentsArr = JSONArray(recentsRaw)
            val rList = mutableListOf<Track>()
            for (i in 0 until recentsArr.length()) {
                val obj = recentsArr.getJSONObject(i)
                rList.add(
                    Track(
                        id = obj.optString("id"),
                        url = obj.optString("url"),
                        title = obj.optString("title"),
                        artist = obj.optString("artist"),
                        thumbnailUrl = obj.optString("thumbnailUrl"),
                        durationSeconds = obj.optLong("durationSeconds", 0L)
                    )
                )
            }
            _recentlyPlayed.value = rList
        } catch (_: Exception) { _recentlyPlayed.value = emptyList() }

        try {
            // 2. Load taste maps
            val scoresJson = JSONObject(prefs.getString(KEY_SCORES, "{}") ?: "{}")
            val playsJson = JSONObject(prefs.getString(KEY_PLAYS, "{}") ?: "{}")
            val trackDataJson = JSONObject(prefs.getString(KEY_TRACK_DATA, "{}") ?: "{}")

            val sKeys = scoresJson.keys()
            while (sKeys.hasNext()) {
                val k = sKeys.next()
                scoresMap[k] = scoresJson.optInt(k, 0).coerceIn(MIN_SCORE, MAX_SCORE)
            }

            val pKeys = playsJson.keys()
            while (pKeys.hasNext()) {
                val k = pKeys.next()
                playsMap[k] = playsJson.optInt(k, 0)
            }

            val tKeys = trackDataJson.keys()
            while (tKeys.hasNext()) {
                val k = tKeys.next()
                val obj = trackDataJson.optJSONObject(k)
                if (obj != null) {
                    trackDataMap[k] = Track(
                        id = obj.optString("id"),
                        url = obj.optString("url"),
                        title = obj.optString("title"),
                        artist = obj.optString("artist"),
                        thumbnailUrl = obj.optString("thumbnailUrl"),
                        durationSeconds = obj.optLong("durationSeconds", 0L)
                    )
                }
            }

        } catch (_: Exception) {
            scoresMap.clear(); playsMap.clear(); trackDataMap.clear()
        }
        try {
            val r = JSONObject(prefs.getString(KEY_REACTIONS, "{}") ?: "{}")
            r.keys().forEach { k -> val v = r.optInt(k, 0); if (v != 0) reactionMap[k] = v }
            val rt = JSONObject(prefs.getString(KEY_REACT_TRACKS, "{}") ?: "{}")
            rt.keys().forEach { k ->
                rt.optJSONObject(k)?.let { obj ->
                    reactedTracks[k] = Track(
                        id = obj.optString("id"), url = obj.optString("url"), title = obj.optString("title"),
                        artist = obj.optString("artist"), thumbnailUrl = obj.optString("thumbnailUrl"),
                        durationSeconds = obj.optLong("durationSeconds", 0L)
                    )
                }
            }
            val al = JSONObject(prefs.getString(KEY_ARTIST_LIKE, "{}") ?: "{}")
            al.keys().forEach { k -> artistLike[k] = al.optInt(k, 0) }
            val ad = JSONObject(prefs.getString(KEY_ARTIST_DISLIKE, "{}") ?: "{}")
            ad.keys().forEach { k -> artistDislike[k] = ad.optInt(k, 0) }
        } catch (_: Exception) {
            reactionMap.clear(); reactedTracks.clear(); artistLike.clear(); artistDislike.clear()
        }
        _reactions.value = HashMap(reactionMap)
        refreshOnRepeat()
        refreshStatistics()
    }

    @Synchronized
    private fun refreshStatistics() {
        _totalPlays.value = playsMap.values.sum()
        val artists = trackDataMap.entries.mapNotNull { (id, track) ->
            track.artist.trim().takeIf { it.isNotBlank() && (playsMap[id] ?: 0) > 0 }
        }.toSet()
        _uniqueArtistsCount.value = artists.size
        _topArtists.value = trackDataMap.entries.mapNotNull { (id, track) ->
            val count = playsMap[id] ?: 0
            val artist = track.artist.trim()
            if (count > 0 && artist.isNotBlank()) artist to count else null
        }.groupBy({ it.first }, { it.second })
            .map { it.key to it.value.sum() }
            .sortedByDescending { it.second }
            .take(5)
    }

    fun getTotalPlays(): Int {
        var total = 0
        playsMap.values.forEach { total += it }
        return total
    }

    fun getTopArtists(limit: Int = 5): List<Pair<String, Int>> {
        val artistCounts = mutableMapOf<String, Int>()
        trackDataMap.forEach { (id, track) ->
            val count = playsMap[id] ?: 0
            val artist = track.artist.trim()
            if (artist.isNotBlank() && count > 0) {
                artistCounts[artist] = (artistCounts[artist] ?: 0) + count
            }
        }
        return artistCounts.toList().sortedByDescending { it.second }.take(limit)
    }

    fun getUniqueArtistsCount(): Int {
        val artists = mutableSetOf<String>()
        trackDataMap.forEach { (id, track) ->
            val a = track.artist.trim()
            if ((playsMap[id] ?: 0) > 0 && a.isNotBlank()) artists.add(a)
        }
        return artists.size
    }

    fun getTopTracks(limit: Int = 5): List<Pair<Track, Int>> {
        val list = mutableListOf<Pair<Track, Int>>()
        trackDataMap.forEach { (id, track) ->
            val count = playsMap[id] ?: 0
            if (count > 0) {
                list.add(Pair(track, count))
            }
        }
        return list.sortedByDescending { it.second }.take(limit)
    }

    companion object {
        private const val PREF_NAME = "nexrify_taste_profile"
        private const val KEY_SCORES = "scores"
        private const val KEY_PLAYS = "plays"
        private const val KEY_TRACK_DATA = "track_data"
        private const val KEY_RECENTS = "recents"
        private const val KEY_REACTIONS = "reactions"
        private const val KEY_REACT_TRACKS = "react_tracks"
        private const val KEY_ARTIST_LIKE = "artist_like"
        private const val KEY_ARTIST_DISLIKE = "artist_dislike"
        private const val REACT_LIKE_DELTA = 14
        private const val ARTIST_LIKE_STEP = 3
        private const val ARTIST_LIKE_MAX = 15
        private const val ARTIST_DISLIKE_MAX = 5
        private const val JITTER = 6
        /** Peluang lagu yang di-dislike tetap lolos ke rekomendasi ("muncul sih, tapi jarang"). */
        private const val BLOCKED_TRACK_CHANCE = 0.03f
        /** Peluang lagu lain dari artis yang di-dislike tetap lolos. */
        private const val DISLIKED_ARTIST_CHANCE = 0.25f
        private const val MAX_RECENTS = 30
        private const val MAX_TRACK_ENTRIES = 100
        private const val PLAY_DELTA = 3
        private const val LIKE_DELTA = 5
        private const val SKIP_DELTA = -7
        private const val MIN_SCORE = -30
        private const val MAX_SCORE = 200
        /** Skor di bawah/sama dengan ini = tidak disukai (sekitar dua kali dilewati). */
        private const val DISLIKE_THRESHOLD = -6

        @Volatile
        private var INSTANCE: TasteManager? = null

        fun getInstance(context: Context): TasteManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TasteManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

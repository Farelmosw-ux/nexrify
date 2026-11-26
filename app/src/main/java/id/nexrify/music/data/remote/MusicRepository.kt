package id.nexrify.music.data.remote

import android.util.Log
import android.util.LruCache
import id.nexrify.music.data.model.Track
import id.nexrify.music.data.model.HomeSection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

data class ResolvedStream(
    val url: String,
    val mimeType: String? = null
)

/** Hasil pencarian yang sudah diurutkan: [primary] satu versi terbaik per lagu, [others] versi lain dengan judul yang sama. */
data class RankedSearch(val primary: List<Track>, val others: List<Track>) {
    val all: List<Track> get() = primary + others
}

/** Satu hasil mentah YouTube Music beserta sinyal popularitasnya. */
private data class YtmItem(val track: Track, val views: Long, val topic: Boolean, val top: Boolean = false)

private class Cand(val track: Track, val src: Int, val pos: Int, val views: Long, val topic: Boolean) { var score = 0.0 }

class MusicRepository {

    private val service = ServiceList.YouTube

    private companion object {
        const val YTM_CLIENT_VERSION = "1.20251001.03.00"
        /** Filter pencarian YouTube Music: hanya Lagu. */
        const val YTM_FILTER_SONGS = "EgWKAQIIAWoKEAkQBRAKEAMQBA=="
        /** Filter pencarian YouTube Music: hanya Video (lagu yang cuma ada sebagai video, cover, remix, dll). */
        const val YTM_FILTER_VIDEOS = "EgWKAQIQAWoKEAkQBRAKEAMQBA=="
        const val MIN_YTM_RESULTS = 5
        const val YTM_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
        val VISITOR_DATA_REGEX = Regex("\"VISITOR_DATA\"\\s*:\\s*\"([^\"]+)\"")
        val VISITOR_DATA_REGEX_ALT = Regex("\"visitorData\"\\s*:\\s*\"([^\"]+)\"")
        @Volatile var cachedVisitorData: String? = null
        val YTM_TYPE_LABELS = setOf("song", "video", "single", "ep", "album", "lagu", "playlist", "artist", "artis")
    }
    /** Visitor id YouTube Music: bikin rak beranda ikut berubah-ubah, bukan itu-itu saja. */
    private fun ensureVisitorData(): String? {
        cachedVisitorData?.let { return it }
        return try {
            val req = okhttp3.Request.Builder()
                .url("https://music.youtube.com/sw.js_data")
                .header("User-Agent", YTM_UA)
                .header("Referer", "https://music.youtube.com/")
                .build()
            HttpClientProvider.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val raw = resp.body?.string().orEmpty()
                val m = VISITOR_DATA_REGEX.find(raw)?.groupValues?.getOrNull(1)
                    ?: VISITOR_DATA_REGEX_ALT.find(raw)?.groupValues?.getOrNull(1)
                if (!m.isNullOrBlank()) cachedVisitorData = m
                cachedVisitorData
            }
        } catch (_: Exception) { null }
    }

    private val streamUrlCache = LruCache<String, Pair<ResolvedStream, Long>>(50)
    private val CACHE_EXPIRY_MS = 100 * 60 * 1000L

    private var homeCache: Pair<List<HomeSection>, Long>? = null
    private val HOME_CACHE_TTL = 10 * 60 * 1000L // 10 minutes

    private val searchCache = LruCache<String, Pair<List<Track>, Long>>(30)
    private val SEARCH_CACHE_TTL = 5 * 60 * 1000L // 5 minutes

    private val radioCache = LruCache<String, Pair<List<Track>, Long>>(20)
    private val RADIO_CACHE_TTL = 30 * 60 * 1000L // 30 minutes

    private fun cleanTrackTitle(raw: String): String {
        return raw
            .replace(Regex("""(?i)\s*[\(\[](?:official\s*(?:music\s*)?video|official\s*audio|music\s*video|lyric\s*video|lyrics|audio|visualizer|remastered|remaster|4k|hd|hq|mv|clip\s*officiel|video\s*oficial)[\)\]]"""), "")
            .replace(Regex("""(?i)\s*\|\s*(?:official\s*(?:music\s*)?video|official\s*audio|visualizer).*"""), "")
            .trim()
    }

    fun upgradeThumbnailUrl(rawUrl: String): String {
        if (rawUrl.isBlank()) return rawUrl
        var u = rawUrl.trim()
        if (u.startsWith("//")) {
            u = "https:$u"
        }
        if (u.contains("googleusercontent.com")) {
            return if (u.contains(Regex("=w\\d+-h\\d+"))) {
                u.replace(Regex("=w\\d+-h\\d+[^&?]*"), "=w1080-h1080-l90-rj")
            } else if (u.contains(Regex("=s\\d+"))) {
                u.replace(Regex("=s\\d+[^&?]*"), "=w1080-h1080-l90-rj")
            } else if (u.contains("=")) {
                u.replace(Regex("=[^=]*$"), "=w1080-h1080-l90-rj")
            } else {
                "$u=w1080-h1080-l90-rj"
            }
        }
        if (u.contains("ytimg.com")) {
            return u
                .replace("/default.jpg", "/hqdefault.jpg")
                .replace("/default.webp", "/hqdefault.webp")
                .replace("/mqdefault.jpg", "/hqdefault.jpg")
                .replace("/mqdefault.webp", "/hqdefault.webp")
        }
        return u
    }

    suspend fun resolveBestThumbnail(track: Track): String = withContext(Dispatchers.IO) {
        val upgraded = upgradeThumbnailUrl(track.thumbnailUrl)
        if (upgraded.contains("ytimg.com") && track.id.isNotBlank()) {
            val maxres = "https://i.ytimg.com/vi/${track.id}/maxresdefault.jpg"
            try {
                val req = okhttp3.Request.Builder()
                    .url(maxres)
                    .head()
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                val client = HttpClientProvider.client
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        return@withContext maxres
                    }
                }
            } catch (_: Exception) {}
        }
        upgraded
    }

    private fun parseDurationToSeconds(durationStr: String): Long {
        if (durationStr.isBlank()) return 0L
        val clean = durationStr.replace(".", ":").trim()
        val parts = clean.split(":")
        return try {
            when (parts.size) {
                2 -> (parts[0].toLong() * 60) + parts[1].toLong()
                3 -> (parts[0].toLong() * 3600) + (parts[1].toLong() * 60) + parts[2].toLong()
                else -> 0L
            }
        } catch (_: Exception) {
            0L
        }
    }

    suspend fun searchTracks(query: String, forceRefresh: Boolean = false): List<Track> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val q = query.trim().lowercase()
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            synchronized(searchCache) {
                searchCache.get(q)?.let { (cached, time) ->
                    if (now - time < SEARCH_CACHE_TTL) {
                        return@withContext cached
                    }
                }
            }
        }

        // 1. YouTube Music: Lagu DAN Video dicari bersamaan. Dulu hanya filter Lagu, jadi lagu yang
        //    di YouTube Music cuma ada sebagai video (atau versi cover/remix/upload) tidak pernah muncul.
        val (songs, videos) = coroutineScope {
            val s = async { searchYouTubeMusic(query, YTM_FILTER_SONGS).map { it.track } }
            val v = async { searchYouTubeMusic(query, YTM_FILTER_VIDEOS).map { it.track } }
            s.await() to v.await()
        }
        val merged = mergeSearchResults(songs, videos)
        if (merged.size >= MIN_YTM_RESULTS) {
            synchronized(searchCache) { searchCache.put(q, Pair(merged, now)) }
            return@withContext merged
        }

        // 2. Hasil YouTube Music sedikit atau kosong: lengkapi dengan NewPipe.
        val fallback = searchWithNewPipe(query)
        val combined = mergeSearchResults(merged, fallback)
        if (combined.isNotEmpty()) {
            synchronized(searchCache) { searchCache.put(q, Pair(combined, now)) }
        }
        combined
    }

    private val rankedCache = LruCache<String, Pair<RankedSearch, Long>>(30)

    /**
     * Pencarian untuk layar cari: hasil diurutkan menurut kemungkinan itulah lagu yang dimaksud, lalu versi lain dengan judul sama
     * (artis berbeda, cover, remix) dipisah ke [RankedSearch.others] supaya daftar utama tidak menumpuk.
     * Sinyal yang dipakai: hasil teratas YouTube Music, urutan hasil Lagu/Video, jumlah tayangan, kecocokan artis/judul dengan
     * kata kunci, dan penalti untuk cover/remix/slowed/dll kalau kata kunci tidak memintanya.
     */
    suspend fun searchTracksRanked(query: String, forceRefresh: Boolean = false): RankedSearch = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext RankedSearch(emptyList(), emptyList())
        val q = query.trim().lowercase()
        val now = System.currentTimeMillis()
        if (!forceRefresh) synchronized(rankedCache) {
            rankedCache.get(q)?.let { (c, t) -> if (now - t < SEARCH_CACHE_TTL) return@withContext c }
        }
        val (songs, videos, top) = coroutineScope {
            val s = async { searchYouTubeMusic(query, YTM_FILTER_SONGS) }
            val v = async { searchYouTubeMusic(query, YTM_FILTER_VIDEOS) }
            val t = async { searchYouTubeMusic(query, "").filter { it.top } }
            Triple(s.await(), v.await(), t.await())
        }
        val cands = ArrayList<Cand>()
        top.firstOrNull()?.let { cands.add(Cand(it.track, 0, 0, it.views, it.topic)) }
        songs.forEachIndexed { i, it -> cands.add(Cand(it.track, 1, i, it.views, it.topic)) }
        videos.forEachIndexed { i, it -> cands.add(Cand(it.track, 2, i, it.views, it.topic)) }
        if (cands.size < MIN_YTM_RESULTS) searchWithNewPipe(query).forEach { cands.add(Cand(it, 3, 0, 0L, false)) }
        val ranked = rankCandidates(query, cands)
        if (ranked.primary.isNotEmpty()) synchronized(rankedCache) { rankedCache.put(q, Pair(ranked, now)) }
        ranked
    }

    private fun norm(s: String): String =
        s.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()

    /** Judul tanpa (feat. ...), [..], dan akhiran " - ...", dipakai untuk menggabungkan versi lagu yang sama. */
    private fun titleBase(title: String): String =
        norm(title.replace(Regex("[\\(\\[].*?[\\)\\]]"), " ").replace(Regex("\\s[-–—]\\s.*$"), ""))

    private val NOISE = listOf(
        "cover", "remix", "slowed", "sped up", "speed up", "reverb", "nightcore", "karaoke", "instrumental", "lofi", "lo-fi", "8d",
        "tribute", "mashup", "made famous", "originally performed", "bass boosted", "tiktok", "live", "acoustic", "piano version",
        "remake", "re-edit", "reaction", "dj", "versi", "type beat", "backing track", "minus one", "ringtone", "mix"
    ).map { it to Regex("\\b" + Regex.escape(it) + "\\b", RegexOption.IGNORE_CASE) }

    private fun rankCandidates(query: String, cands: List<Cand>): RankedSearch {
        val nq = norm(query)
        val videoViews = cands.filter { it.src == 2 && it.views > 0 }
        for (c in cands) {
            val t = c.track
            val nt = titleBase(t.title)
            val na = norm(t.artist)
            var sc = when (c.src) {
                0 -> 400.0
                1 -> maxOf(20.0, 100.0 - c.pos * 6.0)
                2 -> maxOf(10.0, 70.0 - c.pos * 5.0)
                else -> 5.0
            }
            // Tayangan: milik sendiri, atau milik video dengan judul dan artis yang sama (lagu YTM tidak menampilkan tayangan).
            var views = c.views
            if (views == 0L) videoViews.forEach { v ->
                val vt = titleBase(v.track.title); val va = norm(v.track.artist)
                if (vt == nt && (va == na || va.contains(na) || na.contains(va))) views = maxOf(views, v.views)
            }
            if (views > 0) sc += Math.log10(views + 1.0) * 14.0
            if (c.src <= 1) sc += 12.0
            if (c.topic) sc += 10.0
            if (na.length >= 3 && nq.contains(na)) sc += 90.0
            if (nt.isNotEmpty()) {
                if (nq == nt) sc += 30.0 else if (nq.contains(nt)) sc += 25.0
            }
            val hay = (t.title + " " + t.artist)
            if (NOISE.any { (w, re) -> re.containsMatchIn(hay) && !nq.contains(w) }) sc -= 140.0
            c.score = sc
        }
        // Buang duplikat id dan judul+artis, simpan skor tertinggi.
        val byId = LinkedHashMap<String, Cand>()
        for (c in cands) { if (c.track.id.isBlank()) continue; val o = byId[c.track.id]; if (o == null || c.score > o.score) byId[c.track.id] = c }
        val byKey = LinkedHashMap<String, Cand>()
        for (c in byId.values) { val k = trackKey(c.track); val o = byKey[k]; if (o == null || c.score > o.score) byKey[k] = c }
        val groups = byKey.values.groupBy { titleBase(it.track.title).ifBlank { it.track.id } }
            .values.map { g -> g.sortedByDescending { it.score } }
            .sortedByDescending { it.first().score }
        val primary = groups.map { it.first().track }
        val others = groups.flatMap { it.drop(1) }.map { it.track }
        return RankedSearch(primary, others)
    }

    /** "1,2 jt x ditonton" / "54M views" jadi angka. "M" = miliar di bahasa Indonesia, juta di Inggris. */
    private fun parseViews(text: String): Long {
        val m = Regex("([\\d.,]+)\\s*(rb|jt|k|m|b)?\\s*(x\\s*ditonton|tayangan|views?|kali)", RegexOption.IGNORE_CASE).find(text) ?: return 0L
        val id = text.contains("ditonton", true) || text.contains("tayangan", true)
        val raw = m.groupValues[1].let { n ->
            // Format Indonesia memakai koma desimal ("1,2 jt"), Inggris titik ("1.2M").
            if (id) n.replace(".", "").replace(",", ".") else n.replace(",", "")
        }.toDoubleOrNull() ?: return 0L
        val mult = when (m.groupValues[2].lowercase()) {
            "k", "rb" -> 1_000.0
            "jt" -> 1_000_000.0
            "m" -> if (id) 1_000_000_000.0 else 1_000_000.0
            "b" -> 1_000_000_000.0
            else -> 1.0
        }
        return (raw * mult).toLong()
    }

    private fun trackKey(t: Track): String =
        (t.title + "|" + t.artist).lowercase().filter { it.isLetterOrDigit() }

    /** Gabung hasil, urutan dipertahankan, tanpa duplikat id maupun judul+artis yang sama. */
    private fun mergeSearchResults(first: List<Track>, second: List<Track>): List<Track> {
        val seenIds = HashSet<String>()
        val seenKeys = HashSet<String>()
        val out = ArrayList<Track>(first.size + second.size)
        for (t in first + second) {
            if (t.id.isBlank() || !seenIds.add(t.id)) continue
            if (!seenKeys.add(trackKey(t))) continue
            out.add(t)
        }
        return out
    }

    private fun searchWithNewPipe(query: String): List<Track> {
        return try {
            val searchExtractor = service.getSearchExtractor(
                query,
                listOf("videos"),
                ""
            )
            searchExtractor.fetchPage()
            val page = searchExtractor.initialPage
            page.items
                .filterIsInstance<StreamInfoItem>()
                .mapNotNull { item ->
                    val vid = item.url.substringAfter("v=", "").substringBefore("&")
                    if (vid.isBlank()) return@mapNotNull null
                    val rawThumb = item.thumbnails.maxByOrNull { it.width * it.height }?.url
                        ?: item.thumbnails.lastOrNull()?.url
                        ?: item.thumbnails.firstOrNull()?.url
                        ?: ""
                    Track(
                        id = vid,
                        url = item.url,
                        title = cleanTrackTitle(item.name ?: "Unknown Title"),
                        artist = item.uploaderName?.replace(" - Topic", "") ?: "Unknown Artist",
                        thumbnailUrl = upgradeThumbnailUrl(rawThumb),
                        durationSeconds = item.duration.coerceAtLeast(0L)
                    )
                }
        } catch (e: Exception) {
            Log.e("MusicRepository", "Search failed: ${e.message}", e)
            emptyList()
        }
    }

    private fun searchYouTubeMusic(query: String, params: String): List<YtmItem> {
        return try {
            val auth = id.nexrify.music.data.auth.AuthManager.getInstance()
            val origin = "https://music.youtube.com"
            val cookie = auth.getCookie() ?: ""
            val sapisidHash = auth.getSapisidHash(origin)

            val payload = org.json.JSONObject().apply {
                put("context", org.json.JSONObject().apply {
                    put("client", org.json.JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", YTM_CLIENT_VERSION)
                        put("hl", "id")
                        put("gl", "ID")
                    })
                })
                put("query", query)
                if (params.isNotEmpty()) put("params", params)
            }

            val client = HttpClientProvider.client
            val reqBody = payload.toString().toRequestBody("application/json".toMediaType())
            val reqBuilder = okhttp3.Request.Builder()
                .url("$origin/youtubei/v1/search")
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Origin", origin)
                .header("Referer", "$origin/")
                .header("X-Origin", origin)
                .header("X-YouTube-Client-Name", "67")
                .header("X-YouTube-Client-Version", YTM_CLIENT_VERSION)
                .post(reqBody)

            if (cookie.isNotBlank()) reqBuilder.header("Cookie", cookie)
            if (!sapisidHash.isNullOrBlank()) {
                reqBuilder.header("Authorization", sapisidHash)
                reqBuilder.header("X-Goog-AuthUser", "0")
            }

            client.newCall(reqBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w("MusicRepository", "YTM search HTTP ${response.code}")
                    return emptyList()
                }
                val jsonStr = response.body?.string() ?: return emptyList()
                parseYtmSearchTracks(org.json.JSONObject(jsonStr))
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "YTM Search exception: ${e.message}")
            emptyList()
        }
    }

    /** Ambil nama artis dari baris subtitle YouTube Music, melewati label jenis ("Song", "Video", ...) dan pemisah. */
    private fun pickArtist(runs: org.json.JSONArray?): String {
        if (runs == null) return "Unknown Artist"
        for (i in 0 until runs.length()) {
            val t = runs.optJSONObject(i)?.optString("text", "")?.trim().orEmpty()
            if (t.isEmpty() || t == "•" || t == "&" || t == ",") continue
            if (t.lowercase() in YTM_TYPE_LABELS) continue
            return t
        }
        return "Unknown Artist"
    }

    /** Kartu "Hasil teratas" YouTube Music (hanya kalau berupa lagu/video yang bisa diputar). */
    private fun parseTopCard(card: org.json.JSONObject): YtmItem? {
        val t0 = card.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0) ?: return null
        val videoId = t0.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId", "").orEmpty()
        if (videoId.isBlank()) return null
        val runs = card.optJSONObject("subtitle")?.optJSONArray("runs")
        val rawArtist = pickArtist(runs)
        var dur = 0L
        var views = 0L
        if (runs != null) for (i in 0 until runs.length()) {
            val tx = runs.optJSONObject(i)?.optString("text", "").orEmpty()
            if (i == runs.length() - 1) dur = parseDurationToSeconds(tx)
            views = maxOf(views, parseViews(tx))
        }
        val thumbs = card.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val thumb = if (thumbs != null && thumbs.length() > 0) thumbs.optJSONObject(thumbs.length() - 1)?.optString("url", "").orEmpty() else ""
        return YtmItem(
            Track(id = videoId, url = "https://www.youtube.com/watch?v=$videoId", title = cleanTrackTitle(t0.optString("text", "")),
                artist = rawArtist.replace(" - Topic", ""), thumbnailUrl = upgradeThumbnailUrl(thumb), durationSeconds = dur),
            views, rawArtist.endsWith(" - Topic"), top = true
        )
    }

    private fun parseYtmSearchTracks(root: org.json.JSONObject): List<YtmItem> {
        val list = mutableListOf<YtmItem>()
        try {
            val tabs = root.optJSONObject("contents")
                ?.optJSONObject("tabbedSearchResultsRenderer")
                ?.optJSONArray("tabs") ?: return emptyList()
            val sections = tabs.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: return emptyList()

            for (s in 0 until sections.length()) {
                val card = sections.optJSONObject(s)?.optJSONObject("musicCardShelfRenderer")
                if (card != null) { parseTopCard(card)?.let { list.add(it) }; continue }
                val shelf = sections.optJSONObject(s)?.optJSONObject("musicShelfRenderer") ?: continue
                val items = shelf.optJSONArray("contents") ?: continue
                for (i in 0 until items.length()) {
                    val r = items.optJSONObject(i)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                    val cols = r.optJSONArray("flexColumns") ?: continue
                    if (cols.length() < 2) continue

                    val col0Runs = cols.optJSONObject(0)
                        ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                        ?.optJSONObject("text")?.optJSONArray("runs") ?: continue
                    if (col0Runs.length() == 0) continue

                    val title = col0Runs.optJSONObject(0)?.optString("text", "") ?: ""
                    val videoId = col0Runs.optJSONObject(0)
                        ?.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("watchEndpoint")
                        ?.optString("videoId", "") ?: ""
                    if (videoId.isBlank()) continue

                    val col1Runs = cols.optJSONObject(1)
                        ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                        ?.optJSONObject("text")?.optJSONArray("runs")
                    val artist = pickArtist(col1Runs)
                    var views = 0L
                    if (col1Runs != null) for (k in 0 until col1Runs.length()) views = maxOf(views, parseViews(col1Runs.optJSONObject(k)?.optString("text", "").orEmpty()))

                    var durationSec = 0L
                    if (col1Runs != null && col1Runs.length() > 0) {
                        val lastRunText = col1Runs.optJSONObject(col1Runs.length() - 1)?.optString("text", "") ?: ""
                        durationSec = parseDurationToSeconds(lastRunText)
                    }

                    var thumbUrl = ""
                    val thumbs = r.optJSONObject("thumbnail")
                        ?.optJSONObject("musicThumbnailRenderer")
                        ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                    if (thumbs != null && thumbs.length() > 0) {
                        thumbUrl = thumbs.optJSONObject(thumbs.length() - 1)?.optString("url", "") ?: ""
                    }

                    list.add(
                        YtmItem(
                            Track(
                                id = videoId,
                                url = "https://www.youtube.com/watch?v=$videoId",
                                title = cleanTrackTitle(title),
                                artist = artist.replace(" - Topic", ""),
                                thumbnailUrl = upgradeThumbnailUrl(thumbUrl),
                                durationSeconds = durationSec
                            ),
                            views, artist.endsWith(" - Topic")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "parseYtmSearchTracks error: ${e.message}")
        }
        return list
    }

    suspend fun getSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val extractor = service.suggestionExtractor
            extractor.suggestionList(query)
        } catch (e: Exception) {
            Log.e("MusicRepository", "Suggestions failed: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getHomeShelves(forceRefresh: Boolean = false): List<HomeSection> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        synchronized(this) {
            if (forceRefresh) homeCache = null
            homeCache?.let { (cached, time) ->
                if (now - time < HOME_CACHE_TTL) {
                    return@withContext variate(cached)
                }
            }
        }
        val ytmShelves = fetchYouTubeMusicShelves()
        if (ytmShelves.isNotEmpty()) {
            synchronized(this) { homeCache = Pair(ytmShelves, now) }
            return@withContext variate(ytmShelves)
        }

        // Fallback: group into categories
        val defaultTracks = searchTracks("Top Global Hits 2026")
        if (defaultTracks.isNotEmpty()) {
            listOf(
                HomeSection(title = "Pilihan Cepat", subtitle = "Dengarkan musik favoritmu", tracks = defaultTracks.take(10)),
                HomeSection(title = "Lagu Trending", subtitle = "Paling banyak diputar", tracks = defaultTracks.drop(10))
            )
        } else {
            emptyList()
        }
    }

    /**
     * Acak halus tiap beranda dimuat: urutan rak (2 teratas tetap) dan isi tiap rak (lagu pertama tetap) digeser,
     * jadi isi beranda tidak itu-itu saja walau data dari server sama.
     */
    private fun variate(shelves: List<HomeSection>): List<HomeSection> {
        if (shelves.size <= 2) return shelves
        val head = shelves.take(2)
        val tail = shelves.drop(2).shuffled()
        return (head + tail).map { sec ->
            if (sec.tracks.size > 3) sec.copy(tracks = sec.tracks.take(1) + sec.tracks.drop(1).shuffled()) else sec
        }
    }

    /** Rak penemuan acak dari satu kata kunci (dipakai rak selera & jelajah). */
    suspend fun getDiscoverShelf(query: String, title: String, subtitle: String = "", limit: Int = 12): HomeSection? {
        val tracks = runCatching { searchTracks(query) }.getOrDefault(emptyList())
        if (tracks.isEmpty()) return null
        return HomeSection(title = title, subtitle = subtitle, tracks = tracks.shuffled().take(limit))
    }

    suspend fun getTrendingTracks(): List<Track> = withContext(Dispatchers.IO) {
        searchTracks("Top Global Hits 2026")
    }

    suspend fun getRadioTracks(videoId: String): List<Track> = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext emptyList()
        val now = System.currentTimeMillis()
        synchronized(radioCache) {
            radioCache.get(videoId)?.let { (cached, time) ->
                if (now - time < RADIO_CACHE_TTL) {
                    return@withContext cached
                }
            }
        }
        try {
            val auth = id.nexrify.music.data.auth.AuthManager.getInstance()
            val origin = "https://music.youtube.com"
            val cookie = auth.getCookie() ?: ""
            val sapisidHash = auth.getSapisidHash(origin)

            val payload = org.json.JSONObject().apply {
                put("context", org.json.JSONObject().apply {
                    put("client", org.json.JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", YTM_CLIENT_VERSION)
                        put("hl", "id")
                        put("gl", "ID")
                    })
                })
                put("videoId", videoId)
                put("playlistId", "RDAMVM$videoId")
            }

            val client = HttpClientProvider.client
            val reqBody = payload.toString().toRequestBody("application/json".toMediaType())
            val reqBuilder = okhttp3.Request.Builder()
                .url("$origin/youtubei/v1/next")
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Origin", origin)
                .header("Referer", "$origin/")
                .header("X-Origin", origin)
                .header("X-YouTube-Client-Name", "67")
                .header("X-YouTube-Client-Version", YTM_CLIENT_VERSION)
                .post(reqBody)

            if (cookie.isNotBlank()) reqBuilder.header("Cookie", cookie)
            if (!sapisidHash.isNullOrBlank()) {
                reqBuilder.header("Authorization", sapisidHash)
            }

            val bodyStr = client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                resp.body?.string().orEmpty()
            }
            val json = org.json.JSONObject(bodyStr)
            val tabs = json.optJSONObject("contents")
                ?.optJSONObject("singleColumnMusicWatchNextResultsRenderer")
                ?.optJSONObject("tabbedRenderer")
                ?.optJSONObject("watchNextTabbedResultsRenderer")
                ?.optJSONArray("tabs") ?: return@withContext emptyList()

            var queueTab: org.json.JSONObject? = null
            for (ti in 0 until tabs.length()) {
                val q = tabs.optJSONObject(ti)?.optJSONObject("tabRenderer")
                    ?.optJSONObject("content")?.optJSONObject("musicQueueRenderer")
                if (q != null) {
                    queueTab = q
                    break
                }
            }
            if (queueTab == null) {
                queueTab = tabs.optJSONObject(0)?.optJSONObject("tabRenderer")
                    ?.optJSONObject("content")?.optJSONObject("musicQueueRenderer")
            }

            val items = queueTab?.optJSONObject("content")
                ?.optJSONObject("playlistPanelRenderer")?.optJSONArray("contents") ?: return@withContext emptyList()

            val radioList = mutableListOf<Track>()
            for (i in 0 until items.length()) {
                val r = items.optJSONObject(i)?.optJSONObject("playlistPanelVideoRenderer") ?: continue
                val vid = r.optString("videoId", "")
                if (vid.isBlank() || vid == videoId) continue

                val t = r.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text", "") ?: ""
                val a = r.optJSONObject("longBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text", "Unknown Artist") ?: "Unknown Artist"
                val durStr = r.optJSONObject("lengthText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text", "") ?: ""
                val durSec = parseDurationToSeconds(durStr)

                val thumbs = r.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                val thumbUrl = if (thumbs != null && thumbs.length() > 0) {
                    thumbs.optJSONObject(thumbs.length() - 1)?.optString("url", "") ?: ""
                } else ""

                radioList.add(
                    Track(
                        id = vid,
                        url = "https://www.youtube.com/watch?v=$vid",
                        title = cleanTrackTitle(t),
                        artist = a.replace(" - Topic", ""),
                        thumbnailUrl = upgradeThumbnailUrl(thumbUrl),
                        durationSeconds = durSec
                    )
                )
            }
            if (radioList.isNotEmpty()) {
                synchronized(radioCache) { radioCache.put(videoId, Pair(radioList, now)) }
            }
            radioList
        } catch (e: Exception) {
            Log.e("MusicRepository", "getRadioTracks error: ${e.message}")
            emptyList()
        }
    }

    private suspend fun fetchYouTubeMusicShelves(): List<HomeSection> = withContext(Dispatchers.IO) {
        val result = mutableListOf<HomeSection>()
        try {
            val auth = id.nexrify.music.data.auth.AuthManager.getInstance()
            val origin = "https://music.youtube.com"
            val cookie = auth.getCookie() ?: ""
            val sapisidHash = auth.getSapisidHash(origin)
            val client = HttpClientProvider.client
            val visitorData = ensureVisitorData()

            fun fetchBrowse(browseId: String, continuation: String? = null): org.json.JSONObject? {
                val payload = org.json.JSONObject().apply {
                    put("context", org.json.JSONObject().apply {
                        put("client", org.json.JSONObject().apply {
                            put("clientName", "WEB_REMIX")
                            put("clientVersion", YTM_CLIENT_VERSION)
                            put("hl", "id")
                            put("gl", "ID")
                            if (!visitorData.isNullOrBlank()) put("visitorData", visitorData)
                        })
                    })
                    if (continuation != null) put("continuation", continuation) else put("browseId", browseId)
                }
                val reqBuilder = okhttp3.Request.Builder()
                    .url("$origin/youtubei/v1/browse")
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Origin", origin)
                    .header("Referer", "$origin/")
                    .header("X-Origin", origin)
                    .header("X-YouTube-Client-Name", "67")
                    .header("X-YouTube-Client-Version", YTM_CLIENT_VERSION)
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))

                if (!visitorData.isNullOrBlank()) reqBuilder.header("X-Goog-Visitor-Id", visitorData)
                if (cookie.isNotBlank()) reqBuilder.header("Cookie", cookie)
                if (!sapisidHash.isNullOrBlank()) {
                    reqBuilder.header("Authorization", sapisidHash)
                    reqBuilder.header("X-Goog-AuthUser", "0")
                }

                return client.newCall(reqBuilder.build()).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string()?.let { org.json.JSONObject(it) } else null
                }
            }

            coroutineScope {
                val homeDeferred = async { fetchBrowse("FEmusic_home") }
                val exploreDeferred = async { fetchBrowse("FEmusic_explore") }
                val homeJson = homeDeferred.await()
                val exploreJson = exploreDeferred.await()

                if (homeJson != null) {
                    result.addAll(parseYtmSections(homeJson, "Pilihan Cepat"))
                    // Halaman lanjutan: rak tambahan supaya beranda lebih beragam.
                    var cont = homeJson.optJSONObject("contents")
                        ?.optJSONObject("singleColumnBrowseResultsRenderer")?.optJSONArray("tabs")
                        ?.optJSONObject(0)?.optJSONObject("tabRenderer")?.optJSONObject("content")
                        ?.optJSONObject("sectionListRenderer")?.optJSONArray("continuations")
                        ?.optJSONObject(0)?.optJSONObject("nextContinuationData")
                        ?.optString("continuation")?.takeIf { it.isNotBlank() }
                    var pages = 0
                    while (cont != null && pages < 2) {
                        val next = fetchBrowse("FEmusic_home", cont) ?: break
                        val list = next.optJSONObject("continuationContents")?.optJSONObject("sectionListContinuation")
                        list?.optJSONArray("contents")?.let { result.addAll(parseYtmSectionsArray(it, "Pilihan Cepat")) }
                        cont = list?.optJSONArray("continuations")?.optJSONObject(0)
                            ?.optJSONObject("nextContinuationData")?.optString("continuation")?.takeIf { it.isNotBlank() }
                        pages++
                    }
                }
                if (exploreJson != null) {
                    result.addAll(parseYtmSections(exploreJson, "Lagu Trending"))
                }
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "fetchYouTubeMusicShelves error: ${e.message}")
        }
        result
    }

    private fun parseYtmSections(root: org.json.JSONObject, defaultTitle: String): List<HomeSection> {
        val tabs = root.optJSONObject("contents")
            ?.optJSONObject("singleColumnBrowseResultsRenderer")
            ?.optJSONArray("tabs") ?: return emptyList()
        val sections = tabs.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents") ?: return emptyList()
        return parseYtmSectionsArray(sections, defaultTitle)
    }

    private fun parseYtmSectionsArray(sections: org.json.JSONArray, defaultTitle: String): List<HomeSection> {
        val list = mutableListOf<HomeSection>()
        try {
            for (s in 0 until sections.length()) {
                val shelfObj = sections.optJSONObject(s)?.optJSONObject("musicCarouselShelfRenderer") ?: continue
                val hRuns = shelfObj.optJSONObject("header")
                    ?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                    ?.optJSONObject("title")?.optJSONArray("runs")
                var shelfTitle = ""
                if (hRuns != null) {
                    val sb = StringBuilder()
                    for (k in 0 until hRuns.length()) {
                        sb.append(hRuns.optJSONObject(k)?.optString("text", ""))
                    }
                    shelfTitle = sb.toString().trim()
                }
                if (shelfTitle.isBlank()) shelfTitle = defaultTitle

                val sRuns = shelfObj.optJSONObject("header")
                    ?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                    ?.optJSONObject("strapline")?.optJSONArray("runs")
                var strapline = ""
                if (sRuns != null) {
                    val sb = StringBuilder()
                    for (k in 0 until sRuns.length()) {
                        sb.append(sRuns.optJSONObject(k)?.optString("text", ""))
                    }
                    strapline = sb.toString().trim()
                }

                val items = shelfObj.optJSONArray("contents") ?: continue
                val tracks = mutableListOf<Track>()
                for (i in 0 until items.length()) {
                    val it = items.optJSONObject(i) ?: continue
                    val track = parseSingleTrackItem(it)
                    if (track != null) tracks.add(track)
                }

                if (tracks.isNotEmpty()) {
                    list.add(HomeSection(title = shelfTitle, subtitle = strapline, tracks = tracks))
                }
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "parseYtmSections error: ${e.message}")
        }
        return list
    }

    private fun parseSingleTrackItem(it: org.json.JSONObject): Track? {
        try {
            if (it.has("musicResponsiveListItemRenderer")) {
                val r = it.getJSONObject("musicResponsiveListItemRenderer")
                val cols = r.optJSONArray("flexColumns") ?: return null
                if (cols.length() < 2) return null

                val col0Runs = cols.optJSONObject(0)
                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.optJSONObject("text")?.optJSONArray("runs") ?: return null
                if (col0Runs.length() == 0) return null

                val title = col0Runs.optJSONObject(0)?.optString("text", "") ?: ""
                val videoId = col0Runs.optJSONObject(0)
                    ?.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("watchEndpoint")
                    ?.optString("videoId", "") ?: ""
                if (videoId.isBlank()) return null

                val col1Runs = cols.optJSONObject(1)
                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.optJSONObject("text")?.optJSONArray("runs")
                val artist = pickArtist(col1Runs)

                var durationSec = 0L
                if (col1Runs != null && col1Runs.length() > 0) {
                    val lastRunText = col1Runs.optJSONObject(col1Runs.length() - 1)?.optString("text", "") ?: ""
                    durationSec = parseDurationToSeconds(lastRunText)
                }

                var thumbUrl = ""
                val thumbs = r.optJSONObject("thumbnail")
                    ?.optJSONObject("musicThumbnailRenderer")
                    ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                if (thumbs != null && thumbs.length() > 0) {
                    thumbUrl = thumbs.optJSONObject(thumbs.length() - 1)?.optString("url", "") ?: ""
                }

                return Track(
                    id = videoId,
                    url = "https://www.youtube.com/watch?v=$videoId",
                    title = cleanTrackTitle(title),
                    artist = artist.replace(" - Topic", ""),
                    thumbnailUrl = upgradeThumbnailUrl(thumbUrl),
                    durationSeconds = durationSec
                )
            } else if (it.has("musicTwoRowItemRenderer")) {
                val r = it.getJSONObject("musicTwoRowItemRenderer")
                val titleRuns = r.optJSONObject("title")?.optJSONArray("runs") ?: return null
                if (titleRuns.length() == 0) return null

                val title = titleRuns.optJSONObject(0)?.optString("text", "") ?: ""
                val videoId = r.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("watchEndpoint")
                    ?.optString("videoId", "") ?: ""
                if (videoId.isBlank()) return null

                val subtitleRuns = r.optJSONObject("subtitle")?.optJSONArray("runs")
                val artist = pickArtist(subtitleRuns)

                var thumbUrl = ""
                val thumbs = r.optJSONObject("thumbnailRenderer")
                    ?.optJSONObject("musicThumbnailRenderer")
                    ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                if (thumbs != null && thumbs.length() > 0) {
                    thumbUrl = thumbs.optJSONObject(thumbs.length() - 1)?.optString("url", "") ?: ""
                }

                return Track(
                    id = videoId,
                    url = "https://www.youtube.com/watch?v=$videoId",
                    title = cleanTrackTitle(title),
                    artist = artist.replace(" - Topic", ""),
                    thumbnailUrl = upgradeThumbnailUrl(thumbUrl),
                    durationSeconds = 0L
                )
            }
        } catch (_: Exception) {}
        return null
    }

    /** Buang URL stream yang tersimpan (misalnya setelah gagal diputar / kedaluwarsa) supaya pemutaran berikutnya mengambil yang baru. */
    fun invalidateStream(url: String) {
        synchronized(streamUrlCache) {
            streamUrlCache.snapshot().keys.filter { it.startsWith("$url#") }.forEach { streamUrlCache.remove(it) }
        }
    }

    private fun isHttpAudio(s: org.schabi.newpipe.extractor.stream.AudioStream) = s.content?.startsWith("http") == true

    private fun isM4a(s: org.schabi.newpipe.extractor.stream.AudioStream) =
        s.format == org.schabi.newpipe.extractor.MediaFormat.M4A || s.format?.suffix.equals("m4a", ignoreCase = true)

    /** Bitrate yang bisa dipercaya; stream yang tidak melaporkan bitrate dianggap 0. */
    private fun bitrateOf(s: org.schabi.newpipe.extractor.stream.AudioStream) = s.averageBitrate.coerceAtLeast(0)

    private fun isOpus(s: org.schabi.newpipe.extractor.stream.AudioStream) =
        s.format == org.schabi.newpipe.extractor.MediaFormat.WEBMA_OPUS || s.format == org.schabi.newpipe.extractor.MediaFormat.WEBMA ||
            s.content?.contains("itag=251") == true || s.content?.contains("itag=250") == true || s.content?.contains("itag=249") == true

    /** Pilihan detail yang aktif untuk tingkat kualitas ini; kalau tidak cocok dipakai bawaan tingkat itu. */
    private fun activeSub(quality: id.nexrify.music.data.settings.AudioQuality): id.nexrify.music.data.settings.AudioSubQuality =
        runCatching {
            val active = id.nexrify.music.data.settings.SettingsManager.getInstance().audioSubQuality.value
            if (active.parent == quality) active else id.nexrify.music.data.settings.AudioSubQuality.defaultFor(quality)
        }.getOrDefault(id.nexrify.music.data.settings.AudioSubQuality.defaultFor(quality))

    private fun pickAudio(
        streams: List<org.schabi.newpipe.extractor.stream.AudioStream>,
        sub: id.nexrify.music.data.settings.AudioSubQuality
    ): org.schabi.newpipe.extractor.stream.AudioStream? {
        val http = streams.filter { isHttpAudio(it) }
        if (http.isEmpty()) return null
        val opus = http.filter { isOpus(it) }
        val m4a = http.filter { isM4a(it) }
        val known = { l: List<org.schabi.newpipe.extractor.stream.AudioStream> -> l.filter { bitrateOf(it) > 0 }.ifEmpty { l } }
        val pool = (if (sub.preferOpus) opus else m4a).ifEmpty { http }
        return when (sub) {
            id.nexrify.music.data.settings.AudioSubQuality.HIGH_OPUS_160 -> opus.maxByOrNull { bitrateOf(it) } ?: http.maxByOrNull { bitrateOf(it) }
            id.nexrify.music.data.settings.AudioSubQuality.HIGH_M4A_256 -> m4a.maxByOrNull { bitrateOf(it) } ?: http.maxByOrNull { bitrateOf(it) }
            id.nexrify.music.data.settings.AudioSubQuality.MED_M4A_128,
            id.nexrify.music.data.settings.AudioSubQuality.MED_OPUS_128 ->
                known(pool).minByOrNull { kotlin.math.abs(bitrateOf(it) - 128) } ?: known(http).minByOrNull { kotlin.math.abs(bitrateOf(it) - 128) }
            id.nexrify.music.data.settings.AudioSubQuality.LOW_OPUS_70 ->
                known(pool).minByOrNull { kotlin.math.abs(bitrateOf(it) - 70) } ?: known(http).minByOrNull { bitrateOf(it) }
            id.nexrify.music.data.settings.AudioSubQuality.LOW_OPUS_50 ->
                known(pool).minByOrNull { bitrateOf(it) } ?: known(http).minByOrNull { bitrateOf(it) }
            id.nexrify.music.data.settings.AudioSubQuality.LOW_M4A_48 ->
                known(m4a).minByOrNull { bitrateOf(it) } ?: known(http).minByOrNull { bitrateOf(it) }
        } ?: http.maxByOrNull { bitrateOf(it) } ?: http.first()
    }

    private fun mimeFor(s: org.schabi.newpipe.extractor.stream.AudioStream): String? =
        runCatching { s.format?.mimeType }.getOrNull()?.takeIf { it.startsWith("audio/") || it.startsWith("video/") }

    suspend fun getAudioStream(
        url: String,
        quality: id.nexrify.music.data.settings.AudioQuality = id.nexrify.music.data.settings.AudioQuality.HIGH,
        subQualityOverride: id.nexrify.music.data.settings.AudioSubQuality? = null
    ): ResolvedStream? = withContext(Dispatchers.IO) {
        val sub = subQualityOverride ?: activeSub(quality)
        val cacheKey = "$url#${quality.name}#${sub.name}"
        val now = System.currentTimeMillis()
        synchronized(streamUrlCache) {
            streamUrlCache.get(cacheKey)?.let { (cached, timestamp) ->
                if (now - timestamp < CACHE_EXPIRY_MS) return@withContext cached
            }
        }
        try {
            val streamInfo = StreamInfo.getInfo(service, url)
            val chosen = pickAudio(streamInfo.audioStreams, sub)
            val resolved = if (chosen != null) {
                // MIME diambil dari format stream yang benar-benar dipilih, bukan ditebak dari URL.
                ResolvedStream(chosen.content, mimeFor(chosen))
            } else {
                // Tidak ada stream audio terpisah: pakai stream video bergabung sebagai jalan terakhir.
                streamInfo.videoStreams.firstOrNull { it.content?.startsWith("http") == true }
                    ?.let { ResolvedStream(it.content, null) }
            }
            if (resolved != null) synchronized(streamUrlCache) { streamUrlCache.put(cacheKey, Pair(resolved, now)) }
            resolved
        } catch (e: Exception) {
            Log.e("MusicRepository", "Failed to get audio stream for $url: ${e.message}", e)
            null
        }
    }

    suspend fun getAudioStreamUrl(
        url: String,
        quality: id.nexrify.music.data.settings.AudioQuality = id.nexrify.music.data.settings.AudioQuality.HIGH
    ): String? = getAudioStream(url, quality)?.url

    /** Stream cadangan saat stream utama gagal: pilih format lain (Opus/WebM dulu), tanpa memakai cache. */
    suspend fun getFallbackStream(url: String, failedUrl: String? = null): ResolvedStream? = withContext(Dispatchers.IO) {
        try {
            val http = StreamInfo.getInfo(service, url).audioStreams.filter { isHttpAudio(it) && it.content != failedUrl }
            val webm = http.filter {
                it.format == org.schabi.newpipe.extractor.MediaFormat.WEBMA_OPUS ||
                    it.format == org.schabi.newpipe.extractor.MediaFormat.WEBMA
            }.maxByOrNull { bitrateOf(it) }
            val chosen = webm ?: http.maxByOrNull { bitrateOf(it) }
            chosen?.let { ResolvedStream(it.content, mimeFor(it)) }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getFallbackStreamUrl(url: String): String? = getFallbackStream(url)?.url

    suspend fun getLikedTracks(): List<Track> = withContext(Dispatchers.IO) {
        val auth = id.nexrify.music.data.auth.AuthManager.getInstance()
        val cookie = auth.getCookie()
        if (cookie.isNullOrBlank()) return@withContext emptyList()

        try {
            val origin = "https://music.youtube.com"
            val sapisidHash = auth.getSapisidHash(origin)

            val payload = org.json.JSONObject().apply {
                put("context", org.json.JSONObject().apply {
                    put("client", org.json.JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", YTM_CLIENT_VERSION)
                        put("hl", "id")
                        put("gl", "ID")
                    })
                })
                put("browseId", "FEmusic_liked_videos")
            }

            val client = HttpClientProvider.client
            val reqBody = payload.toString().toRequestBody("application/json".toMediaType())
            val reqBuilder = okhttp3.Request.Builder()
                .url("$origin/youtubei/v1/browse")
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Origin", origin)
                .header("Referer", "$origin/")
                .header("X-Origin", origin)
                .header("X-YouTube-Client-Name", "67")
                .header("X-YouTube-Client-Version", YTM_CLIENT_VERSION)
                .header("Cookie", cookie)
                .post(reqBody)

            if (sapisidHash != null) {
                reqBuilder.header("Authorization", sapisidHash)
            }

            val bodyStr = client.newCall(reqBuilder.build()).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
            }
            if (bodyStr.isNotBlank()) {
                val json = org.json.JSONObject(bodyStr)
                val contents = json.optJSONObject("contents")
                    ?.optJSONObject("singleColumnBrowseResultsRenderer")
                    ?.optJSONArray("tabs")
                    ?.optJSONObject(0)
                    ?.optJSONObject("tabRenderer")
                    ?.optJSONObject("content")
                    ?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents")

                val trackList = mutableListOf<Track>()
                if (contents != null) {
                    for (si in 0 until contents.length()) {
                        val items = contents.optJSONObject(si)
                            ?.optJSONObject("musicShelfRenderer")
                            ?.optJSONArray("contents") ?: continue
                        for (i in 0 until items.length()) {
                            val row = items.optJSONObject(i) ?: continue
                            val t = parseSingleTrackItem(row) ?: continue
                            if (t.title.isNotBlank()) trackList.add(t.copy(isFavorite = true))
                        }
                    }
                }
                if (trackList.isNotEmpty()) {
                    return@withContext trackList.distinctBy { it.id }
                }
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "Liked songs InnerTube fetch error: ${e.message}", e)
        }

        // Fallback: NewPipe playlist extractor
        try {
            val playlistExtractor = service.getPlaylistExtractor("https://www.youtube.com/playlist?list=LL")
            playlistExtractor.fetchPage()
            val page = playlistExtractor.initialPage
            page.items
                .filterIsInstance<StreamInfoItem>()
                .map { item ->
                    val rawThumb = item.thumbnails.maxByOrNull { it.width * it.height }?.url
                        ?: item.thumbnails.lastOrNull()?.url
                        ?: item.thumbnails.firstOrNull()?.url
                        ?: ""
                    Track(
                        id = item.url.substringAfter("v=").substringBefore("&"),
                        url = item.url,
                        title = cleanTrackTitle(item.name ?: "Unknown Title"),
                        artist = item.uploaderName?.replace(" - Topic", "") ?: "Unknown Artist",
                        thumbnailUrl = upgradeThumbnailUrl(rawThumb),
                        durationSeconds = item.duration.coerceAtLeast(0L),
                        isFavorite = true
                    )
                }
        } catch (e: Exception) {
            Log.e("MusicRepository", "Failed to fetch liked tracks via NewPipe: ${e.message}")
            emptyList()
        }
    }
}

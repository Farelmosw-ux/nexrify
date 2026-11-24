package id.nexrify.music.data.lyrics

import android.content.Context
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class LyricsRepository(private val context: Context) {

    private val httpClient = id.nexrify.music.data.remote.HttpClientProvider.client

    private val memCache = LruCache<String, LyricsData>(100)
    private val negativeCache = LruCache<String, Long>(200)
    private val NEGATIVE_CACHE_TTL = 24 * 60 * 60 * 1000L // 1 hari (dulu 7 hari)
    private val diskCacheDir = File(context.cacheDir, "lyrics_cache_v4").apply { mkdirs() }

    /** Catatan satu proses pencarian: apakah ada permintaan yang GAGAL (jaringan/server), bukan sekadar "tidak ada lirik". */
    private class Outcome { var failed = false }

    /** Hapus semua cache lirik satu lagu (dipakai tombol "Cari lirik lagi"). */
    fun clearCache(trackId: String) {
        synchronized(memCache) { memCache.remove(trackId) }
        synchronized(negativeCache) { negativeCache.remove(trackId) }
        runCatching { File(diskCacheDir, "$trackId.json").delete() }
        runCatching { File(diskCacheDir, "$trackId.empty").delete() }
    }

    suspend fun getLyrics(
        trackId: String,
        title: String,
        artist: String,
        durationSeconds: Long = 0L,
        autoTranslate: Boolean = true,
        forceRefresh: Boolean = false
    ): LyricsData? = withContext(Dispatchers.IO) {
        if (forceRefresh) clearCache(trackId)

        // 1. Negative cache: hanya untuk lagu yang terbukti tidak punya lirik (bukan karena gagal jaringan).
        synchronized(negativeCache) {
            val emptyAt = negativeCache.get(trackId)
            if (emptyAt != null && System.currentTimeMillis() - emptyAt < NEGATIVE_CACHE_TTL) return@withContext null
        }

        // 2. Cache memori
        synchronized(memCache) { memCache.get(trackId)?.let { return@withContext it } }

        // 3. Cache disk
        val diskFile = File(diskCacheDir, "$trackId.json")
        if (diskFile.exists()) {
            try {
                val cached = parseLyricsJson(diskFile.readText())
                if (cached != null && isValidLyricsLines(cached.lines)) {
                    synchronized(memCache) { memCache.put(trackId, cached) }
                    return@withContext cached
                } else diskFile.delete() // berkas rusak / isi tidak valid
            } catch (e: Exception) {
                Log.d("LyricsRepository", "Disk cache read error: ${e.message}")
                runCatching { diskFile.delete() }
            }
        }
        val emptyMarker = File(diskCacheDir, "$trackId.empty")
        if (emptyMarker.exists()) {
            if (System.currentTimeMillis() - emptyMarker.lastModified() < NEGATIVE_CACHE_TTL) {
                synchronized(negativeCache) { negativeCache.put(trackId, emptyMarker.lastModified()) }
                return@withContext null
            } else emptyMarker.delete()
        }

        // 4. Cari di internet
        val outcome = Outcome()
        var lyrics = fetchFromLrcLib(trackId, title, artist, durationSeconds, outcome)
        if (lyrics == null) lyrics = fetchFromYouTubeMusic(trackId, title, artist, outcome)
        if (lyrics != null && !isValidLyricsLines(lyrics.lines)) lyrics = null

        // 5. Terjemahan (opsional)
        if (lyrics != null && autoTranslate) {
            val translatedLines = translateLinesToIndonesian(lyrics.lines)
            if (translatedLines != null) lyrics = lyrics.copy(lines = translatedLines)
        }

        // 6. Simpan
        val found = lyrics
        if (found != null) {
            synchronized(memCache) { memCache.put(trackId, found) }
            try {
                pruneDiskCacheIfNeeded()
                diskFile.writeText(serializeLyricsJson(found))
                emptyMarker.delete()
            } catch (e: Exception) {
                Log.d("LyricsRepository", "Disk cache save error: ${e.message}")
            }
        } else if (!outcome.failed) {
            // Semua sumber menjawab dan memang tidak ada lirik: jangan tanya lagi dalam waktu dekat.
            synchronized(negativeCache) { negativeCache.put(trackId, System.currentTimeMillis()) }
            runCatching { emptyMarker.createNewFile() }
        }
        // Kalau gagal karena jaringan: TIDAK dicatat sebagai "tidak ada lirik", jadi dicoba lagi lain kali.
        found
    }

    private fun pruneDiskCacheIfNeeded() {
        try {
            val files = diskCacheDir.listFiles() ?: return
            if (files.size > 500) files.sortedBy { it.lastModified() }.take(files.size - 400).forEach { it.delete() }
        } catch (_: Exception) {}
    }

    // ---------------------------------------------------------------- pembersihan judul

    private val NOISE_WORDS =
        "official|audio|video|lyrics?|lirik|remaster(?:ed)?|visuali[sz]er|slowed|sped\\s*up|speed\\s*up|nightcore|reverb|hd|4k|hq|mv|m/v|version|\\+\\s*reverb|bass\\s*boosted|8d"
    private val BRACKET_NOISE = Regex("""\s*[\(\[][^\)\]]*\b(?:$NOISE_WORDS)\b[^\)\]]*[\)\]]""", RegexOption.IGNORE_CASE)
    private val FEAT = Regex("""\s*[\(\[]?\b(?:feat|ft)\b\.?.*""", RegexOption.IGNORE_CASE)
    private val TRAILING_NOISE = Regex("""\s*[-–|]\s*(?:topic|vevo|official.*|lyrics?.*|audio.*|slowed.*|sped\s*up.*)$""", RegexOption.IGNORE_CASE)
    private val SPEED_VARIANT = Regex("""\b(slowed|sped\s*up|speed\s*up|nightcore)\b""", RegexOption.IGNORE_CASE)

    private fun cleanSearchTerm(term: String): String =
        term.replace(BRACKET_NOISE, "")
            .replace(FEAT, "")
            .replace(TRAILING_NOISE, "")
            .replace(Regex("""\s*[-–|]\s*$"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

    /** Judul YouTube sering berbentuk "Artis - Lagu"; buang bagian artis kalau sama dengan nama artis. */
    private fun stripArtistPrefix(title: String, artist: String): String {
        val a = artist.trim()
        if (a.isBlank()) return title
        val m = Regex("""^\s*${Regex.escape(a)}\s*[-–:]\s*""", RegexOption.IGNORE_CASE).find(title)
        return if (m != null) title.substring(m.range.last + 1) else title
    }

    // ---------------------------------------------------------------- LRCLIB

    /** GET sederhana. Null = tidak ada hasil (404/kosong). Menandai outcome.failed untuk gangguan jaringan/server. */
    private fun lrcGet(url: String, outcome: Outcome): String? {
        return try {
            val req = Request.Builder().url(url).header("User-Agent", "NexrifyMusic/2.6.0 (Android)").build()
            httpClient.newCall(req).execute().use { resp ->
                when {
                    resp.isSuccessful -> resp.body?.string()
                    resp.code == 404 -> null
                    else -> { if (resp.code >= 500 || resp.code == 429 || resp.code == 408) outcome.failed = true; null }
                }
            }
        } catch (e: Exception) {
            Log.d("LyricsRepository", "LRCLIB request error: ${e.message}")
            outcome.failed = true
            null
        }
    }

    private fun parseArray(body: String?): List<JSONObject> {
        if (body.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(body)
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
        } catch (_: Exception) { emptyList() }
    }

    private class Pick(val json: JSONObject, val scale: Double)

    /** Pilih lirik ber-timestamp yang durasinya paling cocok. Untuk lagu slowed/sped-up, waktu lirik diskalakan ke durasi lagu. */
    private fun pickSynced(cands: List<JSONObject>, durationSeconds: Long, speedVariant: Boolean): Pick? {
        val synced = cands.filter { it.optString("syncedLyrics").isNotBlank() }
        if (synced.isEmpty()) return null
        if (durationSeconds <= 0) return Pick(synced.first(), 1.0)
        if (speedVariant) {
            // Versi slowed/sped up: durasinya sengaja berbeda dari aslinya. Ambil hasil paling relevan yang rasionya masuk akal.
            for (c in synced) {
                val d = c.optDouble("duration", 0.0)
                if (d <= 0.0) continue
                val ratio = durationSeconds / d
                if (ratio in 0.6..1.6) return Pick(c, ratio)
            }
            return null
        }
        val best = synced.minByOrNull { kotlin.math.abs(it.optDouble("duration", 0.0) - durationSeconds) } ?: return null
        // Selisih > 10 detik: timestamp hampir pasti meleset, lebih baik tidak dipakai.
        return if (kotlin.math.abs(best.optDouble("duration", 0.0) - durationSeconds) <= 10.0) Pick(best, 1.0) else null
    }

    private fun pickPlain(cands: List<JSONObject>, durationSeconds: Long): JSONObject? {
        val plain = cands.filter { it.optString("plainLyrics").isNotBlank() }
        if (plain.isEmpty()) return null
        return if (durationSeconds > 0) plain.minByOrNull { kotlin.math.abs(it.optDouble("duration", 0.0) - durationSeconds) } else plain.first()
    }

    private fun syncedLines(raw: String, scale: Double): List<LyricLine> {
        val lines = LrcParser.parse(raw)
        if (scale == 1.0) return lines
        // Lagu dipercepat/diperlambat: waktu tiap baris ikut diskalakan.
        return lines.map { it.copy(timeMs = (it.timeMs / scale).toLong()) }
    }

    private fun fetchFromLrcLib(
        trackId: String, origTitle: String, origArtist: String, durationSeconds: Long, outcome: Outcome
    ): LyricsData? {
        val speedVariant = SPEED_VARIANT.containsMatchIn(origTitle)
        val artist = cleanSearchTerm(origArtist)
        val title = cleanSearchTerm(stripArtistPrefix(origTitle, artist))
        if (title.isBlank()) return null

        // 1) Cocok persis (dengan durasi). Dilewati untuk versi slowed/sped up karena durasinya memang beda.
        if (durationSeconds > 0 && !speedVariant && artist.isNotBlank()) {
            lrcGet("https://lrclib.net/api/get?track_name=${encode(title)}&artist_name=${encode(artist)}&duration=$durationSeconds", outcome)?.let { body ->
                runCatching {
                    val j = JSONObject(body)
                    val synced = j.optString("syncedLyrics")
                    if (synced.isNotBlank()) {
                        val lines = LrcParser.parse(synced)
                        if (lines.isNotEmpty()) return LyricsData(trackId, origTitle, origArtist, lines, isSynced = true)
                    }
                }
            }
        }

        // 2) Pencarian berdasarkan judul + artis, lalu 3) pencarian umum.
        val queries = buildList {
            if (artist.isNotBlank()) add("https://lrclib.net/api/search?track_name=${encode(title)}&artist_name=${encode(artist)}")
            add("https://lrclib.net/api/search?q=${encode("$title $artist".trim())}")
        }
        var plainFallback: JSONObject? = null
        for (q in queries) {
            val cands = parseArray(lrcGet(q, outcome))
            if (cands.isEmpty()) continue
            pickSynced(cands, durationSeconds, speedVariant)?.let { pick ->
                val lines = syncedLines(pick.json.getString("syncedLyrics"), pick.scale)
                if (lines.isNotEmpty()) return LyricsData(trackId, origTitle, origArtist, lines, isSynced = true)
            }
            if (plainFallback == null) plainFallback = pickPlain(cands, durationSeconds)
        }
        // Tidak ada yang ber-timestamp: pakai lirik biasa (tanpa sinkronisasi) bila ada.
        plainFallback?.let {
            val lines = LrcParser.parsePlain(it.getString("plainLyrics"))
            if (lines.isNotEmpty()) return LyricsData(trackId, origTitle, origArtist, lines, isSynced = false)
        }
        return null
    }

    // ---------------------------------------------------------------- YouTube Music (lirik resmi, tanpa timestamp)

    private fun ytmPost(path: String, payload: JSONObject, outcome: Outcome): JSONObject? {
        return try {
            val req = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/$path")
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
                .header("X-YouTube-Client-Name", "67")
                .header("X-YouTube-Client-Version", "1.20240101.01.00")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    if (resp.code >= 500 || resp.code == 429) outcome.failed = true
                    null
                } else JSONObject(resp.body?.string().orEmpty())
            }
        } catch (e: Exception) {
            Log.d("LyricsRepository", "YTM request error: ${e.message}")
            outcome.failed = true
            null
        }
    }

    private fun ytmContext() = JSONObject().put("client", JSONObject()
        .put("clientName", "WEB_REMIX").put("clientVersion", "1.20240101.01.00").put("hl", "en").put("gl", "US"))

    private fun fetchFromYouTubeMusic(trackId: String, origTitle: String, origArtist: String, outcome: Outcome): LyricsData? {
        val next = ytmPost("next", JSONObject().put("context", ytmContext()).put("videoId", trackId), outcome) ?: return null
        val tabs = next.optJSONObject("contents")
            ?.optJSONObject("singleColumnMusicWatchNextResultsRenderer")
            ?.optJSONObject("tabbedRenderer")
            ?.optJSONObject("watchNextTabbedResultsRenderer")
            ?.optJSONArray("tabs") ?: return null
        var browseId: String? = null
        for (i in 0 until tabs.length()) {
            val tab = tabs.optJSONObject(i)?.optJSONObject("tabRenderer") ?: continue
            if (tab.optString("title", "").equals("Lyrics", ignoreCase = true) && !tab.optBoolean("unselectable", false)) {
                browseId = tab.optJSONObject("endpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
                break
            }
        }
        if (browseId.isNullOrBlank()) return null
        val browse = ytmPost("browse", JSONObject().put("context", ytmContext()).put("browseId", browseId), outcome) ?: return null
        val runs = browse.optJSONObject("contents")?.optJSONObject("sectionListRenderer")?.optJSONArray("contents")
            ?.optJSONObject(0)?.optJSONObject("musicDescriptionShelfRenderer")?.optJSONObject("description")?.optJSONArray("runs")
            ?: return null
        val sb = StringBuilder()
        for (j in 0 until runs.length()) sb.append(runs.optJSONObject(j)?.optString("text", "").orEmpty())
        val rawText = sb.toString().trim()
        if (!isValidLyricsText(rawText)) return null
        val lines = LrcParser.parsePlain(rawText)
        return if (isValidLyricsLines(lines)) LyricsData(trackId, origTitle, origArtist, lines, isSynced = false) else null
    }

    private fun isValidLyricsText(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.length < 25) return false
        val lower = trimmed.lowercase()
        if (lower == "null" || lower == "cancel" || lower == "batal") return false
        val lines = lower.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.all { it in listOf("null", "cancel", "batal", "ok", "error") }) return false
        return true
    }

    fun isValidLyricsLines(lines: List<LyricLine>): Boolean {
        if (lines.isEmpty()) return false
        val nonBlank = lines.filter { it.text.isNotBlank() }
        if (nonBlank.isEmpty()) return false
        if (nonBlank.size <= 2) {
            val combined = nonBlank.joinToString(" ") { it.text.lowercase().trim() }
            if (combined.contains("null") || combined.contains("batal") || combined.contains("cancel")) {
                return false
            }
        }
        return true
    }

    /** Terjemahkan lirik ke Bahasa Indonesia. Kalau sudah pernah diterjemahkan, tidak minta ulang ke server. */
    suspend fun translateLyrics(lyrics: LyricsData): LyricsData = withContext(Dispatchers.IO) {
        if (lyrics.lines.isEmpty()) return@withContext lyrics
        if (lyrics.lines.any { it.translation != null }) return@withContext lyrics
        // Gagal total (misalnya offline): kembalikan apa adanya dan JANGAN simpan, supaya dicoba lagi nanti.
        val translatedLines = translateLinesToIndonesian(lyrics.lines) ?: return@withContext lyrics
        val result = lyrics.copy(lines = translatedLines)
        synchronized(memCache) { memCache.put(lyrics.trackId, result) }
        try {
            File(diskCacheDir, "${lyrics.trackId}.json").writeText(serializeLyricsJson(result))
        } catch (_: Exception) {}
        result
    }

    /** Satu permintaan terjemahan untuk beberapa baris (dipisah newline). Null = gagal. */
    private fun translateRequest(texts: List<String>): String? {
        return try {
            val formBody = ("client=gtx&sl=auto&tl=id&dt=t&q=" + encode(texts.joinToString("\n")))
                .toRequestBody("application/x-www-form-urlencoded".toMediaType())
            val req = Request.Builder()
                .url("https://translate.googleapis.com/translate_a/single")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .post(formBody).build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val segments = JSONArray(resp.body?.string().orEmpty()).optJSONArray(0) ?: return@use null
                val sb = StringBuilder()
                for (i in 0 until segments.length()) sb.append(segments.optJSONArray(i)?.optString(0).orEmpty())
                sb.toString()
            }
        } catch (e: Exception) {
            Log.d("LyricsRepository", "Translation error: ${e.message}")
            null
        }
    }

    /**
     * Terjemahkan satu kelompok baris dan pastikan jumlah hasilnya sama dengan jumlah baris asli.
     * Kalau tidak sama (Google menggabung/memecah baris), kelompok dibelah dua dan dicoba lagi,
     * jadi terjemahan tidak pernah bergeser ke baris yang salah.
     */
    private fun translateGroup(texts: List<String>, budget: IntArray): List<String?>? {
        if (texts.isEmpty()) return emptyList()
        if (budget[0] <= 0) return null
        budget[0]--
        val out = translateRequest(texts) ?: return null
        val parts = out.split("\n").map { it.trim() }
        if (parts.size == texts.size) return parts
        if (texts.size == 1) return listOf(parts.joinToString(" ").trim())
        val mid = texts.size / 2
        val a = translateGroup(texts.subList(0, mid), budget) ?: return null
        val b = translateGroup(texts.subList(mid, texts.size), budget) ?: return null
        return a + b
    }

    private fun translateLinesToIndonesian(lines: List<LyricLine>): List<LyricLine>? {
        if (lines.isEmpty()) return lines
        // Hanya baris yang berisi huruf yang diterjemahkan (♪, tanda baca, dll dilewati).
        val idx = lines.indices.filter { i -> lines[i].text.any { it.isLetter() } }
        if (idx.isEmpty()) return lines
        val translated = arrayOfNulls<String>(lines.size)
        val budget = intArrayOf(60) // batas permintaan per lagu
        var anyOk = false
        for (chunk in idx.chunked(40)) {
            val res = translateGroup(chunk.map { lines[it].text.replace("\n", " ") }, budget) ?: continue
            anyOk = true
            chunk.forEachIndexed { k, lineIdx -> translated[lineIdx] = res.getOrNull(k) }
        }
        if (!anyOk) return null
        return lines.mapIndexed { i, line ->
            val t = translated[i]?.trim()
            val valid = if (!t.isNullOrBlank() && !t.equals(line.text.trim(), ignoreCase = true)) t else null
            line.copy(translation = valid)
        }
    }

    private fun encode(str: String): String = URLEncoder.encode(str, "UTF-8")

    private fun serializeLyricsJson(data: LyricsData): String {
        val root = JSONObject().apply {
            put("trackId", data.trackId)
            put("title", data.title)
            put("artist", data.artist)
            put("isSynced", data.isSynced)
            val linesArr = JSONArray()
            data.lines.forEach { line ->
                val lineObj = JSONObject().apply {
                    put("timeMs", line.timeMs)
                    put("text", line.text)
                    if (line.translation != null) put("translation", line.translation)
                }
                linesArr.put(lineObj)
            }
            put("lines", linesArr)
        }
        return root.toString()
    }

    private fun parseLyricsJson(jsonStr: String): LyricsData? {
        return try {
            val root = JSONObject(jsonStr)
            val trackId = root.getString("trackId")
            val title = root.getString("title")
            val artist = root.getString("artist")
            val isSynced = root.optBoolean("isSynced", true)
            val linesArr = root.getJSONArray("lines")
            val lines = mutableListOf<LyricLine>()
            for (i in 0 until linesArr.length()) {
                val obj = linesArr.getJSONObject(i)
                lines.add(
                    LyricLine(
                        timeMs = obj.getLong("timeMs"),
                        text = obj.getString("text"),
                        translation = if (obj.has("translation")) obj.getString("translation") else null
                    )
                )
            }
            LyricsData(trackId, title, artist, lines, isSynced)
        } catch (e: Exception) {
            null
        }
    }
}

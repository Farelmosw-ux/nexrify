package id.nexrify.music.data.playlist

import android.net.Uri
import id.nexrify.music.data.account.AccountError
import id.nexrify.music.data.account.AccountManager
import id.nexrify.music.data.model.Track
import id.nexrify.music.data.remote.HttpClientProvider
import id.nexrify.music.data.remote.NexrifyLinks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Playlist milik orang lain yang dibuka lewat tautan. */
data class SharedPlaylist(
    val id: String,
    val name: String,
    val coverUrl: String,
    val ownerName: String,
    val tracks: List<Track>,
    val url: String
)

/** Permintaan buka tautan playlist (dari deep link). Diisi MainActivity, dikonsumsi layar utama. */
object PlaylistShareNav {
    val request = MutableStateFlow<String?>(null)

    /** Ambil id playlist dari tautan https://nexrify.biz.id/playlist/<username>/<id> (juga tautan lama /playlist/usr/<id>). */
    fun parseId(uri: Uri?): String? {
        uri ?: return null
        if (!uri.host.equals("nexrify.biz.id", ignoreCase = true) && !uri.host.equals("www.nexrify.biz.id", ignoreCase = true)) return null
        val seg = uri.pathSegments
        if (seg.size < 3 || seg[0] != "playlist" || seg[1].isBlank()) return null
        return seg[2].takeIf { ID_RE.matches(it) }
    }

    fun parseText(text: String): String? =
        Regex("""nexrify\.biz\.id/playlist/[0-9A-Za-z_.\-]+/([0-9A-Za-z]{8})(?![0-9A-Za-z])""").find(text)?.groupValues?.get(1)

    private val ID_RE = Regex("^[0-9A-Za-z]{8}$")
}

object PlaylistShare {
    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    private val client by lazy { HttpClientProvider.client.newBuilder().callTimeout(25, TimeUnit.SECONDS).build() }
    private const val ENDPOINT = NexrifyLinks.SITE + "/api/user"

    /** Tautan publik: https://nexrify.biz.id/playlist/<username pemilik>/<id>. */
    fun linkFor(id: String, username: String? = null): String {
        val name = (username ?: AccountManager.getInstance().user.value?.username).orEmpty()
            .lowercase().filter { it.isLetterOrDigit() || it == '_' }.ifBlank { "usr" }
        return "${NexrifyLinks.SITE}/playlist/$name/$id"
    }

    private fun execute(request: Request): JSONObject {
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(text) }.getOrNull() ?: throw AccountError("server", "HTTP ${response.code}")
                if (!json.optBoolean("ok", false)) {
                    throw AccountError(json.optString("code", "server"), json.optString("error", "HTTP ${response.code}"))
                }
                return json
            }
        } catch (e: AccountError) {
            throw e
        } catch (e: IOException) {
            throw AccountError("network", e.message ?: "network")
        }
    }

    /** Buat atau perbarui tautan playlist. Hanya sampul https yang ikut dibagikan (file:// lokal tidak bisa dilihat orang lain). */
    suspend fun share(playlist: Playlist): String = withContext(Dispatchers.IO) {
        val token = AccountManager.getInstance().token() ?: throw AccountError("auth", "auth")
        val tracks = JSONArray()
        for (t in playlist.tracks) {
            tracks.put(
                JSONObject().put("id", t.id).put("url", t.url).put("title", t.title).put("artist", t.artist)
                    .put("thumbnailUrl", t.thumbnailUrl).put("durationSeconds", t.durationSeconds)
            )
        }
        val body = JSONObject().put("action", "share").put("name", playlist.name).put("shareId", playlist.shareId)
            .put("coverUrl", playlist.coverUrl.takeIf { it.startsWith("https://") } ?: "").put("tracks", tracks)
        val json = execute(
            Request.Builder().url(ENDPOINT).header("Accept", "application/json").header("User-Agent", "Nexrify-Android")
                .header("Authorization", "Bearer $token").post(body.toString().toRequestBody(JSON_TYPE)).build()
        )
        json.getString("id")
    }

    suspend fun unshare(shareId: String) = withContext(Dispatchers.IO) {
        val token = AccountManager.getInstance().token() ?: throw AccountError("auth", "auth")
        val body = JSONObject().put("action", "unshare").put("shareId", shareId)
        execute(
            Request.Builder().url(ENDPOINT).header("Accept", "application/json").header("User-Agent", "Nexrify-Android")
                .header("Authorization", "Bearer $token").post(body.toString().toRequestBody(JSON_TYPE)).build()
        )
        Unit
    }

    /** Ambil playlist publik. Tidak perlu login. */
    suspend fun fetch(id: String): SharedPlaylist = withContext(Dispatchers.IO) {
        val json = execute(
            Request.Builder().url("$ENDPOINT?pl=${Uri.encode(id)}").header("Accept", "application/json").header("User-Agent", "Nexrify-Android").get().build()
        )
        val p = json.getJSONObject("playlist")
        val arr = p.optJSONArray("tracks") ?: JSONArray()
        val tracks = buildList {
            for (i in 0 until arr.length()) {
                val t = arr.optJSONObject(i) ?: continue
                val tid = t.optString("id")
                if (tid.isBlank()) continue
                add(
                    Track(
                        id = tid, url = t.optString("url"), title = t.optString("title"), artist = t.optString("artist"),
                        thumbnailUrl = t.optString("thumbnailUrl"), durationSeconds = t.optLong("durationSeconds", 0L)
                    )
                )
            }
        }
        val owner = p.optJSONObject("owner")
        SharedPlaylist(
            id = p.optString("id", id), name = p.optString("name", "Playlist"), coverUrl = p.optString("coverUrl", ""),
            ownerName = owner?.optString("display").orEmpty().ifBlank { owner?.optString("username").orEmpty() },
            tracks = tracks, url = linkFor(id, owner?.optString("username")?.takeIf { it.isNotBlank() })
        )
    }
}

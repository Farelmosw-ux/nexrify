package id.nexrify.music.data.chat

import id.nexrify.music.data.account.AccountError
import id.nexrify.music.data.remote.HttpClientProvider
import id.nexrify.music.data.remote.NexrifyLinks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Klien /api/chat di nexrify.biz.id. */
object ChatApi {

    private val client by lazy { HttpClientProvider.client.newBuilder().callTimeout(15, TimeUnit.SECONDS).build() }
    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    private const val PATH = "/api/chat"

    fun avatarUrl(uid: String, av: String): String = "${NexrifyLinks.SITE}$PATH?avatar=$uid&v=$av"
    fun coverUrl(uid: String, v: String): String = "${NexrifyLinks.SITE}$PATH?cover=$uid&v=$v"
    fun groupPhotoUrl(v: String): String = "${NexrifyLinks.SITE}$PATH?group_photo=1&v=$v"

    private fun execute(request: Request): JSONObject {
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(text) }.getOrNull()
                    ?: throw AccountError("server", "HTTP ${response.code}")
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

    suspend fun get(token: String, params: Map<String, String>): JSONObject = withContext(Dispatchers.IO) {
        val url = (NexrifyLinks.SITE + PATH).toHttpUrl().newBuilder().apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        execute(
            Request.Builder().url(url).get()
                .header("Accept", "application/json").header("User-Agent", "Nexrify-Android")
                .header("Authorization", "Bearer $token").build()
        )
    }

    /** Unduh audio VN ke [dest]. Melempar AccountError("vn_gone") kalau sudah kedaluwarsa. */
    suspend fun downloadVoice(token: String, id: String, dest: java.io.File) = withContext(Dispatchers.IO) {
        val url = (NexrifyLinks.SITE + PATH).toHttpUrl().newBuilder().addQueryParameter("vn", id).build()
        val req = Request.Builder().url(url).get().header("User-Agent", "Nexrify-Android").header("Authorization", "Bearer $token").build()
        val c = client.newBuilder().callTimeout(30, TimeUnit.SECONDS).build()
        try {
            c.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw AccountError(if (r.code == 404) "vn_gone" else "server", "HTTP ${r.code}")
                val body = r.body ?: throw AccountError("server", "empty")
                dest.parentFile?.mkdirs()
                val tmp = java.io.File(dest.parentFile, dest.name + ".part")
                body.byteStream().use { inp -> tmp.outputStream().use { out -> inp.copyTo(out) } }
                if (!tmp.renameTo(dest)) throw AccountError("server", "rename")
            }
        } catch (e: AccountError) {
            throw e
        } catch (e: IOException) {
            throw AccountError("network", e.message ?: "network")
        }
    }

    /** Unduh foto chat pribadi. 404 berarti sudah kedaluwarsa atau bukan milik percakapan ini. */
    suspend fun downloadDmImage(token: String, id: String, withUid: String, dest: java.io.File) = withContext(Dispatchers.IO) {
        val url = (NexrifyLinks.SITE + PATH).toHttpUrl().newBuilder().addQueryParameter("dmimg", id).addQueryParameter("with", withUid).build()
        val req = Request.Builder().url(url).get().header("User-Agent", "Nexrify-Android").header("Authorization", "Bearer $token").build()
        val c = client.newBuilder().callTimeout(30, TimeUnit.SECONDS).build()
        try {
            c.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw AccountError(if (r.code == 404) "img_gone" else "server", "HTTP ${r.code}")
                val body = r.body ?: throw AccountError("server", "empty")
                dest.parentFile?.mkdirs()
                val tmp = java.io.File(dest.parentFile, dest.name + ".part")
                body.byteStream().use { inp -> tmp.outputStream().use { out -> inp.copyTo(out) } }
                if (!tmp.renameTo(dest)) throw AccountError("server", "rename")
            }
        } catch (e: AccountError) {
            throw e
        } catch (e: IOException) {
            throw AccountError("network", e.message ?: "network")
        }
    }

    suspend fun post(token: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        execute(
            Request.Builder().url(NexrifyLinks.SITE + PATH).post(body.toString().toRequestBody(JSON_TYPE))
                .header("Accept", "application/json").header("User-Agent", "Nexrify-Android")
                .header("Authorization", "Bearer $token").build()
        )
    }

    suspend fun feed(token: String, ev: Long): ChatFeed {
        val j = get(token, mapOf("feed" to "1", "ev" to ev.toString()))
        val typing = j.optJSONArray("typing").toStringList()
        val arr = j.optJSONArray("messages") ?: JSONArray()
        val msgs = buildList { for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { add(parseMessage(it)) } }
        val seenArr = j.optJSONArray("seen")
        val seen = buildList { if (seenArr != null) for (i in 0 until seenArr.length()) seenArr.optJSONObject(i)?.let { add(ChatSeen(it.optString("uid"), it.optLong("seq"))) } }
        return ChatFeed(j.optLong("ev", ev), j.optBoolean("reset", false), typing, msgs, seen)
    }

    suspend fun notifs(token: String, since: Long): Pair<Long, List<ChatNotif>> {
        val j = get(token, mapOf("notifs" to "1", "since" to since.toString()))
        val arr = j.optJSONArray("items") ?: JSONArray()
        val items = buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(ChatNotif(o.optString("k"), o.optLong("seq"), o.optString("from"), o.optString("text"), o.optLong("ts")))
            }
        }
        return j.optLong("now", System.currentTimeMillis()) to items
    }

    suspend fun profile(token: String, username: String): ChatProfile =
        parseProfile(get(token, mapOf("profile" to username)).getJSONObject("profile"))

    suspend fun users(token: String, prefix: String): List<ChatUserHit> {
        val arr = get(token, mapOf("users" to prefix)).optJSONArray("users") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(ChatUserHit(o.optString("username"), o.optString("uid"), o.optString("display")))
            }
        }
    }

    suspend fun group(token: String): ChatGroup = parseGroup(get(token, mapOf("group" to "1")).getJSONObject("group"))

    suspend fun members(token: String): List<ChatMember> {
        val arr = get(token, mapOf("members" to "1")).optJSONArray("members") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(ChatMember(o.optString("uid"), o.optString("username"), o.optString("display"), o.optString("role"), o.optBoolean("online"), o.optLong("lastSeen")))
            }
        }
    }

    fun parseGroup(o: JSONObject) = ChatGroup(
        name = o.optString("name", "Global Chat"), desc = o.optString("desc"), photoV = o.optString("photoV"),
        hasPhoto = o.optBoolean("hasPhoto"), members = o.optInt("members"), online = o.optInt("online"),
        myRole = o.optString("myRole"), canEdit = o.optBoolean("canEdit"),
        pins = o.optJSONArray("pins").let { arr -> if (arr == null) emptyList() else buildList { for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { add(parseMessage(it)) } } }
    )

    fun parseProfile(o: JSONObject): ChatProfile {
        val top = o.optJSONArray("top")
        return ChatProfile(
            uid = o.optString("uid"), username = o.optString("username"), display = o.optString("display"),
            av = o.optString("av"), joined = o.optLong("joined"), bio = o.optString("bio"), status = o.optString("status"),
            messages = o.optInt("messages"), reactionsReceived = o.optInt("reactionsReceived"),
            liked = o.optInt("liked"), playlists = o.optInt("playlists"),
            top = buildList {
                if (top != null) for (i in 0 until top.length()) top.optJSONObject(i)?.let { add(ChatTopTrack(it.optString("t"), it.optString("a"))) }
            },
            badges = o.optJSONArray("badges").toStringList(),
            blockedByMe = o.optBoolean("blockedByMe", false),
            online = o.optBoolean("online", false),
            lastSeen = o.optLong("lastSeen"), presenceHidden = o.optBoolean("presenceHidden"), profileHidden = o.optBoolean("profileHidden"),
            musicHidden = o.optBoolean("musicHidden"), chatBlocked = o.optBoolean("chatBlocked"),
            donateRank = o.optInt("donateRank", 0),
            titleOn = o.optBoolean("titleOn", true),
            titles = parseTitles(o.optJSONArray("titles")),
            titlesOwned = parseTitles(o.optJSONArray("titlesOwned")),
            titlesMax = o.optInt("titlesMax", 3),
            titlesInfo = parseTitlesInfo(o.optJSONObject("titlesInfo")),
            followers = o.optInt("followers"), following = o.optInt("following"), friends = o.optInt("friends"),
            iFollow = o.optBoolean("iFollow"), followsMe = o.optBoolean("followsMe"), friend = o.optBoolean("friend"),
            followHidden = o.optBoolean("followHidden"), hasCover = o.optBoolean("hasCover"), coverV = o.optString("coverV"),
            creatorApp = o.optJSONObject("creatorApp")?.let {
                CreatorApp(it.optString("status"), it.optString("platform"), it.optLong("createdAt"), it.optLong("reviewedAt"), it.optString("reason"))
            }
        )
    }

    /** kind: followers | following | friends. cursor 0 = halaman pertama. */
    suspend fun follows(token: String, username: String, kind: String, cursor: Int): FollowPage {
        val o = get(token, mapOf("follows" to username, "kind" to kind, "cursor" to cursor.toString()))
        val arr = o.optJSONArray("items")
        val items = buildList {
            if (arr != null) for (i in 0 until arr.length()) {
                val r = arr.optJSONObject(i) ?: continue
                add(FollowUser(r.optString("uid"), r.optString("username"), r.optString("display"), r.optString("av"),
                    r.optBoolean("iFollow"), r.optBoolean("followsMe"), r.optBoolean("friend")))
            }
        }
        return FollowPage(items, o.optInt("next"), o.optInt("total"), o.optBoolean("hidden"))
    }

    fun parseTitles(arr: JSONArray?): List<ProfileTitle> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val t = arr.optJSONObject(i) ?: continue
                val code = t.optString("c")
                if (code.isNotEmpty()) add(ProfileTitle(code, t.optString("p"), t.optInt("r"), t.optInt("n")))
            }
        }
    }

    fun parseTitlesInfo(o: JSONObject?): Map<String, TitleProgress> {
        if (o == null) return emptyMap()
        return buildMap {
            o.keys().forEach { code ->
                val i = o.optJSONObject(code) ?: return@forEach
                val h = i.optJSONObject("holder")
                put(code, TitleProgress(
                    mine = i.optLong("mine"), need = i.optLong("need"), min = i.optLong("min"), resetAt = i.optLong("resetAt"),
                    hasHolder = h != null, holderName = h?.optString("username").orEmpty(),
                    holderValue = if (h == null) 0L else if (h.has("ms")) h.optLong("ms") else h.optLong("n"),
                    holderMe = h?.optBoolean("me") ?: false,
                    cur = i.optLong("cur"), goal = i.optLong("goal")
                ))
            }
        }
    }

    /** Title khusus (kreator, pendengar terlama, top streak, pencapaian) per username huruf kecil. */
    suspend fun titleDirectory(token: String): Map<String, List<ProfileTitle>> {
        val o = get(token, mapOf("titles" to "1")).optJSONObject("titles") ?: return emptyMap()
        return buildMap { o.keys().forEach { k -> put(k.lowercase(), parseTitles(o.optJSONArray(k))) } }
    }

    fun parseMessage(o: JSONObject): ChatMessage {
        val rx = o.optJSONObject("reactions")
        val reactions = buildMap<String, List<String>> {
            if (rx != null) rx.keys().forEach { k -> put(k, rx.optJSONArray(k).toStringList()) }
        }
        val r = o.optJSONObject("reply")
        return ChatMessage(
            seq = o.optLong("seq"), uid = o.optString("uid"), username = o.optString("username"), display = o.optString("display"),
            badges = o.optJSONArray("badges").toStringList(), av = o.optString("av"), text = o.optString("text"),
            ts = o.optLong("ts"), edited = o.optLong("edited"), deleted = o.optBoolean("deleted"),
            announce = o.optBoolean("announce"), mentions = o.optJSONArray("mentions").toStringList(),
            reply = r?.let { ChatReply(it.optLong("seq"), it.optString("username"), it.optString("text"), it.optBoolean("deleted")) },
            reactions = reactions,
            pinned = o.optBoolean("pinned", false),
            voice = o.optJSONObject("voice")?.let { v -> ChatVoice(v.optString("id"), v.optLong("durMs"), v.optString("wave")) },
            onceText = o.optBoolean("onceText"), onceViewed = o.optBoolean("onceViewed"),
            call = o.optJSONObject("call")?.let { v -> DmCall(v.optString("status"), v.optLong("dur")) },
            image = o.optJSONObject("image")?.let { v -> DmImage(v.optString("id"), v.optInt("w", 1), v.optInt("h", 1), v.optBoolean("once"), v.optBoolean("viewed")) },
            poll = o.optJSONObject("poll")?.let { p ->
                val opts = p.optJSONArray("options")
                ChatPoll(
                    question = p.optString("question"),
                    options = buildList { if (opts != null) for (i in 0 until opts.length()) opts.optJSONObject(i)?.let { add(ChatPollOption(it.optString("text"), it.optInt("votes"))) } },
                    total = p.optInt("total"), closesAt = p.optLong("closesAt"), closed = p.optBoolean("closed")
                )
            }
        )
    }

    suspend fun dmInbox(token: String): Pair<Int, List<DmConversation>> {
        val j = get(token, mapOf("dminbox" to "1"))
        val arr = j.optJSONArray("conversations") ?: JSONArray()
        val list = buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(
                    DmConversation(
                        o.optString("uid"), o.optString("username"), o.optString("display"), o.optString("av"),
                        o.optString("last"), o.optLong("ts"), o.optInt("unread"), o.optBoolean("fromMe"),
                        o.optBoolean("pinned"), o.optBoolean("muted"), o.optBoolean("archived"), o.optBoolean("request"), o.optBoolean("note")
                    )
                )
            }
        }
        return j.optInt("unread") to list
    }

    suspend fun dmThread(token: String, uid: String, after: Long): DmThreadData {
        val j = get(token, mapOf("dm" to uid, "after" to after.toString()))
        val p = j.getJSONObject("peer")
        val arr = j.optJSONArray("messages") ?: JSONArray()
        return DmThreadData(
            peer = DmPeer(p.optString("id"), p.optString("username"), p.optString("display"), p.optString("av")),
            messages = buildList { for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { add(parseDm(it)) } },
            typing = j.optBoolean("typing"), peerRead = j.optLong("peerRead"), blocked = j.optBoolean("blocked"), ev = j.optLong("ev", after),
            stars = j.optJSONArray("stars")?.let { a -> buildSet { for (i in 0 until a.length()) add(a.optLong(i)) } } ?: emptySet(),
            disappear = j.optLong("disappear"),
            request = j.optBoolean("request"), pending = j.optBoolean("pending")
        )
    }

    fun parseDm(o: JSONObject): DmMessage {
        val r = o.optJSONObject("reply")
        return DmMessage(
            seq = o.optLong("seq"), uid = o.optString("uid"), text = o.optString("text"), ts = o.optLong("ts"),
            edited = o.optLong("edited"), deleted = o.optBoolean("deleted"), ev = o.optLong("ev"),
            reply = r?.let { DmReply(it.optLong("seq"), it.optString("uid"), it.optString("text")) },
            reactions = o.optJSONObject("reactions")?.let { rx ->
                buildMap<String, List<String>> { rx.keys().forEach { k -> put(k, rx.optJSONArray(k).toStringList()) } }
            } ?: emptyMap(),
            song = o.optJSONObject("song")?.let { sg ->
                DmSong(sg.optString("id"), sg.optString("title"), sg.optString("artist"), sg.optString("thumb"), sg.optString("url"), sg.optLong("dur"))
            },
            forwarded = o.optBoolean("forwarded"), expires = o.optLong("expires"),
            voice = o.optJSONObject("voice")?.let { v -> ChatVoice(v.optString("id"), v.optLong("durMs"), v.optString("wave")) }
        )
    }

    suspend fun linkPreview(token: String, url: String): LinkPreview? {
        val o = get(token, mapOf("linkprev" to url)).optJSONObject("preview") ?: return null
        val title = o.optString("title")
        if (title.isBlank()) return null
        return LinkPreview(o.optString("site"), title, o.optString("author"), o.optString("thumb"), o.optString("url"))
    }

    suspend fun peerExtra(token: String, uid: String): PeerExtra {
        val j = get(token, mapOf("peerextra" to uid))
        val m = j.optJSONObject("mood")
        val n = j.optJSONObject("np")
        return PeerExtra(m?.optString("e").orEmpty(), m?.optString("t").orEmpty(), n?.optString("t").orEmpty(), n?.optString("a").orEmpty())
    }

    suspend fun dmStars(token: String, uid: String): List<DmMessage> {
        val j = get(token, mapOf("dmstars" to uid))
        val arr = j.optJSONArray("messages") ?: JSONArray()
        return buildList { for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { add(parseDm(it)) } }
    }

    suspend fun dmSearch(token: String, uid: String, q: String): List<DmMessage> {
        val j = get(token, mapOf("dmsearch" to uid, "q" to q))
        val arr = j.optJSONArray("messages") ?: JSONArray()
        return buildList { for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { add(parseDm(it)) } }
    }

    private fun JSONArray?.toStringList(): List<String> =
        if (this == null) emptyList() else buildList { for (i in 0 until length()) add(optString(i)) }
}

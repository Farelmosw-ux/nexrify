package id.nexrify.music.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class UserAccount(
    val name: String,
    val email: String,
    val channelHandle: String,
    val avatarUrl: String,
    val cookie: String
)

class AuthManager private constructor(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val httpClient = id.nexrify.music.data.remote.HttpClientProvider.client

    private val _authState = MutableStateFlow<UserAccount?>(loadStoredAccount())
    val authState: StateFlow<UserAccount?> = _authState.asStateFlow()

    private fun loadStoredAccount(): UserAccount? {
        val cookie = sanitizeCookie(prefs.getString(KEY_COOKIE, null).orEmpty()).takeIf { it.isNotBlank() } ?: return null
        var name = prefs.getString(KEY_NAME, "") ?: ""
        var email = prefs.getString(KEY_EMAIL, "") ?: ""
        var handle = prefs.getString(KEY_HANDLE, "") ?: ""
        val avatar = prefs.getString(KEY_AVATAR, "") ?: ""

        // Sanitize if previously saved as raw JSON object string
        if (name.startsWith("{") || name.contains("\"runs\"")) {
            name = parseRawJsonRuns(name).ifBlank { "YouTube User" }
        }
        if (email.startsWith("{") || email.contains("\"runs\"")) {
            email = parseRawJsonRuns(email)
        }
        if (handle.startsWith("{") || handle.contains("\"runs\"")) {
            handle = parseRawJsonRuns(handle)
        }

        if (name.isBlank() || name.equals("YouTube User", ignoreCase = true) || name.equals("Nexrify User", ignoreCase = true)) {
            name = email.substringBefore('@').takeIf { it.isNotBlank() } ?: ""
        }
        return UserAccount(name, email, handle, avatar, cookie)
    }

    private fun parseRawJsonRuns(jsonStr: String): String {
        return try {
            val json = JSONObject(jsonStr)
            val runs = json.optJSONArray("runs")
            if (runs != null && runs.length() > 0) {
                runs.optJSONObject(0)?.optString("text", "") ?: ""
            } else {
                json.optString("simpleText", "")
            }
        } catch (_: Exception) {
            jsonStr
        }
    }

    fun isLoggedIn(): Boolean = _authState.value != null

    fun getCookie(): String? = _authState.value?.cookie

    fun parseCookies(cookieStr: String): Map<String, String> {
        return cookieStr.split(";")
            .map { it.trim() }
            .filter { it.contains("=") }
            .associate {
                val idx = it.indexOf("=")
                it.substring(0, idx).trim() to it.substring(idx + 1).trim()
            }
    }

    fun getSapisidHash(origin: String = "https://music.youtube.com"): String? {
        val cookie = getCookie() ?: return null
        val map = parseCookies(cookie)
        val time = System.currentTimeMillis() / 1000
        fun hash(secret: String) = "${time}_${sha1Hex("$time $secret $origin")}"
        val main = map["SAPISID"] ?: map["__Secure-3PAPISID"] ?: map["__Secure-1PAPISID"] ?: return null
        // YouTube terbaru memeriksa tiga hash sekaligus bila cookie-nya ada; yang tidak ada dilewati.
        val parts = mutableListOf("SAPISIDHASH ${hash(main)}")
        map["__Secure-1PAPISID"]?.let { parts += "SAPISID1PHASH ${hash(it)}" }
        map["__Secure-3PAPISID"]?.let { parts += "SAPISID3PHASH ${hash(it)}" }
        return parts.joinToString(" ")
    }

    /** Header Cookie hanya boleh berisi ASCII yang bisa dicetak; karakter lain membuat OkHttp melempar exception. */
    fun sanitizeCookie(raw: String): String =
        raw.filter { it.code in 0x20..0x7E }.trim().trim(';').trim()

    /** Apakah cookie memuat kunci yang dibutuhkan untuk permintaan terautentikasi. */
    fun hasAuthCookies(cookie: String): Boolean {
        val m = parseCookies(cookie)
        return m.containsKey("SAPISID") || m.containsKey("__Secure-3PAPISID") || m.containsKey("__Secure-1PAPISID")
    }

    private fun sha1Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun handleLoginSuccess(rawCookie: String, initialName: String = "", initialAvatar: String = "") {
        val cookie = sanitizeCookie(rawCookie)
        if (cookie.isBlank() || !hasAuthCookies(cookie)) return
        val savedName = if (initialName.isNotBlank()) initialName else (prefs.getString(KEY_NAME, "YouTube User") ?: "YouTube User")
        val savedAvatar = if (initialAvatar.isNotBlank()) initialAvatar else (prefs.getString(KEY_AVATAR, "") ?: "")

        val initialAccount = UserAccount(
            name = savedName,
            email = prefs.getString(KEY_EMAIL, "") ?: "",
            channelHandle = prefs.getString(KEY_HANDLE, "") ?: "",
            avatarUrl = savedAvatar,
            cookie = cookie
        )
        prefs.edit()
            .putString(KEY_COOKIE, cookie)
            .apply {
                if (initialName.isNotBlank()) putString(KEY_NAME, initialName)
                if (initialAvatar.isNotBlank()) putString(KEY_AVATAR, initialAvatar)
            }
            .apply()

        _authState.value = initialAccount

        // Fetch detailed profile from InnerTube in background
        CoroutineScope(Dispatchers.IO).launch {
            fetchAccountInfo(cookie)
        }
    }

    fun updateProfile(name: String, email: String, avatarUrl: String) {
        val current = _authState.value ?: return
        val candidateName = name.trim()
        val newEmail = if (email.isNotBlank()) email.trim() else current.email
        val newName = if (candidateName.isNotBlank() && !candidateName.equals("YouTube User", true)) candidateName
            else newEmail.substringBefore('@').takeIf { it.isNotBlank() } ?: current.name.takeIf { it.isNotBlank() && !it.equals("Nexrify User", true) } ?: ""
        val cleanAvatar = avatarUrl.trim().let { if (it.startsWith("//")) "https:$it" else it }
        val newAvatar = if (cleanAvatar.isNotBlank()) cleanAvatar else current.avatarUrl

        prefs.edit()
            .putString(KEY_NAME, newName)
            .putString(KEY_EMAIL, newEmail)
            .putString(KEY_AVATAR, newAvatar)
            .apply()

        _authState.value = current.copy(
            name = newName,
            email = newEmail,
            avatarUrl = newAvatar
        )
        Log.d("NexrifyAuth", "Profile explicitly updated: $newName ($newAvatar)")
    }

    suspend fun fetchAccountInfo(cookie: String) {
        // Try YouTube Music first, then YouTube Web fallback
        val success = tryFetchAccountMenu(cookie, isMusic = true)
        if (!success) {
            tryFetchAccountMenu(cookie, isMusic = false)
        }
    }

    private fun tryFetchAccountMenu(cookie: String, isMusic: Boolean): Boolean {
        try {
            val origin = if (isMusic) "https://music.youtube.com" else "https://www.youtube.com"
            val clientName = if (isMusic) "WEB_REMIX" else "WEB"
            val clientNum = if (isMusic) "67" else "1"
            val clientVer = if (isMusic) "1.20240101.01.00" else "2.20240101.01.00"
            val sapisidHash = getSapisidHash(origin)

            val jsonBody = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", clientName)
                        put("clientVersion", clientVer)
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
            }

            val reqBuilder = Request.Builder()
                .url("$origin/youtubei/v1/account/account_menu")
                .header("Content-Type", "application/json")
                .header("Cookie", cookie)
                .header("Origin", origin)
                .header("Referer", "$origin/")
                .header("X-Origin", origin)
                .header("X-YouTube-Client-Name", clientNum)
                .header("X-YouTube-Client-Version", clientVer)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))

            if (sapisidHash != null) {
                reqBuilder.header("Authorization", sapisidHash)
            }

            val (ok, respString) = httpClient.newCall(reqBuilder.build()).execute().use { r ->
                r.isSuccessful to r.body?.string().orEmpty()
            }
            if (ok && respString.isNotBlank()) {
                val json = JSONObject(respString)
                val actions = json.optJSONArray("actions")
                val openPopup = actions?.optJSONObject(0)?.optJSONObject("openPopupAction")
                val multiPage = openPopup?.optJSONObject("popup")?.optJSONObject("multiPageMenuRenderer")
                val activeAccount = multiPage?.optJSONObject("header")?.optJSONObject("activeAccountHeaderRenderer")

                if (activeAccount != null) {
                    val nameObj = activeAccount.opt("accountName")
                    val name = when (nameObj) {
                        is JSONObject -> nameObj.optJSONArray("runs")?.optJSONObject(0)?.optString("text", "")
                            ?: nameObj.optString("simpleText", "")
                        is String -> nameObj
                        else -> ""
                    }

                    val emailObj = activeAccount.opt("email")
                    val email = when (emailObj) {
                        is JSONObject -> emailObj.optJSONArray("runs")?.optJSONObject(0)?.optString("text", "")
                            ?: emailObj.optString("simpleText", "")
                        is String -> emailObj
                        else -> ""
                    }

                    val handleObj = activeAccount.opt("channelHandle")
                    val handle = when (handleObj) {
                        is JSONObject -> handleObj.optJSONArray("runs")?.optJSONObject(0)?.optString("text", "")
                            ?: handleObj.optString("simpleText", "")
                        is String -> handleObj
                        else -> ""
                    }

                    var avatarUrl = ""
                    val accountPhoto = activeAccount.optJSONObject("accountPhoto")
                    val thumbs = accountPhoto?.optJSONArray("thumbnails")
                    if (thumbs != null && thumbs.length() > 0) {
                        avatarUrl = thumbs.optJSONObject(thumbs.length() - 1)?.optString("url") ?: ""
                    }

                    if (name.isNotBlank() || avatarUrl.isNotBlank()) {
                        updateProfile(name, email, avatarUrl)
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("NexrifyAuth", "Account menu fetch error: ${e.message}")
        }
        return false
    }

    /** Ambil ulang nama dan foto akun Google/YouTube di latar belakang, dipanggil tiap aplikasi dibuka. */
    fun refreshProfile() {
        val cookie = getCookie() ?: return
        CoroutineScope(Dispatchers.IO).launch { fetchAccountInfo(cookie) }
    }

    fun logout() {
        prefs.edit().clear().apply()
        _authState.value = null
        try {
            val cm = android.webkit.CookieManager.getInstance()
            cm.removeAllCookies { cm.flush() }
            cm.flush()
            android.webkit.WebStorage.getInstance().deleteAllData()
        } catch (e: Exception) {
            Log.e("NexrifyAuth", "Failed to clear WebView cookies: ${e.message}")
        }
    }

    companion object {
        private const val PREF_NAME = "nexrify_auth_prefs"
        private const val KEY_COOKIE = "yt_cookie"
        private const val KEY_NAME = "yt_name"
        private const val KEY_EMAIL = "yt_email"
        private const val KEY_HANDLE = "yt_handle"
        private const val KEY_AVATAR = "yt_avatar"

        @Volatile
        private var instance: AuthManager? = null

        fun init(context: Context): AuthManager {
            return instance ?: synchronized(this) {
                instance ?: AuthManager(context.applicationContext).also { instance = it }
            }
        }

        fun getInstance(): AuthManager {
            return instance ?: throw IllegalStateException("AuthManager must be initialized in Application.onCreate")
        }
    }
}

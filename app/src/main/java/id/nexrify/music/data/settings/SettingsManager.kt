package id.nexrify.music.data.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

enum class ThemeMode {
    SYSTEM, DARK, LIGHT
}

enum class ThemeStyle { CLASSIC, LIQUID_GLASS, LIQUID_GLASS_V2, VIOLET_NIGHT, AURORA_GOLD, OBSIDIAN }

enum class AppLanguage { SYSTEM, INDONESIAN, ENGLISH }

enum class AudioQuality(val title: String, val description: String) {
    HIGH("High Quality (M4A / 256 kbps)", "Crisp sound, best audio fidelity"),
    MEDIUM("Normal Quality (128 kbps)", "Balanced speed & data usage"),
    LOW("Data Saver (64 kbps)", "Fastest buffer, minimal data usage")
}

/** Pilihan detail di dalam tiap tingkat kualitas (format dan bitrate), sama seperti di Kuro. */
enum class AudioSubQuality(
    val parent: AudioQuality,
    val badge: String,
    val subtitleId: String,
    val subtitleEn: String,
    val targetBitrate: Int,
    val preferOpus: Boolean
) {
    HIGH_OPUS_160(AudioQuality.HIGH, "WebM Opus • ~160 kbps", "Kualitas tertinggi YouTube Music (setara 256 kbps AAC)", "Highest YouTube Music clarity (itag 251 / 774)", 160, true),
    HIGH_M4A_256(AudioQuality.HIGH, "M4A AAC • 128–256 kbps", "Hardware decoder ramah TWS & hemat baterai (itag 141 / 140)", "Hardware decoder friendly & low battery usage (itag 141 / 140)", 256, false),
    MED_M4A_128(AudioQuality.MEDIUM, "M4A AAC • 128 kbps", "Standar seimbang & kompatibilitas tinggi (itag 140)", "Balanced standard AAC stream (itag 140)", 128, false),
    MED_OPUS_128(AudioQuality.MEDIUM, "WebM Opus • ~128 kbps", "Karakter vokal & separasi instrumen lebih tajam", "Crisp vocal & instrument separation", 128, true),
    LOW_OPUS_70(AudioQuality.LOW, "WebM Opus • ~70 kbps", "Hemat kuota dengan suara tetap jernih (itag 250)", "Data saver with clean vocals (itag 250)", 70, true),
    LOW_OPUS_50(AudioQuality.LOW, "WebM Opus • ~50 kbps", "Ultra hemat kuota (itag 249)", "Ultra data saver (itag 249)", 50, true),
    LOW_M4A_48(AudioQuality.LOW, "M4A HE-AAC • ~48 kbps", "Buffer paling cepat di sinyal lemah (itag 139)", "Fastest buffer on weak mobile signal (itag 139)", 48, false);

    companion object {
        fun optionsFor(quality: AudioQuality): List<AudioSubQuality> = values().filter { it.parent == quality }
        fun defaultFor(quality: AudioQuality): AudioSubQuality = when (quality) {
            AudioQuality.HIGH -> HIGH_OPUS_160
            AudioQuality.MEDIUM -> MED_M4A_128
            AudioQuality.LOW -> LOW_OPUS_70
        }
    }
}

class SettingsManager private constructor(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _themeStyle = MutableStateFlow(loadThemeStyle())
    val themeStyle: StateFlow<ThemeStyle> = _themeStyle.asStateFlow()

    private val _audioQuality = MutableStateFlow(loadAudioQuality())
    val audioQuality: StateFlow<AudioQuality> = _audioQuality.asStateFlow()

    private fun loadSub(key: String, parent: AudioQuality): AudioSubQuality {
        val saved = prefs.getString(key, null)?.let { runCatching { AudioSubQuality.valueOf(it) }.getOrNull() }
        return if (saved != null && saved.parent == parent) saved else AudioSubQuality.defaultFor(parent)
    }

    private val _audioSubQuality = MutableStateFlow(loadSub(KEY_AUDIO_SUB, loadAudioQuality()))
    /** Pilihan detail kualitas streaming (format dan bitrate). */
    val audioSubQuality: StateFlow<AudioSubQuality> = _audioSubQuality.asStateFlow()

    fun setAudioSubQuality(sub: AudioSubQuality) {
        _audioSubQuality.value = sub
        _audioQuality.value = sub.parent
        prefs.edit().putString(KEY_AUDIO_SUB, sub.name).putString(KEY_AUDIO_QUALITY, sub.parent.name).apply()
    }

    private fun loadDownloadQuality(): AudioQuality {
        val name = prefs.getString(KEY_DOWNLOAD_QUALITY, AudioQuality.HIGH.name) ?: AudioQuality.HIGH.name
        return try { AudioQuality.valueOf(name) } catch (_: Exception) { AudioQuality.HIGH }
    }
    private val _downloadQuality = MutableStateFlow(loadDownloadQuality())
    private val _downloadSubQuality = MutableStateFlow(loadSub(KEY_DOWNLOAD_SUB, loadDownloadQuality()))
    /** Pilihan detail kualitas unduhan offline. */
    val downloadSubQuality: StateFlow<AudioSubQuality> = _downloadSubQuality.asStateFlow()

    fun setDownloadSubQuality(sub: AudioSubQuality) {
        _downloadSubQuality.value = sub
        _downloadQuality.value = sub.parent
        prefs.edit().putString(KEY_DOWNLOAD_SUB, sub.name).putString(KEY_DOWNLOAD_QUALITY, sub.parent.name).apply()
    }
    /** Kualitas file unduhan offline, terpisah dari kualitas streaming. */
    val downloadQuality: StateFlow<AudioQuality> = _downloadQuality.asStateFlow()

    fun setDownloadQuality(quality: AudioQuality) {
        _downloadQuality.value = quality
        prefs.edit().putString(KEY_DOWNLOAD_QUALITY, quality.name).apply()
    }

    private val _autoTranslate = MutableStateFlow(prefs.getBoolean(KEY_AUTO_TRANSLATE, true))
    val autoTranslate: StateFlow<Boolean> = _autoTranslate.asStateFlow()

    private fun loadLanguage(): AppLanguage {
        val saved = prefs.getString(KEY_LANGUAGE, AppLanguage.SYSTEM.name) ?: AppLanguage.SYSTEM.name
        return runCatching { AppLanguage.valueOf(saved) }.getOrDefault(AppLanguage.SYSTEM)
    }
    private val _language = MutableStateFlow(loadLanguage())
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun setLanguage(language: AppLanguage) {
        _language.value = language
        prefs.edit().putString(KEY_LANGUAGE, language.name).apply()
    }

    fun isEnglish(): Boolean = when (_language.value) {
        AppLanguage.ENGLISH -> true
        AppLanguage.INDONESIAN -> false
        AppLanguage.SYSTEM -> !java.util.Locale.getDefault().language.equals("id", ignoreCase = true)
    }

    private val _capsuleEnabled = MutableStateFlow(prefs.getBoolean(KEY_CAPSULE, true))
    val capsuleEnabled: StateFlow<Boolean> = _capsuleEnabled.asStateFlow()

    @Volatile
    private var capsuleListener: (() -> Unit)? = null

    fun isCapsuleEnabled(): Boolean = _capsuleEnabled.value

    fun setCapsuleEnabled(enabled: Boolean) {
        _capsuleEnabled.value = enabled
        prefs.edit().putBoolean(KEY_CAPSULE, enabled).apply()
        capsuleListener?.invoke()
    }

    private val _capsuleLocked = MutableStateFlow(prefs.getBoolean(KEY_CAPSULE_LOCKED, false))
    val capsuleLocked: StateFlow<Boolean> = _capsuleLocked.asStateFlow()

    fun isCapsuleLocked(): Boolean = _capsuleLocked.value

    fun setCapsuleLocked(locked: Boolean) {
        _capsuleLocked.value = locked
        prefs.edit().putBoolean(KEY_CAPSULE_LOCKED, locked).apply()
    }

    fun setCapsuleChangeListener(listener: (() -> Unit)?) {
        capsuleListener = listener
    }

    /** Baca ulang semua pengaturan dari penyimpanan (setelah data akun diterapkan). */
    fun reloadFromPrefs() {
        _themeMode.value = loadThemeMode()
        _themeStyle.value = loadThemeStyle()
        _audioQuality.value = loadAudioQuality()
        _downloadQuality.value = loadDownloadQuality()
        _audioSubQuality.value = loadSub(KEY_AUDIO_SUB, _audioQuality.value)
        _downloadSubQuality.value = loadSub(KEY_DOWNLOAD_SUB, _downloadQuality.value)
        _autoTranslate.value = prefs.getBoolean(KEY_AUTO_TRANSLATE, true)
        _language.value = loadLanguage()
        _capsuleEnabled.value = prefs.getBoolean(KEY_CAPSULE, true)
        _capsuleLocked.value = prefs.getBoolean(KEY_CAPSULE_LOCKED, false)
        capsuleListener?.invoke()
    }

    private fun loadThemeMode(): ThemeMode {
        val name = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        return try {
            ThemeMode.valueOf(name)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }
    }

    private fun loadThemeStyle(): ThemeStyle {
        val saved = runCatching {
            ThemeStyle.valueOf(prefs.getString(KEY_THEME_STYLE, ThemeStyle.CLASSIC.name) ?: ThemeStyle.CLASSIC.name)
        }.getOrDefault(ThemeStyle.CLASSIC)
        // Tema lama yang sudah dihentikan dipindahkan ke Liquid Glass gratis.
        return if (saved == ThemeStyle.VIOLET_NIGHT || saved == ThemeStyle.AURORA_GOLD || saved == ThemeStyle.OBSIDIAN) {
            ThemeStyle.LIQUID_GLASS
        } else saved
    }

    fun setThemeStyle(style: ThemeStyle) { _themeStyle.value = style; prefs.edit().putString(KEY_THEME_STYLE, style.name).apply() }

    private fun loadAudioQuality(): AudioQuality {
        val name = prefs.getString(KEY_AUDIO_QUALITY, AudioQuality.HIGH.name) ?: AudioQuality.HIGH.name
        return try {
            AudioQuality.valueOf(name)
        } catch (_: Exception) {
            AudioQuality.HIGH
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun setAudioQuality(quality: AudioQuality) {
        _audioQuality.value = quality
        prefs.edit().putString(KEY_AUDIO_QUALITY, quality.name).apply()
    }

    fun setAutoTranslate(enabled: Boolean) {
        _autoTranslate.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_TRANSLATE, enabled).apply()
    }

    fun getCacheSizeBytes(): Long {
        var total = 0L
        try {
            val cacheDirs = listOf(
                File(context.cacheDir, "lyrics_cache_v3"),
                File(context.cacheDir, "lyrics_cache_v2"),
                File(context.cacheDir, "lyrics_cache"),
                File(context.cacheDir, "http_cache"),
                File(context.cacheDir, "image_cache")
            )
            cacheDirs.forEach { dir ->
                if (dir.exists()) {
                    total += calculateDirSize(dir)
                }
            }
        } catch (_: Exception) {}
        return total
    }

    private fun calculateDirSize(dir: File): Long {
        var bytes = 0L
        dir.listFiles()?.forEach { file ->
            bytes += if (file.isDirectory) calculateDirSize(file) else file.length()
        }
        return bytes
    }

    suspend fun getFormattedCacheSizeAsync(): String = withContext(Dispatchers.IO) {
        val bytes = getCacheSizeBytes()
        if (bytes <= 0) return@withContext "0.0 MB"
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        String.format("%.1f MB", mb)
    }

    fun getFormattedCacheSize(): String {
        val bytes = getCacheSizeBytes()
        if (bytes <= 0) return "0.0 MB"
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return String.format("%.1f MB", mb)
    }

    suspend fun clearCacheAsync(): Boolean = withContext(Dispatchers.IO) {
        try {
            val cacheDirs = listOf(
                File(context.cacheDir, "lyrics_cache_v3"),
                File(context.cacheDir, "lyrics_cache_v2"),
                File(context.cacheDir, "lyrics_cache"),
                File(context.cacheDir, "image_cache"),
                File(context.cacheDir, "http_cache")
            )
            cacheDirs.forEach { dir ->
                if (dir.exists()) {
                    dir.listFiles()?.forEach { file ->
                        if (file.name != "journal" && !file.name.endsWith(".lock")) {
                            file.deleteRecursively()
                        }
                    }
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun clearCache(): Boolean {
        return try {
            val cacheDirs = listOf(
                File(context.cacheDir, "lyrics_cache_v3"),
                File(context.cacheDir, "lyrics_cache_v2"),
                File(context.cacheDir, "lyrics_cache"),
                File(context.cacheDir, "image_cache"),
                File(context.cacheDir, "http_cache")
            )
            cacheDirs.forEach { dir ->
                if (dir.exists()) {
                    dir.listFiles()?.forEach { file ->
                        if (file.name != "journal" && !file.name.endsWith(".lock")) {
                            file.deleteRecursively()
                        }
                    }
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private const val PREF_NAME = "nexrify_settings"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_AUDIO_QUALITY = "audio_quality"
        private const val KEY_DOWNLOAD_QUALITY = "download_quality"
        private const val KEY_AUDIO_SUB = "audio_sub_quality"
        private const val KEY_DOWNLOAD_SUB = "download_sub_quality"
        private const val KEY_THEME_STYLE = "theme_style"
        private const val KEY_AUTO_TRANSLATE = "lyrics_auto_translate"
        private const val KEY_LANGUAGE = "app_language"
        private const val KEY_CAPSULE = "music_capsule_enabled"
        private const val KEY_CAPSULE_LOCKED = "music_capsule_locked"

        @Volatile
        private var instance: SettingsManager? = null

        fun init(context: Context): SettingsManager {
            return instance ?: synchronized(this) {
                instance ?: SettingsManager(context.applicationContext).also { instance = it }
            }
        }

        fun getInstance(): SettingsManager {
            return checkNotNull(instance) { "SettingsManager must be initialized before use" }
        }
    }
}

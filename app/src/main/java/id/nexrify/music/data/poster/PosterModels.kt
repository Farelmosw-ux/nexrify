package id.nexrify.music.data.poster

enum class PosterLayout { ORBIT, STACK, ROW, COLUMN }
enum class GlassType { SMOKE, CLEAR, MILK, DARK }
enum class PhotoFit { FILL, FIT }
enum class PlayerVisualMode { NORMAL, POSTER_LIVE }

/** Penyesuaian per kartu: ukuran %, putar, miring datar (kiri-kanan), miring tegak (atas-bawah), dalam derajat. */
data class CardAdj(val size: Int = 100, val rot: Int = 0, val tiltX: Int = 0, val tiltY: Int = 0)

/** Semua pengaturan poster. Angka slider 0..100 kecuali disebut lain. */
data class PosterState(
    val layout: PosterLayout = PosterLayout.ORBIT,
    // Gaya kartu
    val glass: GlassType = GlassType.SMOKE,
    val perspective: Int = 50,
    val thickness: Int = 40,
    val opacity: Int = 85,
    val corner: Int = 55,
    val shine: Int = 55,
    val shadowPower: Int = 65,
    val shadowBlur: Int = 55,
    val shadowDistance: Int = 50,
    val shadowAngle: Int = 115, // derajat 0..360
    // Efek
    val mainLight: Int = 35,
    val bloom: Int = 18,
    val sparkle: Int = 48,
    val lens: Int = 12,
    val grain: Int = 0,
    val edge: Int = 60,
    val sparkles: Boolean = true,
    // Foto & latar
    val dim: Int = 22,
    val bgBlur: Int = 0,
    val cardLight: Int = 40,
    val lightColor: Int? = null, // null = otomatis mengikuti warna dominan sampul
    val photoScale: Int = 100,   // 20..200
    val photoX: Int = 0,         // -100..100
    val photoY: Int = 0,
    val photoRot: Int = 0,       // kelipatan 90 derajat (0..3)
    val mirror: Boolean = false,
    val fit: PhotoFit = PhotoFit.FILL,
    val subjectInFront: Boolean = true, // hasil potong latar menutupi kartu
    // Interaktif & Live Player Poster
    val likedIds: Set<String> = emptySet(),
    val progressMap: Map<String, Float> = emptyMap(), // 0f..1f per id lagu
    val elapsedTextMap: Map<String, String> = emptyMap(), // misal "01:12"
    val activeLyricLine: String? = null,
    val activeLyricTranslation: String? = null,
    val showLyricsOnPoster: Boolean = true,
    /** Geseran kartu hasil seret, per id lagu (satuan koordinat poster 540x960). */
    val offsets: Map<String, Pair<Float, Float>> = emptyMap(),
    /** Ukuran, putaran, dan kemiringan per kartu, per id lagu. */
    val adj: Map<String, CardAdj> = emptyMap()
) {
    /** Rapikan: kembalikan semua kartu ke posisi dan bentuk bawaan susunan. */
    fun tidy(): PosterState = copy(offsets = emptyMap(), adj = emptyMap())

    /** Reset hanya bagian gaya kartu, sama seperti tombol "Atur ulang gaya kartu". */
    fun resetCardStyle(): PosterState {
        val d = PosterState()
        return copy(
            glass = d.glass, perspective = d.perspective, thickness = d.thickness, opacity = d.opacity,
            corner = d.corner, shine = d.shine, shadowPower = d.shadowPower, shadowBlur = d.shadowBlur,
            shadowDistance = d.shadowDistance, shadowAngle = d.shadowAngle
        )
    }
}
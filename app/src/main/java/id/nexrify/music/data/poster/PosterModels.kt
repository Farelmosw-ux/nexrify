package id.nexrify.music.data.poster

enum class PosterLayout { ORBIT, STACK, ROW, COLUMN }
enum class GlassType { SMOKE, CLEAR, MILK, DARK }
enum class PhotoFit { FILL, FIT }

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
    val corner: Int = 50,
    val shine: Int = 50,
    val shadowPower: Int = 60,
    val shadowBlur: Int = 50,
    val shadowDistance: Int = 50,
    val shadowAngle: Int = 115, // derajat 0..360
    // Efek
    val mainLight: Int = 30,
    val bloom: Int = 15,
    val sparkle: Int = 45,
    val lens: Int = 0,
    val grain: Int = 0,
    val edge: Int = 50,
    val sparkles: Boolean = true,
    // Foto & latar
    val dim: Int = 17,
    val bgBlur: Int = 0,
    val cardLight: Int = 35,
    val lightColor: Int? = null, // null = otomatis (putih)
    val photoScale: Int = 100,   // 20..200
    val photoX: Int = 0,         // -100..100
    val photoY: Int = 0,
    val photoRot: Int = 0,       // kelipatan 90 derajat (0..3)
    val mirror: Boolean = false,
    val fit: PhotoFit = PhotoFit.FILL,
    val subjectInFront: Boolean = true, // hasil potong latar menutupi kartu
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

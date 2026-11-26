package id.nexrify.music.data.poster

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Potong latar foto (orang jadi cutout transparan) memakai ML Kit Selfie Segmentation,
 * modelnya ikut di dalam aplikasi sehingga jalan offline. Kalau nanti mau pakai MODNet,
 * cukup ganti isi [cut] dengan keluaran matte MODNet; pemanggilnya tidak perlu diubah.
 */
object BackgroundRemover {
    private val segmenter by lazy {
        Segmentation.getClient(
            SelfieSegmenterOptions.Builder()
                .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                .enableRawSizeMask()
                .build()
        )
    }

    suspend fun cut(src: Bitmap): Bitmap? {
        val mask = suspendCancellableCoroutine<SegmentationMask?> { cont ->
            segmenter.process(InputImage.fromBitmap(src, 0))
                .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        } ?: return null
        return withContext(Dispatchers.Default) {
            val w = src.width
            val h = src.height
            val mw = mask.width
            val mh = mask.height
            val buf = mask.buffer.also { it.rewind() }
            val conf = FloatArray(mw * mh).also { buf.asFloatBuffer().get(it) }
            val px = IntArray(w * h)
            src.getPixels(px, 0, w, 0, 0, w, h)
            for (y in 0 until h) {
                val my = y * mh / h
                for (x in 0 until w) {
                    val c = conf[my * mw + x * mw / w]
                    val t = ((c - 0.35f) / 0.4f).coerceIn(0f, 1f)
                    val a = (t * t * (3f - 2f * t) * 255f).toInt()
                    val i = y * w + x
                    px[i] = (px[i] and 0x00FFFFFF) or (a shl 24)
                }
            }
            Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        }
    }
}

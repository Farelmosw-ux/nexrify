package id.nexrify.music.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Foto siap kirim: JPEG terkompres beserta ukurannya. */
class DmImagePrep(val bytes: ByteArray, val w: Int, val h: Int)

/**
 * Menyiapkan foto untuk chat pribadi: dikecilkan (sisi terpanjang 1280 px), diputar sesuai EXIF, dan dimampatkan ke JPEG
 * di bawah batas 400 KB server. Metadata EXIF (termasuk lokasi) tidak ikut terkirim karena gambar dikodekan ulang.
 */
object DmImage {
    private const val MAX_SIDE = 1280
    private const val MAX_BYTES = 380 * 1024

    fun compress(context: Context, uri: Uri): DmImagePrep? {
        return try {
            val cr = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / sample > MAX_SIDE * 2 || bounds.outHeight / sample > MAX_SIDE * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
            val rotation = cr.openInputStream(uri)?.use { rotationOf(it) } ?: 0
            var side = MAX_SIDE
            var quality = 82
            var attempt = 0
            while (attempt < 8) {
                val scale = minOf(1f, side / maxOf(decoded.width, decoded.height).toFloat())
                val m = Matrix().apply {
                    postRotate(rotation.toFloat())
                    postScale(scale, scale)
                }
                val out = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
                val bos = ByteArrayOutputStream()
                out.compress(Bitmap.CompressFormat.JPEG, quality, bos)
                val bytes = bos.toByteArray()
                if (bytes.size <= MAX_BYTES) {
                    val result = DmImagePrep(bytes, out.width, out.height)
                    if (out !== decoded) decoded.recycle()
                    return result
                }
                if (out !== decoded) out.recycle()
                if (quality > 55) quality -= 10 else side = (side * 0.8f).toInt()
                attempt++
            }
            decoded.recycle()
            null
        } catch (e: Throwable) {
            null
        }
    }

    private fun rotationOf(stream: InputStream): Int = try {
        when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (e: Throwable) {
        0
    }
}

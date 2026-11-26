package id.nexrify.music.data.playlist

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Membuat gambar sampul persegi dari sebuah playlist (sampul kustom, atau kolase lagu kalau tidak ada)
 * untuk ikut terkirim saat playlist dibagikan. Disimpan di cache dan dibuka lewat FileProvider.
 */
object PlaylistCoverExport {
    private const val SIZE = 800

    suspend fun export(context: Context, playlist: Playlist): Uri? = withContext(Dispatchers.IO) {
        try {
            val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            canvas.drawColor(0xFF111827.toInt())

            var drawn = false
            val cover = load(context, coverSource(playlist.coverUrl))
            if (cover != null) {
                drawCrop(canvas, cover, RectF(0f, 0f, SIZE.toFloat(), SIZE.toFloat()))
                drawn = true
            } else {
                val thumbs = playlist.tracks.take(4).mapNotNull { load(context, it.highResThumbnailUrl) }
                val h = SIZE / 2f
                when {
                    thumbs.size >= 4 -> {
                        drawCrop(canvas, thumbs[0], RectF(0f, 0f, h, h))
                        drawCrop(canvas, thumbs[1], RectF(h, 0f, SIZE.toFloat(), h))
                        drawCrop(canvas, thumbs[2], RectF(0f, h, h, SIZE.toFloat()))
                        drawCrop(canvas, thumbs[3], RectF(h, h, SIZE.toFloat(), SIZE.toFloat()))
                        drawn = true
                    }
                    thumbs.isNotEmpty() -> {
                        drawCrop(canvas, thumbs[0], RectF(0f, 0f, SIZE.toFloat(), SIZE.toFloat()))
                        drawn = true
                    }
                }
            }
            if (!drawn) return@withContext null

            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            dir.listFiles()?.forEach { runCatching { it.delete() } }
            val file = File(dir, "playlist_${playlist.id.take(12)}.jpg")
            file.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (_: Throwable) {
            null
        }
    }

    private fun coverSource(url: String): Any? {
        val u = url.trim()
        if (u.isEmpty()) return null
        if (u.startsWith("http://") || u.startsWith("https://") || u.startsWith("data:image/")) return u
        if (u.startsWith("file://")) {
            val f = File(u.removePrefix("file://"))
            return if (f.exists()) f else null
        }
        return null
    }

    private suspend fun load(context: Context, data: Any?): Bitmap? {
        if (data == null || (data is String && data.isBlank())) return null
        return runCatching {
            val req = ImageRequest.Builder(context).data(data).size(SIZE).allowHardware(false).build()
            (context.imageLoader.execute(req) as? SuccessResult)?.drawable?.toBitmap()
        }.getOrNull()
    }

    private fun drawCrop(canvas: Canvas, bmp: Bitmap, dst: RectF) {
        val side = minOf(bmp.width, bmp.height)
        val src = Rect((bmp.width - side) / 2, (bmp.height - side) / 2, (bmp.width + side) / 2, (bmp.height + side) / 2)
        canvas.drawBitmap(bmp, src, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    }
}

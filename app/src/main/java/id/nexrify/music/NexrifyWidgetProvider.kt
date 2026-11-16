package id.nexrify.music

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Bundle
import android.widget.RemoteViews
import id.nexrify.music.i18n.currentStrings
import id.nexrify.music.service.PlaybackBridge
import id.nexrify.music.service.PlaybackService

/**
 * Widget layar utama Nexrify dengan tiga ukuran:
 *  - Ringkas 4x1  ([NexrifyWidgetProvider])
 *  - Kotak   2x2  ([NexrifyWidgetSquareProvider])
 *  - Besar   4x2  ([NexrifyWidgetLargeProvider])
 *
 * Semua tombol memakai PendingIntent eksplisit (bukan media key), jadi tetap bisa diklik walau aplikasi
 * sedang tidak terbuka:
 *  - Jeda / Berikutnya / Sebelumnya / Suka -> broadcast ke receiver ini (proses pemutar sedang hidup).
 *  - Putar -> [WidgetActionActivity] (tembus pandang, langsung menutup sendiri). Activity boleh dimulai dari
 *    klik widget, dan kalau pemutar sudah mati ia membuka aplikasi untuk melanjutkan lagu terakhir.
 */
open class NexrifyWidgetProvider : AppWidgetProvider() {

    internal open val kind: Int get() = KIND_COMPACT

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        safeUpdate(context, manager, ids, kind)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        safeUpdate(context, manager, intArrayOf(id), kind)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PAUSE -> {
                val p = PlaybackService.playerRef
                if (p != null && p.isPlaying) p.pause()
                else openAppToPlay(context)
                refresh(context)
            }
            ACTION_PREV -> context.sendBroadcast(Intent(PlaybackService.ACTION_PREV).setPackage(context.packageName))
            ACTION_NEXT -> context.sendBroadcast(Intent(PlaybackService.ACTION_NEXT).setPackage(context.packageName))
            ACTION_LIKE -> PlaybackBridge.onToggleFavorite?.invoke()
        }
    }

    companion object {
        const val KIND_COMPACT = 0
        const val KIND_SQUARE = 1
        const val KIND_LARGE = 2

        private const val PREFS = "nexrify_widget"
        internal const val ACTION_PAUSE = "id.nexrify.music.WIDGET_PAUSE"
        private const val ACTION_PREV = "id.nexrify.music.WIDGET_PREV"
        private const val ACTION_NEXT = "id.nexrify.music.WIDGET_NEXT"
        private const val ACTION_LIKE = "id.nexrify.music.WIDGET_LIKE"

        @Volatile private var cachedArtwork: Bitmap? = null
        @Volatile private var cachedArtworkTitle: String? = null

        private val providers = listOf(
            NexrifyWidgetProvider::class.java to KIND_COMPACT,
            NexrifyWidgetSquareProvider::class.java to KIND_SQUARE,
            NexrifyWidgetLargeProvider::class.java to KIND_LARGE
        )

        fun save(context: Context, title: String, artist: String, playing: Boolean) {
            if (title != cachedArtworkTitle) {
                cachedArtwork = null
                cachedArtworkTitle = title
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("title", title)
                .putString("artist", artist)
                .putBoolean("playing", playing)
                .apply()
        }

        fun saveLiked(context: Context, liked: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("liked", liked).apply()
        }

        /** Kembalikan widget ke keadaan kosong (dipakai saat keluar akun). */
        fun reset(context: Context) {
            cachedArtwork = null
            cachedArtworkTitle = null
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
            refresh(context)
        }

        // RemoteViews dibatasi sekitar 6,9 MB memori bitmap total. Sampul dikecilkan dulu supaya aman.
        private const val MAX_ART_SIDE = 320

        private fun shrink(src: Bitmap): Bitmap {
            return try {
                val soft = if (src.config == Bitmap.Config.HARDWARE) {
                    src.copy(Bitmap.Config.ARGB_8888, false) ?: return src
                } else src
                val longest = maxOf(soft.width, soft.height)
                if (longest <= MAX_ART_SIDE) soft
                else {
                    val scale = MAX_ART_SIDE.toFloat() / longest
                    Bitmap.createScaledBitmap(
                        soft,
                        (soft.width * scale).toInt().coerceAtLeast(1),
                        (soft.height * scale).toInt().coerceAtLeast(1),
                        true
                    )
                }
            } catch (_: Throwable) {
                src
            }
        }

        fun updateArtwork(context: Context, bitmap: Bitmap) {
            cachedArtwork = shrink(bitmap)
            refresh(context)
        }

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            for ((cls, kind) in providers) {
                val ids = manager.getAppWidgetIds(ComponentName(context, cls))
                if (ids.isNotEmpty()) safeUpdate(context, manager, ids, kind)
            }
        }

        /** Perbarui widget. Kalau sistem menolak (memori bitmap terlalu besar), ulangi tanpa sampul, jangan sampai crash. */
        private fun safeUpdate(context: Context, manager: AppWidgetManager, ids: IntArray, kind: Int) {
            for (id in ids) {
                try {
                    manager.updateAppWidget(id, buildViews(context, manager, id, kind))
                } catch (_: IllegalArgumentException) {
                    cachedArtwork = null
                    try { manager.updateAppWidget(id, buildViews(context, manager, id, kind)) } catch (_: Throwable) {}
                } catch (_: Throwable) {
                }
            }
        }

        // ------------------------------------------------------------------ gambar

        private fun roundedArt(art: Bitmap, sidePx: Int, radiusPx: Float): Bitmap {
            val out = Bitmap.createBitmap(sidePx, sidePx, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            val path = Path().apply { addRoundRect(RectF(0f, 0f, sidePx.toFloat(), sidePx.toFloat()), radiusPx, radiusPx, Path.Direction.CW) }
            c.clipPath(path)
            drawCenterCrop(c, art, sidePx.toFloat(), sidePx.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            return out
        }

        private fun drawCenterCrop(c: Canvas, art: Bitmap, w: Float, h: Float, paint: Paint) {
            val s = maxOf(w / art.width, h / art.height)
            val dw = art.width * s
            val dh = art.height * s
            val l = (w - dw) / 2f
            val t = (h - dh) / 2f
            c.drawBitmap(art, null, RectF(l, t, l + dw, t + dh), paint)
        }

        /**
         * Latar widget 2x2 dan 4x2 digambar sendiri (RemoteViews tidak bisa memotong sudut membulat).
         * Kotak: sampul penuh dengan gradasi gelap di bawah. Besar: sampul diburamkan jadi nuansa warna.
         */
        private fun backdrop(context: Context, art: Bitmap?, wDp: Int, hDp: Int, blurred: Boolean): Bitmap {
            val d = context.resources.displayMetrics.density
            var w = (wDp * d)
            var h = (hDp * d)
            val longest = maxOf(w, h)
            if (longest > 640f) { val k = 640f / longest; w *= k; h *= k }
            val wi = w.toInt().coerceAtLeast(64)
            val hi = h.toInt().coerceAtLeast(64)
            val radius = 24f * d * (wi / (wDp * d).coerceAtLeast(1f)).coerceAtMost(1f)

            val out = Bitmap.createBitmap(wi, hi, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            val rect = RectF(0f, 0f, wi.toFloat(), hi.toFloat())
            c.clipPath(Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) })
            val smooth = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

            c.drawColor(Color.parseColor("#FF0C1017"))
            if (art != null) {
                if (blurred) {
                    val tiny = Bitmap.createScaledBitmap(art, 20, 20, true)
                    drawCenterCrop(c, tiny, wi.toFloat(), hi.toFloat(), smooth)
                    c.drawColor(Color.parseColor("#9E0A0D12"))
                } else {
                    drawCenterCrop(c, art, wi.toFloat(), hi.toFloat(), smooth)
                }
            } else {
                val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(
                        wi * 0.85f, hi * 0.1f, maxOf(wi, hi) * 0.9f,
                        intArrayOf(Color.parseColor("#4DA5F3FC"), Color.parseColor("#00A5F3FC")), null, Shader.TileMode.CLAMP
                    )
                }
                c.drawRect(rect, glow)
            }
            if (!blurred) {
                val shade = Paint().apply {
                    shader = LinearGradient(
                        0f, hi * 0.18f, 0f, hi.toFloat(),
                        intArrayOf(Color.TRANSPARENT, Color.parseColor("#B30A0D12"), Color.parseColor("#F20A0D12")),
                        floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
                    )
                }
                c.drawRect(rect, shade)
            } else {
                val shade = Paint().apply {
                    shader = LinearGradient(
                        0f, 0f, wi.toFloat(), 0f,
                        intArrayOf(Color.parseColor("#33000000"), Color.parseColor("#66000000")), null, Shader.TileMode.CLAMP
                    )
                }
                c.drawRect(rect, shade)
            }
            // Garis tepi tipis supaya widget terlihat "kaca" di atas wallpaper terang.
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = d
                color = Color.parseColor("#33FFFFFF")
            }
            c.drawRoundRect(RectF(d / 2, d / 2, wi - d / 2, hi - d / 2), radius, radius, stroke)
            return out
        }

        private fun sizeDp(manager: AppWidgetManager, id: Int, defW: Int, defH: Int): Pair<Int, Int> {
            val o = manager.getAppWidgetOptions(id)
            val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, defW).takeIf { it > 0 } ?: defW
            val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, defH).takeIf { it > 0 } ?: defH
            return w to h
        }

        // ------------------------------------------------------------------ tampilan

        private fun buildViews(context: Context, manager: AppWidgetManager, id: Int, kind: Int): RemoteViews {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val title = prefs.getString("title", "").orEmpty()
            val artist = prefs.getString("artist", "").orEmpty()
            val playing = prefs.getBoolean("playing", false) && PlaybackService.alive
            val liked = prefs.getBoolean("liked", false)
            val d = context.resources.displayMetrics.density
            val art = cachedArtwork
            val idle = title.isBlank()

            val layout = when (kind) {
                KIND_SQUARE -> R.layout.widget_square
                KIND_LARGE -> R.layout.widget_large
                else -> R.layout.widget_player
            }
            val views = RemoteViews(context.packageName, layout)

            views.setTextViewText(R.id.widget_title, if (idle) "Nexrify" else title)
            views.setTextViewText(
                R.id.widget_artist,
                if (idle) currentStrings().noSongPlaying else artist
            )
            views.setImageViewResource(R.id.widget_play, if (playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)

            when (kind) {
                KIND_SQUARE -> {
                    val (w, h) = sizeDp(manager, id, 146, 146)
                    views.setImageViewBitmap(R.id.widget_backdrop, backdrop(context, art, w, h, blurred = false))
                }
                KIND_LARGE -> {
                    val (w, h) = sizeDp(manager, id, 300, 146)
                    views.setImageViewBitmap(R.id.widget_backdrop, backdrop(context, art, w, h, blurred = true))
                    setArt(views, art, (98 * d).toInt(), 16f * d)
                    views.setImageViewResource(R.id.widget_like, if (liked) R.drawable.ic_widget_heart_on else R.drawable.ic_heart_outline)
                    views.setOnClickPendingIntent(R.id.widget_like, broadcast(context, ACTION_LIKE, 5))
                }
                else -> setArt(views, art, (50 * d).toInt(), 14f * d)
            }

            views.setOnClickPendingIntent(R.id.widget_prev, broadcast(context, ACTION_PREV, 1))
            views.setOnClickPendingIntent(R.id.widget_next, broadcast(context, ACTION_NEXT, 3))
            // Jeda lewat broadcast (proses pemutar hidup). Putar lewat activity tembus pandang supaya selalu bisa jalan.
            views.setOnClickPendingIntent(
                R.id.widget_play,
                if (playing) broadcast(context, ACTION_PAUSE, 2) else playActivity(context)
            )

            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            if (launch != null) {
                val open = PendingIntent.getActivity(
                    context, 4, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                views.setOnClickPendingIntent(R.id.widget_root, open)
            }
            return views
        }

        private fun setArt(views: RemoteViews, art: Bitmap?, sidePx: Int, radiusPx: Float) {
            if (art != null) views.setImageViewBitmap(R.id.widget_artwork, roundedArt(art, sidePx.coerceIn(48, 480), radiusPx))
            else views.setImageViewResource(R.id.widget_artwork, R.mipmap.ic_launcher)
        }

        private fun broadcast(context: Context, action: String, code: Int): PendingIntent {
            val intent = Intent(context, NexrifyWidgetProvider::class.java).setAction(action)
            return PendingIntent.getBroadcast(
                context, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        private fun playActivity(context: Context): PendingIntent {
            val intent = Intent(context, WidgetActionActivity::class.java)
                .setAction(WidgetActionActivity.ACTION_PLAY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            return PendingIntent.getActivity(
                context, 6, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        /** Pemutar sudah mati: buka aplikasi dan lanjutkan lagu terakhir. */
        internal fun openAppToPlay(context: Context) {
            PlaybackBridge.widgetPlay.value = true
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { context.startActivity(launch) } catch (_: Throwable) {}
        }
    }
}

/** Widget kotak 2x2. */
class NexrifyWidgetSquareProvider : NexrifyWidgetProvider() {
    override val kind: Int get() = KIND_SQUARE
}

/** Widget besar 4x2. */
class NexrifyWidgetLargeProvider : NexrifyWidgetProvider() {
    override val kind: Int get() = KIND_LARGE
}

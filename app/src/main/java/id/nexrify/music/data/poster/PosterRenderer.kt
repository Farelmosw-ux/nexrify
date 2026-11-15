package id.nexrify.music.data.poster

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import id.nexrify.music.data.model.Track
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Menggambar poster ke Bitmap. Koordinat kerja 540x960, otomatis diskalakan ke lebar keluaran. */
object PosterRenderer {
    const val BW = 540f
    const val BH = 960f
    const val CW = 196f
    const val CH = 342f

    data class Place(val x: Float, val y: Float, val rot: Float, val s: Float)

    private val ORBIT = arrayOf(
        floatArrayOf(148f, 290f, -9f), floatArrayOf(396f, 255f, 7f), floatArrayOf(148f, 655f, -6f),
        floatArrayOf(392f, 695f, 8f), floatArrayOf(270f, 475f, 2f)
    )

    fun layout(st: PosterState, tracks: List<Track>): List<Place> {
        val n = tracks.size
        return tracks.mapIndexed { i, t ->
            val c = i - (n - 1) / 2f
            val b = when {
                st.layout == PosterLayout.ORBIT && n == 1 -> Place(270f, 455f, 0f, 1.15f)
                st.layout == PosterLayout.ORBIT -> ORBIT[i].let { Place(it[0], it[1], it[2], if (n > 4) .8f else .9f) }
                st.layout == PosterLayout.STACK -> Place(270f + c * 75f, 270f, c * 9f, .85f)
                st.layout == PosterLayout.ROW -> {
                    val s = min(.6f, 500f / (n * (CW + 8f)))
                    Place(270f + c * (CW + 8f) * s, 240f, 0f, s)
                }
                else -> {
                    val s = min(.55f, (BH - 300f) / (n * (CH + 14f)))
                    Place(420f, 140f + CH * s / 2f + i * (CH + 14f) * s, 0f, s)
                }
            }
            val o = st.offsets[t.id]
            val moved = if (o == null) b else b.copy(x = b.x + o.first, y = b.y + o.second)
            val a = st.adj[t.id]
            if (a == null) moved else moved.copy(rot = moved.rot + a.rot, s = moved.s * a.size / 100f)
        }
    }

    private fun al(color: Int, a: Float) =
        Color.argb((a.coerceIn(0f, 1f) * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))

    private val noise by lazy {
        val r = Random(7)
        val px = IntArray(128 * 128) { val v = r.nextInt(256); Color.rgb(v, v, v) }
        Bitmap.createBitmap(px, 128, 128, Bitmap.Config.ARGB_8888)
    }

    fun extractDominantColor(bmp: Bitmap?): Int {
        if (bmp == null) return 0xFF4FD1E8.toInt()
        return runCatching {
            val tiny = Bitmap.createScaledBitmap(bmp, 4, 4, true)
            var r = 0; var g = 0; var b = 0; var cnt = 0
            for (y in 0 until 4) for (x in 0 until 4) {
                val px = tiny.getPixel(x, y)
                r += Color.red(px); g += Color.green(px); b += Color.blue(px); cnt++
            }
            val hsv = FloatArray(3)
            Color.RGBToHSV(r / cnt, g / cnt, b / cnt, hsv)
            hsv[1] = max(hsv[1], 0.55f)
            hsv[2] = max(hsv[2], 0.85f)
            Color.HSVToColor(hsv)
        }.getOrDefault(0xFF4FD1E8.toInt())
    }

    fun render(
        st: PosterState, tracks: List<Track>, covers: List<Bitmap?>,
        photo: Bitmap?, cutout: Bitmap?, outW: Int, brand: String = "Nexrify",
        selectedId: String? = null, showBrandFooter: Boolean = true, isPlaying: Boolean = true
    ): Bitmap {
        val k = outW / BW
        val outH = (BH * k).toInt()
        val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(k, k)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val dominant = extractDominantColor(covers.firstOrNull())
        fun mix(col: Int, f: Float) = Color.rgb(
            (Color.red(col) * f).toInt().coerceIn(0, 255),
            (Color.green(col) * f).toInt().coerceIn(0, 255),
            (Color.blue(col) * f).toInt().coerceIn(0, 255)
        )
        val inner = mix(dominant, .42f)
        val outer = mix(dominant, .06f)
        p.shader = RadialGradient(270f, 420f, 680f, inner, outer, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, BW, BH, p); p.shader = null

        if (photo != null) drawLayer(c, softBlur(photo, st.bgBlur), photo.width, photo.height, st)
        c.drawColor(al(Color.BLACK, st.dim / 100f))
        p.shader = RadialGradient(270f, 160f, 520f, al(Color.WHITE, st.mainLight / 100f * .32f), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, BW, BH, p); p.shader = null

        layout(st, tracks).forEachIndexed { i, pl ->
            drawCard(c, st, tracks[i], covers.getOrNull(i), pl, i, dominant, isPlaying)
        }
        if (cutout != null && st.subjectInFront) drawLayer(c, cutout, cutout.width, cutout.height, st)

        // Lirik Sinkron Melayang bergaya TikTok / Auralys di atas layer subjek/kartu
        if (st.showLyricsOnPoster && !st.activeLyricLine.isNullOrBlank()) {
            drawFloatingLyrics(c, st.activeLyricLine, st.activeLyricTranslation, dominant)
        }

        if (selectedId != null) {
            val idx = tracks.indexOfFirst { it.id == selectedId }
            if (idx >= 0) {
                val pl = layout(st, tracks)[idx]
                c.save(); c.translate(pl.x, pl.y); c.rotate(pl.rot); c.scale(pl.s, pl.s); c.concat(camMatrix(st, tracks[idx], idx))
                val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f / pl.s; color = Color.WHITE }
                val rr = st.corner * .4f
                c.drawRoundRect(RectF(-CW / 2 - 6, -CH / 2 - 6, CW / 2 + 6, CH / 2 + 6), rr + 6, rr + 6, sp)
                sp.style = Paint.Style.FILL; c.drawCircle(0f, CH / 2 + 6, 5f / pl.s + 2f, sp)
                c.restore()
            }
        }

        val raw = Canvas(bmp)
        val full = Rect(0, 0, outW, outH)
        if (st.bloom > 0) {
            val small = Bitmap.createScaledBitmap(bmp, max(1, outW / 8), max(1, outH / 8), true)
            val bp = Paint(Paint.FILTER_BITMAP_FLAG)
            bp.alpha = (st.bloom * 1.8f).toInt().coerceAtMost(255)
            bp.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
            raw.drawBitmap(small, null, full, bp)
        }
        if (st.grain > 0) {
            val gp = Paint()
            gp.shader = BitmapShader(noise, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            gp.alpha = (st.grain / 100f * .35f * 255).toInt()
            gp.xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
            raw.drawRect(0f, 0f, outW.toFloat(), outH.toFloat(), gp)
        }
        if (st.lens > 0) {
            val vp = Paint()
            vp.shader = RadialGradient(
                outW / 2f, outH / 2f, outH * .7f,
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, al(Color.BLACK, st.lens / 100f * .82f)),
                floatArrayOf(0f, .36f, 1f), Shader.TileMode.CLAMP
            )
            raw.drawRect(0f, 0f, outW.toFloat(), outH.toFloat(), vp)
        }

        if (showBrandFooter) {
            val ix = BW / 2 - 80; val iy = 882f; val isz = 36f; val k2 = isz / 108f
            p.style = Paint.Style.FILL; p.color = Color.BLACK; c.drawRoundRect(RectF(ix, iy, ix + isz, iy + isz), 9f, 9f, p)
            p.style = Paint.Style.STROKE; p.color = Color.WHITE; p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = 3.5f * k2; c.drawCircle(ix + isz / 2, iy + isz / 2, 34f * k2, p)
            p.strokeWidth = 4f * k2
            floatArrayOf(44f, 6f, 50f, 12f, 56f, 16f, 62f, 9f, 68f, 4f).toList().chunked(2).forEach { (bx, hh) ->
                val xx = ix + isz / 2 + (bx - 54f) * k2
                c.drawLine(xx, iy + isz / 2 - hh * k2, xx, iy + isz / 2 + hh * k2, p)
            }
            p.style = Paint.Style.FILL
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 24f; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
            c.drawText(brand, BW / 2 - 36, 908f, tp)
        }
        return bmp
    }

    private fun drawFloatingLyrics(c: Canvas, lyric: String, translation: String?, accent: Int) {
        val boxW = 460f
        val left = (BW - boxW) / 2f
        val top = 735f
        val tpMain = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 21f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setShadowLayer(8f, 0f, 2f, Color.BLACK)
        }
        val mainLayout = StaticLayout.Builder.obtain(lyric, 0, lyric.length, tpMain, (boxW - 36f).toInt())
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(2)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

        val tpSub = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = al(Color.WHITE, 0.78f)
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val subLayout = if (!translation.isNullOrBlank()) {
            StaticLayout.Builder.obtain(translation, 0, translation.length, tpSub, (boxW - 36f).toInt())
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setMaxLines(2)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
        } else null

        val totalH = mainLayout.height + (subLayout?.height?.plus(6) ?: 0) + 28f
        val bgRect = RectF(left, top, left + boxW, top + totalH)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = al(0xFF0E1117.toInt(), 0.72f)
        }
        c.drawRoundRect(bgRect, 22f, 22f, bgPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            shader = LinearGradient(left, top, left + boxW, top + totalH, al(accent, 0.75f), al(Color.WHITE, 0.25f), Shader.TileMode.CLAMP)
        }
        c.drawRoundRect(bgRect, 22f, 22f, borderPaint)

        c.save()
        c.translate(left + 18f, top + 14f)
        mainLayout.draw(c)
        if (subLayout != null) {
            c.translate(0f, mainLayout.height + 6f)
            subLayout.draw(c)
        }
        c.restore()
    }

    private fun softBlur(src: Bitmap, amount: Int): Bitmap {
        if (amount <= 0) return src
        val f = 1f + amount * .15f
        val small = Bitmap.createScaledBitmap(src, max(1, (src.width / f).toInt()), max(1, (src.height / f).toInt()), true)
        return Bitmap.createScaledBitmap(small, src.width, src.height, true)
    }

    /** Foto latar atau cutout: dipasang dengan skala, geser, putar, dan cermin yang sama. */
    private fun drawLayer(c: Canvas, bmp: Bitmap, iw: Int, ih: Int, st: PosterState) {
        val rot = st.photoRot * 90f
        val sideways = st.photoRot % 2 == 1
        val rw = if (sideways) ih.toFloat() else iw.toFloat()
        val rh = if (sideways) iw.toFloat() else ih.toFloat()
        val base = if (st.fit == PhotoFit.FILL) max(BW / rw, BH / rh) else min(BW / rw, BH / rh)
        val sc = base * st.photoScale / 100f
        val m = Matrix()
        m.postTranslate(-iw / 2f, -ih / 2f)
        m.postRotate(rot)
        if (st.mirror) m.postScale(-1f, 1f)
        m.postScale(sc, sc)
        m.postTranslate(BW / 2 + st.photoX * 3f, BH / 2 + st.photoY * 5f)
        c.drawBitmap(bmp, m, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }

    private fun star(c: Canvas, cx: Float, cy: Float, s: Float) {
        if (s < 2f) return
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(cx, cy, s, al(0xFFBDF3FF.toInt(), .85f), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, s, p); p.shader = null
        val q = s * .09f
        val path = Path().apply {
            moveTo(cx, cy - s); quadTo(cx + q, cy - q, cx + s, cy); quadTo(cx + q, cy + q, cx, cy + s)
            quadTo(cx - q, cy + q, cx - s, cy); quadTo(cx - q, cy - q, cx, cy - s); close()
        }
        p.color = 0xFFE6FAFF.toInt(); c.drawPath(path, p)
    }

    private fun camMatrix(st: PosterState, t: Track, i: Int): Matrix {
        val ad = st.adj[t.id] ?: CardAdj()
        val d = (st.perspective - 50).toFloat()
        val cam = Camera()
        cam.setLocation(0f, 0f, -24f)
        cam.rotateY(d * .5f * (if (i % 2 == 0) 1 else -1) + ad.tiltX)
        cam.rotateX(-d * .15f + ad.tiltY)
        return Matrix().also { cam.getMatrix(it) }
    }

    private fun drawCard(
        c: Canvas, st: PosterState, t: Track, cover: Bitmap?,
        pl: Place, i: Int, fallbackAccent: Int, isPlaying: Boolean
    ) {
        val w = CW; val h = CH; val l = -w / 2; val top = -h / 2
        val r = st.corner * .42f
        val rect = RectF(l, top, l + w, top + h)
        val op = st.opacity / 100f
        val cardAccent = extractDominantColor(cover)
        val lc = st.lightColor ?: cardAccent
        val milk = st.glass == GlassType.MILK
        val tc = if (milk) 0xFF15171C.toInt() else Color.WHITE
        val fill = when (st.glass) {
            GlassType.SMOKE -> al(0xFF23252C.toInt(), op)
            GlassType.CLEAR -> al(Color.WHITE, .12f + .14f * op)
            GlassType.MILK -> al(0xFFF2F3F7.toInt(), .42f + .42f * op)
            GlassType.DARK -> al(0xFF0A0B0E.toInt(), op)
        }
        c.save()
        c.translate(pl.x, pl.y); c.rotate(pl.rot); c.scale(pl.s, pl.s)
        c.concat(camMatrix(st, t, i))

        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.BLACK
        p.setShadowLayer(st.cardLight * 1.15f + 2f, 0f, 0f, al(lc, st.cardLight / 100f * .6f))
        c.drawRoundRect(rect, r, r, p)

        val a = Math.toRadians(st.shadowAngle.toDouble())
        val dist = st.shadowDistance * .6f
        p.color = fill
        p.setShadowLayer(st.shadowBlur * .9f + 1f, (Math.cos(a) * dist).toFloat(), (Math.sin(a) * dist).toFloat(), al(Color.BLACK, st.shadowPower / 100f))
        c.drawRoundRect(rect, r, r, p)
        p.clearShadowLayer()

        // Kilau diagonal khas glassmorphism Auralys-Nexrify
        p.shader = LinearGradient(l, top, l + w, top + h, al(Color.WHITE, st.shine / 100f * .32f), al(lc, st.shine / 100f * .08f), Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, r, r, p); p.shader = null

        // Bingkai tepi dengan gradasi tipis
        p.style = Paint.Style.STROKE; p.strokeWidth = 1f + st.thickness / 22f
        p.shader = LinearGradient(l, top, l + w, top + h, al(Color.WHITE, st.edge / 100f * .5f), al(lc, st.edge / 100f * .28f), Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, r, r, p); p.shader = null; p.style = Paint.Style.FILL

        // Sampul album
        val mg = 14f; val cs = w - 2 * mg; val cy = top + mg
        val cr = RectF(l + mg, cy, l + mg + cs, cy + cs)
        c.save()
        c.clipPath(Path().apply { addRoundRect(cr, r * .58f, r * .58f, Path.Direction.CW) })
        if (cover != null) c.drawBitmap(cover, null, cr, Paint(Paint.FILTER_BITMAP_FLAG))
        else { p.color = 0xFF334155.toInt(); c.drawRect(cr, p) }
        c.restore()

        // Garis tepi tipis di sekitar sampul
        p.style = Paint.Style.STROKE; p.strokeWidth = 1f; p.color = al(Color.WHITE, 0.18f)
        c.drawRoundRect(cr, r * .58f, r * .58f, p); p.style = Paint.Style.FILL

        // Badge NEXRIFY + Like Status
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = tc }
        var y = cy + cs + 31f
        tp.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD); tp.textSize = 7.5f; tp.alpha = 145
        c.drawText("NEXRIFY • LOSS-FREE", l + mg, y - 17f, tp); tp.alpha = 255

        tp.typeface = Typeface.create("sans-serif", Typeface.BOLD); tp.textSize = 16.5f
        c.drawText(TextUtils.ellipsize(t.title, tp, cs - 26f, TextUtils.TruncateAt.END).toString(), l + mg, y, tp)

        tp.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); tp.textSize = 11f; tp.alpha = 160
        c.drawText(TextUtils.ellipsize(t.artist, tp, cs - 26f, TextUtils.TruncateAt.END).toString(), l + mg, y + 16f, tp)

        // Tombol Like (Merah menyala jika aktif, outline jika belum)
        val isLiked = t.isFavorite || t.id in st.likedIds
        tp.alpha = 255; tp.textSize = 17f; tp.textAlign = Paint.Align.RIGHT
        tp.color = if (isLiked) 0xFFFF3B30.toInt() else tc
        c.drawText(if (isLiked) "♥" else "♡", l + w - mg, y + 6f, tp)
        tp.textAlign = Paint.Align.LEFT; tp.color = tc

        // Waveform Audio + Waktu Lagu Real-Time
        y += 38f
        val progress = (st.progressMap[t.id] ?: 0.35f).coerceIn(0f, 1f)
        val elapsedStr = st.elapsedTextMap[t.id] ?: formatPercentDuration(t.durationSeconds, progress)
        val bars = 34; val bwid = cs / bars
        val waveColor = if (milk) 0xFF0EA5E9.toInt() else lc
        for (bi in 0 until bars) {
            val animShift = if (isPlaying) sin((progress * 40f + bi * 0.45f).toDouble()).toFloat() * 2.2f else 0f
            val baseH = 3.2f + 8.5f * abs(sin(bi * 0.68 + t.title.length)).toFloat()
            val hh = (baseH + animShift).coerceIn(2.5f, 14f)
            p.color = if (bi <= bars * progress) waveColor else al(tc, .28f)
            c.drawRoundRect(
                RectF(l + mg + bi * bwid + 0.9f, y - hh / 2f, l + mg + (bi + 1) * bwid - 0.9f, y + hh / 2f),
                1.5f, 1.5f, p
            )
        }
        p.color = tc
        tp.textSize = 8.8f; tp.alpha = 155
        c.drawText(elapsedStr, l + mg, y + 15f, tp)
        tp.textAlign = Paint.Align.RIGHT
        c.drawText(t.formattedDuration, l + w - mg, y + 15f, tp)
        tp.textAlign = Paint.Align.LEFT

        // Kontrol Transport Kapsul (Prev, Play/Pause, Next)
        y += 37f
        val cx = 0f
        p.color = al(tc, 0.1f)
        c.drawRoundRect(RectF(cx - 56f, y - 16f, cx + 56f, y + 16f), 16f, 16f, p)

        p.color = tc; p.alpha = 230
        val ctrl = Path().apply {
            moveTo(cx - 30, y - 6); lineTo(cx - 39, y); lineTo(cx - 30, y + 6)
            moveTo(cx - 22, y - 6); lineTo(cx - 31, y); lineTo(cx - 22, y + 6)
            moveTo(cx + 22, y - 6); lineTo(cx + 31, y); lineTo(cx + 22, y + 6)
            moveTo(cx + 30, y - 6); lineTo(cx + 39, y); lineTo(cx + 30, y + 6)
        }
        c.drawPath(ctrl, p)

        p.alpha = 255
        p.color = waveColor
        c.drawCircle(cx, y, 14.5f, p)
        p.color = if (milk) Color.WHITE else 0xFF07131A.toInt()
        if (isPlaying) {
            c.drawRect(cx - 4.5f, y - 6.5f, cx - 1.5f, y + 6.5f, p)
            c.drawRect(cx + 1.5f, y - 6.5f, cx + 4.5f, y + 6.5f, p)
        } else {
            val tri = Path().apply {
                moveTo(cx - 3.5f, y - 6.5f)
                lineTo(cx + 6.5f, y)
                lineTo(cx - 3.5f, y + 6.5f)
                close()
            }
            c.drawPath(tri, p)
        }

        if (st.sparkles) star(c, l + w - 4f, top + 8f, st.sparkle * .8f)
        c.restore()
    }

    private fun formatPercentDuration(totalSec: Int, pct: Float): String {
        val sec = (totalSec * pct).toInt().coerceAtLeast(0)
        return String.format("%d:%02d", sec / 60, sec % 60)
    }
}

/** Simpan PNG ke galeri (Pictures/Nexrify). Android 9 ke bawah butuh izin penyimpanan. */
object PosterExport {
    fun save(ctx: Context, bmp: Bitmap): Uri? = runCatching {
        val v = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Poster_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Nexrify")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val res = ctx.contentResolver
        val uri = res.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v) ?: return null
        res.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: return null
        if (Build.VERSION.SDK_INT >= 29) {
            v.clear(); v.put(MediaStore.Images.Media.IS_PENDING, 0); res.update(uri, v, null, null)
        }
        uri
    }.getOrNull()
}
package id.nexrify.music.data.poster

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.TextPaint
import android.text.TextUtils
import id.nexrify.music.data.model.Track
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Menggambar poster ke Bitmap. Koordinat kerja 540x960, otomatis diskalakan ke lebar keluaran. */
object PosterRenderer {
    const val BW = 540f
    const val BH = 960f
    const val CW = 190f
    const val CH = 330f

    data class Place(val x: Float, val y: Float, val rot: Float, val s: Float)

    private val ORBIT = arrayOf(
        floatArrayOf(150f, 300f, -9f), floatArrayOf(395f, 260f, 7f), floatArrayOf(150f, 660f, -6f),
        floatArrayOf(390f, 700f, 8f), floatArrayOf(270f, 480f, 2f)
    )

    fun layout(st: PosterState, tracks: List<Track>): List<Place> {
        val n = tracks.size
        return tracks.mapIndexed { i, t ->
            val c = i - (n - 1) / 2f
            val b = when {
                st.layout == PosterLayout.ORBIT && n == 1 -> Place(270f, 480f, 0f, 1f)
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

    fun render(
        st: PosterState, tracks: List<Track>, covers: List<Bitmap?>,
        photo: Bitmap?, cutout: Bitmap?, outW: Int, brand: String = "Nexrify", selectedId: String? = null
    ): Bitmap {
        val k = outW / BW
        val outH = (BH * k).toInt()
        val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(k, k)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // Warna latar mengikuti sampul lagu pertama (kalau tidak ada foto)
        val tint = covers.firstOrNull()?.let { runCatching { Bitmap.createScaledBitmap(it, 1, 1, true).getPixel(0, 0) }.getOrNull() }
        fun mix(col: Int, f: Float) = Color.rgb((Color.red(col) * f).toInt(), (Color.green(col) * f).toInt(), (Color.blue(col) * f).toInt())
        val inner = if (tint != null) mix(tint, .5f) else 0xFF1B3A4A.toInt()
        val outer = if (tint != null) mix(tint, .07f) else 0xFF060D12.toInt()
        p.shader = RadialGradient(270f, 420f, 640f, inner, outer, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, BW, BH, p); p.shader = null

        if (photo != null) drawLayer(c, softBlur(photo, st.bgBlur), photo.width, photo.height, st)
        c.drawColor(al(Color.BLACK, st.dim / 100f))
        p.shader = RadialGradient(270f, 160f, 520f, al(Color.WHITE, st.mainLight / 100f * .3f), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, BW, BH, p); p.shader = null

        layout(st, tracks).forEachIndexed { i, pl -> drawCard(c, st, tracks[i], covers.getOrNull(i), pl, i) }
        if (cutout != null && st.subjectInFront) drawLayer(c, cutout, cutout.width, cutout.height, st)
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
            vp.shader = RadialGradient(outW / 2f, outH / 2f, outH * .7f, intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, al(Color.BLACK, st.lens / 100f * .8f)),
                floatArrayOf(0f, .36f, 1f), Shader.TileMode.CLAMP)
            raw.drawRect(0f, 0f, outW.toFloat(), outH.toFloat(), vp)
        }
        // Footer: ikon Nexrify (lingkaran + batang gelombang) dan nama
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
        return bmp
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
        p.shader = RadialGradient(cx, cy, s, al(0xFFBDF3FF.toInt(), .8f), Color.TRANSPARENT, Shader.TileMode.CLAMP)
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

    private fun drawCard(c: Canvas, st: PosterState, t: Track, cover: Bitmap?, pl: Place, i: Int) {
        val w = CW; val h = CH; val l = -w / 2; val top = -h / 2
        val r = st.corner * .4f
        val rect = RectF(l, top, l + w, top + h)
        val op = st.opacity / 100f
        val lc = st.lightColor ?: Color.WHITE
        val milk = st.glass == GlassType.MILK
        val tc = if (milk) 0xFF15171C.toInt() else Color.WHITE
        val fill = when (st.glass) {
            GlassType.SMOKE -> al(0xFF2C2C30.toInt(), op)
            GlassType.CLEAR -> al(Color.WHITE, .1f + .12f * op)
            GlassType.MILK -> al(0xFFECECF2.toInt(), .35f + .4f * op)
            GlassType.DARK -> al(0xFF0A0A0C.toInt(), op)
        }
        c.save()
        c.translate(pl.x, pl.y); c.rotate(pl.rot); c.scale(pl.s, pl.s)
        // Miring 3D asli lewat Camera (perspektif + miring per kartu)
        c.concat(camMatrix(st, t, i))

        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.BLACK
        p.setShadowLayer(st.cardLight * 1.1f + 1f, 0f, 0f, al(lc, st.cardLight / 100f * .55f))
        c.drawRoundRect(rect, r, r, p)
        val a = Math.toRadians(st.shadowAngle.toDouble())
        val dist = st.shadowDistance * .6f
        p.color = fill
        p.setShadowLayer(st.shadowBlur * .9f + 1f, (Math.cos(a) * dist).toFloat(), (Math.sin(a) * dist).toFloat(), al(Color.BLACK, st.shadowPower / 100f))
        c.drawRoundRect(rect, r, r, p)
        p.clearShadowLayer()
        p.shader = LinearGradient(l, top, l + w, top + h, al(Color.WHITE, st.shine / 100f * .28f), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, r, r, p); p.shader = null
        p.style = Paint.Style.STROKE; p.strokeWidth = 1f + st.thickness / 22f; p.color = al(Color.WHITE, st.edge / 100f * .4f)
        c.drawRoundRect(rect, r, r, p); p.style = Paint.Style.FILL

        // Sampul
        val mg = 14f; val cs = w - 2 * mg; val cy = top + mg
        val cr = RectF(l + mg, cy, l + mg + cs, cy + cs)
        c.save()
        c.clipPath(Path().apply { addRoundRect(cr, r * .55f, r * .55f, Path.Direction.CW) })
        if (cover != null) c.drawBitmap(cover, null, cr, Paint(Paint.FILTER_BITMAP_FLAG))
        else { p.color = 0xFF334155.toInt(); c.drawRect(cr, p) }
        c.restore()

        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = tc }
        var y = cy + cs + 30f
        tp.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); tp.textSize = 8f; tp.alpha = 120
        c.drawText("NEXRIFY", l + mg, y - 17f, tp); tp.alpha = 255
        tp.typeface = Typeface.create("sans-serif", Typeface.BOLD); tp.textSize = 17f
        c.drawText(TextUtils.ellipsize(t.title, tp, cs - 24f, TextUtils.TruncateAt.END).toString(), l + mg, y, tp)
        tp.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); tp.textSize = 11f; tp.alpha = 150
        c.drawText(TextUtils.ellipsize(t.artist, tp, cs - 24f, TextUtils.TruncateAt.END).toString(), l + mg, y + 16f, tp)
        tp.alpha = 255; tp.textSize = 16f; tp.textAlign = Paint.Align.RIGHT
        c.drawText("♡", l + w - mg, y + 4f, tp); tp.textAlign = Paint.Align.LEFT

        y += 38f
        val bars = 30; val bwid = cs / bars
        for (bi in 0 until bars) {
            val hh = 3f + 9f * Math.abs(Math.sin(bi * 0.7 + t.title.length)).toFloat()
            p.color = if (bi < bars * .4f) 0xFF4FD1E8.toInt() else al(tc, .3f)
            c.drawRoundRect(RectF(l + mg + bi * bwid + 1f, y - hh / 2, l + mg + (bi + 1) * bwid - 1f, y + hh / 2), 1.5f, 1.5f, p)
        }
        p.color = tc
        tp.textSize = 9f; tp.alpha = 140
        c.drawText("0:40", l + mg, y + 14f, tp); tp.textAlign = Paint.Align.RIGHT
        c.drawText(t.formattedDuration, l + w - mg, y + 14f, tp)

        y += 36f
        val cx = 0f
        val ctrl = Path().apply {
            moveTo(cx - 34, y - 7); lineTo(cx - 44, y); lineTo(cx - 34, y + 7)
            moveTo(cx - 24, y - 7); lineTo(cx - 34, y); lineTo(cx - 24, y + 7)
            moveTo(cx + 24, y - 7); lineTo(cx + 34, y); lineTo(cx + 24, y + 7)
            moveTo(cx + 34, y - 7); lineTo(cx + 44, y); lineTo(cx + 34, y + 7)
        }
        p.alpha = 255; c.drawPath(ctrl, p)
        p.color = 0xFF4FD1E8.toInt(); c.drawCircle(cx, y, 15f, p)
        p.color = 0xFF06202B.toInt(); c.drawRect(cx - 5, y - 7, cx - 2, y + 7, p); c.drawRect(cx + 2, y - 7, cx + 5, y + 7, p)
        p.color = tc
        if (st.sparkles) star(c, l + w - 4f, top + 8f, st.sparkle * .8f)
        c.restore()
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

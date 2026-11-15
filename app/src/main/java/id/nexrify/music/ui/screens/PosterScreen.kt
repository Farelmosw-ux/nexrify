package id.nexrify.music.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import id.nexrify.music.data.model.Track
import id.nexrify.music.data.playlist.Playlist
import id.nexrify.music.data.poster.*
import id.nexrify.music.i18n.S
import id.nexrify.music.ui.icons.NexIcons
import id.nexrify.music.ui.theme.NexrifyAppTheme
import id.nexrify.music.ui.theme.Typography
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val MAX_SONGS = 5
private val LIGHT_COLORS = listOf(0xFFFFD9A8, 0xFFFFC94D, 0xFFFF9A4D, 0xFFFF6FA0, 0xFFDFE3F0).map { it.toInt() }

private suspend fun loadBmp(ctx: Context, data: Any, size: Int): Bitmap? = runCatching {
    val req = ImageRequest.Builder(ctx).data(data).size(size).allowHardware(false).build()
    (ctx.imageLoader.execute(req) as? SuccessResult)?.drawable?.toBitmap()
}.getOrNull()

private fun hitCard(st: PosterState, tracks: List<Track>, px: Float, py: Float): String? {
    val pl = PosterRenderer.layout(st, tracks)
    for (i in pl.indices.reversed()) {
        val p = pl[i]
        val a = Math.toRadians(-p.rot.toDouble())
        val dx = px - p.x; val dy = py - p.y
        val u = dx * cos(a) - dy * sin(a); val v = dx * sin(a) + dy * cos(a)
        if (abs(u) < PosterRenderer.CW / 2 * p.s && abs(v) < PosterRenderer.CH / 2 * p.s) return tracks[i].id
    }
    return null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PosterScreen(playlist: Playlist, initial: List<Track> = playlist.tracks.take(MAX_SONGS), onBack: () -> Unit) {
    val colors = NexrifyAppTheme.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val isEn = S.english
    fun tx(id: String, en: String) = if (isEn) en else id
    BackHandler(onBack = onBack)

    var st by remember {
        mutableStateOf(
            PosterState(
                likedIds = initial.filter { it.isFavorite }.map { it.id }.toSet()
            )
        )
    }
    var picked by remember { mutableStateOf(initial.take(MAX_SONGS)) }
    var tab by remember { mutableStateOf(0) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var cutout by remember { mutableStateOf<Bitmap?>(null) }
    var cutting by remember { mutableStateOf(false) }
    var cutJob by remember { mutableStateOf<Job?>(null) }
    var dragId by remember { mutableStateOf<String?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    fun setAdj(id: String, f: (CardAdj) -> CardAdj) { st = st.copy(adj = st.adj + (id to f(st.adj[id] ?: CardAdj()))) }
    var busy by remember { mutableStateOf(false) }

    val covers by produceState(listOf<Bitmap?>(), picked) {
        value = coroutineScope { picked.map { async { loadBmp(ctx, it.highResThumbnailUrl, 512) } }.awaitAll() }
    }
    val preview by produceState<Bitmap?>(null, st, picked, covers, photo, cutout, selectedId) {
        value = withContext(Dispatchers.Default) { PosterRenderer.render(st, picked, covers, photo, cutout, 540, selectedId = selectedId) }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            cutJob?.cancel()
            val b = loadBmp(ctx, uri, 1600) ?: return@launch
            photo = b; cutout = null
            cutting = true
            cutJob = launch {
                try { cutout = BackgroundRemover.cut(b) } finally { cutting = false }
            }
        }
    }

    fun export(share: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            val bmp = withContext(Dispatchers.Default) { PosterRenderer.render(st, picked, covers, photo, cutout, 1080) }
            val uri = withContext(Dispatchers.IO) { PosterExport.save(ctx, bmp) }
            busy = false
            if (uri == null) { Toast.makeText(ctx, tx("Gagal menyimpan poster", "Could not save the poster"), Toast.LENGTH_SHORT).show(); return@launch }
            if (share) {
                val i = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                ctx.startActivity(Intent.createChooser(i, null))
            } else Toast.makeText(ctx, tx("Poster tersimpan di galeri", "Poster saved to gallery"), Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize().background(colors.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(NexIcons.ArrowBack, null, tint = colors.textPrimary) }
            Text(tx("Poster lagu", "Song poster"), style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary, modifier = Modifier.weight(1f))
            TextButton(onClick = { st = PosterState(); photo = null; cutout = null }) { Text(tx("Atur ulang", "Reset"), color = colors.textMuted) }
            TextButton(onClick = { export(true) }) { Text(tx("Bagikan", "Share"), color = colors.textPrimary) }
            Button(
                onClick = { export(false) }, enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.background),
                shape = RoundedCornerShape(20.dp)
            ) { Text(tx("Simpan", "Save"), fontWeight = FontWeight.Bold) }
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
            preview?.let { bmp ->
                Image(
                    bmp.asImageBitmap(), contentDescription = null,
                    modifier = Modifier.fillMaxHeight().aspectRatio(9f / 16f).clip(RoundedCornerShape(8.dp))
                        .pointerInput(Unit) {
                            detectTapGestures { o ->
                                val k = PosterRenderer.BW / size.width
                                selectedId = hitCard(st, picked, o.x * k, o.y * k)
                                if (selectedId != null) tab = 1
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { o ->
                                    val k = PosterRenderer.BW / size.width
                                    dragId = hitCard(st, picked, o.x * k, o.y * k)
                                    if (dragId != null) { selectedId = dragId; tab = 1 }
                                },
                                onDragEnd = { dragId = null }, onDragCancel = { dragId = null },
                                onDrag = { ch, amt ->
                                    ch.consume()
                                    val id = dragId ?: return@detectDragGestures
                                    val k = PosterRenderer.BW / size.width
                                    val cur = st.offsets[id] ?: (0f to 0f)
                                    st = st.copy(offsets = st.offsets + (id to (cur.first + amt.x * k to cur.second + amt.y * k)))
                                }
                            )
                        }
                )
            }
            if (cutting) {
                Row(
                    Modifier.align(Alignment.TopCenter).padding(top = 12.dp).clip(RoundedCornerShape(24.dp))
                        .background(colors.card).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.accent)
                    Spacer(Modifier.width(8.dp))
                    Text(tx("Memotong latar…", "Cutting background…"), color = colors.textPrimary, style = Typography.bodySmall)
                    TextButton(onClick = { cutJob?.cancel() }) { Text(tx("Batal", "Cancel"), color = colors.accent) }
                }
            }
        }

        val tabs = listOf(tx("Foto", "Photo"), tx("Kartu", "Cards"), tx("Tampilan kartu", "Card look"), tx("Efek", "Effects"), tx("Stiker", "Stickers"), tx("Lagu", "Songs"))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tabs.size) { i -> PChip(tabs[i], tab == i) { tab = i } }
        }
        Column(Modifier.fillMaxWidth().height(290.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
            when (tab) {
                0 -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PChip(if (photo == null) tx("Pilih foto", "Choose photo") else tx("Ganti foto", "Change photo"), false) { picker.launch("image/*") }
                        if (photo != null) PChip(tx("Hapus foto", "Remove photo"), false) { cutJob?.cancel(); photo = null; cutout = null }
                    }
                    if (photo != null) {
                        PSlider(tx("Ukuran", "Size"), st.photoScale, 20..200) { st = st.copy(photoScale = it) }
                        PSlider(tx("Geser kiri-kanan", "Shift horizontally"), st.photoX, -100..100) { st = st.copy(photoX = it) }
                        PSlider(tx("Geser atas-bawah", "Shift vertically"), st.photoY, -100..100) { st = st.copy(photoY = it) }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PChip(tx("Isi layar", "Fill"), st.fit == PhotoFit.FILL) { st = st.copy(fit = PhotoFit.FILL) }
                            PChip(tx("Muat penuh", "Fit"), st.fit == PhotoFit.FIT) { st = st.copy(fit = PhotoFit.FIT) }
                            PChip(tx("Putar", "Rotate"), false) { st = st.copy(photoRot = (st.photoRot + 1) % 4) }
                            PChip(tx("Cermin", "Mirror"), st.mirror) { st = st.copy(mirror = !st.mirror) }
                        }
                        if (cutout != null) {
                            Spacer(Modifier.height(8.dp))
                            PChip(
                                if (st.subjectInFront) tx("Objek di depan kartu: aktif", "Subject in front of cards: on")
                                else tx("Objek di depan kartu: mati", "Subject in front of cards: off"), st.subjectInFront
                            ) { st = st.copy(subjectInFront = !st.subjectInFront) }
                        }
                    }
                    PLabel(tx("Latar", "Background"))
                    PSlider(tx("Gelap", "Dim"), st.dim) { st = st.copy(dim = it) }
                    PSlider(tx("Kabur", "Blur"), st.bgBlur) { st = st.copy(bgBlur = it) }
                    PSlider(tx("Cahaya kartu", "Card glow"), st.cardLight) { st = st.copy(cardLight = it) }
                    PLabel(tx("Warna cahaya", "Glow color"))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        PChip(tx("Otomatis", "Auto"), st.lightColor == null) { st = st.copy(lightColor = null) }
                        LIGHT_COLORS.forEach { col ->
                            Box(
                                Modifier.size(34.dp).clip(CircleShape).background(Color(col))
                                    .clickable { st = st.copy(lightColor = col) },
                                contentAlignment = Alignment.Center
                            ) { if (st.lightColor == col) Text("✓", color = Color.Black, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
                1 -> {
                    PLabel(tx("Tata letak", "Layout"))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(PosterLayout.ORBIT to tx("Melayang", "Float"), PosterLayout.STACK to tx("Kipas", "Fan"), PosterLayout.ROW to tx("Baris", "Row"), PosterLayout.COLUMN to tx("Kolom", "Column"))
                            .forEach { (l, n) -> PChip(n, st.layout == l) { st = st.copy(layout = l, offsets = emptyMap(), adj = emptyMap()) } }
                        PChip(tx("Rapikan", "Tidy up"), false) { st = st.tidy() }
                    }
                    val sel = picked.firstOrNull { it.id == selectedId }
                    if (sel == null) {
                        Spacer(Modifier.height(10.dp))
                        Text(tx("Ketuk kartu di gambar untuk mengatur ukuran, putaran, like, waktu lagu, dan kemiringannya. Seret untuk memindahkan.", "Tap a card on the poster to set its size, rotation, like, song time and tilt. Drag to move it."), color = colors.textMuted, style = Typography.bodyMedium)
                    } else {
                        val a = st.adj[sel.id] ?: CardAdj()
                        val isLiked = sel.id in st.likedIds
                        val progPct = ((st.progressMap[sel.id] ?: 0.35f) * 100).roundToInt()
                        PLabel(sel.title)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PChip(if (isLiked) "♥ Liked" else "♡ Like", isLiked) {
                                st = st.copy(likedIds = if (isLiked) st.likedIds - sel.id else st.likedIds + sel.id)
                            }
                            PChip(tx("Atur ulang kartu ini", "Reset this card"), false) {
                                st = st.copy(adj = st.adj - sel.id, offsets = st.offsets - sel.id)
                            }
                        }
                        PSlider(tx("Waktu lagu", "Song progress"), progPct, 0..100) { v ->
                            st = st.copy(progressMap = st.progressMap + (sel.id to (v / 100f)))
                        }
                        PSlider(tx("Ukuran", "Size"), a.size, 30..200) { v -> setAdj(sel.id) { it.copy(size = v) } }
                        PSlider(tx("Putar", "Rotate"), a.rot, -180..180, "°") { v -> setAdj(sel.id) { it.copy(rot = v) } }
                        PSlider(tx("Miring datar", "Tilt sideways"), a.tiltX, -60..60, "°") { v -> setAdj(sel.id) { it.copy(tiltX = v) } }
                        PSlider(tx("Miring tegak", "Tilt up-down"), a.tiltY, -60..60, "°") { v -> setAdj(sel.id) { it.copy(tiltY = v) } }
                    }
                }
                2 -> {
                    PLabel(tx("Kaca", "Glass"))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(GlassType.SMOKE to tx("Kaca kelabu", "Grey glass"), GlassType.CLEAR to tx("Kaca bening", "Clear"), GlassType.MILK to tx("Kaca susu", "Milky"), GlassType.DARK to tx("Gelap", "Dark"))
                            .forEach { (g, n) -> PChip(n, st.glass == g) { st = st.copy(glass = g) } }
                    }
                    PSlider(tx("Perspektif", "Perspective"), st.perspective) { st = st.copy(perspective = it) }
                    PSlider(tx("Ketebalan", "Thickness"), st.thickness) { st = st.copy(thickness = it) }
                    PSlider(tx("Kekeruhan", "Opacity"), st.opacity) { st = st.copy(opacity = it) }
                    PSlider(tx("Sudut", "Corners"), st.corner) { st = st.copy(corner = it) }
                    PSlider(tx("Pantulan", "Reflection"), st.shine) { st = st.copy(shine = it) }
                    PLabel(tx("Bayangan", "Shadow"))
                    PSlider(tx("Kekuatan", "Strength"), st.shadowPower) { st = st.copy(shadowPower = it) }
                    PSlider(tx("Kabur", "Blur"), st.shadowBlur) { st = st.copy(shadowBlur = it) }
                    PSlider(tx("Jarak", "Distance"), st.shadowDistance) { st = st.copy(shadowDistance = it) }
                    PSlider(tx("Arah", "Direction"), st.shadowAngle, 0..360, "°") { st = st.copy(shadowAngle = it) }
                    PChip(tx("Atur ulang gaya kartu", "Reset card style"), false) { st = st.resetCardStyle() }
                }
                3 -> {
                    PSlider(tx("Cahaya utama", "Main light"), st.mainLight) { st = st.copy(mainLight = it) }
                    PSlider(tx("Pendar", "Bloom"), st.bloom) { st = st.copy(bloom = it) }
                    PSlider(tx("Kilau", "Sparkle"), st.sparkle) { st = st.copy(sparkle = it) }
                    PSlider(tx("Garis lensa", "Vignette"), st.lens) { st = st.copy(lens = it) }
                    PSlider(tx("Butiran film", "Film grain"), st.grain) { st = st.copy(grain = it) }
                    PSlider(tx("Tepi kartu", "Card edge"), st.edge) { st = st.copy(edge = it) }
                }
                4 -> {
                    Text(tx("Stiker kilau di pojok kartu.", "Sparkle sticker on card corners."), color = colors.textMuted, style = Typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PChip(tx("Kilau", "Sparkle"), st.sparkles) { st = st.copy(sparkles = !st.sparkles) }
                    }
                }
                else -> {
                    picked.forEachIndexed { i, t ->
                        val isLiked = t.id in st.likedIds
                        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(t.title, color = colors.textPrimary, maxLines = 1, style = Typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(t.artist, color = colors.textMuted, maxLines = 1, style = Typography.bodySmall)
                            }
                            TextButton(onClick = {
                                st = st.copy(likedIds = if (isLiked) st.likedIds - t.id else st.likedIds + t.id)
                            }) { Text(if (isLiked) "♥" else "♡", color = if (isLiked) Color(0xFFFF3B30) else colors.textMuted) }
                            fun swap(j: Int) { if (j in picked.indices) picked = picked.toMutableList().also { l -> val x = l[i]; l[i] = l[j]; l[j] = x } }
                            TextButton(onClick = { swap(i - 1) }) { Text("▲", color = colors.textMuted) }
                            TextButton(onClick = { swap(i + 1) }) { Text("▼", color = colors.textMuted) }
                            TextButton(onClick = { if (picked.size > 1) picked = picked.filterIndexed { j, _ -> j != i } }) { Text("✕", color = colors.textMuted) }
                        }
                    }
                    val rest = playlist.tracks.filter { t -> picked.none { it.id == t.id } }
                    if (picked.size < MAX_SONGS && rest.isNotEmpty()) {
                        PLabel(tx("Tambah dari playlist", "Add from playlist"))
                        rest.forEach { t ->
                            Text("+  ${t.title} · ${t.artist}", color = colors.textPrimary, maxLines = 1, style = Typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth().clickable { picked = picked + t }.padding(vertical = 8.dp))
                        }
                    } else if (picked.size >= MAX_SONGS) {
                        Text(tx("Maksimal $MAX_SONGS lagu.", "Up to $MAX_SONGS songs."), color = colors.textMuted, style = Typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun PChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = NexrifyAppTheme.colors
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (selected) colors.accent else colors.card)
            .clickable(onClick = onClick).padding(horizontal = 15.dp, vertical = 9.dp)
    ) { Text(text, color = if (selected) colors.background else colors.textPrimary, fontWeight = FontWeight.Medium, style = Typography.bodyMedium, maxLines = 1) }
}

@Composable
private fun PLabel(text: String) {
    Text(text, color = NexrifyAppTheme.colors.textMuted, style = Typography.bodySmall, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

@Composable
private fun PSlider(label: String, v: Int, range: IntRange = 0..100, unit: String = "%", onChange: (Int) -> Unit) {
    val colors = NexrifyAppTheme.colors
    Column(Modifier.padding(bottom = 2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = colors.textMuted, style = Typography.bodySmall)
            Text("$v$unit", color = colors.textPrimary, style = Typography.bodySmall)
        }
        Slider(
            value = v.toFloat(), onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent, inactiveTrackColor = colors.card)
        )
    }
}

/** Langkah 1: pilih sampai 5 lagu dari playlist, lalu lanjut ke editor poster. */
@Composable
fun PosterSongPicker(playlist: Playlist, onBack: () -> Unit, onNext: (List<Track>) -> Unit) {
    val colors = NexrifyAppTheme.colors
    val isEn = S.english
    fun tx(id: String, en: String) = if (isEn) en else id
    BackHandler(onBack = onBack)
    var chosen by remember { mutableStateOf(playlist.tracks.take(MAX_SONGS).map { it.id }) }
    Column(Modifier.fillMaxSize().background(colors.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(NexIcons.ArrowBack, null, tint = colors.textPrimary) }
            Text(tx("Pilih lagu untuk poster", "Choose songs for the poster"), style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tx("${chosen.size} dari $MAX_SONGS", "${chosen.size} of $MAX_SONGS"), color = colors.textPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            PChip(tx("Pilih $MAX_SONGS pertama", "Pick first $MAX_SONGS"), false) { chosen = playlist.tracks.take(MAX_SONGS).map { it.id } }
        }
        androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(playlist.tracks, key = { it.id }) { t ->
                val on = t.id in chosen
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { chosen = if (on) chosen - t.id else if (chosen.size < MAX_SONGS) chosen + t.id else chosen }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    coil.compose.AsyncImage(
                        model = t.thumbnailUrl, contentDescription = null,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(colors.card)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.title, color = colors.textPrimary, maxLines = 1, fontWeight = FontWeight.Medium, style = Typography.bodyMedium)
                        Text(t.artist, color = colors.textMuted, maxLines = 1, style = Typography.bodySmall)
                    }
                    Box(
                        Modifier.size(26.dp).clip(CircleShape).background(if (on) colors.accent else Color.Transparent)
                            .then(if (on) Modifier else Modifier.border(1.5.dp, colors.textMuted, CircleShape)),
                        contentAlignment = Alignment.Center
                    ) { if (on) Icon(NexIcons.Check, null, tint = colors.background, modifier = Modifier.size(16.dp)) }
                }
            }
        }
        Button(
            onClick = { onNext(playlist.tracks.filter { it.id in chosen }.sortedBy { chosen.indexOf(it.id) }) },
            enabled = chosen.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.background),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(50.dp)
        ) { Text(tx("Lanjut", "Next"), fontWeight = FontWeight.Bold) }
    }
}

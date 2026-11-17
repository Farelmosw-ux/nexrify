package id.nexrify.music.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.nexrify.music.data.account.AccountManager
import id.nexrify.music.data.chat.ChatXApi
import id.nexrify.music.data.chat.FeedbackItem
import id.nexrify.music.data.chat.FeedbackUpload
import id.nexrify.music.i18n.S
import id.nexrify.music.i18n.currentStrings
import id.nexrify.music.ui.components.NexNotifier
import id.nexrify.music.ui.components.chatErrText
import id.nexrify.music.ui.icons.NexIcons
import id.nexrify.music.ui.theme.NexrifyAppTheme
import id.nexrify.music.ui.theme.Typography
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.DateFormat
import java.util.Date

private val FB_GREEN = Color(0xFF3DDC84)
private val FB_RED = Color(0xFFE5484D)
private val FB_AMBER = Color(0xFFF5B83D)
private val FB_BLUE = Color(0xFF60A5FA)

@Composable
private fun statusInfo(status: String): Pair<String, Color> = when (status) {
    "planned" -> S.feedbackStatusPlanned to FB_BLUE
    "progress" -> S.feedbackStatusProgress to FB_AMBER
    "done" -> S.feedbackStatusDone to FB_GREEN
    "rejected" -> S.feedbackStatusRejected to FB_RED
    else -> S.feedbackStatusOpen to NexrifyAppTheme.colors.textMuted
}

private const val MAX_ATTACH = 3

/* ------------------------------------------------------------------ gambar lampiran */

private val mediaCache = object : LruCache<String, Bitmap>(24) {}

/** Muat gambar lampiran (butuh login), di-cache di memori. */
@Composable
private fun rememberFbImage(itemId: String, index: Int): Bitmap? {
    val key = "$itemId:$index"
    val state = produceState<Bitmap?>(mediaCache.get(key), key) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = ChatXApi.feedbackMedia(AccountManager.getInstance().token().orEmpty(), itemId, index)
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1400) sample *= 2
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                }.getOrNull()?.also { mediaCache.put(key, it) }
            }
        }
    }
    return state.value
}

@Composable
private fun FbThumb(itemId: String, index: Int, size: Int, onClick: () -> Unit) {
    val colors = NexrifyAppTheme.colors
    val bmp = rememberFbImage(itemId, index)
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.size(size.dp).clip(shape).background(colors.border.copy(alpha = 0.5f)).border(1.dp, colors.border, shape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) Image(bmp.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else CircularProgressIndicator(color = colors.textMuted, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaViewer(item: FeedbackItem, start: Int, onDismiss: () -> Unit) {
    val pager = rememberPagerState(initialPage = start) { item.media.size }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.96f))) {
            HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                val bmp = rememberFbImage(item.id, page)
                Column(Modifier.fillMaxSize().clickable(onClick = onDismiss).statusBarsPadding().navigationBarsPadding().padding(vertical = 56.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    if (bmp != null) Image(bmp.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.weight(1f).fillMaxWidth())
                    else Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp)) }
                    val cap = item.media.getOrNull(page).orEmpty()
                    if (cap.isNotBlank()) Text(cap, color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp))
                }
            }
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${pager.currentPage + 1} / ${item.media.size}", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 8.dp))
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                    Icon(NexIcons.Close, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ layar utama */

@Composable
fun FeedbackScreen(onBack: () -> Unit) {
    val colors = NexrifyAppTheme.colors
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<FeedbackItem>?>(null) }
    var tab by remember { mutableStateOf(0) }
    var showNew by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<FeedbackItem?>(null) }
    var viewer by remember { mutableStateOf<Pair<FeedbackItem, Int>?>(null) }
    val fmt = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }

    fun token(): String = AccountManager.getInstance().token().orEmpty()

    suspend fun load() {
        try { items = ChatXApi.feedbackList(token()) }
        catch (e: CancellationException) { throw e }
        catch (e: Throwable) { if (items == null) items = emptyList(); NexNotifier.warning(chatErrText(e), currentStrings().feedbackTitle) }
    }
    LaunchedEffect(Unit) { load() }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                    Icon(NexIcons.ArrowBack, null, tint = colors.textPrimary, modifier = Modifier.size(22.dp))
                }
                Text(S.feedbackTitle, style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary, modifier = Modifier.weight(1f).padding(horizontal = 6.dp))
                Box(Modifier.size(42.dp).clip(CircleShape).clickable { scope.launch { load() } }, contentAlignment = Alignment.Center) {
                    Icon(NexIcons.Refresh, null, tint = colors.textPrimary, modifier = Modifier.size(22.dp))
                }
            }

            // Pemilih tab bergaya segmented
            val segShape = RoundedCornerShape(16.dp)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(segShape).background(colors.card).border(1.dp, colors.border, segShape).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Segment(S.feedbackTabFeatures, NexIcons.Lightbulb, tab == 0, Modifier.weight(1f)) { tab = 0 }
                Segment(S.feedbackTabMine, NexIcons.Bug, tab == 1, Modifier.weight(1f)) { tab = 1 }
            }

            val all = items
            if (all == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.textPrimary, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                }
            } else {
                val shown = if (tab == 0) all.filter { it.kind == "feature" } else all.filter { it.mine }
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 120.dp)) {
                    if (tab == 1) {
                        item {
                            Row(
                                Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(14.dp)).background(colors.card.copy(alpha = 0.7f)).padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(NexIcons.Lock, null, tint = colors.textMuted, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(S.feedbackBugPrivacy, fontSize = 12.sp, lineHeight = 16.sp, color = colors.textMuted)
                            }
                        }
                    }
                    if (shown.isEmpty()) {
                        item {
                            Column(Modifier.fillMaxWidth().padding(vertical = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(64.dp).clip(CircleShape).background(colors.card).border(1.dp, colors.border, CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(if (tab == 0) NexIcons.Lightbulb else NexIcons.Bug, null, tint = colors.textMuted, modifier = Modifier.size(28.dp))
                                }
                                Spacer(Modifier.height(14.dp))
                                Text(if (tab == 0) S.feedbackEmpty else S.feedbackMineEmpty, color = colors.textMuted, textAlign = TextAlign.Center, fontSize = 14.sp)
                            }
                        }
                    }
                    items(shown, key = { it.id }) { it ->
                        FeedbackCard(
                            it, fmt, onVote = {
                                scope.launch {
                                    try {
                                        val upd = ChatXApi.feedbackVote(token(), it.id)
                                        items = items?.map { x -> if (x.id == upd.id) upd else x }
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Throwable) {
                                        NexNotifier.warning(chatErrText(e), currentStrings().feedbackTitle)
                                    }
                                }
                            },
                            onDelete = { confirmDelete = it },
                            onOpenMedia = { idx -> viewer = it to idx }
                        )
                    }
                }
            }
        }

        // Tombol kirim melayang dengan gradasi di belakangnya
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, colors.background.copy(alpha = 0.92f), colors.background)))
                .navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 14.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(colors.textPrimary).clickable { showNew = true },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
            ) {
                Icon(NexIcons.Add, null, tint = colors.background, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(S.feedbackNew, color = colors.background, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showNew) {
        NewFeedbackSheet(
            onSent = { item ->
                showNew = false
                items = listOf(item) + (items ?: emptyList())
                tab = if (item.kind == "bug") 1 else 0
                NexNotifier.success(currentStrings().feedbackSent, currentStrings().feedbackTitle)
            },
            onDismiss = { showNew = false }
        )
    }
    viewer?.let { (item, idx) -> MediaViewer(item, idx) { viewer = null } }
    confirmDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = colors.card,
            title = { Text(S.feedbackDelete, color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = { Text(item.title, color = colors.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    scope.launch {
                        try {
                            ChatXApi.feedbackDelete(token(), item.id)
                            items = items?.filter { it.id != item.id }
                            NexNotifier.info(currentStrings().feedbackDeleted, currentStrings().feedbackTitle)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            NexNotifier.warning(chatErrText(e), currentStrings().feedbackTitle)
                        }
                    }
                }) { Text(S.feedbackDelete, color = FB_RED, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(S.cancel, color = colors.textSecondary) } }
        )
    }
}

@Composable
private fun Segment(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = NexrifyAppTheme.colors
    val shape = RoundedCornerShape(13.dp)
    Row(
        modifier.clip(shape).background(if (selected) colors.textPrimary else Color.Transparent).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = if (selected) colors.background else colors.textSecondary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (selected) colors.background else colors.textSecondary)
    }
}

@Composable
private fun FeedbackCard(it: FeedbackItem, fmt: DateFormat, onVote: () -> Unit, onDelete: () -> Unit, onOpenMedia: (Int) -> Unit) {
    val colors = NexrifyAppTheme.colors
    val (label, tone) = statusInfo(it.status)
    val shape = RoundedCornerShape(22.dp)
    val kindTone = if (it.kind == "bug") FB_RED else FB_AMBER
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(shape).background(colors.card).border(1.dp, colors.border, shape).height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(tone.copy(alpha = if (it.status == "open") 0.0f else 0.9f)))
        Column(Modifier.weight(1f).padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(kindTone.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Icon(if (it.kind == "bug") NexIcons.Bug else NexIcons.Lightbulb, null, tint = kindTone, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(it.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp, color = colors.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = tone,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(tone.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            if (it.body.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(it.body, fontSize = 14.sp, lineHeight = 20.sp, color = colors.textSecondary, maxLines = 6, overflow = TextOverflow.Ellipsis)
            }
            if (it.media.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    it.media.forEachIndexed { idx, cap ->
                        Column(Modifier.width(96.dp)) {
                            FbThumb(it.id, idx, 96) { onOpenMedia(idx) }
                            if (cap.isNotBlank()) Text(cap, fontSize = 11.sp, lineHeight = 14.sp, color = colors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
            if (it.note.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.border.copy(alpha = 0.5f)).height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(tone))
                    Column(Modifier.padding(10.dp)) {
                        Text(S.feedbackOwnerNote, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.textMuted)
                        Spacer(Modifier.height(2.dp))
                        Text(it.note, fontSize = 13.sp, lineHeight = 18.sp, color = colors.textPrimary)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (it.kind == "feature" && it.author.isNotBlank()) "@${it.author} · " else "") + fmt.format(Date(it.createdAt)),
                    fontSize = 11.sp, color = colors.textMuted, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (it.mine && it.status == "open") {
                    Text(
                        S.feedbackDelete, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FB_RED,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onDelete).padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                if (it.kind == "feature") {
                    val canVote = it.status != "done" && it.status != "rejected"
                    val pill = RoundedCornerShape(16.dp)
                    Row(
                        Modifier.clip(pill)
                            .background(if (it.voted) colors.textPrimary else Color.Transparent)
                            .border(1.dp, if (it.voted) colors.textPrimary else colors.border, pill)
                            .clickable(enabled = canVote, onClick = onVote)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(NexIcons.Check, null, tint = if (it.voted) colors.background else colors.textPrimary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(S.feedbackVotes(it.votes), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (it.voted) colors.background else colors.textPrimary)
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ form kirim */

private class Attach(val uri: Uri, val bytes: ByteArray, val bitmap: Bitmap) { var caption by mutableStateOf("") }

/** Perkecil dan kompres gambar ke JPEG (maks. 1280 px, di bawah ~600 KB) supaya muat di batas permintaan server. */
private suspend fun prepareImage(context: android.content.Context, uri: Uri): Attach? = withContext(Dispatchers.IO) {
    runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return@runCatching null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return@runCatching null
        val scale = 1280f / maxOf(decoded.width, decoded.height)
        val bmp = if (scale < 1f) Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt().coerceAtLeast(1), (decoded.height * scale).toInt().coerceAtLeast(1), true) else decoded
        var q = 85
        var out: ByteArray
        do {
            val bos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, q, bos)
            out = bos.toByteArray()
            q -= 10
        } while (out.size > 600_000 && q >= 35)
        if (out.size > 680_000) null else Attach(uri, out, bmp)
    }.getOrNull()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewFeedbackSheet(onSent: (FeedbackItem) -> Unit, onDismiss: () -> Unit) {
    val colors = NexrifyAppTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf("feature") }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val attachments = remember { mutableStateListOf<Attach>() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            var failed = false
            for (u in uris) {
                if (attachments.size >= MAX_ATTACH) { NexNotifier.info(currentStrings().feedbackMediaMax, currentStrings().feedbackTitle); break }
                val a = prepareImage(context, u)
                if (a != null) attachments.add(a) else failed = true
            }
            if (failed) NexNotifier.warning(currentStrings().feedbackMediaFail, currentStrings().feedbackTitle)
        }
    }

    fun submit() {
        val t = title.trim(); val b = body.trim()
        val strings = currentStrings()
        if (t.length < 4) { NexNotifier.warning(strings.feedbackNeedTitle, strings.feedbackTitle); return }
        if (attachments.isEmpty() && b.length < 10) { NexNotifier.warning(strings.feedbackNeedBody, strings.feedbackTitle); return }
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
        val device = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), Nexrify $version"
        scope.launch {
            busy = true
            try {
                val token = AccountManager.getInstance().token().orEmpty()
                val media = withContext(Dispatchers.Default) { attachments.map { FeedbackUpload(Base64.encodeToString(it.bytes, Base64.NO_WRAP), it.caption.trim()) } }
                onSent(ChatXApi.feedbackSubmit(token, kind, t, b, if (kind == "bug") device else "", media))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                NexNotifier.warning(chatErrText(e), currentStrings().feedbackTitle)
            }
            busy = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { Box(Modifier.padding(vertical = 10.dp).width(36.dp).height(4.dp).clip(CircleShape).background(colors.border)) }
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(S.feedbackNew, style = Typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KindCard(S.feedbackKindFeature, S.feedbackKindHintFeature, NexIcons.Lightbulb, FB_AMBER, kind == "feature", Modifier.weight(1f)) { kind = "feature" }
                KindCard(S.feedbackKindBug, S.feedbackKindHintBug, NexIcons.Bug, FB_RED, kind == "bug", Modifier.weight(1f)) { kind = "bug" }
            }
            Spacer(Modifier.height(16.dp))
            Field(S.feedbackTitleLabel, title, 80, singleLine = true) { title = it }
            Spacer(Modifier.height(12.dp))
            val bodyLabel = (if (kind == "bug") S.feedbackBodyBug else S.feedbackBodyFeature) + if (attachments.isNotEmpty()) " (${S.feedbackOptional})" else ""
            Field(bodyLabel, body, 800, singleLine = false) { body = it }

            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(S.feedbackAttachTitle, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colors.textPrimary)
                Spacer(Modifier.width(8.dp))
                Text("${attachments.size}/$MAX_ATTACH", fontSize = 12.sp, color = colors.textMuted)
            }
            Text(S.feedbackAttachHint, fontSize = 12.sp, lineHeight = 16.sp, color = colors.textMuted, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))

            attachments.forEachIndexed { idx, a ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(18.dp)).background(colors.card).border(1.dp, colors.border, RoundedCornerShape(18.dp)).padding(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(Modifier.size(76.dp).clip(RoundedCornerShape(12.dp))) {
                        Image(a.bitmap.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.background).border(1.dp, colors.border, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 9.dp)
                        ) {
                            if (a.caption.isEmpty()) Text(S.feedbackCaptionHint, fontSize = 13.sp, color = colors.textMuted)
                            BasicTextField(
                                value = a.caption, onValueChange = { a.caption = it.take(120).replace("\n", " ") }, singleLine = true,
                                textStyle = TextStyle(color = colors.textPrimary, fontSize = 13.sp), cursorBrush = SolidColor(colors.textPrimary), modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            S.feedbackDelete, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FB_RED,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { attachments.removeAt(idx) }.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            if (attachments.size < MAX_ATTACH) {
                val dash = RoundedCornerShape(18.dp)
                Row(
                    Modifier.fillMaxWidth().height(56.dp).clip(dash).border(1.dp, colors.border, dash).clickable { picker.launch("image/*") },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
                ) {
                    Icon(NexIcons.ImagePick, null, tint = colors.textPrimary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(S.feedbackAttachAdd, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                }
            }

            if (kind == "bug") {
                Spacer(Modifier.height(12.dp))
                Text(S.feedbackDeviceNote + " " + S.feedbackBugPrivacy, fontSize = 12.sp, lineHeight = 16.sp, color = colors.textMuted)
            }
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(colors.textPrimary).clickable(enabled = !busy, onClick = ::submit),
                contentAlignment = Alignment.Center
            ) {
                if (busy) CircularProgressIndicator(color = colors.background, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                else Text(S.feedbackSubmit, color = colors.background, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun KindCard(title: String, hint: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tone: Color, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = NexrifyAppTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.clip(shape).background(if (selected) tone.copy(alpha = 0.12f) else colors.card)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) tone else colors.border, shape).clickable(onClick = onClick).padding(12.dp)
    ) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(tone.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tone, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colors.textPrimary)
        Text(hint, fontSize = 11.sp, lineHeight = 14.sp, color = colors.textMuted, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun Field(label: String, value: String, max: Int, singleLine: Boolean, onChange: (String) -> Unit) {
    val colors = NexrifyAppTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Row {
            Text(label, fontSize = 12.sp, color = colors.textSecondary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${value.length}/$max", fontSize = 11.sp, color = colors.textMuted)
        }
        Spacer(Modifier.height(6.dp))
        val shape = RoundedCornerShape(16.dp)
        Box(Modifier.fillMaxWidth().clip(shape).background(colors.card).border(1.dp, colors.border, shape).padding(horizontal = 14.dp, vertical = 12.dp)) {
            BasicTextField(
                value = value,
                onValueChange = { onChange(it.take(max).let { t -> if (singleLine) t.replace("\n", " ") else t }) },
                singleLine = singleLine,
                maxLines = if (singleLine) 1 else 8,
                textStyle = TextStyle(color = colors.textPrimary, fontSize = 15.sp, lineHeight = 21.sp),
                cursorBrush = SolidColor(colors.textPrimary),
                modifier = Modifier.fillMaxWidth().then(if (singleLine) Modifier else Modifier.height(130.dp))
            )
        }
    }
}

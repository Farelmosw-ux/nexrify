package id.nexrify.music.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import id.nexrify.music.data.chat.ChatMessage
import id.nexrify.music.data.chat.ChatProfile
import id.nexrify.music.data.chat.ChatReply
import id.nexrify.music.data.mubar.MubarEvent
import id.nexrify.music.data.account.AccountError
import id.nexrify.music.data.account.AccountManager
import id.nexrify.music.data.chat.CHAT_EMOJIS_QUICK
import id.nexrify.music.data.chat.ChatManager
import id.nexrify.music.data.chat.FollowUser
import id.nexrify.music.data.model.Track
import id.nexrify.music.data.mubar.MubarInvite
import id.nexrify.music.data.mubar.MubarManager
import id.nexrify.music.data.mubar.MubarMember
import id.nexrify.music.data.mubar.MubarMsg
import id.nexrify.music.data.mubar.MubarTrack
import id.nexrify.music.i18n.S
import id.nexrify.music.i18n.currentStrings
import id.nexrify.music.ui.icons.NexIcons
import id.nexrify.music.ui.theme.NexrifyAppTheme
import id.nexrify.music.ui.theme.Typography
import id.nexrify.music.viewmodel.MusicViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private fun MubarTrack.toTrack() = Track(
    id = id,
    url = url.ifBlank { "https://music.youtube.com/watch?v=$id" },
    title = title,
    artist = artist,
    thumbnailUrl = thumb,
    durationSeconds = durationSec.toLong()
)

/* ============================================================
 * Host: dipasang sekali di root app. Mengurus sinkronisasi lagu,
 * undangan masuk, pemberitahuan ruangan bubar, dan lembar ruangan.
 * ============================================================ */

@Composable
fun MubarHost(viewModel: MusicViewModel) {
    val mgr = remember { MubarManager.getInstance() }
    val room by mgr.room.collectAsState()
    val invites by mgr.invites.collectAsState()
    val open by mgr.sheetOpen.collectAsState()
    val scope = rememberCoroutineScope()
    var wasIn by remember { mutableStateOf(false) }
    var joining by remember { mutableStateOf(false) }

    // Kontrol pemutar dikunci selama di ruangan; keluar/bubar menghentikan lagu bersama.
    LaunchedEffect(room != null) {
        viewModel.setMubarLocked(room != null)
        if (wasIn && room == null) viewModel.mubarStop()
        wasIn = room != null
    }

    LaunchedEffect(Unit) {
        mgr.ended.collect { why ->
            val st = currentStrings()
            NexNotifier.info(
                when (why) {
                    "host_left", "host_gone" -> st.mubarEndedHost
                    "timeout" -> st.mubarEndedTimeout
                    else -> st.mubarEndedGeneric
                },
                st.mubarTitle
            )
        }
    }

    // Saat lagu baru dipilih (atau ruangan baru dimasuki), semua anggota langsung memutar lagu itu di posisi bersama.
    val trackId = room?.track?.id
    val startedAt = room?.startedAt ?: 0L
    LaunchedEffect(trackId, startedAt) {
        val r = mgr.room.value ?: return@LaunchedEffect
        val t = r.track ?: return@LaunchedEffect
        val elapsed = (mgr.serverNowMs() - r.startedAt).coerceAtLeast(0L)
        if (elapsed < t.durationSec * 1000L) viewModel.playTrack(t.toTrack(), startPositionMs = elapsed)
    }

    // Koreksi selisih posisi tiap beberapa detik (jaringan lambat, buffering, jeda sendiri lalu lanjut).
    LaunchedEffect(Unit) {
        while (true) {
            delay(4_000L)
            val r = mgr.room.value ?: continue
            val t = r.track ?: continue
            val expected = mgr.serverNowMs() - r.startedAt
            if (expected < 0 || expected >= t.durationSec * 1000L) continue
            val cur = viewModel.currentTrack.value
            if (cur?.id != t.id) {
                viewModel.playTrack(t.toTrack(), startPositionMs = expected)
            } else if (viewModel.isPlaying.value && abs(viewModel.playbackPosition.value - expected) > 3_500L) {
                viewModel.mubarSeek(expected)
            }
        }
    }

    // Pemberitahuan anggota: gabung, menolak, keluar.
    LaunchedEffect(Unit) {
        mgr.events.collect { e ->
            val st = currentStrings()
            when (e.kind) {
                "joined" -> NexNotifier.success(st.mubarMemberJoined(e.display), st.mubarTitle)
                "declined" -> NexNotifier.warning(st.mubarMemberDeclined(e.display), st.mubarTitle)
                "left" -> NexNotifier.info(st.mubarMemberLeft(e.display), st.mubarTitle)
            }
        }
    }

    // Undangan masuk: balon yang meluncur dari kanan, hilang sendiri setelah 5 detik (hanya saat belum di ruangan).
    val hidden = remember { mutableStateListOf<String>() }
    val inv = invites.firstOrNull { it.code !in hidden }
    if (room == null && inv != null) {
        MubarInviteToast(
            inv = inv, busy = joining,
            onAccept = {
                scope.launch {
                    joining = true
                    try { mgr.accept(inv) } catch (e: CancellationException) { throw e } catch (_: Throwable) {
                        NexNotifier.error(currentStrings().mubarJoinFail, currentStrings().mubarTitle)
                        runCatching { mgr.refresh() }
                    }
                    joining = false
                }
            },
            onDecline = { scope.launch { runCatching { mgr.decline(inv) } } },
            onExpire = { hidden.add(inv.code) }
        )
    }

    if (open) MubarSheet(viewModel = viewModel, onDismiss = { mgr.closeSheet() })
}

/* ============================================================
 * Balon undangan: meluncur dari kanan ke kiri ala undangan game
 * ============================================================ */

private val DANGER = Color(0xFFE5484D)
private const val INVITE_MS = 5000L

@Composable
private fun MubarInviteToast(inv: MubarInvite, busy: Boolean, onAccept: () -> Unit, onDecline: () -> Unit, onExpire: () -> Unit) {
    val colors = NexrifyAppTheme.colors
    val density = LocalDensity.current
    val topPx = WindowInsets.statusBars.getTop(density) + with(density) { 12.dp.roundToPx() }
    val marginPx = with(density) { 12.dp.roundToPx() }
    val state = remember(inv.code) { MutableTransitionState(false).apply { targetState = true } }
    var left by remember(inv.code) { mutableStateOf(INVITE_MS) }
    val expire by rememberUpdatedState(onExpire)
    LaunchedEffect(inv.code) {
        val start = System.currentTimeMillis()
        while (left > 0L) {
            delay(40L)
            left = (INVITE_MS - (System.currentTimeMillis() - start)).coerceAtLeast(0L)
        }
        state.targetState = false
        delay(380L)
        expire()
    }
    val provider = remember(topPx, marginPx) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset =
                IntOffset(windowSize.width - popupContentSize.width - marginPx, topPx)
        }
    }
    val name = inv.display.ifBlank { inv.host }
    val shape = RoundedCornerShape(22.dp)
    val accent = Color(0xFF5AA9FF)
    Popup(popupPositionProvider = provider, properties = PopupProperties(focusable = false, clippingEnabled = false)) {
        AnimatedVisibility(
            visibleState = state,
            enter = slideInHorizontally(initialOffsetX = { it + 80 }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it + 80 }) + fadeOut()
        ) {
            Column(
                Modifier.widthIn(max = 330.dp).fillMaxWidth()
                    .shadow(20.dp, shape, ambientColor = accent, spotColor = accent)
                    .clip(shape)
                    .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.20f).compositeOver(colors.card), colors.card, colors.card)))
                    .border(1.dp, accent.copy(alpha = 0.5f), shape)
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        ChatAvatar("", "", name, 46.dp)
                        Box(Modifier.size(18.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                            Icon(NexIcons.Group, null, tint = Color.White, modifier = Modifier.size(11.dp))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(S.mubarInviteBadge.uppercase(), fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, color = accent, letterSpacing = 1.sp)
                        Text(S.mubarInviteFrom(name), fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(8.dp))
                    // Cincin hitung mundur dengan ikon waktu dan detik sisa.
                    val frac = left / INVITE_MS.toFloat()
                    Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val sw = 3.dp.toPx()
                            drawArc(colors.border, 0f, 360f, false, topLeft = Offset(sw / 2, sw / 2), size = Size(size.width - sw, size.height - sw), style = Stroke(sw))
                            drawArc(accent, -90f, 360f * frac, false, topLeft = Offset(sw / 2, sw / 2), size = Size(size.width - sw, size.height - sw), style = Stroke(sw, cap = StrokeCap.Round))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(NexIcons.Timer, null, tint = accent, modifier = Modifier.size(13.dp))
                            Text(((left + 999L) / 1000L).toString(), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary, lineHeight = 11.sp)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(14.dp)).background(colors.background.copy(alpha = 0.5f))
                            .border(1.dp, colors.border, RoundedCornerShape(14.dp)).clickable(enabled = !busy, onClick = onDecline),
                        contentAlignment = Alignment.Center
                    ) { Text(S.mubarDecline, color = colors.textSecondary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
                    Box(
                        Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(14.dp)).background(accent).clickable(enabled = !busy, onClick = onAccept),
                        contentAlignment = Alignment.Center
                    ) {
                        if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        else Text(S.mubarJoin, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

/** Dialog konfirmasi keluar / bubarkan ruangan. */
@Composable
private fun MubarConfirmDialog(isHost: Boolean, members: Int, maxMembers: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = NexrifyAppTheme.colors
    val shape = RoundedCornerShape(28.dp)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(shape).background(colors.surface).border(1.dp, DANGER.copy(alpha = 0.35f), shape)
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(DANGER.copy(alpha = 0.14f)).border(1.5.dp, DANGER.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(if (isHost) NexIcons.Group else NexIcons.Logout, null, tint = DANGER, modifier = Modifier.size(28.dp)) }
            Spacer(Modifier.height(16.dp))
            Text(if (isHost) S.mubarDisband else S.mubarLeaveConfirm, style = Typography.titleLarge, fontWeight = FontWeight.Bold, color = colors.textPrimary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(if (isHost) S.mubarDisbandConfirm else S.mubarLeaveBody, style = Typography.bodyMedium, color = colors.textMuted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).background(colors.card).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(NexIcons.Group, null, tint = colors.textSecondary, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(S.mubarMembersCount(members, maxMembers), fontSize = 12.sp, color = colors.textSecondary, fontWeight = FontWeight.SemiBold)
                if (isHost) { Spacer(Modifier.width(8.dp)); Text("·  " + S.mubarDisbandSub, fontSize = 12.sp, color = colors.textMuted) }
            }
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(16.dp)).background(colors.card).border(1.dp, colors.border, RoundedCornerShape(16.dp)).clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) { Text(if (isHost) S.mubarCancel else S.mubarStay, color = colors.textSecondary, fontWeight = FontWeight.SemiBold) }
                Box(
                    Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(16.dp)).background(DANGER).clickable(onClick = onConfirm),
                    contentAlignment = Alignment.Center
                ) { Text(if (isHost) S.mubarDisband else S.mubarLeave, color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/* ============================================================
 * Pilihan di tombol Bagikan player
 * ============================================================ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareChoiceSheet(onShareLink: () -> Unit, onTogether: () -> Unit, onDismiss: () -> Unit) {
    val colors = NexrifyAppTheme.colors
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.background, dragHandle = { MubarHandle() }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text(S.mubarShareTitle, style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
            Spacer(Modifier.height(12.dp))
            ChoiceRow(NexIcons.Group, S.mubarShareTogether, S.mubarShareTogetherSub, onTogether)
            Spacer(Modifier.height(8.dp))
            ChoiceRow(NexIcons.Share, S.mubarShareLink, S.mubarShareLinkSub, onShareLink)
        }
    }
}

@Composable
private fun ChoiceRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, onClick: () -> Unit) {
    val colors = NexrifyAppTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(colors.card).border(1.dp, colors.border, shape).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(colors.textPrimary.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = colors.textPrimary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            Text(sub, fontSize = 12.sp, color = colors.textMuted, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun MubarHandle() {
    val colors = NexrifyAppTheme.colors
    Box(Modifier.padding(vertical = 10.dp).width(36.dp).height(4.dp).clip(CircleShape).background(colors.border))
}

/* ============================================================
 * Lembar ruangan
 * ============================================================ */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MubarSheet(viewModel: MusicViewModel, onDismiss: () -> Unit) {
    val colors = NexrifyAppTheme.colors
    val mgr = remember { MubarManager.getInstance() }
    val room by mgr.room.collectAsState()
    val messages by mgr.messages.collectAsState()
    val me by ChatManager.getInstance().me.collectAsState()
    val myUid = me?.uid ?: AccountManager.getInstance().user.value?.id.orEmpty()
    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var showInvite by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var profileUser by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<MubarMsg?>(null) }
    var menuMsg by remember { mutableStateOf<MubarMsg?>(null) }
    var deleteMsg by remember { mutableStateOf<MubarMsg?>(null) }
    var highlight by remember { mutableStateOf(0L) }
    val clipboard = LocalClipboardManager.current
    val chatMgr = remember { ChatManager.getInstance() }
    val profiles = remember { mutableStateMapOf<String, ChatProfile>() }
    val nameKey = ((room?.members?.map { it.username } ?: emptyList()) + messages.map { it.username }).filter { it.isNotBlank() }.distinct().sorted().joinToString(",")
    // Profil (lencana, title, role) tiap anggota diambil sekali dan dipakai ulang oleh slot dan gelembung chat.
    LaunchedEffect(nameKey) {
        nameKey.split(",").filter { it.isNotBlank() && it !in profiles }.forEach { u ->
            runCatching { chatMgr.profile(u) }.getOrNull()?.let { profiles[u] = it }
        }
    }
    fun badgesOf(m: MubarMsg): List<String> =
        profiles[m.username]?.badges?.takeIf { it.isNotEmpty() }
            ?: room?.members?.firstOrNull { it.uid == m.uid }?.role?.takeIf { it.isNotBlank() }?.let { listOf(it) }.orEmpty()
    val nowMs by produceState(initialValue = mgr.serverNowMs()) { while (true) { value = mgr.serverNowMs(); delay(500L) } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { MubarHandle() }
    ) {
        val r = room
        if (r == null) {
            Lobby(busy = busy, onCreate = {
                scope.launch {
                    busy = true
                    try { mgr.create(viewModel.currentTrack.value) } catch (e: CancellationException) { throw e } catch (_: Throwable) {
                        NexNotifier.error(currentStrings().mubarCreateFail, currentStrings().mubarTitle)
                    }
                    busy = false
                }
            })
            return@ModalBottomSheet
        }

        val isHost = r.hostUid == myUid
        val myTurn = r.turnUid == myUid
        val turnMember = r.members.firstOrNull { it.uid == r.turnUid }
        val nextMember = r.members.getOrNull((r.members.indexOfFirst { it.uid == r.turnUid } + 1) % r.members.size.coerceAtLeast(1))
        val track = r.track
        val elapsed = if (track != null) (nowMs - r.startedAt).coerceAtLeast(0L) else 0L
        val canChange = myTurn && (track == null || elapsed < r.lockMs)
        val secLeft = if (track != null) ((r.lockMs - elapsed) / 1000L).toInt().coerceAtLeast(0) else 0

        Column(Modifier.fillMaxWidth().fillMaxHeight(0.95f).imePadding().padding(horizontal = 16.dp)) {
            // Kepala
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(S.mubarTitle, style = Typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
                    Text("${S.mubarRoomCode} ${r.code}", fontSize = 11.sp, color = colors.textMuted)
                }
                Text(
                    if (isHost) S.mubarDisband else S.mubarLeave,
                    color = Color(0xFFE5484D), fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { confirmLeave = true }.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
            Spacer(Modifier.height(10.dp))

            // Anggota (urutan = urutan giliran)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (i in 0 until r.maxMembers) {
                    val m = r.members.getOrNull(i)
                    if (m != null) MemberSlot(Modifier.weight(1f), i + 1, m, profiles[m.username], isTurn = m.uid == r.turnUid, isHost = m.uid == r.hostUid, isMe = m.uid == myUid) { profileUser = m.username }
                    else EmptySlot(Modifier.weight(1f), i + 1, canInvite = isHost) { if (isHost) showInvite = true }
                }
            }
            Spacer(Modifier.height(12.dp))

            // Sedang diputar
            val cardShape = RoundedCornerShape(18.dp)
            Column(Modifier.fillMaxWidth().clip(cardShape).background(colors.card).border(1.dp, colors.border, cardShape).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)).background(colors.background), contentAlignment = Alignment.Center) {
                        if (track != null && track.thumb.isNotBlank()) AsyncImage(track.thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else Icon(NexIcons.Headphones, null, tint = colors.textMuted, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(S.mubarNowPlaying, fontSize = 10.5.sp, color = colors.textMuted)
                        Text(track?.title ?: S.mubarNothingYet, fontWeight = FontWeight.Bold, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (track != null) Text(track.artist, fontSize = 12.sp, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (track != null) {
                    Spacer(Modifier.height(10.dp))
                    val frac = (elapsed.toFloat() / (track.durationSec * 1000f)).coerceIn(0f, 1f)
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(colors.border)) {
                        Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(colors.textPrimary))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                myTurn && track == null -> S.mubarYourTurn
                                myTurn && canChange -> S.mubarLockedIn(secLeft)
                                myTurn -> S.mubarLocked
                                track != null && elapsed >= r.lockMs -> S.mubarLocked
                                else -> S.mubarTurnOf(turnMember?.username.orEmpty())
                            },
                            fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = colors.textSecondary
                        )
                        if (nextMember != null && r.members.size > 1 && nextMember.uid != r.turnUid) {
                            Text(S.mubarNextUp(nextMember.username), fontSize = 11.sp, color = colors.textMuted)
                        }
                    }
                    if (canChange) {
                        Box(
                            Modifier.height(38.dp).clip(RoundedCornerShape(19.dp)).background(colors.textPrimary).clickable { showPicker = true }.padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(if (track == null) S.mubarPickSong else S.mubarChangeSong(secLeft), color = colors.background, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            // Chat grup
            val listState = rememberLazyListState()
            LaunchedEffect(messages.lastOrNull()?.seq) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (messages.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(S.mubarChatEmpty, color = colors.textMuted, fontSize = 13.sp) }
                } else {
                    LazyColumn(Modifier.fillMaxSize(), state = listState) {
                        itemsIndexed(messages, key = { _, it -> it.seq }) { i, m ->
                            val prev = messages.getOrNull(i - 1)
                            val showHeader = prev == null || prev.uid != m.uid || m.ts - prev.ts > 300_000L
                            ChatBubble(
                                msg = m.toChat(badgesOf(m)), mine = m.uid == myUid, myId = myUid, myUsername = me?.username.orEmpty(),
                                showHeader = showHeader, highlighted = highlight == m.seq,
                                onLongPress = { menuMsg = m },
                                onReplyClick = { seq ->
                                    val idx = messages.indexOfFirst { it.seq == seq }
                                    if (idx >= 0) scope.launch { listState.animateScrollToItem(idx); highlight = seq; delay(1300L); highlight = 0L }
                                },
                                onAvatarClick = { profileUser = m.username },
                                onReactionClick = { e -> scope.launch { runCatching { mgr.react(m.seq, e) } } },
                                onRetry = {},
                                onSwipeReply = { replyTo = m },
                                replyFullText = m.reply?.let { rp -> messages.firstOrNull { it.seq == rp.seq }?.text },
                                replyDeleted = m.reply?.let { rp -> messages.firstOrNull { it.seq == rp.seq }?.deleted == true } ?: false
                            )
                        }
                    }
                }
            }

            replyTo?.let { rt ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(14.dp)).background(colors.card)
                        .border(1.dp, colors.border, RoundedCornerShape(14.dp)).padding(end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.width(4.dp).height(44.dp).background(Color(0xFF5AA9FF)))
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Text("${S.mubarReplyingTo} ${rt.display.ifBlank { rt.username }}", fontSize = 11.5.sp, color = Color(0xFF5AA9FF), fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(if (rt.deleted) S.mubarMessageDeleted else rt.text, fontSize = 12.sp, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(NexIcons.Close, null, tint = colors.textMuted, modifier = Modifier.size(18.dp).clickable { replyTo = null })
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(colors.card).border(1.dp, colors.border, RoundedCornerShape(22.dp)).padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = input, onValueChange = { input = it.take(300) },
                        textStyle = TextStyle(color = colors.textPrimary, fontSize = 15.sp),
                        cursorBrush = SolidColor(colors.textPrimary), maxLines = 4,
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            Box { if (input.isEmpty()) Text(S.mubarChatHint, color = colors.textMuted.copy(alpha = 0.7f), fontSize = 14.sp); inner() }
                        }
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(if (input.isBlank()) colors.card else colors.textPrimary)
                        .clickable(enabled = input.isNotBlank()) {
                            val text = input.trim()
                            val rt = replyTo?.seq
                            input = ""; replyTo = null
                            scope.launch {
                                try { mgr.send(text, rt) } catch (e: CancellationException) { throw e } catch (_: Throwable) {
                                    NexNotifier.error(currentStrings().mubarSendFail, currentStrings().mubarTitle)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) { Icon(NexIcons.Send, null, tint = if (input.isBlank()) colors.textMuted else colors.background, modifier = Modifier.size(20.dp)) }
            }
        }

        if (showPicker) MubarPickerSheet(
            viewModel = viewModel, onDismiss = { showPicker = false },
            onPick = { t ->
                showPicker = false
                scope.launch {
                    if (t.durationSeconds < 20) { NexNotifier.warning(currentStrings().mubarPickNoDuration, currentStrings().mubarTitle); return@launch }
                    try { mgr.pick(t) } catch (e: CancellationException) { throw e } catch (e: AccountError) {
                        val st = currentStrings()
                        NexNotifier.warning(
                            when (e.code) { "locked" -> st.mubarPickLocked; "not_your_turn" -> st.mubarPickNotTurn; else -> st.mubarPickFail },
                            st.mubarTitle
                        )
                    } catch (_: Throwable) { NexNotifier.error(currentStrings().mubarPickFail, currentStrings().mubarTitle) }
                }
            }
        )
        if (showInvite) MubarInviteSheet(memberNames = r.members.map { it.username }.toSet(), onDismiss = { showInvite = false })
        menuMsg?.let { mm ->
            ChatMessageMenu(
                msg = mm.toChat(badgesOf(mm)), mine = mm.uid == myUid, canEdit = false, isMuted = false, myId = myUid,
                onReact = { e -> menuMsg = null; scope.launch { runCatching { mgr.react(mm.seq, e) } } },
                onReply = { menuMsg = null; replyTo = mm },
                onCopy = { menuMsg = null; clipboard.setText(AnnotatedString(mm.text)); NexNotifier.success(currentStrings().chatCopied) },
                onEdit = {}, onReport = {}, onToggleMute = {},
                onDelete = { menuMsg = null; deleteMsg = mm },
                onProfile = { menuMsg = null; profileUser = mm.username },
                moderation = false, canDeleteOthers = isHost,
                onDismiss = { menuMsg = null }
            )
        }
        deleteMsg?.let { dm ->
            ChatDeleteDialog(
                onConfirm = { deleteMsg = null; scope.launch { runCatching { mgr.delete(dm.seq) } } },
                onDismiss = { deleteMsg = null }, preview = dm.text
            )
        }
        profileUser?.let { u ->
            ChatProfileSheet(
                username = u, isMe = u == me?.username, isMuted = false, onMention = {}, onToggleMute = {},
                onDismiss = { profileUser = null }, showActions = false
            )
        }
        if (confirmLeave) {
            MubarConfirmDialog(
                isHost = isHost, members = r.members.size, maxMembers = r.maxMembers,
                onConfirm = { confirmLeave = false; scope.launch { runCatching { mgr.leave() } } },
                onDismiss = { confirmLeave = false }
            )
        }
    }
}

@Composable
private fun Lobby(busy: Boolean, onCreate: () -> Unit) {
    val colors = NexrifyAppTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(colors.textPrimary.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
            Icon(NexIcons.Group, null, tint = colors.textPrimary, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(S.mubarLobbyTitle, style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(S.mubarLobbySub, fontSize = 13.sp, color = colors.textMuted, lineHeight = 19.sp)
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(colors.textPrimary).clickable(enabled = !busy, onClick = onCreate),
            contentAlignment = Alignment.Center
        ) {
            if (busy) CircularProgressIndicator(color = colors.background, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            else Text(S.mubarCreate, color = colors.background, fontWeight = FontWeight.Bold)
        }
    }
}

private fun MubarMsg.toChat(badges: List<String>) = ChatMessage(
    seq = seq, uid = uid, username = username, display = display.ifBlank { username }, badges = badges, av = av, text = text, ts = ts,
    edited = 0L, deleted = deleted, announce = false, mentions = emptyList(),
    reply = reply?.let { ChatReply(it.seq, it.username, it.text, it.deleted) }, reactions = reactions
)

@Composable
private fun MemberSlot(modifier: Modifier, order: Int, m: MubarMember, profile: ChatProfile?, isTurn: Boolean, isHost: Boolean, isMe: Boolean, onClick: () -> Unit) {
    val colors = NexrifyAppTheme.colors
    val name = m.display.ifBlank { m.username }
    val badges = (profile?.badges ?: emptyList()).filter { it != "early" && it != "active" && it != "loved" }
        .ifEmpty { m.role.takeIf { it.isNotBlank() }?.let { listOf(it) }.orEmpty() }
    Column(modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                Modifier.size(60.dp).clip(CircleShape).border(if (isTurn) 3.dp else 1.dp, if (isTurn) Color(0xFF5AA9FF) else colors.border, CircleShape).padding(3.dp),
                contentAlignment = Alignment.Center
            ) { ChatAvatar(m.uid, m.av, name, 52.dp) }
            if (isHost) Box(Modifier.size(20.dp).clip(CircleShape).background(Color(0xFFF7B733)), contentAlignment = Alignment.Center) {
                Icon(NexIcons.Crown, null, tint = Color(0xFF3A2500), modifier = Modifier.size(12.dp))
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(name, fontSize = 11.5.sp, color = if (isTurn) colors.textPrimary else colors.textSecondary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("@${m.username}", fontSize = 9.5.sp, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(3.dp))
        // Role / lencana, lalu title yang sedang dipasang.
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            badges.take(1).forEach { BadgeChip(it) }
            if (isMe && badges.isEmpty()) Text(S.mubarYouTag, fontSize = 9.sp, color = colors.textMuted)
        }
        profile?.titles?.firstOrNull()?.let { Spacer(Modifier.height(3.dp)); TitleChip(it) }
    }
}

@Composable
private fun EmptySlot(modifier: Modifier, order: Int, canInvite: Boolean, onClick: () -> Unit) {
    val colors = NexrifyAppTheme.colors
    Column(modifier.clip(RoundedCornerShape(14.dp)).clickable(enabled = canInvite, onClick = onClick).padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(60.dp).clip(CircleShape).border(1.dp, colors.border, CircleShape), contentAlignment = Alignment.Center) {
            if (canInvite) Icon(NexIcons.Add, null, tint = colors.textMuted, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(5.dp))
        Text("$order. ${if (canInvite) S.mubarInviteSlot else S.mubarEmptySlot}", fontSize = 11.sp, color = colors.textMuted, maxLines = 1)
    }
}

/* ---------------- pemilih lagu ---------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MubarPickerSheet(viewModel: MusicViewModel, onDismiss: () -> Unit, onPick: (Track) -> Unit) {
    val colors = NexrifyAppTheme.colors
    val results by viewModel.searchResults.collectAsState()
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { MubarHandle() }
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).imePadding().padding(horizontal = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.card).border(1.dp, colors.border, RoundedCornerShape(22.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(NexIcons.Search, null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = query, onValueChange = { query = it.take(100) }, singleLine = true,
                    textStyle = TextStyle(color = colors.textPrimary, fontSize = 15.sp), cursorBrush = SolidColor(colors.textPrimary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) viewModel.search(query.trim()) }),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner -> Box { if (query.isEmpty()) Text(S.mubarSearchHint, color = colors.textMuted.copy(alpha = 0.7f), fontSize = 14.sp); inner() } }
                )
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.fillMaxSize()) {
                items(results, key = { it.id }) { t ->
                    TrackItem(track = t, isPlaying = false, onClick = { onPick(t) }, onFavoriteClick = {})
                }
            }
        }
    }
}

/* ---------------- undang teman ---------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MubarInviteSheet(memberNames: Set<String>, onDismiss: () -> Unit) {
    val colors = NexrifyAppTheme.colors
    val mgr = remember { MubarManager.getInstance() }
    val chat = remember { ChatManager.getInstance() }
    val scope = rememberCoroutineScope()
    val myName = chat.me.collectAsState().value?.username.orEmpty()
    val friends = remember { mutableStateListOf<FollowUser>() }
    var loading by remember { mutableStateOf(true) }
    val invited = remember { mutableStateListOf<String>() }
    var busyName by remember { mutableStateOf("") }

    LaunchedEffect(myName) {
        if (myName.isEmpty()) { loading = false; return@LaunchedEffect }
        var cursor = 0
        var guard = 0
        while (guard++ < 4) {
            val page = chat.followPage(myName, "friends", cursor) ?: break
            friends.addAll(page.items.filter { f -> friends.none { it.uid == f.uid } })
            if (page.next == 0) break
            cursor = page.next
        }
        loading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss, containerColor = colors.background,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), dragHandle = { MubarHandle() }
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(horizontal = 20.dp)) {
            Text(S.mubarInviteTitle, style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
            Text(S.mubarInviteSub, fontSize = 12.sp, color = colors.textMuted)
            Spacer(Modifier.height(10.dp))
            when {
                loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.textPrimary, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                }
                friends.isEmpty() -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(S.mubarNoFriends, color = colors.textMuted, fontSize = 13.sp)
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(friends, key = { it.uid }) { f ->
                        val inRoom = f.username in memberNames
                        val sent = f.username in invited
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            ChatAvatar(f.uid, f.av, f.display, 44.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.display, fontWeight = FontWeight.Bold, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("@${f.username}", fontSize = 12.sp, color = colors.textMuted, maxLines = 1)
                            }
                            val enabled = !inRoom && !sent && busyName.isEmpty()
                            Box(
                                Modifier.height(34.dp).clip(RoundedCornerShape(17.dp))
                                    .background(if (enabled) colors.textPrimary else Color.Transparent)
                                    .border(1.dp, if (enabled) colors.textPrimary else colors.border, RoundedCornerShape(17.dp))
                                    .clickable(enabled = enabled) {
                                        scope.launch {
                                            busyName = f.username
                                            try {
                                                mgr.invite(f.username)
                                                invited.add(f.username)
                                                NexNotifier.success(currentStrings().mubarInviteSent, currentStrings().mubarTitle)
                                            } catch (e: CancellationException) { throw e } catch (e: AccountError) {
                                                val st = currentStrings()
                                                NexNotifier.warning(
                                                    when (e.code) { "full" -> st.mubarInviteFull; "busy" -> st.mubarInviteBusy; else -> st.mubarInviteFail },
                                                    st.mubarTitle
                                                )
                                            } catch (_: Throwable) { NexNotifier.error(currentStrings().mubarInviteFail, currentStrings().mubarTitle) }
                                            busyName = ""
                                        }
                                    }
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    when { inRoom -> S.mubarInRoom; sent -> S.mubarInvited; else -> S.mubarInviteSlot },
                                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (enabled) colors.background else colors.textMuted
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

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

    // Undangan masuk (hanya saat belum di ruangan).
    val inv = invites.firstOrNull()
    if (room == null && inv != null) {
        val colors = NexrifyAppTheme.colors
        AlertDialog(
            onDismissRequest = { scope.launch { runCatching { mgr.decline(inv) } } },
            containerColor = colors.card,
            title = { Text(S.mubarInviteFrom(inv.display.ifBlank { inv.host }), color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = { Text(S.mubarInviteBody, color = colors.textMuted) },
            confirmButton = {
                TextButton(enabled = !joining, onClick = {
                    scope.launch {
                        joining = true
                        try { mgr.accept(inv) } catch (e: CancellationException) { throw e } catch (_: Throwable) {
                            NexNotifier.error(currentStrings().mubarJoinFail, currentStrings().mubarTitle)
                            runCatching { mgr.refresh() }
                        }
                        joining = false
                    }
                }) { Text(S.mubarJoin, color = colors.textPrimary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { scope.launch { runCatching { mgr.decline(inv) } } }) { Text(S.mubarDecline, color = colors.textMuted) }
            }
        )
    }

    if (open) MubarSheet(viewModel = viewModel, onDismiss = { mgr.closeSheet() })
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
                    if (m != null) MemberSlot(i + 1, m, isTurn = m.uid == r.turnUid, isHost = m.uid == r.hostUid) { profileUser = m.username }
                    else EmptySlot(i + 1, canInvite = isHost) { if (isHost) showInvite = true }
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
                    LazyColumn(Modifier.fillMaxSize(), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(messages, key = { it.seq }) { m ->
                            MubarBubble(
                                msg = m, mine = m.uid == myUid, myUid = myUid, canDelete = m.uid == myUid || isHost,
                                onAvatar = { profileUser = m.username },
                                onReact = { e -> scope.launch { runCatching { mgr.react(m.seq, e) } } },
                                onReply = { replyTo = m },
                                onDelete = { scope.launch { runCatching { mgr.delete(m.seq) } } }
                            )
                        }
                    }
                }
            }

            replyTo?.let { rt ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(12.dp)).background(colors.card).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${S.mubarReplyingTo} @${rt.username}", fontSize = 11.sp, color = colors.textMuted, fontWeight = FontWeight.SemiBold)
                        Text(rt.text.ifEmpty { S.mubarMessageDeleted }, fontSize = 12.sp, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        profileUser?.let { u ->
            ChatProfileSheet(
                username = u, isMe = u == me?.username, isMuted = false, onMention = {}, onToggleMute = {},
                onDismiss = { profileUser = null }, showActions = false
            )
        }
        if (confirmLeave) {
            AlertDialog(
                onDismissRequest = { confirmLeave = false },
                containerColor = colors.card,
                title = { Text(if (isHost) S.mubarDisband else S.mubarLeaveConfirm, color = colors.textPrimary, fontWeight = FontWeight.Bold) },
                text = { if (isHost) Text(S.mubarDisbandConfirm, color = colors.textMuted) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmLeave = false
                        scope.launch { runCatching { mgr.leave() } }
                    }) { Text(if (isHost) S.mubarDisband else S.mubarLeave, color = Color(0xFFE5484D), fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(S.mubarCancel, color = colors.textMuted) } }
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

@Composable
private fun MemberSlot(order: Int, m: MubarMember, isTurn: Boolean, isHost: Boolean, onClick: () -> Unit) {
    val colors = NexrifyAppTheme.colors
    Column(Modifier.width(72.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                Modifier.size(58.dp).clip(CircleShape).border(if (isTurn) 3.dp else 1.dp, if (isTurn) colors.textPrimary else colors.border, CircleShape).padding(3.dp),
                contentAlignment = Alignment.Center
            ) { ChatAvatar(m.uid, m.av, m.display, 50.dp) }
            if (isHost) Box(Modifier.size(20.dp).clip(CircleShape).background(Color(0xFFF7B733)), contentAlignment = Alignment.Center) {
                Icon(NexIcons.Crown, null, tint = Color(0xFF3A2500), modifier = Modifier.size(12.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("$order. ${m.display.ifBlank { m.username }}", fontSize = 11.sp, color = if (isTurn) colors.textPrimary else colors.textSecondary, fontWeight = if (isTurn) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EmptySlot(order: Int, canInvite: Boolean, onClick: () -> Unit) {
    val colors = NexrifyAppTheme.colors
    Column(Modifier.width(72.dp).clickable(enabled = canInvite, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(58.dp).clip(CircleShape).border(1.dp, colors.border, CircleShape), contentAlignment = Alignment.Center) {
            if (canInvite) Icon(NexIcons.Add, null, tint = colors.textMuted, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text("$order. ${if (canInvite) S.mubarInviteSlot else S.mubarEmptySlot}", fontSize = 11.sp, color = colors.textMuted, maxLines = 1)
    }
}

/* ---------------- gelembung chat grup ---------------- */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MubarBubble(
    msg: MubarMsg, mine: Boolean, myUid: String, canDelete: Boolean,
    onAvatar: () -> Unit, onReact: (String) -> Unit, onReply: () -> Unit, onDelete: () -> Unit
) {
    val colors = NexrifyAppTheme.colors
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Bottom) {
        if (!mine) {
            ChatAvatar(msg.uid, msg.av, msg.display, 30.dp, Modifier.clickable(onClick = onAvatar))
            Spacer(Modifier.width(8.dp))
        }
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start, modifier = Modifier.weight(1f, fill = false)) {
            if (!mine) Text(msg.display.ifBlank { msg.username }, fontSize = 11.sp, color = colors.textMuted, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
            Box {
                Column(
                    Modifier.clip(shape).background(if (mine) colors.textPrimary else colors.card)
                        .then(if (mine) Modifier else Modifier.border(1.dp, colors.border, shape))
                        .combinedClickable(enabled = !msg.deleted, onClick = {}, onLongClick = { menu = true })
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    val ink = if (mine) colors.background else colors.textPrimary
                    msg.reply?.let { r ->
                        Column(Modifier.padding(bottom = 6.dp).clip(RoundedCornerShape(8.dp)).background(ink.copy(alpha = 0.1f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Text("@${r.username}", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = ink.copy(alpha = 0.8f))
                            Text(
                                if (r.deleted || r.text.isEmpty()) S.mubarMessageDeleted else r.text,
                                fontSize = 12.sp, color = ink.copy(alpha = 0.8f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                                fontStyle = if (r.deleted || r.text.isEmpty()) FontStyle.Italic else FontStyle.Normal
                            )
                        }
                    }
                    if (msg.deleted) Text(S.mubarMessageDeleted, fontSize = 13.sp, fontStyle = FontStyle.Italic, color = ink.copy(alpha = 0.6f))
                    else Text(msg.text, fontSize = 14.5.sp, color = ink, lineHeight = 20.sp)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        CHAT_EMOJIS_QUICK.forEach { e ->
                            Text(e, fontSize = 22.sp, modifier = Modifier.clip(CircleShape).clickable { menu = false; onReact(e) }.padding(6.dp))
                        }
                    }
                    DropdownMenuItem(text = { Text(S.mubarReply) }, leadingIcon = { Icon(NexIcons.Reply, null, modifier = Modifier.size(18.dp)) }, onClick = { menu = false; onReply() })
                    if (canDelete) DropdownMenuItem(
                        text = { Text(S.mubarDelete, color = Color(0xFFE5484D)) },
                        leadingIcon = { Icon(NexIcons.DeleteOutline, null, tint = Color(0xFFE5484D), modifier = Modifier.size(18.dp)) },
                        onClick = { menu = false; onDelete() }
                    )
                }
            }
            if (msg.reactions.isNotEmpty() && !msg.deleted) {
                Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    msg.reactions.forEach { (e, who) ->
                        val on = myUid in who
                        Row(
                            Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) colors.textPrimary.copy(alpha = 0.12f) else colors.card)
                                .border(1.dp, if (on) colors.textPrimary.copy(alpha = 0.5f) else colors.border, RoundedCornerShape(10.dp))
                                .clickable { onReact(e) }.padding(horizontal = 7.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(e, fontSize = 12.sp)
                            Spacer(Modifier.width(3.dp))
                            Text(who.size.toString(), fontSize = 11.sp, color = colors.textSecondary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
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

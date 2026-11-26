package id.nexrify.music.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.nexrify.music.data.chat.DmImage
import id.nexrify.music.util.DmImage as DmImageUtil
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import id.nexrify.music.ui.components.VoiceBubble
import id.nexrify.music.ui.components.VoiceRecordBar
import id.nexrify.music.util.VoicePlayer
import id.nexrify.music.util.VoiceRecorder
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import id.nexrify.music.data.account.AccountManager
import id.nexrify.music.data.chat.CHAT_EMOJIS_QUICK
import id.nexrify.music.data.chat.CHAT_MAX_LENGTH
import id.nexrify.music.data.chat.ChatUserHit
import id.nexrify.music.data.chat.DmConversation
import id.nexrify.music.data.chat.DmManager
import id.nexrify.music.data.chat.DmMessage
import id.nexrify.music.data.chat.DmPeer
import id.nexrify.music.data.chat.DmSong
import id.nexrify.music.data.chat.ChatManager
import id.nexrify.music.data.chat.SendStatus
import id.nexrify.music.data.model.Track
import androidx.compose.ui.text.input.ImeAction
import id.nexrify.music.i18n.S
import id.nexrify.music.i18n.currentStrings
import id.nexrify.music.ui.components.ChatAvatar
import id.nexrify.music.ui.components.ChatDateHeader
import id.nexrify.music.ui.components.NexNotifier
import android.view.WindowManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import id.nexrify.music.data.account.AccountError
import id.nexrify.music.data.chat.LinkPreview
import id.nexrify.music.data.chat.PeerExtra
import id.nexrify.music.data.mubar.MubarManager
import id.nexrify.music.data.playlist.PlaylistShareNav
import id.nexrify.music.data.call.CallManager
import id.nexrify.music.data.call.CallPeer
import id.nexrify.music.data.call.CallPhase
import id.nexrify.music.ui.components.rememberMicPermission
import id.nexrify.music.ui.components.TranslationBlock
import id.nexrify.music.ui.components.chatAgo
import id.nexrify.music.ui.components.chatDayKey
import id.nexrify.music.ui.components.chatDayLabel
import id.nexrify.music.ui.components.chatTime
import id.nexrify.music.ui.icons.NexIcons
import id.nexrify.music.ui.theme.NexrifyAppTheme
import id.nexrify.music.ui.theme.Typography
import id.nexrify.music.viewmodel.MusicViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val EDIT_WINDOW = 3_600_000L
private val RED = Color(0xFFE5484D)

/** Warna gelembung milikku. Indeks 0 = bawaan tema. */
private val BUBBLE_THEMES = listOf(null, Color(0xFF2563EB), Color(0xFF16A34A), Color(0xFF9333EA), Color(0xFFE11D48), Color(0xFFEA580C))

/** Draf yang belum terkirim, tersimpan selama aplikasi berjalan (uid lawan bicara -> teks). */
private val dmDrafts = HashMap<String, String>()

/** Ringkasan satu baris sebuah pesan untuk kutipan, hasil cari, dan daftar bintang. */
private fun dmPreview(m: DmMessage): String {
    val s = currentStrings()
    return when {
        m.onceText -> "🔒 ${s.dmTextOnce}"
        m.voice != null -> "🎤 ${s.dmVoiceLabel}"
        m.call != null -> "📞 ${s.callTitle}"
        m.image != null -> "📷 " + m.text.ifBlank { s.dmPhoto }
        m.song != null -> m.song.title
        else -> m.text
    }
}

/** Nama yang ditampilkan; [ver] hanya memicu susun ulang saat nama panggilan berubah. */
private fun dmName(display: String, username: String, @Suppress("UNUSED_PARAMETER") ver: Int): String = display.ifBlank { username }

private fun dmErrText(code: String): String {
    val s = currentStrings()
    return when (code) {
        "dm_blocked" -> s.dmErrBlocked
        "dm_you_blocked" -> s.dmErrYouBlocked
        "chat_banned" -> s.dmBannedErr
        "rate_limited" -> s.dmSlowDown
        "network" -> s.dmNoNetworkErr
        "pin_limit" -> s.dmPinLimit
        "star_limit" -> s.dmStarLimit
        "dm_pending" -> s.dmErrPending
        "dm_declined" -> s.dmErrDeclined
        "vn_short" -> s.voiceTooShort
        "dm_img_request" -> s.dmErrImgRequest
        "translate_failed" -> s.dmErrTranslate
        "viewed" -> s.dmErrViewed
        "img_invalid" -> s.dmPhotoFail
        "vn_gone" -> s.voiceGone
        else -> s.dmGenericErr
    }
}

@Composable
private fun TopIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val colors = NexrifyAppTheme.colors
    Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = colors.textPrimary, modifier = Modifier.size(22.dp))
    }
}

/* =============================== KOTAK MASUK =============================== */

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun DmInboxContent(onBack: () -> Unit, onOpenChat: (String) -> Unit) {
    val colors = NexrifyAppTheme.colors
    val dms = remember { DmManager.getInstance() }
    val inbox by dms.inbox.collectAsState()
    val loaded by dms.inboxLoaded.collectAsState()
    val scope = rememberCoroutineScope()
    var showNew by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<DmConversation?>(null) }
    var confirmHide by remember { mutableStateOf<DmConversation?>(null) }
    var showArchive by remember { mutableStateOf(false) }
    var showRequests by remember { mutableStateOf(false) }
    var showLockSheet by remember { mutableStateOf(false) }
    var showStatus by remember { mutableStateOf(false) }
    var showLocked by remember { mutableStateOf(false) }
    var askFolderPin by remember { mutableStateOf(false) }
    var folderPinError by remember { mutableStateOf<String?>(null) }
    // 0 = tidak ada, 1 = buat PIN baru, 2 = ulangi PIN baru, 3 = verifikasi untuk mematikan, 4 = verifikasi untuk mengubah
    var lockFlow by remember { mutableStateOf(0) }
    var lockFirst by remember { mutableStateOf("") }
    var lockError by remember { mutableStateOf<String?>(null) }
    val localVer by dms.localVersion.collectAsState()
    val myName = AccountManager.getInstance().user.value?.username.orEmpty()
    val requestCount = inbox.count { it.request }
    val archivedCount = inbox.count { it.archived && !it.request && !dms.isChatLocked(it.uid) }
    val lockedCount = inbox.count { !it.request && dms.isChatLocked(it.uid) }
    val shown = inbox.filter {
        val locked = !it.request && dms.isChatLocked(it.uid)
        when {
            showRequests -> it.request
            showLocked -> locked
            showArchive -> it.archived && !it.request && !locked
            else -> !it.archived && !it.request && !locked
        }
    }
    val hasNote = inbox.any { it.note }
    LaunchedEffect(archivedCount) { if (archivedCount == 0) showArchive = false }
    LaunchedEffect(requestCount) { if (requestCount == 0) showRequests = false }
    LaunchedEffect(lockedCount) { if (lockedCount == 0) showLocked = false }
    BackHandler(enabled = showArchive || showRequests || showLocked) { showArchive = false; showRequests = false; showLocked = false }

    DisposableEffect(Unit) {
        dms.watchInbox(true)
        onDispose { dms.watchInbox(false) }
    }

    Column(Modifier.fillMaxSize().background(colors.background)) {
        Row(Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TopIcon(NexIcons.ArrowBack) { if (showArchive || showRequests || showLocked) { showArchive = false; showRequests = false; showLocked = false } else onBack() }
            Text(if (showRequests) S.dmRequests else if (showLocked) S.dmLockedFolder else if (showArchive) S.dmArchived else S.dmTitle, style = Typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary, modifier = Modifier.weight(1f).padding(start = 6.dp))
            TopIcon(NexIcons.Lock) { showLockSheet = true }
            TopIcon(NexIcons.Edit) { showNew = true }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))

        when {
            !loaded && inbox.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.textPrimary, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            }
            inbox.isEmpty() && myName.isBlank() -> Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(NexIcons.ChatBubbleOutline, null, tint = colors.textMuted, modifier = Modifier.size(44.dp))
                Spacer(Modifier.height(14.dp))
                Text(S.dmEmptyTitle, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.height(6.dp))
                Text(S.dmEmptySub, color = colors.textMuted, fontSize = 13.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(18.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(24.dp)).background(colors.textPrimary).clickable { showNew = true }.padding(horizontal = 22.dp, vertical = 12.dp)
                ) { Text(S.dmNewMessage, color = colors.background, fontWeight = FontWeight.Bold) }
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                if (!showArchive && !showRequests && !showLocked && requestCount > 0) item(key = "request-row") {
                    Row(Modifier.fillMaxWidth().clickable { showRequests = true }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(NexIcons.Mail, null, tint = colors.textSecondary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(16.dp))
                        Text(S.dmRequests, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Box(Modifier.clip(CircleShape).background(colors.accent).padding(horizontal = 7.dp, vertical = 2.dp)) {
                            Text(requestCount.toString(), color = colors.background, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border.copy(alpha = 0.5f)))
                }
                if (!showArchive && !showRequests && !showLocked && !hasNote && myName.isNotBlank()) item(key = "note-row") {
                    Row(Modifier.fillMaxWidth().clickable { onOpenChat(myName) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(50.dp).clip(CircleShape).background(colors.card).border(1.dp, colors.border, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(NexIcons.Pin, null, tint = colors.textPrimary, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(S.dmNote, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(S.dmNoteSub, color = colors.textMuted, fontSize = 13.sp)
                        }
                    }
                    Box(Modifier.fillMaxWidth().padding(start = 78.dp).height(1.dp).background(colors.border.copy(alpha = 0.5f)))
                }
                if (!showArchive && !showRequests && !showLocked && lockedCount > 0) item(key = "locked-row") {
                    Row(Modifier.fillMaxWidth().clickable { folderPinError = null; askFolderPin = true }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(NexIcons.Lock, null, tint = colors.textSecondary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(16.dp))
                        Text(S.dmLockedFolder, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border.copy(alpha = 0.5f)))
                }
                if (!showArchive && !showRequests && !showLocked && archivedCount > 0) item(key = "archive-row") {
                    Row(Modifier.fillMaxWidth().clickable { showArchive = true }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(NexIcons.Download, null, tint = colors.textSecondary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(16.dp))
                        Text(S.dmArchived, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(archivedCount.toString(), color = colors.textMuted, fontSize = 13.sp)
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border.copy(alpha = 0.5f)))
                }
                items(shown, key = { it.uid }) { c ->
                    val draft = dmDrafts[c.uid].orEmpty()
                    Row(
                        Modifier.fillMaxWidth()
                            .combinedClickable(onClick = { onOpenChat(c.username) }, onLongClick = { menuFor = c })
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ChatAvatar(c.uid, c.av, c.display.ifBlank { c.username }, 50.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (c.note) S.dmNote else dmName(dms.nickname(c.uid).ifBlank { c.display }, c.username, localVer),
                                    color = colors.textPrimary, fontWeight = if (c.unread > 0) FontWeight.Bold else FontWeight.SemiBold,
                                    fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                                )
                                if (c.pinned) { Spacer(Modifier.width(6.dp)); Icon(NexIcons.Pin, null, tint = colors.textMuted, modifier = Modifier.size(13.dp)) }
                                if (c.muted) { Spacer(Modifier.width(4.dp)); Icon(NexIcons.BellOff, null, tint = colors.textMuted, modifier = Modifier.size(13.dp)) }
                            }
                            Spacer(Modifier.height(2.dp))
                            if (draft.isNotBlank()) {
                                Text("${S.dmDraftLabel}: $draft", color = RED, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            } else {
                                Text(
                                    (if (c.fromMe) "${S.dmYou}: " else "") + c.last,
                                    color = if (c.unread > 0) colors.textPrimary else colors.textMuted,
                                    fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            val today = chatDayKey(System.currentTimeMillis())
                            val dk = chatDayKey(c.ts)
                            Text(
                                if (dk == today) chatTime(c.ts) else chatDayLabel(dk, S),
                                color = if (c.unread > 0) colors.textPrimary else colors.textMuted, fontSize = 11.sp
                            )
                            if (c.unread > 0) {
                                Spacer(Modifier.height(5.dp))
                                Box(
                                    Modifier.clip(CircleShape).background(if (c.muted) colors.textMuted else colors.accent).padding(horizontal = 7.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(if (c.unread > 99) "99+" else c.unread.toString(), color = colors.background, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().padding(start = 78.dp).height(1.dp).background(colors.border.copy(alpha = 0.5f)))
                }
            }
        }
    }

    if (showLockSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLockSheet = false },
            containerColor = colors.background,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text(S.dmSettings, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(6.dp))
                SheetRow(NexIcons.Edit, S.dmMyStatus) { showLockSheet = false; showStatus = true }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(S.dmShareNp, color = colors.textPrimary, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = dms.shareNowPlaying(),
                        onCheckedChange = { on ->
                            dms.setShareNowPlaying(on)
                            if (!on) scope.launch { dms.publishNowPlaying(null) }
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = colors.background)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(S.dmLockTitle, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 20.dp))
                Text(S.dmLockNote, color = colors.textMuted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                if (!dms.lockEnabled()) {
                    SheetRow(NexIcons.Lock, S.dmLockOn) { showLockSheet = false; lockError = null; lockFlow = 1 }
                } else {
                    SheetRow(NexIcons.Lock, S.dmLockChange) { showLockSheet = false; lockError = null; lockFlow = 4 }
                    SheetRow(NexIcons.DeleteOutline, S.dmLockOff, danger = true) { showLockSheet = false; lockError = null; lockFlow = 3 }
                }
            }
        }
    }

    if (askFolderPin) {
        Dialog(onDismissRequest = { askFolderPin = false }) {
            Box(Modifier.clip(RoundedCornerShape(24.dp)).background(colors.surface).padding(vertical = 20.dp, horizontal = 12.dp)) {
                PinPad(
                    title = S.dmPinEnter,
                    error = folderPinError,
                    onComplete = { pin ->
                        if (dms.checkPin(pin)) {
                            inbox.filter { dms.isChatLocked(it.uid) }.forEach { dms.markChatUnlocked(it.uid) }
                            dms.touchLock()
                            folderPinError = null
                            askFolderPin = false
                            showLocked = true
                        } else {
                            folderPinError = currentStrings().dmPinWrong
                        }
                    }
                )
            }
        }
    }

    if (showStatus) {
        var emoji by remember { mutableStateOf(dms.myMoodEmoji()) }
        var text by remember { mutableStateOf(dms.myMoodText()) }
        AlertDialog(
            onDismissRequest = { showStatus = false },
            containerColor = colors.surface,
            title = { Text(S.dmMyStatus, color = colors.textPrimary) },
            text = {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("😊", "😎", "🎧", "😴", "🔥", "💭", "😢", "❤️").forEach { e ->
                            Box(
                                Modifier.size(34.dp).clip(CircleShape)
                                    .background(if (emoji == e) colors.card else Color.Transparent)
                                    .border(1.dp, if (emoji == e) colors.accent else Color.Transparent, CircleShape)
                                    .clickable { emoji = if (emoji == e) "" else e },
                                contentAlignment = Alignment.Center
                            ) { Text(e, fontSize = 18.sp) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.card).border(1.dp, colors.border, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
                        if (text.isEmpty()) Text(S.dmStatusHint, color = colors.textMuted, fontSize = 14.sp)
                        BasicTextField(
                            value = text, onValueChange = { text = it.take(40) }, singleLine = true,
                            textStyle = TextStyle(color = colors.textPrimary, fontSize = 14.sp), cursorBrush = SolidColor(colors.textPrimary),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(S.dmStatusNote, color = colors.textMuted, fontSize = 11.sp, lineHeight = 15.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showStatus = false
                    scope.launch { dms.setMood(emoji, text.trim())?.let { NexNotifier.warning(dmErrText(it), currentStrings().dmTitle) } }
                }) { Text(S.dmSave, color = colors.textPrimary) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showStatus = false
                    scope.launch { dms.setMood("", "")?.let { NexNotifier.warning(dmErrText(it), currentStrings().dmTitle) } }
                }) { Text(S.dmStatusClear, color = RED) }
            }
        )
    }

    if (lockFlow != 0) {
        Dialog(onDismissRequest = { lockFlow = 0; lockFirst = "" }) {
            Box(Modifier.clip(RoundedCornerShape(24.dp)).background(colors.surface).padding(vertical = 20.dp, horizontal = 12.dp)) {
                PinPad(
                    title = when (lockFlow) {
                        1 -> S.dmPinNew
                        2 -> S.dmPinConfirm
                        else -> S.dmPinCurrent
                    },
                    error = lockError,
                    onComplete = { pin ->
                        lockError = null
                        when (lockFlow) {
                            1 -> { lockFirst = pin; lockFlow = 2 }
                            2 -> if (pin == lockFirst) {
                                dms.setPin(pin); lockFlow = 0; lockFirst = ""
                                NexNotifier.success(currentStrings().dmLockDone, currentStrings().dmTitle)
                            } else { lockFlow = 1; lockFirst = ""; lockError = currentStrings().dmPinMismatch }
                            3 -> if (dms.checkPin(pin)) {
                                dms.removePin(); lockFlow = 0
                                NexNotifier.info(currentStrings().dmLockOffDone, currentStrings().dmTitle)
                            } else { lockError = currentStrings().dmPinWrong }
                            4 -> if (dms.checkPin(pin)) { lockFlow = 1 } else { lockError = currentStrings().dmPinWrong }
                            else -> Unit
                        }
                        Unit
                    }
                )
            }
        }
    }

    if (showNew) NewDmSheet(onPick = { showNew = false; onOpenChat(it) }, onDismiss = { showNew = false })

    menuFor?.let { c ->
        ModalBottomSheet(
            onDismissRequest = { menuFor = null },
            containerColor = colors.background,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text(c.display.ifBlank { c.username }, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                SheetRow(NexIcons.Pin, if (c.pinned) S.dmUnpin else S.dmPin) {
                    menuFor = null
                    scope.launch { dms.inboxAction("dm_pin", c.uid, !c.pinned)?.let { NexNotifier.warning(dmErrText(it), currentStrings().dmTitle) } }
                }
                SheetRow(if (c.muted) NexIcons.Bell else NexIcons.BellOff, if (c.muted) S.dmUnmute else S.dmMute) {
                    menuFor = null
                    scope.launch { dms.inboxAction("dm_mute", c.uid, !c.muted)?.let { NexNotifier.warning(dmErrText(it), currentStrings().dmTitle) } }
                }
                if (c.unread == 0) SheetRow(NexIcons.Mail, S.dmMarkUnread) {
                    menuFor = null
                    scope.launch { dms.inboxAction("dm_unread", c.uid)?.let { NexNotifier.warning(dmErrText(it), currentStrings().dmTitle) } }
                }
                SheetRow(NexIcons.Lock, if (dms.isChatLocked(c.uid)) S.dmUnlockChat else S.dmLockChat) {
                    menuFor = null
                    if (dms.isChatLocked(c.uid)) {
                        dms.setChatLocked(c.uid, c.username, false)
                    } else if (!dms.lockEnabled()) {
                        NexNotifier.info(currentStrings().dmLockNeedPin, currentStrings().dmTitle)
                        showLockSheet = true
                    } else {
                        dms.setChatLocked(c.uid, c.username, true)
                    }
                }
                SheetRow(NexIcons.Download, if (c.archived) S.dmUnarchive else S.dmArchive) {
                    menuFor = null
                    scope.launch { dms.inboxAction("dm_archive", c.uid, !c.archived)?.let { NexNotifier.warning(dmErrText(it), currentStrings().dmTitle) } }
                }
                SheetRow(NexIcons.DeleteOutline, S.dmDeleteChat, danger = true) { confirmHide = c; menuFor = null }
            }
        }
    }

    confirmHide?.let { c ->
        AlertDialog(
            onDismissRequest = { confirmHide = null },
            containerColor = colors.surface,
            title = { Text(S.dmDeleteChat, color = colors.textPrimary) },
            text = { Text(S.dmDeleteChatBody, color = colors.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    confirmHide = null
                    dmDrafts.remove(c.uid)
                    scope.launch { dms.inboxAction("dm_hide", c.uid)?.let { NexNotifier.warning(dmErrText(it), currentStrings().dmTitle) } }
                }) { Text(S.dmDeleteAction, color = RED) }
            },
            dismissButton = { TextButton(onClick = { confirmHide = null }) { Text(S.dmCancel, color = colors.textPrimary) } }
        )
    }
}

@Composable
private fun SheetRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val colors = NexrifyAppTheme.colors
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (danger) RED else colors.textPrimary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = if (danger) RED else colors.textPrimary, fontSize = 15.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewDmSheet(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = NexrifyAppTheme.colors
    val manager = remember { ChatManager.getInstance() }
    val myName = AccountManager.getInstance().user.value?.username.orEmpty()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ChatUserHit>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        val q = query.trim().removePrefix("@")
        if (q.length < 2) { results = emptyList(); searched = false; loading = false; return@LaunchedEffect }
        loading = true
        delay(300)
        results = runCatching { manager.searchUsers(q) }.getOrDefault(emptyList()).filter { !it.username.equals(myName, true) }
        searched = true
        loading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.fillMaxWidth().fillMaxSize(0.9f).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(S.dmNewMessage, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.card).border(1.dp, colors.border, RoundedCornerShape(24.dp)).padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (query.isEmpty()) Text(S.dmSearchUsers, color = colors.textMuted, fontSize = 15.sp)
                BasicTextField(
                    value = query, onValueChange = { query = it.take(24) }, singleLine = true,
                    textStyle = TextStyle(color = colors.textPrimary, fontSize = 15.sp),
                    cursorBrush = SolidColor(colors.textPrimary), modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(10.dp))
            when {
                loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.textPrimary, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
                searched && results.isEmpty() -> Text(S.dmNoResults, color = colors.textMuted, modifier = Modifier.padding(24.dp))
                !searched -> Text(S.dmNewChatFirst, color = colors.textMuted, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
                else -> LazyColumn(Modifier.fillMaxWidth()) {
                    items(results, key = { it.uid }) { u ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(u.username) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ChatAvatar(u.uid, "", u.display.ifBlank { u.username }, 42.dp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(u.display.ifBlank { u.username }, color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
                                Text("@${u.username}", color = colors.textMuted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/* =============================== PERCAKAPAN =============================== */

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun DmThreadContent(viewModel: MusicViewModel, username: String, onBack: () -> Unit) {
    val colors = NexrifyAppTheme.colors
    val dms = remember { DmManager.getInstance() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val myId = AccountManager.getInstance().user.value?.id.orEmpty()

    var peer by remember { mutableStateOf<DmPeer?>(null) }
    var peerFailed by remember { mutableStateOf(false) }

    // Cari lawan bicara: dari kotak masuk dulu (cepat), kalau belum ada tanya server lewat profil.
    LaunchedEffect(username) {
        peerFailed = false
        val c = dms.inbox.value.firstOrNull { it.username.equals(username, true) }
        val p = if (c != null) DmPeer(c.uid, c.username, c.display, c.av) else runCatching { dms.peerFromUsername(username) }.getOrNull()
        if (p == null) peerFailed = true else { peer = p; dms.openThread(p) }
    }
    DisposableEffect(Unit) {
        dms.watchInbox(true)
        onDispose {
            dms.watchInbox(false)
            dms.closeThread()
        }
    }

    val liveMessages by dms.messages.collectAsState()
    val livePeer by dms.peer.collectAsState()
    val typing by dms.typing.collectAsState()
    val peerRead by dms.peerRead.collectAsState()
    val blocked by dms.blocked.collectAsState()
    val loaded by dms.loaded.collectAsState()
    val offline by dms.offline.collectAsState()
    val stars by dms.stars.collectAsState()
    val disappear by dms.disappear.collectAsState()
    val inboxList by dms.inbox.collectAsState()
    val requestIn by dms.request.collectAsState()
    val translations by dms.translated.collectAsState()
    val callPhase by CallManager.getInstance().phase.collectAsState()
    val pending by dms.pending.collectAsState()
    val localVer by dms.localVersion.collectAsState()
    val isNote = peer?.uid != null && peer?.uid == myId
    val shownPeer = livePeer?.takeIf { it.uid == peer?.uid } ?: peer
    val messages = if (shownPeer != null && livePeer?.uid == shownPeer.uid) liveMessages else emptyList()

    LaunchedEffect(Unit) { dms.errors.collect { NexNotifier.warning(dmErrText(it), currentStrings().dmTitle) } }

    var input by remember { mutableStateOf(TextFieldValue("")) }
    var replyTo by remember { mutableStateOf<DmMessage?>(null) }
    var editing by remember { mutableStateOf<DmMessage?>(null) }
    var menuMsg by remember { mutableStateOf<DmMessage?>(null) }
    var deleteMsg by remember { mutableStateOf<DmMessage?>(null) }
    var showMore by remember { mutableStateOf(false) }
    var showStars by remember { mutableStateOf(false) }
    var showDisappear by remember { mutableStateOf(false) }
    var forwardMsg by remember { mutableStateOf<DmMessage?>(null) }
    val context = LocalContext.current
    val recorder = remember { VoiceRecorder(context) }
    var recording by remember { mutableStateOf(false) }
    var sendingVoice by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { recorder.cancel(); VoicePlayer.stop() } }
    fun beginRecording() {
        if (recorder.start()) recording = true else NexNotifier.warning(currentStrings().voiceMicFail, currentStrings().dmTitle)
    }
    val micLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) beginRecording() else NexNotifier.warning(currentStrings().voiceMicDenied, currentStrings().dmTitle)
    }
    fun startVoice() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) beginRecording()
        else micLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
    }
    LaunchedEffect(Unit) { VoicePlayer.errors.collect { code -> NexNotifier.warning(dmErrText(if (code == "vn_gone") "vn_gone" else code), currentStrings().dmTitle) } }
    // Terakhir aktif lawan bicara (hormati pengaturan privasi: kalau disembunyikan, tidak ditampilkan).
    var peerSeen by remember { mutableStateOf(0L) }
    var peerOnline by remember { mutableStateOf(false) }
    var peerHidden by remember { mutableStateOf(true) }
    LaunchedEffect(username) {
        if (username.equals(AccountManager.getInstance().user.value?.username, true)) return@LaunchedEffect
        while (true) {
            val prof = ChatManager.getInstance().profile(username)
            if (prof != null) { peerSeen = prof.lastSeen; peerOnline = prof.online; peerHidden = prof.presenceHidden }
            delay(60_000)
        }
    }
    val placeCall = rememberMicPermission {
        val sp = shownPeer
        if (sp != null) {
            if (callPhase != CallPhase.IDLE) NexNotifier.info(currentStrings().callErrBusyMe, currentStrings().callTitle)
            else CallManager.getInstance().startCall(CallPeer(sp.uid, sp.username, sp.display, sp.av))
        }
    }
    var showAttach by remember { mutableStateOf(false) }
    var sendingImage by remember { mutableStateOf(false) }
    var viewerImage by remember { mutableStateOf<DmImage?>(null) }
    var onceId by remember { mutableStateOf<String?>(null) }
    var onceTextShown by remember { mutableStateOf<String?>(null) }
    var pendingOnce by remember { mutableStateOf(false) }
    var highlightSeq by remember { mutableStateOf<Long?>(null) }
    var extra by remember { mutableStateOf<PeerExtra?>(null) }
    LaunchedEffect(shownPeer?.uid) {
        val uid = shownPeer?.uid ?: return@LaunchedEffect
        if (uid == myId) return@LaunchedEffect
        while (true) {
            extra = dms.peerExtra(uid)
            delay(60_000)
        }
    }
    val pickImage = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && !sendingImage) {
            val rt = replyTo
            val once = pendingOnce
            pendingOnce = false
            val caption = if (once) "" else input.text.trim()
            sendingImage = true
            scope.launch {
                val prep = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { DmImageUtil.compress(context, uri) }
                if (prep == null) {
                    NexNotifier.warning(currentStrings().dmPhotoFail, currentStrings().dmTitle)
                } else {
                    val err = dms.sendImage(prep.bytes, prep.w, prep.h, caption, rt, once)
                    if (err == null) {
                        replyTo = null
                        if (caption.isNotEmpty() && editing == null) input = TextFieldValue("")
                    } else NexNotifier.warning(dmErrText(err), currentStrings().dmTitle)
                }
                sendingImage = false
            }
        }
    }
    var showNick by remember { mutableStateOf(false) }
    var showTheme by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var searchQ by remember { mutableStateOf("") }
    var searchRes by remember { mutableStateOf<List<DmMessage>>(emptyList()) }
    val listState = rememberLazyListState()

    // Pulihkan draf saat masuk, simpan saat keluar.
    LaunchedEffect(shownPeer?.uid) {
        val uid = shownPeer?.uid ?: return@LaunchedEffect
        val d = dmDrafts[uid].orEmpty()
        if (d.isNotEmpty() && input.text.isEmpty()) input = TextFieldValue(d, androidx.compose.ui.text.TextRange(d.length))
    }
    DisposableEffect(shownPeer?.uid) {
        onDispose {
            val uid = shownPeer?.uid
            if (uid != null) {
                if (input.text.isBlank() || editing != null) { dmDrafts.remove(uid) } else { dmDrafts[uid] = input.text }
            }
        }
    }

    // Gulir ke bawah saat ada pesan baru (kalau sudah dekat dasar atau pesan dariku).
    LaunchedEffect(messages.size, typing) {
        if (messages.isEmpty()) return@LaunchedEffect
        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        val total = listState.layoutInfo.totalItemsCount
        if (last >= total - 3 || messages.last().uid == myId) listState.scrollToItem(messages.size - 1)
    }

    LaunchedEffect(searchQ, searching) {
        if (!searching || searchQ.trim().length < 2) { searchRes = emptyList(); return@LaunchedEffect }
        delay(350)
        searchRes = dms.search(searchQ.trim())
    }

    fun flash(idx: Int) {
        val target = messages.getOrNull(idx)?.seq ?: return
        scope.launch {
            listState.animateScrollToItem(idx)
            highlightSeq = target
            delay(1600)
            if (highlightSeq == target) highlightSeq = null
        }
    }

    fun submit() {
        val text = input.text.trim()
        if (text.isEmpty()) return
        val ed = editing
        if (ed != null) {
            if (text != ed.text) scope.launch { dms.edit(ed.seq, text) }
            editing = null
        } else {
            dms.send(text, replyTo)
            replyTo = null
        }
        input = TextFieldValue("")
        shownPeer?.uid?.let { dmDrafts.remove(it) }
    }

    Column(Modifier.fillMaxSize().background(colors.background)) {
        // ---- header ----
        Row(Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TopIcon(NexIcons.ArrowBack, onBack)
            if (shownPeer != null) ChatAvatar(shownPeer.uid, shownPeer.av, shownPeer.display.ifBlank { shownPeer.username }, 38.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (isNote) S.dmNote else dmName(shownPeer?.uid?.let { dms.nickname(it) }.orEmpty().ifBlank { shownPeer?.display.orEmpty() }, shownPeer?.username ?: username, localVer),
                    style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                val sub = when {
                    isNote -> S.dmNoteSub
                    typing -> S.dmTyping
                    offline -> S.dmOffline
                    !peerHidden && peerOnline -> S.dmOnline
                    !peerHidden && peerSeen > 0 -> S.chatLastSeen(chatAgo(peerSeen, S))
                    else -> "@${shownPeer?.username ?: username}"
                }
                val ex = extra
                val extraLine = when {
                    ex == null -> ""
                    else -> listOf(
                        (ex.moodEmoji + " " + ex.moodText).trim(),
                        if (ex.npTitle.isNotBlank()) "🎧 " + ex.npTitle + (if (ex.npArtist.isNotBlank()) " – " + ex.npArtist else "") else ""
                    ).filter { it.isNotBlank() }.joinToString("  ·  ")
                }
                Text(sub, fontSize = 12.sp, color = if (typing) colors.textPrimary else colors.textMuted, fontWeight = if (typing) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                if (extraLine.isNotBlank() && !isNote) Text(extraLine, fontSize = 11.sp, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!isNote && !blocked && !requestIn && !pending && shownPeer != null) TopIcon(NexIcons.Phone) { placeCall() }
            TopIcon(NexIcons.Search) { searching = !searching; if (!searching) { searchQ = ""; searchRes = emptyList() } }
            Box {
                TopIcon(NexIcons.MoreVert) { showMore = true }
                DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                    if (!isNote && shownPeer != null) DropdownMenuItem(
                        text = { Text(S.dmMubarInvite, color = colors.textPrimary) },
                        onClick = {
                            showMore = false
                            val sp = shownPeer
                            scope.launch {
                                val mb = MubarManager.getInstance()
                                try {
                                    if (mb.room.value == null) mb.create(viewModel.currentTrack.value)
                                    if (mb.room.value == null) {
                                        NexNotifier.warning(currentStrings().dmGenericErr, currentStrings().dmTitle)
                                    } else {
                                        mb.invite(sp.username)
                                        NexNotifier.success(currentStrings().dmMubarSent(sp.display.ifBlank { sp.username }), currentStrings().dmTitle)
                                        mb.openSheet()
                                    }
                                } catch (e: AccountError) {
                                    NexNotifier.warning(dmErrText(e.code), currentStrings().dmTitle)
                                } catch (e: Throwable) {
                                    NexNotifier.warning(currentStrings().dmNoNetworkErr, currentStrings().dmTitle)
                                }
                            }
                        }
                    )
                    DropdownMenuItem(text = { Text(S.dmStarred, color = colors.textPrimary) }, onClick = { showMore = false; showStars = true })
                    DropdownMenuItem(text = { Text(S.dmDisappear, color = colors.textPrimary) }, onClick = { showMore = false; showDisappear = true })
                    if (!isNote) DropdownMenuItem(text = { Text(S.dmNickname, color = colors.textPrimary) }, onClick = { showMore = false; showNick = true })
                    DropdownMenuItem(text = { Text(S.dmTheme, color = colors.textPrimary) }, onClick = { showMore = false; showTheme = true })
                    if (!isNote) DropdownMenuItem(
                        text = { Text(if (blocked) S.dmUnblockUser else S.dmBlockUser, color = colors.textPrimary) },
                        onClick = {
                            showMore = false
                            val uid = shownPeer?.uid ?: return@DropdownMenuItem
                            scope.launch {
                                val ok = dms.setBlock(uid, !blocked)
                                if (ok) NexNotifier.info(if (blocked) currentStrings().dmUnblockedDone else currentStrings().dmBlockedDone, currentStrings().dmTitle)
                                else NexNotifier.warning(currentStrings().dmGenericErr, currentStrings().dmTitle)
                            }
                        }
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))

        if (disappear > 0) {
            Text(
                S.dmDisappearBanner(if (disappear >= 604_800L) S.dmDisappear7 else S.dmDisappear24),
                color = colors.textMuted, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 16.dp, vertical = 5.dp)
            )
        }

        if (searching) {
            Column(Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.card).border(1.dp, colors.border, RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (searchQ.isEmpty()) Text(S.dmSearchInChat, color = colors.textMuted, fontSize = 14.sp)
                    BasicTextField(
                        value = searchQ, onValueChange = { searchQ = it.take(60) }, singleLine = true,
                        textStyle = TextStyle(color = colors.textPrimary, fontSize = 14.sp), cursorBrush = SolidColor(colors.textPrimary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (searchQ.trim().length >= 2 && searchRes.isEmpty()) Text(S.dmNoResults, color = colors.textMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
                searchRes.take(6).forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            val idx = messages.indexOfFirst { it.seq == m.seq }
                            searching = false; searchQ = ""; searchRes = emptyList()
                            if (idx >= 0) flash(idx)
                        }.padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            (if (m.uid == myId) "${S.dmYou}: " else "") + dmPreview(m),
                            color = colors.textPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                        )
                        Text(chatTime(m.ts), color = colors.textMuted, fontSize = 11.sp)
                    }
                }
            }
        }

        // ---- isi ----
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                peerFailed -> Text(S.dmLoadFailed, color = colors.textMuted, modifier = Modifier.align(Alignment.Center).padding(24.dp))
                !loaded || shownPeer == null -> CircularProgressIndicator(color = colors.textPrimary, strokeWidth = 2.dp, modifier = Modifier.align(Alignment.Center).size(28.dp))
                messages.isEmpty() -> Text(S.dmStartHint, color = colors.textMuted, fontSize = 13.sp, modifier = Modifier.align(Alignment.Center).padding(32.dp))
                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                    itemsIndexed(messages, key = { _, m -> m.localId ?: "s${m.seq}" }) { i, m ->
                        val prev = messages.getOrNull(i - 1)
                        if (prev == null || chatDayKey(prev.ts) != chatDayKey(m.ts)) ChatDateHeader(chatDayLabel(chatDayKey(m.ts), S))
                        if (m.call != null) DmCallRow(m, m.uid == myId) else DmBubble(
                            m = m, mine = m.uid == myId, read = m.uid == myId && m.seq > 0 && peerRead >= m.seq, myId = myId,
                            starred = m.seq in stars,
                            loadImage = { id -> dms.loadImage(id) },
                            loadPreview = { url -> dms.linkPreview(url) },
                            onOpenOnce = { om -> scope.launch { dms.openOnce(om.seq)?.let { onceId = it } } },
                            onOpenOnceText = { om -> scope.launch { dms.openOnceText(om.seq)?.let { onceTextShown = it } } },
                            highlight = highlightSeq == m.seq,
                            translation = translations[m.seq],
                            onHideTranslation = { dms.hideTranslation(m.seq) },
                            onOpenImage = { viewerImage = it },
                            mineColor = BUBBLE_THEMES.getOrNull(shownPeer?.uid?.let { dms.theme(it) } ?: 0),
                            onQuote = { qs ->
                                val idx = messages.indexOfFirst { it.seq == qs }
                                if (idx >= 0) flash(idx)
                            },
                            onSwipeReply = { if (m.status == SendStatus.SENT && !m.deleted) { replyTo = m; editing = null } },
                            onLong = { if (m.status == SendStatus.SENT && !m.deleted) menuMsg = m },
                            onRetry = { m.localId?.let { dms.retry(it) } },
                            onReact = { e -> scope.launch { dms.react(m.seq, e) } },
                            onPlay = { s ->
                                viewModel.playTrackFromSearch(
                                    Track(
                                        id = s.id, url = s.url.ifBlank { "https://www.youtube.com/watch?v=${s.id}" }, title = s.title,
                                        artist = s.artist, thumbnailUrl = s.thumb, durationSeconds = s.dur
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }

        // ---- kolom ketik ----
        if (requestIn && !isNote) {
            Column(Modifier.fillMaxWidth().background(colors.surface).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(S.dmRequestBanner(shownPeer?.display?.takeIf { it.isNotBlank() } ?: shownPeer?.username ?: "@$username"), color = colors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).border(1.dp, colors.border, RoundedCornerShape(24.dp)).clickable {
                            val uid = shownPeer?.uid ?: return@clickable
                            scope.launch {
                                val err = dms.declineRequest(uid)
                                if (err == null) onBack() else NexNotifier.warning(dmErrText(err), currentStrings().dmTitle)
                            }
                        }.padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(S.dmDecline, color = RED, fontWeight = FontWeight.Bold) }
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(colors.textPrimary).clickable {
                            val uid = shownPeer?.uid ?: return@clickable
                            scope.launch { dms.acceptRequest(uid)?.let { NexNotifier.warning(dmErrText(it), currentStrings().dmTitle) } }
                        }.padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(S.dmAccept, color = colors.background, fontWeight = FontWeight.Bold) }
                }
            }
        } else if (pending && messages.any { it.uid == myId }) {
            Box(Modifier.fillMaxWidth().background(colors.surface).padding(16.dp), contentAlignment = Alignment.Center) {
                Text(S.dmPendingNote, color = colors.textMuted, fontSize = 13.sp)
            }
        } else if (blocked) {
            Box(
                Modifier.fillMaxWidth().background(colors.surface).clickable {
                    val uid = shownPeer?.uid ?: return@clickable
                    scope.launch { dms.setBlock(uid, false) }
                }.padding(16.dp),
                contentAlignment = Alignment.Center
            ) { Text(S.dmYouBlockedTap, color = colors.textSecondary, fontSize = 13.sp) }
        } else {
            val banner = editing ?: replyTo
            if (banner != null) {
                Row(Modifier.fillMaxWidth().background(colors.surface).padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(3.dp).height(32.dp).background(colors.accent))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (editing != null) S.dmEditing else (if (banner.uid == myId) S.dmYou else shownPeer?.display ?: ""),
                            color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp
                        )
                        Text(dmPreview(banner), color = colors.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TopIcon(NexIcons.Close) { if (editing != null) { editing = null; input = TextFieldValue("") } else replyTo = null }
                }
            }
            if (recording) {
                VoiceRecordBar(
                    recorder = recorder, sending = sendingVoice,
                    onCancel = { recording = false },
                    onTooShort = { recording = false; NexNotifier.info(currentStrings().voiceTooShort, currentStrings().dmTitle) },
                    onSend = { clip ->
                        val rt = replyTo
                        sendingVoice = true
                        scope.launch {
                            val err = dms.sendVoice(clip, rt)
                            sendingVoice = false
                            recording = false
                            if (err == null) replyTo = null
                            else { runCatching { clip.file.delete() }; NexNotifier.warning(dmErrText(err), currentStrings().dmTitle) }
                        }
                    }
                )
            } else Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.Bottom) {
                if (editing == null) {
                    Box(
                        Modifier.size(46.dp).clip(CircleShape).background(colors.card).border(1.dp, colors.border, CircleShape).clickable(enabled = !sendingImage) { showAttach = true },
                        contentAlignment = Alignment.Center
                    ) {
                        if (sendingImage) CircularProgressIndicator(color = colors.textPrimary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        else Icon(NexIcons.Add, S.dmPhoto, tint = colors.textPrimary, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(26.dp)).background(colors.card).border(1.dp, colors.border, RoundedCornerShape(26.dp)).padding(horizontal = 16.dp, vertical = 11.dp)
                ) {
                    if (input.text.isEmpty()) Text(S.dmInputHint, color = colors.textMuted, fontSize = 15.sp)
                    BasicTextField(
                        value = input,
                        onValueChange = { v ->
                            val clipped = if (v.text.length > CHAT_MAX_LENGTH) v.copy(text = v.text.take(CHAT_MAX_LENGTH)) else v
                            if (clipped.text.length > input.text.length && clipped.text.isNotBlank() && !isNote) dms.notifyTyping()
                            input = clipped
                        },
                        maxLines = 5,
                        textStyle = TextStyle(color = colors.textPrimary, fontSize = 15.sp, lineHeight = 20.sp),
                        cursorBrush = SolidColor(colors.textPrimary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.width(8.dp))
                val canSend = input.text.isNotBlank()
                val micMode = !canSend && editing == null
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(if (canSend) colors.textPrimary else colors.card)
                        .border(1.dp, if (canSend) colors.textPrimary else colors.border, CircleShape)
                        .clickable(enabled = canSend || micMode) { if (micMode) startVoice() else submit() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(if (micMode) NexIcons.Mic else if (editing != null) NexIcons.Check else NexIcons.Send, null, tint = if (canSend) colors.background else colors.textPrimary, modifier = Modifier.size(22.dp))
                }
            }
        }
    }

    // ---- menu pesan ----
    menuMsg?.let { m ->
        val mine = m.uid == myId
        ModalBottomSheet(
            onDismissRequest = { menuMsg = null },
            containerColor = colors.background,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    CHAT_EMOJIS_QUICK.forEach { e ->
                        val mineReact = m.reactions[e]?.contains(myId) == true
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).background(if (mineReact) colors.card else Color.Transparent)
                                .clickable { menuMsg = null; scope.launch { dms.react(m.seq, e) } },
                            contentAlignment = Alignment.Center
                        ) { Text(e, fontSize = 24.sp) }
                    }
                }
                SheetRow(NexIcons.Reply, S.dmReply) { replyTo = m; editing = null; menuMsg = null }
                if (m.voice == null && m.image == null) SheetRow(NexIcons.Send, S.dmForward) { forwardMsg = m; menuMsg = null }
                if (m.text.isNotBlank() && m.seq !in translations) SheetRow(NexIcons.Translate, S.dmTranslate) {
                    menuMsg = null
                    scope.launch { dms.translate(m.seq, if (currentStrings().english) "en" else "id")?.let { NexNotifier.warning(dmErrText(it), currentStrings().dmTitle) } }
                }
                SheetRow(NexIcons.Flag, if (m.seq in stars) S.dmUnstar else S.dmStar) {
                    menuMsg = null
                    scope.launch { dms.star(m.seq, m.seq !in stars) }
                }
                if (m.text.isNotBlank()) SheetRow(NexIcons.Copy, S.dmCopy) {
                    clipboard.setText(AnnotatedString(m.text)); menuMsg = null
                    NexNotifier.info(currentStrings().dmCopied, currentStrings().dmTitle)
                }
                if (mine && m.text.isNotBlank() && m.song == null && System.currentTimeMillis() - m.ts < EDIT_WINDOW) SheetRow(NexIcons.Edit, S.dmEditAction) {
                    editing = m; replyTo = null
                    input = TextFieldValue(m.text, androidx.compose.ui.text.TextRange(m.text.length))
                    menuMsg = null
                }
                if (mine) SheetRow(NexIcons.DeleteOutline, S.dmDeleteAction, danger = true) { deleteMsg = m; menuMsg = null }
            }
        }
    }

    if (showAttach) {
        ModalBottomSheet(
            onDismissRequest = { showAttach = false },
            containerColor = colors.background,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                SheetRow(NexIcons.ImagePick, S.dmPhoto) { showAttach = false; pendingOnce = false; pickImage.launch("image/*") }
                SheetRow(NexIcons.Lock, S.dmTextOnce) {
                    showAttach = false
                    val txt = input.text.trim()
                    if (txt.isEmpty()) {
                        NexNotifier.info(currentStrings().dmTextOnceHint, currentStrings().dmTitle)
                    } else {
                        input = TextFieldValue("")
                        scope.launch { dms.sendOnceText(txt)?.let { NexNotifier.warning(dmErrText(it), currentStrings().dmTitle) } }
                    }
                }
                SheetRow(NexIcons.Lock, S.dmPhotoOnce) { showAttach = false; pendingOnce = true; pickImage.launch("image/*") }
                SheetRow(NexIcons.Headphones, S.dmSongNow) {
                    showAttach = false
                    val t = viewModel.currentTrack.value
                    if (t == null) NexNotifier.info(currentStrings().dmNoSong, currentStrings().dmTitle)
                    else dms.sendSong(DmSong(t.id, t.title, t.artist, t.thumbnailUrl, t.url, t.durationSeconds))
                }
            }
        }
    }

    onceTextShown?.let { txt ->
        Dialog(onDismissRequest = { onceTextShown = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            val view = LocalView.current
            DisposableEffect(view) {
                (view.parent as? DialogWindowProvider)?.window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                onDispose { }
            }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f)).clickable { onceTextShown = null }.padding(28.dp), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.clip(RoundedCornerShape(20.dp)).background(colors.surface).padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(NexIcons.Lock, null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(txt, color = colors.textPrimary, fontSize = 17.sp, lineHeight = 24.sp)
                    Spacer(Modifier.height(14.dp))
                    Text(S.dmOnceTextNote, color = colors.textMuted, fontSize = 11.sp)
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = { onceTextShown = null }) { Text(S.dmClose, color = colors.textPrimary) }
                }
            }
        }
    }

    onceId?.let { oid ->
        Dialog(onDismissRequest = { onceId = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            // Foto sekali lihat: cegah tangkapan layar selama dibuka (hanya menghambat, tidak bisa menjamin).
            val view = LocalView.current
            DisposableEffect(view) {
                (view.parent as? DialogWindowProvider)?.window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                onDispose { }
            }
            var bmp by remember(oid) { mutableStateOf<android.graphics.Bitmap?>(null) }
            var failed by remember(oid) { mutableStateOf(false) }
            LaunchedEffect(oid) {
                val b = dms.loadOnceImage(oid)
                bmp = b
                failed = b == null
            }
            Box(Modifier.fillMaxSize().background(Color.Black).clickable { onceId = null }, contentAlignment = Alignment.Center) {
                val b = bmp
                when {
                    b != null -> Image(bitmap = b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    failed -> Text(S.dmImageGone, color = Color.White, fontSize = 14.sp)
                    else -> CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                }
            }
        }
    }

    viewerImage?.let { im ->
        Dialog(onDismissRequest = { viewerImage = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            var full by remember(im.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
            LaunchedEffect(im.id) { full = dms.loadImage(im.id) }
            Box(Modifier.fillMaxSize().background(Color.Black).clickable { viewerImage = null }, contentAlignment = Alignment.Center) {
                val b = full
                if (b != null) Image(bitmap = b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                else CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            }
        }
    }

    if (showStars) {
        var list by remember { mutableStateOf<List<DmMessage>?>(null) }
        LaunchedEffect(Unit) { list = dms.starred() }
        ModalBottomSheet(
            onDismissRequest = { showStars = false },
            containerColor = colors.background,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Text(S.dmStarred, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(10.dp))
                val l = list
                when {
                    l == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.textPrimary, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    }
                    l.isEmpty() -> Text(S.dmNoStarred, color = colors.textMuted, modifier = Modifier.padding(vertical = 24.dp))
                    else -> LazyColumn(Modifier.fillMaxWidth()) {
                        items(l, key = { it.seq }) { sm ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    showStars = false
                                    val idx = messages.indexOfFirst { it.seq == sm.seq }
                                    if (idx >= 0) flash(idx)
                                }.padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(if (sm.uid == myId) S.dmYou else shownPeer?.display.orEmpty(), color = colors.textMuted, fontSize = 11.sp)
                                    Text(dmPreview(sm), color = colors.textPrimary, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                }
                                Text(chatTime(sm.ts), color = colors.textMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    forwardMsg?.let { fm ->
        ModalBottomSheet(
            onDismissRequest = { forwardMsg = null },
            containerColor = colors.background,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Text(S.dmForwardTo, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(10.dp))
                val targets = inboxList.filter { it.uid != shownPeer?.uid }
                if (targets.isEmpty()) Text(S.dmEmptyTitle, color = colors.textMuted, modifier = Modifier.padding(vertical = 24.dp))
                else LazyColumn(Modifier.fillMaxWidth()) {
                    items(targets, key = { it.uid }) { c ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                forwardMsg = null
                                scope.launch {
                                    val err = dms.forward(c.uid, fm)
                                    if (err == null) NexNotifier.success(currentStrings().dmForwardDone, currentStrings().dmTitle) else NexNotifier.warning(dmErrText(err), currentStrings().dmTitle)
                                }
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ChatAvatar(c.uid, c.av, c.display.ifBlank { c.username }, 42.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(c.display.ifBlank { c.username }, color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    if (showDisappear) {
        AlertDialog(
            onDismissRequest = { showDisappear = false },
            containerColor = colors.surface,
            title = { Text(S.dmDisappear, color = colors.textPrimary) },
            text = {
                Column {
                    Text(S.dmDisappearBody, color = colors.textSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    listOf(0L to S.dmDisappearOff, 86_400L to S.dmDisappear24, 604_800L to S.dmDisappear7).forEach { (secs, label) ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                                showDisappear = false
                                scope.launch { dms.setDisappear(secs) }
                            }.padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, color = colors.textPrimary, modifier = Modifier.weight(1f), fontWeight = if (disappear == secs) FontWeight.Bold else FontWeight.Normal)
                            if (disappear == secs) Icon(NexIcons.Check, null, tint = colors.textPrimary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showDisappear = false }) { Text(S.dmCancel, color = colors.textPrimary) } }
        )
    }

    if (showNick) {
        var nick by remember { mutableStateOf(shownPeer?.uid?.let { dms.nickname(it) }.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showNick = false },
            containerColor = colors.surface,
            title = { Text(S.dmNickname, color = colors.textPrimary) },
            text = {
                Column {
                    Text(S.dmNicknameHint, color = colors.textSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.card).border(1.dp, colors.border, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
                        BasicTextField(
                            value = nick, onValueChange = { nick = it.take(24) }, singleLine = true,
                            textStyle = TextStyle(color = colors.textPrimary, fontSize = 15.sp), cursorBrush = SolidColor(colors.textPrimary),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { shownPeer?.uid?.let { dms.setNickname(it, nick) }; showNick = false }) { Text(S.dmSave, color = colors.textPrimary) } },
            dismissButton = { TextButton(onClick = { showNick = false }) { Text(S.dmCancel, color = colors.textPrimary) } }
        )
    }

    if (showTheme) {
        AlertDialog(
            onDismissRequest = { showTheme = false },
            containerColor = colors.surface,
            title = { Text(S.dmTheme, color = colors.textPrimary) },
            text = {
                val current = shownPeer?.uid?.let { dms.theme(it) } ?: 0
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    BUBBLE_THEMES.forEachIndexed { idx, c ->
                        Box(
                            Modifier.size(38.dp).clip(CircleShape).background(c ?: colors.textPrimary)
                                .border(if (idx == current) 3.dp else 1.dp, if (idx == current) colors.accent else colors.border, CircleShape)
                                .clickable { shownPeer?.uid?.let { dms.setTheme(it, idx) }; showTheme = false }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showTheme = false }) { Text(S.dmCancel, color = colors.textPrimary) } }
        )
    }

    deleteMsg?.let { m ->
        AlertDialog(
            onDismissRequest = { deleteMsg = null },
            containerColor = colors.surface,
            title = { Text(S.dmDeleteMsgTitle, color = colors.textPrimary) },
            text = { Text(S.dmDeleteMsgBody, color = colors.textSecondary) },
            confirmButton = { TextButton(onClick = { deleteMsg = null; scope.launch { dms.delete(m.seq) } }) { Text(S.dmDeleteAction, color = RED) } },
            dismissButton = { TextButton(onClick = { deleteMsg = null }) { Text(S.dmCancel, color = colors.textPrimary) } }
        )
    }
}

/* =============================== GELEMBUNG =============================== */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DmBubble(
    m: DmMessage,
    mine: Boolean,
    read: Boolean,
    myId: String,
    starred: Boolean,
    loadImage: suspend (String) -> android.graphics.Bitmap?,
    translation: String?,
    onHideTranslation: () -> Unit,
    onOpenImage: (DmImage) -> Unit,
    loadPreview: suspend (String) -> LinkPreview?,
    onOpenOnce: (DmMessage) -> Unit,
    onOpenOnceText: (DmMessage) -> Unit,
    highlight: Boolean,
    mineColor: Color?,
    onQuote: (Long) -> Unit,
    onSwipeReply: () -> Unit,
    onLong: () -> Unit,
    onRetry: () -> Unit,
    onReact: (String) -> Unit,
    onPlay: (DmSong) -> Unit
) {
    val colors = NexrifyAppTheme.colors
    val bg = if (mine) (mineColor ?: colors.textPrimary) else colors.card
    val fg = if (mine) (if (mineColor != null) Color.White else colors.background) else colors.textPrimary
    val sub = if (mine) fg.copy(alpha = 0.65f) else colors.textMuted
    val shape = RoundedCornerShape(
        topStart = 18.dp, topEnd = 18.dp,
        bottomStart = if (mine) 18.dp else 4.dp, bottomEnd = if (mine) 4.dp else 18.dp
    )
    var drag by remember { mutableStateOf(0f) }
    val trigger = with(LocalDensity.current) { 56.dp.toPx() }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp)
            .offset { IntOffset(drag.roundToInt(), 0) }
            .clip(RoundedCornerShape(12.dp))
            .background(if (highlight) colors.accent.copy(alpha = 0.22f) else Color.Transparent)
            .pointerInput(m.seq) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        drag = (drag + amount * 0.6f).coerceIn(0f, trigger * 1.4f)
                    },
                    onDragEnd = { if (drag >= trigger) onSwipeReply(); drag = 0f },
                    onDragCancel = { drag = 0f }
                )
            },
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start, modifier = Modifier.widthIn(max = 300.dp)) {
            Column(
                Modifier.clip(shape).background(bg)
                    .then(if (!mine) Modifier.border(1.dp, colors.border, shape) else Modifier)
                    .combinedClickable(
                        onClick = { if (m.status == SendStatus.FAILED) onRetry() },
                        onLongClick = onLong
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (m.deleted) {
                    Text(S.dmMessageDeleted, color = sub, fontStyle = FontStyle.Italic, fontSize = 14.sp)
                } else {
                    if (m.forwarded) Text("↪ ${S.dmForwarded}", color = sub, fontSize = 11.sp, fontStyle = FontStyle.Italic, modifier = Modifier.padding(bottom = 4.dp))
                    m.reply?.let { r ->
                        Row(
                            Modifier.padding(bottom = 6.dp).clip(RoundedCornerShape(8.dp)).background(fg.copy(alpha = 0.10f)).clickable { onQuote(r.seq) }.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.width(3.dp).height(26.dp).background(fg.copy(alpha = 0.5f)))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(if (r.uid == myId) S.dmYou else "", color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Text(r.text.ifBlank { S.dmMessageDeleted }, color = sub, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (m.onceText) DmOnceTextCard(m.onceViewed, mine, fg, sub) { onOpenOnceText(m) }
                    m.voice?.let { v -> VoiceBubble(v, mine) }
                    m.image?.let { im ->
                        if (im.once) DmOnceCard(im, mine, fg, sub) { onOpenOnce(m) }
                        else DmImageView(im, loadImage, { onOpenImage(im) }, onLong, if (m.text.isNotBlank()) 6.dp else 0.dp)
                    }
                    m.song?.let { s ->
                        Row(
                            Modifier.padding(bottom = if (m.text.isNotBlank()) 6.dp else 0.dp).clip(RoundedCornerShape(12.dp)).background(fg.copy(alpha = 0.10f)).clickable { onPlay(s) }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(fg.copy(alpha = 0.15f))) {
                                if (s.thumb.isNotBlank()) AsyncImage(model = s.thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f, fill = false)) {
                                Text(s.title, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (s.artist.isNotBlank()) Text(s.artist, color = sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.width(10.dp))
                            Box(Modifier.size(34.dp).clip(CircleShape).background(fg), contentAlignment = Alignment.Center) {
                                Icon(NexIcons.PlayArrow, S.dmSongPlay, tint = bg, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    if (m.text.isNotBlank()) Text(m.text, color = fg, fontSize = 15.sp, lineHeight = 20.sp)
                    translation?.let { TranslationBlock(it, fg, sub, onHideTranslation) }
                    if (m.text.isNotBlank() && !m.deleted) DmLinkCards(m.text, loadPreview, fg, sub)
                }
                Row(Modifier.align(Alignment.End).padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (starred) Text("★  ", color = sub, fontSize = 10.sp)
                    if (m.expires > 0 && !m.deleted) Text("⏳  ", color = sub, fontSize = 10.sp)
                    if (m.edited > 0 && !m.deleted) Text("${S.dmEdited}  ", color = sub, fontSize = 10.sp)
                    Text(chatTime(m.ts), color = sub, fontSize = 10.sp)
                    if (mine) {
                        Spacer(Modifier.width(4.dp))
                        when (m.status) {
                            SendStatus.SENDING -> Text("…", color = sub, fontSize = 11.sp)
                            SendStatus.FAILED -> Text("!", color = RED, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            SendStatus.SENT -> Text(if (read) "✓✓" else "✓", color = if (read) Color(0xFF3B82F6) else sub, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (m.status == SendStatus.FAILED) Text(S.dmFailedTap, color = RED, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
            if (m.reactions.isNotEmpty() && !m.deleted) {
                Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    m.reactions.entries.forEach { (emoji, users) ->
                        val me = users.contains(myId)
                        Row(
                            Modifier.clip(RoundedCornerShape(12.dp)).background(if (me) colors.accent.copy(alpha = 0.25f) else colors.card)
                                .border(1.dp, if (me) colors.accent else colors.border, RoundedCornerShape(12.dp))
                                .clickable { onReact(emoji) }.padding(horizontal = 7.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(emoji, fontSize = 13.sp)
                            if (users.size > 1) Text(" ${users.size}", color = colors.textSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DmImageView(img: DmImage, load: suspend (String) -> android.graphics.Bitmap?, onClick: () -> Unit, onLong: () -> Unit, bottomGap: androidx.compose.ui.unit.Dp) {
    val colors = NexrifyAppTheme.colors
    var bmp by remember(img.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var failed by remember(img.id) { mutableStateOf(false) }
    LaunchedEffect(img.id) {
        val b = load(img.id)
        bmp = b
        failed = b == null
    }
    val ratio = (img.w.toFloat() / img.h.coerceAtLeast(1).toFloat()).coerceIn(0.6f, 1.8f)
    Box(
        Modifier.padding(bottom = bottomGap).width(220.dp).aspectRatio(ratio).clip(RoundedCornerShape(12.dp))
            .background(colors.border.copy(alpha = 0.4f))
            .combinedClickable(enabled = true, onClick = { if (bmp != null) onClick() }, onLongClick = onLong),
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        when {
            b != null -> Image(bitmap = b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            failed -> Text(S.dmImageGone, color = colors.textMuted, fontSize = 12.sp)
            else -> CircularProgressIndicator(color = colors.textMuted, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        }
    }
}

/* =============================== KUNCI PIN =============================== */

/** Layar publik kotak masuk: dibungkus kunci PIN kalau pengguna mengaktifkannya. */
@Composable
fun DmInboxScreen(onBack: () -> Unit, onOpenChat: (String) -> Unit) {
    DmLockGate(onBack) { DmInboxContent(onBack, onOpenChat) }
}

/** Layar publik percakapan: dibungkus kunci PIN kalau pengguna mengaktifkannya. */
@Composable
fun DmThreadScreen(viewModel: MusicViewModel, username: String, onBack: () -> Unit) {
    val dms = remember { DmManager.getInstance() }
    DmLockGate(onBack, dms.lockedUidForUsername(username)) { DmThreadContent(viewModel, username, onBack) }
}

@Composable
private fun DmLockGate(onBack: () -> Unit, forceUid: String? = null, content: @Composable () -> Unit) {
    val colors = NexrifyAppTheme.colors
    val dms = remember { DmManager.getInstance() }
    var locked by remember { mutableStateOf(dms.shouldLock() || (forceUid != null && dms.lockEnabled() && !dms.chatUnlocked(forceUid))) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var stoppedAt = 0L
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) stoppedAt = System.currentTimeMillis()
            if (event == Lifecycle.Event.ON_START && stoppedAt > 0L && dms.lockEnabled() && System.currentTimeMillis() - stoppedAt > 30_000L) {
                locked = true
                stoppedAt = 0L
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    if (locked) {
        Column(Modifier.fillMaxSize().background(colors.background)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TopIcon(NexIcons.ArrowBack, onBack)
                Text(S.dmTitle, style = Typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary, modifier = Modifier.padding(start = 6.dp))
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PinPad(
                        title = S.dmPinEnter, error = error,
                        onComplete = { pin ->
                            if (dms.checkPin(pin)) { dms.touchLock(); forceUid?.let { dms.markChatUnlocked(it) }; error = null; locked = false } else { error = currentStrings().dmPinWrong }
                        }
                    )
                    TextButton(onClick = { confirmReset = true }) { Text(S.dmForgotPin, color = colors.textMuted) }
                }
            }
            if (confirmReset) {
                AlertDialog(
                    onDismissRequest = { confirmReset = false },
                    containerColor = colors.surface,
                    title = { Text(S.dmForgotPinTitle, color = colors.textPrimary) },
                    text = { Text(S.dmForgotPinBody, color = colors.textSecondary) },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmReset = false
                            dms.removePin()
                            AccountManager.getInstance().clearLocal()
                            onBack()
                        }) { Text(S.dmForgotPinTitle, color = RED) }
                    },
                    dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(S.dmCancel, color = colors.textPrimary) } }
                )
            }
        }
    } else {
        LaunchedEffect(Unit) {
            while (true) { dms.touchLock(); delay(10_000) }
        }
        DisposableEffect(Unit) { onDispose { dms.touchLock() } }
        content()
    }
}

/** Papan angka 4 digit. Memanggil [onComplete] begitu digit keempat dimasukkan lalu mengosongkan isiannya. */
@Composable
private fun PinPad(title: String, error: String?, onComplete: (String) -> Unit) {
    val colors = NexrifyAppTheme.colors
    var pin by remember { mutableStateOf("") }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(NexIcons.Lock, null, tint = colors.textPrimary, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Spacer(Modifier.height(6.dp))
        Text(error.orEmpty(), color = RED, fontSize = 12.sp, modifier = Modifier.height(16.dp))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(4) { i ->
                Box(
                    Modifier.size(14.dp).clip(CircleShape)
                        .background(if (i < pin.length) colors.textPrimary else Color.Transparent)
                        .border(1.5.dp, colors.textPrimary, CircleShape)
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "<")).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                row.forEach { key ->
                    Box(
                        Modifier.size(68.dp).clip(CircleShape)
                            .background(if (key.isEmpty()) Color.Transparent else colors.card)
                            .clickable(enabled = key.isNotEmpty()) {
                                if (key == "<") pin = pin.dropLast(1)
                                else if (pin.length < 4) {
                                    pin += key
                                    if (pin.length == 4) { val done = pin; pin = ""; onComplete(done) }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (key == "<") Icon(NexIcons.ArrowBack, null, tint = colors.textPrimary, modifier = Modifier.size(22.dp))
                        else Text(key, color = colors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

/** Catatan panggilan suara di tengah percakapan (selesai, tak terjawab, atau ditolak). */
@Composable
private fun DmCallRow(m: DmMessage, mine: Boolean) {
    val colors = NexrifyAppTheme.colors
    val c = m.call ?: return
    val missed = c.status == "missed" && !mine
    val text = when (c.status) {
        "ended" -> S.callLogEnded("%d:%02d".format(c.dur / 60, c.dur % 60))
        "declined" -> if (mine) S.callLogDeclinedOut else S.callLogDeclinedIn
        else -> if (mine) S.callLogMissedOut else S.callLogMissedIn
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.Center) {
        Row(
            Modifier.clip(RoundedCornerShape(16.dp)).background(colors.card).border(1.dp, colors.border, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(NexIcons.Phone, null, tint = if (missed) RED else colors.textMuted, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, color = if (missed) RED else colors.textSecondary, fontSize = 12.sp)
            Spacer(Modifier.width(8.dp))
            Text(chatTime(m.ts), color = colors.textMuted, fontSize = 10.sp)
        }
    }
}

/** Pengganti foto sekali lihat: isinya tidak pernah ikut terkirim ke daftar pesan, hanya status terbuka atau belum. */
@Composable
private fun DmOnceCard(img: DmImage, mine: Boolean, fg: Color, sub: Color, onOpen: () -> Unit) {
    val label = when {
        img.viewed -> S.dmOnceOpened
        mine -> S.dmOnceSent
        else -> S.dmOnceTap
    }
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(fg.copy(alpha = 0.10f))
            .clickable(enabled = !mine && !img.viewed, onClick = onOpen).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(NexIcons.Lock, null, tint = if (img.viewed) sub else fg, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = if (img.viewed) sub else fg, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

private val URL_RE = Regex("""https://[^\s]+""")
private val PROFILE_RE = Regex("""^👤 @([A-Za-z0-9_.]{2,30})$""")
private val PREVIEW_HOSTS = listOf("youtube.com", "youtu.be", "open.spotify.com", "tiktok.com", "soundcloud.com")

/** Kartu otomatis di bawah teks: playlist Nexrify, profil yang dibagikan, atau pratinjau tautan YouTube/Spotify/TikTok/SoundCloud. */
@Composable
private fun DmLinkCards(text: String, loadPreview: suspend (String) -> LinkPreview?, fg: Color, sub: Color) {
    val context = LocalContext.current
    PROFILE_RE.find(text.trim())?.let { m ->
        val name = m.groupValues[1]
        SimpleLinkCard("👤", S.dmProfileCard, "@$name  ·  ${S.dmProfileOpen}", fg, sub) {
            id.nexrify.music.data.chat.ChatNav.profileRequest.value = name
        }
        return
    }
    PlaylistShareNav.parseText(text)?.let { pid ->
        SimpleLinkCard("🎶", S.dmPlaylistCard, S.dmPlaylistOpen, fg, sub) { PlaylistShareNav.request.value = pid }
        return
    }
    val url = URL_RE.find(text)?.value?.trimEnd('.', ',', ')', '!', '?') ?: return
    val host = runCatching { android.net.Uri.parse(url).host.orEmpty().lowercase() }.getOrDefault("")
    if (PREVIEW_HOSTS.none { host == it || host.endsWith(".$it") }) return
    var pv by remember(url) { mutableStateOf<LinkPreview?>(null) }
    LaunchedEffect(url) { pv = loadPreview(url) }
    val p = pv ?: return
    Row(
        Modifier.padding(top = 6.dp).clip(RoundedCornerShape(12.dp)).background(fg.copy(alpha = 0.10f))
            .clickable { runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(p.url.ifBlank { url })).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (p.thumb.isNotBlank()) {
            AsyncImage(model = p.thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f, fill = false)) {
            Text(p.site, color = sub, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Text(p.title, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (p.author.isNotBlank()) Text(p.author, color = sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SimpleLinkCard(emoji: String, title: String, sub: String, fg: Color, subColor: Color, onClick: () -> Unit) {
    Row(
        Modifier.padding(top = 6.dp).clip(RoundedCornerShape(12.dp)).background(fg.copy(alpha = 0.10f)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 22.sp)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, color = subColor, fontSize = 11.sp)
        }
    }
}

/** Pengganti pesan teks sekali lihat: isinya tidak ada di daftar pesan, hanya status terbuka atau belum. */
@Composable
private fun DmOnceTextCard(viewed: Boolean, mine: Boolean, fg: Color, sub: Color, onOpen: () -> Unit) {
    val label = when {
        viewed -> S.dmOnceTextOpened
        mine -> S.dmOnceTextSent
        else -> S.dmOnceTextTap
    }
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(fg.copy(alpha = 0.10f))
            .clickable(enabled = !mine && !viewed, onClick = onOpen).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(NexIcons.Lock, null, tint = if (viewed) sub else fg, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = if (viewed) sub else fg, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

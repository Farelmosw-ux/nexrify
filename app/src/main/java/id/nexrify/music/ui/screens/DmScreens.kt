package id.nexrify.music.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.zIndex
import id.nexrify.music.ui.components.ChatProfileSheet
import id.nexrify.music.ui.components.DmSharePicker
import id.nexrify.music.ui.components.EditProfileDialog
import id.nexrify.music.ui.components.NexAvatar
import id.nexrify.music.ui.components.PersonTags
import id.nexrify.music.ui.components.TitlePickerSheet
import id.nexrify.music.ui.components.rememberChatProfiles
import id.nexrify.music.ui.components.rememberMyProfile
import androidx.compose.ui.window.DialogProperties
import id.nexrify.music.data.chat.DmContact
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import id.nexrify.music.data.account.AccountManager
import id.nexrify.music.data.chat.CHAT_EMOJIS_QUICK
import id.nexrify.music.data.chat.CHAT_MAX_LENGTH
import id.nexrify.music.data.chat.DmConversation
import id.nexrify.music.data.chat.DmManager
import id.nexrify.music.data.chat.DmMessage
import id.nexrify.music.data.chat.DmPeer
import id.nexrify.music.data.chat.DmSong
import id.nexrify.music.data.chat.ChatManager
import id.nexrify.music.data.chat.SendStatus
import id.nexrify.music.data.model.Track
import id.nexrify.music.i18n.S
import id.nexrify.music.i18n.currentStrings
import id.nexrify.music.ui.components.ChatAvatar
import id.nexrify.music.ui.components.ChatDateHeader
import id.nexrify.music.ui.components.NexNotifier
import android.view.WindowManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
/** Centang terbaca hijau terang seperti semula agar jelas terlihat. */
private val READ_GREEN = Color(0xFF22C55E)

private fun readHidden(c: android.content.Context, key: String): Set<Long> =
    c.getSharedPreferences("nexrify_dm_hidden", android.content.Context.MODE_PRIVATE).getStringSet(key, emptySet()).orEmpty()
        .mapNotNull { it.toLongOrNull() }.toSet()

private fun writeHidden(c: android.content.Context, key: String, set: Set<Long>) {
    c.getSharedPreferences("nexrify_dm_hidden", android.content.Context.MODE_PRIVATE).edit()
        .putStringSet(key, set.map { it.toString() }.toSet()).apply()
}

private fun dmSameGroup(a: DmMessage?, b: DmMessage?): Boolean =
    a != null && b != null && a.call == null && b.call == null && a.uid == b.uid &&
        chatDayKey(a.ts) == chatDayKey(b.ts) && kotlin.math.abs(b.ts - a.ts) < 3 * 60_000L

private val BUBBLE_THEMES = listOf(null, Color(0xFF2563EB), Color(0xFF16A34A), Color(0xFF9333EA), Color(0xFFE11D48), Color(0xFFEA580C))

private val dmDrafts = HashMap<String, String>()

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
    var showContacts by remember { mutableStateOf(false) }
    var showMainMenu by remember { mutableStateOf(false) }
    var showAllStars by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<DmConversation?>(null) }
    var confirmHide by remember { mutableStateOf<DmConversation?>(null) }
    var showArchive by remember { mutableStateOf(false) }
    var showRequests by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showEditAccount by remember { mutableStateOf(false) }
    var showTitleSheet by remember { mutableStateOf(false) }
    var showStatus by remember { mutableStateOf(false) }
    var showLocked by remember { mutableStateOf(false) }
    var askFolderPin by remember { mutableStateOf(false) }
    var folderPinError by remember { mutableStateOf<String?>(null) }
    var lockFlow by remember { mutableStateOf(0) }
    var lockFirst by remember { mutableStateOf("") }
    var lockError by remember { mutableStateOf<String?>(null) }
    val localVer by dms.localVersion.collectAsState()
    val meUser by AccountManager.getInstance().user.collectAsState()
    val myProfile = rememberMyProfile()
    val myName = meUser?.username.orEmpty()
    val myDisplayNick = meUser?.display?.ifBlank { meUser?.username.orEmpty() }.orEmpty()
    val noteDisplayLabel = if (myDisplayNick.isNotBlank()) "$myDisplayNick (Anda)" else S.dmNote

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

    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize()) {
            // Header: Nexrify + titik 3 (Berbintang & Pengaturan), tanpa kunci pesan pribadi & pesan baru
            Row(Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TopIcon(NexIcons.ArrowBack) {
                    if (showArchive || showRequests || showLocked) {
                        showArchive = false; showRequests = false; showLocked = false
                    } else onBack()
                }
                Text(
                    text = if (showRequests) S.dmRequests else if (showLocked) S.dmLockedFolder else if (showArchive) S.dmArchived else "Nexrify",
                    style = Typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f).padding(start = 6.dp)
                )
                Box {
                    TopIcon(NexIcons.MoreVert) { showMainMenu = true }
                    DropdownMenu(expanded = showMainMenu, onDismissRequest = { showMainMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Berbintang", color = colors.textPrimary) },
                            onClick = { showMainMenu = false; showAllStars = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Pengaturan", color = colors.textPrimary) },
                            onClick = { showMainMenu = false; showSettingsSheet = true }
                        )
                    }
                }
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
                        Modifier.clip(RoundedCornerShape(24.dp)).background(colors.textPrimary).clickable { showContacts = true }.padding(horizontal = 22.dp, vertical = 12.dp)
                    ) { Text("Pilih Kontak", color = colors.background, fontWeight = FontWeight.Bold) }
                }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp)) {
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
                    // Catatan pribadi default jika belum ada di inbox: <profil> nickname (Anda)
                    if (!showArchive && !showRequests && !showLocked && !hasNote && myName.isNotBlank()) item(key = "note-row") {
                        Row(Modifier.fillMaxWidth().clickable { onOpenChat(myName) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (meUser != null) NexAvatar(meUser!!, 50.dp)
                            else Box(Modifier.size(50.dp).clip(CircleShape).background(colors.card).border(1.dp, colors.border, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(NexIcons.Person, null, tint = colors.textPrimary, modifier = Modifier.size(22.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(noteDisplayLabel, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
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
                            if (c.note && meUser != null) {
                                NexAvatar(meUser!!, 50.dp)
                            } else {
                                ChatAvatar(c.uid, c.av, c.display.ifBlank { c.username }, 50.dp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (c.note) noteDisplayLabel else dmName(dms.nickname(c.uid).ifBlank { c.display }, c.username, localVer),
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

        // Tombol melayang di kanan bawah untuk masuk ke Daftar Kontak
        Row(
            Modifier.align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(20.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(colors.textPrimary)
                .clickable { showContacts = true }
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(NexIcons.Group, "Kontak", tint = colors.background, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Kontak", color = colors.background, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }

    // Halaman Daftar Kontak (Pilih Kontak + Kontak Baru + Daftar Kontak Di Nexrify)
    if (showContacts) {
        ContactsScreen(
            onBack = { showContacts = false },
            onOpenChat = { uname -> showContacts = false; onOpenChat(uname) }
        )
    }

    // Sheet Berbintang (semua pesan berbintang dari seluruh obrolan)
    if (showAllStars) {
        var allList by remember { mutableStateOf<List<Pair<DmConversation, DmMessage>>?>(null) }
        LaunchedEffect(Unit) { allList = dms.allStarred() }
        ModalBottomSheet(
            onDismissRequest = { showAllStars = false },
            containerColor = colors.background,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Text("Berbintang", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(10.dp))
                val l = allList
                when {
                    l == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.textPrimary, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    }
                    l.isEmpty() -> Text(S.dmNoStarred, color = colors.textMuted, modifier = Modifier.padding(vertical = 24.dp))
                    else -> LazyColumn(Modifier.fillMaxWidth().height(380.dp)) {
                        items(l, key = { "${it.first.uid}_${it.second.seq}" }) { (conv, sm) ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    showAllStars = false
                                    onOpenChat(conv.username)
                                }.padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ChatAvatar(conv.uid, conv.av, conv.display.ifBlank { conv.username }, 38.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        dmName(dms.nickname(conv.uid).ifBlank { conv.display }, conv.username, localVer),
                                        color = colors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                                    )
                                    Text(dmPreview(sm), color = colors.textPrimary, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                Text(chatTime(sm.ts), color = colors.textMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Sheet Pengaturan (Username, Profil, Title, Status, Kunci PIN, dll.)
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            containerColor = colors.background,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
                Text("Pengaturan", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(12.dp))
                // Kartu info akun sendiri
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(16.dp)).background(colors.card)
                        .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                        .clickable { showSettingsSheet = false; showEditAccount = true }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (meUser != null) NexAvatar(meUser!!, 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(meUser?.display?.ifBlank { meUser?.username.orEmpty() }.orEmpty(), color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("@${meUser?.username.orEmpty()}", color = colors.textMuted, fontSize = 13.sp)
                    }
                    Icon(NexIcons.Edit, null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.height(10.dp))
                SheetRow(NexIcons.AccountCircle, "Profil & Username") { showSettingsSheet = false; showEditAccount = true }
                if (myProfile.value != null) {
                    SheetRow(NexIcons.Crown, "Title Profil") { showSettingsSheet = false; showTitleSheet = true }
                }
                SheetRow(NexIcons.Edit, S.dmMyStatus) { showSettingsSheet = false; showStatus = true }
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
                HorizontalDivider(color = colors.border, modifier = Modifier.padding(vertical = 8.dp))
                Text(S.dmLockTitle, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 20.dp))
                Text(S.dmLockNote, color = colors.textMuted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
                if (!dms.lockEnabled()) {
                    SheetRow(NexIcons.Lock, S.dmLockOn) { showSettingsSheet = false; lockError = null; lockFlow = 1 }
                } else {
                    SheetRow(NexIcons.Lock, S.dmLockChange) { showSettingsSheet = false; lockError = null; lockFlow = 4 }
                    SheetRow(NexIcons.DeleteOutline, S.dmLockOff, danger = true) { showSettingsSheet = false; lockError = null; lockFlow = 3 }
                }
            }
        }
    }

    if (showEditAccount) {
        EditProfileDialog(onDismiss = { showEditAccount = false })
    }

    val mp = myProfile.value
    if (showTitleSheet && mp != null) {
        TitlePickerSheet(
            profile = mp,
            onDismiss = { showTitleSheet = false },
            onSaved = { myProfile.value = it },
            onApplyCreator = { showTitleSheet = false }
        )
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
                        showSettingsSheet = true
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

/* =============================== DAFTAR KONTAK & KONTAK BARU =============================== */

@Composable
private fun ContactsScreen(onBack: () -> Unit, onOpenChat: (String) -> Unit) {
    val colors = NexrifyAppTheme.colors
    val dms = remember { DmManager.getInstance() }
    val chat = remember { ChatManager.getInstance() }
    val scope = rememberCoroutineScope()
    val localVer by dms.localVersion.collectAsState()
    val myName = AccountManager.getInstance().user.value?.username.orEmpty()
    val userNotFoundMsg = S.dmUserNotFound

    var searching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var usernameInput by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var profilePreviewUser by remember { mutableStateOf<String?>(null) }

    val allContacts = remember(localVer) { dms.contacts() }
    val filteredContacts = remember(allContacts, searchQuery) {
        val q = searchQuery.trim().removePrefix("@").lowercase()
        if (q.isEmpty()) allContacts
        else allContacts.filter {
            it.firstName.lowercase().contains(q) ||
                it.lastName.lowercase().contains(q) ||
                it.fullName.lowercase().contains(q) ||
                it.username.lowercase().contains(q)
        }
    }

    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(
            Modifier.fillMaxSize().background(colors.background).statusBarsPadding().navigationBarsPadding()
        ) {
            // Header: Pilih Kontak + Tombol Cari + Jumlah Kontak
            Row(
                Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TopIcon(NexIcons.ArrowBack, onBack)
                Column(Modifier.weight(1f).padding(start = 6.dp)) {
                    Text("Pilih Kontak", style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
                    Text("${allContacts.size} Kontak", color = colors.textMuted, fontSize = 12.sp)
                }
                TopIcon(NexIcons.Search) {
                    searching = !searching
                    if (!searching) searchQuery = ""
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))

            if (searching) {
                Box(Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.card)
                            .border(1.dp, colors.border, RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        if (searchQuery.isEmpty()) Text("Cari username / nama depan / belakang...", color = colors.textMuted, fontSize = 14.sp)
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it.take(32) },
                            singleLine = true,
                            textStyle = TextStyle(color = colors.textPrimary, fontSize = 14.sp),
                            cursorBrush = SolidColor(colors.textPrimary),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Form Kontak Baru
                item(key = "new-contact-card") {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.card)
                            .border(1.dp, colors.border, RoundedCornerShape(18.dp)).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Kontak Baru", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        ContactInputField(value = firstName, hint = "Nama Depan", onValueChange = { firstName = it.take(24) })
                        ContactInputField(value = lastName, hint = "Nama Belakang (Opsional)", onValueChange = { lastName = it.take(24) })
                        ContactInputField(value = usernameInput, hint = "Nama Pengguna / Username", onValueChange = { usernameInput = it.take(24) })
                        Spacer(Modifier.height(2.dp))
                        Box(
                            Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(24.dp))
                                .background(if (saving) colors.border else colors.textPrimary)
                                .clickable(enabled = !saving) {
                                    val un = usernameInput.trim().removePrefix("@").lowercase()
                                    val fn = firstName.trim()
                                    val ln = lastName.trim()
                                    if (fn.isEmpty() || un.isEmpty()) {
                                        NexNotifier.warning("Nama depan dan username wajib diisi", "Kontak")
                                        return@clickable
                                    }
                                    if (un.equals(myName, true)) {
                                        NexNotifier.warning("Tidak bisa menambahkan akun sendiri", "Kontak")
                                        return@clickable
                                    }
                                    saving = true
                                    scope.launch {
                                        val prof = runCatching { chat.profile(un) }.getOrNull()
                                        saving = false
                                        if (prof == null) {
                                            NexNotifier.warning(userNotFoundMsg, "Kontak")
                                        } else {
                                            dms.saveContact(prof.uid, prof.username, fn, ln, prof.av)
                                            firstName = ""; lastName = ""; usernameInput = ""
                                            NexNotifier.success("Kontak disimpan", "Kontak")
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (saving) CircularProgressIndicator(color = colors.background, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                            else Text("Simpan", color = colors.background, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }

                // Judul Daftar Kontak Di Nexrify
                item(key = "contact-list-header") {
                    Text(
                        "Daftar Kontak Di Nexrify",
                        color = colors.textSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )
                }

                if (filteredContacts.isEmpty()) {
                    item(key = "contact-empty") {
                        Text(
                            if (allContacts.isEmpty()) "Belum ada kontak tersimpan" else "Kontak tidak ditemukan",
                            color = colors.textMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
                        )
                    }
                } else {
                    items(filteredContacts, key = { it.uid }) { contact ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.card)
                                .border(1.dp, colors.border.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                                .clickable { onOpenChat(contact.username) }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Tekan foto profil -> munculkan profil lengkap
                            Box(Modifier.clip(CircleShape).clickable { profilePreviewUser = contact.username }) {
                                ChatAvatar(contact.uid, contact.av, contact.fullName, 46.dp)
                            }
                            Spacer(Modifier.width(12.dp))
                            // Tekan nama/baris kontak -> masuk ke chat
                            Column(Modifier.weight(1f)) {
                                Text(contact.fullName, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("@${contact.username}", color = colors.textMuted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        profilePreviewUser?.let { u ->
            ChatProfileSheet(
                username = u,
                isMe = false,
                isMuted = false,
                onMention = { profilePreviewUser = null },
                onToggleMute = {},
                onDismiss = { profilePreviewUser = null },
                showMention = false
            )
        }
    }
}

@Composable
private fun ContactInputField(value: String, hint: String, onValueChange: (String) -> Unit) {
    val colors = NexrifyAppTheme.colors
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.background)
            .border(1.dp, colors.border, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        if (value.isEmpty()) Text(hint, color = colors.textMuted, fontSize = 14.sp)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = colors.textPrimary, fontSize = 14.sp),
            cursorBrush = SolidColor(colors.textPrimary),
            modifier = Modifier.fillMaxWidth()
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

/* =============================== PERCAKAPAN PRIBADI =============================== */

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun DmThreadContent(viewModel: MusicViewModel, username: String, onBack: () -> Unit) {
    val colors = NexrifyAppTheme.colors
    val dms = remember { DmManager.getInstance() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val myId = AccountManager.getInstance().user.value?.id.orEmpty()
    val meUser by AccountManager.getInstance().user.collectAsState()

    var peer by remember { mutableStateOf<DmPeer?>(null) }
    var peerFailed by remember { mutableStateOf(false) }

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
    val hiddenCtx = LocalContext.current
    val hiddenKey = "h_" + (peer?.uid ?: "")
    var hiddenSeqs by remember(hiddenKey) { mutableStateOf(readHidden(hiddenCtx, hiddenKey)) }
    val messages = (if (shownPeer != null && livePeer?.uid == shownPeer.uid) liveMessages else emptyList())
        .let { all -> if (hiddenSeqs.isEmpty()) all else all.filter { it.seq <= 0L || it.seq !in hiddenSeqs } }

    LaunchedEffect(Unit) { dms.errors.collect { NexNotifier.warning(dmErrText(it), currentStrings().dmTitle) } }

    var input by remember { mutableStateOf(TextFieldValue("")) }
    var replyTo by remember { mutableStateOf<DmMessage?>(null) }
    var editing by remember { mutableStateOf<DmMessage?>(null) }
    var menuMsg by remember { mutableStateOf<DmMessage?>(null) }
    var deleteMsg by remember { mutableStateOf<DmMessage?>(null) }
    var showMore by remember { mutableStateOf(false) }
    var showMoreSub by remember { mutableStateOf(false) }
    var showContactProfile by remember { mutableStateOf(false) }
    var showMediaLinks by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var confirmClearChat by remember { mutableStateOf(false) }
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
    var searching by remember { mutableStateOf(false) }
    var searchQ by remember { mutableStateOf("") }
    var searchRes by remember { mutableStateOf<List<DmMessage>>(emptyList()) }
    val listState = rememberLazyListState()

    val currentConv = inboxList.firstOrNull { it.uid == shownPeer?.uid }
    val convMuted = currentConv?.muted == true
    val displayNick = if (isNote) {
        val myNick = meUser?.display?.ifBlank { meUser?.username.orEmpty() }.orEmpty()
        if (myNick.isNotBlank()) "$myNick (Anda)" else S.dmNote
    } else {
        dmName(shownPeer?.uid?.let { dms.nickname(it) }.orEmpty().ifBlank { shownPeer?.display.orEmpty() }, shownPeer?.username ?: username, localVer)
    }

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

    fun clearAllLocalMessages() {
        val allSeqs = messages.map { it.seq }.filter { it > 0L }.toSet()
        hiddenSeqs = hiddenSeqs + allSeqs
        writeHidden(hiddenCtx, hiddenKey, hiddenSeqs)
        NexNotifier.info("Obrolan dibersihkan", currentStrings().dmTitle)
    }

    Column(Modifier.fillMaxSize().background(colors.background)) {
        // ---- header ----
        Row(Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TopIcon(NexIcons.ArrowBack, onBack)
            // Profil/nickname ditekan -> otomatis masuk ke profil lengkap
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(enabled = shownPeer != null && !isNote) {
                    showContactProfile = true
                }.padding(vertical = 2.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isNote && meUser != null) {
                    NexAvatar(meUser!!, 38.dp)
                } else if (shownPeer != null) {
                    ChatAvatar(shownPeer.uid, shownPeer.av, displayNick, 38.dp)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        displayNick,
                        style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
            }
            if (!isNote && !blocked && !requestIn && !pending && shownPeer != null) TopIcon(NexIcons.Phone) { placeCall() }
            Box {
                TopIcon(NexIcons.MoreVert) { showMoreSub = false; showMore = true }
                DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false; showMoreSub = false }) {
                    if (!showMoreSub) {
                        if (!isNote && shownPeer != null) {
                            DropdownMenuItem(
                                text = { Text("Lihat Kontak", color = colors.textPrimary) },
                                onClick = { showMore = false; showContactProfile = true }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Cari", color = colors.textPrimary) },
                            onClick = { showMore = false; searching = !searching; if (!searching) { searchQ = ""; searchRes = emptyList() } }
                        )
                        DropdownMenuItem(
                            text = { Text("Media & tautan", color = colors.textPrimary) },
                            onClick = { showMore = false; showMediaLinks = true }
                        )
                        if (!isNote && shownPeer != null) {
                            DropdownMenuItem(
                                text = { Text(if (convMuted) "Bunyikan Notifikasi" else "Senyapkan Notifikasi", color = colors.textPrimary) },
                                onClick = {
                                    showMore = false
                                    scope.launch {
                                        dms.inboxAction("dm_mute", shownPeer.uid, !convMuted)
                                        NexNotifier.info(if (convMuted) "Notifikasi dibunyikan" else "Notifikasi disenyapkan", currentStrings().dmTitle)
                                    }
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Lainnya", color = colors.textPrimary, modifier = Modifier.weight(1f))
                                    Icon(NexIcons.ChevronRight, null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
                                }
                            },
                            onClick = { showMoreSub = true }
                        )
                    } else {
                        if (!isNote && shownPeer != null) {
                            DropdownMenuItem(
                                text = { Text("Laporkan", color = RED) },
                                onClick = { showMore = false; showMoreSub = false; showReportDialog = true }
                            )
                            DropdownMenuItem(
                                text = { Text(if (blocked) "Buka Blokir" else "Blokir", color = RED) },
                                onClick = {
                                    showMore = false; showMoreSub = false
                                    scope.launch {
                                        val ok = dms.setBlock(shownPeer.uid, !blocked)
                                        if (ok) NexNotifier.info(if (blocked) currentStrings().dmUnblockedDone else currentStrings().dmBlockedDone, currentStrings().dmTitle)
                                    }
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Bersihkan Obrolan", color = colors.textPrimary) },
                            onClick = { showMore = false; showMoreSub = false; confirmClearChat = true }
                        )
                        if (!isNote && shownPeer != null) {
                            DropdownMenuItem(
                                text = { Text("Musik Bareng", color = colors.textPrimary) },
                                onClick = {
                                    showMore = false; showMoreSub = false
                                    scope.launch {
                                        val mb = MubarManager.getInstance()
                                        try {
                                            if (mb.room.value == null) mb.create(viewModel.currentTrack.value)
                                            if (mb.room.value == null) {
                                                NexNotifier.warning(currentStrings().dmGenericErr, currentStrings().dmTitle)
                                            } else {
                                                mb.invite(shownPeer.username)
                                                NexNotifier.success(currentStrings().dmMubarSent(displayNick), currentStrings().dmTitle)
                                                mb.openSheet()
                                            }
                                        } catch (e: AccountError) {
                                            NexNotifier.warning(dmErrText(e.code), currentStrings().dmTitle)
                                        } catch (_: Throwable) {
                                            NexNotifier.warning(currentStrings().dmNoNetworkErr, currentStrings().dmTitle)
                                        }
                                    }
                                }
                            )
                        }
                    }
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
                        val nextMsg = messages.getOrNull(i + 1)
                        val groupedPrev = prev != null && dmSameGroup(prev, m)
                        val groupedNext = dmSameGroup(m, nextMsg)
                        if (m.call != null) DmCallRow(m, m.uid == myId, onRecordVoice = { if (!recording) startVoice() }, onCallBack = { placeCall() }) else DmBubble(
                            firstInGroup = !groupedPrev, lastInGroup = !groupedNext,
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
                Text(S.dmRequestBanner(displayNick), color = colors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
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
                            if (editing != null) S.dmEditing else (if (banner.uid == myId) S.dmYou else displayNick),
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
            } else Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(26.dp)).background(colors.card).padding(start = 18.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f).padding(vertical = 9.dp)) {
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
                    if (editing == null) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).clickable(enabled = !sendingImage) { showAttach = true },
                            contentAlignment = Alignment.Center
                        ) {
                            if (sendingImage) CircularProgressIndicator(color = colors.textPrimary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                            else Icon(NexIcons.Add, S.dmPhoto, tint = colors.textSecondary, modifier = Modifier.size(24.dp))
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                val canSend = input.text.isNotBlank()
                val micMode = !canSend && editing == null
                Box(
                    Modifier.size(50.dp).clip(CircleShape).background(colors.textPrimary)
                        .clickable(enabled = canSend || micMode) { if (micMode) startVoice() else submit() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(if (micMode) NexIcons.Mic else if (editing != null) NexIcons.Check else NexIcons.Send, null, tint = colors.background, modifier = Modifier.size(24.dp))
                }
            }
        }
    }

    // ---- Profil Lengkap Kontak (saat nickname/profil header atau Lihat Kontak ditekan) ----
    if (showContactProfile && shownPeer != null && !isNote) {
        ContactDetailScreen(
            peer = shownPeer,
            nickname = displayNick,
            isMuted = convMuted,
            isBlocked = blocked,
            isChatLocked = dms.isChatLocked(shownPeer.uid),
            onBack = { showContactProfile = false },
            onCall = { showContactProfile = false; placeCall() },
            onOpenMediaLinks = { showContactProfile = false; showMediaLinks = true },
            onToggleMute = {
                scope.launch {
                    dms.inboxAction("dm_mute", shownPeer.uid, !convMuted)
                    NexNotifier.info(if (convMuted) "Notifikasi diaktifkan" else "Notifikasi disenyapkan", currentStrings().dmTitle)
                }
            },
            onToggleLock = {
                if (dms.isChatLocked(shownPeer.uid)) {
                    dms.setChatLocked(shownPeer.uid, shownPeer.username, false)
                    NexNotifier.info("Kunci obrolan dimatikan", currentStrings().dmTitle)
                } else if (!dms.lockEnabled()) {
                    NexNotifier.info("Atur kunci PIN terlebih dahulu di Pengaturan", currentStrings().dmTitle)
                } else {
                    dms.setChatLocked(shownPeer.uid, shownPeer.username, true)
                    NexNotifier.info("Obrolan dikunci", currentStrings().dmTitle)
                }
            },
            onClearChat = { showContactProfile = false; confirmClearChat = true },
            onToggleBlock = {
                scope.launch {
                    val ok = dms.setBlock(shownPeer.uid, !blocked)
                    if (ok) NexNotifier.info(if (blocked) currentStrings().dmUnblockedDone else currentStrings().dmBlockedDone, currentStrings().dmTitle)
                }
            },
            onReport = { showContactProfile = false; showReportDialog = true }
        )
    }

    // ---- Sheet Media & Tautan ----
    if (showMediaLinks) {
        MediaAndLinksSheet(
            messages = messages,
            loadImage = { id -> dms.loadImage(id) },
            onOpenImage = { viewerImage = it },
            onDismiss = { showMediaLinks = false }
        )
    }

    if (confirmClearChat) {
        AlertDialog(
            onDismissRequest = { confirmClearChat = false },
            containerColor = colors.surface,
            title = { Text("Bersihkan Obrolan", color = colors.textPrimary) },
            text = { Text("Semua pesan di obrolan ini akan dibersihkan dari perangkatmu.", color = colors.textSecondary) },
            confirmButton = {
                TextButton(onClick = { confirmClearChat = false; clearAllLocalMessages() }) {
                    Text("Bersihkan", color = RED)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearChat = false }) { Text(S.dmCancel, color = colors.textPrimary) }
            }
        )
    }

    if (showReportDialog && shownPeer != null) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            containerColor = colors.surface,
            title = { Text("Laporkan $displayNick", color = colors.textPrimary) },
            text = {
                Column {
                    listOf("Spam atau penipuan", "Pelecehan atau kata kasar", "Akun palsu / lainnya").forEach { reason ->
                        Text(
                            reason,
                            color = colors.textPrimary,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                                showReportDialog = false
                                NexNotifier.success("Laporan terkirim ke tim Nexrify", currentStrings().dmTitle)
                            }.padding(vertical = 12.dp, horizontal = 6.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) { Text(S.dmCancel, color = colors.textPrimary) }
            }
        )
    }

    menuMsg?.let { m ->
        val mine = m.uid == myId
        DmMessageContextMenu(
            m = m, mine = mine, myId = myId,
            mineColor = BUBBLE_THEMES.getOrNull(shownPeer?.uid?.let { dms.theme(it) } ?: 0),
            starred = m.seq in stars,
            canForward = m.voice == null && m.image == null,
            canTranslate = m.text.isNotBlank() && m.seq !in translations,
            canCopy = m.text.isNotBlank(),
            canEdit = mine && m.text.isNotBlank() && m.song == null && System.currentTimeMillis() - m.ts < EDIT_WINDOW,
            onDismiss = { menuMsg = null },
            onReact = { e -> menuMsg = null; scope.launch { dms.react(m.seq, e) } },
            onReply = { replyTo = m; editing = null; menuMsg = null },
            onForward = { forwardMsg = m; menuMsg = null },
            onTranslate = {
                menuMsg = null
                scope.launch { dms.translate(m.seq, if (currentStrings().english) "en" else "id")?.let { NexNotifier.warning(dmErrText(it), currentStrings().dmTitle) } }
            },
            onStar = { menuMsg = null; scope.launch { dms.star(m.seq, m.seq !in stars) } },
            onCopy = {
                clipboard.setText(AnnotatedString(m.text)); menuMsg = null
                NexNotifier.info(currentStrings().dmCopied, currentStrings().dmTitle)
            },
            onEdit = {
                editing = m; replyTo = null
                input = TextFieldValue(m.text, androidx.compose.ui.text.TextRange(m.text.length))
                menuMsg = null
            },
            onDelete = { deleteMsg = m; menuMsg = null }
        )
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

    deleteMsg?.let { m ->
        DmDeleteDialog(
            canDeleteForAll = m.uid == myId && !m.deleted,
            onDismiss = { deleteMsg = null },
            onForMe = {
                deleteMsg = null
                hiddenSeqs = hiddenSeqs + m.seq
                writeHidden(hiddenCtx, hiddenKey, hiddenSeqs)
            },
            onForAll = { deleteMsg = null; scope.launch { dms.delete(m.seq) } }
        )
    }
}

/* =============================== PROFIL LENGKAP KONTAK =============================== */

@Composable
private fun ContactDetailScreen(
    peer: DmPeer,
    nickname: String,
    isMuted: Boolean,
    isBlocked: Boolean,
    isChatLocked: Boolean,
    onBack: () -> Unit,
    onCall: () -> Unit,
    onOpenMediaLinks: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleLock: () -> Unit,
    onClearChat: () -> Unit,
    onToggleBlock: () -> Unit,
    onReport: () -> Unit
) {
    val colors = NexrifyAppTheme.colors
    val dms = remember { DmManager.getInstance() }
    val localVer by dms.localVersion.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var showSharePicker by remember { mutableStateOf(false) }
    var showEditContact by remember { mutableStateOf(false) }

    val saved = remember(peer.uid, localVer) { dms.getContact(peer.uid) }
    val shownName = saved?.fullName?.ifBlank { null } ?: nickname

    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(
            Modifier.fillMaxSize().background(colors.background).statusBarsPadding().navigationBarsPadding()
        ) {
            // Header + titik 3 (Bagikan kontak & Edit)
            Row(
                Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TopIcon(NexIcons.ArrowBack, onBack)
                Text("Info Kontak", style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary, modifier = Modifier.weight(1f).padding(start = 6.dp))
                Box {
                    TopIcon(NexIcons.MoreVert) { showMenu = true }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Bagikan kontak", color = colors.textPrimary) },
                            onClick = { showMenu = false; showSharePicker = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Edit", color = colors.textPrimary) },
                            onClick = { showMenu = false; showEditContact = true }
                        )
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))

            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(10.dp))
                ChatAvatar(peer.uid, peer.av, shownName, 92.dp)
                Spacer(Modifier.height(14.dp))
                // Nickname & Username
                Text(shownName, color = colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Text("@${peer.username}", color = colors.textMuted, fontSize = 14.sp)

                Spacer(Modifier.height(22.dp))
                // Tombol Panggilan Suara
                Row(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(colors.card).border(1.dp, colors.border, RoundedCornerShape(16.dp))
                        .clickable(onClick = onCall).padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(NexIcons.Phone, null, tint = colors.textPrimary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Panggilan Suara", color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }

                Spacer(Modifier.height(24.dp))
                // Kartu menu utama
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.card).border(1.dp, colors.border, RoundedCornerShape(18.dp))
                ) {
                    ContactDetailRow(NexIcons.ImagePick, "Media & tautan", onClick = onOpenMediaLinks)
                    HorizontalDivider(color = colors.border.copy(alpha = 0.6f))
                    ContactDetailRow(
                        if (isMuted) NexIcons.BellOff else NexIcons.Bell,
                        if (isMuted) "Notifikasi (Disenyapkan)" else "Notifikasi",
                        onClick = onToggleMute
                    )
                    HorizontalDivider(color = colors.border.copy(alpha = 0.6f))
                    ContactDetailRow(
                        NexIcons.Lock,
                        if (isChatLocked) "Kunci Obrolan (Aktif)" else "Kunci Obrolan",
                        onClick = onToggleLock
                    )
                }

                // ---- > batas
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = colors.border)
                Spacer(Modifier.height(16.dp))

                // Kartu aksi bawah batas
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.card).border(1.dp, colors.border, RoundedCornerShape(18.dp))
                ) {
                    ContactDetailRow(NexIcons.DeleteOutline, "Bersihkan Obrolan", danger = true, onClick = onClearChat)
                    HorizontalDivider(color = colors.border.copy(alpha = 0.6f))
                    ContactDetailRow(
                        NexIcons.Block,
                        if (isBlocked) "Buka Blokir $shownName" else "Blokir $shownName",
                        danger = true,
                        onClick = onToggleBlock
                    )
                    HorizontalDivider(color = colors.border.copy(alpha = 0.6f))
                    ContactDetailRow(NexIcons.Flag, "Laporkan $shownName", danger = true, onClick = onReport)
                }
            }
        }

        if (showSharePicker) {
            DmSharePicker(
                onSend = { c -> dms.sendTo(c.uid, "👤 @${peer.username}") },
                onDismiss = { showSharePicker = false }
            )
        }

        if (showEditContact) {
            var fn by remember { mutableStateOf(saved?.firstName ?: peer.display) }
            var ln by remember { mutableStateOf(saved?.lastName ?: "") }
            AlertDialog(
                onDismissRequest = { showEditContact = false },
                containerColor = colors.surface,
                title = { Text("Edit Kontak", color = colors.textPrimary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ContactInputField(value = fn, hint = "Nama Depan", onValueChange = { fn = it.take(24) })
                        ContactInputField(value = ln, hint = "Nama Belakang (Opsional)", onValueChange = { ln = it.take(24) })
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        dms.saveContact(peer.uid, peer.username, fn, ln, peer.av)
                        showEditContact = false
                        NexNotifier.success("Kontak diperbarui", "Kontak")
                    }) { Text("Simpan", color = colors.textPrimary, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { showEditContact = false }) { Text(S.dmCancel, color = colors.textSecondary) }
                }
            )
        }
    }
}

@Composable
private fun ContactDetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val colors = NexrifyAppTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (danger) RED else colors.textPrimary, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = if (danger) RED else colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaAndLinksSheet(
    messages: List<DmMessage>,
    loadImage: suspend (String) -> android.graphics.Bitmap?,
    onOpenImage: (DmImage) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = NexrifyAppTheme.colors
    val context = LocalContext.current
    val mediaMsgs = remember(messages) { messages.filter { !it.deleted && it.image != null && !it.image.once } }
    val linkMsgs = remember(messages) { messages.filter { !it.deleted && URL_RE.containsMatchIn(it.text) } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Media & tautan", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(14.dp))
            Text("Media (${mediaMsgs.size})", color = colors.textSecondary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            if (mediaMsgs.isEmpty()) {
                Text("Belum ada media yang dikirim", color = colors.textMuted, fontSize = 13.sp)
            } else {
                Row(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    mediaMsgs.take(12).forEach { m ->
                        val im = m.image ?: return@forEach
                        DmImageView(im, loadImage, { onDismiss(); onOpenImage(im) }, {}, 0.dp)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Tautan (${linkMsgs.size})", color = colors.textSecondary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            if (linkMsgs.isEmpty()) {
                Text("Belum ada tautan yang dikirim", color = colors.textMuted, fontSize = 13.sp)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    linkMsgs.take(15).forEach { m ->
                        val url = URL_RE.find(m.text)?.value.orEmpty()
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.card)
                                .clickable {
                                    runCatching {
                                        context.startActivity(
                                            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(NexIcons.LinkIcon, null, tint = colors.textPrimary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(url, color = colors.textPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/* =============================== GELEMBUNG PESAN =============================== */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DmBubble(
    firstInGroup: Boolean = true,
    lastInGroup: Boolean = true,
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
    // Tampilan gelembung seperti semula (tema bersih Nexrify), dan centang terbaca berwarna hijau
    val bg = if (mine) (mineColor ?: colors.textPrimary) else colors.card
    val fg = if (mine) (if (mineColor != null) Color.White else colors.background) else colors.textPrimary
    val sub = if (mine) (if (mineColor != null) Color.White.copy(alpha = 0.72f) else colors.background.copy(alpha = 0.65f)) else colors.textMuted
    val big = 18.dp
    val tail = 4.dp
    val shape = if (mine) RoundedCornerShape(topStart = big, topEnd = if (firstInGroup) big else tail, bottomStart = big, bottomEnd = if (lastInGroup) tail else tail)
    else RoundedCornerShape(topStart = if (firstInGroup) big else tail, topEnd = big, bottomStart = if (lastInGroup) tail else tail, bottomEnd = big)
    var drag by remember { mutableStateOf(0f) }
    val trigger = with(LocalDensity.current) { 56.dp.toPx() }
    Row(
        Modifier.fillMaxWidth().padding(top = if (firstInGroup) 8.dp else 2.dp, bottom = 1.dp)
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
                    .combinedClickable(
                        onClick = { if (m.status == SendStatus.FAILED) onRetry() },
                        onLongClick = onLong
                    )
                    .padding(horizontal = 13.dp, vertical = 8.dp)
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
                    if (m.text.isNotBlank() && !PROFILE_RE.matches(m.text.trim())) Text(m.text, color = fg, fontSize = 15.sp, lineHeight = 20.sp)
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
                            SendStatus.SENT -> Text(
                                text = if (read) "✓✓" else "✓",
                                color = if (read) READ_GREEN else sub,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            if (m.status == SendStatus.FAILED) Text(S.dmFailedTap, color = RED, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
            if (m.reactions.isNotEmpty() && !m.deleted) {
                Row(
                    Modifier.padding(bottom = 6.dp).offset(y = (-7).dp).zIndex(1f)
                        .clip(RoundedCornerShape(14.dp)).background(colors.surface)
                        .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    m.reactions.entries.forEach { (emoji, users) ->
                        val me = users.contains(myId)
                        Row(
                            Modifier.clip(RoundedCornerShape(11.dp)).background(if (me) colors.accent.copy(alpha = 0.22f) else Color.Transparent)
                                .clickable { onReact(emoji) }.padding(horizontal = 5.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(emoji, fontSize = 14.sp)
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

@Composable
fun DmInboxScreen(onBack: () -> Unit, onOpenChat: (String) -> Unit) {
    DmLockGate(onBack) { DmInboxContent(onBack, onOpenChat) }
}

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
                Text("Nexrify", style = Typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary, modifier = Modifier.padding(start = 6.dp))
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

@Composable
private fun DmCallRow(m: DmMessage, mine: Boolean, onRecordVoice: () -> Unit, onCallBack: () -> Unit) {
    val colors = NexrifyAppTheme.colors
    val c = m.call ?: return
    val missedIn = c.status == "missed" && !mine
    val bg = if (mine) colors.textPrimary else colors.card
    val fg = if (mine) colors.background else colors.textPrimary
    val sub = if (mine) colors.background.copy(alpha = 0.68f) else colors.textMuted
    val result = when (c.status) {
        "ended" -> "%d:%02d".format(c.dur / 60, c.dur % 60)
        "declined" -> if (mine) S.callDeclinedLabel else S.callLogDeclinedIn
        else -> if (mine) S.callNotAnswered else S.callMissedLabel
    }
    val canAct = (!mine && c.status != "ended") || (mine && c.status != "ended")
    val shape = RoundedCornerShape(topStart = if (mine) 16.dp else 4.dp, topEnd = if (mine) 4.dp else 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(Modifier.widthIn(min = 220.dp, max = 300.dp).clip(shape).background(bg).padding(6.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(fg.copy(alpha = 0.08f)).padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(fg.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Icon(NexIcons.Phone, null, tint = if (missedIn) RED else fg, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(S.callVoiceCall, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(result, color = if (missedIn) RED else sub, fontSize = 13.sp)
                }
                Spacer(Modifier.width(8.dp))
                Text(chatTime(m.ts), color = sub, fontSize = 10.sp)
            }
            if (canAct && c.status != "ended") {
                Box(Modifier.fillMaxWidth().padding(top = 2.dp).height(1.dp).background(fg.copy(alpha = 0.10f)))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { if (mine) onRecordVoice() else onCallBack() }.padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (mine) S.callVoiceMessage else S.callCallBack, color = fg, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

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

@Composable
private fun DmLinkCards(text: String, loadPreview: suspend (String) -> LinkPreview?, fg: Color, sub: Color) {
    val context = LocalContext.current
    PROFILE_RE.find(text.trim())?.let { m ->
        DmProfileCard(m.groupValues[1], fg, sub)
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

@Composable
private fun DmProfileCard(username: String, fg: Color, sub: Color) {
    val profiles by rememberChatProfiles(listOf(username))
    val p = profiles[username.lowercase()]
    var open by remember { mutableStateOf(false) }
    val myName = AccountManager.getInstance().user.value?.username.orEmpty()
    val settings = remember { id.nexrify.music.data.chat.ChatManager.getInstance().settings }
    val muted by settings.muted.collectAsState()

    Column(
        Modifier.padding(top = 6.dp).widthIn(min = 220.dp).clip(RoundedCornerShape(14.dp)).background(fg.copy(alpha = 0.10f))
            .clickable { open = true }.padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (p != null) ChatAvatar(p.uid, p.av, p.display.ifBlank { p.username }, 48.dp)
            else Box(Modifier.size(48.dp).clip(CircleShape).background(fg.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) { Text("👤", fontSize = 22.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f, fill = false)) {
                Text(
                    p?.display?.ifBlank { null } ?: "@$username", color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text("@$username", color = sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (p != null && p.titles.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            PersonTags(p, Modifier)
        }
        val bio = p?.bio?.trim().orEmpty()
        if (bio.isNotBlank() && p?.profileHidden != true) {
            Spacer(Modifier.height(8.dp))
            Text(bio, color = fg.copy(alpha = 0.9f), fontSize = 13.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(8.dp))
        Text(S.dmProfileFull, color = sub, fontSize = 11.sp)
    }

    if (open) {
        ChatProfileSheet(
            username = username,
            isMe = username.equals(myName, true),
            isMuted = username.lowercase() in muted,
            onMention = { open = false },
            onToggleMute = {
                val st = currentStrings()
                if (username.lowercase() in muted) { settings.unmute(username); NexNotifier.info(st.chatUnmuted("@$username"), st.chatTitle) }
                else { settings.mute(username); NexNotifier.info(st.chatMuted("@$username"), st.chatTitle) }
            },
            onDismiss = { open = false },
            showMention = false
        )
    }
}

@Composable
private fun DmMessageContextMenu(
    m: DmMessage, mine: Boolean, myId: String, mineColor: Color?, starred: Boolean,
    canForward: Boolean, canTranslate: Boolean, canCopy: Boolean, canEdit: Boolean,
    onDismiss: () -> Unit, onReact: (String) -> Unit, onReply: () -> Unit, onForward: () -> Unit,
    onTranslate: () -> Unit, onStar: () -> Unit, onCopy: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit
) {
    val colors = NexrifyAppTheme.colors
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val sc by animateFloatAsState(if (shown) 1f else 0.86f, label = "ctxScale")
    val al by animateFloatAsState(if (shown) 1f else 0f, label = "ctxAlpha")
    val bubbleBg = if (mine) (mineColor ?: colors.textPrimary) else colors.card
    val bubbleFg = if (mine) (if (mineColor != null) Color.White else colors.background) else colors.textPrimary
    val cardShape = RoundedCornerShape(18.dp)
    val emojis = remember { (listOf("❤️") + CHAT_EMOJIS_QUICK).distinct() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.66f * al))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                .statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).scale(sc).alpha(al),
                horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (m.seq > 0) Row(
                    Modifier.clip(RoundedCornerShape(28.dp)).background(colors.surface)
                        .border(1.dp, colors.border, RoundedCornerShape(28.dp)).padding(horizontal = 6.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically
                ) {
                    emojis.forEach { e ->
                        val mineReact = m.reactions[e]?.contains(myId) == true
                        Box(
                            Modifier.size(42.dp).clip(CircleShape).background(if (mineReact) colors.accent.copy(alpha = 0.25f) else Color.Transparent)
                                .clickable { onReact(e) },
                            contentAlignment = Alignment.Center
                        ) { Text(e, fontSize = 24.sp) }
                    }
                }
                Box(
                    Modifier.widthIn(max = 300.dp)
                        .clip(RoundedCornerShape(20.dp)).background(bubbleBg)
                        .then(if (!mine) Modifier.border(1.dp, colors.border, RoundedCornerShape(20.dp)) else Modifier)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        dmPreview(m).ifBlank { S.dmMessageDeleted },
                        color = bubbleFg, fontSize = 15.sp, lineHeight = 21.sp, maxLines = 8, overflow = TextOverflow.Ellipsis
                    )
                }
                Column(
                    Modifier.widthIn(min = 230.dp, max = 270.dp).clip(cardShape).background(colors.surface).border(1.dp, colors.border, cardShape)
                ) {
                    CtxRow(S.dmReply, NexIcons.Reply, false, onReply)
                    if (canCopy) { CtxDivider(); CtxRow(S.dmCopy, NexIcons.Copy, false, onCopy) }
                    if (canForward) { CtxDivider(); CtxRow(S.dmForward, NexIcons.Send, false, onForward) }
                    if (canTranslate) { CtxDivider(); CtxRow(S.dmTranslate, NexIcons.Translate, false, onTranslate) }
                    if (m.seq > 0) { CtxDivider(); CtxRow(if (starred) S.dmUnstar else S.dmStar, NexIcons.Flag, false, onStar) }
                    if (canEdit) { CtxDivider(); CtxRow(S.dmEditAction, NexIcons.Edit, false, onEdit) }
                    CtxDivider()
                    CtxRow(S.dmDeleteAction, NexIcons.DeleteOutline, true, onDelete)
                }
            }
        }
    }
}

@Composable
private fun CtxDivider() {
    Box(Modifier.fillMaxWidth().height(0.5.dp).background(NexrifyAppTheme.colors.border))
}

@Composable
private fun CtxRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, danger: Boolean, onClick: () -> Unit) {
    val colors = NexrifyAppTheme.colors
    val c = if (danger) RED else colors.textPrimary
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = c, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Icon(icon, null, tint = c, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun DmDeleteDialog(canDeleteForAll: Boolean, onDismiss: () -> Unit, onForMe: () -> Unit, onForAll: () -> Unit) {
    val colors = NexrifyAppTheme.colors
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val shape = RoundedCornerShape(18.dp)
            Column(Modifier.fillMaxWidth().clip(shape).background(colors.surface).border(1.dp, colors.border, shape)) {
                Text(
                    S.dmDeleteMsgTitle, color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)
                )
                CtxDivider()
                Column(Modifier.fillMaxWidth().clickable(onClick = onForMe).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(S.dmDeleteForMe, color = RED, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(S.dmDeleteForMeHint, color = colors.textMuted, fontSize = 11.sp)
                }
                if (canDeleteForAll) {
                    CtxDivider()
                    Column(Modifier.fillMaxWidth().clickable(onClick = onForAll).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(S.dmDeleteForAll, color = RED, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text(S.dmDeleteForAllHint, color = colors.textMuted, fontSize = 11.sp)
                    }
                }
            }
            Box(
                Modifier.fillMaxWidth().clip(shape).background(colors.surface).border(1.dp, colors.border, shape).clickable(onClick = onDismiss).padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) { Text(S.dmCancel, color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

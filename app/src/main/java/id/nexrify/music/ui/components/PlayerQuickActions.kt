package id.nexrify.music.ui.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.nexrify.music.data.download.DownloadState
import id.nexrify.music.data.model.Track
import id.nexrify.music.i18n.S
import id.nexrify.music.i18n.currentStrings
import id.nexrify.music.ui.icons.NexIcons
import id.nexrify.music.ui.theme.NexrifyAppTheme
import id.nexrify.music.ui.theme.Typography
import id.nexrify.music.viewmodel.MusicViewModel

/**
 * Deretan aksi cepat di pemutar penuh: playlist, unduh offline, timer tidur, kecepatan putar, bagikan.
 */
@Composable
fun PlayerQuickActions(
    track: Track,
    viewModel: MusicViewModel,
    onAddToPlaylist: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val offlineTracks by viewModel.offlineTracks.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val sleepSec by viewModel.sleepRemainingSec.collectAsState()
    val speed by viewModel.playbackSpeed.collectAsState()
    val voice by viewModel.voiceStyle.collectAsState()

    var showSleep by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }

    val isDownloaded = offlineTracks.any { it.track.id == track.id }
    val dlState = downloads[track.id]?.state
    val dlBusy = dlState is DownloadState.Downloading || dlState == DownloadState.Queued

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuickActionTile(
            icon = NexIcons.Add,
            label = S.actionPlaylist,
            modifier = Modifier.weight(1f),
            onClick = onAddToPlaylist
        )
        QuickActionTile(
            icon = if (isDownloaded) NexIcons.Check else NexIcons.Download,
            label = when {
                isDownloaded -> S.actionDownloaded
                dlState is DownloadState.Downloading -> "${(dlState.progress * 100f).toInt()}%"
                else -> S.actionDownload
            },
            active = isDownloaded || dlBusy,
            busy = dlBusy,
            modifier = Modifier.weight(1f),
            onClick = {
                val st = currentStrings()
                when {
                    isDownloaded -> {
                        viewModel.deleteOffline(track.id)
                        NexNotifier.info(st.toastDownloadRemoved, st.noteDownloadTitle)
                    }
                    dlBusy -> viewModel.cancelDownload(track.id)
                    else -> {
                        viewModel.downloadTrack(track)
                        NexNotifier.success(st.toastDownloadStarted, st.noteDownloadTitle)
                    }
                }
            }
        )
        QuickActionTile(
            icon = NexIcons.Timer,
            label = when {
                sleepSec == null -> S.actionSleep
                sleepSec == -1 -> S.actionSleep
                else -> S.sleepLeft(((sleepSec ?: 0) + 59) / 60)
            },
            active = sleepSec != null,
            modifier = Modifier.weight(1f),
            onClick = { showSleep = true }
        )
        QuickActionTile(
            icon = NexIcons.Speed,
            label = when (voice) {
                id.nexrify.music.viewmodel.VoiceStyle.SPEED_UP -> S.voiceSpeedUp
                id.nexrify.music.viewmodel.VoiceStyle.SLOWED_REVERB -> S.voiceSlowed
                else -> if (speed == 1f) S.actionSpeed else formatSpeed(speed)
            },
            active = speed != 1f || voice != id.nexrify.music.viewmodel.VoiceStyle.NORMAL,
            modifier = Modifier.weight(1f),
            onClick = { showSpeed = true }
        )
        QuickActionTile(
            icon = NexIcons.Share,
            label = S.actionShare,
            modifier = Modifier.weight(1f),
            onClick = { showShare = true }
        )
    }

    var showFriendPick by remember { mutableStateOf(false) }
    if (showFriendPick) {
        DmSharePicker(
            onSend = { c ->
                id.nexrify.music.data.chat.DmManager.getInstance().sendTo(
                    c.uid, "",
                    id.nexrify.music.data.chat.DmSong(track.id, track.title, track.artist, track.thumbnailUrl, track.url, track.durationSeconds)
                )
            },
            onDismiss = { showFriendPick = false }
        )
    }

    if (showShare) {
        ShareChoiceSheet(
            onDismiss = { showShare = false },
            onFriend = if (id.nexrify.music.data.account.AccountManager.getInstance().isLoggedIn()) ({ showShare = false; showFriendPick = true }) else null,
            onTogether = {
                showShare = false
                id.nexrify.music.data.mubar.MubarManager.getInstance().openSheet()
            },
            onShareLink = {
                showShare = false
                runCatching {
                    val st = currentStrings()
                    val send = Intent(Intent.ACTION_SEND).apply {
                        putExtra(Intent.EXTRA_TEXT, st.shareText(track.title, track.artist, track.url))
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(send, st.shareVia))
                }
            }
        )
    }

    if (showSleep) {
        SleepTimerSheet(
            activeSec = sleepSec,
            onPick = { minutes ->
                val st = currentStrings()
                viewModel.startSleepTimer(minutes)
                NexNotifier.success(st.sleepSet(minutes), st.noteSleepTitle)
                showSleep = false
            },
            onEndOfTrack = {
                val st = currentStrings()
                viewModel.startSleepUntilEndOfTrack()
                NexNotifier.success(st.sleepSetEndOfTrack, st.noteSleepTitle)
                showSleep = false
            },
            onOff = {
                val st = currentStrings()
                viewModel.cancelSleepTimer()
                NexNotifier.info(st.sleepCancelled, st.noteSleepTitle)
                showSleep = false
            },
            onDismiss = { showSleep = false }
        )
    }

    if (showSpeed) {
        SpeedSheet(
            current = speed,
            voice = voice,
            onVoice = { v ->
                val st = currentStrings()
                viewModel.setVoiceStyle(v)
                NexNotifier.success(
                    st.voiceStyleSet(when (v) {
                        id.nexrify.music.viewmodel.VoiceStyle.NORMAL -> st.voiceNormal
                        id.nexrify.music.viewmodel.VoiceStyle.SPEED_UP -> st.voiceSpeedUp
                        id.nexrify.music.viewmodel.VoiceStyle.SLOWED_REVERB -> st.voiceSlowed
                    }),
                    st.voiceStyleTitle
                )
                showSpeed = false
            },
            onPick = { x ->
                viewModel.setPlaybackSpeed(x)
                NexNotifier.success(currentStrings().speedSet(formatSpeed(x)), currentStrings().noteSpeedTitle)
                showSpeed = false
            },
            onDismiss = { showSpeed = false }
        )
    }
}

private fun formatSpeed(x: Float): String {
    val s = if (x % 1f == 0f) x.toInt().toString() else x.toString().trimEnd('0')
    return "${s}x"
}

@Composable
private fun QuickActionTile(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    busy: Boolean = false,
    onClick: () -> Unit
) {
    val colors = NexrifyAppTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (active) colors.textPrimary else colors.card)
            .border(1.dp, if (active) colors.textPrimary else colors.border, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            if (busy) {
                CircularProgressIndicator(
                    color = colors.background,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Icon(
                    icon, null,
                    tint = if (active) colors.background else colors.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = Typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
            color = if (active) colors.background else colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTimerSheet(
    activeSec: Int?,
    onPick: (Int) -> Unit,
    onEndOfTrack: () -> Unit,
    onOff: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = NexrifyAppTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        dragHandle = { SheetHandle() }
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text(S.sleepTitle, style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
            Spacer(Modifier.height(2.dp))
            Text(S.sleepSub, style = Typography.bodySmall, color = colors.textMuted)
            Spacer(Modifier.height(14.dp))
            listOf(5, 10, 15, 30, 45, 60).forEach { m ->
                SheetOption(S.sleepMinutes(m), selected = false, onClick = { onPick(m) })
            }
            SheetOption(S.sleepEndOfTrack, selected = activeSec == -1, onClick = onEndOfTrack)
            // Waktu custom: ketik menit sendiri (1 sampai 720 menit).
            var custom by remember { mutableStateOf("") }
            val customMin = custom.toIntOrNull()?.takeIf { it in 1..720 }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.text.BasicTextField(
                    value = custom,
                    onValueChange = { v -> custom = v.filter { it.isDigit() }.take(3) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = { customMin?.let(onPick) }
                    ),
                    textStyle = Typography.bodyMedium.copy(color = colors.textPrimary),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.textPrimary),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, colors.border.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.weight(1f)) {
                                if (custom.isEmpty()) Text(S.sleepCustomHint, style = Typography.bodyMedium, color = colors.textMuted)
                                inner()
                            }
                            Text(S.sleepMinuteUnit, style = Typography.bodySmall, color = colors.textMuted)
                        }
                    }
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(14.dp))
                        .background(if (customMin != null) colors.textPrimary else colors.card)
                        .clickable(enabled = customMin != null) { customMin?.let(onPick) }
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Text(
                        S.sleepStart,
                        style = Typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = if (customMin != null) colors.background else colors.textMuted
                    )
                }
            }
            if (activeSec != null) {
                Spacer(Modifier.height(6.dp))
                SheetOption(S.sleepOff, selected = false, danger = true, onClick = onOff)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedSheet(
    current: Float,
    voice: id.nexrify.music.viewmodel.VoiceStyle,
    onVoice: (id.nexrify.music.viewmodel.VoiceStyle) -> Unit,
    onPick: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = NexrifyAppTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        dragHandle = { SheetHandle() }
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 32.dp)
        ) {
            Text(S.voiceStyleTitle, style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
            Spacer(Modifier.height(2.dp))
            Text(S.voiceStyleSub, style = Typography.bodySmall, color = colors.textMuted)
            Spacer(Modifier.height(12.dp))
            VoiceOption(S.voiceNormal, S.voiceNormalDesc, voice == id.nexrify.music.viewmodel.VoiceStyle.NORMAL && current == 1f) {
                onVoice(id.nexrify.music.viewmodel.VoiceStyle.NORMAL)
            }
            VoiceOption(S.voiceSpeedUp, S.voiceSpeedUpDesc, voice == id.nexrify.music.viewmodel.VoiceStyle.SPEED_UP) {
                onVoice(id.nexrify.music.viewmodel.VoiceStyle.SPEED_UP)
            }
            VoiceOption(S.voiceSlowed, S.voiceSlowedDesc, voice == id.nexrify.music.viewmodel.VoiceStyle.SLOWED_REVERB) {
                onVoice(id.nexrify.music.viewmodel.VoiceStyle.SLOWED_REVERB)
            }
            Spacer(Modifier.height(18.dp))
            Text(S.speedTitle, style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
            Spacer(Modifier.height(10.dp))
            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { x ->
                SheetOption(
                    text = if (x == 1f) "1x  ${S.speedNormal}" else formatSpeed(x),
                    selected = voice == id.nexrify.music.viewmodel.VoiceStyle.NORMAL && current == x,
                    onClick = { onPick(x) }
                )
            }
        }
    }
}

@Composable
private fun VoiceOption(title: String, desc: String, selected: Boolean, onClick: () -> Unit) {
    val colors = NexrifyAppTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(shape)
            .background(if (selected) colors.card else androidx.compose.ui.graphics.Color.Transparent)
            .border(1.dp, if (selected) colors.textPrimary.copy(alpha = 0.5f) else colors.border.copy(alpha = 0.6f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(title, fontWeight = FontWeight.Bold, color = colors.textPrimary)
        Spacer(Modifier.height(2.dp))
        Text(desc, style = Typography.bodySmall, color = colors.textMuted)
    }
}

@Composable
private fun SheetHandle() {
    val colors = NexrifyAppTheme.colors
    Box(
        Modifier.padding(vertical = 10.dp).width(36.dp).height(4.dp).clip(CircleShape).background(colors.border)
    )
}

@Composable
private fun SheetOption(text: String, selected: Boolean, danger: Boolean = false, onClick: () -> Unit) {
    val colors = NexrifyAppTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(shape)
            .background(if (selected) colors.card else androidx.compose.ui.graphics.Color.Transparent)
            .border(1.dp, if (selected) colors.textPrimary.copy(alpha = 0.5f) else colors.border.copy(alpha = 0.6f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            modifier = Modifier.weight(1f),
            style = Typography.bodyLarge.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
            color = if (danger) androidx.compose.ui.graphics.Color(0xFFF87171) else colors.textPrimary
        )
        if (selected) Icon(NexIcons.Check, null, tint = colors.textPrimary, modifier = Modifier.size(20.dp))
    }
}

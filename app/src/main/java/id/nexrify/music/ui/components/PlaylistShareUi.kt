package id.nexrify.music.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.nexrify.music.data.account.AccountError
import id.nexrify.music.data.account.AccountManager
import id.nexrify.music.data.playlist.Playlist
import id.nexrify.music.data.playlist.PlaylistShare
import id.nexrify.music.data.playlist.PlaylistShareNav
import id.nexrify.music.data.playlist.SharedPlaylist
import id.nexrify.music.i18n.S
import id.nexrify.music.ui.icons.NexIcons
import id.nexrify.music.ui.theme.NexrifyAppTheme
import id.nexrify.music.ui.theme.Typography
import id.nexrify.music.viewmodel.MusicViewModel
import kotlinx.coroutines.launch

private fun copyToClipboard(context: Context, text: String) {
    runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Nexrify", text))
    }
}

/** Dialog bagikan playlist: membuat tautan https://nexrify.biz.id/playlist/usr/<id>, lalu salin / kirim / hentikan. */
@Composable
fun SharePlaylistDialog(playlist: Playlist, viewModel: MusicViewModel, onDismiss: () -> Unit) {
    val str = S // dipegang sekali: S adalah getter @Composable, tidak boleh dibaca di dalam onClick / LaunchedEffect
    val colors = NexrifyAppTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(true) }
    var shareId by remember { mutableStateOf(playlist.shareId) }
    var failed by remember { mutableStateOf<String?>(null) }

    suspend fun publish() {
        busy = true
        failed = null
        try {
            val id = PlaylistShare.share(playlist.copy(shareId = shareId))
            shareId = id
            viewModel.setPlaylistShareId(playlist.id, id)
        } catch (e: AccountError) {
            failed = if (e.code == "auth") str.sharePlaylistLogin else if (e.code == "network") str.sharePlaylistFail else e.message ?: str.sharePlaylistFail
        } catch (e: Exception) {
            failed = str.sharePlaylistFail
        }
        busy = false
    }

    LaunchedEffect(Unit) {
        if (playlist.tracks.isEmpty()) { failed = str.sharePlaylistEmpty; busy = false }
        else if (!AccountManager.getInstance().isLoggedIn()) { failed = str.sharePlaylistLogin; busy = false }
        else publish()
    }

    NexDialogCard(icon = NexIcons.Share, title = str.sharePlaylistTitle, onDismiss = onDismiss) {
        val link = if (shareId.isNotBlank()) PlaylistShare.linkFor(shareId) else ""
        when {
            busy -> {
                Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp, color = colors.textPrimary)
                }
                Text(str.sharePlaylistPreparing, style = Typography.bodyMedium, color = colors.textMuted)
            }
            link.isNotBlank() && failed == null -> {
                Text(
                    link,
                    style = Typography.bodyMedium,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.card)
                        .border(1.dp, colors.border, RoundedCornerShape(14.dp)).padding(12.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(str.sharePlaylistHint, style = Typography.bodySmall, color = colors.textMuted)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, str.sharePlaylistText(playlist.name, link)) }
                        runCatching { context.startActivity(Intent.createChooser(send, str.shareVia).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.background)
                ) { Text(str.sharePlaylistSend, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { copyToClipboard(context, link); NexNotifier.success(str.sharePlaylistCopied) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, colors.border)
                ) { Text(str.sharePlaylistCopy, color = colors.textPrimary, fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { scope.launch { publish() } },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, colors.border)
                ) { Text(str.sharePlaylistUpdate, color = colors.textSecondary) }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val id = shareId
                        scope.launch {
                            runCatching { PlaylistShare.unshare(id) }
                            viewModel.setPlaylistShareId(playlist.id, "")
                            NexNotifier.info(str.sharePlaylistStopped)
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFFCA5A5).copy(alpha = 0.5f))
                ) { Text(str.sharePlaylistStop, color = androidx.compose.ui.graphics.Color(0xFFFCA5A5)) }
            }
            else -> {
                Text(failed ?: str.sharePlaylistFail, style = Typography.bodyMedium, color = colors.textMuted)
                Spacer(Modifier.height(16.dp))
                if (playlist.tracks.isNotEmpty() && AccountManager.getInstance().isLoggedIn()) {
                    Button(
                        onClick = { scope.launch { publish() } },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.background)
                    ) { Text(str.retry, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

private sealed interface SharedState {
    object Loading : SharedState
    data class Ready(val playlist: SharedPlaylist) : SharedState
    data class Error(val message: String) : SharedState
}

/** Dipasang sekali di layar utama: membuka dialog saat ada tautan playlist (deep link) yang masuk. */
@Composable
fun SharedPlaylistHost(viewModel: MusicViewModel, onOpenLibrary: () -> Unit) {
    val str = S
    val request by PlaylistShareNav.request.collectAsState()
    var activeId by remember { mutableStateOf<String?>(null) }
    var state by remember { mutableStateOf<SharedState>(SharedState.Loading) }

    LaunchedEffect(request) {
        val id = request ?: return@LaunchedEffect
        PlaylistShareNav.request.value = null
        activeId = id
        state = SharedState.Loading
        state = try {
            SharedState.Ready(PlaylistShare.fetch(id))
        } catch (e: AccountError) {
            SharedState.Error(if (e.code == "not_found") str.sharedPlaylistNotFound else str.sharedPlaylistNetwork)
        } catch (e: Exception) {
            SharedState.Error(str.sharedPlaylistNetwork)
        }
    }
    if (activeId == null) return
    val close = { activeId = null }
    val colors = NexrifyAppTheme.colors

    NexDialogCard(icon = NexIcons.Share, title = (state as? SharedState.Ready)?.playlist?.name ?: str.sharePlaylistTitle, onDismiss = close) {
        when (val st = state) {
            SharedState.Loading -> {
                Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp, color = colors.textPrimary)
                }
                Text(str.sharedPlaylistLoading, style = Typography.bodyMedium, color = colors.textMuted)
            }
            is SharedState.Error -> {
                Text(st.message, style = Typography.bodyMedium, color = colors.textMuted)
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = close, modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, colors.border)
                ) { Text(str.sharedPlaylistClose, color = colors.textPrimary) }
            }
            is SharedState.Ready -> {
                val p = st.playlist
                Text(str.sharedPlaylistBy(p.ownerName.ifBlank { "Nexrify" }, p.tracks.size), style = Typography.bodyMedium, color = colors.textMuted)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        viewModel.importSharedPlaylist(p)
                        NexNotifier.success(str.sharedPlaylistSaved)
                        close()
                        onOpenLibrary()
                    },
                    enabled = p.tracks.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.background)
                ) { Text(str.sharedPlaylistSave, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        viewModel.playPlaylist(Playlist(id = p.id, name = p.name, tracks = p.tracks, coverUrl = p.coverUrl), shuffle = false)
                        close()
                    },
                    enabled = p.tracks.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, colors.border)
                ) { Text(str.sharedPlaylistPlay, color = colors.textPrimary, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

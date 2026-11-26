package id.nexrify.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.nexrify.music.data.chat.DmConversation
import id.nexrify.music.data.chat.DmManager
import id.nexrify.music.i18n.S
import id.nexrify.music.i18n.currentStrings
import id.nexrify.music.ui.theme.NexrifyAppTheme
import kotlinx.coroutines.launch

private fun shareErrText(code: String): String {
    val s = currentStrings()
    return when (code) {
        "dm_blocked" -> s.dmErrBlocked
        "dm_you_blocked" -> s.dmErrYouBlocked
        "dm_pending" -> s.dmErrPending
        "dm_declined" -> s.dmErrDeclined
        "chat_banned" -> s.dmBannedErr
        "rate_limited" -> s.dmSlowDown
        "network" -> s.dmNoNetworkErr
        else -> s.dmGenericErr
    }
}

/**
 * Lembar pilih teman untuk membagikan sesuatu lewat chat pribadi (lagu, playlist, profil).
 * [onSend] mengirim ke percakapan yang dipilih dan mengembalikan kode galat atau null kalau berhasil.
 * Lembar menutup sendiri begitu satu teman dipilih; hasilnya diumumkan lewat notifikasi.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DmSharePicker(onSend: suspend (DmConversation) -> String?, onDismiss: () -> Unit) {
    val colors = NexrifyAppTheme.colors
    val dms = remember { DmManager.getInstance() }
    val inbox by dms.inbox.collectAsState()
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) {
        dms.watchInbox(true)
        onDispose { dms.watchInbox(false) }
    }
    val targets = inbox.filter { !it.request && !it.note }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(S.dmShareToFriend, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(10.dp))
            if (targets.isEmpty()) {
                Text(S.dmShareNoChats, color = colors.textMuted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 24.dp))
            } else {
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(targets, key = { it.uid }) { c ->
                        val name = dms.nickname(c.uid).ifBlank { c.display.ifBlank { c.username } }
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                onDismiss()
                                scope.launch {
                                    val err = onSend(c)
                                    if (err == null) NexNotifier.success(S.dmShareSent(name), S.dmTitle)
                                    else NexNotifier.warning(shareErrText(err), S.dmTitle)
                                }
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ChatAvatar(c.uid, c.av, name, 42.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(name, color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

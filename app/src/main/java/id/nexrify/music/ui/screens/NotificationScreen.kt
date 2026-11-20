package id.nexrify.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.nexrify.music.data.chat.ChatManager
import id.nexrify.music.data.notif.NotifCenter
import id.nexrify.music.data.notif.NotifItem
import id.nexrify.music.i18n.S
import id.nexrify.music.ui.components.ChatAvatar
import id.nexrify.music.ui.icons.NexIcons
import id.nexrify.music.ui.theme.NexrifyAppTheme
import id.nexrify.music.ui.theme.Typography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private fun ago(ts: Long): String {
    val S = id.nexrify.music.i18n.currentStrings()
    val d = (System.currentTimeMillis() - ts) / 1000L
    val en = S.english
    return when {
        d < 60 -> if (en) "now" else "baru saja"
        d < 3600 -> (d / 60).toString() + (if (en) "m" else " mnt")
        d < 86400 -> (d / 3600).toString() + (if (en) "h" else " jam")
        d < 7 * 86400 -> (d / 86400).toString() + (if (en) "d" else " hr")
        else -> DateFormat.getDateInstance(DateFormat.SHORT).format(Date(ts))
    }
}

private fun titleOf(n: NotifItem): String = id.nexrify.music.i18n.currentStrings().let { S -> when (n.kind) {
    "f" -> S.notifFollowTitle(n.display)
    "F" -> S.notifFriendTitle(n.display)
    "m" -> S.notifItemMention(n.from)
    "r" -> S.notifItemReply(n.from)
    "x" -> S.notifItemReaction(n.from)
    "d" -> S.notifItemDm(n.from)
    "w" -> S.notifItemWarn
    else -> S.notifItemAnnounce
} }

/** Pusat notifikasi: pengikut baru, teman, mention, balasan, reaksi, pengumuman, dan peringatan. */
@Composable
fun NotificationScreen(onBack: () -> Unit, onOpenProfile: (String) -> Unit, onOpenChat: (Long) -> Unit) {
    val colors = NexrifyAppTheme.colors
    val center = remember { NotifCenter.getInstance() }
    val items by center.items.collectAsState()
    val scope = rememberCoroutineScope()
    // Yang belum dibaca saat layar dibuka tetap ditandai "Baru" walau sudah otomatis dianggap dibaca.
    val newIds = remember { items.filter { !it.read }.map { it.id }.toSet() }
    var followed by remember { mutableStateOf(setOf<String>()) }
    var busy by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(Unit) { delay(1200); center.markAllRead() }

    Column(Modifier.fillMaxSize().background(colors.background).padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(NexIcons.ArrowBack, S.back, tint = colors.textPrimary) }
            Text(S.notifCenterTitle, style = Typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary, modifier = Modifier.weight(1f))
            if (items.isNotEmpty()) IconButton(onClick = { center.clear() }) { Icon(NexIcons.DeleteOutline, S.notifClearAll, tint = colors.textSecondary) }
        }
        if (items.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(colors.card).border(1.dp, colors.border, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(NexIcons.Bell, null, tint = colors.textMuted, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text(S.notifCenterEmpty, fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text(S.notifCenterEmptySub, color = colors.textMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                val fresh = items.filter { it.id in newIds }
                val older = items.filter { it.id !in newIds }
                if (fresh.isNotEmpty()) item("h_new") { Header(S.notifNew) }
                items(fresh, key = { it.id }) { n -> NotifRow(n, true, n.id in followed, n.id in busy, onOpenProfile, onOpenChat, center) {
                    scope.launch {
                        busy = busy + n.id
                        runCatching { ChatManager.getInstance().follow(n.from) }.onSuccess { followed = followed + n.id }
                        busy = busy - n.id
                    }
                } }
                if (older.isNotEmpty()) item("h_old") { Header(S.notifEarlier) }
                items(older, key = { it.id }) { n -> NotifRow(n, false, n.id in followed, n.id in busy, onOpenProfile, onOpenChat, center) {
                    scope.launch {
                        busy = busy + n.id
                        runCatching { ChatManager.getInstance().follow(n.from) }.onSuccess { followed = followed + n.id }
                        busy = busy - n.id
                    }
                } }
            }
        }
    }
}

@Composable
private fun Header(text: String) {
    val colors = NexrifyAppTheme.colors
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.textMuted, modifier = Modifier.padding(start = 6.dp, top = 12.dp, bottom = 6.dp))
}

@Composable
private fun NotifRow(
    n: NotifItem, fresh: Boolean, followed: Boolean, busy: Boolean,
    onOpenProfile: (String) -> Unit, onOpenChat: (Long) -> Unit, center: NotifCenter, onFollowBack: () -> Unit
) {
    val colors = NexrifyAppTheme.colors
    val shape = RoundedCornerShape(18.dp)
    val follow = n.kind == "f" || n.kind == "F"
    Row(
        Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(shape)
            .background(if (fresh) colors.textPrimary.copy(alpha = 0.07f) else colors.card)
            .border(1.dp, if (fresh) colors.textPrimary.copy(alpha = 0.25f) else colors.border, shape)
            .clickable {
                center.markRead(n.id)
                if (follow) onOpenProfile(n.from) else onOpenChat(if (n.kind == "w") 0L else n.seq)
            }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChatAvatar(n.uid, if (follow) "1" else "", if (n.kind == "a") "N" else n.display, 42.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(titleOf(n), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val sub = when (n.kind) { "f" -> "@${n.from}"; "F" -> S.notifFriendBody; else -> n.text }
            if (sub.isNotBlank()) Text(sub, fontSize = 12.5.sp, color = colors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            if (n.kind == "f") {
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(if (followed) colors.card else colors.textPrimary)
                        .border(1.dp, if (followed) colors.border else colors.textPrimary, RoundedCornerShape(12.dp))
                        .clickable(enabled = !followed && !busy) { onFollowBack() }.padding(horizontal = 14.dp, vertical = 6.dp)
                ) { Text(if (followed) S.socialFollowing else S.notifFollowBack, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (followed) colors.textMuted else colors.background) }
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(ago(n.ts), fontSize = 11.sp, color = colors.textMuted)
            if (fresh) { Spacer(Modifier.height(6.dp)); Box(Modifier.size(8.dp).clip(CircleShape).background(colors.accent)) }
        }
    }
}

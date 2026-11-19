package id.nexrify.music.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.nexrify.music.data.account.AccountError
import id.nexrify.music.data.account.AccountManager
import id.nexrify.music.data.chat.ChatManager
import id.nexrify.music.data.chat.ChatProfile
import id.nexrify.music.i18n.S
import id.nexrify.music.i18n.currentStrings
import id.nexrify.music.ui.theme.NexrifyAppTheme
import id.nexrify.music.ui.theme.Typography
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Profil Global Chat milik sendiri (background, title, lencana, statistik). Diambil sekali, jatuh ke data `me` kalau gagal. */
@Composable
fun rememberMyProfile(): MutableState<ChatProfile?> {
    val mgr = remember { ChatManager.getInstance() }
    val user by AccountManager.getInstance().user.collectAsState()
    val me by mgr.me.collectAsState()
    val st = remember { mutableStateOf<ChatProfile?>(null) }
    LaunchedEffect(user?.username) {
        val u = user?.username.orEmpty()
        if (u.isNotBlank()) runCatching { mgr.profile(u) }.getOrNull()?.let { st.value = it }
    }
    LaunchedEffect(me) { if (st.value == null && me != null) st.value = me }
    return st
}

/** Banner background profil dengan tombol ubah/hapus. [overlay] dipakai untuk menimpa foto profil di atasnya. */
@Composable
fun ProfileBanner(profile: ChatProfile?, onProfile: (ChatProfile) -> Unit, overlay: @Composable BoxScope.() -> Unit = {}) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val mgr = remember { ChatManager.getInstance() }
    var busy by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: android.net.Uri? ->
        if (uri != null) scope.launch {
            busy = true
            val data = withContext(Dispatchers.IO) { runCatching { toCoverDataUrl(ctx, uri) }.getOrNull() }
            if (data == null) NexNotifier.warning(currentStrings().coverInvalid)
            else try {
                onProfile(mgr.setCover(data))
                NexNotifier.success(currentStrings().coverSaved)
            } catch (e: CancellationException) { throw e }
            catch (e: AccountError) { NexNotifier.error(if (e.code == "rate_limited") currentStrings().coverTooMany else currentStrings().coverInvalid) }
            catch (_: Throwable) { NexNotifier.error(currentStrings().socialFail) }
            busy = false
        }
    }
    Box(Modifier.fillMaxWidth()) {
        if (profile != null) {
            ProfileCover(
                profile = profile, isMe = true, busy = busy,
                onPick = { picker.launch("image/*") },
                onRemove = {
                    scope.launch {
                        busy = true
                        try { onProfile(mgr.clearCover()); NexNotifier.info(currentStrings().coverRemoved) }
                        catch (e: CancellationException) { throw e }
                        catch (_: Throwable) { NexNotifier.error(currentStrings().socialFail) }
                        busy = false
                    }
                }
            )
        } else {
            Box(
                Modifier.fillMaxWidth().height(136.dp).clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF7C5CFF), Color(0xFF3FA9FF))))
            )
        }
        overlay()
    }
}

/** Kartu profil sendiri di menu profil: background, foto, nama, role, title, statistik, dan aksi. */
@Composable
fun MyProfileCard(onEdit: () -> Unit, onAvatarClick: () -> Unit, onOpenFull: () -> Unit) {
    val colors = NexrifyAppTheme.colors
    val user by AccountManager.getInstance().user.collectAsState()
    val profileState = rememberMyProfile()
    val profile = profileState.value
    val badges = (profile?.badges ?: emptyList()).filter { it != "early" && it != "active" && it != "loved" }

    Column(Modifier.fillMaxWidth()) {
        ProfileBanner(profile, onProfile = { profileState.value = it }) {
            Box(
                Modifier.align(Alignment.BottomStart).padding(start = 16.dp).offset(y = 38.dp)
                    .clip(CircleShape).background(colors.card).padding(4.dp)
            ) {
                NexAvatar(user, 80.dp, modifier = Modifier.clip(CircleShape).clickable(onClick = onAvatarClick))
            }
        }
        Spacer(Modifier.height(46.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(user?.display ?: S.musicLover, style = Typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("@" + (user?.username ?: ""), style = Typography.bodyMedium, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // Role akun + lencana + umur akun.
        val role = user?.role
        if (isGlintRole(role) || badges.isNotEmpty() || (profile?.joined ?: 0L) > 0L) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (isGlintRole(role)) RoleBadge(role = role.orEmpty())
                badges.filter { it != role }.forEach { BadgeChip(it) }
                profile?.let { if (it.joined > 0L) MemberAgeChip(it.joined) }
            }
        }
        Spacer(Modifier.height(10.dp))
        val titles = profile?.titles.orEmpty()
        if (titles.isNotEmpty()) TitleRow(titles, compact = false)
        else Text(S.profileNoTitle, fontSize = 12.sp, color = colors.textMuted)
        if (profile != null && !profile.status.isBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(profile.status, fontSize = 13.sp, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (profile != null) {
            Spacer(Modifier.height(12.dp))
            FollowStatsRow(profile, onOpen = { onOpenFull() })
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(14.dp)).background(colors.textPrimary).clickable(onClick = onEdit),
                contentAlignment = Alignment.Center
            ) { Text(S.editProfile, color = colors.background, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            Box(
                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(14.dp)).background(colors.background.copy(alpha = 0.4f))
                    .border(1.dp, colors.border, RoundedCornerShape(14.dp)).clickable(onClick = onOpenFull),
                contentAlignment = Alignment.Center
            ) { Text(S.profileViewFull, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
        }
    }
}

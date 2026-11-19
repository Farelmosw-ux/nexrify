package id.nexrify.music.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import id.nexrify.music.data.account.AccountError
import id.nexrify.music.data.chat.ChatApi
import id.nexrify.music.data.chat.ChatManager
import id.nexrify.music.data.chat.ChatProfile
import id.nexrify.music.data.chat.FollowUser
import id.nexrify.music.i18n.S
import id.nexrify.music.i18n.currentStrings
import id.nexrify.music.ui.icons.NexIcons
import id.nexrify.music.ui.theme.NexrifyAppTheme
import id.nexrify.music.ui.theme.Typography
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/* ---------------- background profil ---------------- */

private val COVER_PALETTE = listOf(
    Color(0xFF7C5CFF) to Color(0xFF3FA9FF),
    Color(0xFFFF7A59) to Color(0xFFFF3D7F),
    Color(0xFF19C37D) to Color(0xFF0E7C86),
    Color(0xFFF7B733) to Color(0xFFFC4A1A),
    Color(0xFF5B86E5) to Color(0xFF36D1DC),
    Color(0xFFB24592) to Color(0xFFF15F79)
)

/**
 * Background di belakang foto profil. Kalau pengguna belum memasang gambar, dipakai gradasi yang
 * dipilih dari username supaya setiap profil tetap punya warna sendiri.
 */
@Composable
fun ProfileCover(
    profile: ChatProfile,
    isMe: Boolean,
    busy: Boolean,
    onPick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (c1, c2) = COVER_PALETTE[(profile.username.hashCode() and 0x7fffffff) % COVER_PALETTE.size]
    Box(
        modifier
            .fillMaxWidth()
            .height(136.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(c1, c2)))
    ) {
        if (profile.hasCover) {
            AsyncImage(
                model = ChatApi.coverUrl(profile.uid, profile.coverV),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        // Gradasi tipis di bawah supaya foto profil dan teks tetap terbaca di atas gambar apa pun.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.28f)))
            )
        )
        if (isMe) {
            Row(Modifier.align(Alignment.TopEnd).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (profile.hasCover) CoverIconButton(NexIcons.DeleteOutline, busy, onRemove)
                CoverIconButton(NexIcons.Edit, busy, onPick)
            }
        }
        if (busy) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
            }
        }
    }
}

@Composable
private fun CoverIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, busy: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).clickable(enabled = !busy, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
}

/** Kompres gambar pilihan pengguna jadi background 2:1 (maks 1080 px lebar) berupa data URL JPEG di bawah batas server. */
internal fun toCoverDataUrl(context: Context, uri: Uri, maxWidth: Int = 1080, maxChars: Int = 250_000): String {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
    val decoded = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: error("decode")
    // Potong tengah ke rasio 2:1.
    val w = decoded.width
    val h = decoded.height
    val cropW: Int
    val cropH: Int
    if (w >= h * 2) { cropH = h; cropW = h * 2 } else { cropW = w; cropH = w / 2 }
    val cropped = Bitmap.createBitmap(decoded, (w - cropW) / 2, (h - cropH) / 2, cropW, cropH)
    val tw = minOf(cropW, maxWidth)
    val scaled = if (tw == cropW) cropped else Bitmap.createScaledBitmap(cropped, tw, tw / 2, true)
    var result = ""
    for (quality in intArrayOf(86, 78, 70, 62, 54, 46, 38)) {
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        result = "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        if (result.length <= maxChars) break
    }
    check(result.length <= maxChars) { "too large" }
    return result
}

/* ---------------- statistik + tombol ikuti ---------------- */

private fun compactCount(n: Int): String = when {
    n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000f).replace(".0M", "M")
    n >= 10_000 -> String.format("%.1fK", n / 1_000f).replace(".0K", "K")
    else -> n.toString()
}

/** Tiga angka: Pengikut, Mengikuti, Teman. Diketuk membuka daftarnya. */
@Composable
fun FollowStatsRow(profile: ChatProfile, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = NexrifyAppTheme.colors
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.card)
            .border(1.dp, colors.border, RoundedCornerShape(16.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatCell(compactCount(profile.followers), S.socialFollowers, Modifier.weight(1f)) { onOpen("followers") }
        Box(Modifier.width(1.dp).height(28.dp).background(colors.border))
        StatCell(compactCount(profile.following), S.socialFollowing, Modifier.weight(1f)) { onOpen("following") }
        Box(Modifier.width(1.dp).height(28.dp).background(colors.border))
        StatCell(compactCount(profile.friends), S.socialFriends, Modifier.weight(1f)) { onOpen("friends") }
    }
}

@Composable
private fun StatCell(value: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    val colors = NexrifyAppTheme.colors
    Column(modifier.clickable(onClick = onClick).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary)
        Text(label, fontSize = 11.sp, color = colors.textMuted)
    }
}

/** Label hubungan di dekat nama: Teman, atau Mengikutimu. */
@Composable
fun RelationTag(iFollow: Boolean, followsMe: Boolean, friend: Boolean) {
    val colors = NexrifyAppTheme.colors
    val text = when {
        friend -> S.socialFriendTag
        followsMe && !iFollow -> S.socialFollowsYou
        else -> return
    }
    Box(
        Modifier.clip(RoundedCornerShape(8.dp)).background(if (friend) colors.textPrimary else colors.card)
            .border(1.dp, if (friend) colors.textPrimary else colors.border, RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (friend) colors.background else colors.textSecondary)
    }
}

/** Tombol Ikuti / Ikuti balik / Mengikuti. [compact] untuk baris daftar. */
@Composable
fun FollowButton(iFollow: Boolean, followsMe: Boolean, busy: Boolean, compact: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = NexrifyAppTheme.colors
    val filled = !iFollow
    val label = when {
        iFollow -> S.socialFollowing
        followsMe -> S.socialFollowBack
        else -> S.socialFollow
    }
    val shape = RoundedCornerShape(if (compact) 18.dp else 24.dp)
    Box(
        modifier
            .height(if (compact) 34.dp else 46.dp)
            .clip(shape)
            .background(if (filled) colors.textPrimary else Color.Transparent)
            .border(1.dp, if (filled) colors.textPrimary else colors.border, shape)
            .clickable(enabled = !busy, onClick = onClick)
            .padding(horizontal = if (compact) 14.dp else 20.dp),
        contentAlignment = Alignment.Center
    ) {
        if (busy) CircularProgressIndicator(color = if (filled) colors.background else colors.textPrimary, strokeWidth = 2.dp, modifier = Modifier.size(if (compact) 16.dp else 20.dp))
        else Text(label, fontSize = if (compact) 12.sp else 14.sp, fontWeight = FontWeight.Bold, color = if (filled) colors.background else colors.textPrimary, maxLines = 1)
    }
}

/** Jalankan ikuti / berhenti mengikuti dengan pesan galat yang ramah. Mengembalikan profil target terbaru, atau null kalau gagal. */
suspend fun runFollow(username: String, currentlyFollowing: Boolean): ChatProfile? {
    val m = ChatManager.getInstance()
    return try {
        val p = if (currentlyFollowing) m.unfollow(username) else m.follow(username)
        val st = currentStrings()
        if (currentlyFollowing) NexNotifier.info(st.socialUnfollowOk)
        else NexNotifier.success(if (p.friend) st.socialFriendOk else st.socialFollowOk)
        p
    } catch (e: CancellationException) {
        throw e
    } catch (e: AccountError) {
        val st = currentStrings()
        NexNotifier.error(if (e.code == "blocked") st.socialBlocked else st.socialFail)
        null
    } catch (_: Throwable) {
        NexNotifier.error(currentStrings().socialFail)
        null
    }
}

/* ---------------- daftar pengikut / mengikuti / teman ---------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowListSheet(username: String, startKind: String, onDismiss: () -> Unit, onOpenProfile: (String) -> Unit) {
    val colors = NexrifyAppTheme.colors
    val manager = remember { ChatManager.getInstance() }
    val scope = rememberCoroutineScope()
    val me by manager.me.collectAsState()
    var kind by remember { mutableStateOf(startKind) }
    val rows = remember { mutableStateListOf<FollowUser>() }
    var next by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var hidden by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var busyUid by remember { mutableStateOf("") }

    suspend fun load(reset: Boolean) {
        loading = true
        if (reset) { rows.clear(); next = 0; hidden = false; failed = false }
        val page = manager.followPage(username, kind, if (reset) 0 else next)
        if (page == null) failed = true
        else {
            hidden = page.hidden
            val known = rows.map { it.uid }.toSet()
            rows.addAll(page.items.filter { it.uid !in known })
            next = page.next
        }
        loading = false
    }

    LaunchedEffect(kind) { load(true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { Box(Modifier.padding(vertical = 10.dp).width(36.dp).height(4.dp).clip(CircleShape).background(colors.border)) }
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
            Text("@$username", style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("followers" to S.socialFollowers, "following" to S.socialFollowing, "friends" to S.socialFriends).forEach { (k, label) ->
                    val on = kind == k
                    Box(
                        Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(19.dp))
                            .background(if (on) colors.textPrimary else Color.Transparent)
                            .border(1.dp, if (on) colors.textPrimary else colors.border, RoundedCornerShape(19.dp))
                            .clickable { if (!on) kind = k },
                        contentAlignment = Alignment.Center
                    ) { Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = if (on) colors.background else colors.textPrimary) }
                }
            }
            Spacer(Modifier.height(8.dp))

            when {
                hidden -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(S.socialListHidden, color = colors.textMuted, fontSize = 13.sp)
                }
                rows.isEmpty() && loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.textPrimary, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                }
                rows.isEmpty() -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (failed) S.socialFail else when (kind) {
                            "followers" -> S.socialEmptyFollowers
                            "following" -> S.socialEmptyFollowing
                            else -> S.socialEmptyFriends
                        },
                        color = colors.textMuted, fontSize = 13.sp
                    )
                }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 4.dp)) {
                    items(rows, key = { it.uid }) { u ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onOpenProfile(u.username) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ChatAvatar(u.uid, u.av, u.display, 46.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(u.display, fontWeight = FontWeight.Bold, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("@${u.username}", fontSize = 12.sp, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                    if (u.friend || (u.followsMe && !u.iFollow)) {
                                        Spacer(Modifier.width(6.dp))
                                        RelationTag(u.iFollow, u.followsMe, u.friend)
                                    }
                                }
                            }
                            if (u.uid != me?.uid) {
                                Spacer(Modifier.width(8.dp))
                                FollowButton(u.iFollow, u.followsMe, busy = busyUid == u.uid, compact = true) {
                                    scope.launch {
                                        busyUid = u.uid
                                        val p = runFollow(u.username, u.iFollow)
                                        if (p != null) {
                                            val i = rows.indexOfFirst { it.uid == u.uid }
                                            if (i >= 0) rows[i] = rows[i].copy(iFollow = p.iFollow, followsMe = p.followsMe, friend = p.friend)
                                        }
                                        busyUid = ""
                                    }
                                }
                            }
                        }
                    }
                    if (next > 0) item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                            if (loading) CircularProgressIndicator(color = colors.textPrimary, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                            else Text(
                                S.socialLoadMore, fontWeight = FontWeight.SemiBold, color = colors.textPrimary,
                                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { scope.launch { load(false) } }.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

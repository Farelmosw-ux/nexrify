package id.nexrify.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.nexrify.music.data.account.AccountError
import id.nexrify.music.data.chat.ChatManager
import id.nexrify.music.data.chat.ChatProfile
import id.nexrify.music.data.chat.CreatorForm
import id.nexrify.music.data.chat.ProfileTitle
import id.nexrify.music.data.chat.TitleProgress
import id.nexrify.music.i18n.S
import id.nexrify.music.i18n.currentStrings
import id.nexrify.music.ui.icons.NexIcons
import id.nexrify.music.ui.theme.NexrifyAppTheme
import id.nexrify.music.ui.theme.Typography
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/* ---------------- chip title ---------------- */

private val TikCyan = Color(0xFF25F4EE)
private val TikPink = Color(0xFFFE2C55)
private val YtRed = Color(0xFFFF2D3D)

/** Urutan katalog title di pemilih. */
private val CATALOG = listOf("donor", "marathon", "streak", "creator", "mubar", "hours", "circle")

@Composable
private fun GradientChip(
    icons: List<ImageVector>,
    label: String,
    brush: Brush,
    ink: Color,
    compact: Boolean,
    modifier: Modifier = Modifier,
    borderBrush: Brush? = null
) {
    val shape = RoundedCornerShape(if (compact) 7.dp else 11.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(brush)
            .then(if (borderBrush != null) Modifier.border(1.dp, borderBrush, shape) else Modifier)
            .padding(horizontal = if (compact) 5.dp else 10.dp, vertical = if (compact) 1.5.dp else 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icons.forEach { Icon(it, null, tint = ink, modifier = Modifier.size(if (compact) 10.dp else 15.dp)) }
        Spacer(Modifier.width(if (compact) 3.dp else 6.dp))
        Text(label, fontSize = if (compact) 8.5.sp else 12.sp, fontWeight = FontWeight.ExtraBold, color = ink, maxLines = 1)
    }
}

/** Satu title. [compact] untuk di samping nama (chat), versi besar untuk profil dan pemilih. */
@Composable
fun TitleChip(t: ProfileTitle, compact: Boolean = true, modifier: Modifier = Modifier) {
    when (t.code) {
        "donor" -> if (t.rank in 1..3) TopTag(t.rank, modifier)
        "creator" -> {
            val tt = t.platform == "tiktok"
            val yt = t.platform == "youtube"
            when {
                tt -> GradientChip(
                    listOf(NexIcons.Tiktok), S.titleCreator,
                    Brush.linearGradient(listOf(Color(0xFF050507), Color(0xFF1A1A22))), Color.White, compact, modifier,
                    Brush.linearGradient(listOf(TikCyan, TikPink))
                )
                yt -> GradientChip(
                    listOf(NexIcons.Youtube), S.titleCreator,
                    Brush.linearGradient(listOf(Color(0xFFFF3B47), Color(0xFFB3001B))), Color.White, compact, modifier
                )
                else -> GradientChip(
                    listOf(NexIcons.Tiktok, NexIcons.Youtube), S.titleCreator,
                    Brush.linearGradient(listOf(Color(0xFF050507), Color(0xFF3A0A14))), Color.White, compact, modifier,
                    Brush.linearGradient(listOf(TikCyan, YtRed))
                )
            }
        }
        "marathon" -> GradientChip(
            listOf(NexIcons.Headphones), if (compact) S.titleMarathonShort else S.titleMarathon,
            Brush.linearGradient(listOf(Color(0xFF7C5CFF), Color(0xFF3FA9FF))), Color.White, compact, modifier
        )
        "mubar" -> GradientChip(
            listOf(NexIcons.Group), S.titleMubar,
            Brush.linearGradient(listOf(Color(0xFF14C8A0), Color(0xFF0B7F8C))), Color.White, compact, modifier
        )
        "hours" -> GradientChip(
            listOf(NexIcons.GraphicEq), S.titleHours,
            Brush.linearGradient(listOf(Color(0xFFFF5FA2), Color(0xFF9B3DFF))), Color.White, compact, modifier
        )
        "circle" -> GradientChip(
            listOf(NexIcons.Favorite), S.titleCircle,
            Brush.linearGradient(listOf(Color(0xFFFFD34D), Color(0xFFFF8A2B))), Color(0xFF2A1400), compact, modifier
        )
        "streak" -> GradientChip(
            listOf(NexIcons.Flame), if (compact || t.n <= 0) S.titleStreak else S.titleStreakLabel(t.n),
            Brush.linearGradient(listOf(Color(0xFFFF9A3D), Color(0xFFFF3D54))), Color(0xFF1B0A04), compact, modifier
        )
    }
}

/** Deretan title (maks 3 sudah dibatasi server). Versi besar membungkus ke baris baru kalau layar sempit. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TitleRow(titles: List<ProfileTitle>, compact: Boolean = true, modifier: Modifier = Modifier) {
    if (titles.isEmpty()) return
    if (compact) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            titles.forEach { TitleChip(it, true) }
        }
    } else {
        FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            titles.forEach { TitleChip(it, false) }
        }
    }
}

/* ---------------- komponen kecil form ---------------- */

@Composable
private fun TitleButton(text: String, filled: Boolean, modifier: Modifier = Modifier, busy: Boolean = false, onClick: () -> Unit) {
    val colors = NexrifyAppTheme.colors
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(if (filled) colors.textPrimary else Color.Transparent)
            .border(1.dp, if (filled) colors.textPrimary else colors.border, shape)
            .clickable(enabled = !busy, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (busy) CircularProgressIndicator(color = if (filled) colors.background else colors.textPrimary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        else Text(text, color = if (filled) colors.background else colors.textPrimary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FormField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "",
    max: Int = 100,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    leading: ImageVector? = null,
    leadingTint: Color? = null
) {
    val colors = NexrifyAppTheme.colors
    Column(modifier) {
        Text(label, fontSize = 11.sp, color = colors.textMuted, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.card)
                .border(1.dp, colors.border, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leading != null) {
                Icon(leading, null, tint = leadingTint ?: colors.textMuted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
            }
            BasicTextField(
                value = value,
                onValueChange = { onChange(if (singleLine) it.take(max).replace("\n", " ") else it.take(max)) },
                singleLine = singleLine,
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                textStyle = TextStyle(color = colors.textPrimary, fontSize = 15.sp),
                cursorBrush = SolidColor(colors.textPrimary),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty() && hint.isNotEmpty()) Text(hint, color = colors.textMuted.copy(alpha = 0.7f), fontSize = 14.sp, maxLines = 1)
                        inner()
                    }
                }
            )
        }
    }
}

/** "tiktok", "youtube", atau null. Pencocokan sederhana di sisi app; server tetap memeriksa ulang. */
fun detectChannelPlatform(input: String): String? {
    val s = input.trim().lowercase().removePrefix("https://").removePrefix("http://")
    if (s.isEmpty()) return null
    val host = s.substringBefore('/').substringBefore('?').removePrefix("www.").removePrefix("m.")
    return when (host) {
        "tiktok.com", "vm.tiktok.com", "vt.tiktok.com" -> "tiktok"
        "youtube.com", "youtu.be" -> "youtube"
        else -> null
    }
}

@Composable
private fun TitleSheetHandle() {
    val colors = NexrifyAppTheme.colors
    Box(Modifier.padding(vertical = 10.dp).width(36.dp).height(4.dp).clip(CircleShape).background(colors.border))
}

/* ---------------- pemilih title (maks 3) ---------------- */

private fun titleName(code: String, s: id.nexrify.music.i18n.Strings) = when (code) {
    "donor" -> s.titleDonor
    "marathon" -> s.titleMarathon
    "streak" -> s.titleStreak
    "creator" -> s.titleCreatorFull
    "mubar" -> s.titleMubar
    "hours" -> s.titleHours
    else -> s.titleCircle
}

private fun titleHow(code: String, s: id.nexrify.music.i18n.Strings) = when (code) {
    "donor" -> s.titleHowDonor
    "marathon" -> s.titleHowMarathon
    "streak" -> s.titleHowStreak
    "creator" -> s.titleHowCreator
    "mubar" -> s.titleHowMubar
    "hours" -> s.titleHowHours
    else -> s.titleHowCircle
}

/** Baris kemajuan misi: angka lawan/pemegang, angka kamu, kebutuhan, dan persen. Null kalau tidak ada datanya. */
private data class ProgressLines(val lines: List<String>, val pct: Int, val done: Boolean)

private fun progressOf(code: String, p: TitleProgress?, owned: Boolean, s: id.nexrify.music.i18n.Strings): ProgressLines? {
    if (p == null) return null
    fun pct(a: Long, b: Long) = if (b <= 0L) 0 else ((a.coerceAtLeast(0L) * 100L) / b).toInt().coerceIn(0, 100)
    return when (code) {
        "marathon" -> {
            val l = buildList {
                if (p.hasHolder && p.holderMe) {
                    add(s.titleHolderYou(s.titleDuration(p.holderValue)))
                    add(s.titleNeedToKeep(s.titleDuration(p.holderValue)))
                } else if (p.hasHolder) {
                    add(s.titleHolderNow(p.holderName, s.titleDuration(p.holderValue)))
                    add(s.titleYourValue(s.titleDuration(p.mine)))
                    add(s.titleNeedToTake(s.titleDuration(p.need)))
                } else {
                    add(s.titleYourValue(s.titleDuration(p.mine)))
                    add(s.titleNeedFirst(s.titleDuration(p.need)))
                }
            }
            ProgressLines(l, if (p.hasHolder && p.holderMe) 100 else pct(p.mine, p.need), owned)
        }
        "streak" -> {
            val l = buildList {
                if (p.hasHolder && p.holderMe) {
                    add(s.titleHolderYou(s.titleDays(p.holderValue)))
                    add(s.titleNeedToKeep(s.titleDays(p.holderValue)))
                } else if (p.hasHolder) {
                    add(s.titleHolderNow(p.holderName, s.titleDays(p.holderValue)))
                    add(s.titleYourValue(s.titleDays(p.mine)))
                    add(s.titleNeedToTake(s.titleDays(p.need)))
                } else {
                    add(s.titleYourValue(s.titleDays(p.mine)))
                    add(s.titleNeedFirst(s.titleDays(p.need)))
                }
            }
            ProgressLines(l, if (p.hasHolder && p.holderMe) 100 else pct(p.mine, p.need), owned)
        }
        "mubar" -> ProgressLines(listOf(s.titleOfRooms(p.cur, p.goal)), if (owned) 100 else pct(p.cur, p.goal), owned)
        "hours" -> ProgressLines(listOf(s.titleOfTime(s.titleDuration(p.cur), s.titleDuration(p.goal))), if (owned) 100 else pct(p.cur, p.goal), owned)
        "circle" -> ProgressLines(listOf(s.titleOfFriends(p.cur, p.goal)), if (owned) 100 else pct(p.cur, p.goal), owned)
        else -> null
    }
}

@Composable
private fun ProgressBlock(pl: ProgressLines) {
    val colors = NexrifyAppTheme.colors
    Column(Modifier.padding(top = 4.dp)) {
        pl.lines.forEach { Text(it, fontSize = 10.5.sp, color = colors.textSecondary, fontWeight = FontWeight.SemiBold, lineHeight = 14.sp) }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(5.dp).clip(CircleShape).background(colors.border)) {
                Box(Modifier.fillMaxWidth(pl.pct / 100f).height(5.dp).clip(CircleShape).background(colors.textPrimary))
            }
            Spacer(Modifier.width(8.dp))
            Text(if (pl.done && pl.pct >= 100) S.titleDone else "${pl.pct}%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
        }
    }
}

/** Contoh tampilan untuk title yang belum dimiliki. */
private fun sampleOf(code: String) = when (code) {
    "donor" -> ProfileTitle("donor", rank = 1)
    "creator" -> ProfileTitle("creator", platform = "both")
    else -> ProfileTitle(code)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitlePickerSheet(profile: ChatProfile, onDismiss: () -> Unit, onSaved: (ChatProfile) -> Unit, onApplyCreator: () -> Unit) {
    val colors = NexrifyAppTheme.colors
    val scope = rememberCoroutineScope()
    val max = profile.titlesMax.coerceAtLeast(1)
    val selected = remember { mutableStateListOf<String>().also { l -> l.addAll(profile.titles.map { it.code }) } }
    var busy by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { TitleSheetHandle() }
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 20.dp)) {
            Text(S.titleSelect, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            Spacer(Modifier.height(2.dp))
            Text(S.titlePickerSub, fontSize = 11.5.sp, color = colors.textMuted, lineHeight = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text(S.titleSelectedCount(selected.size, max), fontSize = 11.5.sp, color = colors.textSecondary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))

            CATALOG.forEach { code ->
                val owned = profile.titlesOwned.firstOrNull { it.code == code }
                val on = code in selected
                val single = code == "marathon" || code == "streak"
                val shape = RoundedCornerShape(12.dp)
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 5.dp).clip(shape)
                        .background(if (on) colors.textPrimary.copy(alpha = 0.08f) else colors.card)
                        .border(if (on) 1.5.dp else 1.dp, if (on) colors.textPrimary else colors.border, shape)
                        .clickable(enabled = owned != null) {
                            if (on) selected.remove(code)
                            else if (selected.size >= max) NexNotifier.warning(currentStrings().titleLimit)
                            else selected.add(code)
                        }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(Modifier.dimUnlessOwned(owned != null), verticalAlignment = Alignment.CenterVertically) {
                            TitleChip(owned ?: sampleOf(code), compact = true)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                titleName(code, S), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(titleHow(code, S), fontSize = 10.sp, color = colors.textMuted, lineHeight = 13.sp, maxLines = 5, overflow = TextOverflow.Ellipsis, modifier = Modifier.dimUnlessOwned(owned != null))
                        val note = when {
                            owned == null -> S.titleLocked
                            single -> S.titleOnlyOne
                            else -> null
                        }
                        if (note != null) Text(note, fontSize = 9.5.sp, color = colors.textSecondary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
                        progressOf(code, profile.titlesInfo[code], owned != null, S)?.let { ProgressBlock(it) }
                    }
                    Spacer(Modifier.width(8.dp))
                    if (owned != null) {
                        Box(
                            Modifier.size(20.dp).clip(CircleShape)
                                .background(if (on) colors.textPrimary else Color.Transparent)
                                .border(1.5.dp, if (on) colors.textPrimary else colors.border, CircleShape),
                            contentAlignment = Alignment.Center
                        ) { if (on) Icon(NexIcons.Check, null, tint = colors.background, modifier = Modifier.size(12.dp)) }
                    } else if (code == "creator") {
                        Box(
                            Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, colors.border, RoundedCornerShape(10.dp))
                                .clickable { onApplyCreator() }.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) { Text(S.creatorApplyCta, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary) }
                    } else {
                        Icon(NexIcons.Lock, null, tint = colors.textMuted, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            TitleButton(S.titleSave, filled = true, busy = busy, modifier = Modifier.fillMaxWidth()) {
                scope.launch {
                    busy = true
                    try {
                        val p = ChatManager.getInstance().setTitles(selected.toList())
                        NexNotifier.success(currentStrings().titleSaved)
                        onSaved(p)
                        onDismiss()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: AccountError) {
                        val st = currentStrings()
                        NexNotifier.error(if (e.code == "not_owned") st.titleNotOwned else if (e.code == "limit") st.titleLimit else st.titleSaveFail)
                    } catch (_: Throwable) {
                        NexNotifier.error(currentStrings().titleSaveFail)
                    } finally {
                        busy = false
                    }
                }
            }
        }
    }
}

/** Title yang belum dimiliki ditampilkan agak pudar. */
private fun Modifier.dimUnlessOwned(owned: Boolean): Modifier = if (owned) this else this.alpha(0.45f)

/* ---------------- formulir Konten Kreator ---------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatorApplySheet(profile: ChatProfile, onDismiss: () -> Unit, onDone: (ChatProfile) -> Unit) {
    val colors = NexrifyAppTheme.colors
    val scope = rememberCoroutineScope()
    val status = profile.creatorApp?.status.orEmpty()
    val links = remember { mutableStateListOf("") }
    var channelName by remember { mutableStateOf("") }
    var whatsapp by remember { mutableStateOf("") }
    var followers by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var agree by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { TitleSheetHandle() }
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TitleChip(ProfileTitle("creator", platform = "both"), compact = false)
            }
            Spacer(Modifier.height(10.dp))
            Text(S.creatorApplyTitle, style = Typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
            Spacer(Modifier.height(4.dp))

            if (status == "pending" || status == "approved") {
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.card)
                        .border(1.dp, colors.border, RoundedCornerShape(16.dp)).padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (status == "approved") NexIcons.Verified else NexIcons.Timer, null, tint = colors.textPrimary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (status == "approved") S.creatorStatusApproved else S.creatorStatusPending,
                            fontWeight = FontWeight.Bold, color = colors.textPrimary
                        )
                    }
                    if (status == "pending") {
                        Spacer(Modifier.height(6.dp))
                        Text(S.creatorStatusPendingSub, fontSize = 12.sp, color = colors.textMuted, lineHeight = 17.sp)
                    }
                }
                Spacer(Modifier.height(14.dp))
                TitleButton(S.close, filled = false, modifier = Modifier.fillMaxWidth()) { onDismiss() }
                return@Column
            }

            if (status == "rejected") {
                Spacer(Modifier.height(6.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.card)
                        .border(1.dp, colors.border, RoundedCornerShape(14.dp)).padding(12.dp)
                ) {
                    Text(S.creatorStatusRejected, fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 13.sp)
                    Spacer(Modifier.height(2.dp))
                    Text(S.creatorStatusRejectedSub, fontSize = 12.sp, color = colors.textMuted)
                }
                Spacer(Modifier.height(6.dp))
            }

            Text(S.creatorFormIntro, style = Typography.bodySmall, color = colors.textMuted, lineHeight = 18.sp)
            Spacer(Modifier.height(16.dp))

            links.forEachIndexed { i, value ->
                val plat = detectChannelPlatform(value)
                val icon = when (plat) { "tiktok" -> NexIcons.Tiktok; "youtube" -> NexIcons.Youtube; else -> NexIcons.LinkIcon }
                val tint = when (plat) { "tiktok" -> TikCyan; "youtube" -> YtRed; else -> null }
                FormField(
                    label = if (links.size > 1) "${S.creatorLinkLabel} ${i + 1}" else S.creatorLinkLabel,
                    value = value, onChange = { links[i] = it }, hint = S.creatorLinkHint, max = 200,
                    keyboard = KeyboardType.Uri, leading = icon, leadingTint = tint
                )
                if (value.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when (plat) { "tiktok" -> S.creatorLinkDetectTiktok; "youtube" -> S.creatorLinkDetectYoutube; else -> S.creatorLinkInvalid },
                        fontSize = 11.sp, color = if (plat == null) Color(0xFFE5484D) else colors.textSecondary, fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            if (links.size < 2) {
                Text(
                    S.creatorAddLink, color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { links.add("") }.padding(vertical = 6.dp, horizontal = 2.dp)
                )
                Spacer(Modifier.height(8.dp))
            }

            FormField(S.creatorChannelName, channelName, { channelName = it }, max = 60)
            Spacer(Modifier.height(12.dp))
            FormField(S.creatorWhatsapp, whatsapp, { whatsapp = it.filter { c -> c.isDigit() || c == '+' || c == ' ' || c == '-' } }, hint = S.creatorWhatsappHint, max = 20, keyboard = KeyboardType.Phone)
            Spacer(Modifier.height(12.dp))
            FormField(S.creatorFollowers, followers, { followers = it.filter { c -> c.isDigit() } }, max = 10, keyboard = KeyboardType.Number)
            Spacer(Modifier.height(12.dp))
            FormField(S.creatorContent, content, { content = it }, max = 80)
            Spacer(Modifier.height(12.dp))
            FormField(S.creatorNote, note, { note = it }, max = 300, singleLine = false)
            Spacer(Modifier.height(16.dp))

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { agree = !agree }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(22.dp).clip(RoundedCornerShape(7.dp))
                        .background(if (agree) colors.textPrimary else Color.Transparent)
                        .border(1.5.dp, if (agree) colors.textPrimary else colors.border, RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center
                ) { if (agree) Icon(NexIcons.Check, null, tint = colors.background, modifier = Modifier.size(15.dp)) }
                Spacer(Modifier.width(10.dp))
                Text(S.creatorAgree, fontSize = 12.5.sp, color = colors.textSecondary, lineHeight = 17.sp, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))

            TitleButton(S.creatorSubmit, filled = true, busy = busy, modifier = Modifier.fillMaxWidth()) {
                val st = currentStrings()
                val filled = links.map { it.trim() }.filter { it.isNotEmpty() }
                when {
                    filled.isEmpty() || filled.any { detectChannelPlatform(it) == null } -> NexNotifier.warning(st.creatorNeedLink)
                    channelName.trim().length < 2 -> NexNotifier.warning(st.creatorNeedName)
                    whatsapp.count { it.isDigit() } < 9 -> NexNotifier.warning(st.creatorNeedContact)
                    !agree -> NexNotifier.warning(st.creatorNeedAgree)
                    else -> scope.launch {
                        busy = true
                        try {
                            val p = ChatManager.getInstance().applyCreator(
                                CreatorForm(filled, channelName.trim(), whatsapp.trim(), followers.toLongOrNull() ?: 0L, content.trim(), note.trim(), true)
                            )
                            NexNotifier.success(currentStrings().creatorSent, currentStrings().creatorApplyTitle)
                            onDone(p)
                            onDismiss()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: AccountError) {
                            val s2 = currentStrings()
                            NexNotifier.error(
                                when (e.code) {
                                    "link" -> s2.creatorNeedLink
                                    "name" -> s2.creatorNeedName
                                    "contact" -> s2.creatorNeedContact
                                    "agree" -> s2.creatorNeedAgree
                                    "pending" -> s2.creatorPending409
                                    "already" -> s2.creatorStatusApproved
                                    "rate_limited" -> s2.creatorRateLimited
                                    else -> s2.chatGenericError
                                }
                            )
                        } catch (_: Throwable) {
                            NexNotifier.error(currentStrings().chatGenericError)
                        } finally {
                            busy = false
                        }
                    }
                }
            }
        }
    }
}

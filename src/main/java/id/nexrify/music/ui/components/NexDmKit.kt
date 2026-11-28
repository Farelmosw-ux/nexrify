package id.nexrify.music.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import id.nexrify.music.ui.icons.NexIcons
import id.nexrify.music.ui.theme.NexrifyAppTheme

/* =====================================================================
 *  NexDmKit: komponen desain bersama untuk pesan pribadi, kontak, dan header Beranda.
 *  Semua warna mengikuti NexrifyAppTheme, jadi tetap rapi di tema gelap maupun terang.
 * ===================================================================== */

/** Warna aksen kecil untuk chip ikon. Dipakai sedikit saja supaya tetap bersih. */
object NexTint {
    val Violet = Color(0xFF8B5CF6)
    val Sky = Color(0xFF38BDF8)
    val Amber = Color(0xFFF59E0B)
    val Emerald = Color(0xFF34D399)
    val Rose = Color(0xFFFB7185)
    val Danger = Color(0xFFE5484D)
}

/** Permukaan lembut: gradasi tipis plus garis tepi halus. */
@Composable
fun Modifier.nexSoftSurface(shape: Shape, strength: Float = 1f): Modifier {
    val c = NexrifyAppTheme.colors
    val hi = c.textPrimary.copy(alpha = (if (c.isDark) 0.10f else 0.07f) * strength)
    val lo = c.textPrimary.copy(alpha = (if (c.isDark) 0.035f else 0.025f) * strength)
    return this
        .clip(shape)
        .background(Brush.verticalGradient(listOf(hi, lo)), shape)
        .border(
            1.dp,
            Brush.verticalGradient(listOf(c.textPrimary.copy(alpha = 0.16f * strength), c.textPrimary.copy(alpha = 0.05f * strength))),
            shape
        )
}

/** Latar ambient: dua cahaya lembut di pojok supaya halaman tidak terasa datar. */
@Composable
fun NexAmbientBackground(
    modifier: Modifier = Modifier,
    tintA: Color = NexTint.Violet,
    tintB: Color = NexTint.Sky,
    content: @Composable BoxScope.() -> Unit
) {
    val c = NexrifyAppTheme.colors
    val strength = if (c.isDark) 1f else 0.55f
    Box(
        modifier
            .background(c.background)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(tintA.copy(alpha = 0.20f * strength), Color.Transparent),
                        center = Offset(size.width * 0.92f, 0f),
                        radius = size.width * 0.95f
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(tintB.copy(alpha = 0.12f * strength), Color.Transparent),
                        center = Offset(0f, size.height * 0.55f),
                        radius = size.width * 0.9f
                    )
                )
            },
        content = content
    )
}

/** Tombol ikon bulat untuk header. */
@Composable
fun NexRoundIcon(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tint: Color? = null,
    contentDescription: String? = null
) {
    val c = NexrifyAppTheme.colors
    Box(
        modifier.size(size).nexSoftSurface(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = tint ?: c.textPrimary, modifier = Modifier.size(size * 0.5f))
    }
}

/** Header halaman: gradasi halus, judul dan subjudul, garis bawah memudar. */
@Composable
fun NexTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val c = NexrifyAppTheme.colors
    Column(modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(c.surface, c.background)))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                NexRoundIcon(NexIcons.ArrowBack, onBack)
                Spacer(Modifier.width(12.dp))
            } else {
                Spacer(Modifier.width(6.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = c.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, color = c.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        NexHairline()
    }
}

/** Garis tipis yang memudar di kedua ujung. */
@Composable
fun NexHairline(modifier: Modifier = Modifier) {
    val c = NexrifyAppTheme.colors
    Box(
        modifier.fillMaxWidth().height(1.dp)
            .background(Brush.horizontalGradient(listOf(Color.Transparent, c.border, Color.Transparent)))
    )
}

/** Chip ikon berwarna lembut (dipakai di menu, sheet, dan baris pengaturan). */
@Composable
fun NexIconChip(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 34.dp) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.34f)).background(tint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.54f))
    }
}

/**
 * Menu titik tiga bergaya kartu kaca. Taruh di dalam Box yang sama dengan tombol pemicunya:
 *   Box { NexRoundIcon(...) { open = true }; NexPopupMenu(open, { open = false }) { ... } }
 */
@Composable
fun NexPopupMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 244.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!expanded) return
    val c = NexrifyAppTheme.colors
    val density = LocalDensity.current
    val yOff = with(density) { 48.dp.roundToPx() }
    val shape = RoundedCornerShape(22.dp)
    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(0, yOff),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        val state = remember { MutableTransitionState(false).apply { targetState = true } }
        AnimatedVisibility(
            visibleState = state,
            enter = fadeIn(tween(130)) + scaleIn(initialScale = 0.92f, transformOrigin = TransformOrigin(1f, 0f), animationSpec = tween(170))
        ) {
            Box(Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 18.dp)) {
                Column(
                    modifier
                        .width(width)
                        .shadow(18.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                        .clip(shape)
                        .background(Brush.verticalGradient(listOf(c.card, c.surface)))
                        .border(1.dp, c.textPrimary.copy(alpha = 0.10f), shape)
                        .padding(6.dp),
                    content = content
                )
            }
        }
    }
}

/** Satu baris di dalam NexPopupMenu. */
@Composable
fun NexMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    danger: Boolean = false,
    chevron: Boolean = false
) {
    val c = NexrifyAppTheme.colors
    val chipTint = if (danger) NexTint.Danger else (tint ?: c.textSecondary)
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NexIconChip(icon, chipTint, size = 32.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            color = if (danger) NexTint.Danger else c.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (chevron) Icon(NexIcons.ChevronRight, null, tint = c.textMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun NexMenuDivider() {
    val c = NexrifyAppTheme.colors
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).height(1.dp).background(c.border.copy(alpha = 0.6f)))
}

/** Judul kecil di atas kelompok daftar. */
@Composable
fun NexSectionLabel(text: String, modifier: Modifier = Modifier) {
    val c = NexrifyAppTheme.colors
    Text(
        text.uppercase(),
        color = c.textMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = modifier.padding(horizontal = 6.dp)
    )
}

/** Kartu aksi besar untuk lembar lampiran (grid dua kolom). */
@Composable
fun NexActionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = NexrifyAppTheme.colors
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .nexSoftSurface(shape)
            .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.10f), Color.Transparent)), shape)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        NexIconChip(icon, tint, size = 42.dp)
        Spacer(Modifier.height(12.dp))
        Text(title, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, color = c.textMuted, fontSize = 11.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Kolom input dengan ikon dan garis tepi yang menyala saat fokus. */
@Composable
fun NexField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null
) {
    val c = NexrifyAppTheme.colors
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.fillMaxWidth()
            .clip(shape)
            .background(c.textPrimary.copy(alpha = if (c.isDark) 0.05f else 0.04f), shape)
            .border(1.dp, if (focused) c.textPrimary.copy(alpha = 0.55f) else c.border, shape)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            Icon(leading, null, tint = if (focused) c.textPrimary else c.textMuted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
        }
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(hint, color = c.textMuted, fontSize = 15.sp)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = c.textPrimary, fontSize = 15.sp),
                cursorBrush = SolidColor(c.textPrimary),
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
            )
        }
    }
}

/** Tombol utama berbentuk pil dengan gradasi. */
@Composable
fun NexPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val c = NexrifyAppTheme.colors
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier.fillMaxWidth().height(54.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(c.textPrimary, c.textPrimary.copy(alpha = 0.84f))), shape)
            .clickable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(color = c.background, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, null, tint = c.background, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(text, color = c.background, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Keadaan kosong yang rapi: lingkaran ikon, judul, keterangan. */
@Composable
fun NexEmptyState(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    val c = NexrifyAppTheme.colors
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(76.dp).nexSoftSurface(CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c.textSecondary, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, color = c.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = c.textMuted, fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center)
    }
}

/** Baris pintasan kotak masuk (Permintaan, Terkunci, Arsip). */
@Composable
fun NexShortcutRow(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null
) {
    val c = NexrifyAppTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
            .nexSoftSurface(shape, 0.8f)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NexIconChip(icon, tint, size = 38.dp)
        Spacer(Modifier.width(14.dp))
        Text(label, color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (badge != null) {
            Box(Modifier.clip(CircleShape).background(tint).padding(horizontal = 9.dp, vertical = 3.dp)) {
                Text(badge, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(6.dp))
        Icon(NexIcons.ChevronRight, null, tint = c.textMuted, modifier = Modifier.size(18.dp))
    }
}

package id.nexrify.music.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import id.nexrify.music.ui.theme.GlassBorder
import id.nexrify.music.ui.theme.GlassBorderActive
import id.nexrify.music.ui.theme.NexrifyAppTheme
import id.nexrify.music.ui.theme.SurfaceElevated

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    radius: Dp = 20.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = NexrifyAppTheme.colors
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        colors.card.copy(alpha = if (Build.VERSION.SDK_INT >= 33) 0.68f else 0.88f),
                        colors.surface.copy(alpha = if (Build.VERSION.SDK_INT >= 33) 0.62f else 0.82f),
                        colors.textPrimary.copy(alpha = if (colors.isDark) 0.06f else 0.12f)
                    )
                )
            )
            .border(1.dp, colors.border.copy(alpha = 0.9f), shape)
    ) {
        if (Build.VERSION.SDK_INT >= 33) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            listOf(
                                colors.textPrimary.copy(alpha = if (colors.isDark) 0.20f else 0.28f),
                                colors.accent.copy(alpha = 0.12f),
                                Color.Transparent
                            )
                        )
                    )
                    .blur(30.dp)
            )
        }
        content()
    }
}

@Composable
fun GlassDropdownContainer(
    modifier: Modifier = Modifier,
    radius: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = NexrifyAppTheme.colors
    val shape = RoundedCornerShape(radius)
    Column(
        modifier = modifier
            .shadow(
                elevation = 20.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.65f),
                spotColor = Color.Black.copy(alpha = 0.75f)
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = if (colors.isDark) {
                        listOf(
                            Color(0xFF1B1D24),
                            Color(0xFF121318)
                        )
                    } else {
                        listOf(
                            Color(0xFFFFFFFF),
                            Color(0xFFF4F5F8)
                        )
                    }
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = if (colors.isDark) {
                        listOf(
                            Color.White.copy(alpha = 0.22f),
                            Color.White.copy(alpha = 0.06f)
                        )
                    } else {
                        listOf(
                            Color.Black.copy(alpha = 0.12f),
                            Color.Black.copy(alpha = 0.05f)
                        )
                    }
                ),
                shape = shape
            ),
        content = content
    )
}

fun Modifier.nexGlassCard(
    cornerRadius: Dp = 20.dp,
    isActive: Boolean = false,
    alpha: Float = 0.78f
): Modifier = composed {
    val shape = RoundedCornerShape(cornerRadius)
    val borderBrush = if (isActive) {
        Brush.linearGradient(
            colors = listOf(
                GlassBorderActive,
                Color(0x33A78BFA),
                GlassBorder
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color(0x2EFFFFFF),
                Color(0x0CFFFFFF)
            )
        )
    }

    this
        .clip(shape)
        .background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    SurfaceElevated.copy(alpha = alpha),
                    Color(0xFF0A0B0E).copy(alpha = (alpha + 0.12f).coerceAtMost(0.98f))
                )
            )
        )
        .border(width = 1.dp, brush = borderBrush, shape = shape)
}


/**
 * DropdownMenu tanpa background bawaan, supaya GlassDropdownContainer yang tampil.
 * Material3 versi BOM 2024.05 belum punya parameter containerColor dan shadowElevation,
 * jadi warna surface dibuat transparan lewat tema lokal.
 */
@Composable
fun NexTransparentDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(surface = Color.Transparent),
        shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(0.dp))
    ) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            content = content
        )
    }
}

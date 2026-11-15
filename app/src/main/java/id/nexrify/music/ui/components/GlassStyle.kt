package id.nexrify.music.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.composed
import id.nexrify.music.ui.theme.GlassBorder
import id.nexrify.music.ui.theme.GlassBorderActive
import id.nexrify.music.ui.theme.SurfaceElevated
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import id.nexrify.music.ui.theme.NexrifyAppTheme

/**
 * Shared frosted-glass treatment. Android 13+ gets soft blurred lens highlights;
 * older Android versions retain a lightweight translucent surface and fine border.
 */
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
                            listOf(colors.textPrimary.copy(alpha = if (colors.isDark) 0.20f else 0.28f), colors.accent.copy(alpha = 0.12f), Color.Transparent)
                        )
                    )
                    .blur(if (Build.VERSION.SDK_INT >= 33) 30.dp else 12.dp)
            )
        }
        content()
    }
}

/** Kartu kaca gaya baru (dipakai NexFloatingNavBar dan komponen desain baru). */
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
                Color(0x22A78BFA),
                GlassBorder
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color(0x26FFFFFF),
                Color(0x0AFFFFFF)
            )
        )
    }

    this
        .clip(shape)
        .background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    SurfaceElevated.copy(alpha = alpha),
                    Color(0xFF0C0C10).copy(alpha = alpha + 0.1f)
                )
            )
        )
        .border(width = 1.dp, brush = borderBrush, shape = shape)
}

package id.nexrify.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import id.nexrify.music.ui.theme.GlassBorder
import id.nexrify.music.ui.theme.GlassBorderActive
import id.nexrify.music.ui.theme.SurfaceElevated

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

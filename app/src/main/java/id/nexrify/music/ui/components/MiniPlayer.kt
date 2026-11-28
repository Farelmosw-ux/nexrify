package id.nexrify.music.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import id.nexrify.music.R
import id.nexrify.music.data.model.Track
import id.nexrify.music.data.settings.SettingsManager
import id.nexrify.music.data.settings.ThemeStyle
import id.nexrify.music.i18n.S
import id.nexrify.music.ui.icons.NexIcons
import id.nexrify.music.ui.theme.NexrifyAppTheme
import id.nexrify.music.ui.theme.Typography

@Composable
fun MiniPlayer(
    track: Track,
    isPlaying: Boolean,
    isLoading: Boolean = false,
    progress: Float,
    onPlayPauseClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    onClick: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = NexrifyAppTheme.colors
    val glassStyle by SettingsManager.getInstance().themeStyle.collectAsState()
    val isGlassV2 = glassStyle == ThemeStyle.LIQUID_GLASS_V2
    val shape = RoundedCornerShape(22.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .shadow(
                elevation = 18.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.55f),
                spotColor = Color.Black.copy(alpha = 0.7f)
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = if (colors.isDark) {
                        listOf(
                            Color(0xFF1E212B).copy(alpha = 0.95f),
                            Color(0xFF12141A).copy(alpha = 0.98f)
                        )
                    } else {
                        listOf(
                            Color.White.copy(alpha = 0.96f),
                            colors.card.copy(alpha = 0.95f)
                        )
                    }
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = if (colors.isDark) {
                        listOf(
                            Color.White.copy(alpha = 0.22f),
                            Color.White.copy(alpha = 0.05f),
                            colors.accent.copy(alpha = 0.25f)
                        )
                    } else {
                        listOf(
                            Color.Black.copy(alpha = 0.12f),
                            Color.Black.copy(alpha = 0.04f)
                        )
                    }
                ),
                shape = shape
            )
            .clickable { onClick() }
    ) {
        if (Build.VERSION.SDK_INT >= 33) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                colors.accent.copy(alpha = if (colors.isDark) 0.16f else 0.22f),
                                Color.Transparent
                            )
                        )
                    )
                    .blur(18.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = track.highResThumbnailUrl,
                    contentDescription = track.title,
                    modifier = Modifier.size(46.dp),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                if (isGlassV2) {
                    Text(
                        text = track.artist.uppercase(),
                        style = Typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.accentAlt,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = track.title,
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = track.title,
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = track.artist,
                        style = Typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            if (isLoading) {
                Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = colors.accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (isGlassV2) Brush.linearGradient(listOf(colors.accent, colors.accentAlt))
                            else Brush.linearGradient(listOf(colors.textPrimary, colors.textPrimary.copy(alpha = 0.9f)))
                        )
                        .clickable { onPlayPauseClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = if (isPlaying) painterResource(R.drawable.ic_nexrify_pause) else painterResource(R.drawable.ic_nexrify_play),
                        contentDescription = if (isPlaying) S.cd("Pause") else S.cd("Play"),
                        tint = colors.background,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.textPrimary.copy(alpha = 0.06f))
                    .clickable { onSkipNextClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_nexrify_fast_forward),
                    contentDescription = S.cd("Next"),
                    tint = colors.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (onDismiss != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = NexIcons.KeyboardArrowDown,
                        contentDescription = S.cd("Collapse"),
                        tint = colors.textMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Progress bar terintegrasi di bagian bawah kartu
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.5.dp)
                .background(colors.border.copy(alpha = 0.35f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(2.5.dp)
                    .background(
                        if (isGlassV2) Brush.horizontalGradient(listOf(colors.accent, colors.accentAlt))
                        else Brush.horizontalGradient(listOf(colors.textPrimary, colors.textSecondary))
                    )
            )
        }
    }
}

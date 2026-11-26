package id.nexrify.music.ui.components

import id.nexrify.music.i18n.S
import id.nexrify.music.ui.icons.NexIcons
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.StateFlow
import id.nexrify.music.data.lyrics.LyricsData
import id.nexrify.music.ui.theme.NexrifyAppTheme
import id.nexrify.music.ui.theme.Typography

@Composable
fun SyncedLyricsView(
    lyricsData: LyricsData?,
    isLoading: Boolean,
    playbackPositionFlow: StateFlow<Long>,
    onSeekTo: (Long) -> Unit,
    isExpanded: Boolean = false,
    onExpandToggle: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val currentPositionMs by playbackPositionFlow.collectAsState()
    var showTranslation by remember { mutableStateOf(true) }
    val listState = remember(lyricsData?.trackId) { androidx.compose.foundation.lazy.LazyListState() }

    val activeIndex by remember(lyricsData) {
        derivedStateOf {
            lyricsData?.getCurrentLineIndex(currentPositionMs) ?: -1
        }
    }

    LaunchedEffect(activeIndex, listState, lyricsData?.trackId) {
        if (activeIndex >= 0) {
            val targetScroll = (activeIndex - 1).coerceAtLeast(0)
            if (!listState.isScrollInProgress) {
                try { listState.animateScrollToItem(targetScroll) } catch (_: Exception) {}
            }
        }
    }

    val colors = NexrifyAppTheme.colors
    val isV2 = NexrifyAppTheme.isV2

    // Pastikan kontras kartu lirik selalu solid & terbaca jelas pada Tema V1, V2, maupun tema aksen
    val cardSurface = if (colors.isDark) {
        colors.surface.copy(alpha = 0.94f)
    } else {
        colors.surface.copy(alpha = 0.96f)
    }
    val cardBorderBrush = Brush.verticalGradient(
        listOf(
            colors.accent.copy(alpha = if (isV2) 0.42f else 0.25f),
            colors.border.copy(alpha = 0.85f)
        )
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(cardSurface)
            .border(1.dp, cardBorderBrush, RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Lyrics Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(enabled = onExpandToggle != null) { onExpandToggle?.invoke() }
                ) {
                    Text(
                        text = S.lyrics,
                        style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.accent.copy(alpha = 0.16f))
                            .border(1.dp, colors.accent.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (lyricsData?.isSynced == true) S.karaokeSync else S.lyricsPlain,
                            style = Typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                            color = colors.accent
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (showTranslation) colors.accent else colors.card)
                            .border(1.dp, if (showTranslation) colors.accent else colors.border, RoundedCornerShape(12.dp))
                            .clickable { showTranslation = !showTranslation }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = NexIcons.Translate,
                            contentDescription = S.cd("Translate"),
                            tint = if (showTranslation) colors.background else colors.textPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (showTranslation) S.translateOn else S.translateOff,
                            style = Typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (showTranslation) colors.background else colors.textPrimary
                        )
                    }

                    if (onExpandToggle != null) {
                        IconButton(
                            onClick = onExpandToggle,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isExpanded) NexIcons.KeyboardArrowDown else NexIcons.KeyboardArrowUp,
                                contentDescription = if (isExpanded) S.cd("Collapse") else S.cd("Expand"),
                                tint = colors.textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = colors.accent,
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = S.loadingLyrics,
                            style = Typography.bodyMedium,
                            color = colors.textMuted
                        )
                    }
                }
            } else if (lyricsData == null || lyricsData.lines.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = S.noLyrics,
                            style = Typography.bodyMedium,
                            color = colors.textMuted
                        )
                        if (onRetry != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.accent.copy(alpha = 0.14f))
                                    .border(1.dp, colors.accent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                    .clickable { onRetry() }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(NexIcons.Refresh, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(S.retryLyrics, style = Typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    itemsIndexed(
                        items = lyricsData.lines,
                        key = { index, line -> "${line.timeMs}_$index" }
                    ) { index, line ->
                        val isActive = index == activeIndex
                        val textColor by animateColorAsState(
                            targetValue = if (isActive) colors.textPrimary else colors.textPrimary.copy(alpha = 0.56f),
                            animationSpec = tween(durationMillis = 200),
                            label = "textColor"
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isActive) colors.accent.copy(alpha = 0.10f) else colors.surface.copy(alpha = 0f))
                                .clickable {
                                    if (line.timeMs >= 0) {
                                        onSeekTo(line.timeMs)
                                    }
                                }
                                .padding(horizontal = if (isActive) 8.dp else 2.dp, vertical = 5.dp)
                        ) {
                            if (isActive) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(1.5.dp))
                                        .background(colors.accent)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = line.text,
                                    style = Typography.headlineMedium,
                                    fontSize = if (isActive) 18.sp else 15.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                    color = textColor,
                                    lineHeight = if (isActive) 25.sp else 21.sp
                                )

                                if (showTranslation && line.translation != null) {
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = line.translation!!,
                                        style = Typography.bodyMedium,
                                        fontSize = if (isActive) 13.sp else 12.sp,
                                        fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                                        color = if (isActive) colors.accent else colors.textMuted,
                                        lineHeight = if (isActive) 18.sp else 16.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

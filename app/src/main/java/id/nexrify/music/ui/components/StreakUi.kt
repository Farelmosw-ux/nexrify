package id.nexrify.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.nexrify.music.R
import id.nexrify.music.data.streak.FlameState
import id.nexrify.music.data.streak.StreakInfo
import id.nexrify.music.data.streak.StreakManager
import id.nexrify.music.i18n.S
import id.nexrify.music.ui.theme.NexrifyAppTheme
import id.nexrify.music.ui.theme.Typography

private fun flameRes(state: FlameState) = when (state) {
    FlameState.BLUE -> R.drawable.ic_flame_blue
    FlameState.RED -> R.drawable.ic_flame_red
    else -> R.drawable.ic_flame_gray
}

private fun flameInk(state: FlameState, fallback: Color) = when (state) {
    FlameState.BLUE -> Color(0xFF6FC3FF)
    FlameState.RED -> Color(0xFFFF7A59)
    else -> fallback
}

@Composable
fun FlameIcon(state: FlameState, size: Dp, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Image(
        painter = painterResource(flameRes(state)),
        contentDescription = null,
        modifier = modifier.size(size)
    )
}

/** Chip kecil api + jumlah hari di beranda. Ketuk untuk membuka rincian. */
@Composable
fun StreakChip(info: StreakInfo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = NexrifyAppTheme.colors
    val ink = flameInk(info.state, colors.textMuted)
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.card)
            .border(1.dp, if (info.listenedToday) ink.copy(alpha = 0.55f) else colors.border, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FlameIcon(info.state, 20.dp)
        Spacer(Modifier.width(4.dp))
        Text(info.count.toString(), color = ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun StreakDialog(info: StreakInfo, onDismiss: () -> Unit) {
    val colors = NexrifyAppTheme.colors
    val ink = flameInk(info.state, colors.textSecondary)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .clip(RoundedCornerShape(24.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(24.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(84.dp).clip(CircleShape).background(ink.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) { FlameIcon(info.state, 56.dp) }
            Spacer(Modifier.height(12.dp))
            Text(S.streakDays(info.count), style = Typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                when (info.state) {
                    FlameState.NONE -> S.streakStateNone
                    FlameState.RED -> S.streakStateRed
                    FlameState.BLUE -> S.streakStateBlue
                    FlameState.GRAY -> S.streakStateGray(info.daysLeft)
                },
                color = colors.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            // Progres 1 menit hari ini.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(S.streakToday, color = colors.textMuted, fontSize = 12.sp)
                Text("${info.todaySeconds}/${StreakManager.GOAL_SECONDS} ${S.streakSecondsUnit}", color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(colors.card)) {
                val frac = (if (info.listenedToday) 1f else info.todaySeconds / StreakManager.GOAL_SECONDS.toFloat()).coerceIn(0f, 1f)
                Box(Modifier.fillMaxWidth(frac).height(8.dp).clip(RoundedCornerShape(4.dp)).background(if (info.state == FlameState.NONE || info.state == FlameState.GRAY) colors.textSecondary else ink))
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(S.streakBest, color = colors.textMuted, fontSize = 12.sp)
                Text(S.streakDaysShort(info.best), color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            if (info.count in 1 until StreakManager.BLUE_AT) {
                Spacer(Modifier.height(6.dp))
                Text(S.streakNextLevel(StreakManager.BLUE_AT - info.count), color = colors.textMuted, fontSize = 11.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(14.dp))
            Text(S.streakRules, color = colors.textMuted, fontSize = 11.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.card)
                    .border(1.dp, colors.border, RoundedCornerShape(14.dp)).clickable(onClick = onDismiss).padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) { Text(S.close, color = colors.textPrimary, fontWeight = FontWeight.SemiBold) }
        }
    }
}

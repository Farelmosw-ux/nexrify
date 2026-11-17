package id.nexrify.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.nexrify.music.i18n.S
import id.nexrify.music.ui.theme.NexrifyAppTheme
import kotlinx.coroutines.delay

/** Tingkat umur akun. Dari Member Baru sampai Veteran (1 tahun ke atas). */
private data class MemberTier(val index: Int, val color: Color)

private val TIER_COLORS = listOf(
    Color(0xFF7BE0A0), // Member Baru
    Color(0xFF6FC3FF), // Pendatang
    Color(0xFFB79CFF), // Warga
    Color(0xFFFFA45E), // Anggota Setia
    Color(0xFFFF7D9C), // Senior
    Color(0xFFFFCF4D)  // Veteran
)

private fun tierFor(days: Long): MemberTier {
    val i = when {
        days < 7 -> 0
        days < 30 -> 1
        days < 90 -> 2
        days < 180 -> 3
        days < 365 -> 4
        else -> 5
    }
    return MemberTier(i, TIER_COLORS[i])
}

/**
 * Chip umur akun, dihitung langsung dari tanggal bergabung dan diperbarui tiap 30 detik.
 * Menggantikan lencana "OG / Early member" yang statis.
 */
@Composable
fun MemberAgeChip(joined: Long, modifier: Modifier = Modifier, compact: Boolean = false) {
    if (joined <= 0L) return
    val colors = NexrifyAppTheme.colors
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(30_000L); value = System.currentTimeMillis() }
    }
    val elapsed = (now - joined).coerceAtLeast(0L)
    val days = elapsed / 86_400_000L
    val tier = tierFor(days)
    val shape = RoundedCornerShape(10.dp)
    val bg = if (NexrifyAppTheme.isV2)
        Modifier.background(Brush.horizontalGradient(listOf(tier.color.copy(alpha = 0.26f), tier.color.copy(alpha = 0.08f))), shape)
    else Modifier.background(tier.color.copy(alpha = 0.14f), shape)
    Row(
        modifier = modifier
            .clip(shape)
            .then(bg)
            .border(1.dp, tier.color.copy(alpha = 0.55f), shape)
            .padding(horizontal = if (compact) 6.dp else 9.dp, vertical = if (compact) 1.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(if (compact) 5.dp else 7.dp).clip(CircleShape).background(tier.color))
        Spacer(Modifier.width(if (compact) 4.dp else 6.dp))
        Text(S.memberTier(tier.index), fontSize = if (compact) 9.sp else 11.sp, fontWeight = FontWeight.Bold, color = tier.color)
        if (!compact) Text("  ${S.memberDuration(elapsed)}", fontSize = 11.sp, color = colors.textSecondary)
    }
}

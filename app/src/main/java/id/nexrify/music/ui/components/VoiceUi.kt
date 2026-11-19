package id.nexrify.music.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.nexrify.music.data.chat.ChatVoice
import id.nexrify.music.i18n.S
import id.nexrify.music.ui.icons.NexIcons
import id.nexrify.music.ui.theme.NexrifyAppTheme
import id.nexrify.music.util.VoiceClip
import id.nexrify.music.util.VoicePlayer
import id.nexrify.music.util.VoiceRecorder
import kotlinx.coroutines.delay

private fun mmss(ms: Long): String {
    val s = (ms / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(s / 60, s % 60)
}

private val DEFAULT_WAVE = "3568753468975343568864"

/** Gelembung pesan suara: tombol putar, gelombang yang bisa diketuk untuk loncat, durasi, dan kecepatan. */
@Composable
fun VoiceBubble(voice: ChatVoice, mine: Boolean) {
    val colors = NexrifyAppTheme.colors
    val ctx = LocalContext.current
    val st by VoicePlayer.state.collectAsState()
    val active = st.id == voice.id
    val fg = if (mine) colors.background else colors.textPrimary
    val dim = fg.copy(alpha = 0.3f)
    val knob = if (mine) colors.textPrimary else colors.background
    val dur = if (active && st.durMs > 0) st.durMs.toLong() else voice.durMs
    val progress = if (active && st.durMs > 0) (st.posMs / st.durMs.toFloat()).coerceIn(0f, 1f) else 0f
    val bars = remember(voice.wave) { (voice.wave.takeIf { it.length >= 8 } ?: DEFAULT_WAVE).map { (it - '0').coerceIn(0, 9) } }

    Row(Modifier.width(224.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(fg).clickable { VoicePlayer.toggle(ctx, voice.id) },
            contentAlignment = Alignment.Center
        ) {
            if (active && st.loading) CircularProgressIndicator(color = knob, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            else Icon(if (active && st.playing) NexIcons.Pause else NexIcons.PlayArrow, null, tint = knob, modifier = Modifier.size(if (active && st.playing) 20.dp else 18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Canvas(
                Modifier.fillMaxWidth().height(30.dp).pointerInput(voice.id, active) {
                    detectTapGestures { o -> if (active) VoicePlayer.seekTo(o.x / size.width) else VoicePlayer.toggle(ctx, voice.id) }
                }
            ) {
                val n = bars.size
                val gap = 2.dp.toPx()
                val bw = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
                bars.forEachIndexed { i, v ->
                    val h = (4.dp.toPx() + (size.height - 4.dp.toPx()) * (v / 9f)).coerceAtMost(size.height)
                    drawRoundRect(
                        color = if ((i + 0.5f) / n <= progress) fg else dim,
                        topLeft = Offset(i * (bw + gap), (size.height - h) / 2f),
                        size = Size(bw, h), cornerRadius = CornerRadius(bw / 2f)
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (active && st.posMs > 0) mmss(st.posMs.toLong()) + " / " + mmss(dur) else mmss(dur), fontSize = 11.sp, color = fg.copy(alpha = 0.7f))
                if (active && !st.loading) {
                    Text(
                        (if (st.speed == 1f) "1" else if (st.speed == 1.5f) "1.5" else "2") + "x", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = fg,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, fg.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .clickable { VoicePlayer.cycleSpeed() }.padding(horizontal = 7.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

/**
 * Bilah rekam menggantikan kolom ketik: tong sampah (batal), titik merah + timer + gelombang langsung, dan tombol kirim.
 * Otomatis kirim saat mencapai 60 detik.
 */
@Composable
fun VoiceRecordBar(recorder: VoiceRecorder, sending: Boolean, onCancel: () -> Unit, onSend: (VoiceClip) -> Unit, onTooShort: () -> Unit) {
    val colors = NexrifyAppTheme.colors
    val levels = remember { mutableStateListOf<Int>() }
    var elapsed by remember { mutableStateOf(0L) }
    val pulse by rememberInfiniteTransition(label = "rec").animateFloat(0.25f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "dot")

    fun finish() {
        val clip = recorder.stop()
        if (clip == null) onTooShort() else onSend(clip)
    }

    LaunchedEffect(Unit) {
        while (recorder.isRecording) {
            levels.add(recorder.sample())
            if (levels.size > 28) levels.removeAt(0)
            elapsed = recorder.elapsedMs()
            if (elapsed >= VoiceRecorder.MAX_MS - 150L) { finish(); break }
            delay(80L)
        }
    }

    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(colors.card).border(1.dp, colors.border, CircleShape).clickable(enabled = !sending) { recorder.cancel(); onCancel() },
            contentAlignment = Alignment.Center
        ) { Icon(NexIcons.DeleteOutline, S.voiceCancel, tint = Color(0xFFE5484D), modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(8.dp))
        Row(
            Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(26.dp)).background(colors.card).border(1.dp, colors.border, RoundedCornerShape(26.dp)).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFE5484D).copy(alpha = pulse)))
            Spacer(Modifier.width(8.dp))
            Text(mmss(elapsed), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            Spacer(Modifier.width(12.dp))
            Canvas(Modifier.weight(1f).height(26.dp)) {
                val n = 28
                val gap = 2.dp.toPx()
                val bw = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
                val pad = n - levels.size
                for (i in 0 until n) {
                    val v = if (i < pad) 0 else levels[i - pad]
                    val h = 3.dp.toPx() + (size.height - 3.dp.toPx()) * (v / 9f)
                    drawRoundRect(
                        color = if (i < pad) colors.border else colors.textPrimary,
                        topLeft = Offset(i * (bw + gap), (size.height - h) / 2f), size = Size(bw, h), cornerRadius = CornerRadius(bw / 2f)
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(colors.textPrimary).clickable(enabled = !sending) { finish() },
            contentAlignment = Alignment.Center
        ) {
            if (sending) CircularProgressIndicator(color = colors.background, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            else Icon(NexIcons.Send, S.voiceSend, tint = colors.background, modifier = Modifier.size(22.dp))
        }
    }
}

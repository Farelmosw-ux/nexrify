package id.nexrify.music.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import id.nexrify.music.data.call.CallNav
import id.nexrify.music.data.call.CallManager
import id.nexrify.music.data.call.CallPhase
import id.nexrify.music.i18n.S
import id.nexrify.music.i18n.currentStrings
import id.nexrify.music.ui.icons.NexIcons

private val CALL_BG = Color(0xFF0E0E10)
private val CALL_GREEN = Color(0xFF16A34A)
private val CALL_RED = Color(0xFFE5484D)
private val CALL_BTN = Color(0xFF2A2A2E)

/** Teks keterangan untuk kode galat/hasil panggilan dari [CallManager.errors] atau server. */
fun callErrText(code: String): String {
    val s = currentStrings()
    return when (code) {
        "call_busy" -> s.callErrBusy
        "call_busy_me" -> s.callErrBusyMe
        "call_not_allowed" -> s.callErrNotAllowed
        "dm_blocked" -> s.dmErrBlocked
        "call_declined" -> s.callDeclined
        "call_unanswered" -> s.callUnanswered
        "call_failed" -> s.callErrFailed
        "network" -> s.dmNoNetworkErr
        "rate_limited" -> s.dmSlowDown
        "chat_banned" -> s.dmBannedErr
        else -> s.dmGenericErr
    }
}

/**
 * Mengembalikan fungsi yang memastikan izin mikrofon sebelum menjalankan [onGranted]: langsung kalau sudah diizinkan,
 * kalau belum meminta izin dulu.
 */
@Composable
fun rememberMicPermission(onGranted: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val latest by rememberUpdatedState(onGranted)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) latest() else NexNotifier.warning(currentStrings().callMicNeeded, currentStrings().callTitle)
    }
    return {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) latest()
        else launcher.launch(Manifest.permission.RECORD_AUDIO)
    }
}

private fun clock(sec: Int): String =
    if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, (sec % 3600) / 60, sec % 60) else "%02d:%02d".format(sec / 60, sec % 60)

/** Tampilan panggilan: layar penuh (dialog) saat masuk, keluar, dan berlangsung; bisa diperkecil menjadi bilah hijau di atas. */
@Composable
fun CallOverlay() {
    val cm = remember { CallManager.getInstance() }
    val phase by cm.phase.collectAsState()
    val peer by cm.peer.collectAsState()
    val muted by cm.muted.collectAsState()
    val speaker by cm.speaker.collectAsState()
    val seconds by cm.seconds.collectAsState()
    val answerReq by CallNav.answerRequested.collectAsState()
    var minimized by remember { mutableStateOf(false) }
    val answer = rememberMicPermission { cm.accept() }

    LaunchedEffect(phase) { if (phase == CallPhase.IDLE || phase == CallPhase.INCOMING) minimized = false }
    // Tombol Jawab di notifikasi: jawab otomatis begitu panggilan masuk sudah terbaca (atau batalkan permintaan kalau tak kunjung datang).
    LaunchedEffect(answerReq, phase) {
        if (!answerReq) return@LaunchedEffect
        if (phase == CallPhase.INCOMING) {
            CallNav.answerRequested.value = false
            answer()
        } else {
            kotlinx.coroutines.delay(20_000L)
            CallNav.answerRequested.value = false
        }
    }
    LaunchedEffect(Unit) { cm.errors.collect { code ->
        val d = cm.lastDetail.take(120)
        NexNotifier.warning(callErrText(code) + if (d.isNotBlank()) "\n($d)" else "", currentStrings().callTitle)
    } }

    val p = peer
    if (phase == CallPhase.IDLE || p == null) return
    val name = p.display.ifBlank { p.username }
    val status = when (phase) {
        CallPhase.OUTGOING -> S.callOutgoing
        CallPhase.INCOMING -> S.callIncoming
        CallPhase.CONNECTING -> S.callConnecting
        else -> clock(seconds)
    }

    if (minimized && phase != CallPhase.INCOMING) {
        Row(
            Modifier.fillMaxWidth().background(CALL_GREEN).statusBarsPadding().clickable { minimized = false }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(NexIcons.Phone, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text("$name  ·  $status", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Box(Modifier.size(28.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)).clickable { cm.hangup() }, contentAlignment = Alignment.Center) {
                Icon(NexIcons.PhoneOff, S.callHangup, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    } else {
        Dialog(
            onDismissRequest = { if (phase != CallPhase.INCOMING) minimized = true },
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false, usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            CallScreen(
                phase = phase, name = name, status = status, uid = p.uid, av = p.av,
                muted = muted, speaker = speaker,
                onMinimize = { minimized = true },
                onMute = { cm.toggleMute() },
                onSpeaker = { cm.toggleSpeaker() },
                onHangup = { cm.hangup() },
                onDecline = { cm.decline() },
                onAnswer = { answer() }
            )
        }
    }
}

@Composable
private fun CallScreen(
    phase: CallPhase, name: String, status: String, uid: String, av: String,
    muted: Boolean, speaker: Boolean,
    onMinimize: () -> Unit, onMute: () -> Unit, onSpeaker: () -> Unit,
    onHangup: () -> Unit, onDecline: () -> Unit, onAnswer: () -> Unit
) {
    val ringing = phase == CallPhase.OUTGOING || phase == CallPhase.INCOMING || phase == CallPhase.CONNECTING
    val incoming = phase == CallPhase.INCOMING
    val inf = rememberInfiniteTransition(label = "call")
    // Tiga cincin yang melebar bergantian di belakang foto; lebih lambat dan tipis saat sudah tersambung.
    val period = if (ringing) 2400 else 4200
    val r1 by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(period, easing = LinearEasing), RepeatMode.Restart), label = "r1")
    val r2 by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(period, delayMillis = period / 3, easing = LinearEasing), RepeatMode.Restart), label = "r2")
    val r3 by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(period, delayMillis = period * 2 / 3, easing = LinearEasing), RepeatMode.Restart), label = "r3")
    val blink by inf.animateFloat(0.45f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "blink")
    val breathe by inf.animateFloat(0.97f, 1.03f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "breathe")

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF1B1B20), CALL_BG, Color(0xFF050506)))
        )
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // bar atas: perkecil + judul
            Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
                if (!incoming) {
                    Box(
                        Modifier.align(Alignment.CenterStart).size(44.dp).clip(CircleShape).clickable(onClick = onMinimize),
                        contentAlignment = Alignment.Center
                    ) { Icon(NexIcons.ArrowBack, S.callMinimize, tint = Color.White, modifier = Modifier.size(22.dp)) }
                }
                Text(
                    "Nexrify · " + currentStrings().callTitle,
                    color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1
                )
            }

            Spacer(Modifier.weight(0.9f))

            // foto dengan cincin denyut
            Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
                listOf(r1, r2, r3).forEach { t ->
                    Box(
                        Modifier.size(150.dp).scale(1f + t * 0.75f).alpha((1f - t) * (if (ringing) 0.40f else 0.16f))
                            .border(2.dp, Color.White, CircleShape)
                    )
                }
                Box(
                    Modifier.size(156.dp).scale(if (ringing) 1f else breathe).clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f)).border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center
                ) { ChatAvatar(uid, av, name, 148.dp) }
            }

            Spacer(Modifier.height(20.dp))
            Text(name, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                status,
                color = Color.White.copy(alpha = 0.72f),
                fontSize = if (phase == CallPhase.ACTIVE) 20.sp else 16.sp,
                fontWeight = if (phase == CallPhase.ACTIVE) FontWeight.Medium else FontWeight.Normal,
                letterSpacing = if (phase == CallPhase.ACTIVE) 1.sp else 0.sp,
                modifier = Modifier.alpha(if (phase == CallPhase.ACTIVE) 1f else blink)
            )

            Spacer(Modifier.weight(1f))

            if (incoming) {
                Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RoundCallButton(NexIcons.PhoneOff, S.callDecline, CALL_RED, size = 76.dp) { onDecline() }
                    RoundCallButton(NexIcons.Phone, S.callAnswer, CALL_GREEN, size = 76.dp, pulse = blink) { onAnswer() }
                }
            } else {
                // panel kontrol
                val panel = RoundedCornerShape(36.dp)
                Column(
                    Modifier.fillMaxWidth().clip(panel).background(Color.White.copy(alpha = 0.07f))
                        .border(1.dp, Color.White.copy(alpha = 0.10f), panel).padding(horizontal = 20.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        RoundCallButton(NexIcons.Mic, if (muted) S.callUnmute else S.callMute, if (muted) Color.White else CALL_BTN, dark = muted) { onMute() }
                        RoundCallButton(NexIcons.VolumeUp, S.callSpeaker, if (speaker) Color.White else CALL_BTN, dark = speaker) { onSpeaker() }
                        RoundCallButton(NexIcons.PhoneOff, S.callHangup, CALL_RED) { onHangup() }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun RoundCallButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, bg: Color,
    dark: Boolean = false, size: androidx.compose.ui.unit.Dp = 68.dp, pulse: Float = 1f, onClick: () -> Unit
) {
    val color by animateColorAsState(bg, label = "btn")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            if (pulse < 1f) Box(Modifier.size(size + 14.dp).scale(1f + (1f - pulse) * 0.3f).alpha(pulse * 0.35f).clip(CircleShape).background(color))
            Box(Modifier.size(size).clip(CircleShape).background(color).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
                Icon(icon, label, tint = if (dark) Color.Black else Color.White, modifier = Modifier.size(size * 0.41f))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White.copy(alpha = 0.82f), fontSize = 12.sp, maxLines = 1)
    }
}

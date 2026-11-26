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
import id.nexrify.music.data.call.CallManager
import id.nexrify.music.data.call.CallPhase
import id.nexrify.music.i18n.S
import id.nexrify.music.ui.icons.NexIcons

private val CALL_BG = Color(0xFF0E0E10)
private val CALL_GREEN = Color(0xFF16A34A)
private val CALL_RED = Color(0xFFE5484D)
private val CALL_BTN = Color(0xFF2A2A2E)

/** Teks keterangan untuk kode galat/hasil panggilan dari [CallManager.errors] atau server. */
fun callErrText(code: String): String = when (code) {
    "call_busy" -> S.callErrBusy
    "call_busy_me" -> S.callErrBusyMe
    "call_not_allowed" -> S.callErrNotAllowed
    "dm_blocked" -> S.dmErrBlocked
    "call_declined" -> S.callDeclined
    "call_unanswered" -> S.callUnanswered
    "call_failed" -> S.callErrFailed
    "network" -> S.dmNoNetworkErr
    "rate_limited" -> S.dmSlowDown
    "chat_banned" -> S.dmBannedErr
    else -> S.dmGenericErr
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
        if (granted) latest() else NexNotifier.warning(S.callMicNeeded, S.callTitle)
    }
    return {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) latest()
        else launcher.launch(Manifest.permission.RECORD_AUDIO)
    }
}

private fun clock(sec: Int): String = "%02d:%02d".format(sec / 60, sec % 60)

/** Tampilan panggilan: layar penuh (dialog) saat masuk, keluar, dan berlangsung; bisa diperkecil menjadi bilah hijau di atas. */
@Composable
fun CallOverlay() {
    val cm = remember { CallManager.getInstance() }
    val phase by cm.phase.collectAsState()
    val peer by cm.peer.collectAsState()
    val muted by cm.muted.collectAsState()
    val speaker by cm.speaker.collectAsState()
    val seconds by cm.seconds.collectAsState()
    var minimized by remember { mutableStateOf(false) }
    val answer = rememberMicPermission { cm.accept() }

    LaunchedEffect(phase) { if (phase == CallPhase.IDLE || phase == CallPhase.INCOMING) minimized = false }
    LaunchedEffect(Unit) { cm.errors.collect { code -> NexNotifier.warning(callErrText(code), S.callTitle) } }

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
            Modifier.fillMaxWidth().background(CALL_GREEN).clickable { minimized = false }.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(NexIcons.Phone, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text("$name  ·  $status", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
        }
    } else {
        Dialog(
            onDismissRequest = { if (phase != CallPhase.INCOMING) minimized = true },
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false, usePlatformDefaultWidth = false)
        ) {
            Column(
                Modifier.fillMaxSize().background(CALL_BG).padding(horizontal = 24.dp, vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    if (phase != CallPhase.INCOMING) {
                        Box(Modifier.size(40.dp).clip(CircleShape).clickable { minimized = true }, contentAlignment = Alignment.Center) {
                            Icon(NexIcons.ArrowBack, S.callMinimize, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                    }
                }
                Spacer(Modifier.height(48.dp))
                ChatAvatar(p.uid, p.av, name, 128.dp)
                Spacer(Modifier.height(24.dp))
                Text(name, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(status, color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                if (phase == CallPhase.INCOMING) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        RoundCallButton(NexIcons.PhoneOff, S.callDecline, CALL_RED) { cm.decline() }
                        RoundCallButton(NexIcons.Phone, S.callAnswer, CALL_GREEN) { answer() }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        RoundCallButton(NexIcons.Mic, if (muted) S.callUnmute else S.callMute, if (muted) Color.White else CALL_BTN, dark = muted) { cm.toggleMute() }
                        RoundCallButton(NexIcons.VolumeUp, S.callSpeaker, if (speaker) Color.White else CALL_BTN, dark = speaker) { cm.toggleSpeaker() }
                        RoundCallButton(NexIcons.PhoneOff, S.callHangup, CALL_RED) { cm.hangup() }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundCallButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, bg: Color, dark: Boolean = false, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(68.dp).clip(CircleShape).background(bg).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = if (dark) Color.Black else Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
    }
}

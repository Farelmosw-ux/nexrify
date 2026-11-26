package id.nexrify.music.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import id.nexrify.music.data.call.CallManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Tombol Tolak pada notifikasi panggilan masuk. Jalan tanpa membuka aplikasi, bahkan kalau prosesnya baru dihidupkan. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CallNotifier.ACTION_DECLINE) return
        CallNotifier.cancel(context)
        val callId = intent.getStringExtra("id").orEmpty()
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            try {
                CallManager.getInstance().declineFromNotification(callId)
            } catch (_: Throwable) {
            } finally {
                pending.finish()
            }
        }
    }
}

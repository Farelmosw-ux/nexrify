package id.nexrify.music.data.chat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Tombol Balas / Tandai dibaca / Senyapkan pada notifikasi pesan pribadi. Jalan tanpa membuka aplikasi. */
class DmActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uid = intent.getStringExtra("uid").orEmpty()
        val user = intent.getStringExtra("user").orEmpty()
        val name = intent.getStringExtra("name").orEmpty().ifBlank { user }
        if (uid.isBlank()) return
        val app = context.applicationContext
        val reply = if (intent.action == DmNotifier.ACTION_REPLY)
            RemoteInput.getResultsFromIntent(intent)?.getCharSequence(DmNotifier.KEY_REPLY)?.toString()?.trim().orEmpty() else ""
        if (intent.action == DmNotifier.ACTION_REPLY && reply.isBlank()) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    DmNotifier.ACTION_REPLY -> DmNotifier.reply(app, uid, user, name, reply.take(CHAT_MAX_LENGTH))
                    DmNotifier.ACTION_READ -> DmNotifier.markRead(app, uid)
                    DmNotifier.ACTION_MUTE -> DmNotifier.mute(app, uid, user, name)
                    DmNotifier.ACTION_UNMUTE -> DmNotifier.unmute(app, uid, name)
                }
            } catch (_: Throwable) {
            } finally {
                pending.finish()
            }
        }
    }
}

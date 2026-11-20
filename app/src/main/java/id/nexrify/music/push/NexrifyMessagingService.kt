package id.nexrify.music.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import id.nexrify.music.util.CrashReporter
import kotlinx.coroutines.runBlocking

/** Penerima pesan Firebase Cloud Messaging. Dipanggil sistem walau aplikasi sedang ditutup (bukan dihentikan paksa). */
class NexrifyMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        PushManager.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val kind = data["nx_kind"] ?: return
        try {
            // Dijalankan di thread kerja FCM (batas ± 20 detik), jadi boleh menunggu unduhan foto profil.
            runBlocking { PushHandler.handle(applicationContext, kind, data) }
        } catch (e: Throwable) {
            CrashReporter.logNonFatal(e)
        }
    }
}

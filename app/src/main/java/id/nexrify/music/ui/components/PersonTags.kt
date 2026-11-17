package id.nexrify.music.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.nexrify.music.data.chat.ChatManager
import id.nexrify.music.data.chat.ChatProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

/** Cache profil ringan (5 menit) supaya komentar, daftar pembaca, dan daftar reaksi tidak mengunduh ulang terus. */
private object ProfileCache {
    private val map = ConcurrentHashMap<String, Pair<Long, ChatProfile>>()
    fun get(username: String): ChatProfile? =
        map[username.lowercase()]?.takeIf { System.currentTimeMillis() - it.first < 5 * 60_000L }?.second
    fun put(p: ChatProfile) { map[p.username.lowercase()] = System.currentTimeMillis() to p }
}

/**
 * Ambil profil (lencana, peringkat donasi, tanggal bergabung) untuk daftar username.
 * Hasil muncul bertahap; yang gagal dimuat dilewati saja.
 */
@Composable
fun rememberChatProfiles(usernames: List<String>): State<Map<String, ChatProfile>> {
    val state = remember { mutableStateOf<Map<String, ChatProfile>>(emptyMap()) }
    val key = usernames.map { it.lowercase() }.distinct().sorted()
    LaunchedEffect(key) {
        val manager = ChatManager.getInstance()
        // Dari cache dulu supaya langsung tampil.
        val cached = key.mapNotNull { n -> ProfileCache.get(n)?.let { n to it } }.toMap()
        state.value = state.value + cached
        val missing = key.filter { it !in cached }.take(40)
        if (missing.isEmpty()) return@LaunchedEffect
        val gate = Semaphore(4)
        try {
            coroutineScope {
                missing.forEach { name ->
                    async {
                        gate.withPermit {
                            val p = manager.profile(name)
                            if (p != null) {
                                ProfileCache.put(p)
                                state.value = state.value + (name to p)
                            }
                        }
                    }
                }
            }
        } catch (e: CancellationException) { throw e } catch (_: Throwable) { }
    }
    return state
}

/** Tag di samping nama: peringkat donasi, lencana peran, dan tingkat umur akun. */
@Composable
fun PersonTags(profile: ChatProfile?, modifier: Modifier = Modifier) {
    if (profile == null) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (profile.donateRank in 1..3 && profile.titleOn) TopTag(profile.donateRank)
        profile.badges.filter { it != "early" }.forEach { BadgeChip(it) }
        MemberAgeChip(profile.joined, compact = true)
    }
}

package id.nexrify.music.data.streak

import android.content.Context
import id.nexrify.music.NexrifyWidgetProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/** Warna api: abu (belum mendengarkan hari ini), merah (aktif hari ini), biru (aktif hari ini dan sudah 30 hari atau lebih). */
enum class FlameState { NONE, GRAY, RED, BLUE }

data class StreakInfo(
    val count: Int = 0,
    val state: FlameState = FlameState.NONE,
    /** Detik mendengarkan hari ini (maksimal ditampilkan 60). */
    val todaySeconds: Int = 0,
    val best: Int = 0,
    /** Sisa hari sebelum streak hangus, hanya berarti saat api abu. */
    val daysLeft: Int = 0
) {
    val listenedToday get() = state == FlameState.RED || state == FlameState.BLUE
}

/**
 * Streak mendengarkan musik. Tiap hari (hari kalender di zona waktu perangkat) cukup mendengarkan 1 menit
 * supaya api menyala. Kalau satu hari terlewat apinya abu, tetapi streak masih aman. Kalau sampai 3 hari
 * berturut-turut tidak mendengarkan 1 menit pun, streak hangus. Sesudah 30 hari api naik tingkat jadi biru.
 */
class StreakManager private constructor(private val app: Context) {

    private val prefs get() = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _info = MutableStateFlow(snapshot(app))
    val info: StateFlow<StreakInfo> = _info.asStateFlow()

    private var lastPersist = 0L

    /** Dipanggil terus selama lagu benar-benar diputar. [ms] = lama sejak panggilan sebelumnya. */
    @Synchronized
    fun addListening(ms: Long) {
        if (ms <= 0) return
        val today = LocalDate.now().toEpochDay()
        val p = prefs
        var secDay = p.getLong(K_SEC_DAY, -1L)
        var sec = p.getFloat(K_SEC, 0f)
        if (secDay != today) { secDay = today; sec = 0f }
        sec += ms / 1000f

        var count = p.getInt(K_COUNT, 0)
        var last = p.getLong(K_LAST, -1L)
        var best = p.getInt(K_BEST, 0)
        var changed = false
        if (sec >= GOAL_SECONDS && last != today) {
            val gap = if (last < 0) Long.MAX_VALUE else today - last
            count = if (count > 0 && gap <= MAX_GAP_DAYS) count + 1 else 1
            last = today
            if (count > best) best = count
            changed = true
        }
        val now = System.currentTimeMillis()
        if (changed || now - lastPersist > 10_000L) {
            lastPersist = now
            p.edit().putLong(K_SEC_DAY, secDay).putFloat(K_SEC, sec)
                .putInt(K_COUNT, count).putLong(K_LAST, last).putInt(K_BEST, best).apply()
        }
        val fresh = compute(count, last, secDay, sec, best, today)
        if (fresh != _info.value) {
            val visualChange = fresh.count != _info.value.count || fresh.state != _info.value.state
            _info.value = fresh
            if (visualChange) runCatching { NexrifyWidgetProvider.refresh(app) }
        }
    }

    /** Hitung ulang (misalnya saat aplikasi dibuka lagi setelah ganti hari). */
    @Synchronized
    fun refresh() {
        val fresh = snapshot(app)
        if (fresh != _info.value) {
            _info.value = fresh
            runCatching { NexrifyWidgetProvider.refresh(app) }
        }
    }

    companion object {
        private const val PREFS = "nexrify_streak"
        private const val K_COUNT = "count"
        private const val K_LAST = "last_day"
        private const val K_BEST = "best"
        private const val K_SEC = "sec"
        private const val K_SEC_DAY = "sec_day"
        const val GOAL_SECONDS = 60
        /** Boleh tidak mendengarkan sampai 3 hari; hari ke 4 tanpa mendengarkan, streak hangus. */
        const val MAX_GAP_DAYS = 3L
        const val BLUE_AT = 30

        @Volatile private var INSTANCE: StreakManager? = null
        fun getInstance(context: Context): StreakManager =
            INSTANCE ?: synchronized(this) { INSTANCE ?: StreakManager(context.applicationContext).also { INSTANCE = it } }

        private fun compute(count: Int, last: Long, secDay: Long, sec: Float, best: Int, today: Long): StreakInfo {
            val alive = count > 0 && last >= 0 && today - last <= MAX_GAP_DAYS
            val effective = if (alive) count else 0
            val state = when {
                effective == 0 -> FlameState.NONE
                last == today -> if (effective >= BLUE_AT) FlameState.BLUE else FlameState.RED
                else -> FlameState.GRAY
            }
            val todaySec = if (secDay == today) sec.toInt().coerceIn(0, GOAL_SECONDS) else 0
            val left = if (state == FlameState.GRAY) (MAX_GAP_DAYS - (today - last)).toInt().coerceAtLeast(0) else 0
            return StreakInfo(effective, state, todaySec, maxOf(best, effective), left)
        }

        /** Baca langsung dari penyimpanan, aman dipanggil dari widget tanpa pemutar hidup. */
        fun snapshot(context: Context): StreakInfo {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return compute(
                p.getInt(K_COUNT, 0), p.getLong(K_LAST, -1L), p.getLong(K_SEC_DAY, -1L),
                p.getFloat(K_SEC, 0f), p.getInt(K_BEST, 0), LocalDate.now().toEpochDay()
            )
        }
    }
}

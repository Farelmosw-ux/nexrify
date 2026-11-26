package id.nexrify.music.data.search

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/** Riwayat pencarian: terbaru di atas, tanpa duplikat, maksimal 30 item, disimpan di SharedPreferences. */
class SearchHistoryManager(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("nexrify_search_history", Context.MODE_PRIVATE)

    private val _history = MutableStateFlow(load())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    // Hitungan seberapa sering tiap kata dicari (kunci huruf kecil), untuk "Paling banyak dicari".
    private val counts = loadCounts().toMutableMap()
    private val _top = MutableStateFlow(computeTop())
    val topSearches: StateFlow<List<String>> = _top.asStateFlow()

    private fun computeTop(): List<String> =
        counts.entries.filter { it.value >= 2 }.sortedByDescending { it.value }.take(6).map { it.key }

    private fun loadCounts(): Map<String, Int> {
        val raw = prefs.getString(KEY_COUNTS, null) ?: return emptyMap()
        return runCatching {
            val o = org.json.JSONObject(raw)
            o.keys().asSequence().associateWith { o.optInt(it, 0) }
        }.getOrDefault(emptyMap())
    }

    private fun saveCounts() {
        val o = org.json.JSONObject()
        counts.forEach { (k, v) -> o.put(k, v) }
        prefs.edit().putString(KEY_COUNTS, o.toString()).apply()
        _top.value = computeTop()
    }

    fun add(query: String) {
        val clean = query.trim()
        if (clean.isEmpty()) return
        counts.merge(clean.lowercase(), 1) { a, b -> a + b }
        if (counts.size > 200) {
            counts.entries.sortedBy { it.value }.take(counts.size - 200).forEach { counts.remove(it.key) }
        }
        saveCounts()
        val updated = buildList {
            add(clean)
            addAll(_history.value.filterNot { it.equals(clean, ignoreCase = true) })
        }.take(MAX_ITEMS)
        _history.value = updated
        save(updated)
    }

    fun remove(query: String) {
        if (counts.remove(query.trim().lowercase()) != null) saveCounts()
        val updated = _history.value.filterNot { it == query }
        _history.value = updated
        save(updated)
    }

    fun clear() {
        _history.value = emptyList()
        counts.clear()
        prefs.edit().remove(KEY).remove(KEY_COUNTS).apply()
        _top.value = emptyList()
    }

    fun reload() {
        _history.value = load()
        counts.clear(); counts.putAll(loadCounts())
        _top.value = computeTop()
    }

    private fun load(): List<String> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { arr.getString(it) }.filter { it.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    private fun save(list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private companion object {
        const val KEY = "items"
        const val KEY_COUNTS = "counts"
        const val MAX_ITEMS = 30
    }
}

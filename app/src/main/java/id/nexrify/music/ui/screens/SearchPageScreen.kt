package id.nexrify.music.ui.screens

import id.nexrify.music.ui.components.NexConfirmDialog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.nexrify.music.data.model.Track
import id.nexrify.music.i18n.S
import id.nexrify.music.ui.components.AddToPlaylistDialog
import id.nexrify.music.ui.components.NexLoadingFill
import id.nexrify.music.ui.components.PullToRefreshBox
import id.nexrify.music.ui.components.TrackItem
import id.nexrify.music.ui.icons.NexIcons
import id.nexrify.music.ui.theme.NexrifyAppTheme
import id.nexrify.music.ui.theme.Typography
import id.nexrify.music.viewmodel.MusicViewModel

/**
 * Halaman pencarian tersendiri: kotak cari di atas, riwayat pencarian (bisa dihapus satu per satu atau semuanya)
 * saat kotak kosong, saran saat mengetik, dan hasil pencarian di bawahnya.
 *
 * [initialQuery] diisi kalau halaman dibuka dari kategori jelajah. Pencarian dari kategori tidak dicatat ke riwayat,
 * hanya yang diketik atau dipilih sendiri oleh pengguna.
 */
@Composable
fun SearchPageScreen(
    viewModel: MusicViewModel,
    initialQuery: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = NexrifyAppTheme.colors
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initialQuery) }
    var submittedQuery by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initialQuery) }
    var trackForPlaylist by remember { mutableStateOf<Track?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    val history by viewModel.searchHistory.collectAsState()
    val topSearches by viewModel.topSearches.collectAsState()
    val trendingTracks by viewModel.trendingTracks.collectAsState()
    // Pencarian trend: nama artis dari lagu yang sedang trending.
    val trendTerms = remember(trendingTracks) {
        trendingTracks.map { it.artist.trim() }
            .filter { it.isNotBlank() && it.length <= 32 }
            .distinctBy { it.lowercase() }.take(6)
    }
    // Paling banyak dicari: dari kebiasaan pencarianmu; kalau masih sedikit, dilengkapi judul lagu trending.
    val popular = remember(topSearches, trendingTracks) {
        (topSearches + trendingTracks.map { it.title.trim() }.filter { it.length in 3..40 })
            .distinctBy { it.lowercase() }.take(5)
    }
    val suggestions by viewModel.suggestions.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    val otherVersions by viewModel.searchOthers.collectAsState()
    var showOthers by remember(results) { mutableStateOf(false) }
    val isSearching by viewModel.isSearching.collectAsState()
    val isLoadingTrack by viewModel.isLoadingTrack.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val favoriteIds by remember(favorites) { derivedStateOf { favorites.mapTo(HashSet()) { it.id } } }

    LaunchedEffect(Unit) {
        if (initialQuery.isNotBlank()) {
            viewModel.search(initialQuery)
        } else {
            focusRequester.requestFocus()
        }
    }

    fun runSearch(text: String, record: Boolean = true) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        query = clean
        submittedQuery = clean
        focusManager.clearFocus()
        if (record) viewModel.searchAndRemember(clean) else viewModel.search(clean)
    }

    PullToRefreshBox(
        refreshing = isSearching,
        onRefresh = { val q = submittedQuery.ifBlank { query.trim() }; if (q.isNotBlank()) viewModel.search(q, forceRefresh = true) },
        modifier = modifier.fillMaxSize().background(colors.background)
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(NexIcons.ArrowBack, contentDescription = S.back, tint = colors.textPrimary)
            }
            TextField(
                value = query,
                onValueChange = {
                    query = it
                    if (it.isBlank()) submittedQuery = ""
                    viewModel.loadSuggestions(it)
                },
                placeholder = { Text(S.searchHint, color = colors.textMuted, style = Typography.bodyLarge) },
                leadingIcon = {
                    Icon(NexIcons.Search, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            query = ""
                            submittedQuery = ""
                            viewModel.loadSuggestions("")
                            focusRequester.requestFocus()
                        }) {
                            Icon(NexIcons.CloseCircle, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runSearch(query) }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colors.card,
                    unfocusedContainerColor = colors.card,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    cursorColor = colors.textPrimary
                ),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                    .focusRequester(focusRequester)
            )
            Spacer(Modifier.width(8.dp))
        }

        Spacer(Modifier.height(8.dp))

        if (query.isNotBlank()) {
            // Mengetik: saran di atas, hasil lagu langsung di bawahnya (tanpa harus tekan enter).
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(suggestions) { suggestion ->
                    HistoryLikeRow(
                        icon = { Icon(NexIcons.Search, null, tint = colors.textMuted, modifier = Modifier.size(20.dp)) },
                        text = suggestion,
                        onClick = { runSearch(suggestion) },
                        trailing = {
                            IconButton(onClick = {
                                query = suggestion
                                viewModel.loadSuggestions(suggestion)
                            }) {
                                Icon(NexIcons.NorthWest, null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                    )
                }
                if (suggestions.isNotEmpty()) item { Spacer(Modifier.height(8.dp)) }

                if (results.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                            if (isSearching) NexLoadingFill(active = true)
                            else if (suggestions.isEmpty()) Text(S.noTracks, color = colors.textMuted, style = Typography.bodyLarge)
                        }
                    }
                }
                items(results, key = { it.id }, contentType = { "track" }) { track ->
                    TrackItem(
                        track = track.copy(isFavorite = track.id in favoriteIds),
                        isPlaying = currentTrack?.id == track.id,
                        isLoading = isLoadingTrack && currentTrack?.id == track.id,
                        onClick = {
                            viewModel.rememberSearch(query)
                            focusManager.clearFocus()
                            viewModel.playTrackFromSearch(track)
                        },
                        onFavoriteClick = { viewModel.toggleFavorite(track) },
                        onMoreClick = { trackForPlaylist = track }
                    )
                }
                if (otherVersions.isNotEmpty()) {
                    item(key = "others_toggle") {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(16.dp))
                                .background(colors.card).border(1.dp, colors.border, RoundedCornerShape(16.dp))
                                .clickable { showOthers = !showOthers }.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (showOthers) S.searchHideOthers else S.searchShowOthers(otherVersions.size),
                                color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f)
                            )
                            Text(if (showOthers) "−" else "+", color = colors.textMuted, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (showOthers) {
                        items(otherVersions, key = { "o_" + it.id }, contentType = { "track" }) { track ->
                            TrackItem(
                                track = track.copy(isFavorite = track.id in favoriteIds),
                                isPlaying = currentTrack?.id == track.id,
                                isLoading = isLoadingTrack && currentTrack?.id == track.id,
                                onClick = {
                                    viewModel.rememberSearch(query)
                                    focusManager.clearFocus()
                                    viewModel.playTrackFromSearch(track)
                                },
                                onFavoriteClick = { viewModel.toggleFavorite(track) },
                                onMoreClick = { trackForPlaylist = track }
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(110.dp)) }
            }
        } else {
            // Kotak kosong: riwayat, paling banyak dicari, pencarian trend.
            if (history.isEmpty() && popular.isEmpty() && trendTerms.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier.size(64.dp).clip(CircleShape).background(colors.card)
                            .border(1.dp, colors.border, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(NexIcons.History, null, tint = colors.textMuted, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(S.noHistory, style = Typography.titleMedium, color = colors.textPrimary, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    Text(S.noHistoryHint, style = Typography.bodySmall, color = colors.textMuted, textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (history.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(S.searchHistory, style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                TextButton(onClick = { confirmClear = true }) {
                                    Icon(NexIcons.DeleteOutline, null, tint = colors.textMuted, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(S.clearAll, color = colors.textMuted, fontSize = 13.sp)
                                }
                            }
                        }
                        items(history.take(6), key = { "h_$it" }) { item ->
                            HistoryLikeRow(
                                icon = { Icon(NexIcons.History, null, tint = colors.textMuted, modifier = Modifier.size(20.dp)) },
                                text = item,
                                onClick = { runSearch(item) },
                                trailing = {
                                    IconButton(onClick = { viewModel.removeSearchHistory(item) }) {
                                        Icon(NexIcons.Close, S.deleteHistoryItem, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                                    }
                                }
                            )
                        }
                    }
                    if (popular.isNotEmpty()) {
                        item { SearchSectionTitle(S.mostSearched) }
                        itemsIndexed(popular, key = { i, t -> "p_${i}_$t" }) { index, term ->
                            HistoryLikeRow(
                                icon = {
                                    Text(
                                        "${index + 1}",
                                        style = Typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (index < 3) colors.accent else colors.textMuted,
                                        modifier = Modifier.width(20.dp),
                                        textAlign = TextAlign.Center
                                    )
                                },
                                text = term,
                                onClick = { runSearch(term) },
                                trailing = { Spacer(Modifier.width(12.dp)) }
                            )
                        }
                    }
                    if (trendTerms.isNotEmpty()) {
                        item { SearchSectionTitle(S.trendingSearches) }
                        itemsIndexed(trendTerms, key = { i, t -> "t_${i}_$t" }) { _, term ->
                            HistoryLikeRow(
                                icon = { Icon(NexIcons.TrendingUp, null, tint = colors.accent, modifier = Modifier.size(20.dp)) },
                                text = term,
                                onClick = { runSearch(term) },
                                trailing = {
                                    IconButton(onClick = {
                                        query = term
                                        viewModel.loadSuggestions(term)
                                    }) {
                                        Icon(NexIcons.NorthWest, null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                                    }
                                }
                            )
                        }
                    }
                    item { Spacer(Modifier.height(110.dp)) }
                }
            }
        }
    }
    }

    trackForPlaylist?.let { track ->
        AddToPlaylistDialog(track = track, viewModel = viewModel, onDismiss = { trackForPlaylist = null })
    }

    if (confirmClear) {
        NexConfirmDialog(
            title = S.clearHistoryTitle,
            text = S.clearHistoryBody,
            confirmText = S.delete,
            icon = NexIcons.DeleteOutline,
            danger = true,
            onDismiss = { confirmClear = false },
            onConfirm = {
                viewModel.clearSearchHistory()
                confirmClear = false
            }
        )
    }
}

@Composable
private fun SearchSectionTitle(text: String) {
    val colors = NexrifyAppTheme.colors
    Text(
        text = text,
        style = Typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = colors.textPrimary,
        modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 18.dp, bottom = 4.dp)
    )
}

@Composable
private fun HistoryLikeRow(
    icon: @Composable () -> Unit,
    text: String,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit
) {
    val colors = NexrifyAppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(14.dp))
        Text(
            text = text,
            style = Typography.bodyLarge,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(vertical = 12.dp)
        )
        trailing()
    }
}

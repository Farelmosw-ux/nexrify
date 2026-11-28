package id.nexrify.music.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import id.nexrify.music.R
import id.nexrify.music.data.account.AccountManager
import id.nexrify.music.data.auth.UserAccount
import id.nexrify.music.data.chat.ChatManager
import id.nexrify.music.data.chat.DmManager
import id.nexrify.music.data.mailbox.MailboxManager
import id.nexrify.music.data.model.Track
import id.nexrify.music.data.notif.NotifCenter
import id.nexrify.music.data.prayer.MaghribManager
import id.nexrify.music.data.settings.TimeContext
import id.nexrify.music.data.streak.StreakInfo
import id.nexrify.music.i18n.S
import id.nexrify.music.ui.components.AddToPlaylistDialog
import id.nexrify.music.ui.components.LoginWebViewDialog
import id.nexrify.music.ui.components.NexAvatar
import id.nexrify.music.ui.components.NexFooter
import id.nexrify.music.ui.components.NexLoadingFill
import id.nexrify.music.ui.components.ProfileMenuSheet
import id.nexrify.music.ui.components.PullToRefreshBox
import id.nexrify.music.ui.components.SettingsDialog
import id.nexrify.music.ui.components.StreakChip
import id.nexrify.music.ui.components.StreakDialog
import id.nexrify.music.ui.components.TrackItem
import id.nexrify.music.ui.icons.NexIcons
import id.nexrify.music.ui.theme.NexrifyAppTheme
import id.nexrify.music.ui.theme.PureBlack
import id.nexrify.music.ui.theme.PureWhite
import id.nexrify.music.ui.theme.Typography
import id.nexrify.music.viewmodel.MusicViewModel
import java.time.ZonedDateTime
import kotlinx.coroutines.delay

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    viewModel: MusicViewModel,
    onOpenMailbox: () -> Unit = {},
    onOpenChat: () -> Unit = {},
    onOpenDm: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenDonate: () -> Unit = {},
    onOpenFeedback: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val homeSections by viewModel.homeSections.collectAsState()
    val trendingTracks by viewModel.trendingTracks.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val isLoadingTrending by viewModel.isLoadingTrending.collectAsState()
    val isLoadingTrack by viewModel.isLoadingTrack.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val currentAccount by viewModel.authState.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val reactions by viewModel.reactions.collectAsState()
    val streak by viewModel.streakManager.info.collectAsState()
    var showStreak by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.streakManager.refresh() }
    if (showStreak) StreakDialog(streak) { showStreak = false }
    val favoriteIds by remember(favorites) { derivedStateOf { favorites.mapTo(HashSet()) { it.id } } }

    var showProfileMenu by remember { mutableStateOf(false) }
    var showLoginWebView by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var trackForPlaylist by remember { mutableStateOf<Track?>(null) }

    var selectedGenre by rememberSaveable { mutableStateOf("Semua") }
    val genres = listOf("Semua", "Pop", "Lo-Fi", "Rock", "Acoustic", "Anime", "Hip-Hop", "R&B", "Top Global")

    val colors = NexrifyAppTheme.colors
    val isV2 = NexrifyAppTheme.isV2
    val homeListState = rememberLazyListState()
    var lastIsV2 by rememberSaveable { mutableStateOf(isV2) }
    LaunchedEffect(isV2) {
        if (lastIsV2 != isV2) {
            homeListState.scrollToItem(0)
            lastIsV2 = isV2
        }
    }
    var localNow by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            localNow = ZonedDateTime.now()
            delay(10_000L)
        }
    }

    val maghribManager = remember { MaghribManager.getInstance() }
    val maghribPlace by maghribManager.place.collectAsState()
    val maghribStatus by maghribManager.status.collectAsState()
    LaunchedEffect(Unit) {
        while (true) {
            maghribManager.refresh()
            delay(15 * 60_000L)
        }
    }
    val maghribTime = remember(maghribPlace, localNow.toLocalDate(), localNow.zone) {
        maghribManager.maghribFor(localNow.toLocalDate(), localNow.zone)
    }

    PullToRefreshBox(
        refreshing = isLoadingTrending,
        onRefresh = {
            if (selectedGenre == "Semua") viewModel.loadTrending(forceRefresh = true)
            else viewModel.search("$selectedGenre music hits")
        },
        modifier = modifier.fillMaxSize().background(colors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = if (isV2) 4.dp else 16.dp)
        ) {
            val timeContext = TimeContext.resolve(localNow, maghribTime)
            val unreadMail by MailboxManager.getInstance().unreadCount.collectAsState()
            val chatUnread by ChatManager.getInstance().mentionUnread.collectAsState()
            val notifUnread by NotifCenter.getInstance().unread.collectAsState()

            if (isV2) {
                V2UtilityBar(
                    account = currentAccount,
                    chatUnread = chatUnread,
                    mailUnread = unreadMail,
                    notifUnread = notifUnread,
                    onNotifs = onOpenNotifications,
                    onProfile = { showProfileMenu = true },
                    onChat = onOpenChat,
                    onMail = onOpenMailbox,
                    onSettings = { showSettingsDialog = true },
                    streak = streak,
                    onStreak = { showStreak = true }
                )
                V2HomeHeader(
                    greeting = S.period(timeContext.period),
                    description = if (S.english) timeContext.descriptionEn else timeContext.descriptionId,
                    hint = if (maghribStatus == MaghribManager.Status.NO_PERMISSION && localNow.hour in 16..18) S.maghribHomeHint else null
                )
            } else {
                val greeting = S.period(timeContext.period)
                val description = if (S.english) timeContext.descriptionEn else timeContext.descriptionId
                val maghribHint = if (maghribStatus == MaghribManager.Status.NO_PERMISSION && localNow.hour in 16..18) S.maghribHomeHint else null

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = greeting,
                            style = Typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                            color = colors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        StreakChip(streak, onClick = { showStreak = true })
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = description,
                        style = Typography.bodyLarge,
                        color = colors.textMuted
                    )
                    if (maghribHint != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = maghribHint,
                            style = Typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ChatTopButton(unread = chatUnread, onClick = onOpenChat)
                        NotifTopButton(unread = notifUnread, onClick = onOpenNotifications)
                        DmTopButton(onClick = onOpenDm)
                        MailboxTopButton(unread = unreadMail, onClick = onOpenMailbox)
                        Spacer(modifier = Modifier.weight(1f))
                        AccountTopButton(account = currentAccount, onClick = { showProfileMenu = true })
                    }
                }
            }

            if (!isV2) {
                DonateEntryCard(onClick = onOpenDonate)
                FeedbackEntryCard(onClick = onOpenFeedback)
            }
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(genres) { genre ->
                    val isSelected = selectedGenre == genre
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .then(
                                if (isV2 && isSelected) Modifier.background(
                                    Brush.horizontalGradient(listOf(colors.accent, colors.accentAlt))
                                ) else Modifier.background(if (isSelected) colors.textPrimary else colors.card)
                            )
                            .border(
                                1.dp,
                                if (isSelected) (if (isV2) colors.accent else colors.textPrimary) else colors.border,
                                RoundedCornerShape(20.dp)
                            )
                            .clickable {
                                selectedGenre = genre
                                if (genre == "Semua") viewModel.loadTrending() else viewModel.search("$genre music hits")
                            }
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = S.genreLabel(genre),
                            style = Typography.labelMedium.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
                            fontSize = 13.sp,
                            color = if (isSelected) colors.background else colors.textPrimary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))

            if (selectedGenre != "Semua") {
                if (isSearching) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        NexLoadingFill(active = true)
                    }
                } else if (searchResults.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = S.noSongsFor(S.genreLabel(selectedGenre)),
                            style = Typography.bodyMedium,
                            color = colors.textMuted
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(searchResults, key = { it.id }, contentType = { "track" }) { track ->
                            val isFav = track.id in favoriteIds
                            TrackItem(
                                track = track.copy(isFavorite = isFav),
                                isPlaying = currentTrack?.id == track.id,
                                isLoading = isLoadingTrack && currentTrack?.id == track.id,
                                onClick = { viewModel.playTrackFromSearch(track) },
                                onFavoriteClick = { viewModel.toggleFavorite(track) },
                                onMoreClick = { trackForPlaylist = track }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(100.dp)) }
                    }
                }
            } else if (isLoadingTrending) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(5) { SkeletonMusicRow() }
                }
            } else if (homeSections.isNotEmpty()) {
                val playAgainSource = remember(recentlyPlayed, homeSections) {
                    (if (recentlyPlayed.isNotEmpty()) recentlyPlayed else homeSections.firstOrNull()?.tracks.orEmpty())
                        .distinctBy { it.id }
                }
                var playAgainExpanded by remember { mutableStateOf(false) }
                val pickSection = remember(homeSections) { homeSections.firstOrNull { it.tracks.isNotEmpty() } }
                val pickTrack = pickSection?.tracks?.firstOrNull()

                val playAgainBlock: LazyListScope.() -> Unit = {
                    if (playAgainSource.isNotEmpty()) {
                        item(key = "play_again_header") {
                            HomeSectionHeader(
                                title = S.playAgain,
                                action = if (playAgainSource.size > 4) (if (playAgainExpanded) S.showLess else S.viewAll) else null,
                                onAction = { playAgainExpanded = !playAgainExpanded }
                            )
                        }
                        item(key = "play_again_grid") {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                playAgainSource.take(if (playAgainExpanded) 12 else 4).chunked(2).forEach { pair ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        pair.forEach { track ->
                                            PlayAgainTile(
                                                track = track,
                                                isPlaying = currentTrack?.id == track.id,
                                                modifier = Modifier.weight(1f),
                                                onClick = { viewModel.playTrack(track, playAgainSource) },
                                                onLongClick = { trackForPlaylist = track }
                                            )
                                        }
                                        if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(26.dp))
                        }
                    }
                }
                val picksBlock: LazyListScope.() -> Unit = {
                    if (pickTrack != null && pickSection != null) {
                        item(key = "picks_header") { HomeSectionHeader(title = S.picksForYou) }
                        item(key = "picks_banner") {
                            PickBanner(
                                track = pickTrack,
                                onPlay = { viewModel.playTrack(pickTrack, pickSection.tracks) },
                                onLongClick = { trackForPlaylist = pickTrack }
                            )
                            Spacer(modifier = Modifier.height(26.dp))
                        }
                    }
                }
                val favoritesBlock: LazyListScope.() -> Unit = {
                    if (favorites.isNotEmpty()) {
                        item(key = "favorites_header") { HomeSectionHeader(title = S.favoriteSongs) }
                        item(key = "favorites_tracks") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                itemsIndexed(favorites.take(12), key = { index, track -> "favorite_${track.id}_$index" }) { _, track ->
                                    YtmMusicCard(
                                        track = track,
                                        isPlaying = currentTrack?.id == track.id,
                                        onClick = { viewModel.playTrack(track, favorites) },
                                        onLongClick = { trackForPlaylist = track }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(26.dp))
                        }
                    }
                }

                LazyColumn(modifier = Modifier.fillMaxSize(), state = homeListState) {
                    if (isV2) {
                        picksBlock(this)
                        favoritesBlock(this)
                        playAgainBlock(this)
                    } else {
                        playAgainBlock(this)
                        picksBlock(this)
                        favoritesBlock(this)
                    }

                    homeSections.forEachIndexed { sIndex, section ->
                        item(key = "header_${sIndex}_${section.title}") {
                            HomeSectionHeader(
                                title = S.sectionTitle(section.title),
                                subtitle = section.subtitle.takeIf { it.isNotBlank() }?.let { S.sectionSubtitle(it) }
                            )
                        }
                        item(key = "shelf_${sIndex}_${section.title}") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                itemsIndexed(section.tracks, key = { tIndex, track -> "shelf_${sIndex}_${track.id}_$tIndex" }) { _, track ->
                                    YtmMusicCard(
                                        track = track,
                                        isPlaying = currentTrack?.id == track.id,
                                        onClick = { viewModel.playTrack(track, section.tracks) },
                                        onLongClick = { trackForPlaylist = track },
                                        reaction = reactions[track.id] ?: 0,
                                        onLike = { viewModel.reactToTrack(track, 1) },
                                        onDislike = { viewModel.reactToTrack(track, -1) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(26.dp))
                        }
                    }

                    if (isV2) {
                        item(key = "v2_quick") {
                            V2QuickRow(onDonate = onOpenDonate, onFeedback = onOpenFeedback)
                        }
                    }
                    item {
                        NexFooter()
                        Spacer(modifier = Modifier.height(96.dp))
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), state = homeListState) {
                    items(trendingTracks, key = { it.id }, contentType = { "track" }) { track ->
                        val isFav = track.id in favoriteIds
                        TrackItem(
                            track = track.copy(isFavorite = isFav),
                            isPlaying = currentTrack?.id == track.id,
                            isLoading = isLoadingTrack && currentTrack?.id == track.id,
                            onClick = { viewModel.playTrack(track, trendingTracks) },
                            onFavoriteClick = { viewModel.toggleFavorite(track) },
                            onMoreClick = { trackForPlaylist = track }
                        )
                    }
                    if (isV2) {
                        item(key = "v2_quick_fallback") {
                            V2QuickRow(onDonate = onOpenDonate, onFeedback = onOpenFeedback)
                        }
                    }
                    item { Spacer(modifier = Modifier.height(100.dp)) }
                }
            }
        }
    }

    if (showProfileMenu) {
        ProfileMenuSheet(
            account = currentAccount,
            viewModel = viewModel,
            onLoginClick = { showLoginWebView = true },
            onLogoutClick = { viewModel.logout() },
            onOpenSettingsClick = { showSettingsDialog = true },
            onDismiss = { showProfileMenu = false }
        )
    }

    if (showLoginWebView) {
        LoginWebViewDialog(
            onDismiss = { showLoginWebView = false },
            onLoginSuccess = { showProfileMenu = true }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(onDismiss = { showSettingsDialog = false })
    }

    if (trackForPlaylist != null) {
        AddToPlaylistDialog(
            track = trackForPlaylist!!,
            viewModel = viewModel,
            onDismiss = { trackForPlaylist = null }
        )
    }
}

@Composable
fun AccountTopButton(
    account: UserAccount?,
    onClick: () -> Unit
) {
    val nexUser by AccountManager.getInstance().user.collectAsState()
    NexAvatar(
        user = nexUser,
        size = 40.dp,
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun ReactionButton(
    icon: ImageVector,
    active: Boolean,
    description: String,
    onClick: () -> Unit
) {
    val colors = NexrifyAppTheme.colors
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(if (active) colors.accent.copy(alpha = 0.95f) else PureBlack.copy(alpha = 0.62f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (active) colors.background else PureWhite,
            modifier = Modifier.size(15.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun YtmMusicCard(
    track: Track,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    reaction: Int = 0,
    onLike: (() -> Unit)? = null,
    onDislike: (() -> Unit)? = null
) {
    val colors = NexrifyAppTheme.colors
    val cardV2 = NexrifyAppTheme.isV2

    Column(
        modifier = modifier
            .width(144.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(
            modifier = Modifier
                .size(144.dp)
                .clip(RoundedCornerShape(if (cardV2) 26.dp else 12.dp))
                .background(colors.card)
        ) {
            AsyncImage(
                model = track.highResThumbnailUrl,
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PureBlack.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_nexrify_play),
                        contentDescription = S.cd("Playing"),
                        tint = PureWhite,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            if (onLike != null || onDislike != null) {
                Row(
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (onLike != null) ReactionButton(
                        icon = if (reaction == 1) NexIcons.ThumbUpFilled else NexIcons.ThumbUp,
                        active = reaction == 1,
                        description = S.likeSong,
                        onClick = onLike
                    )
                    if (onDislike != null) ReactionButton(
                        icon = if (reaction == -1) NexIcons.ThumbDownFilled else NexIcons.ThumbDown,
                        active = reaction == -1,
                        description = S.dislikeSong,
                        onClick = onDislike
                    )
                }
            }

            if (track.durationSeconds > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(PureBlack.copy(alpha = 0.75f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = track.formattedDuration,
                        style = Typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = PureWhite
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (cardV2) {
            Text(
                text = track.artist.uppercase(),
                style = Typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.accentAlt,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.title,
                style = Typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            Text(
                text = track.title,
                style = Typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.artist,
                style = Typography.bodySmall,
                fontWeight = FontWeight.Normal,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SkeletonMusicRow() {
    val colors = NexrifyAppTheme.colors
    val transition = rememberInfiniteTransition(label = "catalogSkeleton")
    val shimmerAlpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeletonAlpha"
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.card.copy(alpha = shimmerAlpha))
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                Modifier.fillMaxWidth(0.72f).height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.card.copy(alpha = shimmerAlpha))
            )
            Spacer(Modifier.height(9.dp))
            Box(
                Modifier.fillMaxWidth(0.46f).height(11.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.card.copy(alpha = shimmerAlpha * 0.8f))
            )
        }
    }
}

@Composable
private fun HomeSectionHeader(
    title: String,
    subtitle: String? = null,
    action: String? = null,
    onAction: () -> Unit = {}
) {
    val colors = NexrifyAppTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (NexrifyAppTheme.isV2) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(22.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Brush.verticalGradient(listOf(colors.accent, colors.accentAlt)))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(
                    text = title,
                    style = Typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = colors.textPrimary
                )
            }
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, style = Typography.bodySmall, color = colors.textMuted)
            }
        }
        if (action != null) {
            Text(
                text = action,
                style = Typography.labelMedium,
                color = colors.textSecondary,
                modifier = Modifier.clickable(onClick = onAction).padding(vertical = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayAgainTile(
    track: Track,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val colors = NexrifyAppTheme.colors
    Row(
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(if (NexrifyAppTheme.isV2) 29.dp else 14.dp))
            .background(colors.card)
            .border(
                1.dp,
                if (isPlaying) (if (NexrifyAppTheme.isV2) colors.accent else colors.textSecondary) else colors.border,
                RoundedCornerShape(if (NexrifyAppTheme.isV2) 29.dp else 14.dp)
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(58.dp)) {
            AsyncImage(
                model = track.highResThumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (isPlaying) {
                Box(
                    modifier = Modifier.fillMaxSize().background(PureBlack.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(NexIcons.GraphicEq, S.cd("Playing"), tint = PureWhite, modifier = Modifier.size(22.dp))
                }
            }
        }
        Text(
            text = track.title,
            style = Typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PickBanner(
    track: Track,
    onPlay: () -> Unit,
    onLongClick: () -> Unit
) {
    val colors = NexrifyAppTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(if (NexrifyAppTheme.isV2) 220.dp else 176.dp)
            .clip(RoundedCornerShape(if (NexrifyAppTheme.isV2) 32.dp else 22.dp))
            .background(colors.card)
            .combinedClickable(onClick = onPlay, onLongClick = onLongClick)
    ) {
        AsyncImage(
            model = track.highResThumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, PureBlack.copy(alpha = 0.35f), PureBlack.copy(alpha = 0.88f))
                    )
                )
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 18.dp, bottom = 16.dp, end = 86.dp)
        ) {
            Text(
                text = S.personalMix,
                style = Typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                letterSpacing = 1.4.sp,
                color = PureWhite.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = track.title,
                style = Typography.titleLarge,
                fontWeight = FontWeight.Bold,
                fontSize = 21.sp,
                color = PureWhite,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = Typography.bodySmall,
                color = PureWhite.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp)
                .size(54.dp)
                .clip(CircleShape)
                .then(
                    if (NexrifyAppTheme.isV2) Modifier.background(
                        Brush.linearGradient(listOf(colors.accent, colors.accentAlt))
                    ) else Modifier.background(PureWhite)
                )
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_nexrify_play),
                contentDescription = S.cd("Play"),
                tint = PureBlack,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun DonateEntryCard(onClick: () -> Unit) {
    val colors = NexrifyAppTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0x33F5C242), colors.card)
                )
            )
            .border(1.dp, Color(0x55F5C242), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFFFFE9A0), Color(0xFFD9962B))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(NexIcons.Crown, S.donateOpenCd, tint = Color(0xFF2A1B00), modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(S.donateTitle, style = Typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
            Text(S.donateEntrySub, style = Typography.bodySmall, color = colors.textMuted)
        }
    }
}

@Composable
private fun FeedbackEntryCard(onClick: () -> Unit) {
    val colors = NexrifyAppTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(CircleShape).background(colors.border),
            contentAlignment = Alignment.Center
        ) {
            Icon(NexIcons.Lightbulb, S.feedbackEntry, tint = colors.textPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(S.feedbackEntry, style = Typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
            Text(S.feedbackEntrySub, style = Typography.bodySmall, color = colors.textMuted)
        }
    }
}

@Composable
private fun ChatTopButton(unread: Int, onClick: () -> Unit) {
    val colors = NexrifyAppTheme.colors
    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.card)
                .border(1.dp, colors.border, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(NexIcons.Group, S.chatOpenChat, tint = colors.textPrimary, modifier = Modifier.size(20.dp))
        }
        if (unread > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(colors.accent)
                    .border(2.dp, colors.background, CircleShape)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (unread > 9) "9+" else unread.toString(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.background
                )
            }
        }
    }
}

@Composable
private fun NotifTopButton(unread: Int, onClick: () -> Unit) {
    val colors = NexrifyAppTheme.colors
    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(colors.card).border(1.dp, colors.border, CircleShape).clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) { Icon(NexIcons.Bell, S.notifCenterTitle, tint = colors.textPrimary, modifier = Modifier.size(20.dp)) }
        if (unread > 0) {
            Box(
                modifier = Modifier.align(Alignment.TopEnd).clip(CircleShape).background(colors.accent).border(2.dp, colors.background, CircleShape).padding(horizontal = 5.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) { Text(text = if (unread > 9) "9+" else unread.toString(), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = colors.background) }
        }
    }
}

@Composable
private fun DmTopButton(onClick: () -> Unit) {
    val colors = NexrifyAppTheme.colors
    val unread by DmManager.getInstance().unread.collectAsState()
    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.card)
                .border(1.dp, colors.border, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(NexIcons.Send, S.dmTitle, tint = colors.textPrimary, modifier = Modifier.size(19.dp))
        }
        if (unread > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(colors.accent)
                    .border(2.dp, colors.background, CircleShape)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (unread > 9) "9+" else unread.toString(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.background
                )
            }
        }
    }
}

@Composable
private fun MailboxTopButton(unread: Int, onClick: () -> Unit) {
    val colors = NexrifyAppTheme.colors
    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.card)
                .border(1.dp, colors.border, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(NexIcons.Mail, S.mailboxTitle, tint = colors.textPrimary, modifier = Modifier.size(20.dp))
        }
        if (unread > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(colors.accent)
                    .border(2.dp, colors.background, CircleShape)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (unread > 9) "9+" else unread.toString(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.background
                )
            }
        }
    }
}

@Composable
private fun V2HomeHeader(greeting: String, description: String, hint: String?) {
    val colors = NexrifyAppTheme.colors
    val shape = RoundedCornerShape(30.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.border, shape)
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Text(greeting, style = Typography.headlineLarge.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(description, style = Typography.bodyLarge, color = colors.textMuted)
        if (hint != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(hint, style = Typography.bodySmall, color = colors.textSecondary)
        }
    }
}

@Composable
private fun V2UtilityBar(
    account: UserAccount?,
    chatUnread: Int,
    mailUnread: Int,
    notifUnread: Int,
    onNotifs: () -> Unit,
    onProfile: () -> Unit,
    onChat: () -> Unit,
    onMail: () -> Unit,
    onSettings: () -> Unit,
    streak: StreakInfo,
    onStreak: () -> Unit
) {
    val colors = NexrifyAppTheme.colors
    val shape = RoundedCornerShape(30.dp)
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.border, shape)
    ) {
        val roomForTitle = maxWidth >= 400.dp
        val items: @Composable () -> Unit = {
            StreakChip(streak, onClick = onStreak)
            Spacer(modifier = Modifier.width(8.dp))
            ChatTopButton(unread = chatUnread, onClick = onChat)
            Spacer(modifier = Modifier.width(8.dp))
            NotifTopButton(unread = notifUnread, onClick = onNotifs)
            Spacer(modifier = Modifier.width(8.dp))
            MailboxTopButton(unread = mailUnread, onClick = onMail)
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.card)
                    .border(1.dp, colors.border, CircleShape)
                    .clickable(onClick = onSettings),
                contentAlignment = Alignment.Center
            ) {
                Icon(NexIcons.Settings, contentDescription = S.cd("Settings"), tint = colors.textPrimary, modifier = Modifier.size(20.dp))
            }
        }
        if (roomForTitle) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AccountTopButton(account = account, onClick = onProfile)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Nexrify", style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
                items()
            }
        } else {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AccountTopButton(account = account, onClick = onProfile)
                Spacer(modifier = Modifier.width(10.dp))
                items()
            }
        }
    }
}

@Composable
private fun V2QuickRow(onDonate: () -> Unit, onFeedback: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        V2QuickTile(
            title = S.donateTitle,
            sub = S.donateEntrySub,
            icon = NexIcons.Crown,
            gold = true,
            onClick = onDonate,
            modifier = Modifier.weight(1f)
        )
        V2QuickTile(
            title = S.feedbackEntry,
            sub = S.feedbackEntrySub,
            icon = NexIcons.Lightbulb,
            gold = false,
            onClick = onFeedback,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun V2QuickTile(
    title: String,
    sub: String,
    icon: ImageVector,
    gold: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = NexrifyAppTheme.colors
    val shape = RoundedCornerShape(26.dp)
    val goldColor = Color(0xFFF5C242)
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                if (gold) Brush.linearGradient(listOf(goldColor.copy(alpha = 0.26f), colors.card))
                else Brush.linearGradient(listOf(colors.accent.copy(alpha = 0.20f), colors.card))
            )
            .border(1.dp, if (gold) goldColor.copy(alpha = 0.45f) else colors.border, shape)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (gold) Brush.linearGradient(
                        listOf(Color(0xFFFFE9A0), Color(0xFFD9962B))
                    ) else Brush.linearGradient(listOf(colors.accent, colors.accentAlt))
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = title,
                tint = if (gold) Color(0xFF2A1B00) else colors.background,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(title, style = Typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(modifier = Modifier.height(2.dp))
        Text(sub, style = Typography.bodySmall, color = colors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

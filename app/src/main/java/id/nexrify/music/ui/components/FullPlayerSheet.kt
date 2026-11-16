package id.nexrify.music.ui.components

import id.nexrify.music.i18n.S
import id.nexrify.music.ui.icons.NexIcons
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import id.nexrify.music.R
import id.nexrify.music.data.model.Track
import id.nexrify.music.data.poster.*
import id.nexrify.music.ui.theme.NexrifyAppTheme
import id.nexrify.music.ui.theme.Typography
import id.nexrify.music.viewmodel.MusicViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FullPlayerSheet(
    track: Track,
    viewModel: MusicViewModel,
    isPlaying: Boolean,
    isLoading: Boolean = false,
    isShuffle: Boolean = false,
    repeatMode: Int = Player.REPEAT_MODE_OFF,
    onPlayPauseClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    onSkipPreviousClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val currentLyrics by viewModel.currentLyrics.collectAsState()
    val isLoadingLyrics by viewModel.isLoadingLyrics.collectAsState()
    val audioDeviceState by viewModel.audioDeviceState.collectAsState()
    val playbackPositionMs by viewModel.playbackPosition.collectAsState()
    val durationMs by viewModel.duration.collectAsState()

    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var isLyricsExpanded by remember { mutableStateOf(false) }
    var showSongComments by remember { mutableStateOf(false) }

    // Mode Pemutar: NORMAL (seperti foto ke-2) vs POSTER_LIVE (TikTok/Auralys Live Poster)
    var visualMode by remember { mutableStateOf(PlayerVisualMode.NORMAL) }
    var livePosterState by remember { mutableStateOf(PosterState(layout = PosterLayout.ORBIT)) }
    var customPhoto by remember { mutableStateOf<Bitmap?>(null) }
    var customCutout by remember { mutableStateOf<Bitmap?>(null) }
    var isCuttingBg by remember { mutableStateOf(false) }

    val colors = NexrifyAppTheme.colors
    val isV2 = NexrifyAppTheme.isV2
    val isEn = S.english

    // Load cover bitmap untuk poster live
    val coverBitmap by produceState<Bitmap?>(null, track.id, track.highResThumbnailUrl) {
        value = runCatching {
            val req = ImageRequest.Builder(context)
                .data(track.highResThumbnailUrl)
                .size(512)
                .allowHardware(false)
                .build()
            (context.imageLoader.execute(req) as? SuccessResult)?.drawable?.toBitmap()
        }.getOrNull()
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val bmp = runCatching {
                    val req = ImageRequest.Builder(context).data(uri).size(1280).allowHardware(false).build()
                    (context.imageLoader.execute(req) as? SuccessResult)?.drawable?.toBitmap()
                }.getOrNull() ?: return@launch
                customPhoto = bmp
                customCutout = null
                isCuttingBg = true
                try {
                    customCutout = BackgroundRemover.cut(bmp)
                } finally {
                    isCuttingBg = false
                }
            }
        }
    }

    val cdCollapse = S.cd("Collapse Player")
    val cdQueue = S.cd("Queue")

    // Bar atas: tutup, pilih gaya (Normal / Poster), antrian. Dipakai di dua mode supaya tidak ada tombol ganda.
    val topBar: @Composable (Modifier) -> Unit = { mod ->
        Row(
            modifier = mod
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerRoundButton(NexIcons.KeyboardArrowDown, cdCollapse, 28.dp, onDismiss)

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.52f))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(50))
                    .padding(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModeTabPill(text = "Normal", selected = visualMode == PlayerVisualMode.NORMAL) {
                    visualMode = PlayerVisualMode.NORMAL
                }
                ModeTabPill(text = "Poster", selected = visualMode == PlayerVisualMode.POSTER_LIVE) {
                    visualMode = PlayerVisualMode.POSTER_LIVE
                }
            }

            PlayerRoundButton(NexIcons.QueueMusic, cdQueue, 20.dp) { showQueue = true }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(scrollState)
    ) {
        // -------------------------------------------------------------
        // BAGIAN ATAS: sampul persegi rapi (Normal) atau poster live (Poster)
        // -------------------------------------------------------------
        if (visualMode == PlayerVisualMode.NORMAL) {
            val artShape = RoundedCornerShape(if (isV2) 36.dp else 28.dp)
            Box(modifier = Modifier.fillMaxWidth()) {
                // Latar blur dari sampul yang sama, memudar ke warna latar
                AsyncImage(
                    model = track.highResThumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .matchParentSize()
                        .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(48.dp) else Modifier)
                        .alpha(0.4f),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    colors.background.copy(alpha = 0.55f),
                                    colors.background
                                )
                            )
                        )
                )
                Column(modifier = Modifier.fillMaxWidth()) {
                    topBar(Modifier)
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 36.dp)
                            .aspectRatio(1f)
                            .shadow(24.dp, artShape, clip = false)
                            .clip(artShape)
                            .background(colors.card)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(track.highResThumbnailUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = track.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(470.dp)
            ) {
                // GAYA POSTER LIVE (TIKTOK / AURALYS STYLE)
                val effectiveDur = if (durationMs > 0) durationMs else (track.durationSeconds * 1000L).coerceAtLeast(1L)
                val progress = (playbackPositionMs.toFloat() / effectiveDur.toFloat()).coerceIn(0f, 1f)
                val activeIdx = currentLyrics?.getCurrentLineIndex(playbackPositionMs) ?: -1
                val activeLine = if (activeIdx >= 0) currentLyrics?.lines?.getOrNull(activeIdx) else null

                val dynamicState = remember(
                    livePosterState, track.id, track.isFavorite, progress,
                    playbackPositionMs, activeLine
                ) {
                    livePosterState.copy(
                        likedIds = if (track.isFavorite) setOf(track.id) else emptySet(),
                        progressMap = mapOf(track.id to progress),
                        elapsedTextMap = mapOf(track.id to formatMs(playbackPositionMs)),
                        activeLyricLine = activeLine?.text ?: track.title,
                        activeLyricTranslation = activeLine?.translation
                    )
                }

                // Render poster real-time (dengan kuantisasi ringan 120ms agar mulus 60fps tanpa lag)
                val quantizedTick = playbackPositionMs / 120L
                val posterBitmap by produceState<Bitmap?>(
                    null, dynamicState, quantizedTick, coverBitmap, customPhoto, customCutout, isPlaying
                ) {
                    value = withContext(Dispatchers.Default) {
                        PosterRenderer.render(
                            st = dynamicState,
                            tracks = listOf(track),
                            covers = listOf(coverBitmap),
                            photo = customPhoto,
                            cutout = customCutout,
                            outW = 540,
                            showBrandFooter = false,
                            isPlaying = isPlaying
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.background),
                    contentAlignment = Alignment.Center
                ) {
                    posterBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Live Song Poster",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(track.id) {
                                    // Tarik/seret beneran mengikuti jari
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val k = PosterRenderer.BW / size.width.coerceAtLeast(1)
                                        val cur = livePosterState.offsets[track.id] ?: (0f to 0f)
                                        livePosterState = livePosterState.copy(
                                            offsets = livePosterState.offsets + (track.id to (cur.first + dragAmount.x * k to cur.second + dragAmount.y * k))
                                        )
                                    }
                                }
                                .pointerInput(track.id) {
                                    // Cubit untuk memperbesar/memperkecil dan memutar kartu poster
                                    detectTransformGestures { _, pan, zoom, rotation ->
                                        val k = PosterRenderer.BW / size.width.coerceAtLeast(1)
                                        val curOffset = livePosterState.offsets[track.id] ?: (0f to 0f)
                                        val curAdj = livePosterState.adj[track.id] ?: CardAdj()
                                        val nextSize = (curAdj.size * zoom).toInt().coerceIn(55, 165)
                                        val nextRot = (curAdj.rot + rotation).toInt().coerceIn(-45, 45)
                                        livePosterState = livePosterState.copy(
                                            offsets = livePosterState.offsets + (track.id to (curOffset.first + pan.x * k to curOffset.second + pan.y * k)),
                                            adj = livePosterState.adj + (track.id to curAdj.copy(size = nextSize, rot = nextRot))
                                        )
                                    }
                                }
                        )
                    }

                    // Gradient bawah agar menyatu dengan panel kontrol pemutar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, colors.background)
                                )
                            )
                    )

                    // Toolbar mini khusus Gaya Poster (Foto, Kaca, Lirik, Rapikan)
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.58f))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PosterMiniPill(
                            label = if (isCuttingBg) "..." else if (customPhoto == null) (if (isEn) "Photo" else "Foto") else (if (isEn) "Change" else "Ganti Foto"),
                            active = customPhoto != null
                        ) { photoPicker.launch("image/*") }

                        PosterMiniPill(
                            label = when (livePosterState.glass) {
                                GlassType.SMOKE -> if (isEn) "Smoke" else "Kaca"
                                GlassType.CLEAR -> if (isEn) "Clear" else "Bening"
                                GlassType.MILK -> if (isEn) "Milk" else "Susu"
                                GlassType.DARK -> if (isEn) "Dark" else "Gelap"
                            },
                            active = false
                        ) {
                            val next = GlassType.entries[(livePosterState.glass.ordinal + 1) % GlassType.entries.size]
                            livePosterState = livePosterState.copy(glass = next)
                        }

                        PosterMiniPill(
                            label = if (isEn) "Lyrics" else "Lirik",
                            active = livePosterState.showLyricsOnPoster
                        ) {
                            livePosterState = livePosterState.copy(showLyricsOnPoster = !livePosterState.showLyricsOnPoster)
                        }

                        PosterMiniPill(
                            label = if (isEn) "Center" else "Rapikan",
                            active = false
                        ) {
                            livePosterState = livePosterState.tidy()
                        }
                    }
                }

                topBar(Modifier.align(Alignment.TopCenter))
            }
        }

        // -------------------------------------------------------------
        // KONTROL UTAMA
        // -------------------------------------------------------------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = Typography.headlineMedium.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = track.artist,
                        style = Typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onFavoriteClick, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = if (track.isFavorite) NexIcons.Favorite else NexIcons.FavoriteBorder,
                        contentDescription = S.cd("Favorite"),
                        tint = if (track.isFavorite) Color(0xFFFF3B30) else colors.textMuted,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            PlayerScrubber(viewModel = viewModel, track = track)

            Spacer(modifier = Modifier.height(14.dp))

            // Transport: acak, sebelumnya, putar/jeda, berikutnya, ulang
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onShuffleClick, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = NexIcons.Shuffle,
                        contentDescription = S.cd("Shuffle"),
                        tint = if (isShuffle) colors.textPrimary else colors.textSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(onClick = onSkipPreviousClick, modifier = Modifier.size(52.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_nexrify_rewind),
                        contentDescription = S.cd("Previous Track"),
                        tint = colors.textPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .then(
                            if (isV2) Modifier.background(Brush.linearGradient(listOf(colors.accent, colors.accentAlt)))
                            else Modifier.background(colors.textPrimary)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = colors.background, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                    } else {
                        IconButton(onClick = onPlayPauseClick, modifier = Modifier.size(72.dp)) {
                            Icon(
                                painter = if (isPlaying) painterResource(R.drawable.ic_nexrify_pause) else painterResource(R.drawable.ic_nexrify_play),
                                contentDescription = if (isPlaying) S.cd("Pause") else S.cd("Play"),
                                tint = colors.background,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                }

                IconButton(onClick = onSkipNextClick, modifier = Modifier.size(52.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_nexrify_fast_forward),
                        contentDescription = S.cd("Next Track"),
                        tint = colors.textPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                IconButton(onClick = onRepeatClick, modifier = Modifier.size(44.dp)) {
                    val icon = if (repeatMode == Player.REPEAT_MODE_ONE) NexIcons.RepeatOne else NexIcons.Repeat
                    val tint = if (repeatMode != Player.REPEAT_MODE_OFF) colors.textPrimary else colors.textSecondary.copy(alpha = 0.4f)
                    Icon(imageVector = icon, contentDescription = S.cd("Repeat"), tint = tint, modifier = Modifier.size(22.dp))
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Pemilih output audio (speaker / headset / Bluetooth)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.card)
                    .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                    .clickable { viewModel.openAudioOutputSettings() }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val icon = when (audioDeviceState.iconType) {
                    id.nexrify.music.data.audio.OutputType.WIRED -> NexIcons.Headphones
                    id.nexrify.music.data.audio.OutputType.BLUETOOTH -> NexIcons.BluetoothAudio
                    id.nexrify.music.data.audio.OutputType.SPEAKER -> NexIcons.VolumeUp
                }
                Icon(
                    imageVector = icon,
                    contentDescription = S.cd("Audio Output"),
                    tint = colors.textPrimary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = S.deviceLabel(audioDeviceState.title),
                    style = Typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Satu-satunya deretan aksi: playlist, unduh, timer tidur, kecepatan, bagikan
            PlayerQuickActions(
                track = track,
                viewModel = viewModel,
                onAddToPlaylist = { showAddToPlaylist = true }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Lirik
            val hasLyrics = currentLyrics != null && currentLyrics!!.lines.isNotEmpty() &&
                    currentLyrics!!.lines.none { it.text.trim().equals("null", ignoreCase = true) }

            run {
                val lyricsHeight = if (!hasLyrics && !isLoadingLyrics) 150.dp else if (isLyricsExpanded) 380.dp else 185.dp
                SyncedLyricsView(
                    lyricsData = currentLyrics,
                    isLoading = isLoadingLyrics,
                    onRetry = { viewModel.retryLyrics() },
                    playbackPositionFlow = viewModel.playbackPosition,
                    onSeekTo = { viewModel.seekTo(it) },
                    isExpanded = isLyricsExpanded,
                    onExpandToggle = {
                        isLyricsExpanded = !isLyricsExpanded
                        if (isLyricsExpanded) {
                            scope.launch { scrollState.animateScrollTo(scrollState.maxValue) }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(lyricsHeight)
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
        val commentShape = RoundedCornerShape(16.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
                .clip(commentShape)
                .background(colors.card)
                .border(1.dp, colors.border, commentShape)
                .clickable { showSongComments = true }
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(NexIcons.ChatBubbleOutline, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(S.songComments, style = Typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
        }
    }

    if (showAddToPlaylist) {
        AddToPlaylistDialog(
            track = track,
            viewModel = viewModel,
            onDismiss = { showAddToPlaylist = false }
        )
    }

    if (showQueue) {
        QueueSheet(viewModel = viewModel, onDismiss = { showQueue = false })
    }

    if (showSongComments) {
        SongCommentsSheet(track = track, onDismiss = { showSongComments = false })
    }
}

@Composable
private fun PlayerRoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, iconSize: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
    ) {
        Icon(imageVector = icon, contentDescription = desc, tint = Color.White, modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun ModeTabPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Color.White else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = Typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
            color = if (selected) Color.Black else Color.White.copy(alpha = 0.85f)
        )
    }
}

@Composable
private fun PosterMiniPill(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) Color.White else Color.White.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = Typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
            color = if (active) Color.Black else Color.White
        )
    }
}

@Composable
fun PlayerScrubber(
    viewModel: MusicViewModel,
    track: Track,
    modifier: Modifier = Modifier
) {
    val currentPositionMs by viewModel.playbackPosition.collectAsState()
    val durationMs by viewModel.duration.collectAsState()

    val effectiveDuration = if (durationMs > 0) durationMs else (track.durationSeconds * 1000L).coerceAtLeast(1L)
    val progress = if (effectiveDuration > 0) {
        (currentPositionMs.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val currentFormatted = formatMs(currentPositionMs)
    val totalFormatted = formatMs(effectiveDuration)

    val colors = NexrifyAppTheme.colors

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = progress,
            onValueChange = { newProgress ->
                viewModel.seekTo((newProgress * effectiveDuration).toLong())
            },
            colors = SliderDefaults.colors(
                thumbColor = colors.accent,
                activeTrackColor = colors.accent,
                inactiveTrackColor = colors.border.copy(alpha = 0.72f)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = currentFormatted, style = Typography.bodyMedium, color = colors.textSecondary)
            Text(text = totalFormatted, style = Typography.bodyMedium, color = colors.textSecondary)
        }
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

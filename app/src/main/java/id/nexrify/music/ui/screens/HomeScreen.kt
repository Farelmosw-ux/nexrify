package id.nexrify.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import id.nexrify.music.ui.components.nexGlassCard
import id.nexrify.music.ui.theme.*

@Composable
fun HomeScreen(
    userName: String,
    greetingTime: String,
    avatarUrl: String?,
    recentTracks: List<HomeTrack>,
    playlistCategories: List<PlaylistCategory>,
    onTrackClick: (HomeTrack) -> Unit,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg),
        contentPadding = PaddingValues(top = 24.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Header Section
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = greetingTime,
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = userName,
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .nexGlassCard(cornerRadius = 14.dp)
                            .clickable { onSearchClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = "Profile",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .nexGlassCard(cornerRadius = 21.dp)
                            .clickable { onProfileClick() }
                    )
                }
            }
        }

        // Quick Pick / Featured Hero Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(160.dp)
                    .nexGlassCard(cornerRadius = 24.dp, isActive = true)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    NexPurpleDeep.copy(alpha = 0.6f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "SPECIAL MIX",
                            color = NexPurpleLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Dark & Purple Vibes",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Kurasi musik terbaik untuk menemani harimu.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 2
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(NexPurplePrimary, Color(0xFF6D28D9))
                                )
                            )
                            .clickable { },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Play Mix",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }

        // Recent Tracks Section
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Putar Ulang Terakhir",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(recentTracks) { track ->
                        Column(
                            modifier = Modifier
                                .width(130.dp)
                                .clickable { onTrackClick(track) }
                        ) {
                            AsyncImage(
                                model = track.artworkUrl,
                                contentDescription = track.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(130.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(SurfaceElevated)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = track.title,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.artist,
                                color = TextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Playlist Categories Section
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Kategori Pilihan",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(playlistCategories) { category ->
                        Box(
                            modifier = Modifier
                                .nexGlassCard(cornerRadius = 14.dp)
                                .clickable { }
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = category.name,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

data class HomeTrack(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?
)

data class PlaylistCategory(
    val id: String,
    val name: String
)

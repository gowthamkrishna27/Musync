package com.musync.app.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil.compose.AsyncImage
import com.musync.app.core.image.ImageQualityHelper
import com.musync.app.domain.model.Track
import kotlinx.coroutines.delay
import kotlin.math.absoluteValue

/**
 * Editorial Playlist Item for the Hero Carousel
 */
data class EditorialPlaylistItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val categoryBadge: String,
    val artworkUrl: String?,
    val track: Track? = null,
    val tracks: List<Track> = emptyList()
)

/**
 * Helper to build high-quality editorial playlist cards from available music catalog data.
 */
fun buildEditorialCarouselItems(
    trendingTracks: List<Track>,
    teluguTracks: List<Track>,
    tamilTracks: List<Track>,
    hindiTracks: List<Track>,
    globalTracks: List<Track>
): List<EditorialPlaylistItem> {
    val items = mutableListOf<EditorialPlaylistItem>()

    // 1. Trending Top Charts
    val trending = trendingTracks.firstOrNull() ?: teluguTracks.firstOrNull()
    if (trending != null) {
        items.add(
            EditorialPlaylistItem(
                id = "editorial_trending",
                title = trending.title.ifBlank { "Top 50 Hits" },
                subtitle = "${trending.artist.name} · Trending Chartbusters",
                categoryBadge = "Trending",
                artworkUrl = trending.artworkUrl,
                track = trending,
                tracks = trendingTracks.ifEmpty { teluguTracks }
            )
        )
    }

    // 2. Telugu Superhits
    val telugu = teluguTracks.firstOrNull()
    if (telugu != null) {
        items.add(
            EditorialPlaylistItem(
                id = "editorial_telugu",
                title = telugu.title.ifBlank { "Telugu Hotlist" },
                subtitle = "${telugu.artist.name} · Latest Tollywood Hits",
                categoryBadge = "Telugu Hits",
                artworkUrl = telugu.artworkUrl,
                track = telugu,
                tracks = teluguTracks
            )
        )
    }

    // 3. Tamil Chartbusters
    val tamil = tamilTracks.firstOrNull()
    if (tamil != null) {
        items.add(
            EditorialPlaylistItem(
                id = "editorial_tamil",
                title = tamil.title.ifBlank { "Tamil Top 20" },
                subtitle = "${tamil.artist.name} · Kollywood Trending",
                categoryBadge = "Tamil Hits",
                artworkUrl = tamil.artworkUrl,
                track = tamil,
                tracks = tamilTracks
            )
        )
    }

    // 4. Bollywood Hindi Hits
    val hindi = hindiTracks.firstOrNull()
    if (hindi != null) {
        items.add(
            EditorialPlaylistItem(
                id = "editorial_hindi",
                title = hindi.title.ifBlank { "Bollywood Hits" },
                subtitle = "${hindi.artist.name} · Top Hindi Romance & Party",
                categoryBadge = "Hindi Hits",
                artworkUrl = hindi.artworkUrl,
                track = hindi,
                tracks = hindiTracks
            )
        )
    }

    // 5. Global Billboard Hits
    val global = globalTracks.firstOrNull()
    if (global != null) {
        items.add(
            EditorialPlaylistItem(
                id = "editorial_global",
                title = global.title.ifBlank { "Global Top Hits" },
                subtitle = "${global.artist.name} · Billboard Hot 100",
                categoryBadge = "Global Hits",
                artworkUrl = global.artworkUrl,
                track = global,
                tracks = globalTracks
            )
        )
    }

    // 6. Chill & Lo-Fi Vibes
    val chillTrack = trendingTracks.getOrNull(1) ?: teluguTracks.getOrNull(1)
    if (chillTrack != null) {
        items.add(
            EditorialPlaylistItem(
                id = "editorial_chill",
                title = chillTrack.title.ifBlank { "Chill & Relax" },
                subtitle = "${chillTrack.artist.name} · Ambient & Lo-Fi Beats",
                categoryBadge = "Chill & Relax",
                artworkUrl = chillTrack.artworkUrl,
                track = chillTrack,
                tracks = trendingTracks.drop(1)
            )
        )
    }

    // 7. Workout Beast Mode
    val workoutTrack = teluguTracks.getOrNull(2) ?: trendingTracks.getOrNull(2)
    if (workoutTrack != null) {
        items.add(
            EditorialPlaylistItem(
                id = "editorial_workout",
                title = workoutTrack.title.ifBlank { "High Energy Beats" },
                subtitle = "${workoutTrack.artist.name} · Power Workout Energy",
                categoryBadge = "Workout",
                artworkUrl = workoutTrack.artworkUrl,
                track = workoutTrack,
                tracks = teluguTracks.drop(2)
            )
        )
    }

    // 8. Romance & Feel Good
    val romanceTrack = tamilTracks.getOrNull(1) ?: hindiTracks.getOrNull(1)
    if (romanceTrack != null) {
        items.add(
            EditorialPlaylistItem(
                id = "editorial_romance",
                title = romanceTrack.title.ifBlank { "Romantic Melodies" },
                subtitle = "${romanceTrack.artist.name} · Heartstrings & Love",
                categoryBadge = "Romantic",
                artworkUrl = romanceTrack.artworkUrl,
                track = romanceTrack,
                tracks = tamilTracks.drop(1)
            )
        )
    }

    // 9. Discover & Fresh Drops
    val discoverTrack = globalTracks.getOrNull(1) ?: trendingTracks.getOrNull(3)
    if (discoverTrack != null) {
        items.add(
            EditorialPlaylistItem(
                id = "editorial_discover",
                title = discoverTrack.title.ifBlank { "Discover Weekly" },
                subtitle = "${discoverTrack.artist.name} · Fresh Releases & Discoveries",
                categoryBadge = "Discover",
                artworkUrl = discoverTrack.artworkUrl,
                track = discoverTrack,
                tracks = globalTracks.drop(1)
            )
        )
    }

    return items
}

/**
 * Premium Playlist Carousel with Depth Animation, Center Scaling,
 * Continuous Interpolation, Touch Gestures, Auto-Play, and Infinite Looping.
 */
@Composable
fun PremiumPlaylistCarousel(
    items: List<EditorialPlaylistItem>,
    isPlaying: Boolean,
    currentTrackId: String?,
    onPlayTrack: (Track, List<Track>) -> Unit,
    modifier: Modifier = Modifier,
    autoAdvanceIntervalMs: Long = 4000L
) {
    if (items.isEmpty()) return

    // Infinite looping setup with large virtual page count
    val virtualPageCount = if (items.size > 1) 100_000 else 1
    val initialPage = remember(items.size) {
        if (items.size > 1) (50_000 / items.size) * items.size else 0
    }
    val pagerState = rememberPagerState(initialPage = initialPage) { virtualPageCount }

    // Auto-advance carousel slowly; automatically pauses during touch/drag gestures
    LaunchedEffect(pagerState, items.size) {
        if (items.size <= 1) return@LaunchedEffect
        while (true) {
            delay(autoAdvanceIntervalMs)
            if (!pagerState.isScrollInProgress) {
                val nextPage = pagerState.currentPage + 1
                pagerState.animateScrollToPage(
                    page = nextPage,
                    animationSpec = tween(
                        durationMillis = 750,
                        easing = FastOutSlowInEasing
                    )
                )
            }
        }
    }

    HorizontalPager(
        state = pagerState,
        contentPadding = PaddingValues(horizontal = 46.dp),
        pageSpacing = 14.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) { page ->
        val itemIndex = ((page % items.size) + items.size) % items.size
        val item = items[itemIndex]

        // Exact continuous page offset calculation for fluid interpolation
        val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
        val absOffset = pageOffset.absoluteValue.coerceIn(0f, 1f)

        // Visual hierarchy interpolation:
        // Center card: scale = 1.0, opacity = 1.0
        // Side cards: scale = 0.82, opacity = 0.65
        val scale = lerp(1f, 0.82f, absOffset)
        val alpha = lerp(1f, 0.65f, absOffset)

        val isCurrentPlaying = (item.track?.id == currentTrackId || (item.tracks.any { it.id == currentTrackId })) && isPlaying

        PremiumCarouselCard(
            item = item,
            scale = scale,
            alpha = alpha,
            isPlaying = isCurrentPlaying,
            isCenter = absOffset < 0.15f,
            onClick = {
                val trackToPlay = item.track ?: item.tracks.firstOrNull()
                if (trackToPlay != null) {
                    onPlayTrack(trackToPlay, item.tracks.ifEmpty { listOf(trackToPlay) })
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                    clip = true
                    shape = RoundedCornerShape(26.dp)
                }
        )
    }
}

/**
 * Editorial Poster Card Component
 */
@Composable
private fun PremiumCarouselCard(
    item: EditorialPlaylistItem,
    scale: Float,
    alpha: Float,
    isPlaying: Boolean,
    isCenter: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageRequest = remember(item.artworkUrl, item.id) {
        ImageQualityHelper.buildOptimizedImageRequest(context, item.artworkUrl, item.track?.id ?: item.id)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF141419))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = if (isPlaying) listOf(Color(0xFFE50914), Color(0x33E50914))
                    else listOf(Color(0x33FFFFFF), Color(0x0DFFFFFF))
                ),
                shape = RoundedCornerShape(26.dp)
            )
            .clickable(onClick = onClick)
    ) {
        // 1. High-Resolution Poster Artwork (object-fit: cover)
        if (!item.artworkUrl.isNullOrBlank() || item.track?.id?.isNotBlank() == true) {
            AsyncImage(
                model = imageRequest,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            // Elegant Editorial Background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF2A2A35), Color(0xFF121216))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color(0x44FFFFFF),
                    modifier = Modifier.size(64.dp)
                )
            }
        }

        // 2. Subdued Scrim on Side Cards
        if (alpha < 0.95f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = (1f - alpha) * 0.45f))
            )
        }

        // 3. Multi-Stop Vignette & Gradient for Sharp Text Legibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x44000000),
                            Color.Transparent,
                            Color(0x22000000),
                            Color(0x88000000),
                            Color(0xF80C0C10)
                        ),
                        startY = 0f
                    )
                )
        )

        // 4. Top Category Pill Badge
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 18.dp, top = 18.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x99121216))
                .border(0.8.dp, Color(0x55FFFFFF), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(
                text = item.categoryBadge.uppercase(),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        // 5. Bottom Metadata & Interactive Glass Play Button
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 21.sp,
                        lineHeight = 26.sp
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color(0xCCFFFFFF),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Glass Play / Playing Action Button
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isPlaying) Color(0xFFE50914) else Color(0x66222228))
                    .border(1.dp, if (isPlaying) Color(0xFFFF5252) else Color(0x44FFFFFF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.GraphicEq else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Playing" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

package com.musync.app.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.musync.app.auth.AuthProviderType
import com.musync.app.auth.CloudSyncStatus
import com.musync.app.auth.MusyncUser
import com.musync.app.ui.theme.*

@Composable
fun AccountProfileCard(
    user: MusyncUser?,
    syncStatus: CloudSyncStatus,
    onSignInClick: () -> Unit,
    onSyncClick: () -> Unit,
    onSignOutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Rotation animation for sync button when syncing
    val infiniteTransition = rememberInfiniteTransition(label = "sync_rotation")
    val rotation by if (syncStatus == CloudSyncStatus.SYNCING) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "sync_spin"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    val cardGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF1E222D),
            Color(0xFF141720)
        )
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(cardGradient)
            .border(1.dp, Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x11FFFFFF))), RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        if (user != null && !user.isAnonymous) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Profile Avatar with dynamic glow/border
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .border(
                                1.5.dp,
                                Brush.linearGradient(listOf(Color(0x66FFFFFF), Color(0x22FFFFFF))),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!user.photoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = user.photoUrl,
                                contentDescription = "Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0x2AFFFFFF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (user.provider) {
                                        AuthProviderType.GITHUB -> Icons.Default.Code
                                        AuthProviderType.GOOGLE -> Icons.Default.Person
                                        else -> Icons.Default.AccountCircle
                                    },
                                    contentDescription = null,
                                    tint = TextWhite,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    // User Details & Provider Chip
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = user.displayName ?: "Musync Listener",
                                color = TextWhite,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            // Provider Badge
                            val (badgeBg, badgeText, badgeColor) = when (user.provider) {
                                AuthProviderType.GITHUB -> Triple(Color(0xFF24292E), "GitHub", Color.White)
                                AuthProviderType.GOOGLE -> Triple(Color(0x224285F4), "Google", Color(0xFF93C5FD))
                                else -> Triple(Color(0x223B82F6), "Account", Color(0xFF93C5FD))
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(badgeBg)
                                    .border(0.5.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 7.dp, vertical = 2.5.dp)
                            ) {
                                Text(
                                    text = badgeText,
                                    color = badgeColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        if (!user.email.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = user.email,
                                color = TextGreySecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Sync Status Pill
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val (syncText, syncColor) = when (syncStatus) {
                                CloudSyncStatus.SYNCING -> "Syncing library..." to Color(0xFF60A5FA)
                                CloudSyncStatus.SYNCED -> "Cloud Synced" to Color(0xFF4ADE80)
                                CloudSyncStatus.OFFLINE -> "Offline Ready" to Color(0xFFFACC15)
                                CloudSyncStatus.ERROR -> "Sync Offline" to Color(0xFFEF4444)
                                CloudSyncStatus.IDLE -> "Cloud Connected" to TextGreySecondary
                            }

                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(syncColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = syncText,
                                color = syncColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Action Controls
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Sync Button
                        IconButton(
                            onClick = onSyncClick,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0x1EFFFFFF))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Sync",
                                tint = TextWhite,
                                modifier = Modifier
                                    .size(18.dp)
                                    .rotate(rotation)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Sign Out Button
                        IconButton(
                            onClick = onSignOutClick,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0x1AEF4444))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Logout,
                                contentDescription = "Sign Out",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        } else {
            // Unauthenticated / Guest View
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cloud Sync Icon with circular glass badge
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0x2A3B82F6), Color(0x1A1E293B))
                            )
                        )
                        .border(1.dp, Color(0x3360A5FA), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudQueue,
                        contentDescription = null,
                        tint = Color(0xFF93C5FD),
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Musync Cloud Sync",
                        color = TextWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Backup playlists, favorites & history",
                        color = TextGreySecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Modern Sign In Pill Button
                Button(
                    onClick = onSignInClick,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                    modifier = Modifier.height(38.dp)
                ) {
                    Text(
                        text = "Sign In",
                        color = Color.Black,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

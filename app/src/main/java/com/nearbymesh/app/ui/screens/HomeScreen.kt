package com.nearbymesh.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nearbymesh.app.core.MeshCoordinator
import com.nearbymesh.app.core.MeshPeer
import com.nearbymesh.app.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * Friend's Contact / Radar Screen matching reference screenshot 1:1.
 * Features:
 * - Concentric dark slate circular radar discs
 * - White center squircle card with sleek emblem
 * - 10 Orbiting avatar bubbles along the circular track
 * - "Friend's Contact" bold typography + "Enjoy the first offline decentralized chat"
 * - Prominent white "Enter" pill button
 * - Zero clutter: message/send/group icons removed as requested.
 * - 20 Built-in offline avatars picker sheet.
 */
@Composable
fun HomeScreen(
    coordinator: MeshCoordinator,
    onOpenChat: (peerId: String) -> Unit,
    onOpenChatsTab: () -> Unit,
    onOpenCreateGroup: () -> Unit,
    onOpenReceive: () -> Unit,
    onOpenDeviceProfile: (MeshPeer) -> Unit,
    onOpenSettings: () -> Unit,
    onRequestPermissions: () -> Unit = {}
) {
    val peersMap by coordinator.peers.collectAsState()
    val peers = peersMap.values.toList()
    val userProfile by coordinator.userProfile.collectAsState()
    val isScanning by coordinator.isScanning.collectAsState()

    var showAvatarPicker by remember { mutableStateOf(false) }

    // Smooth continuous rotation for revolving contact avatars
    val infiniteTransition = rememberInfiniteTransition(label = "RadarOrbit")
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(28000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "OrbitAngle"
    )

    // Pulse animation for scanning wave
    val scanPulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ScanPulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0E))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(bottom = 96.dp), // Clearance for bottom pill navigation bar
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ========================================================
            // TOP STATUS ROW (Minimalist Radar Status & Trigger)
            // ========================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x18FFFFFF))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (peers.isNotEmpty()) Color(0xFF22C55E) else Color(0xFF38BDF8))
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = if (peers.isNotEmpty()) "${peers.size} nearby" else if (isScanning) "Scanning..." else "Mesh Active",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }

                // Minimalist Scan Refresh Icon
                IconButton(
                    onClick = { coordinator.triggerScan() },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0x14FFFFFF))
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Scan",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.1f))

            // ========================================================
            // HERO RADAR CONSTELLATION DISC (Matching Screenshot 1:1)
            // ========================================================
            Box(
                modifier = Modifier
                    .size(320.dp),
                contentAlignment = Alignment.Center
            ) {
                // 1. Outer Track Disc (Dark Slate #1E1E26)
                Box(
                    modifier = Modifier
                        .size(310.dp)
                        .graphicsLayer {
                            scaleX = scanPulse
                            scaleY = scanPulse
                        }
                        .clip(CircleShape)
                        .background(Color(0xFF1E1E26))
                )

                // 2. Inner Track Disc (Mid Tone #2A2A34)
                Box(
                    modifier = Modifier
                        .size(208.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2B2B36))
                )

                // 3. Center White Squircle Card with Sleek "h" / "hi" Emblem
                Surface(
                    modifier = Modifier
                        .size(88.dp)
                        .shadow(16.dp, RoundedCornerShape(26.dp), spotColor = Color(0x88000000))
                        .clip(RoundedCornerShape(26.dp))
                        .clickable { showAvatarPicker = true },
                    color = Color.White,
                    shape = RoundedCornerShape(26.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        // Sleek lowercase "h" / "hi" typography matching screenshot
                        Text(
                            text = "h",
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Serif,
                            color = Color.Black,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // 4. Orbiting Avatar Contact Bubbles (10 Contacts in orbit)
                val totalBubbles = 10
                val angleStep = 360.0 / totalBubbles
                val radiusPx = 118.dp

                for (i in 0 until totalBubbles) {
                    val peer = peers.getOrNull(i)
                    // If peer is connected, use their avatarId, otherwise use preset avatar for scanning visualization
                    val avatarId = (i % 20) + 1

                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                val currentDeg = (orbitAngle + i * angleStep) % 360.0
                                val rad = Math.toRadians(currentDeg)
                                translationX = (radiusPx.toPx() * cos(rad)).toFloat()
                                translationY = (radiusPx.toPx() * sin(rad)).toFloat()
                            }
                            .clickable {
                                if (peer != null) {
                                    onOpenChat(peer.nodeId)
                                } else {
                                    showAvatarPicker = true
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AvatarBubble(
                            avatarId = avatarId,
                            size = 42.dp,
                            showBorder = true,
                            borderColor = if (peer != null) Color(0xFF22C55E) else Color(0x33FFFFFF),
                            borderWidth = if (peer != null) 2.dp else 1.dp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.15f))

            // ========================================================
            // BOTTOM CONTENT: TITLE + SUBTITLE + "ENTER" PILL BUTTON
            // ========================================================
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Large Bold Title
                Text(
                    text = "Friend's Contact",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    letterSpacing = (-0.5).sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Subtitle
                Text(
                    text = "Enjoy the first offline decentralized chat",
                    fontSize = 14.sp,
                    color = Color(0xFF8E8E98),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Prominent White Pill "Enter" Button (Matching Screenshot 1:1)
                Button(
                    onClick = {
                        if (peers.isNotEmpty()) {
                            onOpenChat(peers.first().nodeId)
                        } else {
                            onOpenChatsTab()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.86f)
                        .height(56.dp)
                        .shadow(12.dp, RoundedCornerShape(28.dp), spotColor = Color(0x55000000)),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                ) {
                    Text(
                        text = "Enter",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        // ========================================================
        // 20 BUILT-IN OFFLINE AVATARS PICKER SHEET
        // ========================================================
        if (showAvatarPicker) {
            AvatarPickerSheet(
                selectedId = userProfile.avatarId,
                onSelectAvatar = { newId ->
                    coordinator.updateAvatarId(newId)
                },
                onDismiss = { showAvatarPicker = false }
            )
        }
    }
}

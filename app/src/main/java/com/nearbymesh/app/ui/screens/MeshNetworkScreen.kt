package com.nearbymesh.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nearbymesh.app.core.MeshCoordinator
import com.nearbymesh.app.core.MeshPeer
import com.nearbymesh.app.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MeshNetworkScreen(
    coordinator: MeshCoordinator,
    onBack: () -> Unit = {}
) {
    val peersMap by coordinator.peers.collectAsState()
    val peers = peersMap.values.toList()
    val userProfile by coordinator.userProfile.collectAsState()

    var meshModeActive by remember { mutableStateOf(userProfile.isMeshRelayEnabled) }
    var discoveryActive by remember { mutableStateOf(userProfile.isDiscoveryEnabled) }

    val surfaceColor = MaterialTheme.colorScheme.surface
    val backgroundColor = MaterialTheme.colorScheme.background
    val textPrimary = MaterialTheme.colorScheme.onBackground
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = MaterialTheme.colorScheme.outline

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = textPrimary
                )
            }
            Text(
                text = "Mesh Network",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )
            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "Scan",
                    tint = textPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Central Radial Constellation Map Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            colors = CardDefaults.cardColors(containerColor = surfaceColor),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                MeshConstellationCanvas(peers = peers)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Legend row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .height(3.dp)
                    .background(BrandEmerald)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Direct Connection", fontSize = 11.sp, color = textSecondary)

            Spacer(modifier = Modifier.width(16.dp))

            Box(
                modifier = Modifier
                    .width(16.dp)
                    .height(3.dp)
                    .background(BrandCyan)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Relay Connection", fontSize = 11.sp, color = textSecondary)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Connected Devices Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = surfaceColor),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = BrandEmerald.copy(alpha = 0.15f),
                        shape = CircleShape,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = null,
                                tint = BrandEmerald,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "${peers.size} devices in mesh",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                        Text(
                            text = "All connected locally • Extending range",
                            fontSize = 12.sp,
                            color = textSecondary
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Mesh Mode Toggle
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = surfaceColor),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ElectricBolt,
                        contentDescription = null,
                        tint = BrandEmerald,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Mesh Mode",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textPrimary
                        )
                        Text(
                            text = "Automatically relay messages for others",
                            fontSize = 11.sp,
                            color = textSecondary
                        )
                    }
                }
                Switch(
                    checked = meshModeActive,
                    onCheckedChange = {
                        meshModeActive = it
                        coordinator.updateUserProfile(userProfile.copy(isMeshRelayEnabled = it))
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BrandEmerald,
                        checkedTrackColor = BrandEmerald.copy(alpha = 0.3f)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Discovery Toggle
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = surfaceColor),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = null,
                        tint = BrandCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Discovery",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textPrimary
                        )
                        Text(
                            text = "Visible to nearby devices",
                            fontSize = 11.sp,
                            color = textSecondary
                        )
                    }
                }
                Switch(
                    checked = discoveryActive,
                    onCheckedChange = {
                        discoveryActive = it
                        coordinator.updateUserProfile(userProfile.copy(isDiscoveryEnabled = it))
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BrandEmerald,
                        checkedTrackColor = BrandEmerald.copy(alpha = 0.3f)
                    )
                )
            }
        }
    }
}

@Composable
fun MeshConstellationCanvas(peers: List<MeshPeer>) {
    val infiniteTransition = rememberInfiniteTransition(label = "ConstellationPulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Pulse"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val orbitRadius = minOf(size.width, size.height) / 2.7f

            // Central glowing field
            drawCircle(
                color = BrandEmerald.copy(alpha = 0.08f),
                radius = orbitRadius * 1.3f,
                center = center
            )

            // Outer constellation ring
            drawCircle(
                color = Color.Gray.copy(alpha = 0.15f),
                radius = orbitRadius,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
            )

            // Connect central "You" node to surrounding peer nodes
            peers.forEachIndexed { index, peer ->
                val angle = Math.toRadians((index * (360.0 / peers.size.coerceAtLeast(1)) - 90))
                val nodeX = (center.x + orbitRadius * cos(angle)).toFloat()
                val nodeY = (center.y + orbitRadius * sin(angle)).toFloat()

                val isDirect = peer.hopsAway <= 1
                val lineColor = if (isDirect) BrandEmerald else BrandCyan

                // Draw link line
                drawLine(
                    color = lineColor.copy(alpha = 0.7f),
                    start = center,
                    end = Offset(nodeX, nodeY),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = if (!isDirect) PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f) else null
                )

                // Draw inter-relay connection between neighboring nodes
                if (index > 0) {
                    val prevAngle = Math.toRadians(((index - 1) * (360.0 / peers.size.coerceAtLeast(1)) - 90))
                    val prevX = (center.x + orbitRadius * cos(prevAngle)).toFloat()
                    val prevY = (center.y + orbitRadius * sin(prevAngle)).toFloat()
                    drawLine(
                        color = BrandCyan.copy(alpha = 0.35f),
                        start = Offset(prevX, prevY),
                        end = Offset(nodeX, nodeY),
                        strokeWidth = 1.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                    )
                }
            }
        }

        // Central "You" Node
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                color = BrandEmerald,
                shape = CircleShape,
                modifier = Modifier
                    .size(52.dp)
                    .border(2.dp, Color.White, CircleShape),
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Smartphone,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "You",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Position surrounding peer node pills
        val total = peers.size.coerceAtLeast(1)
        peers.forEachIndexed { index, peer ->
            val angle = Math.toRadians((index * (360.0 / total) - 90))
            val radiusDp = 100.dp
            val offsetX = (radiusDp.value * cos(angle)).dp
            val offsetY = (radiusDp.value * sin(angle)).dp

            Box(
                modifier = Modifier
                    .offset(x = offsetX, y = offsetY)
                    .align(Alignment.Center)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        color = if (peer.hopsAway <= 1) Color(0xFF1E3A8A) else Color(0xFF0F172A),
                        shape = CircleShape,
                        modifier = Modifier
                            .size(38.dp)
                            .border(1.5.dp, if (peer.hopsAway <= 1) BrandEmerald else BrandCyan, CircleShape)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = peer.nickname.take(1),
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = peer.nickname.take(6),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${peer.estimatedDistance}m",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

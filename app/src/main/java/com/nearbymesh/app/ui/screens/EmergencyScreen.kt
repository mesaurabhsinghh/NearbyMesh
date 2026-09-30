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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nearbymesh.app.core.MeshCoordinator
import com.nearbymesh.app.ui.theme.*

@Composable
fun EmergencyScreen(
    coordinator: MeshCoordinator,
    onBack: () -> Unit = {}
) {
    var isBroadcasting by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "EmergencyShockwave")
    val waveScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WaveScale"
    )

    val surfaceColor = MaterialTheme.colorScheme.surface
    val backgroundColor = MaterialTheme.colorScheme.background
    val textPrimary = MaterialTheme.colorScheme.onBackground
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = MaterialTheme.colorScheme.outline

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = textPrimary
                )
            }
            Text(
                text = "Emergency Mode",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )
            Spacer(modifier = Modifier.width(48.dp))
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Concentric Red Shockwave Halo & SOS Button (Matching Image 2 Screen 9 & Image 3 Screen 10)
        Box(
            modifier = Modifier.size(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val baseRadius = size.width / 3.0f

                // Outer shockwave rings
                listOf(1.0f, 1.3f, 1.6f).forEach { multiplier ->
                    val waveRadius = baseRadius * multiplier * (if (isBroadcasting) waveScale else 1f)
                    drawCircle(
                        color = BrandRed.copy(alpha = (0.25f / multiplier).coerceIn(0.05f, 0.4f)),
                        radius = waveRadius,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }

            // Central Tactile SOS Button
            Surface(
                color = BrandRed,
                shape = CircleShape,
                modifier = Modifier
                    .size(110.dp)
                    .clickable {
                        isBroadcasting = !isBroadcasting
                        if (isBroadcasting) {
                            coordinator.emergencyManager.broadcastEmergencySos(
                                myNodeId = coordinator.myNodeId,
                                nickname = coordinator.myNickname,
                                message = "Need medical assistance",
                                batteryLevel = coordinator.getBatteryLevel()
                            )
                        }
                    },
                shadowElevation = 14.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "SOS",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                        Text(
                            text = "SOS",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Status Text
        Text(
            text = if (isBroadcasting) "Broadcasting to nearby devices..." else "Broadcast to Nearby Devices",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = BrandRed
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "\"Need medical assistance\"",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = textPrimary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Message shared with in-range note
        Surface(
            color = BrandRedDark,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ChatBubble,
                    contentDescription = null,
                    tint = BrandRed,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Message will be shared with all NearbyMesh users in range.",
                    fontSize = 12.sp,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 3 Action Cards (Matching Image 3 Screen 10)
        EmergencyOptionCard(
            icon = Icons.Default.ChatBubbleOutline,
            title = "Send Emergency Message",
            subtitle = "Alert nearby people",
            onClick = {}
        )
        Spacer(modifier = Modifier.height(8.dp))
        EmergencyOptionCard(
            icon = Icons.Default.LocationOn,
            title = "Share Live Location",
            subtitle = "Works offline (mesh)",
            onClick = {}
        )
        Spacer(modifier = Modifier.height(8.dp))
        EmergencyOptionCard(
            icon = Icons.Default.Description,
            title = "Emergency File Share",
            subtitle = "Share medical info / documents",
            onClick = {}
        )

        Spacer(modifier = Modifier.weight(1f))

        // Primary Action Button (Start / Stop Broadcast)
        Button(
            onClick = {
                isBroadcasting = !isBroadcasting
                if (isBroadcasting) {
                    coordinator.emergencyManager.broadcastEmergencySos(
                        myNodeId = coordinator.myNodeId,
                        nickname = coordinator.myNickname,
                        message = "Need medical assistance",
                        batteryLevel = coordinator.getBatteryLevel()
                    )
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isBroadcasting) Color(0xFF261D00) else BrandRed,
                contentColor = if (isBroadcasting) BrandAmber else Color.White
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(
                text = if (isBroadcasting) "Stop Broadcast" else "Start Broadcast",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
fun EmergencyOptionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = BrandRed,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

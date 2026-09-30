package com.nearbymesh.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nearbymesh.app.ui.theme.*

@Composable
fun ReceiveFileScreen(
    fileName: String = "Video_4K.mp4",
    fileSize: String = "1.8 GB",
    onBack: () -> Unit
) {
    var isPaused by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "ReceivePulse")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseRadius"
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
                text = "Receiving File",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )
            Spacer(modifier = Modifier.width(48.dp))
        }

        Spacer(modifier = Modifier.weight(0.5f))

        // Center Glowing Receiving Halo Disc (Matching Image 3 Screen 9)
        Box(
            modifier = Modifier.size(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val baseRadius = size.width / 2.4f

                // Glowing outer ring
                drawCircle(
                    color = BrandCyan.copy(alpha = 0.2f),
                    radius = baseRadius * pulseRadius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )

                // Mid ring
                drawCircle(
                    color = BrandCyan.copy(alpha = 0.4f),
                    radius = baseRadius,
                    center = center,
                    style = Stroke(width = 3.dp.toPx())
                )
            }

            // File Icon Box
            Surface(
                color = Color(0xFF0F3654),
                shape = CircleShape,
                modifier = Modifier
                    .size(90.dp)
                    .border(2.dp, BrandCyan, CircleShape),
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.InsertDriveFile,
                        contentDescription = null,
                        tint = BrandCyanGlow,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Receiving...",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = BrandCyan
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = fileName,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = textPrimary
        )

        Text(
            text = fileSize,
            fontSize = 13.sp,
            color = textSecondary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = null,
                tint = BrandEmerald,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "38 MB/s • 45 seconds left",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = BrandEmerald
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Linear Progress Bar & Percentage
        Column(modifier = Modifier.fillMaxWidth(0.85f)) {
            LinearProgressIndicator(
                progress = { 0.52f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = BrandCyan,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "52%",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary,
                modifier = Modifier.align(Alignment.End)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Bottom Action Controls: Pause & Cancel
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { isPaused = !isPaused },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = textPrimary)
            ) {
                Icon(
                    imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isPaused) "Resume" else "Pause", fontSize = 14.sp)
            }

            Button(
                onClick = onBack,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandRed,
                    contentColor = Color.White
                )
            ) {
                Text("Cancel", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

package com.nearbymesh.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nearbymesh.app.core.MeshCoordinator
import com.nearbymesh.app.core.models.RealFileItem
import com.nearbymesh.app.ui.theme.*

@Composable
fun TransferProgressScreen(
    coordinator: MeshCoordinator,
    targetName: String = "Rahul",
    onBack: () -> Unit,
    onCancel: () -> Unit
) {
    val selectedFiles by coordinator.selectedFiles.collectAsState()

    val surfaceColor = MaterialTheme.colorScheme.surface
    val backgroundColor = MaterialTheme.colorScheme.background
    val textPrimary = MaterialTheme.colorScheme.onBackground
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = MaterialTheme.colorScheme.outline

    val animatedProgress = remember { Animatable(0.78f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .statusBarsPadding()
            .navigationBarsPadding()
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
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = textPrimary
                )
            }
            Text(
                text = "Sending to $targetName",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )
            TextButton(onClick = onCancel) {
                Text(
                    text = "Cancel",
                    color = BrandRed,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Large Circular Gauge Progress Ring (Matching Image 1 Screen 4)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(190.dp)) {
                val strokeWidth = 14.dp.toPx()
                val radius = (size.width - strokeWidth) / 2f
                val center = Offset(size.width / 2f, size.height / 2f)

                // Background track
                drawCircle(
                    color = BrandEmerald.copy(alpha = 0.15f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = strokeWidth)
                )

                // Progress Arc
                drawArc(
                    color = BrandEmerald,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress.value,
                    useCenter = false,
                    topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
                    size = Size(size.width - strokeWidth, size.height - strokeWidth),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${(animatedProgress.value * 100).toInt()}%",
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
                Text(
                    text = "1.64 GB / 2.1 GB",
                    fontSize = 13.sp,
                    color = textSecondary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "42 MB/s",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandEmerald
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Transfer Queue List
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(selectedFiles) { file ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = surfaceColor),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.InsertDriveFile,
                                            contentDescription = null,
                                            tint = BrandEmerald,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = file.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textPrimary
                                    )
                                    Text(
                                        text = file.formattedSize,
                                        fontSize = 11.sp,
                                        color = textSecondary
                                    )
                                }
                            }

                            if (file.name.contains("IMG")) {
                                Surface(
                                    color = BrandEmerald.copy(alpha = 0.15f),
                                    shape = CircleShape,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Done",
                                            tint = BrandEmerald,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            } else if (file.name.contains("Video")) {
                                Text(
                                    text = "78%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandEmerald
                                )
                            } else {
                                Text(
                                    text = "Waiting...",
                                    fontSize = 11.sp,
                                    color = textSecondary
                                )
                            }
                        }

                        if (file.name.contains("Video")) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { 0.78f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = BrandEmerald,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Bottom Stats Row (Speed, Time Left, Files Count)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = surfaceColor),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TransferStatItem(
                    icon = Icons.Default.Bolt,
                    value = "42 MB/s",
                    label = "Speed",
                    color = BrandEmerald
                )
                Divider(
                    modifier = Modifier
                        .height(30.dp)
                        .width(1.dp),
                    color = borderColor
                )
                TransferStatItem(
                    icon = Icons.Default.Schedule,
                    value = "25 sec",
                    label = "Time Left",
                    color = BrandCyan
                )
                Divider(
                    modifier = Modifier
                        .height(30.dp)
                        .width(1.dp),
                    color = borderColor
                )
                TransferStatItem(
                    icon = Icons.Default.Folder,
                    value = "${selectedFiles.size} Files",
                    label = "Total",
                    color = BrandAmber
                )
            }
        }
    }
}

@Composable
fun TransferStatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

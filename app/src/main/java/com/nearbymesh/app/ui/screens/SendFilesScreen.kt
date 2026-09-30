package com.nearbymesh.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nearbymesh.app.core.MeshCoordinator
import com.nearbymesh.app.core.models.FileCategory
import com.nearbymesh.app.core.models.RealFileItem
import com.nearbymesh.app.ui.theme.*

@Composable
fun SendFilesScreen(
    coordinator: MeshCoordinator,
    initialTargetNodeId: String? = null,
    onBack: () -> Unit,
    onStartTransfer: () -> Unit
) {
    val selectedFiles by coordinator.selectedFiles.collectAsState()
    val peersMap by coordinator.peers.collectAsState()
    val peers = peersMap.values.toList()

    var activeCategory by remember { mutableStateOf(FileCategory.FILES) }
    var targetPeer by remember {
        mutableStateOf(peers.firstOrNull { it.nodeId == initialTargetNodeId } ?: peers.firstOrNull())
    }

    val surfaceColor = MaterialTheme.colorScheme.surface
    val backgroundColor = MaterialTheme.colorScheme.background
    val textPrimary = MaterialTheme.colorScheme.onBackground
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = MaterialTheme.colorScheme.outline

    val totalSizeFormatted = remember(selectedFiles) {
        val totalBytes = selectedFiles.sumOf { it.sizeBytes }
        val gb = totalBytes / (1024.0 * 1024.0 * 1024.0)
        val mb = totalBytes / (1024.0 * 1024.0)
        if (gb >= 1.0) "%.1f GB".format(gb) else "%.1f MB".format(mb)
    }

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
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = textPrimary
                )
            }
            Text(
                text = "Send Files",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )
            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = "History",
                    tint = textPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Category Filter Tabs (Matching Image 1 Screen 3)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(FileCategory.values()) { category ->
                val isSelected = category == activeCategory
                Surface(
                    color = if (isSelected) BrandEmerald else surfaceColor,
                    shape = RoundedCornerShape(20.dp),
                    border = if (!isSelected) androidx.compose.foundation.BorderStroke(1.dp, borderColor) else null,
                    modifier = Modifier.clickable { activeCategory = category }
                ) {
                    Text(
                        text = category.label,
                        color = if (isSelected) Color.White else textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Selected Files List
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // File Type Icon Box
                            Surface(
                                color = when (file.category) {
                                    FileCategory.PHOTOS -> Color(0xFF1E88E5)
                                    FileCategory.VIDEOS -> Color(0xFFE53935)
                                    FileCategory.APPS -> Color(0xFF43A047)
                                    else -> Color(0xFF8E24AA)
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = when (file.category) {
                                            FileCategory.PHOTOS -> Icons.Default.Image
                                            FileCategory.VIDEOS -> Icons.Default.Videocam
                                            FileCategory.APPS -> Icons.Default.Android
                                            else -> Icons.Default.InsertDriveFile
                                        },
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = file.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textPrimary
                                )
                                Text(
                                    text = file.formattedSize,
                                    fontSize = 12.sp,
                                    color = textSecondary
                                )
                            }
                        }

                        // Remove X Button
                        IconButton(onClick = { coordinator.removeSelectedFile(file.id) }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove",
                                tint = textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // "Send to" Target Recipient Selector Card
        Text(
            text = "Send to",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = textSecondary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = surfaceColor),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E3A8A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = targetPeer?.nickname?.take(1) ?: "R",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = targetPeer?.nickname ?: "Rahul",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                        Text(
                            text = "${targetPeer?.estimatedDistance ?: 2.3} m away",
                            fontSize = 12.sp,
                            color = textSecondary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    SignalBars(rssi = targetPeer?.rssi ?: -50)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = textSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Floating Action Button: Send X Files (~ Y GB)
        Button(
            onClick = onStartTransfer,
            colors = ButtonDefaults.buttonColors(
                containerColor = BrandEmerald,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Send ${selectedFiles.size} Files (~ $totalSizeFormatted)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun SignalBars(rssi: Int, modifier: Modifier = Modifier) {
    val bars = when {
        rssi > -60 -> 4
        rssi > -70 -> 3
        rssi > -80 -> 2
        else -> 1
    }
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        for (i in 1..4) {
            val height = (4 + i * 3).dp
            val color = if (i <= bars) Color(0xFF22C55E) else Color(0xFF4B5563)
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(height)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color)
            )
        }
    }
}

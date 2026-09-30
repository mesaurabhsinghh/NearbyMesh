package com.nearbymesh.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nearbymesh.app.core.MeshPeer
import com.nearbymesh.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceProfileSheet(
    peer: MeshPeer,
    onDismiss: () -> Unit,
    onChatClick: () -> Unit,
    onSendFilesClick: () -> Unit,
    onConnectClick: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Glowing Avatar
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E3A8A))
                    .border(3.dp, BrandEmerald, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = peer.nickname.take(1),
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Name & Handle
            Text(
                text = "${peer.nickname}'s Device",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Node ID: ${peer.nodeId}",
                fontSize = 12.sp,
                color = BrandEmerald
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "NearbyMesh Peer • 0 MB Data",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 4 Metric Badges Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ProfileMetricBadge(
                    icon = Icons.Default.NearMe,
                    value = "${peer.estimatedDistance} m",
                    label = "Distance"
                )
                ProfileMetricBadge(
                    icon = Icons.Default.BatteryChargingFull,
                    value = if (peer.batteryPercent != -1) "${peer.batteryPercent}%" else "--",
                    label = "Battery"
                )
                ProfileMetricBadge(
                    icon = Icons.Default.Smartphone,
                    value = peer.connectionType,
                    label = "Transport"
                )
                ProfileMetricBadge(
                    icon = Icons.Default.Wifi,
                    value = if (peer.rssi >= -60) "High" else "Medium",
                    label = "Signal"
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Options List
            ProfileOptionRow(
                icon = Icons.Default.ChatBubbleOutline,
                title = "Chat",
                onClick = {
                    onDismiss()
                    onChatClick()
                }
            )
            ProfileOptionRow(
                icon = Icons.Default.FolderOpen,
                title = "Send Files",
                onClick = {
                    onDismiss()
                    onSendFilesClick()
                }
            )
            ProfileOptionRow(
                icon = Icons.Default.InsertDriveFile,
                title = "View Shared Files",
                onClick = { }
            )
            ProfileOptionRow(
                icon = Icons.Default.QrCode,
                title = "Pair & Connect",
                onClick = onConnectClick
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Primary Connect Button
            Button(
                onClick = onConnectClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandEmerald,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = "Connect",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun ProfileMetricBadge(icon: ImageVector, value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = BrandEmerald,
            modifier = Modifier.size(20.dp)
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

@Composable
fun ProfileOptionRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

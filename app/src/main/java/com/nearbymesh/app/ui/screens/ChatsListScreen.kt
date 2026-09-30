package com.nearbymesh.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nearbymesh.app.core.MeshCoordinator
import com.nearbymesh.app.core.MeshPeer
import com.nearbymesh.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Messages Screen (Picture 1 - Right Screen with Full Glassmorphism).
 * Features:
 * - Frosted glass "Search people" bar with refractive border and tune button
 * - Horizontal Stories / Active Peers row with glossy "Start a story" card
 * - "Messages" section header
 * - Frosted glass conversation cards with avatar rings, last message preview, timestamp, and glowing blue unread pill badge
 */
@Composable
fun ChatsListScreen(
    coordinator: MeshCoordinator,
    onOpenChat: (peerId: String, peerName: String) -> Unit,
    onTriggerScan: () -> Unit
) {
    val peersMap by coordinator.peers.collectAsState()
    val peers = peersMap.values.toList()
    val richMessages by coordinator.richMessages.collectAsState()
    val isScanning by coordinator.isScanning.collectAsState()

    val currentTheme by coordinator.appTheme.collectAsState()
    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isDark = when (currentTheme) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> isSystemDark
    }
    val chatsBgBrush = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0F1016),
                Color(0xFF08080C),
                Color(0xFF000000)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFF8FAFC),
                Color(0xFFF1F5F9),
                Color(0xFFE2E8F0)
            )
        )
    }
    val textPrimary = if (isDark) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDark) Color(0xFF8E8E98) else Color(0xFF64748B)

    var searchQuery by remember { mutableStateOf("") }

    val filteredPeers = remember(peers, searchQuery) {
        if (searchQuery.isBlank()) peers
        else peers.filter { it.nickname.contains(searchQuery, ignoreCase = true) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(chatsBgBrush)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp) // Clearance for floating pill bar
        ) {
            // 1. Frosted Glass Search Bar (Matching Picture 1 Right)
            item {
                Surface(
                    color = if (isDark) Color(0xCC161620) else Color(0xFFFFFFFF),
                    shape = RoundedCornerShape(26.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isDark) GlassHighlightGradient else Brush.linearGradient(listOf(Color(0xFFCBD5E1), Color(0xFFE2E8F0)))
                    ),
                    shadowElevation = if (isDark) 10.dp else 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = if (isDark) Color(0xFFA0A0AB) else Color(0xFF64748B),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            androidx.compose.foundation.text.BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = textPrimary,
                                    fontSize = 15.sp
                                ),
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Search people",
                                            color = textSecondary,
                                            fontSize = 15.sp
                                        )
                                    }
                                    innerTextField()
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        IconButton(
                            onClick = onTriggerScan,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Scan Filter",
                                tint = if (isDark) Color(0xFFA0A0AB) else Color(0xFF64748B),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            // 2. Active Nearby Peers Strip (Clean peer badges, No stories)
            if (peers.isNotEmpty()) {
                item {
                    Column {
                        Text(
                            text = "Nearby Active (${peers.size})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(peers) { peer ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.bouncyClickable(scaleDown = 0.92f) {
                                        onOpenChat(peer.nodeId, peer.nickname)
                                    }
                                ) {
                                    Box(modifier = Modifier.size(56.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .shadow(10.dp, CircleShape, spotColor = Color(0x6622C55E))
                                                .clip(CircleShape)
                                                .background(
                                                    when (peer.nickname.take(1).uppercase()) {
                                                        "A" -> Brush.linearGradient(listOf(Color(0xFF0F766E), Color(0xFF14B8A6)))
                                                        "B" -> Brush.linearGradient(listOf(Color(0xFF9A3412), Color(0xFFF97316)))
                                                        "C" -> Brush.linearGradient(listOf(Color(0xFF6B21A8), Color(0xFFA855F7)))
                                                        else -> Brush.linearGradient(listOf(Color(0xFF065F46), Color(0xFF10B981)))
                                                    }
                                                )
                                                .border(2.dp, Color(0xFF22C55E), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = peer.nickname.take(1).uppercase(),
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }

                                        // Online indicator dot
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .align(Alignment.BottomEnd)
                                                .shadow(4.dp, CircleShape, spotColor = Color(0xFF22C55E))
                                                .clip(CircleShape)
                                                .background(Color(0xFF22C55E))
                                                .border(2.dp, Color(0xFF121216), CircleShape)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = peer.nickname,
                                        fontSize = 11.sp,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 64.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }

            // 3. Section Title: "Messages"
            item {
                Text(
                    text = "Messages",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Pinned Broadcast Chat (Always available)
            item {
                val broadcastLastMsg = richMessages.lastOrNull { it.recipientNodeId == "mesh-broadcast" }
                val formattedTime = broadcastLastMsg?.let { formatTime(it.timestamp) } ?: "Mesh"

                ConversationRow(
                    name = "Mesh Broadcast",
                    preview = broadcastLastMsg?.text ?: "Send message to everyone nearby offline",
                    time = formattedTime,
                    unreadCount = if (broadcastLastMsg != null) 1 else 0,
                    ringColor = Color(0xFF3B82F6),
                    avatarInitial = "M",
                    isDark = isDark,
                    onClick = { onOpenChat("mesh-broadcast", "Mesh Broadcast") }
                )

                Spacer(modifier = Modifier.height(8.dp))
            }

            // 4. Discovered Peer Chats List
            if (filteredPeers.isEmpty()) {
                item {
                    if (isScanning) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                color = Color(0xCC161620),
                                shape = CircleShape,
                                border = BorderStroke(1.dp, GlassHighlightGradient),
                                modifier = Modifier.size(68.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Radar,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "Searching for Nearby Friends...",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Scanning offline BLE & Wi-Fi mesh channels...",
                                fontSize = 12.sp,
                                color = Color(0xFF8E8E98),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        Surface(
                            color = Color(0x33141520),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, Color(0x18FFFFFF)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x2238BDF8)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ChatBubble,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = "No Direct Chats Yet",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Discovered nearby devices will appear here",
                                        fontSize = 12.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                items(filteredPeers) { peer ->
                    val lastMsg = richMessages.lastOrNull {
                        it.recipientNodeId == peer.nodeId || it.senderNodeId == peer.nodeId
                    }
                    val previewText = lastMsg?.text ?: "Connected via Bluetooth mesh"
                    val timeText = lastMsg?.let { formatTime(it.timestamp) } ?: "Nearby"

                    ConversationRow(
                        name = peer.nickname,
                        preview = previewText,
                        time = timeText,
                        unreadCount = if (lastMsg != null && !lastMsg.isFromMe) 1 else 0,
                        ringColor = when (peer.nickname.take(1).uppercase()) {
                            "A" -> Color(0xFFE11D48)
                            "B" -> Color(0xFFF59E0B)
                            "C" -> Color(0xFF10B981)
                            else -> Color(0xFF3B82F6)
                        },
                        avatarInitial = peer.nickname.take(1).uppercase(),
                        isDark = isDark,
                        isNearby = true,
                        onClick = { onOpenChat(peer.nodeId, peer.nickname) }
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(
    name: String,
    preview: String,
    time: String,
    unreadCount: Int,
    ringColor: Color,
    avatarInitial: String,
    isDark: Boolean = true,
    isNearby: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        color = if (isDark) Color(0x8016161E) else Color(0xFFFFFFFF),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            1.dp,
            if (isDark) Brush.verticalGradient(listOf(Color(0x22FFFFFF), Color(0x06FFFFFF)))
            else Brush.verticalGradient(listOf(Color(0xFFCBD5E1), Color(0xFFE2E8F0)))
        ),
        shadowElevation = if (isDark) 4.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable(scaleDown = 0.96f) { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                // Avatar with glowing colored ring border (Matching Picture 1 Right)
                Box(modifier = Modifier.size(54.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .shadow(8.dp, CircleShape, spotColor = ringColor)
                            .clip(CircleShape)
                            .border(2.5.dp, ringColor, CircleShape)
                            .padding(3.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(listOf(Color(0xFF1E293B), Color(0xFF0F172A)))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = avatarInitial,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    if (isNearby) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .align(Alignment.BottomEnd)
                                .shadow(4.dp, CircleShape, spotColor = Color(0xFF22C55E))
                                .clip(CircleShape)
                                .background(Color(0xFF22C55E))
                                .border(2.dp, if (isDark) Color(0xFF16161E) else Color(0xFFFFFFFF), CircleShape)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color.White else Color(0xFF0F172A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isNearby) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = Color(0x2222C55E),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0x4422C55E))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF22C55E))
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Nearby",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF4ADE80)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = preview,
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = time,
                    fontSize = 12.sp,
                    color = Color(0xFF8E8E98)
                )

                Spacer(modifier = Modifier.height(6.dp))

                if (unreadCount > 0) {
                    // Blue Pill Badge with Glow (Matching Picture 1 Right)
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .shadow(8.dp, CircleShape, spotColor = Color(0xFF2563EB))
                            .clip(CircleShape)
                            .background(Color(0xFF2563EB)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$unreadCount",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000} mins"
        else -> SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(timestamp))
    }
}

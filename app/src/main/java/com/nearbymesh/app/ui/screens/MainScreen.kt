package com.nearbymesh.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nearbymesh.app.core.MeshCoordinator
import com.nearbymesh.app.core.MeshPeer
import com.nearbymesh.app.core.models.MessageContentType
import com.nearbymesh.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 3 Tabs for floating glass bottom navigation bar:
 * 1. Compass (Home / Friend's Contact)
 * 2. Chat Bubble (Messages)
 * 3. Person (Profile / Settings)
 */
enum class NavigationTab(val label: String, val icon: ImageVector) {
    COMPASS("Home", Icons.Default.Explore),
    CHATS("Messages", Icons.Default.ChatBubble),
    PROFILE("Profile", Icons.Default.Person)
}

enum class ActiveScreen {
    SPLASH,
    MAIN_TABS,
    CHAT_DETAIL,
    RECEIVE_FILE
}

@Composable
fun MainScreen(
    coordinator: MeshCoordinator,
    currentTheme: AppThemeMode,
    initialChatPeerId: String? = null,
    initialChatPeerName: String = "",
    onRequestPermissions: () -> Unit = {},
    onToggleTheme: () -> Unit
) {
    var activeScreen by remember {
        mutableStateOf(if (initialChatPeerId != null) ActiveScreen.CHAT_DETAIL else ActiveScreen.MAIN_TABS)
    }

    var selectedPeerForProfile by remember { mutableStateOf<MeshPeer?>(null) }
    var activeChatPeerId by remember { mutableStateOf(initialChatPeerId) }
    var activeChatPeerName by remember { mutableStateOf(initialChatPeerName) }

    LaunchedEffect(initialChatPeerId) {
        if (initialChatPeerId != null) {
            activeChatPeerId = initialChatPeerId
            activeChatPeerName = initialChatPeerName
            activeScreen = ActiveScreen.CHAT_DETAIL
        }
    }

    val pagerState = rememberPagerState(initialPage = 0) { NavigationTab.values().size }
    val coroutineScope = rememberCoroutineScope()

    // ========================================================
    // BACK NAVIGATION HANDLERS (Fixes app closing unexpectedly)
    // ========================================================
    // 1. In Chat Detail Screen: Back returns cleanly to Messages (Chats) Tab!
    BackHandler(enabled = activeScreen == ActiveScreen.CHAT_DETAIL) {
        activeScreen = ActiveScreen.MAIN_TABS
        coroutineScope.launch {
            pagerState.scrollToPage(NavigationTab.CHATS.ordinal)
        }
    }

    // 2. In Receive File Screen: Back returns to Main Tabs
    BackHandler(enabled = activeScreen == ActiveScreen.RECEIVE_FILE) {
        activeScreen = ActiveScreen.MAIN_TABS
    }

    // 3. On Main Tabs: Back returns from Settings/Messages to Compass (Home Tab) before exiting
    BackHandler(enabled = activeScreen == ActiveScreen.MAIN_TABS && pagerState.currentPage != 0) {
        coroutineScope.launch {
            pagerState.animateScrollToPage(
                page = NavigationTab.COMPASS.ordinal,
                animationSpec = tween(
                    durationMillis = 320,
                    easing = FastOutSlowInEasing
                )
            )
        }
    }

    when (activeScreen) {
        ActiveScreen.SPLASH -> {
            SplashScreen(
                onGetStarted = { activeScreen = ActiveScreen.MAIN_TABS }
            )
        }

        ActiveScreen.CHAT_DETAIL -> {
            ChatDetailScreen(
                coordinator = coordinator,
                peerId = activeChatPeerId ?: "mesh-broadcast",
                peerName = activeChatPeerName.ifEmpty { "NearbyMesh Chat" },
                onBack = {
                    activeScreen = ActiveScreen.MAIN_TABS
                    coroutineScope.launch {
                        pagerState.scrollToPage(NavigationTab.CHATS.ordinal)
                    }
                }
            )
        }

        ActiveScreen.RECEIVE_FILE -> {
            ReceiveFileScreen(
                onBack = { activeScreen = ActiveScreen.MAIN_TABS }
            )
        }

        ActiveScreen.MAIN_TABS -> {
            val isDark = when (currentTheme) {
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
                AppThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isDark) Color(0xFF0A0E17) else Color(0xFFF1F5F9))
            ) {
                // ========================================================
                // HORIZONTAL PAGER (Smooth slide/swipe between tabs!)
                // Compass (0) <---> Messages (1) <---> Settings (2)
                // ========================================================
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = true
                ) { page ->
                    when (page) {
                        0 -> HomeScreen(
                            coordinator = coordinator,
                            onOpenChat = { peerId ->
                                activeChatPeerId = peerId
                                activeChatPeerName = coordinator.peers.value[peerId]?.nickname ?: "Device $peerId"
                                activeScreen = ActiveScreen.CHAT_DETAIL
                            },
                            onOpenChatsTab = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(
                                        page = NavigationTab.CHATS.ordinal,
                                        animationSpec = tween(
                                            durationMillis = 320,
                                            easing = FastOutSlowInEasing
                                        )
                                    )
                                }
                            },
                            onOpenCreateGroup = {
                                activeChatPeerId = "mesh-broadcast"
                                activeChatPeerName = "Mesh Broadcast"
                                activeScreen = ActiveScreen.CHAT_DETAIL
                            },
                            onOpenReceive = {
                                activeScreen = ActiveScreen.RECEIVE_FILE
                            },
                            onOpenDeviceProfile = { peer ->
                                selectedPeerForProfile = peer
                            },
                            onOpenSettings = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(
                                        page = NavigationTab.PROFILE.ordinal,
                                        animationSpec = tween(
                                            durationMillis = 320,
                                            easing = FastOutSlowInEasing
                                        )
                                    )
                                }
                            },
                            onRequestPermissions = onRequestPermissions
                        )

                        1 -> ChatsListScreen(
                            coordinator = coordinator,
                            onOpenChat = { peerId, peerName ->
                                activeChatPeerId = peerId
                                activeChatPeerName = peerName
                                activeScreen = ActiveScreen.CHAT_DETAIL
                            },
                            onTriggerScan = {
                                coordinator.triggerScan()
                            }
                        )

                        2 -> SettingsScreen(
                            coordinator = coordinator,
                            currentTheme = currentTheme,
                            onBack = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(
                                        page = NavigationTab.COMPASS.ordinal,
                                        animationSpec = tween(
                                            durationMillis = 320,
                                            easing = FastOutSlowInEasing
                                        )
                                    )
                                }
                            },
                            onToggleTheme = onToggleTheme,
                            onOpenBroadcastGroup = {
                                activeChatPeerId = "mesh-broadcast"
                                activeChatPeerName = "Mesh Broadcast"
                                activeScreen = ActiveScreen.CHAT_DETAIL
                            }
                        )
                    }
                }

                // ========================================================
                // FLOATING CAPSULE PILL BAR with Fluid Hardware Acceleration
                // ========================================================
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .shadow(
                                if (isDark) 28.dp else 12.dp,
                                RoundedCornerShape(38.dp),
                                spotColor = if (isDark) Color(0x99000000) else Color(0x33000000)
                            )
                            .clip(RoundedCornerShape(38.dp))
                            .background(if (isDark) Color(0xE615151B) else Color(0xF8FFFFFF))
                            .border(
                                1.dp,
                                if (isDark) {
                                    Brush.verticalGradient(listOf(Color(0x4DFFFFFF), Color(0x0FFFFFFF)))
                                } else {
                                    Brush.verticalGradient(listOf(Color(0xFFCBD5E1), Color(0xFFE2E8F0)))
                                },
                                RoundedCornerShape(38.dp)
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            NavigationTab.values().forEach { tab ->
                                val isSelected = pagerState.currentPage == tab.ordinal

                                FloatingNavPillItem(
                                    tab = tab,
                                    isSelected = isSelected,
                                    isDark = isDark,
                                    onClick = {
                                        if (pagerState.currentPage != tab.ordinal) {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(
                                                    page = tab.ordinal,
                                                    animationSpec = tween(
                                                        durationMillis = 320,
                                                        easing = FastOutSlowInEasing
                                                    )
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // Modal Device Profile Sheet (When clicking peer info)
                selectedPeerForProfile?.let { peer ->
                    DeviceProfileSheet(
                        peer = peer,
                        onDismiss = { selectedPeerForProfile = null },
                        onChatClick = {
                            activeChatPeerId = peer.nodeId
                            activeChatPeerName = peer.nickname
                            activeScreen = ActiveScreen.CHAT_DETAIL
                        },
                        onSendFilesClick = {
                            activeChatPeerId = peer.nodeId
                            activeChatPeerName = peer.nickname
                            activeScreen = ActiveScreen.CHAT_DETAIL
                        },
                        onConnectClick = {
                            selectedPeerForProfile = null
                            activeChatPeerId = peer.nodeId
                            activeChatPeerName = peer.nickname
                            activeScreen = ActiveScreen.CHAT_DETAIL
                        }
                    )
                }
            }
        }
    }

    // Global Offline Voice Call Overlay (Accessible across any screen)
    CallScreenOverlay(coordinator = coordinator)

    // Global In-App Pop-up Quick Reply Banner
    InAppMessageReplyBanner(
        coordinator = coordinator,
        onOpenChat = { peerId, peerName ->
            activeChatPeerId = peerId
            activeChatPeerName = peerName
            activeScreen = ActiveScreen.CHAT_DETAIL
        }
    )
}

/**
 * Floating frosted glass in-app pop-up notification banner.
 * Allows quick direct reply without leaving the current screen or opening the full chat!
 */
@Composable
private fun InAppMessageReplyBanner(
    coordinator: MeshCoordinator,
    onOpenChat: (peerId: String, peerName: String) -> Unit
) {
    val alert by coordinator.incomingAlert.collectAsState()
    var replyText by remember { mutableStateOf("") }

    // Auto-dismiss after 8 seconds if user is not actively typing
    LaunchedEffect(alert) {
        if (alert != null) {
            replyText = ""
            delay(8000L)
            if (replyText.isEmpty()) {
                coordinator.dismissAlert()
            }
        }
    }

    AnimatedVisibility(
        visible = alert != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        alert?.let { item ->
            Surface(
                color = Color(0xF2181822),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, Brush.verticalGradient(listOf(Color(0x5522C55E), Color(0x15FFFFFF)))),
                shadowElevation = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Top Row: Avatar + Sender + Message + Close
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Color(0xFF059669), Color(0xFF10B981)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = item.senderName.take(1).uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = item.senderName,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF22C55E))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Nearby",
                                    color = Color(0xFF22C55E),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = item.text,
                                color = Color(0xFFCBD5E1),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }

                        // Open Chat Pill
                        Surface(
                            color = Color(0x3322C55E),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(0.5.dp, Color(0x6622C55E)),
                            modifier = Modifier.clickable {
                                onOpenChat(item.senderNodeId, item.senderName)
                                coordinator.dismissAlert()
                            }
                        ) {
                            Text(
                                text = "Open",
                                color = Color(0xFF22C55E),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Dismiss button
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .clickable { coordinator.dismissAlert() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Bottom Row: Inline Quick Reply Field + Send Button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            color = Color(0x33FFFFFF),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(0.5.dp, Color(0x22FFFFFF)),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp)
                            ) {
                                androidx.compose.foundation.text.BasicTextField(
                                    value = replyText,
                                    onValueChange = { replyText = it },
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        color = Color.White,
                                        fontSize = 13.sp
                                    ),
                                    singleLine = true,
                                    decorationBox = { innerTextField ->
                                        if (replyText.isEmpty()) {
                                            Text(
                                                text = "Quick reply to ${item.senderName}...",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 13.sp
                                            )
                                        }
                                        innerTextField()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    if (replyText.isNotBlank()) Color(0xFF22C55E) else Color(0x22FFFFFF)
                                )
                                .clickable(enabled = replyText.isNotBlank()) {
                                    val textToSend = replyText.trim()
                                    replyText = ""
                                    coordinator.sendRichMessage(
                                        recipientNodeId = item.senderNodeId,
                                        text = textToSend,
                                        contentType = MessageContentType.TEXT
                                    )
                                    coordinator.dismissAlert()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send Reply",
                                tint = if (replyText.isNotBlank()) Color.Black else Color(0xFF64748B),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Ultra-smooth bouncing pill navigation item with fluid width and zero-jank GPU spring
 */
@Composable
private fun FloatingNavPillItem(
    tab: NavigationTab,
    isSelected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Smooth tactile bounce scale on GPU render thread
    val pressScaleState = animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = 600f
        ),
        label = "PillPressScale"
    )

    // Smooth width glide: 48dp (circle) <---> 124dp (capsule pill)
    val pillWidth by animateDpAsState(
        targetValue = if (isSelected) 124.dp else 48.dp,
        animationSpec = tween(
            durationMillis = 240,
            easing = FastOutSlowInEasing
        ),
        label = "PillWidth"
    )

    val textAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isSelected) 160 else 80,
            delayMillis = if (isSelected) 60 else 0
        ),
        label = "PillTextAlpha"
    )

    Box(
        modifier = Modifier
            .width(pillWidth)
            .height(48.dp)
            .graphicsLayer {
                scaleX = pressScaleState.value
                scaleY = pressScaleState.value
            }
            .then(
                if (isSelected) {
                    if (isDark) {
                        Modifier
                            .shadow(10.dp, RoundedCornerShape(24.dp), spotColor = Color(0x66FFFFFF))
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFFFFFFFF), Color(0xFFF1F1F6))
                                )
                            )
                            .border(1.dp, Color(0x99FFFFFF), RoundedCornerShape(24.dp))
                    } else {
                        Modifier
                            .shadow(6.dp, RoundedCornerShape(24.dp), spotColor = Color(0x400F172A))
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF0F172A))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(24.dp))
                    }
                } else {
                    if (isDark) {
                        Modifier
                            .clip(CircleShape)
                            .background(Color(0x22FFFFFF))
                            .border(
                                1.dp,
                                Brush.verticalGradient(listOf(Color(0x28FFFFFF), Color(0x08FFFFFF))),
                                CircleShape
                            )
                    } else {
                        Modifier
                            .clip(CircleShape)
                            .background(Color(0x0D000000))
                            .border(
                                1.dp,
                                Color(0x18000000),
                                CircleShape
                            )
                    }
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        val activeTextColor = if (isDark) Color.Black else Color.White
        val inactiveIconColor = if (isDark) Color(0xFFB0B0B8) else Color(0xFF64748B)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.label,
                tint = if (isSelected) activeTextColor else inactiveIconColor,
                modifier = Modifier.size(19.dp)
            )

            if (isSelected || textAlpha > 0.05f) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = tab.label,
                    color = activeTextColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.graphicsLayer {
                        alpha = textAlpha
                    }
                )
            }
        }
    }
}


package com.nearbymesh.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nearbymesh.app.core.MeshCoordinator
import com.nearbymesh.app.core.MeshPeer
import com.nearbymesh.app.core.models.UserProfile
import com.nearbymesh.app.service.NotificationHelper
import com.nearbymesh.app.ui.theme.AppThemeMode
import com.nearbymesh.app.ui.theme.bouncyClickable
import java.io.File

/**
 * Settings Sub-screens for Category-based Modular Architecture
 */
enum class SettingsSubScreen(val title: String) {
    MAIN("Settings"),
    PROFILE_EDIT("Edit Profile"),
    NEARBY_DISCOVERY("Nearby & Discovery"),
    MESH_NETWORK("Mesh Network"),
    CONNECTED_DEVICES("Connected Devices"),
    RANGE_CONNECTIVITY("Range & Connectivity"),
    MESSAGES("Chat Settings"),
    NOTIFICATIONS("Notifications"),
    GROUPS("Groups"),
    PRIVACY("Privacy"),
    ENCRYPTION("Encryption"),
    STORAGE("Storage"),
    BATTERY_DATA("Data & Battery"),
    APPEARANCE("Theme & Colors"),
    FONT_LAYOUT("Font & Layout"),
    EXPERIMENTAL("Experimental"),
    ABOUT("About NearbyMesh")
}

/**
 * Theme color palette helper for adaptive Light/Dark styling
 */
class SettingsThemeColors(val isDark: Boolean) {
    val bg: Color = if (isDark) Color(0xFF0A0E17) else Color(0xFFF1F5F9)
    val topBarBg: Color = if (isDark) Color(0xEE12131D) else Color(0xF8FFFFFF)
    val topBarBorder: Color = if (isDark) Color(0x22FFFFFF) else Color(0xFFE2E8F0)
    val cardBg: Color = if (isDark) Color(0xFF111726) else Color(0xFFFFFFFF)
    val cardBorder: Brush = if (isDark) {
        Brush.verticalGradient(listOf(Color(0x3038BDF8), Color(0x0C38BDF8)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFE2E8F0), Color(0xFFCBD5E1)))
    }
    val textPrimary: Color = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val textSecondary: Color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val textMuted: Color = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)
    val divider: Color = if (isDark) Color(0x15FFFFFF) else Color(0xFFE2E8F0)
    val iconBoxBg: Color = if (isDark) Color(0x1A38BDF8) else Color(0x150284C7)
    val iconTint: Color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
    val radioSelected: Color = Color(0xFF22C55E)
    val buttonSubtleBg: Color = if (isDark) Color(0x1FFFFFFF) else Color(0xFFF1F5F9)
    val profileCardBg: Color = if (isDark) Color(0xFF0F1E36) else Color(0xFFFFFFFF)
}

@Composable
fun SettingsScreen(
    coordinator: MeshCoordinator,
    currentTheme: AppThemeMode,
    onBack: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenBroadcastGroup: () -> Unit = {}
) {
    val context = LocalContext.current
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (currentTheme) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> isSystemDark
    }
    val colors = remember(isDark) { SettingsThemeColors(isDark) }

    val userProfile by coordinator.userProfile.collectAsState()
    val peersMap by coordinator.peers.collectAsState()
    val peers = remember(peersMap) { peersMap.values.toList() }

    var activeSubScreen by remember { mutableStateOf(SettingsSubScreen.MAIN) }

    // Intercept Hardware Back when inside any Settings sub-screen
    BackHandler(enabled = activeSubScreen != SettingsSubScreen.MAIN) {
        activeSubScreen = SettingsSubScreen.MAIN
    }

    // Photo picker for profile picture
    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { pickedUri ->
            try {
                val avatarFile = File(context.filesDir, "user_profile_avatar.jpg")
                context.contentResolver.openInputStream(pickedUri)?.use { input ->
                    avatarFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                coordinator.updateProfileImage(avatarFile.absolutePath)
                Toast.makeText(context, "Profile photo updated", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                coordinator.updateProfileImage(pickedUri.toString())
            }
        }
    }

    val profileBitmap = remember(userProfile.profileImageUri) {
        userProfile.profileImageUri?.let { pathOrUri ->
            try {
                val file = File(pathOrUri)
                if (file.exists()) {
                    BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                } else {
                    val uri = Uri.parse(pathOrUri)
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
    ) {
        // Top App Bar matching screenshot
        SettingsTopBar(
            title = activeSubScreen.title,
            isRoot = activeSubScreen == SettingsSubScreen.MAIN,
            colors = colors,
            onBack = {
                if (activeSubScreen == SettingsSubScreen.MAIN) {
                    onBack()
                } else {
                    activeSubScreen = SettingsSubScreen.MAIN
                }
            }
        )

        // Animated Screen Switcher
        AnimatedContent(
            targetState = activeSubScreen,
            transitionSpec = {
                if (targetState == SettingsSubScreen.MAIN) {
                    slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                } else {
                    slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                }
            },
            label = "SettingsScreenTransition"
        ) { targetScreen ->
            when (targetScreen) {
                SettingsSubScreen.MAIN -> SettingsMainList(
                    coordinator = coordinator,
                    userProfile = userProfile,
                    profileBitmap = profileBitmap,
                    peersCount = peers.size,
                    currentTheme = currentTheme,
                    colors = colors,
                    onNavigate = { activeSubScreen = it },
                    onPickPhoto = { photoPickerLauncher.launch("image/*") }
                )
                SettingsSubScreen.PROFILE_EDIT -> ProfileEditSubScreen(
                    coordinator = coordinator,
                    userProfile = userProfile,
                    profileBitmap = profileBitmap,
                    colors = colors,
                    onPickPhoto = { photoPickerLauncher.launch("image/*") },
                    onDone = { activeSubScreen = SettingsSubScreen.MAIN }
                )
                SettingsSubScreen.NEARBY_DISCOVERY -> NearbyDiscoverySubScreen(coordinator, colors)
                SettingsSubScreen.MESH_NETWORK -> MeshNetworkSubScreen(coordinator, peers.size, colors)
                SettingsSubScreen.CONNECTED_DEVICES -> ConnectedDevicesSubScreen(coordinator, peers, colors)
                SettingsSubScreen.RANGE_CONNECTIVITY -> RangeConnectivitySubScreen(coordinator, colors)
                SettingsSubScreen.MESSAGES -> MessagesSettingsSubScreen(coordinator, colors)
                SettingsSubScreen.NOTIFICATIONS -> NotificationsSettingsSubScreen(coordinator, colors)
                SettingsSubScreen.GROUPS -> GroupsSettingsSubScreen(coordinator, colors, onOpenBroadcastGroup)
                SettingsSubScreen.PRIVACY -> PrivacySettingsSubScreen(coordinator, colors)
                SettingsSubScreen.ENCRYPTION -> EncryptionSettingsSubScreen(coordinator, colors)
                SettingsSubScreen.STORAGE -> StorageSettingsSubScreen(coordinator, colors)
                SettingsSubScreen.BATTERY_DATA -> BatteryDataSubScreen(coordinator, colors)
                SettingsSubScreen.APPEARANCE -> AppearanceSubScreen(coordinator, currentTheme, colors)
                SettingsSubScreen.FONT_LAYOUT -> FontLayoutSubScreen(coordinator, colors)
                SettingsSubScreen.EXPERIMENTAL -> ExperimentalSubScreen(coordinator, colors)
                SettingsSubScreen.ABOUT -> AboutSubScreen(colors)
            }
        }
    }
}

/**
 * Top App Bar with back button, large title, and subtitle (matching screenshot)
 */
@Composable
private fun SettingsTopBar(
    title: String,
    isRoot: Boolean,
    colors: SettingsThemeColors,
    onBack: () -> Unit
) {
    Surface(
        color = colors.topBarBg,
        border = BorderStroke(0.5.dp, colors.topBarBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.buttonSubtleBg)
                    .bouncyClickable(scaleDown = 0.85f) { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isRoot) "Settings" else title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                if (isRoot) {
                    Text(
                        text = "Customize your NearbyMesh experience",
                        fontSize = 12.5.sp,
                        color = colors.textSecondary
                    )
                }
            }
        }
    }
}

/**
 * Cute Fox Profile Avatar drawn with Canvas, or user's custom photo
 */
@Composable
fun FoxProfileAvatar(
    bitmap: androidx.compose.ui.graphics.ImageBitmap?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFFFB923C), Color(0xFFEA580C))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Avatar",
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        } else {
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(5.dp)
            ) {
                val w = size.width
                val h = size.height

                // White cheeks / muzzle mask
                val cheekPath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.15f, h * 0.52f)
                    quadraticBezierTo(w * 0.5f, h * 0.65f, w * 0.85f, h * 0.52f)
                    quadraticBezierTo(w * 0.95f, h * 0.78f, w * 0.5f, h * 0.92f)
                    quadraticBezierTo(w * 0.05f, h * 0.78f, w * 0.15f, h * 0.52f)
                    close()
                }
                drawPath(cheekPath, color = Color.White)

                // Dark tips on ears
                val leftEarTip = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.22f, h * 0.32f)
                    lineTo(w * 0.28f, h * 0.10f)
                    lineTo(w * 0.42f, h * 0.22f)
                    close()
                }
                drawPath(leftEarTip, color = Color(0xFF1E293B))

                val rightEarTip = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.78f, h * 0.32f)
                    lineTo(w * 0.72f, h * 0.10f)
                    lineTo(w * 0.58f, h * 0.22f)
                    close()
                }
                drawPath(rightEarTip, color = Color(0xFF1E293B))

                // Inner white ear triangles
                val leftEarInner = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.26f, h * 0.30f)
                    lineTo(w * 0.30f, h * 0.16f)
                    lineTo(w * 0.40f, h * 0.24f)
                    close()
                }
                drawPath(leftEarInner, color = Color(0xFFFFFBEB))

                val rightEarInner = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.74f, h * 0.30f)
                    lineTo(w * 0.70f, h * 0.16f)
                    lineTo(w * 0.60f, h * 0.24f)
                    close()
                }
                drawPath(rightEarInner, color = Color(0xFFFFFBEB))

                // Left Eye (Black with gleam)
                drawCircle(
                    color = Color(0xFF0F172A),
                    radius = w * 0.065f,
                    center = androidx.compose.ui.geometry.Offset(w * 0.36f, h * 0.54f)
                )
                drawCircle(
                    color = Color.White,
                    radius = w * 0.02f,
                    center = androidx.compose.ui.geometry.Offset(w * 0.345f, h * 0.525f)
                )

                // Right Eye (Black with gleam)
                drawCircle(
                    color = Color(0xFF0F172A),
                    radius = w * 0.065f,
                    center = androidx.compose.ui.geometry.Offset(w * 0.64f, h * 0.54f)
                )
                drawCircle(
                    color = Color.White,
                    radius = w * 0.02f,
                    center = androidx.compose.ui.geometry.Offset(w * 0.625f, h * 0.525f)
                )

                // Black nose oval
                drawOval(
                    color = Color(0xFF0F172A),
                    topLeft = androidx.compose.ui.geometry.Offset(w * 0.44f, h * 0.68f),
                    size = androidx.compose.ui.geometry.Size(w * 0.12f, h * 0.08f)
                )
            }
        }
    }
}

/**
 * Category Section Header with Colored Icon, Title, and Right Subtitle
 */
@Composable
private fun CategorySectionHeader(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    colors: SettingsThemeColors
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = subtitle,
            fontSize = 11.sp,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Modern Grid Card with Icon Container, Chevron >, Bold Title, and Subtitle
 */
@Composable
private fun SettingsGridCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    badgeText: String? = null,
    colors: SettingsThemeColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = colors.cardBg,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (colors.isDark) Color(0x1F38BDF8) else Color(0xFFE2E8F0)),
        shadowElevation = if (colors.isDark) 4.dp else 1.dp,
        modifier = modifier
            .fillMaxHeight()
            .bouncyClickable(scaleDown = 0.95f, onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            // Top Row: Icon circle on left, Chevron > on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    if (badgeText != null) {
                        Text(
                            text = badgeText,
                            color = iconTint,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = subtitle,
                fontSize = 10.5.sp,
                color = colors.textSecondary,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Main Settings Grid List (Matching the user screenshot 1:1)
 */
@Composable
private fun SettingsMainList(
    coordinator: MeshCoordinator,
    userProfile: UserProfile,
    profileBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    peersCount: Int,
    currentTheme: AppThemeMode,
    colors: SettingsThemeColors,
    onNavigate: (SettingsSubScreen) -> Unit,
    onPickPhoto: () -> Unit
) {
    val isNearbyVisible by coordinator.isNearbyVisibilityEnabled.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // ========================================================
        // TOP PROFILE CARD (Fox avatar, camera badge, name, status)
        // ========================================================
        item {
            Surface(
                color = colors.profileCardBg,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(
                    1.dp,
                    if (colors.isDark) {
                        Brush.horizontalGradient(listOf(Color(0x3338BDF8), Color(0x1038BDF8), Color(0x221E293B)))
                    } else {
                        Brush.horizontalGradient(listOf(Color(0xFFCBD5E1), Color(0xFFE2E8F0)))
                    }
                ),
                shadowElevation = if (colors.isDark) 6.dp else 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .bouncyClickable(scaleDown = 0.97f) { onNavigate(SettingsSubScreen.PROFILE_EDIT) }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (colors.isDark) {
                                Brush.linearGradient(
                                    listOf(Color(0xFF0F2042), Color(0xFF132238), Color(0xFF0B1424))
                                )
                            } else {
                                Brush.linearGradient(
                                    listOf(Color(0xFFE2E8F0), Color(0xFFFFFFFF))
                                )
                            }
                        )
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Fox Avatar + Camera Badge
                        Box(modifier = Modifier.size(62.dp)) {
                            FoxProfileAvatar(
                                bitmap = profileBitmap,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Camera Edit Badge
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .align(Alignment.BottomEnd)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0F172A))
                                    .border(1.5.dp, Color(0xFF334155), CircleShape)
                                    .clickable { onPickPhoto() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Edit photo",
                                    tint = Color.White,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = userProfile.displayName,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "@${userProfile.displayName.lowercase().replace(" ", "_")}_device",
                                fontSize = 13.sp,
                                color = colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .shadow(4.dp, CircleShape, spotColor = Color(0xFF22C55E))
                                        .clip(CircleShape)
                                        .background(if (isNearbyVisible) Color(0xFF22C55E) else Color(0xFFEF4444))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isNearbyVisible) "Available nearby" else "Visibility hidden",
                                    fontSize = 12.sp,
                                    color = if (isNearbyVisible) Color(0xFF22C55E) else Color(0xFFEF4444),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = "Edit Profile",
                            tint = colors.textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // ========================================================
        // SECTION 1: CONNECTION (3 Cards)
        // ========================================================
        item {
            CategorySectionHeader(
                icon = Icons.Default.Wifi,
                iconColor = Color(0xFF00E5FF),
                title = "Connection",
                subtitle = "Find and connect with nearby devices",
                colors = colors
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingsGridCard(
                    title = "Nearby & Discovery",
                    subtitle = "Who can find you, scan settings",
                    icon = Icons.Default.WifiTethering,
                    iconTint = Color(0xFF38BDF8),
                    iconBg = Color(0x2238BDF8),
                    colors = colors,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(SettingsSubScreen.NEARBY_DISCOVERY) }
                )
                SettingsGridCard(
                    title = "Mesh Network",
                    subtitle = "Relay, hops, network map",
                    icon = Icons.Default.Hub,
                    iconTint = Color(0xFF10B981),
                    iconBg = Color(0x2210B981),
                    colors = colors,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(SettingsSubScreen.MESH_NETWORK) }
                )
                SettingsGridCard(
                    title = "Connected Devices",
                    subtitle = "Manage nearby devices",
                    icon = Icons.Default.Smartphone,
                    iconTint = Color(0xFF38BDF8),
                    iconBg = Color(0x2238BDF8),
                    colors = colors,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(SettingsSubScreen.CONNECTED_DEVICES) }
                )
            }
        }

        // ========================================================
        // SECTION 2: MESSAGING (3 Cards)
        // ========================================================
        item {
            CategorySectionHeader(
                icon = Icons.Default.ChatBubble,
                iconColor = Color(0xFF3B82F6),
                title = "Messaging",
                subtitle = "Control your chat experience",
                colors = colors
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingsGridCard(
                    title = "Chat Settings",
                    subtitle = "Message, media, chat options",
                    icon = Icons.Default.ChatBubbleOutline,
                    iconTint = Color(0xFFF43F5E),
                    iconBg = Color(0x22F43F5E),
                    colors = colors,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(SettingsSubScreen.MESSAGES) }
                )
                SettingsGridCard(
                    title = "Groups",
                    subtitle = "Create & manage groups",
                    icon = Icons.Default.Groups,
                    iconTint = Color(0xFFF59E0B),
                    iconBg = Color(0x22F59E0B),
                    colors = colors,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(SettingsSubScreen.GROUPS) }
                )
                SettingsGridCard(
                    title = "Notifications",
                    subtitle = "Alerts, sounds, badges",
                    icon = Icons.Default.Notifications,
                    iconTint = Color(0xFF818CF8),
                    iconBg = Color(0x22818CF8),
                    colors = colors,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(SettingsSubScreen.NOTIFICATIONS) }
                )
            }
        }

        // ========================================================
        // SECTION 3: PRIVACY & SECURITY (2 Cards)
        // ========================================================
        item {
            CategorySectionHeader(
                icon = Icons.Default.Shield,
                iconColor = Color(0xFF10B981),
                title = "Privacy & Security",
                subtitle = "Keep your conversations safe",
                colors = colors
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SettingsGridCard(
                    title = "Privacy",
                    subtitle = "Visibility, blocking, read receipts",
                    icon = Icons.Default.Lock,
                    iconTint = Color(0xFF10B981),
                    iconBg = Color(0x2210B981),
                    colors = colors,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(SettingsSubScreen.PRIVACY) }
                )
                SettingsGridCard(
                    title = "Encryption",
                    subtitle = "End-to-end security for your messages",
                    icon = Icons.Default.VerifiedUser,
                    iconTint = Color(0xFF0284C7),
                    iconBg = Color(0x220284C7),
                    colors = colors,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(SettingsSubScreen.ENCRYPTION) }
                )
            }
        }

        // ========================================================
        // SECTION 4: STORAGE & DATA (2 Cards)
        // ========================================================
        item {
            CategorySectionHeader(
                icon = Icons.Default.Storage,
                iconColor = Color(0xFF38BDF8),
                title = "Storage & Data",
                subtitle = "Manage files and app usage",
                colors = colors
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SettingsGridCard(
                    title = "Storage",
                    subtitle = "Received files, cache, manage space",
                    icon = Icons.Default.Folder,
                    iconTint = Color(0xFFFBBF24),
                    iconBg = Color(0x22FBBF24),
                    colors = colors,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(SettingsSubScreen.STORAGE) }
                )
                SettingsGridCard(
                    title = "Data & Battery",
                    subtitle = "Optimize for better performance",
                    icon = Icons.Default.SwapVert,
                    iconTint = Color(0xFFA855F7),
                    iconBg = Color(0x22A855F7),
                    colors = colors,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(SettingsSubScreen.BATTERY_DATA) }
                )
            }
        }

        // ========================================================
        // SECTION 5: APPEARANCE (2 Cards)
        // ========================================================
        item {
            CategorySectionHeader(
                icon = Icons.Default.Palette,
                iconColor = Color(0xFFA855F7),
                title = "Appearance",
                subtitle = "Make the app look the way you like",
                colors = colors
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SettingsGridCard(
                    title = "Theme & Colors",
                    subtitle = "Dark mode, accent, app style",
                    icon = Icons.Default.ColorLens,
                    iconTint = Color(0xFFEC4899),
                    iconBg = Color(0x22EC4899),
                    colors = colors,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(SettingsSubScreen.APPEARANCE) }
                )
                SettingsGridCard(
                    title = "Font & Layout",
                    subtitle = "Text size, compact mode, animations",
                    icon = Icons.Default.TextFields,
                    badgeText = "Aa",
                    iconTint = Color(0xFF06B6D4),
                    iconBg = Color(0x2206B6D4),
                    colors = colors,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(SettingsSubScreen.FONT_LAYOUT) }
                )
            }
        }

        // ========================================================
        // SECTION 6: ADVANCED (2 Cards)
        // ========================================================
        item {
            CategorySectionHeader(
                icon = Icons.Default.Settings,
                iconColor = Color(0xFF8B5CF6),
                title = "Advanced",
                subtitle = "Extra features and app information",
                colors = colors
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SettingsGridCard(
                    title = "Experimental",
                    subtitle = "Advanced mesh options (for experienced users)",
                    icon = Icons.Default.Science,
                    iconTint = Color(0xFF8B5CF6),
                    iconBg = Color(0x228B5CF6),
                    colors = colors,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(SettingsSubScreen.EXPERIMENTAL) }
                )
                SettingsGridCard(
                    title = "About NearbyMesh",
                    subtitle = "App info, version, licenses",
                    icon = Icons.Default.Info,
                    iconTint = Color(0xFF94A3B8),
                    iconBg = Color(0x2294A3B8),
                    colors = colors,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(SettingsSubScreen.ABOUT) }
                )
            }
        }

        // Space at the bottom so the floating pill bar never overlaps cards
        item {
            Spacer(modifier = Modifier.height(96.dp))
        }
    }
}

// ========================================================
// SUB-SCREEN 1: 📡 NEARBY & DISCOVERY
// ========================================================
@Composable
private fun NearbyDiscoverySubScreen(
    coordinator: MeshCoordinator,
    colors: SettingsThemeColors
) {
    val context = LocalContext.current
    val nearbyVisibility by coordinator.isNearbyVisibilityEnabled.collectAsState()
    val bleDiscovery by coordinator.isBleDiscoveryEnabled.collectAsState()
    val wifiDirectDiscovery by coordinator.isWifiDirectEnabled.collectAsState()
    val isScanning by coordinator.isScanning.collectAsState()

    var showDistance by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            SubSectionCard(title = "Visibility", colors = colors) {
                SettingSwitchRow(
                    title = "Nearby visibility",
                    subtitle = "Allow nearby phones to discover this device via BLE beacon",
                    checked = nearbyVisibility,
                    colors = colors,
                    onCheckedChange = { coordinator.setNearbyVisibility(it) }
                )
            }
        }

        item {
            SubSectionCard(title = "Discovery Radios", colors = colors) {
                SettingSwitchRow(
                    title = "Bluetooth Low Energy (BLE)",
                    subtitle = "Zero-pairing 0xFE60 mesh discovery service",
                    checked = bleDiscovery,
                    colors = colors,
                    onCheckedChange = { coordinator.setBleDiscovery(it) }
                )
                HorizontalDivider(color = colors.divider, thickness = 0.6.dp)
                SettingSwitchRow(
                    title = "Wi-Fi Direct (P2P)",
                    subtitle = "High-speed socket peer discovery",
                    checked = wifiDirectDiscovery,
                    colors = colors,
                    onCheckedChange = { coordinator.setWifiDirectEnabled(it) }
                )
            }
        }

        item {
            SubSectionCard(title = "Manual Discovery Scan", colors = colors) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = "Trigger an immediate active scan across BLE and Wi-Fi Direct radios.",
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            coordinator.triggerScan()
                            Toast.makeText(context, "Scanning for nearby peers...", Toast.LENGTH_SHORT).show()
                        },
                        enabled = !isScanning,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Text(
                            text = if (isScanning) "Scanning nearby..." else "Trigger Active Mesh Scan",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        item {
            SubSectionCard(title = "Peer Metadata", colors = colors) {
                SettingSwitchRow(
                    title = "Show distance",
                    subtitle = "Display estimated distance based on RSSI path-loss model",
                    checked = showDistance,
                    colors = colors,
                    onCheckedChange = { showDistance = it }
                )
            }
        }
    }
}

// ========================================================
// SUB-SCREEN 2: 🔗 MESH NETWORK
// ========================================================
@Composable
private fun MeshNetworkSubScreen(
    coordinator: MeshCoordinator,
    peerCount: Int,
    colors: SettingsThemeColors
) {
    val meshRelayEnabled by coordinator.isMeshRelayEnabled.collectAsState()
    val maxHops by coordinator.maxRelayHops.collectAsState()
    val stats by coordinator.stats.collectAsState()

    var batteryProtection by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            SubSectionCard(title = "Mesh Routing Engine", colors = colors) {
                SettingSwitchRow(
                    title = "Mesh Relay Mode",
                    subtitle = "Forward encrypted packets for peers beyond direct range (Store-and-Forward)",
                    checked = meshRelayEnabled,
                    colors = colors,
                    onCheckedChange = { coordinator.setMeshRelayEnabled(it) }
                )
            }
        }

        item {
            SubSectionCard(title = "Maximum Relay Hops (TTL)", colors = colors) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Maximum relay hops", color = colors.textPrimary, fontSize = 14.sp)
                        Text(text = "$maxHops hops", color = colors.iconTint, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Slider(
                        value = maxHops.toFloat(),
                        onValueChange = { coordinator.setMaxRelayHops(it.toInt()) },
                        valueRange = 1f..10f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = colors.iconTint,
                            activeTrackColor = colors.iconTint
                        )
                    )
                    Text(
                        text = "Controls packet Time-To-Live. Higher hops extend network reach across multiple phones.",
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                }
            }
        }

        item {
            SubSectionCard(title = "Battery Protection", colors = colors) {
                SettingSwitchRow(
                    title = "Battery protection",
                    subtitle = "Stop forwarding high-hop relays when phone battery drops below 20%",
                    checked = batteryProtection,
                    colors = colors,
                    onCheckedChange = { batteryProtection = it }
                )
            }
        }

        item {
            SubSectionCard(title = "Mesh Topology & Traffic", colors = colors) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Connected mesh nodes", color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(text = "Real zero-internet routing peers", color = colors.textSecondary, fontSize = 12.sp)
                    }
                    Surface(
                        color = Color(0x2238BDF8),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0x4438BDF8))
                    ) {
                        Text(
                            text = "$peerCount nodes",
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                HorizontalDivider(color = colors.divider, thickness = 0.6.dp)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Packets Routed / Duplicates", color = colors.textPrimary, fontSize = 13.sp)
                    Text(
                        text = "${stats.packetsRouted} / ${stats.duplicatesDropped}",
                        color = Color(0xFF22C55E),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

// ========================================================
// SUB-SCREEN 3: 📱 CONNECTED DEVICES
// ========================================================
@Composable
private fun ConnectedDevicesSubScreen(
    coordinator: MeshCoordinator,
    peers: List<MeshPeer>,
    colors: SettingsThemeColors
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Devices discovered directly via BLE or routed through multi-hop mesh.",
                fontSize = 13.sp,
                color = colors.textSecondary
            )
        }

        if (peers.isEmpty()) {
            item {
                Surface(
                    color = colors.cardBg,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, colors.cardBorder),
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(imageVector = Icons.Default.Radar, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = "No devices currently connected", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            text = "Nearby phones running NearbyMesh with Bluetooth enabled will automatically appear here.",
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        } else {
            items(peers) { peer ->
                Surface(
                    color = colors.cardBg,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, colors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(colors.iconBoxBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = peer.nickname.take(1).uppercase(),
                                color = colors.iconTint,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = peer.nickname, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF22C55E)))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Connected • ${peer.estimatedDistance}m • ${if (peer.hopsAway <= 1) "Direct BLE" else "${peer.hopsAway} Hops Relay"}",
                                    color = Color(0xFF22C55E),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Surface(
                            color = colors.buttonSubtleBg,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.bouncyClickable(scaleDown = 0.90f) {
                                coordinator.sendChatMessage(peer.nodeId, "Ping test from NearbyMesh")
                                Toast.makeText(context, "Sent ping to ${peer.nickname}", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text(
                                text = "Ping",
                                color = colors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ========================================================
// SUB-SCREEN 4: 📶 RANGE & CONNECTIVITY
// ========================================================
@Composable
private fun RangeConnectivitySubScreen(
    coordinator: MeshCoordinator,
    colors: SettingsThemeColors
) {
    var connectionMode by remember { mutableStateOf("Balanced") }
    var useBleLongRange by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            SubSectionCard(title = "Connection Mode", colors = colors) {
                Column {
                    listOf("Balanced", "Range Priority", "Speed Priority").forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { connectionMode = mode }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (connectionMode == mode),
                                onClick = { connectionMode = mode },
                                colors = RadioButtonDefaults.colors(selectedColor = colors.radioSelected)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(text = mode, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = when (mode) {
                                        "Balanced" -> "Equal balance between transmission distance and battery efficiency"
                                        "Range Priority" -> "Maximum BLE radio sensitivity and packet repetition"
                                        else -> "Optimized for fast large file transmissions via Wi-Fi Direct"
                                    },
                                    color = colors.textSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            SubSectionCard(title = "Hardware PHY Modulation", colors = colors) {
                SettingSwitchRow(
                    title = "Use BLE Long Range (Coded PHY)",
                    subtitle = "Enables Bluetooth 5.0 LE Coded PHY for extended range when hardware supports it",
                    checked = useBleLongRange,
                    colors = colors,
                    onCheckedChange = { useBleLongRange = it }
                )
                HorizontalDivider(color = colors.divider, thickness = 0.6.dp)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Radio Modulation", color = colors.textPrimary, fontSize = 14.sp)
                    Text(text = "1M / Coded PHY", color = Color(0xFF22C55E), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

// ========================================================
// SUB-SCREEN 5: 💬 MESSAGES
// ========================================================
@Composable
private fun MessagesSettingsSubScreen(
    coordinator: MeshCoordinator,
    colors: SettingsThemeColors
) {
    val context = LocalContext.current
    val readReceipts by coordinator.isReadReceiptsEnabled.collectAsState()
    val inAppAlerts by coordinator.isInAppAlertsEnabled.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            SubSectionCard(title = "Chat Delivery Receipts", colors = colors) {
                SettingSwitchRow(
                    title = "Read receipts",
                    subtitle = "Send automatic read confirmation tick when you open a peer's conversation",
                    checked = readReceipts,
                    colors = colors,
                    onCheckedChange = { coordinator.setReadReceiptsEnabled(it) }
                )
            }
        }

        item {
            SubSectionCard(title = "In-App Quick Reply Banner", colors = colors) {
                SettingSwitchRow(
                    title = "Pop-up reply card",
                    subtitle = "Show interactive quick-reply banner at top of screen when a message arrives while app is open",
                    checked = inAppAlerts,
                    colors = colors,
                    onCheckedChange = { coordinator.setInAppAlertsEnabled(it) }
                )
            }
        }

        item {
            SubSectionCard(title = "History Management", colors = colors) {
                Column {
                    Text(
                        text = "Erase all current conversation messages from memory.",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Button(
                        onClick = {
                            coordinator.clearChatHistory()
                            Toast.makeText(context, "Chat history cleared", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Text(text = "Clear All Chat History", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ========================================================
// SUB-SCREEN 6: 🔔 NOTIFICATIONS
// ========================================================
@Composable
private fun NotificationsSettingsSubScreen(
    coordinator: MeshCoordinator,
    colors: SettingsThemeColors
) {
    val context = LocalContext.current
    val inAppAlerts by coordinator.isInAppAlertsEnabled.collectAsState()
    val vibrationEnabled by coordinator.isVibrationEnabled.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            SubSectionCard(title = "Alert Style", colors = colors) {
                SettingSwitchRow(
                    title = "In-App floating alerts",
                    subtitle = "Show top heads-up banner with direct reply field for incoming peer messages",
                    checked = inAppAlerts,
                    colors = colors,
                    onCheckedChange = { coordinator.setInAppAlertsEnabled(it) }
                )
                HorizontalDivider(color = colors.divider, thickness = 0.6.dp)
                SettingSwitchRow(
                    title = "Vibration",
                    subtitle = "Vibrate device on incoming mesh messages",
                    checked = vibrationEnabled,
                    colors = colors,
                    onCheckedChange = { coordinator.setVibrationEnabled(it) }
                )
            }
        }

        item {
            SubSectionCard(title = "Test Notification", colors = colors) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = "Trigger a real Android Heads-Up Notification with Direct Reply to verify system permissions.",
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            NotificationHelper.showIncomingMessageNotification(
                                context = context,
                                peerId = "mesh-bot",
                                senderName = "NearbyMesh Test",
                                messageText = "This is a test notification! You can reply directly from here."
                            )
                            Toast.makeText(context, "Notification sent! Check your notification bar.", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Icon(imageVector = Icons.Default.NotificationsActive, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Send Test Notification", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ========================================================
// SUB-SCREEN 7: 👥 GROUPS
// ========================================================
@Composable
private fun GroupsSettingsSubScreen(
    coordinator: MeshCoordinator,
    colors: SettingsThemeColors,
    onOpenBroadcastGroup: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            SubSectionCard(title = "Decentralized Mesh Broadcast", colors = colors) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = "NearbyMesh supports zero-internet public broadcast channels. Every message sent to 'mesh-broadcast' is flooded across all reachable mesh nodes in range.",
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onOpenBroadcastGroup,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Groups, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Open Mesh Broadcast Channel", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ========================================================
// SUB-SCREEN 8: 🔒 PRIVACY & SECURITY
// ========================================================
@Composable
private fun PrivacySettingsSubScreen(
    coordinator: MeshCoordinator,
    colors: SettingsThemeColors
) {
    val context = LocalContext.current
    val readReceipts by coordinator.isReadReceiptsEnabled.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            SubSectionCard(title = "Chat Privacy", colors = colors) {
                SettingSwitchRow(
                    title = "Read receipts",
                    subtitle = "Let peers know when you have read their messages",
                    checked = readReceipts,
                    colors = colors,
                    onCheckedChange = { coordinator.setReadReceiptsEnabled(it) }
                )
            }
        }

        item {
            SubSectionCard(title = "Data Erasure", colors = colors) {
                Column {
                    TextButton(
                        onClick = {
                            coordinator.clearChatHistory()
                            Toast.makeText(context, "Local message history cleared", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Text(text = "Clear local messages", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    HorizontalDivider(color = colors.divider, thickness = 0.6.dp)
                    TextButton(
                        onClick = {
                            coordinator.clearLocalCacheAndMedia()
                            Toast.makeText(context, "Cached files & media cleared", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Text(text = "Clear received media & cache", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

// ========================================================
// SUB-SCREEN 9: 🛡 ENCRYPTION
// ========================================================
@Composable
private fun EncryptionSettingsSubScreen(
    coordinator: MeshCoordinator,
    colors: SettingsThemeColors
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Surface(
                color = colors.cardBg,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color(0x3322C55E)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF22C55E), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = "End-to-End Encrypted (E2EE)", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Every 1-to-1 message is cryptographically secured using Elliptic-Curve Diffie-Hellman (ECDH) on secp256r1 and AES-256-GCM. Intermediate mesh relay nodes only forward encrypted byte frames and cannot read message payloads.",
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        item {
            SubSectionCard(title = "Cryptographic Identity", colors = colors) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(text = "Your Public Node ID", color = colors.textSecondary, fontSize = 12.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = coordinator.myNodeId, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("Node ID", coordinator.myNodeId))
                            Toast.makeText(context, "Node ID copied", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", tint = colors.textMuted, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = "Public Key Fingerprint (Base64)", color = colors.textSecondary, fontSize = 12.sp)
                    Text(
                        text = coordinator.cryptoEngine.publicKeyBase64.take(48) + "...",
                        color = colors.textPrimary,
                        fontSize = 12.sp,
                        maxLines = 2
                    )
                }
            }
        }
    }
}

// ========================================================
// SUB-SCREEN 10: 📦 STORAGE
// ========================================================
@Composable
private fun StorageSettingsSubScreen(
    coordinator: MeshCoordinator,
    colors: SettingsThemeColors
) {
    val context = LocalContext.current
    var storageTriple by remember { mutableStateOf(coordinator.getStorageUsageFormatted()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SubSectionCard(title = "Storage Breakdown", colors = colors) {
                Column {
                    StorageBreakdownRow(label = "Application & data files", size = storageTriple.first, colors = colors)
                    HorizontalDivider(color = colors.divider, thickness = 0.6.dp)
                    StorageBreakdownRow(label = "Voice notes & media", size = storageTriple.second, colors = colors)
                    HorizontalDivider(color = colors.divider, thickness = 0.6.dp)
                    StorageBreakdownRow(label = "Mesh cache & temp chunks", size = storageTriple.third, colors = colors)
                }
            }
        }

        item {
            SubSectionCard(title = "Storage Actions", colors = colors) {
                Column {
                    TextButton(
                        onClick = {
                            coordinator.clearLocalCacheAndMedia()
                            storageTriple = coordinator.getStorageUsageFormatted()
                            Toast.makeText(context, "App cache cleared", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(text = "Clear Cache & Voice Notes", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    HorizontalDivider(color = colors.divider, thickness = 0.6.dp)
                    TextButton(
                        onClick = {
                            coordinator.clearChatHistory()
                            Toast.makeText(context, "Chat messages cleared", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(text = "Clear Chat History", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageBreakdownRow(label: String, size: String, colors: SettingsThemeColors) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = colors.textPrimary, fontSize = 14.sp)
        Text(text = size, color = colors.iconTint, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

// ========================================================
// SUB-SCREEN 11: 🔋 BATTERY & DATA
// ========================================================
@Composable
private fun BatteryDataSubScreen(
    coordinator: MeshCoordinator,
    colors: SettingsThemeColors
) {
    val batteryLevel = remember { coordinator.getBatteryLevel() }
    var meshScanningMode by remember { mutableStateOf("Balanced") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SubSectionCard(title = "Battery Status", colors = colors) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Device battery level", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(text = "Used for battery-aware mesh routing", color = colors.textSecondary, fontSize = 11.sp)
                    }
                    Text(text = "$batteryLevel%", color = Color(0xFF22C55E), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }

        item {
            SubSectionCard(title = "Mesh Scanning Power", colors = colors) {
                Column {
                    listOf("High (Lowest Latency)", "Balanced (Recommended)", "Battery Saver").forEach { option ->
                        val key = option.takeWhile { it != ' ' }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { meshScanningMode = key }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (meshScanningMode == key),
                                onClick = { meshScanningMode = key },
                                colors = RadioButtonDefaults.colors(selectedColor = colors.radioSelected)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = option, color = colors.textPrimary, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

// ========================================================
// SUB-SCREEN 12: 🎨 APPEARANCE (100% FUNCTIONAL THEME & ACCENT)
// ========================================================
@Composable
private fun AppearanceSubScreen(
    coordinator: MeshCoordinator,
    currentTheme: AppThemeMode,
    colors: SettingsThemeColors
) {
    val context = LocalContext.current
    val accentColor by coordinator.accentColorName.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Live Theme Preview Card
        item {
            SubSectionCard(title = "Live Theme Preview", colors = colors) {
                Surface(
                    color = if (colors.isDark) Color(0xFF0F1422) else Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (colors.isDark) Color(0x3338BDF8) else Color(0xFFCBD5E1)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (colors.isDark) Color(0xFF38BDF8) else Color(0xFF0284C7))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (colors.isDark) "Cosmic Dark Mode Active" else "Crisp Light Mode Active",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = colors.textPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Zero-internet mesh encryption running. This live card immediately adapts when toggling themes.",
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }
                }
            }
        }

        item {
            SubSectionCard(title = "App Theme", colors = colors) {
                Column {
                    listOf(
                        Triple(AppThemeMode.DARK, "Dark", "Deep cosmic dark palette, optimal for AMOLED screens"),
                        Triple(AppThemeMode.LIGHT, "Light", "Crisp, bright high-contrast aesthetic"),
                        Triple(AppThemeMode.SYSTEM, "System default", "Follows your Android device's system theme")
                    ).forEach { (mode, name, desc) ->
                        val isSelected = currentTheme == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coordinator.setAppTheme(mode)
                                    Toast.makeText(context, "$name theme activated", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    coordinator.setAppTheme(mode)
                                    Toast.makeText(context, "$name theme activated", Toast.LENGTH_SHORT).show()
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = colors.radioSelected)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = name, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text(text = desc, color = colors.textSecondary, fontSize = 12.sp)
                            }
                        }
                        if (mode != AppThemeMode.SYSTEM) {
                            HorizontalDivider(color = colors.divider, thickness = 0.6.dp)
                        }
                    }
                }
            }
        }

        item {
            SubSectionCard(title = "Accent Color", colors = colors) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    listOf(
                        "Green" to Color(0xFF22C55E),
                        "Blue" to Color(0xFF38BDF8),
                        "Purple" to Color(0xFFA855F7),
                        "Emerald" to Color(0xFF10B981)
                    ).forEach { (name, color) ->
                        val isSelected = accentColor == name
                        Surface(
                            color = if (isSelected) (if (colors.isDark) Color(0x33FFFFFF) else Color(0x20000000)) else colors.buttonSubtleBg,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(if (isSelected) 2.dp else 0.8.dp, if (isSelected) color else colors.divider),
                            modifier = Modifier
                                .weight(1f)
                                .bouncyClickable(scaleDown = 0.94f) {
                                    coordinator.setAccentColor(name)
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(modifier = Modifier.size(22.dp).clip(CircleShape).background(color))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = name,
                                    color = colors.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ========================================================
// SUB-SCREEN: 🔤 FONT & LAYOUT
// ========================================================
@Composable
private fun FontLayoutSubScreen(
    coordinator: MeshCoordinator,
    colors: SettingsThemeColors
) {
    val context = LocalContext.current
    val fontScale by coordinator.fontSizeScale.collectAsState()
    val isCompact by coordinator.isCompactBubbles.collectAsState()
    val isSmoothAnim by coordinator.isSmoothAnimations.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SubSectionCard(title = "Font Size", colors = colors) {
                Column {
                    listOf(
                        0.9f to "Compact (90%)",
                        1.0f to "Standard (100%)",
                        1.15f to "Large (115%)"
                    ).forEach { (scale, label) ->
                        val isSelected = kotlin.math.abs(fontScale - scale) < 0.05f
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coordinator.setFontSizeScale(scale)
                                    Toast.makeText(context, "$label applied", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    coordinator.setFontSizeScale(scale)
                                    Toast.makeText(context, "$label applied", Toast.LENGTH_SHORT).show()
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = colors.radioSelected)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = label,
                                color = colors.textPrimary,
                                fontSize = (15 * scale).sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        item {
            SubSectionCard(title = "Display & Motion", colors = colors) {
                Column {
                    SettingSwitchRow(
                        title = "Compact Chat Bubbles",
                        subtitle = "Reduce bubble padding for dense screen view",
                        checked = isCompact,
                        colors = colors,
                        onCheckedChange = { coordinator.setCompactBubbles(it) }
                    )
                    HorizontalDivider(color = colors.divider, thickness = 0.6.dp)
                    SettingSwitchRow(
                        title = "Hardware Animations",
                        subtitle = "Enable smooth 60fps transitions and radar sweep",
                        checked = isSmoothAnim,
                        colors = colors,
                        onCheckedChange = { coordinator.setSmoothAnimations(it) }
                    )
                }
            }
        }
    }
}

// ========================================================
// SUB-SCREEN: 🧪 EXPERIMENTAL MESH
// ========================================================
@Composable
private fun ExperimentalSubScreen(
    coordinator: MeshCoordinator,
    colors: SettingsThemeColors
) {
    val isMultiPath by coordinator.isMultiPathRoutingEnabled.collectAsState()
    val isLowPower by coordinator.isLowPowerDutyCycleEnabled.collectAsState()
    val stats by coordinator.stats.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SubSectionCard(title = "Advanced Radio Transport", colors = colors) {
                Column {
                    SettingSwitchRow(
                        title = "Dual-Band Multi-Path Relay",
                        subtitle = "Route mesh packets over BLE and Wi-Fi Direct concurrently for maximum redundancy",
                        checked = isMultiPath,
                        colors = colors,
                        onCheckedChange = { coordinator.setMultiPathRoutingEnabled(it) }
                    )
                    HorizontalDivider(color = colors.divider, thickness = 0.6.dp)
                    SettingSwitchRow(
                        title = "Low-Power Mesh Duty Cycling",
                        subtitle = "Pause radio scanning 200ms every second to conserve 35% battery",
                        checked = isLowPower,
                        colors = colors,
                        onCheckedChange = { coordinator.setLowPowerDutyCycleEnabled(it) }
                    )
                }
            }
        }

        item {
            SubSectionCard(title = "Live Mesh Packet Sniffer", colors = colors) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Node Address:", color = colors.textSecondary, fontSize = 12.sp)
                        Text(text = coordinator.myNodeId.take(12), color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Total Packets Routed:", color = colors.textSecondary, fontSize = 12.sp)
                        Text(text = "${stats.packetsRouted}", color = Color(0xFF22C55E), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Duplicate Drops:", color = colors.textSecondary, fontSize = 12.sp)
                        Text(text = "${stats.duplicatesDropped}", color = Color(0xFFF59E0B), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ========================================================
// SUB-SCREEN 13: ℹ️ ABOUT
// ========================================================
@Composable
private fun AboutSubScreen(colors: SettingsThemeColors) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = Color(0x1A22C55E),
            shape = CircleShape,
            border = BorderStroke(1.dp, Color(0x3322C55E)),
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = Icons.Default.WifiTethering, contentDescription = null, tint = Color(0xFF22C55E), modifier = Modifier.size(42.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "NearbyMesh", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
        Text(text = "Version 2.4.0 • Zero-Internet Mesh P2P", fontSize = 13.sp, color = colors.textSecondary)

        Spacer(modifier = Modifier.height(28.dp))

        Surface(
            color = colors.cardBg,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, colors.cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "NearbyMesh operates 100% offline using decentralized Bluetooth Low Energy (BLE) Mesh and Wi-Fi Direct socket relays.\n\n" +
                            "• No Central Servers or Cloud Database\n" +
                            "• No Phone Numbers or SIM Required\n" +
                            "• End-to-End Encrypted Peer Communication\n" +
                            "• Censorship-Resistant Local Multi-Hop Routing\n" +
                            "• Real-Time Offline Audio & File Transfers",
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

// ========================================================
// SUB-SCREEN 14: PROFILE EDIT
// ========================================================
@Composable
private fun ProfileEditSubScreen(
    coordinator: MeshCoordinator,
    userProfile: UserProfile,
    profileBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    colors: SettingsThemeColors,
    onPickPhoto: () -> Unit,
    onDone: () -> Unit
) {
    var editName by remember { mutableStateOf(userProfile.displayName) }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(if (colors.isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0))
                .border(2.dp, colors.iconTint, CircleShape)
                .bouncyClickable(scaleDown = 0.90f) { onPickPhoto() },
            contentAlignment = Alignment.Center
        ) {
            if (profileBitmap != null) {
                Image(
                    bitmap = profileBitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Text(text = editName.take(1).uppercase(), color = colors.textPrimary, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onPickPhoto) {
            Text(text = "Change Photo", color = colors.iconTint, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = editName,
            onValueChange = { editName = it },
            label = { Text("Display Name") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF22C55E),
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                focusedLabelColor = Color(0xFF22C55E),
                unfocusedLabelColor = colors.textSecondary
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = {
                coordinator.updateDisplayName(editName)
                onDone()
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text(text = "Save Changes", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

// ========================================================
// SHARED SUB-COMPONENTS
// ========================================================
@Composable
private fun SubSectionCard(
    title: String,
    colors: SettingsThemeColors,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textSecondary,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
        )
        Surface(
            color = colors.cardBg,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(0.8.dp, colors.cardBorder),
            shadowElevation = if (colors.isDark) 4.dp else 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), content = content)
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    colors: SettingsThemeColors,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, color = colors.textSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF22C55E),
                uncheckedThumbColor = colors.textMuted,
                uncheckedTrackColor = if (colors.isDark) Color(0xFF1E293B) else Color(0xFFCBD5E1)
            )
        )
    }
}

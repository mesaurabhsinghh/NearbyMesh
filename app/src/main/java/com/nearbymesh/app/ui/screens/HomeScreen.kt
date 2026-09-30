package com.nearbymesh.app.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nearbymesh.app.core.MeshCoordinator
import com.nearbymesh.app.core.MeshPeer
import com.nearbymesh.app.core.models.UserProfile
import com.nearbymesh.app.ui.theme.*
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

data class PresetAvatarItem(
    val id: Int,
    val emoji: String,
    val title: String,
    val gradient: Brush
)

val PRESET_AVATARS = listOf(
    PresetAvatarItem(1, "⚡", "Volt", Brush.linearGradient(listOf(Color(0xFF38BDF8), Color(0xFF1D4ED8)))),
    PresetAvatarItem(2, "🚀", "Cosmo", Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFFEC4899)))),
    PresetAvatarItem(3, "🔥", "Blaze", Brush.linearGradient(listOf(Color(0xFFF97316), Color(0xFFDC2626)))),
    PresetAvatarItem(4, "💎", "Crystal", Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF06B6D4)))),
    PresetAvatarItem(5, "🦊", "Fox", Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFEA580C)))),
    PresetAvatarItem(6, "🎧", "Sonic", Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF4338CA))))
)

/**
 * Friend's Contact Screen with User Profile Photo in Center, Advanced High-Tech Radar Animation,
 * and Nearby Device Nicknames shown right BESIDE (bagal me) each orbiting avatar.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val context = LocalContext.current
    val peersMap by coordinator.peers.collectAsState()
    val peers = remember(peersMap) { peersMap.values.toList() }
    val userProfile by coordinator.userProfile.collectAsState()

    var showProfileModal by remember { mutableStateOf(false) }

    // Real Photo Picker to set/change Profile Picture
    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { pickedUri ->
            try {
                // Permanently cache photo into app's files directory
                val avatarFile = File(context.filesDir, "user_profile_avatar.jpg")
                context.contentResolver.openInputStream(pickedUri)?.use { input ->
                    avatarFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                coordinator.updateProfileImage(avatarFile.absolutePath)
            } catch (e: Exception) {
                coordinator.updateProfileImage(pickedUri.toString())
            }
        }
    }

    // Decode User's Profile Picture Bitmap
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
            } catch (e: Exception) {
                null
            }
        }
    }

    // 1. Continuous smooth orbital rotation for radar sweep & devices (State objects, zero recomposition)
    val infiniteTransition = rememberInfiniteTransition(label = "RadarTransitions")
    val orbitAngleState = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 24000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "OrbitAngle"
    )

    // Reverse slow rotation for outer track ticks
    val counterAngleState = infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 40000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "CounterAngle"
    )

    // 2. High-Tech Sonar Radar Wave Ripples radiating outward
    val ripple1State = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Ripple1"
    )
    val ripple2State = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, delayMillis = 930, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Ripple2"
    )
    val ripple3State = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, delayMillis = 1860, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Ripple3"
    )

    // 3. Subtle breathing scale & glow for center profile
    val pulseScaleState = infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Pulse"
    )

    val haloGlowState = infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "HaloGlow"
    )

    val rippleStates = remember { listOf(ripple1State, ripple2State, ripple3State) }

    val currentTheme by coordinator.appTheme.collectAsState()
    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isDark = when (currentTheme) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> isSystemDark
    }
    val homeBgBrush = if (isDark) {
        Brush.radialGradient(
            colors = listOf(
                Color(0xFF131522),
                Color(0xFF090A0F),
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
    val homeTextPrimary = if (isDark) Color.White else Color(0xFF0F172A)
    val homeTextSecondary = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(homeBgBrush)
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(top = 10.dp, bottom = 96.dp), // Clearance for floating pill bar
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Header: NearbyMesh + Prominent Scanning Badge + Scan Trigger
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "NearbyMesh",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = homeTextPrimary,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .shadow(6.dp, CircleShape, spotColor = Color(0xFF22C55E))
                                .clip(CircleShape)
                                .background(Color(0xFF22C55E))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (peers.isEmpty()) "Scanning nearby..." else "${peers.size} peers connected",
                            fontSize = 12.sp,
                            color = Color(0xFF22C55E),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Glass Refresh Button
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color(0xCC161622) else Color(0xFFFFFFFF))
                        .border(
                            1.dp,
                            if (isDark) GlassHighlightGradient else Brush.linearGradient(listOf(Color(0xFFCBD5E1), Color(0xFFE2E8F0))),
                            CircleShape
                        )
                        .bouncyClickable(scaleDown = 0.88f) { coordinator.triggerScan() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Scan",
                        tint = homeTextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // ========================================================
            // CENTER RADAR COMPASS DISC (High-Tech Animation + Peers)
            // ========================================================
            Box(
                modifier = Modifier
                    .size(320.dp),
                contentAlignment = Alignment.Center
            ) {
                // 1. Ambient Dynamic Pulsing Glow behind radar (Zero recomposition, pure GPU RenderNode)
                Box(
                    modifier = Modifier
                        .size(320.dp)
                        .graphicsLayer {
                            val s = pulseScaleState.value
                            scaleX = s
                            scaleY = s
                            alpha = haloGlowState.value
                        }
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0x6638BDF8),
                                    Color(0x262563EB),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // 2. Outer Track Ring with Rotating Dashed Accents
                Box(
                    modifier = Modifier
                        .size(300.dp)
                        .shadow(28.dp, CircleShape, spotColor = Color(0x88000000))
                        .clip(CircleShape)
                        .background(GlassDiscOuterGradient)
                        .border(1.5.dp, GlassHighlightGradient, CircleShape)
                )

                // Rotating cardinal tick accents (Runs 100% on GPU render layer)
                Box(
                    modifier = Modifier
                        .size(290.dp)
                        .graphicsLayer { rotationZ = counterAngleState.value },
                    contentAlignment = Alignment.Center
                ) {
                    listOf(0.0, 90.0, 180.0, 270.0).forEach { tickAngle ->
                        val rad = Math.toRadians(tickAngle)
                        val r = 142.0
                        Box(
                            modifier = Modifier
                                .offset(x = (r * cos(rad)).dp, y = (r * sin(rad)).dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0x8838BDF8))
                        )
                    }
                }

                // 3. Middle Glass Ring Track (radius ~210dp)
                Box(
                    modifier = Modifier
                        .size(214.dp)
                        .clip(CircleShape)
                        .background(GlassDiscInnerGradient)
                        .border(1.dp, Brush.verticalGradient(listOf(Color(0x4038BDF8), Color(0x1038BDF8))), CircleShape)
                )

                // 4. Inner Ring Track (radius ~140dp)
                Box(
                    modifier = Modifier
                        .size(142.dp)
                        .clip(CircleShape)
                        .border(1.dp, Color(0x3060A5FA), CircleShape)
                )

                // 5. Expanding Sonar Radar Wave Ripples (Zero recomposition, GPU scaled via graphicsLayer)
                rippleStates.forEach { rippleState ->
                    Box(
                        modifier = Modifier
                            .size(300.dp)
                            .graphicsLayer {
                                val p = rippleState.value
                                val s = 0.32f + 0.68f * p
                                scaleX = s
                                scaleY = s
                                alpha = (1f - p).coerceIn(0f, 1f) * 0.55f
                            }
                            .clip(CircleShape)
                            .border(
                                width = 1.4.dp,
                                brush = Brush.linearGradient(
                                    listOf(
                                        Color(0xFF38BDF8),
                                        Color(0xFF2563EB)
                                    )
                                ),
                                shape = CircleShape
                            )
                    )
                }

                // 6. Rotating High-Tech Sweep Scanner Beam (GPU graphicsLayer)
                Box(
                    modifier = Modifier
                        .size(296.dp)
                        .graphicsLayer { rotationZ = orbitAngleState.value }
                        .clip(CircleShape)
                        .background(
                            Brush.sweepGradient(
                                0.0f to Color.Transparent,
                                0.70f to Color.Transparent,
                                0.90f to Color(0x1538BDF8),
                                0.98f to Color(0x4038BDF8),
                                1.0f to Color(0x6060A5FA)
                            )
                        )
                )

                // ========================================================
                // CENTER USER PROFILE PHOTO (Replacing static 'h')
                // ========================================================
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .graphicsLayer {
                            val s = pulseScaleState.value
                            scaleX = s
                            scaleY = s
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Outer Glowing Ring around profile
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .shadow(24.dp, CircleShape, spotColor = Color(0x9938BDF8), ambientColor = Color(0x662563EB))
                            .clip(CircleShape)
                            .background(Color(0xFF161824))
                            .border(
                                2.5.dp,
                                Brush.sweepGradient(
                                    listOf(
                                        Color(0xFF38BDF8),
                                        Color(0xFF2563EB),
                                        Color(0xFF818CF8),
                                        Color(0xFF38BDF8)
                                    )
                                ),
                                CircleShape
                            )
                            .bouncyClickable(scaleDown = 0.92f) {
                                showProfileModal = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            profileBitmap != null -> {
                                Image(
                                    bitmap = profileBitmap,
                                    contentDescription = "My Profile Photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                )
                            }

                            userProfile.avatarId > 0 -> {
                                val preset = PRESET_AVATARS.firstOrNull { it.id == userProfile.avatarId } ?: PRESET_AVATARS[0]
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(preset.gradient),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = preset.emoji,
                                        fontSize = 40.sp
                                    )
                                }
                            }

                            else -> {
                                // Modern Profile Silhouette (No 'H')
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.linearGradient(
                                                listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "Default Profile",
                                        tint = Color(0xFF93C5FD),
                                        modifier = Modifier.size(46.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Floating Camera Badge at Bottom-Right (Unclipped, Crisp white border)
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .align(Alignment.BottomEnd)
                            .offset(x = 1.dp, y = 1.dp)
                            .shadow(10.dp, CircleShape, spotColor = Color(0xCC000000))
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF38BDF8), Color(0xFF2563EB))
                                )
                            )
                            .border(2.dp, Color.White, CircleShape)
                            .bouncyClickable(scaleDown = 0.85f) { showProfileModal = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Change Photo",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    // "◉ You" Center Indicator Pill
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = 12.dp)
                            .bouncyClickable(scaleDown = 0.88f) { showProfileModal = true },
                        color = Color(0xEE0A0F1D),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0x5538BDF8)),
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF38BDF8))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "You",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                // ========================================================
                // NEARBY DEVICES ORBITING BESIDE (BAGAL ME) USER PROFILE
                // ========================================================
                if (peers.isNotEmpty()) {
                    val count = peers.size
                    val step = 360.0 / count

                    peers.take(6).forEachIndexed { index, peer ->
                        // Horizontal Row: Avatar Circle + Nickname Tag right BESIDE it! (GPU RenderNode transforms)
                        Row(
                            modifier = Modifier
                                .graphicsLayer {
                                    val angleDeg = (orbitAngleState.value * 0.7f + index * step) % 360.0
                                    val angleRad = Math.toRadians(angleDeg)
                                    val radiusPx = (if (index % 2 == 0) 118.dp else 102.dp).toPx()
                                    translationX = (radiusPx * cos(angleRad)).toFloat()
                                    translationY = (radiusPx * sin(angleRad)).toFloat()
                                }
                                .bouncyClickable(scaleDown = 0.92f) {
                                    onOpenChat(peer.nodeId)
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Peer Avatar Circle
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .shadow(12.dp, CircleShape, spotColor = Color(0x6622C55E))
                                    .clip(CircleShape)
                                    .background(
                                        when (index % 5) {
                                            0 -> Brush.linearGradient(listOf(Color(0xFF1E3A8A), Color(0xFF3B82F6)))
                                            1 -> Brush.linearGradient(listOf(Color(0xFF9A3412), Color(0xFFF97316)))
                                            2 -> Brush.linearGradient(listOf(Color(0xFF065F46), Color(0xFF10B981)))
                                            3 -> Brush.linearGradient(listOf(Color(0xFF6B21A8), Color(0xFFA855F7)))
                                            else -> Brush.linearGradient(listOf(Color(0xFF9D174D), Color(0xFFEC4899)))
                                        }
                                    )
                                    .border(2.dp, Color(0xFF4ADE80), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = peer.nickname.take(1).uppercase(),
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Nickname Tag shown right beside (bagal me) the peer avatar
                            Surface(
                                color = Color(0xEE14141E),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Brush.verticalGradient(listOf(Color(0x55FFFFFF), Color(0x15FFFFFF)))),
                                shadowElevation = 6.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                        .background(Color(0xFF22C55E))
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Column {
                                        Text(
                                            text = peer.nickname,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Online • Chat",
                                            fontSize = 8.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Scanning State: Orbiting Beacon with tag right BESIDE it (GPU RenderNode transforms)
                    Row(
                        modifier = Modifier
                            .graphicsLayer {
                                val angleDeg = (orbitAngleState.value * 0.8f) % 360.0
                                val angleRad = Math.toRadians(angleDeg)
                                val radiusPx = 108.dp.toPx()
                                translationX = (radiusPx * cos(angleRad)).toFloat()
                                translationY = (radiusPx * sin(angleRad)).toFloat()
                            }
                            .bouncyClickable(scaleDown = 0.92f) {
                                onOpenChat("mesh-broadcast")
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .shadow(14.dp, CircleShape, spotColor = Color(0xAA3B82F6))
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(listOf(Color(0xFF2563EB), Color(0xFF0284C7)))
                            )
                            .border(2.dp, Color(0xFF93C5FD), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.WifiTethering,
                                contentDescription = "Scanning Mesh",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Device Tag beside (bagal me) the node
                        Surface(
                            color = Color(0xEE14141E),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Brush.verticalGradient(listOf(Color(0x55FFFFFF), Color(0x15FFFFFF)))),
                            shadowElevation = 6.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF38BDF8))
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Column {
                                    Text(
                                        text = "Mesh Scanner",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Searching peers...",
                                        fontSize = 8.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ========================================================
            // NEARBY DEVICES SECTION HEADER
            // ========================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (peers.isEmpty()) "Nearby Devices" else "${peers.size} Nearby Devices",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = homeTextPrimary
                )

                if (peers.isNotEmpty()) {
                    Surface(
                        color = Color(0x2222C55E),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0x4422C55E))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF22C55E))
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Active",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF4ADE80)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ========================================================
            // NEARBY DEVICES LIST CARDS
            // ========================================================
            if (peers.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    peers.forEach { peer ->
                        val distanceStr = remember(peer.rssi) {
                            val dist = ((peer.rssi.coerceIn(-95, -35) + 95) * 0.12f + 1.2f)
                            String.format(java.util.Locale.US, "%.1f m", dist)
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bouncyClickable(scaleDown = 0.97f) {
                                    onOpenChat(peer.nodeId)
                                },
                            color = if (isDark) Color(0x351E293B) else Color(0xFFFFFFFF),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, if (isDark) Color(0x25FFFFFF) else Color(0xFFE2E8F0)),
                            shadowElevation = if (isDark) 4.dp else 1.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Avatar Circle
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(Color(0xFF1E3A8A), Color(0xFF3B82F6))
                                            )
                                        )
                                        .border(1.5.dp, Color(0xFF38BDF8), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = peer.nickname.take(1).uppercase(),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = peer.nickname,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = homeTextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "$distanceStr  •  ",
                                            fontSize = 12.sp,
                                            color = homeTextSecondary,
                                            fontWeight = FontWeight.Normal
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF22C55E))
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Available",
                                            fontSize = 12.sp,
                                            color = Color(0xFF4ADE80),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    contentDescription = null,
                                    tint = homeTextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                // Empty state card when scanning
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (isDark) Color(0x221E293B) else Color(0xFFFFFFFF),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, if (isDark) Color(0x18FFFFFF) else Color(0xFFE2E8F0)),
                    shadowElevation = if (isDark) 2.dp else 1.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color(0xFF38BDF8),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Scanning for Nearby Friends...",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = homeTextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Keep Bluetooth & Location ON for discovery",
                                fontSize = 11.sp,
                                color = homeTextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // ========================================================
            // PRIMARY ACTION BUTTONS: [ 💬 Message ] [ 📁 Send ] [ 👥 Groups ]
            // ========================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Message Action Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .shadow(12.dp, RoundedCornerShape(16.dp), spotColor = Color(0x4438BDF8))
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF2563EB), Color(0xFF1D4ED8))
                            )
                        )
                        .border(1.dp, Color(0x5593C5FD), RoundedCornerShape(16.dp))
                        .bouncyClickable(scaleDown = 0.94f) {
                            onOpenChatsTab()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubble,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = "Message",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // 2. Send Files Action Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .shadow(12.dp, RoundedCornerShape(16.dp), spotColor = Color(0x3310B981))
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF059669), Color(0xFF047857))
                            )
                        )
                        .border(1.dp, Color(0x556EE7B7), RoundedCornerShape(16.dp))
                        .bouncyClickable(scaleDown = 0.94f) {
                            if (peers.isNotEmpty()) {
                                onOpenChat(peers.first().nodeId)
                            } else {
                                onOpenReceive()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = "Send",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // 3. Groups Action Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .shadow(12.dp, RoundedCornerShape(16.dp), spotColor = Color(0x338B5CF6))
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF7C3AED), Color(0xFF6D28D9))
                            )
                        )
                        .border(1.dp, Color(0x55C4B5FD), RoundedCornerShape(16.dp))
                        .bouncyClickable(scaleDown = 0.94f) {
                            onOpenCreateGroup()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Groups",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }

    // ========================================================
    // PROFILE CUSTOMIZATION MODAL (Photo Picker + Avatars + Name)
    // ========================================================
    if (showProfileModal) {
        ProfileEditSheet(
            coordinator = coordinator,
            userProfile = userProfile,
            profileBitmap = profileBitmap,
            onPickFromGallery = {
                photoPickerLauncher.launch("image/*")
            },
            onDismiss = { showProfileModal = false }
        )
    }
}

/**
 * Frosted Glass Profile Customization Sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditSheet(
    coordinator: MeshCoordinator,
    userProfile: UserProfile,
    profileBitmap: ImageBitmap?,
    onPickFromGallery: () -> Unit,
    onDismiss: () -> Unit
) {
    var editName by remember { mutableStateOf(userProfile.displayName) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141520),
        scrimColor = Color(0x99000000),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0x44FFFFFF))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Your Mesh Profile",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = "Set your profile photo & name for nearby devices",
                fontSize = 13.sp,
                color = Color(0xFF94A3B8),
                modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
            )

            // Current Avatar Preview
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .shadow(16.dp, CircleShape, spotColor = Color(0x8838BDF8))
                    .clip(CircleShape)
                    .background(Color(0xFF1E293B))
                    .border(2.5.dp, Color(0xFF38BDF8), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                when {
                    profileBitmap != null -> {
                        Image(
                            bitmap = profileBitmap,
                            contentDescription = "Preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    }
                    userProfile.avatarId > 0 -> {
                        val preset = PRESET_AVATARS.firstOrNull { it.id == userProfile.avatarId } ?: PRESET_AVATARS[0]
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(preset.gradient),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = preset.emoji, fontSize = 38.sp)
                        }
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color(0xFF93C5FD),
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action 1: Upload Photo from Gallery
            Button(
                onClick = {
                    onPickFromGallery()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (profileBitmap != null) "Change Photo from Gallery" else "Upload Photo from Gallery",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (profileBitmap != null) {
                Spacer(modifier = Modifier.height(6.dp))
                TextButton(
                    onClick = {
                        coordinator.updateProfileImage(null)
                    }
                ) {
                    Text(
                        text = "Remove Custom Photo",
                        color = Color(0xFFEF4444),
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action 2: Or Pick Preset Avatar
            Text(
                text = "Or choose a preset avatar",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFCBD5E1),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            )

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(PRESET_AVATARS) { preset ->
                    val isSelected = userProfile.avatarId == preset.id && profileBitmap == null
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(preset.gradient)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) Color.White else Color(0x33FFFFFF),
                                shape = CircleShape
                            )
                            .clickable {
                                coordinator.updateAvatarId(preset.id)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = preset.emoji, fontSize = 26.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action 3: Display Name Field
            OutlinedTextField(
                value = editName,
                onValueChange = { editName = it },
                label = { Text("Display Name / Nickname", color = Color(0xFF94A3B8)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF38BDF8),
                    unfocusedBorderColor = Color(0x33FFFFFF),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Done Button
            Button(
                onClick = {
                    if (editName.isNotBlank() && editName != userProfile.displayName) {
                        coordinator.updateDisplayName(editName)
                    }
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) {
                Text(
                    text = "Save & Done",
                    color = Color.Black,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

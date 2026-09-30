package com.nearbymesh.app.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nearbymesh.app.core.MeshCoordinator
import com.nearbymesh.app.core.models.MessageContentType
import com.nearbymesh.app.core.models.RichChatMessage
import com.nearbymesh.app.core.models.MessageStatus
import com.nearbymesh.app.ui.theme.bouncyClickable
import com.nearbymesh.app.ui.theme.AppThemeMode
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Chat Detail Screen (Exact implementation of Picture 3).
 * Features:
 * - Peer avatar with green online badge
 * - "Online" status
 * - Round Action Chips: Voice Call, Video/Camera, 3-dots Menu
 * - Green timestamp headers ("8:16PM", "8:19PM")
 * - Left incoming bubbles with sender name and avatar
 * - Right outgoing bubbles
 * - Bottom input bar: "Type here" pill with Camera icon + separate circular "+" attachment button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    coordinator: MeshCoordinator,
    peerId: String,
    peerName: String,
    onBack: () -> Unit
) {
    // Hardware/Gesture Back Navigation: takes user back to Messages List
    BackHandler {
        onBack()
    }

    val context = LocalContext.current
    val allMessages by coordinator.richMessages.collectAsState()
    val messages = remember(allMessages, peerId) {
        allMessages.filter { it.recipientNodeId == peerId || it.senderNodeId == peerId }
    }

    val haptic = LocalHapticFeedback.current
    val audioPlayerManager = remember { AudioPlayerManager(context) }
    val audioRecordManager = remember { AudioRecordManager(context) }
    var isRecordingVoice by remember { mutableStateOf(false) }
    var recordElapsedSeconds by remember { mutableIntStateOf(0) }

    // Automatically mark chat as read (updates status bar notification and sends read receipt)
    LaunchedEffect(peerId, messages.size) {
        coordinator.markChatAsRead(peerId)
    }

    DisposableEffect(Unit) {
        onDispose {
            audioPlayerManager.release()
            audioRecordManager.cancelRecording()
        }
    }

    LaunchedEffect(isRecordingVoice) {
        if (isRecordingVoice) {
            recordElapsedSeconds = 0
            while (isRecordingVoice) {
                delay(1000L)
                recordElapsedSeconds++
            }
        }
    }

    var inputText by remember { mutableStateOf("") }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Real System File Pickers
    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val (name, size) = queryFileMetadata(context, it)
            coordinator.sendRichMessage(
                recipientNodeId = peerId,
                text = name,
                contentType = MessageContentType.IMAGE,
                mediaUri = it.toString(),
                fileName = name,
                fileSizeFormatted = size
            )
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val (name, size) = queryFileMetadata(context, it)
            coordinator.sendRichMessage(
                recipientNodeId = peerId,
                text = name,
                contentType = MessageContentType.VIDEO,
                mediaUri = it.toString(),
                fileName = name,
                fileSizeFormatted = size
            )
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val (name, size) = queryFileMetadata(context, it)
            coordinator.sendRichMessage(
                recipientNodeId = peerId,
                text = name,
                contentType = MessageContentType.AUDIO,
                mediaUri = it.toString(),
                fileName = name,
                fileSizeFormatted = size
            )
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val (name, size) = queryFileMetadata(context, it)
            coordinator.sendRichMessage(
                recipientNodeId = peerId,
                text = name,
                contentType = MessageContentType.DOCUMENT,
                mediaUri = it.toString(),
                fileName = name,
                fileSizeFormatted = size
            )
        }
    }

    // Auto-scroll to bottom on new message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val currentTheme by coordinator.appTheme.collectAsState()
    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isDark = when (currentTheme) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> isSystemDark
    }
    val chatBg = if (isDark) Color(0xFF0C0C0E) else Color(0xFFF1F5F9)
    val topBarBg = if (isDark) Color(0xE613131A) else Color(0xF8FFFFFF)
    val textPrimary = if (isDark) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .background(chatBg)
    ) {
        // 1. Top Header Bar (Matching Picture 3 Frosted Glassmorphism)
        Surface(
            color = topBarBg,
            border = BorderStroke(
                0.5.dp,
                if (isDark) Brush.verticalGradient(listOf(Color(0x35FFFFFF), Color(0x08FFFFFF)))
                else Brush.verticalGradient(listOf(Color(0xFFCBD5E1), Color(0xFFE2E8F0)))
            ),
            shadowElevation = if (isDark) 14.dp else 2.dp,
            modifier = Modifier.fillMaxWidth().statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Back button + Peer Avatar + Name + Online
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .bouncyClickable(scaleDown = 0.85f) { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = textPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Peer Avatar with Green Online Dot
                    Box {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .shadow(8.dp, CircleShape, spotColor = Color(0x44000000))
                                .clip(CircleShape)
                                .background(
                                    when (peerName.take(1).uppercase()) {
                                        "A" -> Brush.linearGradient(listOf(Color(0xFF1E3A8A), Color(0xFF3B82F6)))
                                        "B" -> Brush.linearGradient(listOf(Color(0xFF0D9488), Color(0xFF14B8A6)))
                                        "C" -> Brush.linearGradient(listOf(Color(0xFF9A3412), Color(0xFFF97316)))
                                        "P" -> Brush.linearGradient(listOf(Color(0xFF6B21A8), Color(0xFFA855F7)))
                                        else -> Brush.linearGradient(listOf(Color(0xFF065F46), Color(0xFF10B981)))
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = peerName.take(1).uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }

                        // Bright Neon Green Online Dot Badge (Picture 3)
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .align(Alignment.TopEnd)
                                .shadow(6.dp, CircleShape, spotColor = Color(0xFF22C55E))
                                .clip(CircleShape)
                                .background(Color(0xFF22C55E))
                                .border(1.5.dp, if (isDark) Color(0xFF121216) else Color(0xFFFFFFFF), CircleShape)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = peerName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                        Text(
                            text = "Online",
                            fontSize = 12.sp,
                            color = Color(0xFF22C55E),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Right: 3 Frosted Circular Action Chips (Audio Call, Video, More)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Audio Call Chip (Real Offline Voice Call!)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .shadow(6.dp, CircleShape, spotColor = Color(0x33000000))
                            .clip(CircleShape)
                            .background(Color(0x28FFFFFF))
                            .border(1.dp, Brush.verticalGradient(listOf(Color(0x45FFFFFF), Color(0x10FFFFFF))), CircleShape)
                            .bouncyClickable(scaleDown = 0.86f) { coordinator.startVoiceCall(peerId, peerName) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Voice Call",
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Video Chip
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .shadow(6.dp, CircleShape, spotColor = Color(0x33000000))
                            .clip(CircleShape)
                            .background(Color(0x28FFFFFF))
                            .border(1.dp, Brush.verticalGradient(listOf(Color(0x45FFFFFF), Color(0x10FFFFFF))), CircleShape)
                            .bouncyClickable(scaleDown = 0.86f) { photoPickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "Video",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Options Menu Chip
                    Box {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .shadow(6.dp, CircleShape, spotColor = Color(0x33000000))
                                .clip(CircleShape)
                                .background(Color(0x28FFFFFF))
                                .border(1.dp, Brush.verticalGradient(listOf(Color(0x45FFFFFF), Color(0x10FFFFFF))), CircleShape)
                                .bouncyClickable(scaleDown = 0.86f) { showMenu = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Share Photo") },
                                onClick = {
                                    showMenu = false
                                    photoPickerLauncher.launch("image/*")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Share Video") },
                                onClick = {
                                    showMenu = false
                                    videoPickerLauncher.launch("video/*")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Voice Call") },
                                onClick = {
                                    showMenu = false
                                    coordinator.startVoiceCall(peerId, peerName)
                                }
                            )
                        }
                    }
                }
            }
        }

        // 2. Messages List (Matching Picture 3)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (messages.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "8:16PM",
                        fontSize = 12.sp,
                        color = Color(0xFF22C55E),
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        color = Color(0xFF1E1E22),
                        shape = RoundedCornerShape(18.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A2A30)),
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = peerName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF8E8E93)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Zero-internet offline direct chat ready over Bluetooth mesh. Send messages, photos, and voice notes.",
                                fontSize = 14.sp,
                                color = Color.White,
                                lineHeight = 19.sp
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(messages) { message ->
                        MessageItemRow(
                            message = message,
                            peerName = peerName,
                            isPlaying = audioPlayerManager.currentlyPlayingUri == message.mediaUri,
                            isDark = isDark,
                            onPlayAudio = { uri -> audioPlayerManager.playOrPause(uri) }
                        )
                    }
                }
            }
        }

        // 3. Bottom Input Bar (Matching Picture 3 Frosted Glassmorphism)
        Surface(
            color = Color(0xE613131A),
            border = BorderStroke(0.5.dp, Brush.verticalGradient(listOf(Color(0x35FFFFFF), Color(0x08FFFFFF)))),
            modifier = Modifier.fillMaxWidth().navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pill Input Container: "Type here" + Camera Icon (Frosted Glass)
                Surface(
                    color = if (isDark) Color(0xCC1A1A24) else Color(0xFFFFFFFF),
                    shape = RoundedCornerShape(28.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isDark) Brush.verticalGradient(listOf(Color(0x3DFFFFFF), Color(0x0DFFFFFF)))
                        else Brush.verticalGradient(listOf(Color(0xFFCBD5E1), Color(0xFFE2E8F0)))
                    ),
                    shadowElevation = if (isDark) 8.dp else 2.dp,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isRecordingVoice) {
                            // Walkie-Talkie Active Recording Visualizer
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                val infiniteTransition = rememberInfiniteTransition(label = "recDot")
                                val dotAlpha by infiniteTransition.animateFloat(
                                    initialValue = 0.2f,
                                    targetValue = 1f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(400, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "dotAlpha"
                                )
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFEF4444).copy(alpha = dotAlpha))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Recording 0:%02d... Release to send".format(recordElapsedSeconds),
                                    color = Color(0xFFEF4444),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        } else {
                            androidx.compose.foundation.text.BasicTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = textPrimary,
                                    fontSize = 15.sp
                                ),
                                singleLine = true,
                                decorationBox = { innerTextField ->
                                    if (inputText.isEmpty()) {
                                        Text(
                                            text = "Type here or hold mic...",
                                            color = textSecondary,
                                            fontSize = 15.sp
                                        )
                                    }
                                    innerTextField()
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Send Arrow if user typed text, else Walkie-Talkie Mic Button
                        if (inputText.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x3322C55E))
                                    .bouncyClickable(scaleDown = 0.85f) {
                                        val textToSend = inputText.trim()
                                        inputText = ""
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        coordinator.sendRichMessage(
                                            recipientNodeId = peerId,
                                            text = textToSend,
                                            contentType = MessageContentType.TEXT
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    tint = Color(0xFF22C55E),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            // Walkie-Talkie Push-to-Talk Mic Button (Hold to talk, release to send)
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (isRecordingVoice) Color(0xFFEF4444) else Color(0x28FFFFFF))
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onPress = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                val file = audioRecordManager.startRecording()
                                                if (file != null) {
                                                    isRecordingVoice = true
                                                    val released = tryAwaitRelease()
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    isRecordingVoice = false
                                                    if (released) {
                                                        val (recFile, duration) = audioRecordManager.stopRecording()
                                                        if (recFile != null && duration >= 1) {
                                                            coordinator.sendRichMessage(
                                                                recipientNodeId = peerId,
                                                                text = "🎙️ Voice note (${duration}s)",
                                                                contentType = MessageContentType.AUDIO,
                                                                mediaUri = recFile.absolutePath,
                                                                audioDurationSeconds = duration
                                                            )
                                                        }
                                                    } else {
                                                        audioRecordManager.cancelRecording()
                                                    }
                                                }
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Hold for Walkie-Talkie",
                                    tint = if (isRecordingVoice) Color.White else Color(0xFF22C55E),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Separate Circular Plus Button on Far Right (Picture 3 Frosted Glass)
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .shadow(if (isDark) 10.dp else 2.dp, CircleShape, spotColor = Color(0x44000000))
                        .clip(CircleShape)
                        .background(if (isDark) Color(0x28FFFFFF) else Color(0xFFFFFFFF))
                        .border(
                            1.dp,
                            if (isDark) Brush.verticalGradient(listOf(Color(0x45FFFFFF), Color(0x10FFFFFF)))
                            else Brush.verticalGradient(listOf(Color(0xFFCBD5E1), Color(0xFFE2E8F0))),
                            CircleShape
                        )
                        .bouncyClickable(scaleDown = 0.88f) { showAttachmentSheet = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Files",
                        tint = textPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }

    // Attachment Modal Bottom Sheet: Real Phone Storage Picker
    if (showAttachmentSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAttachmentSheet = false },
            containerColor = Color(0xFF141416),
            dragHandle = { BottomSheetDefaults.DragHandle() },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Share from Your Phone",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Select real photos, videos, audio, or files stored on device",
                    fontSize = 12.sp,
                    color = Color(0xFF8E8E93)
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    AttachmentOption(
                        icon = Icons.Default.PhotoLibrary,
                        label = "Photos",
                        color = Color(0xFF1E88E5),
                        onClick = {
                            showAttachmentSheet = false
                            photoPickerLauncher.launch("image/*")
                        }
                    )
                    AttachmentOption(
                        icon = Icons.Default.VideoLibrary,
                        label = "Videos",
                        color = Color(0xFFE53935),
                        onClick = {
                            showAttachmentSheet = false
                            videoPickerLauncher.launch("video/*")
                        }
                    )
                    AttachmentOption(
                        icon = Icons.Default.LibraryMusic,
                        label = "Audio",
                        color = Color(0xFF8E24AA),
                        onClick = {
                            showAttachmentSheet = false
                            audioPickerLauncher.launch("audio/*")
                        }
                    )
                    AttachmentOption(
                        icon = Icons.Default.InsertDriveFile,
                        label = "Documents",
                        color = Color(0xFF43A047),
                        onClick = {
                            showAttachmentSheet = false
                            documentPickerLauncher.launch("*/*")
                        }
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun MessageItemRow(
    message: RichChatMessage,
    peerName: String,
    isPlaying: Boolean,
    isDark: Boolean = true,
    onPlayAudio: (String) -> Unit
) {
    val timeFormatted = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.timestamp))

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isFromMe) Alignment.End else Alignment.Start
    ) {
        // Multi-hop Relay Badge for incoming relayed messages (⚡ Relayed via N nodes)
        if (!message.isFromMe && message.hopCount > 1) {
            Surface(
                color = Color(0x2ECA8A04),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(0.5.dp, Color(0x66FACC15)),
                modifier = Modifier.padding(bottom = 4.dp, start = 42.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Relayed",
                        tint = Color(0xFFFACC15),
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "⚡ Relayed via ${message.hopCount} mesh nodes",
                        fontSize = 10.sp,
                        color = Color(0xFFFEF08A),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Header: Time + Delivery Status Ticks (for outgoing)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 4.dp, start = 4.dp, end = 4.dp)
        ) {
            Text(
                text = timeFormatted,
                fontSize = 11.sp,
                color = Color(0xFF22C55E),
                fontWeight = FontWeight.Medium
            )

            // Delivery Status Ticks on Outgoing Messages
            if (message.isFromMe) {
                Spacer(modifier = Modifier.width(4.dp))
                when (message.status) {
                    MessageStatus.QUEUED -> {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = "Queued",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    MessageStatus.SENT -> {
                        // Single Tick ✓ (Sent over radio)
                        Text(
                            text = "✓",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    MessageStatus.DELIVERED_VIA_RELAY -> {
                        // Double Tick ✓✓ (Delivered to peer node via mesh)
                        Text(
                            text = "✓✓",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    MessageStatus.READ -> {
                        // Double Tick ✓✓ with Neon Cyan/Green Glow (Peer has opened/read message)
                        Text(
                            text = "✓✓",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF00E5FF)
                        )
                    }
                }
            }
        }

        if (message.isFromMe) {
            // Outgoing Bubble (Right, Frosted Glass)
            Surface(
                color = if (isDark) Color(0xEB2E2E3A) else Color(0xFF0284C7),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(
                    1.dp,
                    if (isDark) Brush.verticalGradient(listOf(Color(0x38FFFFFF), Color(0x0AFFFFFF)))
                    else Brush.verticalGradient(listOf(Color(0xFF38BDF8), Color(0xFF0369A1)))
                ),
                shadowElevation = if (isDark) 8.dp else 3.dp,
                modifier = Modifier.widthIn(max = 290.dp)
            ) {
                if (message.contentType == MessageContentType.AUDIO) {
                    // Walkie-Talkie Voice Note Bubble
                    VoiceNoteBubbleContent(
                        durationSeconds = message.audioDurationSeconds,
                        isPlaying = isPlaying,
                        onPlayClick = { message.mediaUri?.let { uri: String -> onPlayAudio(uri) } }
                    )
                } else {
                    Text(
                        text = message.text,
                        fontSize = 14.sp,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp)
                    )
                }
            }
        } else {
            // Incoming Bubble (Left with avatar and sender name, Picture 3 Frosted Glass)
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .shadow(6.dp, CircleShape, spotColor = Color(0x33000000))
                        .clip(CircleShape)
                        .background(if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = peerName.take(1).uppercase(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color(0xFF0F172A)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    color = if (isDark) Color(0xEB1D1D26) else Color(0xFFFFFFFF),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isDark) Brush.verticalGradient(listOf(Color(0x30FFFFFF), Color(0x06FFFFFF)))
                        else Brush.verticalGradient(listOf(Color(0xFFCBD5E1), Color(0xFFE2E8F0)))
                    ),
                    shadowElevation = if (isDark) 8.dp else 2.dp
                ) {
                    Column(modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp)) {
                        Text(
                            text = message.senderName.ifEmpty { peerName },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        if (message.contentType == MessageContentType.AUDIO) {
                            VoiceNoteBubbleContent(
                                durationSeconds = message.audioDurationSeconds,
                                isPlaying = isPlaying,
                                onPlayClick = { message.mediaUri?.let { uri: String -> onPlayAudio(uri) } }
                            )
                        } else {
                            Text(
                                text = message.text,
                                fontSize = 14.sp,
                                color = if (isDark) Color.White else Color(0xFF0F172A),
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceNoteBubbleContent(
    durationSeconds: Int,
    isPlaying: Boolean,
    onPlayClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF059669))))
                .bouncyClickable(scaleDown = 0.85f) { onPlayClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Animated or static waveform bars
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.width(100.dp).height(24.dp)
        ) {
            val barHeights = listOf(10, 18, 14, 22, 16, 20, 12, 18, 14, 24, 16, 12)
            val infiniteTransition = rememberInfiniteTransition(label = "wave")
            barHeights.forEachIndexed { index, defaultH ->
                val animatedHeight by if (isPlaying) {
                    infiniteTransition.animateFloat(
                        initialValue = 6f,
                        targetValue = defaultH.toFloat(),
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 350 + (index * 40), easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "bar_$index"
                    )
                } else {
                    remember(defaultH) { mutableFloatStateOf(defaultH.toFloat()) }
                }

                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(animatedHeight.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isPlaying) Color(0xFF22C55E) else Color(0xFF94A3B8))
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = if (durationSeconds > 0) "0:%02d".format(durationSeconds) else "0:04",
            fontSize = 12.sp,
            color = Color(0xFFE2E8F0),
            fontWeight = FontWeight.SemiBold
        )
    }
}

class AudioRecordManager(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var startTimeMs: Long = 0L

    fun startRecording(): File? {
        return try {
            val cacheFolder = File(context.cacheDir, "voice_notes").apply { mkdirs() }
            val file = File(cacheFolder, "voice_${System.currentTimeMillis()}.m4a")
            currentOutputFile = file
            startTimeMs = System.currentTimeMillis()

            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            r.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(64000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = r
            file
        } catch (e: Exception) {
            Log.e("AudioRecordManager", "Recording failed to start: ${e.message}")
            recorder = null
            null
        }
    }

    fun stopRecording(): Pair<File?, Int> {
        val r = recorder ?: return Pair(null, 0)
        return try {
            r.stop()
            r.release()
            recorder = null
            val durationSec = ((System.currentTimeMillis() - startTimeMs) / 1000L).toInt().coerceAtLeast(1)
            Pair(currentOutputFile, durationSec)
        } catch (e: Exception) {
            Log.e("AudioRecordManager", "Recording stop error: ${e.message}")
            try { r.release() } catch (_: Exception) {}
            recorder = null
            Pair(null, 0)
        }
    }

    fun cancelRecording() {
        try {
            recorder?.stop()
            recorder?.release()
        } catch (_: Exception) {}
        recorder = null
        currentOutputFile?.delete()
        currentOutputFile = null
    }
}

class AudioPlayerManager(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    var currentlyPlayingUri by mutableStateOf<String?>(null)
        private set

    fun playOrPause(uriString: String) {
        if (currentlyPlayingUri == uriString && mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
            currentlyPlayingUri = null
            return
        }

        mediaPlayer?.release()
        mediaPlayer = null

        try {
            val player = MediaPlayer()
            if (uriString.startsWith("/")) {
                player.setDataSource(uriString)
            } else {
                player.setDataSource(context, Uri.parse(uriString))
            }
            player.prepare()
            player.setOnCompletionListener {
                currentlyPlayingUri = null
            }
            player.start()
            mediaPlayer = player
            currentlyPlayingUri = uriString
        } catch (e: Exception) {
            Log.e("AudioPlayerManager", "Playback error: ${e.message}")
            currentlyPlayingUri = null
        }
    }

    fun release() {
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        currentlyPlayingUri = null
    }
}

@Composable
private fun AttachmentOption(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Surface(
            color = color.copy(alpha = 0.15f),
            shape = CircleShape,
            modifier = Modifier.size(54.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun queryFileMetadata(context: Context, uri: Uri): Pair<String, String> {
    var name = "Shared_File"
    var sizeBytes = 0L

    try {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex != -1) name = it.getString(nameIndex) ?: name
                if (sizeIndex != -1) sizeBytes = it.getLong(sizeIndex)
            }
        }
    } catch (_: Exception) {}

    val sizeFormatted = when {
        sizeBytes < 1024 -> "$sizeBytes B"
        sizeBytes < 1024 * 1024 -> "${sizeBytes / 1024} KB"
        else -> String.format("%.1f MB", sizeBytes.toDouble() / (1024 * 1024))
    }

    return Pair(name, sizeFormatted)
}

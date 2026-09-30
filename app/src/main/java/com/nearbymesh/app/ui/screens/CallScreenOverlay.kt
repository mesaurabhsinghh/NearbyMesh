package com.nearbymesh.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nearbymesh.app.core.MeshCoordinator
import com.nearbymesh.app.core.voice.CallState
import com.nearbymesh.app.ui.theme.BrandEmerald
import com.nearbymesh.app.ui.theme.BrandRed

@Composable
fun CallScreenOverlay(
    coordinator: MeshCoordinator
) {
    val activeCall by coordinator.activeCall.collectAsState()

    AnimatedVisibility(
        visible = activeCall != null,
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut() + scaleOut(targetScale = 0.9f)
    ) {
        activeCall?.let { call ->
            Dialog(
                onDismissRequest = { /* Prevent accidental dismissal during active call */ },
                properties = DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                    usePlatformDefaultWidth = false
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF0F172A),
                                    Color(0xFF1E293B),
                                    Color(0xFF0F172A)
                                )
                            )
                        )
                        .padding(horizontal = 24.dp, vertical = 40.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Top Status Bar
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(top = 20.dp)
                        ) {
                            Surface(
                                color = BrandEmerald.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(20.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BrandEmerald.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.WifiTethering,
                                        contentDescription = null,
                                        tint = BrandEmerald,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Bluetooth RFCOMM Mesh (Zero Internet)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BrandEmerald
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(28.dp))

                            Text(
                                text = call.peerName,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            val statusText = when (call.state) {
                                CallState.OUTGOING_CALLING -> "Calling via Bluetooth RFCOMM..."
                                CallState.INCOMING_RINGING -> "Incoming Offline Audio Call..."
                                CallState.CONNECTED -> {
                                    val minutes = call.durationSeconds / 60
                                    val seconds = call.durationSeconds % 60
                                    String.format("%02d:%02d • HD 16kHz Offline Audio", minutes, seconds)
                                }
                                CallState.ENDED -> "Call Ended"
                                CallState.IDLE -> ""
                            }

                            Text(
                                text = statusText,
                                fontSize = 15.sp,
                                color = if (call.state == CallState.CONNECTED) BrandEmerald else Color(0xFF94A3B8),
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Center Avatar with visual wave
                        Box(contentAlignment = Alignment.Center) {
                            // Outer pulsing glow circle
                            Box(
                                modifier = Modifier
                                    .size(180.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (call.state == CallState.CONNECTED)
                                            BrandEmerald.copy(alpha = 0.12f)
                                        else
                                            Color(0xFF3B82F6).copy(alpha = 0.12f)
                                    )
                            )

                            // Avatar Circle
                            Box(
                                modifier = Modifier
                                    .size(130.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (call.peerName.take(1).uppercase()) {
                                            "A" -> Color(0xFF1E3A8A)
                                            "B" -> Color(0xFF0D9488)
                                            "C" -> Color(0xFFB45309)
                                            "P" -> Color(0xFF7C3AED)
                                            else -> Color(0xFF047857)
                                        }
                                    )
                                    .border(
                                        3.dp,
                                        if (call.state == CallState.CONNECTED) BrandEmerald else Color(0xFF3B82F6),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = call.peerName.take(1).uppercase(),
                                    fontSize = 54.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        // Bottom Action Controls
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(bottom = 20.dp)
                        ) {
                            when (call.state) {
                                CallState.INCOMING_RINGING -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Decline Button
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            IconButton(
                                                onClick = { coordinator.rejectVoiceCall() },
                                                modifier = Modifier
                                                    .size(72.dp)
                                                    .clip(CircleShape)
                                                    .background(BrandRed)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CallEnd,
                                                    contentDescription = "Decline",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(36.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("Decline", color = Color(0xFF94A3B8), fontSize = 13.sp)
                                        }

                                        // Accept Button
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            IconButton(
                                                onClick = { coordinator.acceptVoiceCall() },
                                                modifier = Modifier
                                                    .size(72.dp)
                                                    .clip(CircleShape)
                                                    .background(BrandEmerald)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Call,
                                                    contentDescription = "Accept",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(36.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("Accept", color = Color(0xFF94A3B8), fontSize = 13.sp)
                                        }
                                    }
                                }

                                CallState.OUTGOING_CALLING -> {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        IconButton(
                                            onClick = { coordinator.hangupVoiceCall() },
                                            modifier = Modifier
                                                .size(72.dp)
                                                .clip(CircleShape)
                                                .background(BrandRed)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CallEnd,
                                                contentDescription = "Cancel",
                                                tint = Color.White,
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("End Call", color = Color(0xFF94A3B8), fontSize = 13.sp)
                                    }
                                }

                                CallState.CONNECTED -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Mute Toggle
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            IconButton(
                                                onClick = { coordinator.toggleCallMute() },
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (call.isMuted) Color(0xFFDC2626) else Color(0xFF334155)
                                                    )
                                            ) {
                                                Icon(
                                                    imageVector = if (call.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                                    contentDescription = "Mute",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(26.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = if (call.isMuted) "Muted" else "Mute",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 12.sp
                                            )
                                        }

                                        // End Call Button
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            IconButton(
                                                onClick = { coordinator.hangupVoiceCall() },
                                                modifier = Modifier
                                                    .size(72.dp)
                                                    .clip(CircleShape)
                                                    .background(BrandRed)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CallEnd,
                                                    contentDescription = "End Call",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(36.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text("End", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                        }

                                        // Speakerphone Toggle
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            IconButton(
                                                onClick = { coordinator.toggleCallSpeaker() },
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (call.isSpeakerOn) BrandEmerald else Color(0xFF334155)
                                                    )
                                            ) {
                                                Icon(
                                                    imageVector = if (call.isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                                                    contentDescription = "Speaker",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(26.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = if (call.isSpeakerOn) "Speaker" else "Earpiece",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }

                                CallState.ENDED -> {
                                    Text(
                                        text = "Call finished",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 14.sp
                                    )
                                }

                                CallState.IDLE -> {}
                            }
                        }
                    }
                }
            }
        }
    }
}

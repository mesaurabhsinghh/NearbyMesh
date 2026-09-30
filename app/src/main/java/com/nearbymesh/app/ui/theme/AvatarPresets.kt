package com.nearbymesh.app.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 20 Built-in Offline Avatars.
 * Designed for 0 MB internet: only 1 byte (avatarId 1..20) is transmitted over BLE / Mesh,
 * allowing instant local rendering on all peer devices without transferring image files!
 */
data class AvatarPreset(
    val id: Int,
    val name: String,
    val emoji: String,
    val gradient: Brush,
    val backgroundColors: List<Color>,
    val accentColor: Color
)

val AVATAR_PRESETS: List<AvatarPreset> = listOf(
    AvatarPreset(1, "Cyber Fox", "🦊", Brush.linearGradient(listOf(Color(0xFFFF5722), Color(0xFFFF9800))), listOf(Color(0xFFFF5722), Color(0xFFFF9800)), Color(0xFFFF9800)),
    AvatarPreset(2, "Cosmo Pilot", "🚀", Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF6366F1))), listOf(Color(0xFF8B5CF6), Color(0xFF6366F1)), Color(0xFFA78BFA)),
    AvatarPreset(3, "Mecha Bot", "🤖", Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF06B6D4))), listOf(Color(0xFF10B981), Color(0xFF06B6D4)), Color(0xFF34D399)),
    AvatarPreset(4, "Neon Wolf", "🐺", Brush.linearGradient(listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8))), listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8)), Color(0xFF60A5FA)),
    AvatarPreset(5, "Shadow Ninja", "🥷", Brush.linearGradient(listOf(Color(0xFF475569), Color(0xFF0F172A))), listOf(Color(0xFF475569), Color(0xFF0F172A)), Color(0xFF94A3B8)),
    AvatarPreset(6, "Bengal Tiger", "🐯", Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFD97706))), listOf(Color(0xFFF59E0B), Color(0xFFD97706)), Color(0xFFFBBF24)),
    AvatarPreset(7, "Royal Lion", "🦁", Brush.linearGradient(listOf(Color(0xFFFBBF24), Color(0xFFB45309))), listOf(Color(0xFFFBBF24), Color(0xFFB45309)), Color(0xFFFDE68A)),
    AvatarPreset(8, "Sky Falcon", "🦅", Brush.linearGradient(listOf(Color(0xFF38BDF8), Color(0xFF0284C7))), listOf(Color(0xFF38BDF8), Color(0xFF0284C7)), Color(0xFF7DD3FC)),
    AvatarPreset(9, "Mythic Dragon", "🐉", Brush.linearGradient(listOf(Color(0xFFEF4444), Color(0xFF991B1B))), listOf(Color(0xFFEF4444), Color(0xFF991B1B)), Color(0xFFF87171)),
    AvatarPreset(10, "Zen Panda", "🐼", Brush.linearGradient(listOf(Color(0xFF64748B), Color(0xFF1E293B))), listOf(Color(0xFF64748B), Color(0xFF1E293B)), Color(0xFFCBD5E1)),
    AvatarPreset(11, "Sonic Volt", "⚡", Brush.linearGradient(listOf(Color(0xFFFACC15), Color(0xFFCA8A04))), listOf(Color(0xFFFACC15), Color(0xFFCA8A04)), Color(0xFFFEF08A)),
    AvatarPreset(12, "Retro Gamer", "👾", Brush.linearGradient(listOf(Color(0xFFEC4899), Color(0xFFA855F7))), listOf(Color(0xFFEC4899), Color(0xFFA855F7)), Color(0xFFF472B6)),
    AvatarPreset(13, "Mystic Oracle", "🔮", Brush.linearGradient(listOf(Color(0xFFA855F7), Color(0xFF4C1D95))), listOf(Color(0xFFA855F7), Color(0xFF4C1D95)), Color(0xFFC084FC)),
    AvatarPreset(14, "Sound DJ", "🎧", Brush.linearGradient(listOf(Color(0xFF14B8A6), Color(0xFF0D9488))), listOf(Color(0xFF14B8A6), Color(0xFF0D9488)), Color(0xFF2DD4BF)),
    AvatarPreset(15, "Monarch Crown", "👑", Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFDAA520))), listOf(Color(0xFFFFD700), Color(0xFFDAA520)), Color(0xFFFFF080)),
    AvatarPreset(16, "Aegis Shield", "🛡️", Brush.linearGradient(listOf(Color(0xFF64748B), Color(0xFF334155))), listOf(Color(0xFF64748B), Color(0xFF334155)), Color(0xFF94A3B8)),
    AvatarPreset(17, "Deep Wave", "🌊", Brush.linearGradient(listOf(Color(0xFF0EA5E9), Color(0xFF0369A1))), listOf(Color(0xFF0EA5E9), Color(0xFF0369A1)), Color(0xFF38BDF8)),
    AvatarPreset(18, "Sniper Target", "🎯", Brush.linearGradient(listOf(Color(0xFFF43F5E), Color(0xFFBE123C))), listOf(Color(0xFFF43F5E), Color(0xFFBE123C)), Color(0xFFFB7185)),
    AvatarPreset(19, "Diamond Star", "💎", Brush.linearGradient(listOf(Color(0xFF2DD4BF), Color(0xFF0284C7))), listOf(Color(0xFF2DD4BF), Color(0xFF0284C7)), Color(0xFF5EEAD4)),
    AvatarPreset(20, "Saturn Orbit", "🪐", Brush.linearGradient(listOf(Color(0xFFC084FC), Color(0xFF7E22CE))), listOf(Color(0xFFC084FC), Color(0xFF7E22CE)), Color(0xFFE9D5FF))
)

fun getAvatarPreset(avatarId: Int): AvatarPreset {
    val cleanId = if (avatarId in 1..20) avatarId else 1
    return AVATAR_PRESETS.firstOrNull { it.id == cleanId } ?: AVATAR_PRESETS[0]
}

/**
 * Universal Avatar Bubble Component.
 * Displays rich gradient disk, emoji symbol, and optional border.
 */
@Composable
fun AvatarBubble(
    avatarId: Int,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    showBorder: Boolean = true,
    borderColor: Color = Color(0x55FFFFFF),
    borderWidth: Dp = 1.5.dp,
    onClick: (() -> Unit)? = null
) {
    val preset = getAvatarPreset(avatarId)
    val clickModifier = if (onClick != null) {
        modifier.clickable { onClick() }
    } else modifier

    Box(
        modifier = clickModifier
            .size(size)
            .shadow(6.dp, CircleShape, spotColor = preset.accentColor.copy(alpha = 0.5f))
            .clip(CircleShape)
            .background(preset.gradient)
            .then(
                if (showBorder) Modifier.border(borderWidth, borderColor, CircleShape) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = preset.emoji,
            fontSize = (size.value * 0.52f).sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Bottom Sheet modal displaying all 20 built-in avatars in an elegant grid.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarPickerSheet(
    selectedId: Int,
    onSelectAvatar: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF121218),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0x44FFFFFF))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Choose Your Avatar",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "20 Built-in offline identities (0 MB Internet)",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 4-Column Grid for 20 Avatars
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(AVATAR_PRESETS) { preset ->
                    val isSelected = preset.id == selectedId || (selectedId <= 0 && preset.id == 1)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) Color(0x2238BDF8) else Color(0x0EFFFFFF))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) Color(0xFF38BDF8) else Color(0x1AFFFFFF),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                onSelectAvatar(preset.id)
                                onDismiss()
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            AvatarBubble(
                                avatarId = preset.id,
                                size = 52.dp,
                                showBorder = isSelected,
                                borderColor = Color(0xFF38BDF8)
                            )
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .align(Alignment.BottomEnd)
                                        .offset(x = 2.dp, y = 2.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF38BDF8)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.Black,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = preset.name,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color(0xFF38BDF8) else Color(0xFFE2E8F0),
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

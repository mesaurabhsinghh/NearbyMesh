package com.nearbymesh.app.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Glassmorphism Color Palettes (Rich Frosted Glass & Satin Dark Metals)
val GlassDarkBase = Color(0xCC16161C)
val GlassDarkCard = Color(0xBF181820)
val GlassDarkInput = Color(0xCC1A1A22)
val GlassPillBar = Color(0xDE1A1A20)

val GlassBorderTop = Color(0x40FFFFFF)
val GlassBorderBottom = Color(0x0DFFFFFF)
val GlassBorderSubtle = Color(0x20FFFFFF)

val GlassHighlightGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0x35FFFFFF),
        Color(0x08FFFFFF)
    )
)

val GlassSurfaceGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xE61E1E26),
        Color(0xCC121216)
    )
)

val GlassAccentBlueGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFF2563EB),
        Color(0xFF1D4ED8),
        Color(0xFF0F172A)
    )
)

val GlassDiscOuterGradient = Brush.radialGradient(
    colors = listOf(
        Color(0xFF222228),
        Color(0xFF16161B),
        Color(0xFF0C0C0E)
    )
)

val GlassDiscInnerGradient = Brush.radialGradient(
    colors = listOf(
        Color(0xFF2A2A32),
        Color(0xFF1E1E24),
        Color(0xFF141418)
    )
)

val EnterButtonGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFFFFFFF),
        Color(0xFFEDEDF2),
        Color(0xFFD6D6DF)
    )
)

/**
 * Modifier extension for true Frosted Glassmorphism with refractive specular rim lighting
 */
fun Modifier.glassmorphic(
    shape: Shape = RoundedCornerShape(24.dp),
    backgroundColor: Color = GlassDarkCard,
    borderBrush: Brush = GlassHighlightGradient,
    borderWidth: Dp = 1.dp,
    elevation: Dp = 12.dp
): Modifier = this
    .shadow(elevation = elevation, shape = shape, spotColor = Color(0x66000000), ambientColor = Color(0x33000000))
    .clip(shape)
    .background(backgroundColor)
    .border(BorderStroke(borderWidth, borderBrush), shape)

/**
 * High-performance springy tactile bounce modifier for buttons and cards.
 * Shrinks elastically on press down, springs back on release.
 */
@Composable
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    scaleDown: Float = 0.93f,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scaleState = animateFloatAsState(
        targetValue = if (isPressed && enabled) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = 600f
        ),
        label = "BouncyClickableScale"
    )
    val alphaState = animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = 600f
        ),
        label = "BouncyClickableAlpha"
    )
    return this
        .graphicsLayer {
            scaleX = scaleState.value
            scaleY = scaleState.value
            alpha = alphaState.value
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}


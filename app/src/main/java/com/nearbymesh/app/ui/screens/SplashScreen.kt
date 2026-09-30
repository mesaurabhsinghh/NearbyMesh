package com.nearbymesh.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nearbymesh.app.ui.theme.*

@Composable
fun SplashScreen(onGetStarted: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "SplashGlobePulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.weight(0.8f))

            // Glowing Cosmic Mesh Globe
            Box(
                modifier = Modifier
                    .size(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = size.width / 2.2f

                    // Outer glowing aura
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(BrandCyanGlow.copy(alpha = 0.25f * pulseAlpha), Color.Transparent),
                            center = center,
                            radius = radius * 1.3f
                        ),
                        radius = radius * 1.3f,
                        center = center
                    )

                    // Core globe
                    drawCircle(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFF0F2B48), Color(0xFF05111E)),
                            start = Offset(0f, 0f),
                            end = Offset(size.width, size.height)
                        ),
                        radius = radius,
                        center = center
                    )

                    // Latitudinal/Longitudinal mesh lines
                    drawCircle(
                        color = BrandCyanGlow.copy(alpha = 0.4f),
                        radius = radius,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawOval(
                        color = BrandEmeraldGlow.copy(alpha = 0.3f),
                        topLeft = Offset(center.x - radius, center.y - radius * 0.4f),
                        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 0.8f),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                    drawOval(
                        color = BrandEmeraldGlow.copy(alpha = 0.3f),
                        topLeft = Offset(center.x - radius * 0.4f, center.y - radius),
                        size = androidx.compose.ui.geometry.Size(radius * 0.8f, radius * 2),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }

                // Central glowing Logo Mark
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.nearbymesh.app.R.drawable.app_logo),
                    contentDescription = "Logo",
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // App Name & Tagline
            Text(
                text = "NearbyMesh",
                color = DarkTextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Connect Beyond Internet",
                color = BrandEmeraldGlow,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(36.dp))

            // 3 Key Feature Badges (Matching Image 2 & 3)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FeatureBadge(icon = Icons.Default.CloudOff, title = "No Internet\nNeeded")
                FeatureBadge(icon = Icons.Default.Shield, title = "Private &\nSecure")
                FeatureBadge(icon = Icons.Default.Share, title = "Share Everything\nNearby")
            }

            Spacer(modifier = Modifier.weight(1f))

            // Get Started Button
            Button(
                onClick = onGetStarted,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandEmeraldGlow,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Get Started",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "People. Devices. Closer.",
                color = DarkTextMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun FeatureBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            color = DarkSurfaceSubtle,
            shape = CircleShape,
            modifier = Modifier.size(46.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = BrandEmeraldGlow,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            color = DarkTextSecondary,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp
        )
    }
}

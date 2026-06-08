package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Custom glass container with Vista Windows glass glow, glossy angle glare reflections,
 * and high-contrast double borders. Gives the ultimate Frutiger Aero feeling!
 */
@Composable
fun AeroGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 3.dp,
    glowColor: Color = Color(0x303AD1FF),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(cornerRadius),
                clip = false,
                ambientColor = glowColor,
                spotColor = Color.Black
            )
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0x59FFFFFF),  // White 35% top glass fill
                        Color(0x1F9FC7EE),  // Soft translucent sky tint
                        Color(0x0EFFFFFF)   // Low opacity bottom
                    )
                ),
                shape = RoundedCornerShape(cornerRadius)
            )
            .drawBehind {
                val r = cornerRadius.toPx()
                // 1. Draw shiny glare sweep (Aero highlight reflection)
                val glareBrush = Brush.linearGradient(
                    colors = listOf(
                        Color(0x66FFFFFF),
                        Color(0x19FFFFFF),
                        Color(0x00FFFFFF)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height * 0.5f)
                )
                drawRect(
                    brush = glareBrush,
                    size = Size(size.width, size.height * 0.5f)
                )

                // 2. High-precision borders representing 1px solid rgba(255,255,255,0.4)
                drawRoundRect(
                    color = Color(0x66FFFFFF),
                    size = size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                    style = Stroke(width = 2f)
                )

                // 3. Inset 0 1px 0 rgba(255,255,255,0.5) top glare edge reflection
                drawLine(
                    color = Color(0x80FFFFFF),
                    start = Offset(r, 1f),
                    end = Offset(size.width - r, 1f),
                    strokeWidth = 2f
                )

                // Inner dark shadow contrast line to accentuate depth
                drawRoundRect(
                    color = Color(0x15000000),
                    size = size.copy(width = size.width - 4, height = size.height - 4),
                    topLeft = Offset(2f, 2f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r - 2, r - 2),
                    style = Stroke(width = 1f)
                )
            }
            .clip(RoundedCornerShape(cornerRadius)),
        content = content
    )
}

/**
 * Windows Media Player style circular glow-sphere Play/Pause center Orb!
 * High bevel 3D look with reflective specular bubbles.
 */
@Composable
fun PlayPauseOrb(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(68.dp)
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    // Smooth hover pulsing animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseGlow"
    )

    val hoverScale by animateFloatAsState(
        targetValue = if (isHovered) 1.08f else 1.0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
        label = "scale"
    )

    Box(
        modifier = modifier
            .testTag("play_pause_button")
            .size((68 * hoverScale).dp)
            .shadow(
                elevation = 12.dp,
                shape = CircleShape,
                ambientColor = Color(0xFF0078D7),
                spotColor = Color.Black
            )
            .background(
                brush = Brush.verticalGradient(
                    colors = if (isPlaying) {
                        listOf(
                            Color(0xFF70F0FF), // Glowing cobalt-teal top reflection
                            Color(0xFF0078D7), // Active vibrant blue
                            Color(0xFF004780), // Deep cobalt shadow
                            Color(0xFF001533)  // Rim shadow
                        )
                    } else {
                        listOf(
                            Color(0xFF99E2FF), // Vibrant glass top reflection
                            Color(0xFF0088FF), // Standby rich blue
                            Color(0xFF00529C), // Mid blue shadow
                            Color(0xFF002142)  // Rim shadow
                        )
                    }
                ),
                shape = CircleShape
            )
            .border(2.dp, Color(0x99FFFFFF), CircleShape) // Thin high-specular glass rim
            .drawBehind {
                val r = size.width / 2f
                // 1. Draw shiny glossy bubble highlight on top half (windows aero bubble highlight)
                val highlightBrush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xAAFFFFFF), // Strong top glare
                        Color(0x33FFFFFF), // Translucent mid
                        Color(0x00FFFFFF)  // Fades out at equator
                    ),
                    startY = 0f,
                    endY = size.height * 0.5f
                )
                drawArc(
                    brush = highlightBrush,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = true,
                    size = size.copy(width = size.width - 4f, height = size.height * 0.9f),
                    topLeft = Offset(2f, 2f)
                )

                // 2. Clear inner reflection highlight ring
                drawCircle(
                    color = Color(0x66FFFFFF),
                    radius = r - 2.dp.toPx(),
                    style = Stroke(width = 1.dp.toPx())
                )

                // 3. Subtle pulse ring if active
                if (isPlaying) {
                    drawCircle(
                        color = Color(0x4000C8FF),
                        radius = r * (1f + (pulseGlow - 1f) * 0.15f),
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(bounded = false, radius = 34.dp, color = Color.White),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (isPlaying) "Pausa" else "Reproducir",
            modifier = Modifier.size(32.dp),
            tint = Color(0xFFFFFFFF)
        )
    }
}

/**
 * Shiny Aero previous/next orbital navigation button.
 */
@Composable
fun AeroMediaNavButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier.size(44.dp)
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val backgroundBrush = if (isHovered) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0x90FFFFFF), // 55% white top gloss
                Color(0x35FFFFFF), // 20% white mid-top
                Color(0x1A000000), // 10% black bottom-divider
                Color(0x4DFFFFFF)  // 30% white reflection base
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0x75FFFFFF), // 45% white top gloss
                Color(0x22FFFFFF), // 13% white mid-top
                Color(0x0E000000), // 5% black bottom-divider
                Color(0x35FFFFFF)  // 20% white reflection base
            )
        )
    }

    Box(
        modifier = modifier
            .testTag(tag)
            .shadow(4.dp, CircleShape, ambientColor = Color(0x400078D7))
            .border(1.dp, Color(0x33FFFFFF), CircleShape) // thin white border-white/20
            .background(brush = backgroundBrush, shape = CircleShape)
            .drawBehind {
                val r = size.width / 2f
                // Draw 1px inset white reflection ring: inset 0 0 0 1px rgba(255,255,255,0.3)
                drawCircle(
                    color = Color(0x4DFFFFFF),
                    radius = r - 1.dp.toPx(),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(bounded = false, radius = 22.dp, color = Color.White),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = Color.White
        )
    }
}

/**
 * Windows Media Player style dynamic volume sliver bar component!
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AudioVolumeTrack(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
        .width(100.dp)
        .height(30.dp)
) {
    Box(
        modifier = modifier
            .drawBehind {
                // Background sliver (Vista metallic gray triangle slider)
                val borderPath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, size.height - 4f)
                    lineTo(size.width, 4f)
                    lineTo(size.width, size.height - 4f)
                    close()
                }
                // Background sliver (Vista metallic silver-gray triangle slider)
                drawPath(
                    path = borderPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFD1D1D1), // Silver metallic edge top
                            Color(0xFFF0F0F0), // Ultra-light silver body
                            Color(0xFFC8C8C8)  // Medium silver base shadow
                        )
                    )
                )

                // Fill sliver (Vista glossy green filled volume level)
                val fillPath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, size.height - 4f)
                    lineTo(size.width * volume, size.height - (size.height - 8f) * volume - 4f)
                    lineTo(size.width * volume, size.height - 4f)
                    close()
                }
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFB4E391), // Light lime-green specular highlight
                            Color(0xFF70C670), // Lively, bright green body
                            Color(0xFF51A651), // Saturated forest green mid-range
                            Color(0xFF409040)  // Dark deep grass base anchor
                        )
                    )
                )

                // Outer glassy frame representing 1px solid #999 and inset 3D reflections
                drawPath(
                    path = borderPath,
                    color = Color(0x66000000), // thin dark border for professional high contrast
                    style = Stroke(width = 1f.dp.toPx())
                )
                drawPath(
                    path = borderPath,
                    color = Color(0x7FFFFFFF), // shiny inner highlight
                    style = Stroke(width = 0.5f.dp.toPx())
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* Change volume based on tapped width */ },
        contentAlignment = Alignment.Center
    ) {
        // We present a transparent slider to catch gestures
        androidx.compose.material3.Slider(
            value = volume,
            onValueChange = onVolumeChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp),
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = Color.Transparent, // Invisible so the custom draw handles thumb,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent
            ),
            thumb = {
                // Glossy circular thumb slider
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .shadow(3.dp, CircleShape)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFFFFFFFF), Color(0xFF9FC2D8))
                            ),
                            shape = CircleShape
                        )
                        .border(1.dp, Color(0xFF1A5A90), CircleShape)
                )
            }
        )
    }
}

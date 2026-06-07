package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Track
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A highly authentic, spinning holographic CD-ROM visualizer with reflective metallic shine!
 * Matches the classic Windows Media Player album art placeholder.
 */
@Composable
fun AlbumArtView(
    track: Track?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier.size(200.dp)
) {
    // Rotation state: spins continuously when track is actively playing
    val transition = rememberInfiniteTransition(label = "cdRot")
    val rotationAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val currentRotation = if (isPlaying) rotationAngle else 0f

    // Subtle breathing scale animation
    val scalePulse by transition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = SineBehavior() /* Easing */),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing"
    )
    val useScale = if (isPlaying) scalePulse else 1.0f

    Box(
        modifier = modifier
            .size((200 * useScale).dp)
            .shadow(12.dp, CircleShape, ambientColor = Color(0xFF00FFEA))
            .background(Color(0xFF010E1C), CircleShape)
            .border(2.dp, Color(0xFF326B9C), CircleShape)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        // Spinning Disc Layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .rotate(currentRotation)
                .drawBehind {
                    val radius = size.width / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)

                    // 1. Draw silver metallic concentric rings
                    for (r in 1..8) {
                        val alpha = (31 - r * 2).coerceAtLeast(5)
                        drawCircle(
                            color = Color(255, 255, 255, alpha), // concentric silver reflections
                            radius = radius * (1.0f - r * 0.08f),
                            style = Stroke(width = 1.5f)
                        )
                    }

                    // 2. Holographic rainbow reflection sweeps (the CD sheen!)
                    val sweep1 = Brush.sweepGradient(
                        colors = listOf(
                            Color(0x00FF0000),
                            Color(0x40FF0000), // Red
                            Color(0x40FFFF00), // Yellow
                            Color(0x4000FF00), // Green
                            Color(0x4000FFFF), // Cyan
                            Color(0x400000FF), // Blue
                            Color(0x40FF00FF), // Violet
                            Color(0x00FF0000)
                        ),
                        center = center
                    )
                    drawCircle(
                        brush = sweep1,
                        radius = radius * 0.95f
                    )

                    // Additional sheen reflecting at 90 degrees offset
                    val sweep2 = Brush.sweepGradient(
                        colors = listOf(
                            Color(0x0000FFFF),
                            Color(0x3500FFAA),
                            Color(0x0000FFFF)
                        )
                    )
                    drawCircle(
                        brush = sweep2,
                        radius = radius * 0.85f
                    )

                    // 3. Central media hub (transparent plastic spacer and dark ring placeholder)
                    drawCircle(
                        color = Color(0x359FC0DE),
                        radius = radius * 0.35f
                    )
                    drawCircle(
                        color = Color(0x95031326),
                        radius = radius * 0.30f
                    )
                    drawCircle(
                        color = Color(0xEE010A14),
                        radius = radius * 0.18f
                    )
                    drawCircle(
                        color = Color(0x40FFFFFF),
                        radius = radius * 0.18f,
                        style = Stroke(width = 2f)
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Label art in the middle portion of the CD
            Box(
                modifier = Modifier
                    .fillMaxSize(0.55f)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0D3256),
                                Color(0xFF031427)
                            )
                        )
                    )
                    .border(2.dp, Color(0xFF438CD0), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Windows Aero glowing vector bubbles in the CD sticker
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color(0xFF00C3FF), Color.Transparent)
                                ),
                                radius = size.width * 0.6f,
                                center = Offset(size.width * 0.2f, size.height * 0.3f)
                            )
                            drawCircle(
                                color = Color(0x2EFFFFFF),
                                radius = size.width * 0.22f,
                                center = Offset(size.width * 0.8f, size.height * 0.7f)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = Color(0xFF53BCFF)
                    )
                }
            }
        }

        // CD Case / Jewel Box Glare SwipeOverlay (static highlight on top of CD, gives depth)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    // Glass highlight reflection
                    drawArc(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0x55FFFFFF), Color(0x05FFFFFF), Color.Transparent)
                        ),
                        startAngle = 210f,
                        sweepAngle = 120f,
                        useCenter = false,
                        style = Stroke(width = 6f)
                    )
                    drawArc(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0x36FFFFFF), Color.Transparent),
                            start = Offset(0f, 0f),
                            end = Offset(size.width, size.height)
                        ),
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = true
                    )
                }
        )
    }
}

// Simple helper to emulate smooth sine easing
private fun SineBehavior(): Easing {
    return Easing { fraction ->
        sin(fraction * PI / 2.0).toFloat()
    }
}

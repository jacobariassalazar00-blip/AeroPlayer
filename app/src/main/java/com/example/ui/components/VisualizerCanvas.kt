package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.math.cos
import kotlin.random.Random

@Composable
fun VisualizerCanvas(
    isPlaying: Boolean,
    modifier: Modifier = Modifier.height(140.dp).fillMaxWidth()
) {
    // We simulate 18 bars (frequencies)
    val barCount = 18
    val heights = remember { mutableStateListOf<Float>().apply { repeat(barCount) { add(0.1f) } } }
    val peaks = remember { mutableStateListOf<Float>().apply { repeat(barCount) { add(0.1f) } } }

    // Procedural spectrum generator
    LaunchedEffect(isPlaying) {
        var cycle = 0f
        while (true) {
            if (isPlaying) {
                cycle += 0.2f
                for (i in 0 until barCount) {
                    // Combine complex waves to represent simulated audio spectrum
                    val baseWave = sin(cycle + i * 0.5f) * 0.4f + 0.5f
                    val noiseWave = cos(cycle * 1.7f - i * 0.9f) * 0.2f + 0.3f
                    val highFreqAttenuation = 1f - (i.toFloat() / barCount) * 0.4f
                    val targetVal = ((baseWave + noiseWave) * highFreqAttenuation).coerceIn(0.05f, 0.95f)

                    // Smooth transition to target
                    heights[i] = heights[i] * 0.6f + targetVal * 0.4f

                    // Handle peaking mechanics
                    if (heights[i] > peaks[i]) {
                        peaks[i] = heights[i]
                    } else {
                        // Slowly decay peak value with gravity
                        peaks[i] = (peaks[i] - 0.02f).coerceAtLeast(heights[i])
                    }
                }
            } else {
                // Slowly decay visualizer bars to flat/idle line
                for (i in 0 until barCount) {
                    heights[i] = heights[i] * 0.82f + 0.02f * heights[i]
                    peaks[i] = (peaks[i] - 0.03f).coerceAtLeast(heights[i])
                }
            }
            delay(50) // 20 frames per second smooth flow
        }
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val spacing = 6f
        val totalSpacing = spacing * (barCount - 1)
        val barWidth = (width - totalSpacing) / barCount

        // 1. Draw glowing background grid lines (typical of Windows Media Player skin)
        val gridLines = 5
        val gridColor = Color(0x153AD1FF)
        for (g in 1..gridLines) {
            val y = height * (g.toFloat() / (gridLines + 1L))
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 2f
            )
        }

        // 2. Draw bars & peak dots
        for (i in 0 until barCount) {
            val barHeight = heights[i] * height
            val x = i * (barWidth + spacing)
            val y = height - barHeight

            // Color grandients: Lime green at bottom, glowing cyan/neon blue at top
            val barBrush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF00FFE0), // Neon Cyan (Top)
                    Color(0xFF00D150), // Lime Green (Mid)
                    Color(0xFF00561B)  // Forest Green (Bottom)
                ),
                startY = y,
                endY = height
            )

            // Equalizer blocks: divide bars into stacked discrete blocks for an authentic retro feel!
            val blockHeight = 12f
            val blockSpacing = 3f
            var currentY = height
            while (currentY > y) {
                val currentBlockHeight = blockHeight.coerceAtMost(currentY - y)
                drawRect(
                    brush = barBrush,
                    topLeft = Offset(x, currentY - currentBlockHeight - blockSpacing),
                    size = Size(barWidth, currentBlockHeight)
                )
                currentY -= (blockHeight + blockSpacing)
            }

            // Draw floating peak dot (Iconic Windows Media Player detail!)
            val peakY = height - (peaks[i] * height)
            drawRect(
                color = Color(0xFF00FFEA),
                topLeft = Offset(x, (peakY - 4f).coerceAtLeast(0f)),
                size = Size(barWidth, 6f)
            )
        }
    }
}

package com.heartmonitor.app.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.heartmonitor.app.presentation.theme.OrangePrimary
import com.heartmonitor.app.presentation.theme.RedWarning
import com.heartmonitor.app.presentation.theme.TextTertiary

@Composable
fun SignalWaveform(
    signalData: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color.Black,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    showGrid: Boolean = true,
    playheadPosition: Float? = null
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val width = size.width
            val height = size.height
            val centerY = height / 2
            
            // Draw grid
            if (showGrid) {
                val gridColor = TextTertiary.copy(alpha = 0.2f)
                
                // Horizontal grid lines
                for (i in 0..4) {
                    val y = height * i / 4
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                
                // Vertical grid lines
                val verticalLines = 10
                for (i in 0..verticalLines) {
                    val x = width * i / verticalLines
                    drawLine(
                        color = gridColor,
                        start = Offset(x, 0f),
                        end = Offset(x, height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
            
            // Draw signal
            if (signalData.isNotEmpty()) {
                val path = Path()
                val xStep = width / (signalData.size - 1).coerceAtLeast(1)
                
                // Normalize signal data
                val maxAbs = signalData.maxOfOrNull { kotlin.math.abs(it) } ?: 1f
                val normalizedData = if (maxAbs > 0) {
                    signalData.map { it / maxAbs }
                } else {
                    signalData
                }
                
                normalizedData.forEachIndexed { index, value ->
                    val x = index * xStep
                    val y = centerY - (value * centerY * 0.8f) // Scale to 80% of half height
                    
                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }
                
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
            
            // Draw playhead
            playheadPosition?.let { position ->
                val x = width * position
                drawLine(
                    color = OrangePrimary,
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center) // ✅ works now
                .width(2.dp)
                .fillMaxHeight()
                .background(Color.Red)
        )
    }
}

@Composable
fun LiveSignalWaveform(
    signalData: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color.Black
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2
        
        // Draw center line
        drawLine(
            color = TextTertiary.copy(alpha = 0.3f),
            start = Offset(0f, centerY),
            end = Offset(width, centerY),
            strokeWidth = 1.dp.toPx()
        )
        
        if (signalData.isNotEmpty()) {
            val path = Path()
            val displayPoints = signalData.takeLast(500)
            val xStep = width / (displayPoints.size - 1).coerceAtLeast(1)
            
            val maxAbs = displayPoints.maxOfOrNull { kotlin.math.abs(it) }?.coerceAtLeast(0.1f) ?: 1f
            
            displayPoints.forEachIndexed { index, value ->
                val x = index * xStep
                val normalizedValue = value / maxAbs
                val y = centerY - (normalizedValue * centerY * 0.8f)
                
                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
            
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}

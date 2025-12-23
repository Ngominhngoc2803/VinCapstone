package com.heartmonitor.app.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.heartmonitor.app.presentation.theme.ChartRed
import com.heartmonitor.app.presentation.theme.ChartRedLight
import com.heartmonitor.app.presentation.viewmodel.BpmDataPoint

@Composable
fun BpmChart(
    dataPoints: List<BpmDataPoint>,
    avgBpm: Int,
    maxBpm: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        // Chart
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            if (dataPoints.isNotEmpty()) {
                BpmChartCanvas(
                    dataPoints = dataPoints,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No data available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Avg Heart Rate",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$avgBpm bpm",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Max Heart Rate",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$maxBpm bpm",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun BpmChartCanvas(
    dataPoints: List<BpmDataPoint>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (dataPoints.isEmpty()) return@Canvas
        
        val width = size.width
        val height = size.height
        val padding = 40f
        
        val chartWidth = width - padding * 2
        val chartHeight = height - padding * 2
        
        // Find min/max values
        val minBpm = (dataPoints.minOfOrNull { it.bpm } ?: 60f) - 10f
        val maxBpm = (dataPoints.maxOfOrNull { it.bpm } ?: 140f) + 10f
        val bpmRange = maxBpm - minBpm
        
        val minTime = dataPoints.minOfOrNull { it.timeSeconds } ?: 0f
        val maxTime = dataPoints.maxOfOrNull { it.timeSeconds } ?: 30f
        val timeRange = maxTime - minTime
        
        // Draw horizontal grid lines
        val gridLineColor = Color.Gray.copy(alpha = 0.2f)
        val gridLines = 5
        for (i in 0..gridLines) {
            val y = padding + (chartHeight * i / gridLines)
            drawLine(
                color = gridLineColor,
                start = Offset(padding, y),
                end = Offset(width - padding, y),
                strokeWidth = 1f
            )
        }
        
        // Draw average line (dashed)
        val avgBpm = dataPoints.map { it.bpm }.average().toFloat()
        val avgY = padding + chartHeight * (1 - (avgBpm - minBpm) / bpmRange)
        drawLine(
            color = Color.Gray.copy(alpha = 0.5f),
            start = Offset(padding, avgY),
            end = Offset(width - padding, avgY),
            strokeWidth = 2f,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                floatArrayOf(10f, 10f)
            )
        )
        
        // Create path for the chart line
        val linePath = Path()
        val fillPath = Path()
        
        dataPoints.forEachIndexed { index, point ->
            val x = padding + chartWidth * ((point.timeSeconds - minTime) / timeRange)
            val y = padding + chartHeight * (1 - (point.bpm - minBpm) / bpmRange)
            
            if (index == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, height - padding)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        
        // Complete fill path
        val lastPoint = dataPoints.lastOrNull()
        if (lastPoint != null) {
            val lastX = padding + chartWidth * ((lastPoint.timeSeconds - minTime) / timeRange)
            fillPath.lineTo(lastX, height - padding)
            fillPath.close()
        }
        
        // Draw filled area with gradient
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    ChartRed.copy(alpha = 0.5f),
                    ChartRedLight.copy(alpha = 0.1f)
                )
            ),
            style = Fill
        )
        
        // Draw line
        drawPath(
            path = linePath,
            color = ChartRed,
            style = Stroke(width = 3f)
        )
        
        // Draw data points
        dataPoints.forEach { point ->
            val x = padding + chartWidth * ((point.timeSeconds - minTime) / timeRange)
            val y = padding + chartHeight * (1 - (point.bpm - minBpm) / bpmRange)
            
            drawCircle(
                color = ChartRed,
                radius = 4f,
                center = Offset(x, y)
            )
        }
    }
}

@Composable
fun BpmChartWithLabels(
    dataPoints: List<BpmDataPoint>,
    avgBpm: Int,
    maxBpm: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        SectionHeader(
            title = "Heart rate (BPM)",
            showDropdown = true
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
        ) {
            // Y-axis labels
            Column(
                modifier = Modifier
                    .width(40.dp)
                    .fillMaxHeight()
                    .padding(vertical = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                val minBpm = (dataPoints.minOfOrNull { it.bpm }?.toInt() ?: 60) - 10
                val maxBpmVal = (dataPoints.maxOfOrNull { it.bpm }?.toInt() ?: 140) + 10
                
                listOf(maxBpmVal, (maxBpmVal + minBpm) / 2, minBpm).forEach { value ->
                    Text(
                        text = "$value",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Chart
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 44.dp)
            ) {
                BpmChart(
                    dataPoints = dataPoints,
                    avgBpm = avgBpm,
                    maxBpm = maxBpm,
                    modifier = Modifier.weight(1f)
                )
                
                // X-axis labels
                if (dataPoints.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val minTime = dataPoints.minOfOrNull { it.timeSeconds }?.toInt() ?: 0
                        val maxTime = dataPoints.maxOfOrNull { it.timeSeconds }?.toInt() ?: 30
                        val step = (maxTime - minTime) / 5
                        
                        (0..5).forEach { i ->
                            val timeValue = minTime + step * i
                            val minutes = timeValue / 60
                            val seconds = timeValue % 60
                            Text(
                                text = if (minutes > 0) "$minutes:${seconds.toString().padStart(2, '0')}" else "$seconds:00",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

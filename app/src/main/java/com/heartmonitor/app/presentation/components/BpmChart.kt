package com.heartmonitor.app.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.heartmonitor.app.presentation.viewmodel.BpmHistoryPoint
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import android.graphics.Paint
import androidx.compose.ui.graphics.nativeCanvas
import com.heartmonitor.app.presentation.viewmodel.BpmDataPoint
import kotlin.math.ceil

@Composable
fun BpmChart(
    points: List<BpmHistoryPoint>,
    modifier: Modifier = Modifier
) {
    val sorted = remember(points) { points.sortedBy { it.date } }
    val avgAll = remember(sorted) {
        if (sorted.isEmpty()) 0f else sorted.map { it.bpm }.average().toFloat()
    }
    val maxAll = remember(sorted) { sorted.maxOfOrNull { it.bpm }?.roundToInt() ?: 0 }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
        ) {
            if (sorted.isNotEmpty()) {
                BpmHistoryChartCanvas(
                    points = sorted,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No recordings yet",
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
                    text = "Avg BPM (all recordings)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${avgAll.roundToInt()} bpm",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Max BPM (all recordings)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$maxAll bpm",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // X-axis labels (first / mid / last date)
        if (sorted.size >= 2) {
            Spacer(modifier = Modifier.height(8.dp))
            val fmt = remember { DateTimeFormatter.ofPattern("MM/dd") }
            val first = sorted.first().date.format(fmt)
            val mid = sorted[sorted.size / 2].date.format(fmt)
            val last = sorted.last().date.format(fmt)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(first, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(mid, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(last, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BpmHistoryChartCanvas(
    points: List<BpmHistoryPoint>,
    modifier: Modifier = Modifier
) {
    val fmt = remember { DateTimeFormatter.ofPattern("MM/dd") }

    Canvas(modifier = modifier) {
        if (points.isEmpty()) return@Canvas

        val width = size.width
        val height = size.height

        // Add extra space at bottom for x-axis labels
        val paddingLeft = 40f
        val paddingRight = 20f
        val paddingTop = 20f
        val paddingBottom = 40f   // <-- important for dates

        val chartWidth = width - paddingLeft - paddingRight
        val chartHeight = height - paddingTop - paddingBottom

        // Y-range
        val minBpm = (points.minOfOrNull { it.bpm } ?: 60f) - 10f
        val maxBpm = (points.maxOfOrNull { it.bpm } ?: 140f) + 10f
        val bpmRange = (maxBpm - minBpm).coerceAtLeast(1f)

        // Grid lines
        val gridLineColor = Color.Gray.copy(alpha = 0.2f)
        val gridLines = 5
        for (i in 0..gridLines) {
            val y = paddingTop + (chartHeight * i / gridLines)
            drawLine(
                color = gridLineColor,
                start = Offset(paddingLeft, y),
                end = Offset(width - paddingRight, y),
                strokeWidth = 1f
            )
        }

        // Avg dashed line
        val avg = points.map { it.bpm }.average().toFloat()
        val avgY = paddingTop + chartHeight * (1f - (avg - minBpm) / bpmRange)
        drawLine(
            color = Color.Gray.copy(alpha = 0.5f),
            start = Offset(paddingLeft, avgY),
            end = Offset(width - paddingRight, avgY),
            strokeWidth = 2f,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
        )

        // X positions (even spacing)
        val n = points.size
        fun xFor(i: Int): Float =
            if (n == 1) paddingLeft + chartWidth / 2f
            else paddingLeft + chartWidth * (i.toFloat() / (n - 1).toFloat())

        fun yFor(bpm: Float): Float =
            paddingTop + chartHeight * (1f - (bpm - minBpm) / bpmRange)

        // Paths
        val linePath = Path()
        val fillPath = Path()

        points.forEachIndexed { i, p ->
            val x = xFor(i)
            val y = yFor(p.bpm)

            if (i == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, paddingTop + chartHeight) // bottom of chart area
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }

        // close fill
        val lastX = xFor(n - 1)
        fillPath.lineTo(lastX, paddingTop + chartHeight)
        fillPath.close()

        // Fill + line
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

        drawPath(
            path = linePath,
            color = ChartRed,
            style = Stroke(width = 3f)
        )

        // dots
        points.forEachIndexed { i, p ->
            drawCircle(
                color = ChartRed,
                radius = 4f,
                center = Offset(xFor(i), yFor(p.bpm))
            )
        }

        // ✅ X-axis date labels
        val labelPaint = Paint().apply {
            isAntiAlias = true
            textSize = 28f
            color = android.graphics.Color.GRAY
            textAlign = Paint.Align.CENTER
        }

        // If too many points, reduce labels so it doesn't become messy
        val maxLabels = 6
        val step = if (n <= maxLabels) 1 else ceil(n / maxLabels.toFloat()).toInt()

        val yLabel = paddingTop + chartHeight + 32f

        drawContext.canvas.nativeCanvas.apply {
            for (i in 0 until n step step) {
                val x = xFor(i)
                val dateText = points[i].date.format(fmt)
                drawText(dateText, x, yLabel, labelPaint)
            }

            // always draw last label (so end date is visible)
            if ((n - 1) % step != 0) {
                val x = xFor(n - 1)
                val dateText = points.last().date.format(fmt)
                drawText(dateText, x, yLabel, labelPaint)
            }
        }
    }
}


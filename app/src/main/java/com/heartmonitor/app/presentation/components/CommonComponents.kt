package com.heartmonitor.app.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.heartmonitor.app.domain.model.HealthStatus
import com.heartmonitor.app.domain.model.HeartRecording
import com.heartmonitor.app.domain.model.VerificationStatus
import com.heartmonitor.app.presentation.theme.*
import com.heartmonitor.app.utils.DateTimeUtils

@Composable
fun RecordingCard(
    recording: HeartRecording,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Play button
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(OrangePrimary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Recording info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recording.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Show record",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Text(
                    text = "${DateTimeUtils.formatDuration(recording.duration)} | ${DateTimeUtils.formatFull(recording.timestamp)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Status badges
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                HealthStatusBadge(status = recording.healthStatus)
                VerificationStatusBadge(status = recording.verificationStatus)
                
                if (recording.verificationStatus == VerificationStatus.CLINIC_VERIFIED) {
                    Text(
                        text = "Approved by",
                        style = MaterialTheme.typography.labelSmall,
                        color = OrangePrimary
                    )
                    Text(
                        text = "${recording.doctorName ?: "Doctor"} from ${recording.hospitalName ?: "Clinic"}",
                        style = MaterialTheme.typography.labelSmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Select doctor & hospital",
                        style = MaterialTheme.typography.labelSmall,
                        color = OrangePrimary
                    )
                }
            }
            
            // Arrow icon
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "View details",
                tint = OrangePrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun HealthStatusBadge(
    status: HealthStatus,
    modifier: Modifier = Modifier
) {
    val (text, color) = when (status) {
        HealthStatus.GOOD_HEALTH -> "Good health" to GreenGood
        HealthStatus.ISSUES_DETECTED -> "Issues detected!" to RedWarning
    }
    
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.8f))
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun VerificationStatusBadge(
    status: VerificationStatus,
    modifier: Modifier = Modifier
) {
    val (text, color) = when (status) {
        VerificationStatus.CLINIC_VERIFIED -> "Clinic verified" to GreenGood
        VerificationStatus.NOT_VERIFIED -> "Not verified!" to YellowCaution
    }
    
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.8f))
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = if (status == VerificationStatus.NOT_VERIFIED) Color.Black else Color.White,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun HeartSignalWaveform(
    signalData: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.onSurface,
    strokeWidth: Float = 2f
) {
    Canvas(modifier = modifier) {
        if (signalData.isEmpty()) return@Canvas
        
        val width = size.width
        val height = size.height
        val centerY = height / 2
        
        // Normalize data
        val maxAbs = signalData.maxOfOrNull { kotlin.math.abs(it) } ?: 1f
        val normalizedData = signalData.map { it / maxAbs }
        
        val path = Path()
        val stepX = width / (normalizedData.size - 1).coerceAtLeast(1)
        
        normalizedData.forEachIndexed { index, value ->
            val x = index * stepX
            val y = centerY - (value * centerY * 0.8f)
            
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = strokeWidth)
        )
    }
}

@Composable
fun AnimatedRecordingButton(
    isRecording: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "recording")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    Box(
        modifier = modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(if (isRecording) RedWarning else OrangePrimary)
            .clickable {
                if (isRecording) onStopRecording() else onStartRecording()
            },
        contentAlignment = Alignment.Center
    ) {
        if (isRecording) {
            Box(
                modifier = Modifier
                    .size((24 * scale).dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White)
            )
        } else {
            Icon(
                imageVector = Icons.Default.FiberManualRecord,
                contentDescription = "Start Recording",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun BpmDisplay(
    currentBpm: Int,
    maxBpm: Int,
    avgBpm: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        BpmStatItem(
            label = "Current",
            value = currentBpm,
            color = when {
                currentBpm > 120 -> RedWarning
                currentBpm < 60 -> YellowCaution
                else -> GreenGood
            }
        )
        BpmStatItem(
            label = "Average",
            value = avgBpm,
            color = MaterialTheme.colorScheme.primary
        )
        BpmStatItem(
            label = "Max",
            value = maxBpm,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
private fun BpmStatItem(
    label: String,
    value: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "$value",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = "BPM",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    showDropdown: Boolean = false,
    onDropdownClick: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = OrangePrimary,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = OrangePrimary,
            modifier = Modifier.weight(1f)
        )
        if (showDropdown) {
            IconButton(onClick = onDropdownClick) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand",
                    tint = OrangePrimary
                )
            }
        }
    }
}

@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = OrangePrimary
        )
    }
}

@Composable
fun ErrorMessage(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            tint = RedWarning,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        onRetry?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = it,
                colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary)
            ) {
                Text("Retry")
            }
        }
    }
}

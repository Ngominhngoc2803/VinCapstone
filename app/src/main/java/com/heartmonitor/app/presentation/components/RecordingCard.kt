package com.heartmonitor.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            // Play Button
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
            
            // Recording Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recording.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = "Show record",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                
                Text(
                    text = "${DateTimeUtils.formatDuration(recording.duration)}| ${DateTimeUtils.formatFull(recording.timestamp)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Status Badges
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Health Status Badge
                HealthStatusBadge(status = recording.healthStatus)
                
                // Verification Status Badge
                VerificationStatusBadge(
                    status = recording.verificationStatus,
                    doctorName = recording.doctorName,
                    hospitalName = recording.hospitalName
                )
            }
            
            // Arrow
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "View Details",
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
    val (backgroundColor, textColor, text) = when (status) {
        HealthStatus.GOOD_HEALTH -> Triple(
            GreenGood.copy(alpha = 0.15f),
            GreenGood,
            "Good health"
        )
        HealthStatus.ISSUES_DETECTED -> Triple(
            RedWarning.copy(alpha = 0.15f),
            RedWarning,
            "Issues detected!"
        )
    }
    
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(textColor)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun VerificationStatusBadge(
    status: VerificationStatus,
    doctorName: String?,
    hospitalName: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End
    ) {
        val (backgroundColor, textColor, text) = when (status) {
            VerificationStatus.CLINIC_VERIFIED -> Triple(
                GreenGood.copy(alpha = 0.15f),
                GreenGood,
                "Clinic verified"
            )
            VerificationStatus.NOT_VERIFIED -> Triple(
                YellowCaution.copy(alpha = 0.2f),
                YellowCaution.copy(alpha = 0.9f),
                "Not verified!"
            )
        }
        
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(backgroundColor)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(textColor)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
                fontWeight = FontWeight.SemiBold
            )
        }
        
        if (status == VerificationStatus.CLINIC_VERIFIED && doctorName != null) {
            Text(
                text = "Approved by",
                style = MaterialTheme.typography.labelSmall,
                color = OrangePrimary
            )
            Row {
                Text(
                    text = doctorName,
                    style = MaterialTheme.typography.labelSmall,
                    color = OrangePrimary,
                    textDecoration = TextDecoration.Underline
                )
                Text(
                    text = " from ",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Text(
                    text = hospitalName ?: "Hospital",
                    style = MaterialTheme.typography.labelSmall,
                    color = OrangePrimary,
                    textDecoration = TextDecoration.Underline
                )
            }
        } else if (status == VerificationStatus.NOT_VERIFIED) {
            Row {
                Text(
                    text = "Select ",
                    style = MaterialTheme.typography.labelSmall,
                    color = OrangePrimary
                )
                Text(
                    text = "doctor",
                    style = MaterialTheme.typography.labelSmall,
                    color = OrangePrimary,
                    textDecoration = TextDecoration.Underline
                )
                Text(
                    text = " & ",
                    style = MaterialTheme.typography.labelSmall,
                    color = OrangePrimary
                )
                Text(
                    text = "hospital",
                    style = MaterialTheme.typography.labelSmall,
                    color = OrangePrimary,
                    textDecoration = TextDecoration.Underline
                )
            }
        }
    }
}

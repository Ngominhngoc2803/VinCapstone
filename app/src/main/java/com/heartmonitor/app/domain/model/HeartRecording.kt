package com.heartmonitor.app.domain.model

import java.time.LocalDateTime

data class HeartRecording(
    val id: Long = 0,
    val name: String,
    val timestamp: LocalDateTime,
    val duration: Long, // in milliseconds
    val signalData: List<Float>,
    val healthStatus: HealthStatus,
    val verificationStatus: VerificationStatus,
    val doctorName: String? = null,
    val hospitalName: String? = null,
    val averageBpm: Int,
    val maxBpm: Int,
    val aiAnalysis: AiAnalysis? = null
)

enum class HealthStatus {
    GOOD_HEALTH,
    ISSUES_DETECTED
}

enum class VerificationStatus {
    CLINIC_VERIFIED,
    NOT_VERIFIED
}

data class AiAnalysis(
    val heartRateStatus: String,
    val detectedConditions: List<DetectedCondition>,
    val suggestions: List<String>
)

data class DetectedCondition(
    val name: String,
    val probability: Int // percentage
)

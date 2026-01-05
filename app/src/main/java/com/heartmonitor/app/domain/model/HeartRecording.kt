package com.heartmonitor.app.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

data class HeartRecording(
    val id: Long = 0,
    val name: String,
    val timestamp: LocalDateTime,
    val duration: Long,
    val signalData: List<Float>,
    val bpmSeries: List<Float> = emptyList(),
    val sampleRateHz: Int = 8000,
    val healthStatus: HealthStatus,
    val verificationStatus: VerificationStatus,
    val doctorName: String? = null,
    val hospitalName: String? = null,
    val averageBpm: Float,
    val maxBpm: Int,
    val aiAnalysis: AiAnalysis? = null,
    val doctorVisitDate: LocalDate? = null,
    val doctorNote: String? = null,
    val diagnosis: String? = null,
    val recommendations: String? = null,

    val pcmFilePath: String? = null,
    val wavFilePath: String? = null,   // ✅ make nullable + default

    val audioSampleRate: Int = 8000,
    val audioChannels: Int = 1
)

enum class HealthStatus { GOOD_HEALTH, ISSUES_DETECTED }
enum class VerificationStatus { CLINIC_VERIFIED, NOT_VERIFIED }

data class AiAnalysis(
    val heartRateStatus: String,
    val detectedConditions: List<DetectedCondition>,
    val suggestions: List<String>
)

data class DetectedCondition(
    val name: String,
    val probability: Int
)
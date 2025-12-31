package com.heartmonitor.app.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

data class HeartRecording(
    val id: Long = 0,
    val name: String,
    val timestamp: LocalDateTime,
    val duration: Long, // ms
    val signalData: List<Float>, // waveform samples
    val bpmSeries: List<Float> = emptyList(), // BPM samples streamed during recording
    val sampleRateHz: Int = 8000, // needed for playback timing
    val healthStatus: HealthStatus,
    val verificationStatus: VerificationStatus,
    val doctorName: String? = null,
    val hospitalName: String? = null,
    val averageBpm: Float,
    val maxBpm: Int,
    val aiAnalysis: AiAnalysis? = null,
    // New fields for doctor visit
    val doctorVisitDate: LocalDate? = null,
    val doctorNote: String? = null,
    val diagnosis: String? = null,
    val recommendations: String? = null
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
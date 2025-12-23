package com.heartmonitor.app.domain.model

data class UserProfile(
    val id: Long = 0,
    val name: String,
    val avatarUrl: String? = null
)

data class WeeklySummary(
    val totalRecordings: Int,
    val warningCount: Int,
    val warningDates: List<String>,
    val detectedConditions: List<String>,
    val aiSummary: String
)

data class WarningDetail(
    val recordingId: Long,
    val bpm: Int,
    val date: String,
    val severity: WarningSeverity,
    val aiSuggestion: String,
    val note: String? = null
)

enum class WarningSeverity {
    LOW,
    MEDIUM,
    HIGH
}

data class DoctorInfo(
    val id: Long = 0,
    val name: String,
    val address: String,
    val phone: String,
    val email: String,
    val avatarUrl: String? = null
)

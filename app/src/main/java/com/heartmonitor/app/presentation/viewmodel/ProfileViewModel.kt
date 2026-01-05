package com.heartmonitor.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heartmonitor.app.data.repository.ChatRepository
import com.heartmonitor.app.data.repository.HeartRecordingRepository
import com.heartmonitor.app.data.repository.UserRepository
import com.heartmonitor.app.domain.model.*
import com.heartmonitor.app.utils.DateTimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val recordingRepository: HeartRecordingRepository,
    private val chatRepository: ChatRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadUserProfile()
        loadWeeklyData()
        loadDoctors()
    }

    private fun loadUserProfile() {
        viewModelScope.launch {
            userRepository.getUserProfile().collect { profile ->
                _uiState.update {
                    it.copy(
                        userProfile = profile ?: UserProfile(
                            name = "User",
                            avatarUrl = null
                        )
                    )
                }
            }
        }
    }

    private fun loadWeeklyData() {
        viewModelScope.launch {
            val startOfWeek = DateTimeUtils.getStartOfWeek()

            recordingRepository.getWarningsAfterDate(startOfWeek).collect { warnings ->
                val warningDetails = warnings.map { recording ->
                    WarningDetail(
                        recordingId = recording.id,
                        bpm = recording.averageBpm,
                        date = DateTimeUtils.formatDate(recording.timestamp),
                        severity = when {
                            recording.averageBpm > 140 || recording.averageBpm < 45 -> WarningSeverity.HIGH
                            recording.averageBpm > 120 || recording.averageBpm < 55 -> WarningSeverity.MEDIUM
                            else -> WarningSeverity.LOW
                        },
                        aiSuggestion = recording.aiAnalysis?.suggestions?.firstOrNull()
                            ?: "Please consult a healthcare professional.",
                        note = null
                    )
                }

                val warningDates = warnings.map { DateTimeUtils.formatDate(it.timestamp) }.distinct()
                val detectedConditions = warnings.flatMap {
                    it.aiAnalysis?.detectedConditions?.map { c -> c.name } ?: emptyList()
                }.distinct()

                val aiSummary = generateAiSummary(warnings, warningDates, detectedConditions)

                val weeklySummary = WeeklySummary(
                    totalRecordings = warnings.size,
                    warningCount = warnings.size,
                    warningDates = warningDates,
                    detectedConditions = detectedConditions,
                    aiSummary = aiSummary
                )

                _uiState.update {
                    it.copy(
                        weeklySummary = weeklySummary,
                        warningDetails = warningDetails
                    )
                }
            }
        }
    }

    private fun generateAiSummary(
        warnings: List<HeartRecording>,
        warningDates: List<String>,
        conditions: List<String>
    ): String {
        if (warnings.isEmpty()) {
            return "Great news! No heart health issues detected this week. Keep maintaining your healthy lifestyle!"
        }

        val conditionsText = if (conditions.isNotEmpty()) {
            "high risks of ${conditions.joinToString(", ")}"
        } else {
            "potential heart irregularities"
        }

        return "This week, we detected that you have $conditionsText " +
                "due to repeated data ${warnings.size} times on dates ${warningDates.joinToString(", and ")}..."
    }

    private fun loadDoctors() {
        viewModelScope.launch {
            userRepository.getAllDoctors().collect { doctors ->
                _uiState.update { it.copy(doctors = doctors) }
            }

            // Add sample doctors if none exist
            if (_uiState.value.doctors.isEmpty()) {
                addSampleDoctors()
            }
        }
    }

    private suspend fun addSampleDoctors() {
        val sampleDoctors = listOf(
            DoctorInfo(
                name = "Dr. Nhat Duong",
                address = "1234 Chestnut St, Philly, PA 19104",
                phone = "267 1234 5678",
                email = "penn@vinuni.edu.vn"
            ),
            DoctorInfo(
                name = "Dr. Sarah Johnson",
                address = "5678 Market St, Philly, PA 19103",
                phone = "267 9876 5432",
                email = "sjohnson@hospital.com"
            )
        )

        sampleDoctors.forEach { doctor ->
            userRepository.saveDoctor(doctor)
        }
    }

    fun updateUserName(name: String) {
        viewModelScope.launch {
            val currentProfile = _uiState.value.userProfile
            val updatedProfile = currentProfile?.copy(name = name) ?: UserProfile(name = name)
            userRepository.saveUserProfile(updatedProfile)
        }
    }

    fun refreshData() {
        loadWeeklyData()
    }

    fun updateUserAvatar(uri: Uri) {
        viewModelScope.launch {
            try {
                // Copy image to app's internal storage
                val fileName = "user_avatar_${System.currentTimeMillis()}.jpg"
                val file = File(context.filesDir, fileName)

                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }

                // Delete old avatar if exists
                _uiState.value.userProfile?.avatarUrl?.let { oldPath ->
                    File(oldPath).delete()
                }

                // Update profile with new avatar path
                val currentProfile = _uiState.value.userProfile
                val updatedProfile = currentProfile?.copy(avatarUrl = file.absolutePath)
                    ?: UserProfile(name = "User", avatarUrl = file.absolutePath)
                userRepository.saveUserProfile(updatedProfile)

            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to save avatar: ${e.message}") }
            }
        }
    }
}

data class ProfileUiState(
    val userProfile: UserProfile? = null,
    val weeklySummary: WeeklySummary? = null,
    val warningDetails: List<WarningDetail> = emptyList(),
    val doctors: List<DoctorInfo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
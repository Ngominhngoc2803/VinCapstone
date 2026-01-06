package com.heartmonitor.app.data.repository

import com.google.gson.Gson
import com.heartmonitor.app.BuildConfig
import com.heartmonitor.app.data.local.*
import com.heartmonitor.app.data.remote.*
import com.heartmonitor.app.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HeartRecordingRepository @Inject constructor(
    private val heartRecordingDao: HeartRecordingDao
) {
    private val gson = Gson()

    fun getAllRecordings(): Flow<List<HeartRecording>> {

        return heartRecordingDao.getAllRecordings().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    suspend fun getRecordingById(id: Long): HeartRecording? {
        return heartRecordingDao.getRecordingById(id)?.toDomainModel()
    }

    fun getRecordingsAfterDate(startDate: LocalDateTime): Flow<List<HeartRecording>> {
        return heartRecordingDao.getRecordingsAfterDate(startDate).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    fun getWarningsAfterDate(startDate: LocalDateTime): Flow<List<HeartRecording>> {
        return heartRecordingDao.getWarningsAfterDate(startDate).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    suspend fun saveRecording(recording: HeartRecording): Long {
        return heartRecordingDao.insertRecording(recording.toEntity())
    }

    suspend fun updateRecording(recording: HeartRecording) {
        heartRecordingDao.updateRecording(recording.toEntity())
    }

    suspend fun deleteRecording(recording: HeartRecording) {
        heartRecordingDao.deleteRecording(recording.toEntity())
    }

    private fun HeartRecordingEntity.toDomainModel(): HeartRecording {
        return HeartRecording(
            id = id,
            name = name,
            timestamp = timestamp,
            duration = duration,
            signalData = emptyList(),  // ✅ Empty - will be loaded from PCM file when needed
            bpmSeries = bpmSeries,
            sampleRateHz = sampleRateHz,
            healthStatus = HealthStatus.valueOf(healthStatus),
            verificationStatus = VerificationStatus.valueOf(verificationStatus),
            doctorName = doctorName,
            hospitalName = hospitalName,
            averageBpm = averageBpm,
            maxBpm = maxBpm,
            doctorVisitDate = doctorVisitDate,
            doctorNote = doctorNote,
            diagnosis = diagnosis,
            recommendations = recommendations,

            pcmFilePath = pcmFilePath,
            wavFilePath = wavFilePath,

            audioSampleRate = audioSampleRate,
            audioChannels = audioChannels
        )
    }

    private fun HeartRecording.toEntity(): HeartRecordingEntity {
        return HeartRecordingEntity(
            id = id,
            name = name,
            timestamp = timestamp,
            duration = duration,
            // ✅ Don't save signalData - it's too large and redundant (stored in PCM file)
            bpmSeries = bpmSeries,
            sampleRateHz = sampleRateHz,
            healthStatus = healthStatus.name,
            verificationStatus = verificationStatus.name,
            doctorName = doctorName,
            hospitalName = hospitalName,
            averageBpm = averageBpm,
            maxBpm = maxBpm,
            doctorVisitDate = doctorVisitDate,
            doctorNote = doctorNote,
            diagnosis = diagnosis,
            recommendations = recommendations,

            pcmFilePath = pcmFilePath,
            wavFilePath = wavFilePath,

            audioSampleRate = audioSampleRate,
            audioChannels = audioChannels
        )
    }
}

@Singleton
class UserRepository @Inject constructor(
    private val userProfileDao: UserProfileDao,
    private val doctorDao: DoctorDao
) {
    fun getUserProfile(): Flow<UserProfile?> {
        return userProfileDao.getUserProfile().map { entity ->
            entity?.let {
                UserProfile(
                    id = it.id,
                    name = it.name,
                    avatarUrl = it.avatarUrl
                )
            }
        }
    }

    suspend fun saveUserProfile(profile: UserProfile) {
        userProfileDao.insertUserProfile(
            UserProfileEntity(
                id = profile.id,
                name = profile.name,
                avatarUrl = profile.avatarUrl
            )
        )
    }

    fun getAllDoctors(): Flow<List<DoctorInfo>> {
        return doctorDao.getAllDoctors().map { entities ->
            entities.map {
                DoctorInfo(
                    id = it.id,
                    name = it.name,
                    address = it.address,
                    phone = it.phone,
                    email = it.email,
                    avatarUrl = it.avatarUrl
                )
            }
        }
    }

    suspend fun saveDoctor(doctor: DoctorInfo) {
        doctorDao.insertDoctor(
            DoctorEntity(
                id = doctor.id,
                name = doctor.name,
                address = doctor.address,
                phone = doctor.phone,
                email = doctor.email,
                avatarUrl = doctor.avatarUrl
            )
        )
    }
}

@Singleton
class ChatRepository @Inject constructor(
    private val openAIService: OpenAIService
) {
    suspend fun sendMessage(
        messages: List<ChatMessage>,
        signalContext: String
    ): Result<String> {
        return try {
            val systemMessage = ChatRequestMessage(
                role = "system",
                content = """You are a helpful medical AI assistant specialized in analyzing heart health data. 
                    You help users understand their heart recordings and provide health insights.
                    Always remind users to consult with healthcare professionals for medical advice.
                    Current heart signal data context: $signalContext"""
            )

            val chatMessages = listOf(systemMessage) + messages.map {
                ChatRequestMessage(
                    role = if (it.isFromUser) "user" else "assistant",
                    content = it.content
                )
            }

            val response = openAIService.getChatCompletion(
                authorization = "Bearer ${BuildConfig.OPENAI_API_KEY}",
                request = ChatCompletionRequest(messages = chatMessages)
            )

            val content = response.choices.firstOrNull()?.message?.content
                ?: "I couldn't process your request. Please try again."

            Result.success(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun analyzeSignal(signalData: List<Float>, bpm: Float): Result<AiAnalysis> {
        return try {
            val prompt = """Analyze this heart signal data:
                - Average BPM: $bpm
                - Signal points (sample): ${signalData.take(100)}
                
                Provide analysis in this exact JSON format:
                {
                    "heartRateStatus": "normal/too fast/too slow",
                    "detectedConditions": [{"name": "condition name", "probability": 0-100}],
                    "suggestions": ["suggestion 1", "suggestion 2"]
                }
                
                Only respond with the JSON, no other text."""

            val response = openAIService.getChatCompletion(
                authorization = "Bearer ${BuildConfig.OPENAI_API_KEY}",
                request = ChatCompletionRequest(
                    messages = listOf(
                        ChatRequestMessage(
                            role = "system",
                            content = "You are a medical AI that analyzes heart data. Respond only with valid JSON."
                        ),
                        ChatRequestMessage(role = "user", content = prompt)
                    )
                )
            )

            val content = response.choices.firstOrNull()?.message?.content ?: "{}"
            val gson = Gson()
            val analysis = gson.fromJson(content, AiAnalysis::class.java)

            Result.success(analysis)
        } catch (e: Exception) {
            // Return a default analysis if API fails
            Result.success(
                AiAnalysis(
                    heartRateStatus = if (bpm > 100) "too fast" else if (bpm < 60) "too slow" else "normal",
                    detectedConditions = emptyList(),
                    suggestions = listOf("Please consult a healthcare professional for accurate diagnosis.")
                )
            )
        }
    }
}
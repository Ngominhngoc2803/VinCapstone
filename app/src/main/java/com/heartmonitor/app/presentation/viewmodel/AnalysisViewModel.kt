package com.heartmonitor.app.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heartmonitor.app.data.repository.ChatRepository
import com.heartmonitor.app.data.repository.HeartRecordingRepository
import com.heartmonitor.app.domain.model.AiAnalysis
import com.heartmonitor.app.domain.model.ChatMessage
import com.heartmonitor.app.domain.model.HeartRecording
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    private val recordingRepository: HeartRecordingRepository,
    private val chatRepository: ChatRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val recordingId: Long = savedStateHandle.get<Long>("recordingId") ?: 0L

    private val _uiState = MutableStateFlow(AnalysisUiState())
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    init {
        loadRecording()
    }


    private var playbackJob: kotlinx.coroutines.Job? = null
    private var totalDurationMs: Long = 0L // set this when you start playback

    private fun loadRecording() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            val recording = recordingRepository.getRecordingById(recordingId)
            
            if (recording != null) {
                _uiState.update {
                    it.copy(
                        recording = recording,
                        isLoading = false
                    )
                }
                
                // Auto-analyze if no analysis exists
                if (recording.aiAnalysis == null) {
                    analyzeSignal()
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Recording not found"
                    )
                }
            }
        }
    }


    fun togglePlay() {
        val rec = _uiState.value.recording ?: return

        if (_uiState.value.isPlaying) {
            stopPlaybackInternal()
        } else {
            startPlaybackInternal(rec)
        }
    }

    private fun startPlaybackInternal(recording: HeartRecording) {
        // ✅ Start your real audio playback here
        // audioPlayer.play(recording.audioPath OR recording.pcmBytes ...)
        // totalDurationMs = audioPlayer.durationMs()

        // If you don't have duration from player, derive from signalData:
        // (Assuming your signalData is 8000 Hz like ESP32)
        val sampleRate = 8000f
        totalDurationMs = ((recording.signalData.size / sampleRate) * 1000f).toLong()

        _uiState.update { it.copy(isPlaying = true, playbackMs = 0L) }

        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            val start = System.currentTimeMillis()

            while (_uiState.value.isPlaying) {
                val elapsed = System.currentTimeMillis() - start
                val clamped = elapsed.coerceAtMost(totalDurationMs)

                _uiState.update { it.copy(playbackMs = clamped) }

                if (clamped >= totalDurationMs) {
                    stopPlaybackInternal()
                    break
                }

                kotlinx.coroutines.delay(33) // ~30fps smooth cursor
            }
        }
    }

    private fun stopPlaybackInternal() {
        // ✅ Stop your real audio playback here
        // audioPlayer.stop()

        playbackJob?.cancel()
        playbackJob = null
        _uiState.update { it.copy(isPlaying = false) }
    }

    fun analyzeSignal() {
        val recording = _uiState.value.recording ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzing = true) }
            
            val result = chatRepository.analyzeSignal(
                signalData = recording.signalData,
                bpm = recording.averageBpm
            )
            
            result.fold(
                onSuccess = { analysis ->
                    _uiState.update {
                        it.copy(
                            analysis = analysis,
                            isAnalyzing = false
                        )
                    }
                    
                    // Update recording with analysis
                    val updatedRecording = recording.copy(aiAnalysis = analysis)
                    recordingRepository.updateRecording(updatedRecording)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isAnalyzing = false,
                            error = error.message
                        )
                    }
                }
            )
        }
    }

    fun sendChatMessage(message: String) {
        if (message.isBlank()) return
        
        val recording = _uiState.value.recording ?: return
        
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            content = message,
            isFromUser = true
        )
        
        _uiState.update {
            it.copy(
                chatMessages = it.chatMessages + userMessage,
                isChatLoading = true
            )
        }
        
        viewModelScope.launch {
            val signalContext = """
                Recording: ${recording.name}
                Average BPM: ${recording.averageBpm}
                Max BPM: ${recording.maxBpm}
                Health Status: ${recording.healthStatus}
                Duration: ${recording.duration / 1000} seconds
                ${recording.aiAnalysis?.let { 
                    "Previous Analysis: Heart rate is ${it.heartRateStatus}. " +
                    "Detected conditions: ${it.detectedConditions.joinToString { c -> "${c.name} (${c.probability}%)" }}"
                } ?: ""}
            """.trimIndent()
            
            val result = chatRepository.sendMessage(
                messages = _uiState.value.chatMessages,
                signalContext = signalContext
            )
            
            result.fold(
                onSuccess = { response ->
                    val assistantMessage = ChatMessage(
                        id = UUID.randomUUID().toString(),
                        content = response,
                        isFromUser = false
                    )
                    
                    _uiState.update {
                        it.copy(
                            chatMessages = it.chatMessages + assistantMessage,
                            isChatLoading = false
                        )
                    }
                },
                onFailure = { error ->
                    val errorMessage = ChatMessage(
                        id = UUID.randomUUID().toString(),
                        content = "Sorry, I couldn't process your request. Please try again.",
                        isFromUser = false
                    )
                    
                    _uiState.update {
                        it.copy(
                            chatMessages = it.chatMessages + errorMessage,
                            isChatLoading = false
                        )
                    }
                }
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

data class AnalysisUiState(
    val recording: HeartRecording? = null,
    val analysis: AiAnalysis? = null,
    val chatMessages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val isAnalyzing: Boolean = false,
    val isChatLoading: Boolean = false,
    val error: String? = null,
    val isPlaying: Boolean = false,
    val playbackMs: Long = 0L
)

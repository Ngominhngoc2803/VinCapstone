package com.heartmonitor.app.presentation.viewmodel

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heartmonitor.app.bluetooth.BleConnectionState
import com.heartmonitor.app.bluetooth.BleManager
import com.heartmonitor.app.data.repository.ChatRepository
import com.heartmonitor.app.data.repository.HeartRecordingRepository
import com.heartmonitor.app.domain.model.HealthStatus
import com.heartmonitor.app.domain.model.HeartRecording
import com.heartmonitor.app.domain.model.VerificationStatus
import com.heartmonitor.app.utils.SignalProcessor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val recordingRepository: HeartRecordingRepository,
    private val chatRepository: ChatRepository,
    private val bleManager: BleManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val bleConnectionState: StateFlow<BleConnectionState> = bleManager.connectionState
    val isScanning: StateFlow<Boolean> = bleManager.isScanning
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = bleManager.discoveredDevices

    private var recordingJob: Job? = null
    private val signalBuffer = mutableListOf<Float>()
    private var recordingStartTime: Long = 0

    init {
        loadRecordings()
        observeBleSignal()
    }

    private fun loadRecordings() {
        viewModelScope.launch {
            recordingRepository.getAllRecordings().collect { recordings ->
                _uiState.update { it.copy(recordings = recordings) }
            }
        }
    }

    private fun observeBleSignal() {
        viewModelScope.launch {
            bleManager.heartSignalData.collect { newData ->
                if (_uiState.value.isRecording) {
                    signalBuffer.addAll(newData)
                    
                    // Update live signal display
                    val displayData = if (signalBuffer.size > 500) {
                        signalBuffer.takeLast(500)
                    } else {
                        signalBuffer.toList()
                    }
                    
                    val currentBpm = SignalProcessor.calculateBpm(signalBuffer)
                    
                    _uiState.update { state ->
                        val maxBpm = maxOf(state.currentMaxBpm, currentBpm)
                        state.copy(
                            currentSignalData = displayData,
                            currentBpm = currentBpm,
                            currentMaxBpm = maxBpm,
                            recordingDuration = System.currentTimeMillis() - recordingStartTime
                        )
                    }
                }
            }
        }
    }

    fun startBleScan() {
        bleManager.startScan()
    }

    fun stopBleScan() {
        bleManager.stopScan()
    }

    fun connectToDevice(device: BluetoothDevice) {
        bleManager.stopScan()
        bleManager.connect(device)
    }

    fun disconnect() {
        bleManager.disconnect()
    }

    fun startRecording(name: String = "Recording") {
        signalBuffer.clear()
        recordingStartTime = System.currentTimeMillis()
        
        _uiState.update {
            it.copy(
                isRecording = true,
                currentRecordingName = name,
                currentSignalData = emptyList(),
                currentBpm = 0,
                currentMaxBpm = 0,
                recordingDuration = 0
            )
        }

        // If BLE is not connected, simulate data for testing
        if (bleConnectionState.value != BleConnectionState.READY) {
            startSimulatedRecording()
        }
    }

    private fun startSimulatedRecording() {
        recordingJob = viewModelScope.launch {
            while (_uiState.value.isRecording) {
                // Generate simulated heart signal data
                val simulatedData = generateSimulatedHeartSignal()
                signalBuffer.addAll(simulatedData)
                
                val displayData = if (signalBuffer.size > 500) {
                    signalBuffer.takeLast(500)
                } else {
                    signalBuffer.toList()
                }
                
                val currentBpm = SignalProcessor.calculateBpm(signalBuffer)
                
                _uiState.update { state ->
                    val maxBpm = maxOf(state.currentMaxBpm, currentBpm)
                    state.copy(
                        currentSignalData = displayData,
                        currentBpm = currentBpm,
                        currentMaxBpm = maxBpm,
                        recordingDuration = System.currentTimeMillis() - recordingStartTime
                    )
                }
                
                delay(100) // Update every 100ms
            }
        }
    }

    private fun generateSimulatedHeartSignal(): List<Float> {
        val data = mutableListOf<Float>()
        val bpm = (70..110).random()
        val samplesPerBeat = 1000 * 60 / bpm / 10 // For 100ms update interval
        
        repeat(10) { i ->
            val phase = (i % samplesPerBeat).toFloat() / samplesPerBeat
            val value = when {
                phase < 0.1f -> kotlin.math.sin(phase * 10 * Math.PI.toFloat()) * 0.3f
                phase < 0.2f -> kotlin.math.sin((phase - 0.1f) * 10 * Math.PI.toFloat()) * 1.0f
                phase < 0.3f -> -kotlin.math.sin((phase - 0.2f) * 10 * Math.PI.toFloat()) * 0.4f
                phase < 0.5f -> kotlin.math.sin((phase - 0.3f) * 5 * Math.PI.toFloat()) * 0.2f
                else -> (Math.random().toFloat() - 0.5f) * 0.1f
            }
            data.add(value + (Math.random().toFloat() - 0.5f) * 0.05f) // Add noise
        }
        
        return data
    }

    fun stopRecording() {
        recordingJob?.cancel()
        
        viewModelScope.launch {
            val signalData = signalBuffer.toList()
            val avgBpm = SignalProcessor.calculateBpm(signalData)
            val maxBpm = _uiState.value.currentMaxBpm
            
            // Determine health status (simple classification - replace with ML model)
            val healthStatus = when {
                avgBpm > 120 || avgBpm < 50 -> HealthStatus.ISSUES_DETECTED
                maxBpm > 150 -> HealthStatus.ISSUES_DETECTED
                else -> HealthStatus.GOOD_HEALTH
            }
            
            // Create and save recording
            val recording = HeartRecording(
                name = _uiState.value.currentRecordingName,
                timestamp = LocalDateTime.now(),
                duration = _uiState.value.recordingDuration,
                signalData = signalData,
                healthStatus = healthStatus,
                verificationStatus = VerificationStatus.NOT_VERIFIED,
                averageBpm = avgBpm,
                maxBpm = maxBpm
            )
            
            recordingRepository.saveRecording(recording)
            
            _uiState.update {
                it.copy(
                    isRecording = false,
                    currentSignalData = emptyList()
                )
            }
            
            signalBuffer.clear()
        }
    }

    fun selectRecording(recording: HeartRecording) {
        _uiState.update {
            it.copy(
                selectedRecording = recording,
                displayedBpmData = generateBpmChartData(recording)
            )
        }
    }

    fun clearSelectedRecording() {
        _uiState.update { it.copy(selectedRecording = null) }
    }

    private fun generateBpmChartData(recording: HeartRecording): List<BpmDataPoint> {
        // Generate BPM data points over time from the recording
        val windowSize = 1000 // 1 second windows
        val signalData = recording.signalData
        
        if (signalData.size < windowSize) {
            return listOf(BpmDataPoint(0f, recording.averageBpm.toFloat()))
        }
        
        val dataPoints = mutableListOf<BpmDataPoint>()
        var index = 0
        
        while (index + windowSize <= signalData.size) {
            val window = signalData.subList(index, index + windowSize)
            val bpm = SignalProcessor.calculateBpm(window)
            val timeSeconds = index.toFloat() / 1000f
            dataPoints.add(BpmDataPoint(timeSeconds, bpm.toFloat()))
            index += windowSize / 2 // 50% overlap
        }
        
        return dataPoints
    }

    fun isBluetoothEnabled(): Boolean = bleManager.isBluetoothEnabled()
}

data class HomeUiState(
    val recordings: List<HeartRecording> = emptyList(),
    val isRecording: Boolean = false,
    val currentRecordingName: String = "",
    val currentSignalData: List<Float> = emptyList(),
    val currentBpm: Int = 0,
    val currentMaxBpm: Int = 0,
    val recordingDuration: Long = 0,
    val selectedRecording: HeartRecording? = null,
    val displayedBpmData: List<BpmDataPoint> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

data class BpmDataPoint(
    val timeSeconds: Float,
    val bpm: Float
)

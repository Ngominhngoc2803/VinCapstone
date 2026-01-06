package com.heartmonitor.app.presentation.viewmodel

import android.bluetooth.BluetoothDevice
import android.os.Build
import androidx.annotation.RequiresApi
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
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import com.heartmonitor.app.utils.convertPcmToWav
import java.io.File
import java.io.FileOutputStream


@HiltViewModel
class HomeViewModel @Inject constructor(
    private val recordingRepository: HeartRecordingRepository,
    private val chatRepository: ChatRepository,
    private val bleManager: BleManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val bleConnectionState: StateFlow<BleConnectionState> = bleManager.connectionState
    val isScanning: StateFlow<Boolean> = bleManager.isScanning
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = bleManager.discoveredDevices

    private var recordingJob: Job? = null
    private val signalBuffer = mutableListOf<Float>()
    private val bpmBuffer = mutableListOf<Float>()

    private var recordingStartTime: Long = 0
    private var pcmOut: FileOutputStream? = null
    private var currentPcmPath: String? = null
    private var currentWavPath: String? = null

    private val AUDIO_SAMPLE_RATE = 8000
    private val AUDIO_CHANNELS = 1

    init {
        loadRecordings()
        observeBleSignal()
        observeBleBpm()
        observeBlePcmAudio()  // ONLY ONE PCM collector!
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

                    // Display only last 500 samples for live waveform
                    val displayData = if (signalBuffer.size > 500) {
                        signalBuffer.takeLast(500)
                    } else {
                        signalBuffer.toList()
                    }

                    _uiState.update { state ->
                        state.copy(
                            currentSignalData = displayData,
                            recordingDuration = System.currentTimeMillis() - recordingStartTime
                        )
                    }
                }
            }
        }
    }

    private fun observeBleBpm() {
        viewModelScope.launch {
            bleManager.bpm.collect { bpmValue ->
                if (_uiState.value.isRecording && bpmValue != null && bpmValue.isFinite()) {
                    bpmBuffer.add(bpmValue)

                    _uiState.update { state ->
                        state.copy(
                            currentBpm = bpmValue.toInt(),
                            currentMaxBpm = maxOf(state.currentMaxBpm, bpmValue.toInt())
                        )
                    }
                }
            }
        }
    }

    /**
     * SINGLE PCM audio collector - writes BLE audio packets to PCM file
     */
    private fun observeBlePcmAudio() {
        viewModelScope.launch {
            var totalBytesWritten = 0L
            var chunkCount = 0

            bleManager.pcmBytes.collect { chunk ->
                if (_uiState.value.isRecording) {
                    try {
                        pcmOut?.write(chunk)
                        totalBytesWritten += chunk.size
                        chunkCount++

                        // Log every 50 chunks to avoid spam
                        if (chunkCount % 50 == 0) {
                            Log.d("PCM_WRITE", "Wrote $chunkCount chunks, total: $totalBytesWritten bytes")
                        }
                    } catch (e: Exception) {
                        Log.e("PCM", "Failed to write PCM chunk", e)
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
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

    @RequiresApi(Build.VERSION_CODES.S)
    fun disconnect() {
        bleManager.disconnect()
    }

    fun startRecording(name: String = "Recording") {
        // Clear buffers
        signalBuffer.clear()
        bpmBuffer.clear()
        recordingStartTime = System.currentTimeMillis()

        // Create PCM and WAV file paths with single timestamp
        val timestamp = System.currentTimeMillis()
        val base = "rec_$timestamp"

        val pcmFile = File(context.filesDir, "$base.pcm")
        currentPcmPath = pcmFile.absolutePath
        pcmOut = FileOutputStream(pcmFile, false)  // false = overwrite if exists

        val wavFile = File(context.filesDir, "$base.wav")
        currentWavPath = wavFile.absolutePath

        Log.d("Recording", "Started recording: PCM=$currentPcmPath")

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
        if (bleConnectionState.value == BleConnectionState.DISCONNECTED) {
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

                if (currentBpm in 30..220) {
                    bpmBuffer.add(currentBpm.toFloat())
                }

                _uiState.update { state ->
                    state.copy(
                        currentSignalData = displayData,
                        currentBpm = currentBpm,
                        currentMaxBpm = maxOf(state.currentMaxBpm, currentBpm),
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
        // 1. Stop and close PCM file
        try {
            pcmOut?.flush()
            pcmOut?.close()
        } catch (e: Exception) {
            Log.e("PCM", "Error closing PCM file", e)
        }
        pcmOut = null

        // 2. Stop recording job
        recordingJob?.cancel()
        recordingJob = null

        viewModelScope.launch {
            val signalData = signalBuffer.toList()

            // Calculate BPM statistics
            val avgBpmFromBle = if (bpmBuffer.isNotEmpty()) {
                bpmBuffer.average().toFloat()
            } else {
                SignalProcessor.calculateBpm(signalData).toFloat()
            }

            val maxBpmFromBle = if (bpmBuffer.isNotEmpty()) {
                bpmBuffer.maxOrNull()?.toInt() ?: 0
            } else {
                _uiState.value.currentMaxBpm
            }

            val healthStatus = when {
                avgBpmFromBle > 120f || avgBpmFromBle < 50f -> HealthStatus.ISSUES_DETECTED
                maxBpmFromBle > 150 -> HealthStatus.ISSUES_DETECTED
                else -> HealthStatus.GOOD_HEALTH
            }

            // 3. Convert PCM to WAV (ONCE!)
            val pcmPath = currentPcmPath
            val wavPath = currentWavPath

            if (!pcmPath.isNullOrBlank() && !wavPath.isNullOrBlank()) {
                val pcmFile = File(pcmPath)
                val wavFile = File(wavPath)

                if (pcmFile.exists()) {
                    val pcmSize = pcmFile.length()
                    val expectedSize = (_uiState.value.recordingDuration / 1000.0 * AUDIO_SAMPLE_RATE * 2).toLong()

                    Log.w("PCM_ANALYSIS", """
                        ╔════════════════════════════════════════════════
                        ║ PCM Recording Analysis
                        ╠════════════════════════════════════════════════
                        ║ Duration: ${_uiState.value.recordingDuration}ms
                        ║ PCM File Size: $pcmSize bytes
                        ║ Expected Size: $expectedSize bytes
                        ║ Percentage: ${(pcmSize * 100.0 / expectedSize).toInt()}%
                        ║ Sample Rate: $AUDIO_SAMPLE_RATE Hz
                        ╚════════════════════════════════════════════════
                    """.trimIndent())

                    if (pcmSize > 0) {
                        try {
                            convertPcmToWav(
                                pcmFile = pcmFile,
                                wavFile = wavFile,
                                sampleRate = AUDIO_SAMPLE_RATE,
                                channels = AUDIO_CHANNELS
                            )
                            Log.d("WAV", "WAV created: ${wavFile.length()} bytes")
                        } catch (e: Exception) {
                            Log.e("WAV", "Failed to convert PCM to WAV", e)
                        }
                    } else {
                        Log.e("WAV", "PCM file is empty - cannot create WAV")
                    }
                } else {
                    Log.w("WAV", "PCM file missing: $pcmPath")
                }
            }

            // 4. Save recording to database
            val recording = HeartRecording(
                name = _uiState.value.currentRecordingName,
                timestamp = LocalDateTime.now(),
                duration = _uiState.value.recordingDuration,
                signalData = signalData,  // Processed signal for display
                bpmSeries = bpmBuffer.toList(),
                sampleRateHz = 8000,
                healthStatus = healthStatus,
                verificationStatus = VerificationStatus.NOT_VERIFIED,
                averageBpm = avgBpmFromBle,
                maxBpm = maxBpmFromBle,
                pcmFilePath = currentPcmPath,  // Raw audio file
                audioSampleRate = AUDIO_SAMPLE_RATE,
                audioChannels = AUDIO_CHANNELS,
                wavFilePath = currentWavPath  // WAV audio file
            )

            recordingRepository.saveRecording(recording)

            // 5. Reset UI state
            _uiState.update {
                it.copy(
                    isRecording = false,
                    currentSignalData = emptyList(),
                    currentBpm = 0,
                    currentMaxBpm = 0,
                    recordingDuration = 0
                )
            }

            // 6. Clear buffers and paths
            signalBuffer.clear()
            bpmBuffer.clear()
            currentPcmPath = null
            currentWavPath = null
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

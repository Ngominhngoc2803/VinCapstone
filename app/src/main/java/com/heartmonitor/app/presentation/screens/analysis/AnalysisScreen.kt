package com.heartmonitor.app.presentation.screens.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.heartmonitor.app.domain.model.ChatMessage
import com.heartmonitor.app.presentation.components.DoctorVisitDialog
import com.heartmonitor.app.presentation.components.DoctorVisitInfoCard
import com.heartmonitor.app.presentation.components.DoctorVisitInput
import com.heartmonitor.app.presentation.components.HeartSignalWaveform
import com.heartmonitor.app.presentation.components.LoadingIndicator
import com.heartmonitor.app.presentation.theme.*
import com.heartmonitor.app.presentation.viewmodel.AnalysisViewModel
import kotlinx.coroutines.launch

import android.media.MediaPlayer

import android.net.Uri
import android.util.Log
import java.io.File
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import android.os.SystemClock
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

import java.io.BufferedInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import android.widget.Toast

private fun playPcmFileFromPath(
    path: String,
    sampleRate: Int
): Pair<AudioTrack?, Long> {
    val f = File(path)
    if (!f.exists() || f.length() <= 0) {
        Log.e("PCM_PLAY", "File missing/empty: $path")
        return null to 0L
    }

    // Read bytes (PCM16 little-endian)
    val bytes = try {
        BufferedInputStream(FileInputStream(f)).use { it.readBytes() }
    } catch (e: Exception) {
        Log.e("PCM_PLAY", "Read error", e)
        return null to 0L
    }

    // Must be even number of bytes for PCM16
    val safeLen = bytes.size - (bytes.size % 2)
    if (safeLen <= 0) {
        Log.e("PCM_PLAY", "Invalid PCM byte length=${bytes.size}")
        return null to 0L
    }

    // Convert to ShortArray (LITTLE ENDIAN)
    val shorts = ShortArray(safeLen / 2)
    val bb = ByteBuffer.wrap(bytes, 0, safeLen).order(ByteOrder.LITTLE_ENDIAN)
    for (i in shorts.indices) {
        shorts[i] = bb.short
    }

    val durationMs = (shorts.size * 1000L) / sampleRate

    val track = try {
        AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            shorts.size * 2,
            AudioTrack.MODE_STATIC,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        ).apply {
            write(shorts, 0, shorts.size)
            play()
        }
    } catch (e: Exception) {
        Log.e("PCM_PLAY", "AudioTrack error", e)
        return null to 0L
    }

    Log.e("PCM_PLAY", "Playing PCM file. bytes=${bytes.size} samples=${shorts.size} durationMs=$durationMs")
    return track to durationMs
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(
    onNavigateBack: () -> Unit,
    viewModel: AnalysisViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    // --- Playback states (KEEP ONLY ONE COPY) ---
    var pcmTrack by remember { mutableStateOf<AudioTrack?>(null) }
    var isPlayingPcm by remember { mutableStateOf(false) }

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlayingWav by remember { mutableStateOf(false) }
    var pcmDurationMs by remember { mutableStateOf(0L) }
    var pcmStartUptime by remember { mutableStateOf(0L) }
    var wavPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    LaunchedEffect(uiState.chatMessages.size) {
        if (uiState.chatMessages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(uiState.chatMessages.size - 1)
            }
        }
    }

    // Doctor Visit Dialog
    DoctorVisitDialog(
        isVisible = uiState.showDoctorVisitDialog,
        existingDoctors = uiState.existingDoctors,
        initialData = uiState.recording?.let { rec ->
            DoctorVisitInput(
                doctorName = rec.doctorName ?: "",
                clinicName = rec.hospitalName ?: "",
                visitDate = rec.doctorVisitDate ?: java.time.LocalDate.now(),
                doctorNote = rec.doctorNote ?: "",
                diagnosis = rec.diagnosis ?: "",
                recommendations = rec.recommendations ?: ""
            )
        } ?: DoctorVisitInput(),
        onDismiss = { viewModel.hideDoctorVisitDialog() },
        onSave = { input -> viewModel.saveDoctorVisit(input) }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Signal Analysis",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = OrangePrimary
                        )
                    }
                },
                actions = {

                    IconButton(onClick = { /* Export to PDF */ }) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = "Export PDF",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            LoadingIndicator()
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Recording info and waveform section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = OrangePrimary
                        )
                        Text(
                            text = uiState.recording?.name ?: "Recording",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = OrangePrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Waveform card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            val rec = uiState.recording

                            // 1) Prefer PCM waveform if available
                            var pcmSignal by remember(rec?.pcmFilePath) { mutableStateOf<List<Float>?>(null) }
                            var pcmLoadError by remember(rec?.pcmFilePath) { mutableStateOf<String?>(null) }

                            LaunchedEffect(rec?.pcmFilePath) {
                                pcmSignal = null
                                pcmLoadError = null

                                val path = rec?.pcmFilePath
                                if (!path.isNullOrBlank()) {
                                    try {
                                        pcmSignal = withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            readPcm16LeAsFloat(path)  // [-1..1]
                                        }
                                    } catch (e: Exception) {
                                        pcmLoadError = e.message
                                        pcmSignal = null
                                    }
                                }
                            }

                            // 2) Choose signal source
                            val fallbackSignal = rec?.signalData ?: emptyList()
                            val signal: List<Float> = pcmSignal ?: fallbackSignal

                            // 3) Choose sample rate (use audioSampleRate for PCM audio files)
                            val sampleRateHz = (rec?.audioSampleRate ?: 8000).toFloat()

                            val totalMs = if (signal.isNotEmpty()) ((signal.size / sampleRateHz) * 1000f).toLong() else 0L
                            val currentMs = uiState.playbackMs.coerceIn(0L, totalMs)

                            // 4) Windowing around playhead
                            val playheadSample = ((currentMs / 1000f) * sampleRateHz).toInt()
                                .coerceIn(0, (signal.size - 1).coerceAtLeast(0))

                            val windowSec = 4f
                            val windowSamples = (sampleRateHz * windowSec).toInt().coerceAtLeast(1)
                            val half = windowSamples / 2

                            val start = (playheadSample - half).coerceAtLeast(0)
                            val end = (start + windowSamples).coerceAtMost(signal.size)
                            val window = if (signal.isNotEmpty() && start < end) signal.subList(start, end) else emptyList()

                            HeartSignalWaveform(
                                signalData = window,
                                modifier = Modifier.fillMaxSize(),
                                lineColor = MaterialTheme.colorScheme.onSurface,
                                strokeWidth = 2f
                            )

                            // Bottom-right time text
                            Text(
                                text = "${formatMs(currentMs)} / ${formatMs(totalMs)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                modifier = Modifier.align(Alignment.BottomEnd)
                            )

                            // 5) Dynamic time markers that match the current window
                            val leftMs = ((currentMs - (windowSec * 1000f / 2f).toLong()).coerceAtLeast(0L))
                            val midMs = (leftMs + (windowSec * 1000f / 2f).toLong()).coerceAtMost(totalMs)
                            val rightMs = (leftMs + (windowSec * 1000f).toLong()).coerceAtMost(totalMs)

                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(formatMs(leftMs), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text(formatMs(midMs), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text(formatMs(rightMs), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            }

                            // Playback indicator (center line)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .width(2.dp)
                                    .fillMaxHeight()
                                    .background(RedWarning)
                            )

                            // Optional: show a tiny debug hint if PCM failed (remove later)
                            if (pcmSignal == null && !rec?.pcmFilePath.isNullOrBlank() && pcmLoadError != null) {
                                Text(
                                    text = "PCM load failed",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RedWarning,
                                    modifier = Modifier.align(Alignment.BottomStart)
                                )
                            }
                        }
                    }


                    Spacer(modifier = Modifier.height(12.dp))

                    // BPM Summary + Playback (WAV file via MediaPlayer)
                    val recording = uiState.recording



                    LaunchedEffect(isPlayingWav, wavPlayer) {
                        while (isPlayingWav && wavPlayer != null) {
                            viewModel.setPlaybackMs(wavPlayer?.currentPosition?.toLong() ?: 0L)
                            delay(33)
                        }
                    }


                    // PCM -> simulate playhead since AudioTrack MODE_STATIC has no position callback
                    LaunchedEffect(isPlayingPcm, pcmDurationMs, pcmStartUptime) {
                        while (isPlayingPcm) {
                            val elapsed = SystemClock.uptimeMillis() - pcmStartUptime
                            val clamped = elapsed.coerceIn(0L, pcmDurationMs)
                            viewModel.setPlaybackMs(clamped)

                            if (elapsed >= pcmDurationMs) {
                                // auto-stop
                                try { pcmTrack?.stop() } catch (_: Exception) {}
                                try { pcmTrack?.release() } catch (_: Exception) {}
                                pcmTrack = null
                                isPlayingPcm = false
                                viewModel.setPlaybackMs(0L)
                                break
                            }
                            delay(33)
                        }
                    }



                    // Cleanup when user switches recordings
                    DisposableEffect(recording?.id) {
                        onDispose {
                            try { pcmTrack?.stop() } catch (_: Exception) {}
                            try { pcmTrack?.release() } catch (_: Exception) {}
                            pcmTrack = null
                            isPlayingPcm = false

                            try { wavPlayer?.stop() } catch (_: Exception) {}
                            try { wavPlayer?.release() } catch (_: Exception) {}
                            wavPlayer = null
                            isPlayingWav = false

                            viewModel.setPlaybackMs(0L)
                        }
                    }




                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("BPM Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                Text("Average: ${"%.1f".format(recording?.averageBpm ?: 0f)} BPM")
                                Text("Max: ${recording?.maxBpm ?: 0} BPM")
                            }





// Cleanup


                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {

                                // -------- PLAY WAV (MediaPlayer) --------
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    IconButton(
                                        onClick = {
                                            val rec = recording ?: return@IconButton
                                            val path = rec.wavFilePath

                                            // stop PCM if playing
                                            if (isPlayingPcm) {
                                                try { pcmTrack?.stop() } catch (_: Exception) {}
                                                try { pcmTrack?.release() } catch (_: Exception) {}
                                                pcmTrack = null
                                                isPlayingPcm = false
                                                viewModel.setPlaybackMs(0L)
                                            }

                                            if (path.isNullOrBlank()) return@IconButton
                                            val file = File(path)
                                            if (!file.exists() || file.length() <= 44) return@IconButton

                                            if (!isPlayingWav) {
                                                try {
                                                    try { wavPlayer?.release() } catch (_: Exception) {}
                                                    wavPlayer = null

                                                    viewModel.setPlaybackMs(0L)

                                                    val uri = Uri.fromFile(file)
                                                    val mp = MediaPlayer().apply {
                                                        setDataSource(context, uri)
                                                        setOnPreparedListener {
                                                            start()
                                                            isPlayingWav = true
                                                        }
                                                        setOnCompletionListener {
                                                            isPlayingWav = false
                                                            viewModel.setPlaybackMs(0L)
                                                            try { release() } catch (_: Exception) {}
                                                            wavPlayer = null
                                                        }
                                                        setOnErrorListener { _, what, extra ->
                                                            Log.e("AUDIO", "MediaPlayer error what=$what extra=$extra")
                                                            isPlayingWav = false
                                                            true
                                                        }
                                                        prepareAsync()
                                                    }
                                                    wavPlayer = mp
                                                } catch (e: Exception) {
                                                    Log.e("AUDIO", "MediaPlayer exception", e)
                                                    isPlayingWav = false
                                                }
                                            } else {
                                                try { wavPlayer?.stop() } catch (_: Exception) {}
                                                try { wavPlayer?.release() } catch (_: Exception) {}
                                                wavPlayer = null
                                                isPlayingWav = false
                                                viewModel.setPlaybackMs(0L)
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (isPlayingWav) Icons.Default.Pause else Icons.Default.PlayCircleFilled,
                                            contentDescription = "Play Audio",
                                            tint = OrangePrimary
                                        )
                                    }
                                    Text("Play", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                }

                                // -------- DOWNLOAD PCM --------
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    IconButton(
                                        onClick = {
                                            val rec = recording ?: return@IconButton
                                            val pcmPath = rec.pcmFilePath ?: return@IconButton

                                            // Use recording name for filename
                                            val fileName = sanitizeFileName(rec.name) + ".pcm"

                                            val uri = exportToDownloads(
                                                context = context,
                                                srcPath = pcmPath,
                                                outName = fileName,
                                                mimeType = "application/octet-stream"
                                            )

                                            if (uri != null) {
                                                Toast.makeText(context, "PCM saved to Downloads", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Failed to export PCM", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FileDownload,
                                            contentDescription = "Download PCM",
                                            tint = OrangePrimary
                                        )
                                    }
                                    Text("PCM", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                }

                                // -------- DOWNLOAD WAV --------
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    IconButton(
                                        onClick = {
                                            val rec = recording ?: return@IconButton
                                            val wavPath = rec.wavFilePath ?: return@IconButton

                                            // Use recording name for filename
                                            val fileName = sanitizeFileName(rec.name) + ".wav"

                                            val uri = exportToDownloads(
                                                context = context,
                                                srcPath = wavPath,
                                                outName = fileName,
                                                mimeType = "audio/wav"
                                            )

                                            if (uri != null) {
                                                Toast.makeText(context, "WAV saved to Downloads", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Failed to export WAV", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LibraryMusic,
                                            contentDescription = "Download WAV",
                                            tint = OrangePrimary
                                        )
                                    }
                                    Text("WAV", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                }
                            }



                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Doctor Visit Info Card - NEW FEATURE
                    DoctorVisitInfoCard(
                        doctorName = recording?.doctorName,
                        clinicName = recording?.hospitalName,
                        visitDate = recording?.doctorVisitDate,
                        doctorNote = recording?.doctorNote,
                        diagnosis = recording?.diagnosis,
                        recommendations = recording?.recommendations,
                        onEditClick = { viewModel.showDoctorVisitDialog() }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // AI Analysis section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // AI Analysis header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = YellowCaution,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AI Analysis",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        if (uiState.isAnalyzing) {
                            Spacer(modifier = Modifier.width(8.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = OrangePrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Analysis results
                    if (uiState.analysis != null || uiState.recording?.aiAnalysis != null) {
                        val analysis = uiState.analysis ?: uiState.recording?.aiAnalysis

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val avg = uiState.recording?.averageBpm ?: 0f
                            AnalysisResultChip(
                                text = "Heart rate is ${analysis?.heartRateStatus ?: "normal"}! Avg BPM ${"%.1f".format(avg)}.",
                                isWarning = analysis?.heartRateStatus == "too fast" || analysis?.heartRateStatus == "too slow"
                            )

                            analysis?.detectedConditions?.forEach { condition ->
                                AnalysisResultChip(
                                    text = "${condition.probability}% of ${condition.name} detected!",
                                    isWarning = condition.probability > 50
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Chat messages
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.chatMessages) { message ->
                            ChatMessageBubble(message = message)
                        }

                        if (uiState.isChatLoading) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Card(
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            repeat(3) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(8.dp),
                                                    strokeWidth = 2.dp,
                                                    color = OrangePrimary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Chat input
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Ask about your heart health...") },
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = OrangePrimary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            maxLines = 3
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        FloatingActionButton(
                            onClick = {
                                if (messageText.isNotBlank()) {
                                    viewModel.sendChatMessage(messageText)
                                    messageText = ""
                                }
                            },
                            containerColor = OrangePrimary,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}



private fun playWaveformPcmFromSignal(
    signal: List<Float>,
    sampleRate: Int
): AudioTrack? {
    if (signal.isEmpty()) return null

    val pcm = ShortArray(signal.size)
    for (i in signal.indices) {
        val v = (signal[i] * 30000f).toInt()
        pcm[i] = v.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }

    val track = AudioTrack(
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build(),
        AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build(),
        pcm.size * 2,
        AudioTrack.MODE_STATIC,
        AudioManager.AUDIO_SESSION_ID_GENERATE
    )

    track.write(pcm, 0, pcm.size)
    track.play()
    return track
}

private fun exportToDownloads(
    context: android.content.Context,
    srcPath: String,
    outName: String,
    mimeType: String
): Uri? {
    val src = File(srcPath)
    if (!src.exists() || src.length() <= 0) return null

    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, outName)
        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }

    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null

    resolver.openOutputStream(uri)?.use { out ->
        FileInputStream(src).use { input ->
            input.copyTo(out)
        }
    }

    values.clear()
    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
    resolver.update(uri, values, null, null)

    return uri
}


@Composable
private fun AnalysisResultChip(
    text: String,
    isWarning: Boolean
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isWarning) RedWarning else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ChatMessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isFromUser) Arrangement.End else Arrangement.Start
    ) {
        if (!message.isFromUser) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(OrangePrimary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            shape = RoundedCornerShape(
                topStart = if (message.isFromUser) 16.dp else 4.dp,
                topEnd = if (message.isFromUser) 4.dp else 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (message.isFromUser)
                    OrangePrimary
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = if (message.isFromUser) Color.White else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(12.dp)
            )
        }

        if (message.isFromUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }


}

@Composable
private fun LabeledIconButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = label, tint = OrangePrimary)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}
private fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000).toInt().coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return String.format("%02d:%02d", m, s)
}

/**
 * Sanitize recording name for use as filename
 * Removes invalid characters and limits length
 */
private fun sanitizeFileName(name: String): String {
    return name
        .replace(Regex("[^a-zA-Z0-9._-]"), "_")  // Replace invalid chars with underscore
        .replace(Regex("_+"), "_")               // Remove consecutive underscores
        .trim('_')                               // Remove leading/trailing underscores
        .take(50)                                // Limit to 50 characters
        .ifBlank { "recording" }                 // Fallback if empty
}

/**
 * Reads raw PCM 16-bit little-endian MONO file into floats [-1..1].
 * If your PCM is stereo, tell me and I’ll adjust the parser.
 */
private fun readPcm16LeAsFloat(path: String): List<Float> {
    val file = java.io.File(path)
    if (!file.exists()) throw IllegalArgumentException("PCM file not found: $path")
    if (file.length() < 4) throw IllegalArgumentException("PCM file too small: $path")

    val out = ArrayList<Float>((file.length() / 2).toInt().coerceAtMost(1_000_000))
    java.io.FileInputStream(file).use { input ->
        val buf = ByteArray(64 * 1024)
        var carry: Int? = null

        while (true) {
            val n = input.read(buf)
            if (n <= 0) break

            var i = 0
            if (carry != null) {
                // complete the previous sample (1 byte left)
                val lo = carry!!
                val hi = buf[0].toInt()
                val s = ((hi shl 8) or (lo and 0xFF)).toShort()
                out.add((s.toInt() / 32768f).coerceIn(-1f, 1f))
                carry = null
                i = 1
            }

            while (i + 1 < n) {
                val lo = buf[i].toInt()
                val hi = buf[i + 1].toInt()
                val s = ((hi shl 8) or (lo and 0xFF)).toShort()
                out.add((s.toInt() / 32768f).coerceIn(-1f, 1f))
                i += 2
            }

            if (i < n) {
                carry = buf[i].toInt()
            }
        }
    }
    return out
}

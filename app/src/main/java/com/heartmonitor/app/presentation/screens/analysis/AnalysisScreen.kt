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
import com.heartmonitor.app.presentation.components.HeartSignalWaveform
import com.heartmonitor.app.presentation.components.LoadingIndicator
import com.heartmonitor.app.presentation.theme.*
import com.heartmonitor.app.presentation.viewmodel.AnalysisViewModel
import kotlinx.coroutines.launch
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


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

    LaunchedEffect(uiState.chatMessages.size) {
        if (uiState.chatMessages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(uiState.chatMessages.size - 1)
            }
        }
    }

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
                    IconButton(onClick = { viewModel.togglePlay() }) {
                        Icon(
                            imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
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
                            val recording = uiState.recording
                            val signal = recording?.signalData ?: emptyList()

// ✅ assume ESP32 sample rate
                            val sampleRate = 8000f

// ✅ playhead sample index from playbackMs
                            val playheadSample = ((uiState.playbackMs / 1000f) * sampleRate).toInt()
                                .coerceIn(0, (signal.size - 1).coerceAtLeast(0))

// ✅ show a sliding window so it feels animated
                            val windowSamples = (sampleRate * 4f).toInt() // show ~4 seconds width like your UI
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


                            // Time markers
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("00:00", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text("00:02", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text("00:04", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            }
                            
                            // Playback indicator
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .width(2.dp)
                                    .fillMaxHeight()
                                    .background(RedWarning)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

// ✅ BPM Summary + Playback
                    val recording = uiState.recording
                    var isPlaying by remember { mutableStateOf(false) }
                    var audioTrack by remember { mutableStateOf<AudioTrack?>(null) }

                    DisposableEffect(Unit) {
                        onDispose {
                            try { audioTrack?.stop() } catch (_: Exception) {}
                            try { audioTrack?.release() } catch (_: Exception) {}
                            audioTrack = null
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

                            IconButton(
                                onClick = {
                                    val rec = recording ?: return@IconButton

                                    coroutineScope.launch {
                                        if (!isPlaying) {
                                            // start
                                            try { audioTrack?.release() } catch (_: Exception) {}
                                            audioTrack = playWaveform(rec.signalData, rec.sampleRateHz)
                                            isPlaying = audioTrack != null
                                        } else {
                                            // stop
                                            try { audioTrack?.stop() } catch (_: Exception) {}
                                            try { audioTrack?.release() } catch (_: Exception) {}
                                            audioTrack = null
                                            isPlaying = false
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = OrangePrimary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }

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
                            // Heart rate status
                            val avg = uiState.recording?.averageBpm ?: 0f
                            AnalysisResultChip(
                                text = "Heart rate is ${analysis?.heartRateStatus ?: "normal"}! Avg BPM ${"%.1f".format(avg)}.",
                                isWarning = analysis?.heartRateStatus == "too fast" || analysis?.heartRateStatus == "too slow"
                            )


                            // Detected conditions
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

private suspend fun playWaveform(
    signal: List<Float>,
    sampleRate: Int
): AudioTrack? = withContext(Dispatchers.Default) {
    if (signal.isEmpty()) return@withContext null

    // Convert Float samples back to PCM16.
    // Your signalData values are approx original_int16 / 1000f.
    // So multiply by 1000 to recover int16-ish magnitude.
    val pcm = ShortArray(signal.size)
    for (i in signal.indices) {
        val v = (signal[i] * 1000f).toInt()
        val clamped = v.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        pcm[i] = clamped.toShort()
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
    track
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

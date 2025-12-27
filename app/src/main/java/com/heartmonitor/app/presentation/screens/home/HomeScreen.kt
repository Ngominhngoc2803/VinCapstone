package com.heartmonitor.app.presentation.screens.home

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.heartmonitor.app.bluetooth.BleConnectionState
import com.heartmonitor.app.presentation.components.*
import com.heartmonitor.app.presentation.theme.*
import com.heartmonitor.app.presentation.viewmodel.BpmDataPoint
import com.heartmonitor.app.presentation.viewmodel.HomeViewModel
import com.heartmonitor.app.utils.DateTimeUtils
import com.heartmonitor.app.presentation.viewmodel.BpmHistoryPoint


@RequiresApi(Build.VERSION_CODES.S)
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToAnalysis: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val bleConnectionState by viewModel.bleConnectionState.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    
    var showDeviceDialog by remember { mutableStateOf(false) }
    var showRecordingDialog by remember { mutableStateOf(false) }
    var recordingName by remember { mutableStateOf("") }
    
    val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        listOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
    
    val permissionState = rememberMultiplePermissionsState(bluetoothPermissions)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Storage & Statistics",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        
        item {
            ConnectionStatusCard(
                connectionState = bleConnectionState,
                isScanning = isScanning,
                onScanClick = {
                    if (permissionState.allPermissionsGranted) {
                        if (isScanning) {
                            viewModel.stopBleScan()
                        } else {
                            viewModel.startBleScan()
                            showDeviceDialog = true
                        }
                    } else {
                        permissionState.launchMultiplePermissionRequest()
                    }
                },
                onDisconnectClick = { viewModel.disconnect() }
            )
        }
        
        item {
            RecordingControlCard(
                isRecording = uiState.isRecording,
                recordingDuration = uiState.recordingDuration,
                currentBpm = uiState.currentBpm,
                signalData = uiState.currentSignalData,
                onStartRecording = { showRecordingDialog = true },
                onStopRecording = { viewModel.stopRecording() }
            )
        }

        item {
            if (uiState.recordings.isNotEmpty()) {

                val historyPoints = uiState.recordings
                    .sortedBy { it.timestamp }
                    .map { rec ->
                        BpmHistoryPoint(
                            date = rec.timestamp.toLocalDate(),
                            bpm = rec.averageBpm
                        )
                    }

                Text(
                    text = "BPM History",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                ) {
                    // Y-axis labels
                    Column(
                        modifier = Modifier
                            .width(42.dp)
                            .fillMaxHeight()
                            .padding(vertical = 16.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.End
                    ) {
                        val minBpm = historyPoints.minOf { it.bpm }.toInt()
                        val maxBpm = historyPoints.maxOf { it.bpm }.toInt()
                        val midBpm = (minBpm + maxBpm) / 2

                        listOf(maxBpm, midBpm, minBpm).forEach { bpm ->
                            Text(
                                text = "$bpm",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Chart
                    BpmChart(
                        points = historyPoints,
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                    )
                }
            }
        }




        item {
            Text(
                text = "Recordings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        
        if (uiState.recordings.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No recordings yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Start a recording to see it here",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        } else {
            items(uiState.recordings) { recording ->
                RecordingCard(
                    recording = recording,
                    onClick = { 
                        viewModel.selectRecording(recording)
                        onNavigateToAnalysis(recording.id) 
                    }
                )
            }
        }
        

        
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
    
    if (showDeviceDialog) {
        DeviceSelectionDialog(
            devices = discoveredDevices,
            isScanning = isScanning,
            onDeviceSelected = { device ->
                viewModel.connectToDevice(device)
                showDeviceDialog = false
            },
            onDismiss = {
                viewModel.stopBleScan()
                showDeviceDialog = false
            }
        )
    }
    
    if (showRecordingDialog) {
        AlertDialog(
            onDismissRequest = { showRecordingDialog = false },
            title = { Text("New Recording") },
            text = {
                OutlinedTextField(
                    value = recordingName,
                    onValueChange = { recordingName = it },
                    label = { Text("Recording Name") },
                    placeholder = { Text("e.g., Input Stethoscope from Home") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = recordingName.ifBlank { "Recording ${uiState.recordings.size + 1}" }
                        viewModel.startRecording(name)
                        recordingName = ""
                        showRecordingDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary)
                ) {
                    Text("Start")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRecordingDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}



@Composable
private fun ConnectionStatusCard(
    connectionState: BleConnectionState,
    isScanning: Boolean,
    onScanClick: () -> Unit,
    onDisconnectClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint = when (connectionState) {
                        BleConnectionState.READY -> GreenGood
                        BleConnectionState.CONNECTING, BleConnectionState.CONNECTED -> YellowCaution
                        BleConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = when (connectionState) {
                            BleConnectionState.READY -> "Connected"
                            BleConnectionState.CONNECTING -> "Connecting..."
                            BleConnectionState.CONNECTED -> "Discovering services..."
                            BleConnectionState.DISCONNECTED -> "Disconnected"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "ESP32 Stethoscope",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            if (connectionState == BleConnectionState.READY) {
                TextButton(onClick = onDisconnectClick) {
                    Text("Disconnect", color = RedWarning)
                }
            } else {
                Button(
                    onClick = onScanClick,
                    colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                    enabled = connectionState == BleConnectionState.DISCONNECTED
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (isScanning) "Scanning..." else "Connect")
                }
            }
        }
    }
}

@Composable
private fun RecordingControlCard(
    isRecording: Boolean,
    recordingDuration: Long,
    currentBpm: Int,
    signalData: List<Float>,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isRecording) 
                RedWarning.copy(alpha = 0.1f) 
            else 
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isRecording) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(8.dp)
                ) {
                    HeartSignalWaveform(
                        signalData = signalData,
                        modifier = Modifier.fillMaxSize(),
                        lineColor = RedWarning,
                        strokeWidth = 2f
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = DateTimeUtils.formatDuration(recordingDuration),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = RedWarning
                        )
                        Text(
                            text = "Duration",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    AnimatedRecordingButton(
                        isRecording = true,
                        onStartRecording = {},
                        onStopRecording = onStopRecording
                    )
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$currentBpm",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                currentBpm > 120 -> RedWarning
                                currentBpm < 60 -> YellowCaution
                                else -> GreenGood
                            }
                        )
                        Text(
                            text = "BPM",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    AnimatedRecordingButton(
                        isRecording = false,
                        onStartRecording = onStartRecording,
                        onStopRecording = {}
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column {
                        Text(
                            text = "Start Recording",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Tap to begin heart sound capture",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceSelectionDialog(
    devices: List<BluetoothDevice>,
    isScanning: Boolean,
    onDeviceSelected: (BluetoothDevice) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Select Device")
                if (isScanning) {
                    Spacer(modifier = Modifier.width(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        },
        text = {
            if (devices.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = OrangePrimary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Searching for devices...",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Make sure your ESP32 stethoscope is powered on",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn {
                    items(devices) { device ->
                        DeviceItem(
                            device = device,
                            onClick = { onDeviceSelected(device) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DeviceItem(
    device: BluetoothDevice,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Bluetooth,
                contentDescription = null,
                tint = OrangePrimary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                @Suppress("MissingPermission")
                Text(
                    text = device.name ?: "Unknown Device",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

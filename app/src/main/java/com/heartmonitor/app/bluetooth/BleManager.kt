package com.heartmonitor.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleManager @Inject constructor(
    private val context: Context
) {
    private val bluetoothManager: BluetoothManager? = 
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val bleScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    
    private var bluetoothGatt: BluetoothGatt? = null
    
    private val _connectionState = MutableStateFlow(BleConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()
    
    private val _heartSignalData = MutableSharedFlow<List<Float>>(replay = 1)
    val heartSignalData: SharedFlow<List<Float>> = _heartSignalData.asSharedFlow()
    
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    
    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices.asStateFlow()
    
    // ESP32 Heart Monitor Service UUID - customize this to match your ESP32 firmware
    companion object {
        val HEART_MONITOR_SERVICE_UUID: UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_MEASUREMENT_UUID: UUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb")
        val HEART_SIGNAL_DATA_UUID: UUID = UUID.fromString("00002A38-0000-1000-8000-00805f9b34fb")
        val CLIENT_CHARACTERISTIC_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
    
    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val currentDevices = _discoveredDevices.value.toMutableList()
            if (!currentDevices.any { it.address == device.address }) {
                currentDevices.add(device)
                _discoveredDevices.value = currentDevices
            }
        }
        
        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
        }
    }
    
    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = BleConnectionState.CONNECTED
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = BleConnectionState.DISCONNECTED
                    bluetoothGatt?.close()
                    bluetoothGatt = null
                }
            }
        }
        
        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(HEART_MONITOR_SERVICE_UUID)
                service?.let {
                    val signalCharacteristic = it.getCharacteristic(HEART_SIGNAL_DATA_UUID)
                    signalCharacteristic?.let { char ->
                        gatt.setCharacteristicNotification(char, true)
                        val descriptor = char.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
                        descriptor?.let { desc ->
                            desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(desc)
                        }
                    }
                }
                _connectionState.value = BleConnectionState.READY
            }
        }
        
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == HEART_SIGNAL_DATA_UUID) {
                val data = parseHeartSignalData(characteristic.value)
                _heartSignalData.tryEmit(data)
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    fun startScan() {
        if (_isScanning.value) return
        
        _discoveredDevices.value = emptyList()
        _isScanning.value = true
        
        val scanFilters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(HEART_MONITOR_SERVICE_UUID))
                .build()
        )
        
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        
        bleScanner?.startScan(scanFilters, scanSettings, scanCallback)
    }
    
    @SuppressLint("MissingPermission")
    fun stopScan() {
        _isScanning.value = false
        bleScanner?.stopScan(scanCallback)
    }
    
    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        _connectionState.value = BleConnectionState.CONNECTING
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }
    
    @SuppressLint("MissingPermission")
    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _connectionState.value = BleConnectionState.DISCONNECTED
    }
    
    private fun parseHeartSignalData(data: ByteArray): List<Float> {
        // Parse the raw bytes from ESP32 into float values
        // This implementation assumes the ESP32 sends 16-bit signed integers
        // Adjust based on your actual ESP32 firmware implementation
        val signalPoints = mutableListOf<Float>()
        
        var i = 0
        while (i + 1 < data.size) {
            val value = ((data[i + 1].toInt() and 0xFF) shl 8) or (data[i].toInt() and 0xFF)
            val signedValue = if (value > 32767) value - 65536 else value
            signalPoints.add(signedValue / 1000f) // Normalize to -32.768 to 32.767 range
            i += 2
        }
        
        return signalPoints
    }
    
    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true
}

enum class BleConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    READY
}

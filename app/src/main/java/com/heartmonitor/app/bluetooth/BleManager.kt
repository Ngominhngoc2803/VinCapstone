package com.heartmonitor.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Build.*
import android.os.ParcelUuid
import androidx.annotation.RequiresApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

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

    private val _heartAudioData = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = 64
    )
    val heartAudioData: SharedFlow<ByteArray> = _heartAudioData

    private val _heartSignalData = MutableSharedFlow<List<Float>>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val heartSignalData: SharedFlow<List<Float>> = _heartSignalData.asSharedFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices.asStateFlow()

    private val _bpm = MutableStateFlow<Float?>(null)
    val bpm: StateFlow<Float?> = _bpm.asStateFlow()



    // ESP32 Heart Monitor Service UUID - customize this to match your ESP32 firmware
    companion object {
        val HEART_MONITOR_SERVICE_UUID: UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_MEASUREMENT_UUID: UUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb")
        val HEART_SIGNAL_DATA_UUID: UUID = UUID.fromString("00002A38-0000-1000-8000-00805f9b34fb")
        val CLIENT_CHARACTERISTIC_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }


    private val cccdQueue: ArrayDeque<BluetoothGattDescriptor> = ArrayDeque()

    @SuppressLint("MissingPermission")
    private fun enqueueEnableNotify(gatt: BluetoothGatt, ch: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(ch, true)
        val cccd = ch.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG) ?: return
        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        cccdQueue.add(cccd)
    }
    private var audioChar: BluetoothGattCharacteristic? = null
    @SuppressLint("MissingPermission")
    private fun writeNextCccd(gatt: BluetoothGatt) {
        val next = cccdQueue.removeFirstOrNull()
        if (next == null) {
            _connectionState.value = BleConnectionState.READY
            Log.d("BLE", "All notifications enabled -> READY")
            return
        }

        val ok = gatt.writeDescriptor(next)
        Log.d("BLE", "writeDescriptor CCCD uuid=${next.uuid} ok=$ok")

        if (!ok) {
            // optional: fail fast or retry
            writeNextCccd(gatt)
        }
    }




    private val scanCallback = object : ScanCallback() {

        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return

            val advertisedUuids = result.scanRecord?.serviceUuids?.map { it.uuid } ?: emptyList()
            val hasHeartService = advertisedUuids.contains(HEART_MONITOR_SERVICE_UUID)

            // If it advertises the service, keep it (DON'T rely on name)
            if (!hasHeartService) return

            val current = _discoveredDevices.value.toMutableList()
            if (current.none { it.address == device.address }) {
                current.add(device)
                _discoveredDevices.value = current
                Log.d("BLE", "Found device: addr=${device.address} name=${device.name} rssi=${result.rssi}")
            }
        }

        override fun onScanFailed(errorCode: Int) {
            // 🔍 ADD THIS LOG
            Log.e("BLE", "Scan failed with error code: $errorCode")

            _isScanning.value = false
        }


    }

    private fun parseHeartRateMeasurement(value: ByteArray): Float? {
        if (value.isEmpty()) return null
        val flags = value[0].toInt() and 0xFF
        val isUint16 = (flags and 0x01) != 0

        return if (!isUint16) {
            if (value.size < 2) null else (value[1].toInt() and 0xFF).toFloat()
        } else {
            if (value.size < 3) null
            else {
                val hr = ((value[2].toInt() and 0xFF) shl 8) or (value[1].toInt() and 0xFF)
                hr.toFloat()
            }
        }
    }

    private val pcmChannel = kotlinx.coroutines.channels.Channel<ByteArray>(
        capacity = kotlinx.coroutines.channels.Channel.BUFFERED
    )

    private val _pcmBytes = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val pcmBytes: SharedFlow<ByteArray> = _pcmBytes.asSharedFlow()


    private val gattCallback by lazy {
        val callback = object : BluetoothGattCallback() {
            @RequiresApi(VERSION_CODES.S)
            @SuppressLint("MissingPermission")
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        _connectionState.value = BleConnectionState.CONNECTED
                        bluetoothGatt = gatt
                        gatt.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        _connectionState.value = BleConnectionState.DISCONNECTED

                        try { gatt.close() } catch (_: Exception) {}
                        if (bluetoothGatt == gatt) bluetoothGatt = null

                        // 🔥 let UI see device again
                        startScan(force = true)
                    }
                }
            }

            @SuppressLint("MissingPermission")
            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {
                Log.d("BLE", "onDescriptorWrite status=$status uuid=${descriptor.uuid}")

                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.e("BLE", "CCCD write failed status=$status for ${descriptor.uuid}")
                    // You can choose to stop here, but simplest: continue.
                }

                writeNextCccd(gatt) // ✅ call ONCE only
            }


            private lateinit var upAllNotifications: BluetoothGatt
            private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")




            @SuppressLint("MissingPermission")
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.e("BLE", "Service discovery failed: $status")
                    return
                }

                cccdQueue.clear()

                // Heart service
                val heartService = gatt.getService(HEART_MONITOR_SERVICE_UUID)
                if (heartService == null) {
                    Log.e("BLE", "Heart service not found")
                    return
                }

                val signalChar = heartService.getCharacteristic(HEART_SIGNAL_DATA_UUID)
                val hrChar = heartService.getCharacteristic(HEART_RATE_MEASUREMENT_UUID)

                if (signalChar != null) enqueueEnableNotify(gatt, signalChar) else Log.e("BLE", "2A38 not found")
                if (hrChar != null) enqueueEnableNotify(gatt, hrChar) else Log.e("BLE", "2A37 not found")


                Log.d("BLE", "Queued CCCDs = ${cccdQueue.size}")
                writeNextCccd(gatt) // ✅ start chain once
            }



            @Deprecated("Deprecated in Java")


            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                when (characteristic.uuid) {
                    HEART_SIGNAL_DATA_UUID -> {

                        _heartAudioData.tryEmit(value)              // raw PCM bytes
                        _heartSignalData.tryEmit(parseHeartSignalData(value)) // floats
                    }

                    HEART_RATE_MEASUREMENT_UUID -> {
                        val bpmValue = parseHeartRateMeasurement(value)
                        _bpm.value = bpmValue
                    }

                }
            }



        }
        callback
    }

    @RequiresApi(VERSION_CODES.S)
    @SuppressLint("MissingPermission")
    fun startScan(force: Boolean = false) {
        if (!hasBlePermissions()) {
            Log.e("BLE", "No BLE permissions -> not scanning")
            _isScanning.value = false
            return
        }

        if (_isScanning.value && !force) return

        // Force restart
        stopScan()
        _discoveredDevices.value = emptyList()
        _isScanning.value = true

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(HEART_MONITOR_SERVICE_UUID))
                .build()
        )

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        Log.d("BLE", "Starting scan...")
        bleScanner?.startScan(filters, settings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (_isScanning.value) {
            bleScanner?.stopScan(scanCallback)
        }
        _isScanning.value = false
    }


    @SuppressLint("ObsoleteSdkInt")
    @RequiresApi(VERSION_CODES.S)
    fun hasBlePermissions(): Boolean {
        return if (VERSION.SDK_INT >= VERSION_CODES.S) {
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }


    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        _connectionState.value = BleConnectionState.CONNECTING
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    @RequiresApi(VERSION_CODES.S)
    @SuppressLint("MissingPermission")
    fun disconnect() {
        try {
            bluetoothGatt?.disconnect()
        } catch (_: Exception) { }

        try {
            bluetoothGatt?.close()
        } catch (_: Exception) { }

        bluetoothGatt = null
        _connectionState.value = BleConnectionState.DISCONNECTED

        // 🔥 restart scan so device re-appears in UI
        startScan(force = true)
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

private fun <T> ArrayDeque<T>.removeFirstOrNull(): T? {
    return if (isEmpty()) null else removeFirst()
}


enum class BleConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    READY
}

package com.spop.poverlay.ble

import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * BLE GATT Server for FTMS (Fitness Machine Service)
 * Handles BLE advertising and GATT server operations
 */
class BleFtmsServer(
    private val context: Context,
    private val deviceName: String = "Grupetto FTMS"
) {
    
    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var gattServer: BluetoothGattServer? = null
    
    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val _connectionCount = MutableStateFlow(0)
    val connectionCount: StateFlow<Int> = _connectionCount.asStateFlow()
    
    private val connectedDevices = mutableSetOf<BluetoothDevice>()
    private val subscribedDevices = mutableMapOf<String, MutableSet<BluetoothGattCharacteristic>>()
    
    // GATT characteristics
    private var indoorBikeDataCharacteristic: BluetoothGattCharacteristic? = null
    private var fitnessMachineStatusCharacteristic: BluetoothGattCharacteristic? = null
    private var fitnessMachineControlPointCharacteristic: BluetoothGattCharacteristic? = null
    
    fun initialize(): Boolean {
        bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        
        if (bluetoothAdapter == null) {
            Timber.e("Bluetooth adapter is null")
            return false
        }
        
        if (!bluetoothAdapter!!.isEnabled) {
            Timber.e("Bluetooth is not enabled")
            return false
        }
        
        bluetoothLeAdvertiser = bluetoothAdapter!!.bluetoothLeAdvertiser
        if (bluetoothLeAdvertiser == null) {
            Timber.e("Bluetooth LE Advertiser is not available")
            return false
        }
        
        return true
    }
    
    fun startServer(): Boolean {
        if (!initialize()) {
            return false
        }
        
        try {
            gattServer = bluetoothManager?.openGattServer(context, gattServerCallback)
            if (gattServer == null) {
                Timber.e("Failed to create GATT server")
                return false
            }
            
            setupGattServices()
            startAdvertising()
            
            return true
        } catch (e: SecurityException) {
            Timber.e(e, "Security exception when starting BLE server")
            return false
        }
    }
    
    fun stopServer() {
        try {
            stopAdvertising()
            gattServer?.close()
            gattServer = null
            connectedDevices.clear()
            subscribedDevices.clear()
            _isConnected.value = false
            _connectionCount.value = 0
        } catch (e: SecurityException) {
            Timber.e(e, "Security exception when stopping BLE server")
        }
    }
    
    private fun setupGattServices() {
        // Create FTMS service
        val ftmsService = BluetoothGattService(
            FtmsUuids.FITNESS_MACHINE_SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        
        // Fitness Machine Feature characteristic (read-only)
        val fitnessMachineFeatureCharacteristic = BluetoothGattCharacteristic(
            FtmsUuids.FITNESS_MACHINE_FEATURE_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        ftmsService.addCharacteristic(fitnessMachineFeatureCharacteristic)
        
        // Indoor Bike Data characteristic (notify)
        indoorBikeDataCharacteristic = BluetoothGattCharacteristic(
            FtmsUuids.INDOOR_BIKE_DATA_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        val indoorBikeDataDescriptor = BluetoothGattDescriptor(
            FtmsUuids.CLIENT_CHARACTERISTIC_CONFIG_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        // Initialize descriptor with default value (notifications disabled)
        indoorBikeDataDescriptor.value = byteArrayOf(0x00, 0x00)
        indoorBikeDataCharacteristic!!.addDescriptor(indoorBikeDataDescriptor)
        ftmsService.addCharacteristic(indoorBikeDataCharacteristic!!)
        
        // Fitness Machine Status characteristic (notify)
        fitnessMachineStatusCharacteristic = BluetoothGattCharacteristic(
            FtmsUuids.FITNESS_MACHINE_STATUS_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        val statusDescriptor = BluetoothGattDescriptor(
            FtmsUuids.CLIENT_CHARACTERISTIC_CONFIG_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        // Initialize descriptor with default value (notifications disabled)
        statusDescriptor.value = byteArrayOf(0x00, 0x00)
        fitnessMachineStatusCharacteristic!!.addDescriptor(statusDescriptor)
        ftmsService.addCharacteristic(fitnessMachineStatusCharacteristic!!)
        
        // Fitness Machine Control Point characteristic (write, indicate)
        fitnessMachineControlPointCharacteristic = BluetoothGattCharacteristic(
            FtmsUuids.FITNESS_MACHINE_CONTROL_POINT_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_INDICATE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        val controlPointDescriptor = BluetoothGattDescriptor(
            FtmsUuids.CLIENT_CHARACTERISTIC_CONFIG_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        // Initialize descriptor with default value (indications disabled)
        controlPointDescriptor.value = byteArrayOf(0x00, 0x00)
        fitnessMachineControlPointCharacteristic!!.addDescriptor(controlPointDescriptor)
        ftmsService.addCharacteristic(fitnessMachineControlPointCharacteristic!!)
        
        // Supported Power Range characteristic (read-only)
        val supportedPowerRangeCharacteristic = BluetoothGattCharacteristic(
            FtmsUuids.SUPPORTED_POWER_RANGE_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        ftmsService.addCharacteristic(supportedPowerRangeCharacteristic)
        
        // Supported Resistance Level Range characteristic (read-only)
        val supportedResistanceRangeCharacteristic = BluetoothGattCharacteristic(
            FtmsUuids.SUPPORTED_RESISTANCE_LEVEL_RANGE_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        ftmsService.addCharacteristic(supportedResistanceRangeCharacteristic)
        
        // Add service to server
        gattServer?.addService(ftmsService)
        
        // Create Device Information Service
        val deviceInfoService = BluetoothGattService(
            FtmsUuids.DEVICE_INFORMATION_SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        
        // Manufacturer Name
        val manufacturerNameCharacteristic = BluetoothGattCharacteristic(
            FtmsUuids.MANUFACTURER_NAME_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        deviceInfoService.addCharacteristic(manufacturerNameCharacteristic)
        
        // Model Number
        val modelNumberCharacteristic = BluetoothGattCharacteristic(
            FtmsUuids.MODEL_NUMBER_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        deviceInfoService.addCharacteristic(modelNumberCharacteristic)
        
        // Add device info service
        gattServer?.addService(deviceInfoService)
    }
    
    private fun startAdvertising() {
        try {
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(true)
                .build()
            
            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(ParcelUuid(FtmsUuids.FITNESS_MACHINE_SERVICE_UUID))
                .build()
            
            bluetoothLeAdvertiser?.startAdvertising(settings, data, advertiseCallback)
        } catch (e: SecurityException) {
            Timber.e(e, "Security exception when starting advertising")
        }
    }
    
    private fun stopAdvertising() {
        try {
            bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
            _isAdvertising.value = false
        } catch (e: SecurityException) {
            Timber.e(e, "Security exception when stopping advertising")
        }
    }
    
    fun sendIndoorBikeData(ftmsData: FtmsData) {
        indoorBikeDataCharacteristic?.let { characteristic ->
            try {
                val data = ftmsData.toIndoorBikeDataBytes()
                characteristic.value = data
                Timber.d("Setting indoor bike data: ${data.size} bytes [${data.joinToString(",") { "%02x".format(it) }}], power=${ftmsData.instantaneousPower}, cadence=${ftmsData.instantaneousCadence}")
                
                val notificationResult = notifyCharacteristicChanged(characteristic)
                Timber.d("Notification result: $notificationResult")
            } catch (e: Exception) {
                Timber.e(e, "Error setting indoor bike data")
            }
        } ?: run {
            Timber.w("Indoor bike data characteristic is null, cannot send data")
        }
    }
    
    fun sendFitnessMachineStatus(statusData: ByteArray) {
        fitnessMachineStatusCharacteristic?.let { characteristic ->
            characteristic.value = statusData
            notifyCharacteristicChanged(characteristic)
        }
    }
    
    private fun notifyCharacteristicChanged(characteristic: BluetoothGattCharacteristic): Boolean {
        try {
            val subscribedCount = connectedDevices.count { device ->
                subscribedDevices[device.address]?.contains(characteristic) == true
            }
            
            Timber.d("Notifying ${connectedDevices.size} connected devices, $subscribedCount subscribed to ${characteristic.uuid}")
            
            var allSuccess = true
            connectedDevices.forEach { device ->
                val deviceAddress = device.address
                if (subscribedDevices[deviceAddress]?.contains(characteristic) == true) {
                    val success = gattServer?.notifyCharacteristicChanged(device, characteristic, false) ?: false
                    Timber.d("Notified device $deviceAddress: success=$success")
                    if (!success) allSuccess = false
                } else {
                    Timber.d("Device $deviceAddress not subscribed to ${characteristic.uuid}")
                }
            }
            return allSuccess
        } catch (e: SecurityException) {
            Timber.e(e, "Security exception when notifying characteristic changed")
            return false
        }
    }
    
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Timber.i("BLE advertising started successfully")
            _isAdvertising.value = true
        }
        
        override fun onStartFailure(errorCode: Int) {
            val errorMessage = when (errorCode) {
                ADVERTISE_FAILED_ALREADY_STARTED -> "Already started"
                ADVERTISE_FAILED_DATA_TOO_LARGE -> "Data too large"
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "Feature unsupported"
                ADVERTISE_FAILED_INTERNAL_ERROR -> "Internal error"
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Too many advertisers"
                else -> "Unknown error: $errorCode"
            }
            Timber.e("BLE advertising failed: $errorMessage")
            _isAdvertising.value = false
        }
    }
    
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            device?.let {
                synchronized(this@BleFtmsServer) {
                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            connectedDevices.add(it)
                            subscribedDevices[it.address] = mutableSetOf()
                            _isConnected.value = true
                            _connectionCount.value = connectedDevices.size
                            Timber.i("Device connected: ${it.address}, status: $status")
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            connectedDevices.remove(it)
                            subscribedDevices.remove(it.address)
                            _isConnected.value = connectedDevices.isNotEmpty()
                            _connectionCount.value = connectedDevices.size
                            Timber.i("Device disconnected: ${it.address}, status: $status")
                        }
                    }
                }
            }
        }
        
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic?
        ) {
            var data: ByteArray? = null
            var status = BluetoothGatt.GATT_SUCCESS
            
            when (characteristic?.uuid) {
                FtmsUuids.FITNESS_MACHINE_FEATURE_UUID -> {
                    // Return supported features for indoor bike
                    data = ByteArray(8)
                    val features = FtmsConstants.INDOOR_BIKE_FEATURES
                    val targetSettings = FtmsConstants.INDOOR_BIKE_TARGET_SETTINGS
                    
                    // Pack features (4 bytes) + target settings (4 bytes)
                    data[0] = (features and 0xFF).toByte()
                    data[1] = ((features shr 8) and 0xFF).toByte()
                    data[2] = ((features shr 16) and 0xFF).toByte()
                    data[3] = ((features shr 24) and 0xFF).toByte()
                    data[4] = (targetSettings and 0xFF).toByte()
                    data[5] = ((targetSettings shr 8) and 0xFF).toByte()
                    data[6] = ((targetSettings shr 16) and 0xFF).toByte()
                    data[7] = ((targetSettings shr 24) and 0xFF).toByte()
                }
                FtmsUuids.SUPPORTED_POWER_RANGE_UUID -> {
                    // Min power (0W), Max power (2000W), Min increment (1W)
                    data = byteArrayOf(0, 0, 0xD0.toByte(), 0x07, 1, 0)
                }
                FtmsUuids.SUPPORTED_RESISTANCE_LEVEL_RANGE_UUID -> {
                    // Min resistance (0), Max resistance (100), Min increment (1)
                    data = byteArrayOf(0, 0, 100, 0, 1, 0)
                }
                FtmsUuids.MANUFACTURER_NAME_UUID -> {
                    data = deviceName.toByteArray()
                }
                FtmsUuids.MODEL_NUMBER_UUID -> {
                    data = "Peloton FTMS Bridge".toByteArray()
                }
                else -> {
                    status = BluetoothGatt.GATT_READ_NOT_PERMITTED
                }
            }
            
            try {
                gattServer?.sendResponse(device, requestId, status, 0, data)
            } catch (e: SecurityException) {
                Timber.e(e, "Security exception when sending response")
            }
        }
        
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            descriptor: BluetoothGattDescriptor?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            var status = BluetoothGatt.GATT_SUCCESS
            
            try {
                Timber.d("Descriptor write request from ${device?.address}: ${descriptor?.uuid}, value=${value?.contentToString()}")
                
                if (descriptor?.uuid == FtmsUuids.CLIENT_CHARACTERISTIC_CONFIG_UUID) {
                    val characteristic = descriptor.characteristic
                    val deviceAddress = device?.address
                    
                    if (value != null && value.size >= 2 && deviceAddress != null && characteristic != null) {
                        // Set the descriptor value first
                        descriptor.value = value
                        
                        val enabled = (value[0].toInt() and 0x01) != 0 || (value[0].toInt() and 0x02) != 0
                        
                        // Ensure device exists in subscribed devices map
                        if (subscribedDevices[deviceAddress] == null) {
                            subscribedDevices[deviceAddress] = mutableSetOf()
                        }
                        
                        if (enabled) {
                            subscribedDevices[deviceAddress]?.add(characteristic)
                            Timber.i("Device $deviceAddress subscribed to ${characteristic.uuid}")
                        } else {
                            subscribedDevices[deviceAddress]?.remove(characteristic)
                            Timber.i("Device $deviceAddress unsubscribed from ${characteristic.uuid}")
                        }
                    } else {
                        status = BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH
                        Timber.w("Invalid descriptor write request: value=${value?.contentToString()}, size=${value?.size}")
                    }
                } else {
                    status = BluetoothGatt.GATT_WRITE_NOT_PERMITTED
                    Timber.w("Write not permitted for descriptor: ${descriptor?.uuid}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Exception in onDescriptorWriteRequest")
                status = BluetoothGatt.GATT_FAILURE
            }
            
            if (responseNeeded) {
                try {
                    gattServer?.sendResponse(device, requestId, status, 0, null)
                    Timber.d("Sent descriptor write response: status=$status")
                } catch (e: SecurityException) {
                    Timber.e(e, "Security exception when sending response")
                }
            }
        }
        
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            var status = BluetoothGatt.GATT_SUCCESS
            
            when (characteristic?.uuid) {
                FtmsUuids.FITNESS_MACHINE_CONTROL_POINT_UUID -> {
                    // Handle control point commands
                    if (value != null && value.isNotEmpty()) {
                        handleControlPointCommand(device, value[0])
                    } else {
                        status = BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH
                    }
                }
                else -> {
                    status = BluetoothGatt.GATT_WRITE_NOT_PERMITTED
                }
            }
            
            if (responseNeeded) {
                try {
                    gattServer?.sendResponse(device, requestId, status, 0, null)
                } catch (e: SecurityException) {
                    Timber.e(e, "Security exception when sending response")
                }
            }
        }
    }
    
    private fun handleControlPointCommand(device: BluetoothDevice?, opCode: Byte) {
        val response = when (opCode) {
            FtmsConstants.FTMS_CONTROL_REQUEST_CONTROL -> {
                Timber.d("Control requested by ${device?.address}")
                byteArrayOf(0x80.toByte(), opCode, FtmsConstants.FTMS_RESPONSE_SUCCESS)
            }
            FtmsConstants.FTMS_CONTROL_START_OR_RESUME -> {
                Timber.d("Start/Resume requested by ${device?.address}")
                // Send status notification
                sendFitnessMachineStatus(byteArrayOf(FtmsConstants.STATUS_STARTED_BY_EXTERNAL))
                byteArrayOf(0x80.toByte(), opCode, FtmsConstants.FTMS_RESPONSE_SUCCESS)
            }
            FtmsConstants.FTMS_CONTROL_STOP_OR_PAUSE -> {
                Timber.d("Stop/Pause requested by ${device?.address}")
                // Send status notification
                sendFitnessMachineStatus(byteArrayOf(FtmsConstants.STATUS_PAUSED_BY_EXTERNAL))
                byteArrayOf(0x80.toByte(), opCode, FtmsConstants.FTMS_RESPONSE_SUCCESS)
            }
            FtmsConstants.FTMS_CONTROL_RESET -> {
                Timber.d("Reset requested by ${device?.address}")
                // Send status notification
                sendFitnessMachineStatus(byteArrayOf(FtmsConstants.STATUS_RESET))
                byteArrayOf(0x80.toByte(), opCode, FtmsConstants.FTMS_RESPONSE_SUCCESS)
            }
            else -> {
                Timber.d("Unsupported op code: $opCode from ${device?.address}")
                byteArrayOf(0x80.toByte(), opCode, FtmsConstants.FTMS_RESPONSE_OP_CODE_NOT_SUPPORTED)
            }
        }
        
        // Send indication response
        fitnessMachineControlPointCharacteristic?.let { characteristic ->
            characteristic.value = response
            try {
                device?.let {
                    gattServer?.notifyCharacteristicChanged(it, characteristic, true)
                }
            } catch (e: SecurityException) {
                Timber.e(e, "Security exception when sending indication")
            }
        }
    }
}

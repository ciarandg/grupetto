package com.spop.poverlay.ble

import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import com.spop.poverlay.sensor.interfaces.SensorInterface
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.*

// Listener for sensor data updates
interface SensorDataListener {
    fun onSensorDataUpdated(cadence: Float, power: Float, resistance: Float)
}

// Base class for all BLE services
abstract class BaseBleService(val server: BleServer) : SensorDataListener {
    abstract val service: BluetoothGattService
    protected val connectedDevices = mutableSetOf<BluetoothDevice>()

    open fun onConnected(device: BluetoothDevice) {
        connectedDevices.add(device)
    }

    open fun onDisconnected(device: BluetoothDevice) {
        connectedDevices.remove(device)
    }

    open fun onCharacteristicWriteRequest(device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
        if (responseNeeded) {
            server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
        }
    }

    open fun onDescriptorWriteRequest(device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
        descriptor.value = value
        if (responseNeeded) {
            server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
        }
    }

    open fun onDescriptorReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor) {
        server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, descriptor.value)
    }
}


class BleServer(
    private val context: Context,
    private val bluetoothManager: BluetoothManager,
    private val sensorInterface: SensorInterface
) : BluetoothGattServerCallback(), CoroutineScope {

    override val coroutineContext = SupervisorJob() + Dispatchers.IO
    private var sensorDataJob: Job? = null

    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private val registeredServices = mutableListOf<BaseBleService>()
    private val servicesToRegister = LinkedList<BaseBleService>()
    private var currentlyRegisteringService: BaseBleService? = null

    fun start() {
        val bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null) {
            Timber.e("Bluetooth adapter is null")
            return
        }
        val localAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (localAdvertiser == null) {
            Timber.e("Failed to create advertiser")
            return
        }
        advertiser = localAdvertiser

        try {
            gattServer = bluetoothManager.openGattServer(context, this)
            setupServices()
        } catch (e: SecurityException) {
            Timber.e(e, "Missing bluetooth permissions")
        }
    }

    private fun setupServices() {
        servicesToRegister.addAll(
            listOf(
               // FitnessMachineService(this),
               // CyclingPowerService(this),
                CyclingSpeedAndCadenceService(this),
                DeviceInformationService(this)
            )
        )
        registerNextService()
    }

    private fun registerNextService() {
        if (servicesToRegister.isEmpty()) {
            currentlyRegisteringService = null
            startAdvertising()
            startSensorDataUpdates()
        } else {
            currentlyRegisteringService = servicesToRegister.pop()
            try {
                gattServer?.addService(currentlyRegisteringService!!.service)
            } catch (e: SecurityException) {
                Timber.e(e, "Failed to add service ${currentlyRegisteringService!!.service.uuid}")
                currentlyRegisteringService = null
                servicesToRegister.clear()
            }
        }
    }

    fun stop() {
        try {
            stopSensorDataUpdates()
            stopAdvertising()
            gattServer?.clearServices()
            gattServer?.close()
            gattServer = null
            registeredServices.clear()
            servicesToRegister.clear()
            currentlyRegisteringService = null
        } catch (e: SecurityException) {
            Timber.e(e, "Missing bluetooth permissions")
        }
    }

    fun notifyCharacteristicChanged(device: BluetoothDevice, characteristic: BluetoothGattCharacteristic, confirm: Boolean) {
        try {
            gattServer?.notifyCharacteristicChanged(device, characteristic, confirm)
        } catch (e: SecurityException) {
            Timber.e(e, "Missing bluetooth permissions")
        }
    }

    fun sendResponse(device: BluetoothDevice?, requestId: Int, status: Int, offset: Int, value: ByteArray?) {
        try {
            gattServer?.sendResponse(device, requestId, status, offset, value)
        } catch (e: SecurityException) {
            Timber.e(e, "Missing bluetooth permissions")
        }
    }

    private fun startAdvertising() {
        val serviceUuids = registeredServices.map { ParcelUuid(it.service.uuid) }
        try {
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .build()

            val dataBuilder = AdvertiseData.Builder()
                .setIncludeDeviceName(true)

            for (uuid in serviceUuids) {
                dataBuilder.addServiceUuid(uuid)
            }

            advertiser?.startAdvertising(settings, dataBuilder.build(), advertisingCallback)
        } catch (e: SecurityException) {
            Timber.e(e, "Missing bluetooth permissions")
        }
    }

    private fun stopAdvertising() {
        try {
            advertiser?.stopAdvertising(advertisingCallback)
        } catch (e: SecurityException) {
            Timber.e(e, "Missing bluetooth permissions")
        }
    }

    private val advertisingCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Timber.i("BLE advertising started")
        }

        override fun onStartFailure(errorCode: Int) {
            Timber.e("BLE advertising failed: $errorCode")
        }
    }

    override fun onServiceAdded(status: Int, service: BluetoothGattService) {
        if (currentlyRegisteringService?.service?.uuid != service.uuid) {
            Timber.e("Mismatched service added callback! Expected ${currentlyRegisteringService?.service?.uuid}, got ${service.uuid}")
            servicesToRegister.clear()
            currentlyRegisteringService = null
            return
        }

        if (status == BluetoothGatt.GATT_SUCCESS) {
            Timber.d("Service added ${service.uuid}")
            registeredServices.add(currentlyRegisteringService!!)
        } else {
            Timber.e("Failed to add service ${service.uuid}, status: $status")
        }
        registerNextService()
    }

    override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            device?.let { registeredServices.forEach { it.onConnected(device) } }
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            device?.let { registeredServices.forEach { it.onDisconnected(device) } }
        }
    }

    private fun findServiceForCharacteristic(uuid: UUID?): BaseBleService? {
        return registeredServices.firstOrNull { it.service.uuid == uuid }
    }

    override fun onCharacteristicWriteRequest(device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
        findServiceForCharacteristic(characteristic.service.uuid)?.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)
    }

    override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic) {
        val service = findServiceForCharacteristic(characteristic.service.uuid)
        if (service == null) {
            sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
            return
        }
        sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristic.value)
    }

    override fun onDescriptorReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor) {
        findServiceForCharacteristic(descriptor.characteristic.service.uuid)?.onDescriptorReadRequest(device, requestId, offset, descriptor)
    }

    override fun onDescriptorWriteRequest(device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
        findServiceForCharacteristic(descriptor.characteristic.service.uuid)?.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value)
    }

    private fun startSensorDataUpdates() {
        sensorDataJob?.cancel()
        sensorDataJob = launch {
            val mutex = Mutex()
            val cadenceBuffer = mutableListOf<Float>()
            val powerBuffer = mutableListOf<Float>()
            val resistanceBuffer = mutableListOf<Float>()

            launch {
                combine(sensorInterface.cadence, sensorInterface.power, sensorInterface.resistance) { cadence, power, resistance ->
                    mutex.withLock {
                        cadenceBuffer.add(cadence)
                        powerBuffer.add(power)
                        resistanceBuffer.add(resistance)
                    }
                }.collect()
            }

            launch {
                while (isActive) {
                    delay(300)
                    val buffers = mutex.withLock {
                        if (cadenceBuffer.isEmpty()) null else Triple(cadenceBuffer.toList(), powerBuffer.toList(), resistanceBuffer.toList()).also {
                            cadenceBuffer.clear()
                            powerBuffer.clear()
                            resistanceBuffer.clear()
                        }
                    }
                    buffers?.let { (cadence, power, resistance) ->
                        val avgCadence = cadence.average().toFloat()
                        val avgPower = power.average().toFloat()
                        val avgResistance = resistance.average().toFloat()
                        registeredServices.forEach { it.onSensorDataUpdated(avgCadence, avgPower, avgResistance) }
                    }
                }
            }
        }
    }

    private fun stopSensorDataUpdates() {
        sensorDataJob?.cancel()
    }
}

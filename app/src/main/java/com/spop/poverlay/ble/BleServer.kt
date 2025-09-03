package com.spop.poverlay.ble

import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import timber.log.Timber

class BleServer(private val context: Context, private val bluetoothManager: BluetoothManager) {

    private var gattServer: BluetoothGattServer? = null
    private lateinit var advertiser: BluetoothLeAdvertiser
    private val registeredServices = mutableListOf<BleService>()

    fun start(services: List<BleService>) {
        advertiser = bluetoothManager.adapter.bluetoothLeAdvertiser
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)

        val serviceUuids = services.map { ParcelUuid(it.service.uuid) }

        for (service in services) {
            gattServer?.addService(service.service)
            registeredServices.add(service)
        }

        startAdvertising(serviceUuids)
    }

    fun stop() {
        stopAdvertising()
        gattServer?.close()
        registeredServices.clear()
    }

    fun notifyCharacteristicChanged(device: BluetoothDevice, characteristic: BluetoothGattCharacteristic, confirm: Boolean) {
        gattServer?.notifyCharacteristicChanged(device, characteristic, confirm)
    }

    private fun startAdvertising(serviceUuids: List<ParcelUuid>) {
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

        advertiser.startAdvertising(settings, dataBuilder.build(), advertisingCallback)
    }

    private fun stopAdvertising() {
        advertiser.stopAdvertising(advertisingCallback)
    }

    private val advertisingCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            Timber.i("BLE advertising started")
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            Timber.e("BLE advertising failed: $errorCode")
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                device?.let { 
                    registeredServices.forEach { it.onConnected(device) }
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                device?.let { 
                    registeredServices.forEach { it.onDisconnected(device) }
                }
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristic?.value)
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
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }

        override fun onDescriptorReadRequest(device: BluetoothDevice?, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor?) {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor)
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, descriptor?.value)
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
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value)
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }
    }
}

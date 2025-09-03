package com.spop.poverlay.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.os.Build

class DeviceInformationService : BleService {

    private val manufacturerNameCharacteristic = BluetoothGattCharacteristic(
        DeviceInformationConstants.MANUFACTURER_NAME_STRING_UUID,
        BluetoothGattCharacteristic.PROPERTY_READ,
        BluetoothGattCharacteristic.PERMISSION_READ
    ).apply { value = "spop".toByteArray() }

    private val modelNumberCharacteristic = BluetoothGattCharacteristic(
        DeviceInformationConstants.MODEL_NUMBER_STRING_UUID,
        BluetoothGattCharacteristic.PROPERTY_READ,
        BluetoothGattCharacteristic.PERMISSION_READ
    ).apply { value = "Grupetto".toByteArray() }

    private val serialNumberCharacteristic = BluetoothGattCharacteristic(
        DeviceInformationConstants.SERIAL_NUMBER_STRING_UUID,
        BluetoothGattCharacteristic.PROPERTY_READ,
        BluetoothGattCharacteristic.PERMISSION_READ
    ).apply { value = "1".toByteArray() }

    private val hardwareRevisionCharacteristic = BluetoothGattCharacteristic(
        DeviceInformationConstants.HARDWARE_REVISION_STRING_UUID,
        BluetoothGattCharacteristic.PROPERTY_READ,
        BluetoothGattCharacteristic.PERMISSION_READ
    ).apply { value = Build.HARDWARE.toByteArray() }

    private val firmwareRevisionCharacteristic = BluetoothGattCharacteristic(
        DeviceInformationConstants.FIRMWARE_REVISION_STRING_UUID,
        BluetoothGattCharacteristic.PROPERTY_READ,
        BluetoothGattCharacteristic.PERMISSION_READ
    ).apply { value = Build.ID.toByteArray() }

    private val softwareRevisionCharacteristic = BluetoothGattCharacteristic(
        DeviceInformationConstants.SOFTWARE_REVISION_STRING_UUID,
        BluetoothGattCharacteristic.PROPERTY_READ,
        BluetoothGattCharacteristic.PERMISSION_READ
    ).apply { value = "0.1".toByteArray() }


    override val service = BluetoothGattService(
        DeviceInformationConstants.DEVICE_INFORMATION_SERVICE_UUID,
        BluetoothGattService.SERVICE_TYPE_PRIMARY
    ).apply {
        addCharacteristic(manufacturerNameCharacteristic)
        addCharacteristic(modelNumberCharacteristic)
        addCharacteristic(serialNumberCharacteristic)
        addCharacteristic(hardwareRevisionCharacteristic)
        addCharacteristic(firmwareRevisionCharacteristic)
        addCharacteristic(softwareRevisionCharacteristic)
    }

    override fun onConnected(device: BluetoothDevice) {}

    override fun onDisconnected(device: BluetoothDevice) {}

}

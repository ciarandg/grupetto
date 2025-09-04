package com.spop.poverlay.ble

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.os.Build
import android.health.connect.datatypes.Device
import com.spop.poverlay.ble.DeviceInformationConstants

class DeviceInformationService(server: BleServer) : BaseBleService(server) {

    override val service = BluetoothGattService(
        DeviceInformationConstants.ServiceUUID,
        BluetoothGattService.SERVICE_TYPE_PRIMARY
    ).apply {
        addCharacteristic(
            BluetoothGattCharacteristic(
                DeviceInformationConstants.ManufacturerNameUUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
        )
        addCharacteristic(
            BluetoothGattCharacteristic(
                DeviceInformationConstants.ModelNumberUUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
        )
        addCharacteristic(
            BluetoothGattCharacteristic(
                DeviceInformationConstants.SerialNumberUUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
        )
        addCharacteristic(
            BluetoothGattCharacteristic(
                DeviceInformationConstants.HardwareRevisionUUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
        )
        addCharacteristic(
            BluetoothGattCharacteristic(
                DeviceInformationConstants.FirmwareRevisionUUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
        )
        addCharacteristic(
            BluetoothGattCharacteristic(
                DeviceInformationConstants.SoftwareRevisionUUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
        )
    }

    override fun onSensorDataUpdated(cadence: Float, power: Float, resistance: Float) {
        service.getCharacteristic(DeviceInformationConstants.SoftwareRevisionUUID)?.setValue("test".toByteArray())
    }
}

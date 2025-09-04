package com.spop.poverlay.ble

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.os.Build
import android.health.connect.datatypes.Device
import com.spop.poverlay.ble.DeviceInformationCharacteristics

class DeviceInformationService(server: BleServer) : BaseBleService(server) {

    override val service = BluetoothGattService(
        DeviceInformationCharacteristics.ServiceUUID,
        BluetoothGattService.SERVICE_TYPE_PRIMARY
    ).apply {
        addCharacteristic(
            BluetoothGattCharacteristic(
                DeviceInformationCharacteristics.ManufacturerNameUUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
        )
        addCharacteristic(
            BluetoothGattCharacteristic(
                DeviceInformationCharacteristics.ModelNumberUUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
        )
        addCharacteristic(
            BluetoothGattCharacteristic(
                DeviceInformationCharacteristics.SerialNumberUUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
        )
        addCharacteristic(
            BluetoothGattCharacteristic(
                DeviceInformationCharacteristics.HardwareRevisionUUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
        )
        addCharacteristic(
            BluetoothGattCharacteristic(
                DeviceInformationCharacteristics.FirmwareRevisionUUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
        )
        addCharacteristic(
            BluetoothGattCharacteristic(
                DeviceInformationCharacteristics.SoftwareRevisionUUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
        )
    }

    override fun onSensorDataUpdated(cadence: Float, power: Float, resistance: Float) {
        service.getCharacteristic(DeviceInformationCharacteristics.SoftwareRevisionUUID)?.setValue("test".toByteArray())
    }
}

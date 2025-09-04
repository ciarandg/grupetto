package com.spop.poverlay.ble

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService

class CyclingPowerService(server: BleServer) : BaseBleService(server) {

    private val measurementCharacteristic = BluetoothGattCharacteristic(
        CyclingPowerCharacteristics.MeasurementUUID,
        BluetoothGattCharacteristic.PROPERTY_NOTIFY,
        BluetoothGattCharacteristic.PERMISSION_READ
    ).apply {
        addDescriptor(
            BluetoothGattDescriptor(
                CyclingPowerCharacteristics.ClientCharacteristicConfigurationUUID,
                BluetoothGattDescriptor.PERMISSION_WRITE or BluetoothGattDescriptor.PERMISSION_READ
            )
        )
    }

    private val featureCharacteristic = BluetoothGattCharacteristic(
        CyclingPowerCharacteristics.FeatureUUID,
        BluetoothGattCharacteristic.PROPERTY_READ,
        BluetoothGattCharacteristic.PERMISSION_READ
    ).apply {
        val flags = CyclingPowerCharacteristics.FeatureFlags.CrankRevolutionDataSupported or
                CyclingPowerCharacteristics.FeatureFlags.WheelRevolutionDataSupported
        value = byteArrayOf(
            (flags and 0xFF).toByte(),
            (flags shr 8 and 0xFF).toByte(),
            (flags shr 16 and 0xFF).toByte(),
            (flags shr 24 and 0xFF).toByte()
        )
    }

    private val sensorLocationCharacteristic = BluetoothGattCharacteristic(
        CyclingPowerCharacteristics.SensorLocationUUID,
        BluetoothGattCharacteristic.PROPERTY_READ,
        BluetoothGattCharacteristic.PERMISSION_READ
    ).apply { value = byteArrayOf(0x05) } // Left Crank

    override val service = BluetoothGattService(
        CyclingPowerCharacteristics.ServiceUUID,
        BluetoothGattService.SERVICE_TYPE_PRIMARY
    ).apply {
        addCharacteristic(measurementCharacteristic)
        addCharacteristic(featureCharacteristic)
        addCharacteristic(sensorLocationCharacteristic)
    }

    override fun onSensorDataUpdated(cadence: Float, power: Float, resistance: Float) {
        val flags = CyclingPowerCharacteristics.MeasurementFlags.CrankRevolutionDataPresent
        val powerValue = power.toInt()
        val crankRevolutions = (cadence * 60).toInt()
        val lastCrankEventTime = (System.currentTimeMillis() / 1000).toInt()

        measurementCharacteristic.value = byteArrayOf(
            (flags and 0xFF).toByte(),
            (flags shr 8 and 0xFF).toByte(),
            (powerValue and 0xFF).toByte(),
            (powerValue shr 8 and 0xFF).toByte(),
            (crankRevolutions and 0xFF).toByte(),
            (crankRevolutions shr 8 and 0xFF).toByte(),
            (lastCrankEventTime and 0xFF).toByte(),
            (lastCrankEventTime shr 8 and 0xFF).toByte()
        )

        for (device in connectedDevices) {
            server.notifyCharacteristicChanged(device, measurementCharacteristic, false)
        }
    }
}

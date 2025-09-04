package com.spop.poverlay.ble

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService

class CyclingSpeedAndCadenceService(server: BleServer) : BaseBleService(server) {

    private val measurementCharacteristic = BluetoothGattCharacteristic(
        CyclingSpeedAndCadenceConstants.MeasurementUUID,
        BluetoothGattCharacteristic.PROPERTY_NOTIFY,
        BluetoothGattCharacteristic.PERMISSION_READ
    ).apply {
        addDescriptor(
            BluetoothGattDescriptor(
                CyclingSpeedAndCadenceConstants.ClientCharacteristicConfigurationUUID,
                BluetoothGattDescriptor.PERMISSION_WRITE or BluetoothGattDescriptor.PERMISSION_READ
            )
        )
    }

    private val featureCharacteristic = BluetoothGattCharacteristic(
        CyclingSpeedAndCadenceConstants.FeatureUUID,
        BluetoothGattCharacteristic.PROPERTY_READ,
        BluetoothGattCharacteristic.PERMISSION_READ
    ).apply {
        val flags = CyclingSpeedAndCadenceConstants.FeatureFlags.WheelRevolutionDataSupported or
                CyclingSpeedAndCadenceConstants.FeatureFlags.CrankRevolutionDataSupported or
                CyclingSpeedAndCadenceConstants.FeatureFlags.MultipleSensorLocationsSupported
        value = byteArrayOf(
            (flags and 0xFF).toByte(),
            (flags shr 8 and 0xFF).toByte()
        )
    }

    override val service = BluetoothGattService(
        CyclingSpeedAndCadenceConstants.ServiceUUID,
        BluetoothGattService.SERVICE_TYPE_PRIMARY
    ).apply {
        addCharacteristic(measurementCharacteristic)
        addCharacteristic(featureCharacteristic)
    }

    override fun onSensorDataUpdated(cadence: Float, power: Float, resistance: Float) {
        val flags = CyclingSpeedAndCadenceConstants.MeasurementFlags.WheelRevolutionDataPresent or
                CyclingSpeedAndCadenceConstants.MeasurementFlags.CrankRevolutionDataPresent

        val cumulativeWheelRevolutions = (power * 10).toLong()
        val lastWheelEventTime = (System.currentTimeMillis() / 1000).toInt()
        val cumulativeCrankRevolutions = (cadence * 60).toInt()
        val lastCrankEventTime = (System.currentTimeMillis() / 1000).toInt()

        measurementCharacteristic.value = byteArrayOf(
            flags.toByte(),
            (cumulativeWheelRevolutions and 0xFF).toByte(),
            (cumulativeWheelRevolutions shr 8 and 0xFF).toByte(),
            (cumulativeWheelRevolutions shr 16 and 0xFF).toByte(),
            (cumulativeWheelRevolutions shr 24 and 0xFF).toByte(),
            (lastWheelEventTime and 0xFF).toByte(),
            (lastWheelEventTime shr 8 and 0xFF).toByte(),
            (cumulativeCrankRevolutions and 0xFF).toByte(),
            (cumulativeCrankRevolutions shr 8 and 0xFF).toByte(),
            (lastCrankEventTime and 0xFF).toByte(),
            (lastCrankEventTime shr 8 and 0xFF).toByte()
        )

        for (device in connectedDevices) {
            server.notifyCharacteristicChanged(device, measurementCharacteristic, false)
        }
    }
}

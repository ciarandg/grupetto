package com.spop.poverlay.ble

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService

class FitnessMachineService(server: BleServer) : BaseBleService(server) {

    private val indoorBikeDataCharacteristic = BluetoothGattCharacteristic(
        FitnessMachineCharacteristics.IndoorBikeDataUUID,
        BluetoothGattCharacteristic.PROPERTY_NOTIFY,
        BluetoothGattCharacteristic.PERMISSION_READ
    ).apply {
        addDescriptor(
            BluetoothGattDescriptor(
                FitnessMachineCharacteristics.ClientCharacteristicConfigurationUUID,
                BluetoothGattDescriptor.PERMISSION_WRITE or BluetoothGattDescriptor.PERMISSION_READ
            )
        )
    }

    private val featureCharacteristic = BluetoothGattCharacteristic(
        FitnessMachineCharacteristics.FeatureUUID,
        BluetoothGattCharacteristic.PROPERTY_READ,
        BluetoothGattCharacteristic.PERMISSION_READ
    ).apply {
        val flags = FitnessMachineCharacteristics.FeatureFlags.CadenceSupported or
                FitnessMachineCharacteristics.FeatureFlags.PowerMeasurementSupported or
                FitnessMachineCharacteristics.FeatureFlags.ResistanceLevelSupported
        value = byteArrayOf(
            (flags and 0xFF).toByte(),
            (flags shr 8 and 0xFF).toByte(),
            (flags shr 16 and 0xFF).toByte(),
            (flags shr 24 and 0xFF).toByte()
        )
    }

    private val controlPointCharacteristic = BluetoothGattCharacteristic(
        FitnessMachineCharacteristics.ControlPointUUID,
        BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_INDICATE,
        BluetoothGattCharacteristic.PERMISSION_WRITE
    )

    private val supportedResistanceRangeCharacteristic = BluetoothGattCharacteristic(
        FitnessMachineCharacteristics.SupportedResistanceRangeUUID,
        BluetoothGattCharacteristic.PROPERTY_READ,
        BluetoothGattCharacteristic.PERMISSION_READ
    ).apply {
        value = byteArrayOf(0, 100, 1) // Min 0, Max 100, Step 1
    }

    private val trainingStatusCharacteristic = BluetoothGattCharacteristic(
        FitnessMachineCharacteristics.TrainingStatusUUID,
        BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
        BluetoothGattCharacteristic.PERMISSION_READ
    ).apply { value = byteArrayOf(0x01) } // Idle

    override val service = BluetoothGattService(
        FitnessMachineCharacteristics.ServiceUUID,
        BluetoothGattService.SERVICE_TYPE_PRIMARY
    ).apply {
        addCharacteristic(indoorBikeDataCharacteristic)
        addCharacteristic(featureCharacteristic)
        addCharacteristic(controlPointCharacteristic)
        addCharacteristic(supportedResistanceRangeCharacteristic)
        addCharacteristic(trainingStatusCharacteristic)
    }

    override fun onSensorDataUpdated(cadence: Float, power: Float, resistance: Float) {
        val flags = FitnessMachineCharacteristics.IndoorBikeDataFlags.InstantaneousCadencePresent or
                FitnessMachineCharacteristics.IndoorBikeDataFlags.InstantaneousPowerPresent or
                FitnessMachineCharacteristics.IndoorBikeDataFlags.ResistanceLevelPresent

        val cadenceValue = (cadence * 2).toInt()
        val powerValue = power.toInt()
        val resistanceValue = resistance.toInt()

        indoorBikeDataCharacteristic.value = byteArrayOf(
            (flags and 0xFF).toByte(),
            (flags shr 8 and 0xFF).toByte(),
            (cadenceValue and 0xFF).toByte(),
            (cadenceValue shr 8 and 0xFF).toByte(),
            (powerValue and 0xFF).toByte(),
            (powerValue shr 8 and 0xFF).toByte(),
            (resistanceValue and 0xFF).toByte(),
            (resistanceValue shr 8 and 0xFF).toByte()
        )

        for (device in connectedDevices) {
            server.notifyCharacteristicChanged(device, indoorBikeDataCharacteristic, false)
        }

        val newStatus = if (cadence > 0) 0x0D.toByte() else 0x01.toByte()
        if (trainingStatusCharacteristic.value.first() != newStatus) {
            trainingStatusCharacteristic.value = byteArrayOf(newStatus)
            for (device in connectedDevices) {
                server.notifyCharacteristicChanged(device, trainingStatusCharacteristic, false)
            }
        }
    }
}

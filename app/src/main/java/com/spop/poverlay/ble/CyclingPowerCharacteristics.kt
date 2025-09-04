package com.spop.poverlay.ble

import java.util.UUID

object CyclingPowerCharacteristics {
    val ServiceUUID: UUID = UUID.fromString("00001818-0000-1000-8000-00805f9b34fb")
    val MeasurementUUID: UUID = UUID.fromString("00002a63-0000-1000-8000-00805f9b34fb")
    val FeatureUUID: UUID = UUID.fromString("00002a65-0000-1000-8000-00805f9b34fb")
    val SensorLocationUUID: UUID = UUID.fromString("00002a5d-0000-1000-8000-00805f9b34fb")
    val ClientCharacteristicConfigurationUUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    object MeasurementFlags {
        const val CrankRevolutionDataPresent = 0x02
        const val WheelRevolutionDataPresent = 0x01
    }

    object FeatureFlags {
        const val CrankRevolutionDataSupported = 0x00000020
        const val WheelRevolutionDataSupported = 0x00000010
    }
}

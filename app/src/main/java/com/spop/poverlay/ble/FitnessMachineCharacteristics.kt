package com.spop.poverlay.ble

import java.util.UUID

object FitnessMachineCharacteristics {
    val ServiceUUID: UUID = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")
    val IndoorBikeDataUUID: UUID = UUID.fromString("00002ad2-0000-1000-8000-00805f9b34fb")
    val FeatureUUID: UUID = UUID.fromString("00002acc-0000-1000-8000-00805f9b34fb")
    val ControlPointUUID: UUID = UUID.fromString("00002ad9-0000-1000-8000-00805f9b34fb")
    val SupportedResistanceRangeUUID: UUID = UUID.fromString("00002ad6-0000-1000-8000-00805f9b34fb")
    val TrainingStatusUUID: UUID = UUID.fromString("00002ad3-0000-1000-8000-00805f9b34fb")
    val ClientCharacteristicConfigurationUUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    object IndoorBikeDataFlags {
        const val InstantaneousCadencePresent = 0x02
        const val InstantaneousPowerPresent = 0x04
        const val ResistanceLevelPresent = 0x10
    }

    object FeatureFlags {
        const val CadenceSupported = 0x00000002
        const val PowerMeasurementSupported = 0x00000008
        const val ResistanceLevelSupported = 0x00000080
    }
}

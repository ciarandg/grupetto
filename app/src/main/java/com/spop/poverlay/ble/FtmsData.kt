package com.spop.poverlay.ble

import kotlin.math.roundToInt

/**
 * Data class representing FTMS (Fitness Machine Service) measurement data
 */
data class FtmsData(
    val instantaneousPower: Int = 0,        // watts
    val instantaneousCadence: Float = 0f,   // rpm (half revolutions per minute)
    val instantaneousSpeed: Float = 0f,     // m/s * 0.01
    val totalDistance: Int = 0,             // meters
    val elapsedTime: Int = 0,               // seconds
    val heartRate: Int = 0,                 // bpm
    val resistanceLevel: Float = 0f,        // resistance level
    val totalEnergy: Int = 0,               // kilojoules
    val energyPerHour: Int = 0,             // watts
    val energyPerMinute: Int = 0            // watts
) {
    companion object {
        const val SPEED_RESOLUTION = 0.01f      // Speed resolution in m/s
        const val CADENCE_RESOLUTION = 0.5f     // Cadence resolution in RPM
        const val DISTANCE_RESOLUTION = 1       // Distance resolution in meters
    }

    /**
     * Convert to Indoor Bike Data characteristic bytes (0x2AD2)
     * This follows the FTMS specification for indoor bike data
     * Simplified version with essential data only
     */
    fun toIndoorBikeDataBytes(): ByteArray {
        val data = mutableListOf<Byte>()
        
        // Flags (2 bytes) - indicating which fields are present
        // Keep it simple with just the basic fields
        var flags = 0
        flags = flags or 0x0004  // Instantaneous Cadence present  
        flags = flags or 0x0040  // Instantaneous Power present
        
        data.addAll(flags.toUInt16Bytes())
        
        // Instantaneous Speed (uint16) - km/h with resolution 0.01
        // Speed is always present according to FTMS spec
        val speedKmh = (instantaneousSpeed * 3.6f / SPEED_RESOLUTION).roundToInt().coerceIn(0, 65534)
        data.addAll(speedKmh.toUInt16Bytes())
        
        // Instantaneous Cadence (uint16) - rpm with resolution 0.5
        val cadenceData = (instantaneousCadence / CADENCE_RESOLUTION).roundToInt().coerceIn(0, 65534)
        data.addAll(cadenceData.toUInt16Bytes())
        
        // Instantaneous Power (sint16) - watts
        val powerData = instantaneousPower.coerceIn(-32768, 32767)
        data.addAll(powerData.toSInt16Bytes())
        
        return data.toByteArray()
    }

    /**
     * Convert to Fitness Machine Status characteristic bytes (0x2ADA)
     */
    fun toFitnessMachineStatusBytes(): ByteArray {
        val data = mutableListOf<Byte>()
        
        // Status field (uint8) - 0x01 = Fitness Machine Started or Resumed by User
        data.add(0x01.toByte())
        
        return data.toByteArray()
    }
}

// Extension functions for byte conversion
private fun Int.toUInt16Bytes(): List<Byte> {
    return listOf(
        (this and 0xFF).toByte(),
        ((this shr 8) and 0xFF).toByte()
    )
}

private fun Int.toSInt16Bytes(): List<Byte> {
    return listOf(
        (this and 0xFF).toByte(),
        ((this shr 8) and 0xFF).toByte()
    )
}

private fun Int.toUInt24Bytes(): List<Byte> {
    return listOf(
        (this and 0xFF).toByte(),
        ((this shr 8) and 0xFF).toByte(),
        ((this shr 16) and 0xFF).toByte()
    )
}

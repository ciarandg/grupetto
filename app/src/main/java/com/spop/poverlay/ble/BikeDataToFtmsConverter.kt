package com.spop.poverlay.ble

import com.spop.poverlay.sensor.BikeData
import com.spop.poverlay.util.calculateSpeedFromPelotonV1Power

/**
 * Utility class to convert Peloton BikeData to FTMS data
 */
object BikeDataToFtmsConverter {
    
    /**
     * Convert BikeData to FtmsData
     */
    fun convert(
        bikeData: BikeData,
        elapsedTimeSeconds: Int,
        totalDistanceMeters: Float,
        totalEnergyKj: Float
    ): FtmsData {
        val power = bikeData.power.toFloat()
        val cadence = bikeData.rpm.toFloat()
        val resistance = bikeData.currentResistance.toFloat()
        val speed = calculateSpeedFromPelotonV1Power(power)
        
        return FtmsData(
            instantaneousPower = power.toInt(),
            instantaneousCadence = cadence,
            instantaneousSpeed = speed,
            totalDistance = totalDistanceMeters.toInt(),
            elapsedTime = elapsedTimeSeconds,
            heartRate = 0, // Not available from Peloton data
            resistanceLevel = resistance,
            totalEnergy = totalEnergyKj.toInt(),
            energyPerHour = power.toInt(),
            energyPerMinute = (power / 60f).toInt()
        )
    }
    
    /**
     * Calculate accumulated distance based on speed and time delta
     */
    fun calculateDistanceDelta(speedMs: Float, deltaTimeSeconds: Float): Float {
        return speedMs * deltaTimeSeconds
    }
    
    /**
     * Calculate accumulated energy based on power and time delta
     */
    fun calculateEnergyDelta(powerWatts: Float, deltaTimeSeconds: Float): Float {
        // Energy in kilojoules = Power (watts) * Time (seconds) / 1000
        return powerWatts * deltaTimeSeconds / 1000f
    }
    
    /**
     * Convert Peloton resistance level (0-100) to a standardized resistance value
     */
    fun normalizeResistance(pelotonResistance: Int): Float {
        // Peloton resistance is typically 0-100, normalize to 0.0-1.0 range
        return (pelotonResistance.coerceIn(0, 100) / 100f)
    }
}

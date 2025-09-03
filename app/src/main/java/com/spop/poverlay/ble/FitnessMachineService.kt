package com.spop.poverlay.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import com.spop.poverlay.sensor.interfaces.SensorInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.*

class FitnessMachineService(private val sensorInterface: SensorInterface, private val server: BleServer) : BleService, CoroutineScope {

    override val coroutineContext = SupervisorJob() + Dispatchers.IO

    private val connectedDevices = mutableSetOf<BluetoothDevice>()

    private val indoorBikeDataCharacteristic = BluetoothGattCharacteristic(
        FtmsConstants.INDOOR_BIKE_DATA_UUID,
        BluetoothGattCharacteristic.PROPERTY_NOTIFY,
        BluetoothGattCharacteristic.PERMISSION_READ
    )

    private val fitnessMachineFeatureCharacteristic = BluetoothGattCharacteristic(
        FtmsConstants.FITNESS_MACHINE_FEATURE_UUID,
        BluetoothGattCharacteristic.PROPERTY_READ,
        BluetoothGattCharacteristic.PERMISSION_READ
    )

    override val service = BluetoothGattService(
        FtmsConstants.FTMS_SERVICE_UUID,
        BluetoothGattService.SERVICE_TYPE_PRIMARY
    ).apply {
        addCharacteristic(indoorBikeDataCharacteristic)
        addCharacteristic(fitnessMachineFeatureCharacteristic)
    }

    init {
        launch {
            combine(
                sensorInterface.cadence,
                sensorInterface.power,
                sensorInterface.resistance
            ) { cadence, power, resistance ->
                val indoorBikeData = formatIndoorBikeData(cadence, power, resistance)
                indoorBikeDataCharacteristic.value = indoorBikeData
                for (device in connectedDevices) {
                    server.notifyCharacteristicChanged(device, indoorBikeDataCharacteristic, false)
                }
            }.collect {}
        }
    }

    override fun onConnected(device: BluetoothDevice) {
        connectedDevices.add(device)
    }

    override fun onDisconnected(device: BluetoothDevice) {
        connectedDevices.remove(device)
    }

    private fun formatIndoorBikeData(cadence: Float, power: Float, resistance: Float): ByteArray {
        val flags = 0x4004
        val cadenceValue = (cadence * 2).toInt()
        val powerValue = power.toInt()

        return byteArrayOf(
            (flags and 0xFF).toByte(),
            (flags shr 8 and 0xFF).toByte(),
            (cadenceValue and 0xFF).toByte(),
            (cadenceValue shr 8 and 0xFF).toByte(),
            (powerValue and 0xFF).toByte(),
            (powerValue shr 8 and 0xFF).toByte()
        )
    }
}

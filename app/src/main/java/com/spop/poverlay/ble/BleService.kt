package com.spop.poverlay.ble

import android.bluetooth.BluetoothGattService

interface BleService {
    val service: BluetoothGattService
    fun onConnected(device: android.bluetooth.BluetoothDevice)
    fun onDisconnected(device: android.bluetooth.BluetoothDevice)
}

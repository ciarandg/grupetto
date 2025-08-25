package com.spop.poverlay.ble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.spop.poverlay.ConfigurationRepository
import com.spop.poverlay.MainActivity
import com.spop.poverlay.R
import com.spop.poverlay.sensor.interfaces.DummySensorInterface
import com.spop.poverlay.sensor.interfaces.PelotonBikePlusSensorInterface
import com.spop.poverlay.sensor.interfaces.PelotonBikeSensorInterfaceV1New
import com.spop.poverlay.sensor.interfaces.SensorInterface
import com.spop.poverlay.util.IsBikePlus
import com.spop.poverlay.util.IsRunningOnPeloton
import com.spop.poverlay.util.LifecycleEnabledService
import com.spop.poverlay.util.calculateSpeedFromPelotonV1Power
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.*
import kotlin.time.Duration.Companion.seconds

/**
 * BLE FTMS Service that bridges Peloton bike data to BLE FTMS protocol
 */
class BleFtmsService : LifecycleEnabledService() {
    
    companion object {
        private const val NOTIFICATION_ID = 2033
        private const val NOTIFICATION_CHANNEL_ID = "ble_ftms_service"
        private const val UPDATE_INTERVAL_MS = 1000L // 1 second
        
        // Actions for service control
        const val ACTION_START_FTMS = "com.spop.poverlay.ble.START_FTMS"
        const val ACTION_STOP_FTMS = "com.spop.poverlay.ble.STOP_FTMS"
        const val ACTION_TOGGLE_FTMS = "com.spop.poverlay.ble.TOGGLE_FTMS"
    }
    
    private var bleFtmsServer: BleFtmsServer? = null
    private var sensorInterface: SensorInterface? = null
    private var configurationRepository: ConfigurationRepository? = null
    
    private var dataUpdateJob: Job? = null
    private var startTime: Long = 0
    private var totalDistance: Float = 0f
    private var totalEnergy: Float = 0f
    private var lastPowerValue: Float = 0f
    private var lastUpdateTime: Long = 0
    
    private var isServiceEnabled = false
    
    override fun onCreate() {
        super.onCreate()
        Timber.i("BleFtmsService created")
        
        // Check for Bluetooth permissions
        if (!hasBluetoothPermissions()) {
            Timber.w("Missing Bluetooth permissions - service will not start")
            stopSelf()
            return
        }
        
        configurationRepository = ConfigurationRepository(applicationContext, this)
        setupSensorInterface()
        
        val notification = createNotification(isServiceEnabled)
        startForeground(NOTIFICATION_ID, notification)
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_FTMS -> startFtmsService()
            ACTION_STOP_FTMS -> stopFtmsService()
            ACTION_TOGGLE_FTMS -> toggleFtmsService()
            else -> {
                // Default behavior - check if we should auto-start
                if (configurationRepository?.bleFtmsEnabled?.value == true) {
                    startFtmsService()
                }
            }
        }
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopFtmsService()
        configurationRepository?.close()
        Timber.i("BleFtmsService destroyed")
    }
    
    private fun hasBluetoothPermissions(): Boolean {
        val bluetoothPermission = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.BLUETOOTH
        ) == PackageManager.PERMISSION_GRANTED
        
        val bluetoothAdminPermission = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.BLUETOOTH_ADMIN
        ) == PackageManager.PERMISSION_GRANTED
        
        val locationPermission = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        // Check for Android 12+ permissions
        var bluetoothAdvertisePermission = true
        var bluetoothConnectPermission = true
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            bluetoothAdvertisePermission = ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.BLUETOOTH_ADVERTISE
            ) == PackageManager.PERMISSION_GRANTED
            
            bluetoothConnectPermission = ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        }
        
        return bluetoothPermission && bluetoothAdminPermission && locationPermission &&
                bluetoothAdvertisePermission && bluetoothConnectPermission
    }
    
    private fun setupSensorInterface() {
        sensorInterface = if (IsRunningOnPeloton) {
            if (IsBikePlus) {
                PelotonBikePlusSensorInterface(this).also {
                    lifecycle.addObserver(object : DefaultLifecycleObserver {
                        override fun onDestroy(owner: LifecycleOwner) {
                            it.stop()
                        }
                    })
                }
            } else {
                PelotonBikeSensorInterfaceV1New(this).also {
                    lifecycle.addObserver(object : DefaultLifecycleObserver {
                        override fun onDestroy(owner: LifecycleOwner) {
                            it.stop()
                        }
                    })
                }
            }
        } else {
            DummySensorInterface()
        }
    }
    
    private fun startFtmsService() {
        if (isServiceEnabled) {
            Timber.d("FTMS service already running")
            return
        }
        
        if (!hasBluetoothPermissions()) {
            Timber.w("Cannot start FTMS service - missing permissions")
            updateNotification(false)
            return
        }
        
        bleFtmsServer = BleFtmsServer(this, configurationRepository?.bleFtmsDeviceName?.value ?: "Grupetto FTMS")
        
        if (bleFtmsServer!!.startServer()) {
            isServiceEnabled = true
            startDataUpdates()
            updateNotification(true)
            Timber.i("FTMS service started successfully")
            
            // Send initial status
            lifecycleScope.launch {
                delay(1000) // Wait a moment for connections
                bleFtmsServer?.sendFitnessMachineStatus(byteArrayOf(FtmsConstants.STATUS_STARTED_BY_EXTERNAL))
            }
        } else {
            Timber.e("Failed to start FTMS server")
            bleFtmsServer = null
            updateNotification(false)
        }
    }
    
    private fun stopFtmsService() {
        if (!isServiceEnabled) {
            Timber.d("FTMS service already stopped")
            return
        }
        
        isServiceEnabled = false
        stopDataUpdates()
        bleFtmsServer?.stopServer()
        bleFtmsServer = null
        updateNotification(false)
        resetCounters()
        Timber.i("FTMS service stopped")
    }
    
    private fun toggleFtmsService() {
        if (isServiceEnabled) {
            stopFtmsService()
        } else {
            startFtmsService()
        }
    }
    
    private fun startDataUpdates() {
        val sensor = sensorInterface ?: return
        
        startTime = System.currentTimeMillis()
        lastUpdateTime = startTime
        resetCounters()
        
        Timber.i("Starting BLE FTMS data updates with sensor: ${sensor::class.simpleName}")
        
        dataUpdateJob = lifecycleScope.launch {
            // First send some test data to verify connection
            delay(2000) // Wait for potential connections
            sendTestData()
            
            // Then start real data collection
            var dataCount = 0
            combine(
                sensor.power,
                sensor.cadence,
                sensor.resistance
            ) { power, cadence, resistance ->
                Triple(power, cadence, resistance)
            }.collect { (power, cadence, resistance) ->
                dataCount++
                if (dataCount % 10 == 0) { // Log every 10th reading to avoid spam
                    Timber.i("BLE FTMS data #$dataCount: power=$power, cadence=$cadence, resistance=$resistance")
                }
                updateFtmsData(power, cadence, resistance)
                delay(UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun sendTestData() {
        Timber.i("Sending test FTMS data")
        val testData = FtmsData(
            instantaneousPower = 150,
            instantaneousCadence = 80f,
            instantaneousSpeed = 25f,
            totalDistance = 100,
            elapsedTime = 60,
            heartRate = 0,
            resistanceLevel = 50f,
            totalEnergy = 1,
            energyPerHour = 150,
            energyPerMinute = 3
        )
        bleFtmsServer?.sendIndoorBikeData(testData)
        
        // Also start a periodic test data sender for debugging
        lifecycleScope.launch {
            var counter = 0
            while (isActive && isServiceEnabled) {
                delay(5000) // Every 5 seconds
                counter++
                val periodicTestData = FtmsData(
                    instantaneousPower = 100 + (counter % 50),
                    instantaneousCadence = 70f + (counter % 20),
                    instantaneousSpeed = 20f + (counter % 10),
                    totalDistance = counter * 10,
                    elapsedTime = counter * 5,
                    heartRate = 0,
                    resistanceLevel = 40f + (counter % 30),
                    totalEnergy = counter,
                    energyPerHour = 120 + (counter % 30),
                    energyPerMinute = 2 + (counter % 3)
                )
                Timber.i("Sending periodic test data #$counter")
                bleFtmsServer?.sendIndoorBikeData(periodicTestData)
            }
        }
    }
    
    private fun stopDataUpdates() {
        dataUpdateJob?.cancel()
        dataUpdateJob = null
    }
    
    private fun updateFtmsData(power: Float, cadence: Float, resistance: Float) {
        val currentTime = System.currentTimeMillis()
        val elapsedTimeSeconds = ((currentTime - startTime) / 1000).toInt()
        val deltaTimeSeconds = (currentTime - lastUpdateTime) / 1000f
        
        // Calculate speed from power (using existing utility function)
        val speed = calculateSpeedFromPelotonV1Power(power)
        
        // Update cumulative values
        if (deltaTimeSeconds > 0) {
            totalDistance += speed * deltaTimeSeconds
            totalEnergy += power * deltaTimeSeconds / 3600f / 1000f // Convert to kJ
        }
        
        // Calculate energy rates
        val energyPerHour = power.toInt()
        val energyPerMinute = (power / 60f).toInt()
        
        val ftmsData = FtmsData(
            instantaneousPower = power.toInt(),
            instantaneousCadence = cadence,
            instantaneousSpeed = speed,
            totalDistance = totalDistance.toInt(),
            elapsedTime = elapsedTimeSeconds,
            heartRate = 0, // Not available from Peloton data
            resistanceLevel = resistance,
            totalEnergy = totalEnergy.toInt(),
            energyPerHour = energyPerHour,
            energyPerMinute = energyPerMinute
        )
        
        Timber.d("Sending FTMS data: $ftmsData")
        
        // Send data to connected BLE devices
        bleFtmsServer?.sendIndoorBikeData(ftmsData)
        
        lastPowerValue = power
        lastUpdateTime = currentTime
    }
    
    private fun resetCounters() {
        totalDistance = 0f
        totalEnergy = 0f
        lastPowerValue = 0f
    }
    
    private fun createNotification(isRunning: Boolean): Notification {
        createNotificationChannel()
        
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)
        
        // Toggle action
        val toggleIntent = Intent(this, BleFtmsService::class.java).apply {
            action = ACTION_TOGGLE_FTMS
        }
        val togglePendingIntent = PendingIntent.getService(this, 1, toggleIntent, pendingIntentFlags)
        
        val statusText = if (isRunning) "Running" else "Stopped"
        val actionText = if (isRunning) "Stop" else "Start"
        
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("BLE FTMS Service")
            .setContentText("Status: $statusText")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, actionText, togglePendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }
    
    private fun updateNotification(isRunning: Boolean) {
        val notification = createNotification(isRunning)
        val notificationManager = NotificationManagerCompat.from(this)
        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Timber.e(e, "Failed to update notification")
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "BLE FTMS Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "BLE FTMS Service Status"
                enableVibration(false)
                setSound(null, null)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
}

package com.locqar.kiosk.hardware.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.locqar.kiosk.hardware.controller.*
import com.locqar.kiosk.hardware.demo.DemoLockerController
import com.locqar.kiosk.hardware.serial.SerialManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Foreground service that manages RS485 hardware communication.
 *
 * Responsibilities:
 * - Serial connection lifecycle
 * - Background polling of door states
 * - Door open commands
 * - Station online/offline detection
 */
class LockerDaemonService : Service() {

    companion object {
        private const val TAG = "LockerDaemon"
        private const val CHANNEL_ID = "locker_daemon"
        private const val NOTIFICATION_ID = 1
        private const val DEFAULT_POLL_INTERVAL_MS = 5000L
        private const val OFFLINE_THRESHOLD = 3 // consecutive timeouts before offline
    }

    inner class DaemonBinder : Binder() {
        fun getService(): LockerDaemonService = this@LockerDaemonService
    }

    private val binder = DaemonBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var serialManager: SerialManager? = null
    private var controller: LockerController = DemoLockerController()

    // Configurable state
    private val _demoMode = MutableStateFlow(true)
    val demoMode = _demoMode.asStateFlow()

    private val _stationNumber = MutableStateFlow(1)
    val stationNumber = _stationNumber.asStateFlow()

    private val _pollIntervalMs = MutableStateFlow(DEFAULT_POLL_INTERVAL_MS)
    val pollIntervalMs = _pollIntervalMs.asStateFlow()

    // Hardware state
    private val _doorStates = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    val doorStates = _doorStates.asStateFlow()

    private val _isStationOnline = MutableStateFlow(false)
    val isStationOnline = _isStationOnline.asStateFlow()

    private val _isUsbConnected = MutableStateFlow(false)
    val isUsbConnected = _isUsbConnected.asStateFlow()

    private var pollingJob: Job? = null
    private var consecutiveTimeouts = 0

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        Log.i(TAG, "Daemon service created")
    }

    override fun onDestroy() {
        scope.cancel()
        serialManager?.destroy()
        Log.i(TAG, "Daemon service destroyed")
        super.onDestroy()
    }

    // ---------- Configuration ----------

    fun setStationNumber(station: Int) {
        _stationNumber.value = station
        Log.i(TAG, "Station number set to $station")
    }

    fun setDemoMode(enabled: Boolean) {
        _demoMode.value = enabled
        if (enabled) {
            serialManager?.disconnect()
            controller = DemoLockerController()
            _isUsbConnected.value = false
            _isStationOnline.value = true
            Log.i(TAG, "Demo mode enabled")
        } else {
            connectSerial()
        }
    }

    fun setPollInterval(intervalMs: Long) {
        _pollIntervalMs.value = intervalMs
    }

    // ---------- Serial connection ----------

    private fun connectSerial() {
        if (serialManager == null) {
            serialManager = SerialManager(applicationContext)
        }
        val sm = serialManager!!
        sm.connect()
        controller = LockerControllerImpl(sm)
        _isUsbConnected.value = sm.isConnected
        Log.i(TAG, "Serial connection attempt, connected=${sm.isConnected}")
    }

    // ---------- Polling ----------

    fun startPolling() {
        stopPolling()
        pollingJob = scope.launch {
            Log.i(TAG, "Polling started, interval=${_pollIntervalMs.value}ms")
            while (isActive) {
                pollOnce()
                delay(_pollIntervalMs.value)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    suspend fun pollOnce(): PollResult {
        val result = controller.pollStation(_stationNumber.value)

        when (result) {
            is PollResult.Success -> {
                consecutiveTimeouts = 0
                _isStationOnline.value = true
                _doorStates.value = result.doorStates
            }
            is PollResult.Timeout -> {
                consecutiveTimeouts++
                if (consecutiveTimeouts >= OFFLINE_THRESHOLD) {
                    _isStationOnline.value = false
                    Log.w(TAG, "Station ${_stationNumber.value} marked offline ($consecutiveTimeouts timeouts)")
                }
            }
            else -> { /* keep current state */ }
        }
        return result
    }

    // ---------- Door commands ----------

    suspend fun openDoor(lock: Int): OpenResult {
        Log.i(TAG, "Opening door: station=${_stationNumber.value}, lock=$lock")
        return controller.openDoor(_stationNumber.value, lock)
    }

    suspend fun safeOpenDoor(lock: Int, confirmTimeoutMs: Long = 5000): SafeOpenResult {
        Log.i(TAG, "Safe opening door: station=${_stationNumber.value}, lock=$lock")
        return controller.safeOpenDoor(_stationNumber.value, lock, confirmTimeoutMs)
    }

    // ---------- Notification ----------

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Locker Hardware",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Locker hardware daemon service" }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("LocQar Kiosk")
            .setContentText("Locker hardware service running")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }
}

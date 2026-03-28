package com.locqar.kiosk.hardware.demo

import com.locqar.kiosk.hardware.controller.*
import kotlinx.coroutines.delay

/**
 * Simulated locker controller for UI testing without physical hardware.
 * Doors auto-close after 8 seconds.
 */
class DemoLockerController : LockerController {

    private val doorStates = mutableMapOf<String, Boolean>() // "station:lock" -> isOpen
    private val autoCloseJobs = mutableMapOf<String, Long>()

    override val isConnected: Boolean = true

    override suspend fun openDoor(station: Int, lock: Int): OpenResult {
        delay(80) // simulate RS485 round-trip
        val key = "$station:$lock"
        doorStates[key] = true
        autoCloseJobs[key] = System.currentTimeMillis() + 8000
        return OpenResult.Success
    }

    override suspend fun pollStation(station: Int, mask: Int): PollResult {
        delay(60) // simulate RS485 round-trip
        val now = System.currentTimeMillis()

        // Auto-close doors past their timeout
        autoCloseJobs.entries.removeAll { (key, closeAt) ->
            if (now >= closeAt) {
                doorStates[key] = false
                true
            } else false
        }

        val states = (1..16).associateWith { lock ->
            doorStates["$station:$lock"] ?: false
        }
        return PollResult.Success(states)
    }

    override suspend fun safeOpenDoor(station: Int, lock: Int, confirmTimeoutMs: Long): SafeOpenResult {
        openDoor(station, lock)
        return SafeOpenResult.Confirmed
    }
}

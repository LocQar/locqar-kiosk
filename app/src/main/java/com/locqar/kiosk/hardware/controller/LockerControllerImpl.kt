package com.locqar.kiosk.hardware.controller

import android.util.Log
import com.locqar.kiosk.hardware.codec.WinnsenCodec
import com.locqar.kiosk.hardware.serial.SerialManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Real hardware implementation using RS485 serial via USB adapter.
 *
 * Thread safety: All RS485 commands go through a Mutex to prevent
 * interleaving on the shared serial bus.
 *
 * Inter-command delay: 50ms minimum between commands to give the
 * board time to process.
 */
class LockerControllerImpl(
    private val serial: SerialManager
) : LockerController {

    companion object {
        private const val TAG = "LockerCtrl"
        private const val INTER_COMMAND_DELAY_MS = 50L
    }

    private val busMutex = Mutex()
    private var lastCommandTime = 0L

    override val isConnected: Boolean get() = serial.isConnected

    override suspend fun openDoor(station: Int, lock: Int): OpenResult {
        if (!isConnected) return OpenResult.NotConnected

        return busMutex.withLock {
            enforceDelay()
            try {
                val txFrame = WinnsenCodec.buildOpenCommand(station, lock)
                Log.d(TAG, "TX open: ${WinnsenCodec.toHex(txFrame)}")

                val rxData = serial.sendAndReceive(txFrame, WinnsenCodec.OPEN_RESPONSE_LEN)
                lastCommandTime = System.currentTimeMillis()

                if (rxData == null) {
                    Log.w(TAG, "Open door timeout: station=$station lock=$lock")
                    return@withLock OpenResult.Timeout
                }

                Log.d(TAG, "RX open: ${WinnsenCodec.toHex(rxData)}")
                val response = WinnsenCodec.parseOpenResponse(rxData)

                when {
                    response == null -> OpenResult.Error("Invalid response frame")
                    response.success -> {
                        Log.i(TAG, "Door opened: station=$station lock=$lock")
                        OpenResult.Success
                    }
                    else -> {
                        Log.w(TAG, "Door open failed: station=$station lock=$lock")
                        OpenResult.Failed
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Open door error", e)
                OpenResult.Error(e.message ?: "Unknown error")
            }
        }
    }

    override suspend fun pollStation(station: Int, mask: Int): PollResult {
        if (!isConnected) return PollResult.NotConnected

        return busMutex.withLock {
            enforceDelay()
            try {
                val txFrame = WinnsenCodec.buildPollCommand(station, mask)
                Log.d(TAG, "TX poll: ${WinnsenCodec.toHex(txFrame)}")

                val rxData = serial.sendAndReceive(txFrame, WinnsenCodec.POLL_RESPONSE_LEN)
                lastCommandTime = System.currentTimeMillis()

                if (rxData == null) {
                    return@withLock PollResult.Timeout
                }

                Log.d(TAG, "RX poll: ${WinnsenCodec.toHex(rxData)}")
                val response = WinnsenCodec.parsePollResponse(rxData)

                if (response != null) {
                    PollResult.Success(response.doorStates)
                } else {
                    PollResult.Error("Invalid response frame")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Poll error", e)
                PollResult.Error(e.message ?: "Unknown error")
            }
        }
    }

    override suspend fun safeOpenDoor(
        station: Int,
        lock: Int,
        confirmTimeoutMs: Long
    ): SafeOpenResult {
        if (!isConnected) return SafeOpenResult.NotConnected

        // Step 1: Send open command
        when (val result = openDoor(station, lock)) {
            is OpenResult.Success -> { /* continue to confirmation */ }
            is OpenResult.Failed -> return SafeOpenResult.OpenFailed
            is OpenResult.NotConnected -> return SafeOpenResult.NotConnected
            else -> return SafeOpenResult.OpenNotConfirmed
        }

        // Step 2: Poll to confirm door is physically open
        val deadline = System.currentTimeMillis() + confirmTimeoutMs
        while (System.currentTimeMillis() < deadline) {
            delay(200) // poll every 200ms

            when (val poll = pollStation(station)) {
                is PollResult.Success -> {
                    val isOpen = poll.doorStates[lock] ?: false
                    if (isOpen) {
                        Log.i(TAG, "Door confirmed open: station=$station lock=$lock")
                        return SafeOpenResult.Confirmed
                    }
                }
                else -> { /* retry */ }
            }
        }

        Log.w(TAG, "Door open not confirmed within ${confirmTimeoutMs}ms")
        return SafeOpenResult.OpenNotConfirmed
    }

    private suspend fun enforceDelay() {
        val elapsed = System.currentTimeMillis() - lastCommandTime
        if (elapsed < INTER_COMMAND_DELAY_MS) {
            delay(INTER_COMMAND_DELAY_MS - elapsed)
        }
    }
}

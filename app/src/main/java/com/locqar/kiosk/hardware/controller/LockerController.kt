package com.locqar.kiosk.hardware.controller

/**
 * Abstraction over physical lock hardware.
 * Implementations: LockerControllerImpl (real RS485), DemoLockerController (simulated).
 */
interface LockerController {

    val isConnected: Boolean

    /**
     * Send an open command for a specific lock on a station.
     */
    suspend fun openDoor(station: Int, lock: Int): OpenResult

    /**
     * Poll all lock states on a station.
     * @param mask 16-bit bitmask of which locks to query (default: locks 1-12)
     */
    suspend fun pollStation(station: Int, mask: Int = 0x0FFF): PollResult

    /**
     * Open a door and wait for confirmation that it physically opened,
     * then optionally wait for it to close again.
     */
    suspend fun safeOpenDoor(
        station: Int,
        lock: Int,
        confirmTimeoutMs: Long = 5000
    ): SafeOpenResult
}

sealed class OpenResult {
    data object Success : OpenResult()
    data object Failed : OpenResult()  // Board responded but door didn't open
    data class Error(val message: String) : OpenResult()
    data object NotConnected : OpenResult()
    data object Timeout : OpenResult()
}

sealed class PollResult {
    data class Success(val doorStates: Map<Int, Boolean>) : PollResult()
    data class Error(val message: String) : PollResult()
    data object NotConnected : PollResult()
    data object Timeout : PollResult()
}

sealed class SafeOpenResult {
    data object Confirmed : SafeOpenResult()           // Door opened and confirmed via poll
    data object OpenNotConfirmed : SafeOpenResult()    // Open sent but poll didn't confirm
    data object OpenFailed : SafeOpenResult()          // Board said open failed
    data object NotConnected : SafeOpenResult()
}

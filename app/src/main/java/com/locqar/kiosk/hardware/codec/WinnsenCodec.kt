package com.locqar.kiosk.hardware.codec

/**
 * RS485 protocol codec for Winnsen lock controller boards.
 *
 * Frame format (from Winnsen Serial Command Document V202104):
 *
 * Open Lock:
 *   TX: 90 06 05 <station> <lock> 03        (6 bytes)
 *   RX: 90 07 85 <station> <lock> <status> 03 (7 bytes)
 *       status: 01 = opened, 00 = failed
 *
 * Poll State (check locks):
 *   TX: 90 07 02 <station> <lowMask> <highMask> 03 (7 bytes)
 *   RX: 90 07 82 <station> <lowState> <highState> 03 (7 bytes)
 *       Each bit: 1 = open, 0 = closed. Bits 0-15 = locks 1-16.
 *
 * Serial: 9600 baud, 8 data bits, 1 stop bit, no parity.
 */
object WinnsenCodec {

    // Frame markers
    private const val FRAME_HEADER: Byte = 0x90.toByte()
    private const val FRAME_END: Byte = 0x03

    // Function codes (TX)
    private const val FN_OPEN: Byte = 0x05
    private const val FN_POLL: Byte = 0x02

    // Function codes (RX)
    private const val FN_OPEN_RESP: Byte = 0x85.toByte()
    private const val FN_POLL_RESP: Byte = 0x82.toByte()

    // Response lengths
    const val OPEN_RESPONSE_LEN = 7
    const val POLL_RESPONSE_LEN = 7

    // ---------- Build commands ----------

    /**
     * Build an Open Lock command frame.
     * @param station Board station number (1-255, set by DIP switch)
     * @param lock Lock/door number on that board (1-16)
     */
    fun buildOpenCommand(station: Int, lock: Int): ByteArray {
        require(station in 1..255) { "Station must be 1-255, got $station" }
        require(lock in 1..16) { "Lock must be 1-16, got $lock" }
        return byteArrayOf(
            FRAME_HEADER,
            0x06, // length
            FN_OPEN,
            station.toByte(),
            lock.toByte(),
            FRAME_END
        )
    }

    /**
     * Build a Poll State command frame.
     * @param station Board station number (1-255)
     * @param mask 16-bit mask of which locks to query (bit 0 = lock 1, etc.)
     *             Default 0x0FFF queries locks 1-12.
     */
    fun buildPollCommand(station: Int, mask: Int = 0x0FFF): ByteArray {
        require(station in 1..255) { "Station must be 1-255, got $station" }
        val lowMask = mask and 0xFF
        val highMask = (mask shr 8) and 0xFF
        return byteArrayOf(
            FRAME_HEADER,
            0x07, // length
            FN_POLL,
            station.toByte(),
            lowMask.toByte(),
            highMask.toByte(),
            FRAME_END
        )
    }

    // ---------- Parse responses ----------

    data class OpenResponse(
        val station: Int,
        val lock: Int,
        val success: Boolean
    )

    data class PollResponse(
        val station: Int,
        val stateBits: Int,
        val doorStates: Map<Int, Boolean> // lock number -> isOpen
    )

    /**
     * Parse an Open Lock response.
     * @return OpenResponse or null if frame is invalid
     */
    fun parseOpenResponse(data: ByteArray): OpenResponse? {
        if (data.size < OPEN_RESPONSE_LEN) return null
        if (data[0] != FRAME_HEADER) return null
        if (data[2] != FN_OPEN_RESP) return null
        if (data[6] != FRAME_END) return null

        return OpenResponse(
            station = data[3].toInt() and 0xFF,
            lock = data[4].toInt() and 0xFF,
            success = (data[5].toInt() and 0xFF) == 0x01
        )
    }

    /**
     * Parse a Poll State response.
     * @return PollResponse or null if frame is invalid
     */
    fun parsePollResponse(data: ByteArray): PollResponse? {
        if (data.size < POLL_RESPONSE_LEN) return null
        if (data[0] != FRAME_HEADER) return null
        if (data[2] != FN_POLL_RESP) return null
        if (data[6] != FRAME_END) return null

        val station = data[3].toInt() and 0xFF
        val lowState = data[4].toInt() and 0xFF
        val highState = data[5].toInt() and 0xFF
        val stateBits = lowState or (highState shl 8)

        return PollResponse(
            station = station,
            stateBits = stateBits,
            doorStates = decodeDoorStates(stateBits)
        )
    }

    /**
     * Decode a 16-bit state bitmask into a map of lock number -> isOpen.
     * Bit 0 = lock 1, bit 1 = lock 2, etc. 1 = open, 0 = closed.
     */
    fun decodeDoorStates(stateBits: Int): Map<Int, Boolean> {
        return (1..16).associateWith { lock ->
            (stateBits shr (lock - 1)) and 1 == 1
        }
    }

    /**
     * Format bytes as hex string for logging.
     */
    fun toHex(data: ByteArray): String =
        data.joinToString(" ") { "%02X".format(it) }
}

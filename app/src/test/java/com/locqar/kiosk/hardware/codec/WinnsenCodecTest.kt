package com.locqar.kiosk.hardware.codec

import org.junit.Assert.*
import org.junit.Test

class WinnsenCodecTest {

    // ===== buildOpenCommand =====

    @Test
    fun `buildOpenCommand produces correct 6-byte frame`() {
        val cmd = WinnsenCodec.buildOpenCommand(station = 1, lock = 5)
        assertEquals(6, cmd.size)
        assertEquals(0x90.toByte(), cmd[0]) // header
        assertEquals(0x06.toByte(), cmd[1]) // length byte
        assertEquals(0x05.toByte(), cmd[2]) // function code: open
        assertEquals(0x01.toByte(), cmd[3]) // station
        assertEquals(0x05.toByte(), cmd[4]) // lock number
        assertEquals(0x03.toByte(), cmd[5]) // frame end
    }

    @Test
    fun `buildOpenCommand with max valid station and lock`() {
        val cmd = WinnsenCodec.buildOpenCommand(station = 255, lock = 16)
        assertEquals(0xFF.toByte(), cmd[3])
        assertEquals(16.toByte(), cmd[4])
    }

    @Test(expected = IllegalArgumentException::class)
    fun `buildOpenCommand rejects station 0`() {
        WinnsenCodec.buildOpenCommand(station = 0, lock = 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `buildOpenCommand rejects station 256`() {
        WinnsenCodec.buildOpenCommand(station = 256, lock = 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `buildOpenCommand rejects lock 0`() {
        WinnsenCodec.buildOpenCommand(station = 1, lock = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `buildOpenCommand rejects lock 17`() {
        WinnsenCodec.buildOpenCommand(station = 1, lock = 17)
    }

    // ===== buildPollCommand =====

    @Test
    fun `buildPollCommand produces correct 7-byte frame`() {
        val cmd = WinnsenCodec.buildPollCommand(station = 2, mask = 0x0FFF)
        assertEquals(7, cmd.size)
        assertEquals(0x90.toByte(), cmd[0])
        assertEquals(0x07.toByte(), cmd[1])
        assertEquals(0x02.toByte(), cmd[2]) // function code: poll
        assertEquals(0x02.toByte(), cmd[3]) // station
        assertEquals(0xFF.toByte(), cmd[4]) // low mask (0x0FFF & 0xFF = 0xFF)
        assertEquals(0x0F.toByte(), cmd[5]) // high mask (0x0FFF >> 8 = 0x0F)
        assertEquals(0x03.toByte(), cmd[6])
    }

    @Test
    fun `buildPollCommand with full 16-bit mask`() {
        val cmd = WinnsenCodec.buildPollCommand(station = 1, mask = 0xFFFF)
        assertEquals(0xFF.toByte(), cmd[4])
        assertEquals(0xFF.toByte(), cmd[5])
    }

    @Test
    fun `buildPollCommand with single lock mask`() {
        // Query only lock 1 (bit 0)
        val cmd = WinnsenCodec.buildPollCommand(station = 1, mask = 0x0001)
        assertEquals(0x01.toByte(), cmd[4])
        assertEquals(0x00.toByte(), cmd[5])
    }

    // ===== parseOpenResponse =====

    @Test
    fun `parseOpenResponse with successful open`() {
        val data = byteArrayOf(
            0x90.toByte(), 0x07, 0x85.toByte(),
            0x01, // station
            0x05, // lock
            0x01, // status: success
            0x03  // frame end
        )
        val resp = WinnsenCodec.parseOpenResponse(data)
        assertNotNull(resp)
        assertEquals(1, resp!!.station)
        assertEquals(5, resp.lock)
        assertTrue(resp.success)
    }

    @Test
    fun `parseOpenResponse with failed open`() {
        val data = byteArrayOf(
            0x90.toByte(), 0x07, 0x85.toByte(),
            0x01, 0x03, 0x00, 0x03
        )
        val resp = WinnsenCodec.parseOpenResponse(data)
        assertNotNull(resp)
        assertFalse(resp!!.success)
    }

    @Test
    fun `parseOpenResponse returns null for too short data`() {
        val data = byteArrayOf(0x90.toByte(), 0x07, 0x85.toByte(), 0x01)
        assertNull(WinnsenCodec.parseOpenResponse(data))
    }

    @Test
    fun `parseOpenResponse returns null for wrong header`() {
        val data = byteArrayOf(0x00, 0x07, 0x85.toByte(), 0x01, 0x05, 0x01, 0x03)
        assertNull(WinnsenCodec.parseOpenResponse(data))
    }

    @Test
    fun `parseOpenResponse returns null for wrong function code`() {
        val data = byteArrayOf(0x90.toByte(), 0x07, 0x82.toByte(), 0x01, 0x05, 0x01, 0x03)
        assertNull(WinnsenCodec.parseOpenResponse(data))
    }

    @Test
    fun `parseOpenResponse returns null for wrong frame end`() {
        val data = byteArrayOf(0x90.toByte(), 0x07, 0x85.toByte(), 0x01, 0x05, 0x01, 0xFF.toByte())
        assertNull(WinnsenCodec.parseOpenResponse(data))
    }

    // ===== parsePollResponse =====

    @Test
    fun `parsePollResponse all doors closed`() {
        val data = byteArrayOf(
            0x90.toByte(), 0x07, 0x82.toByte(),
            0x01, // station
            0x00, // low state (all closed)
            0x00, // high state (all closed)
            0x03
        )
        val resp = WinnsenCodec.parsePollResponse(data)
        assertNotNull(resp)
        assertEquals(1, resp!!.station)
        assertEquals(0, resp.stateBits)
        // All 16 doors should be closed
        for (lock in 1..16) {
            assertFalse("Lock $lock should be closed", resp.doorStates[lock] ?: true)
        }
    }

    @Test
    fun `parsePollResponse door 1 open`() {
        val data = byteArrayOf(
            0x90.toByte(), 0x07, 0x82.toByte(),
            0x01, 0x01, 0x00, 0x03
        )
        val resp = WinnsenCodec.parsePollResponse(data)!!
        assertTrue(resp.doorStates[1]!!)
        assertFalse(resp.doorStates[2]!!)
    }

    @Test
    fun `parsePollResponse doors 1 and 9 open`() {
        // Door 1 = bit 0 in low byte, Door 9 = bit 0 in high byte
        val data = byteArrayOf(
            0x90.toByte(), 0x07, 0x82.toByte(),
            0x01,
            0x01, // low: bit 0 set (door 1)
            0x01, // high: bit 0 set (door 9)
            0x03
        )
        val resp = WinnsenCodec.parsePollResponse(data)!!
        assertTrue(resp.doorStates[1]!!)
        assertFalse(resp.doorStates[2]!!)
        assertTrue(resp.doorStates[9]!!)
        assertFalse(resp.doorStates[10]!!)
    }

    @Test
    fun `parsePollResponse all doors open`() {
        val data = byteArrayOf(
            0x90.toByte(), 0x07, 0x82.toByte(),
            0x01, 0xFF.toByte(), 0xFF.toByte(), 0x03
        )
        val resp = WinnsenCodec.parsePollResponse(data)!!
        assertEquals(0xFFFF, resp.stateBits)
        for (lock in 1..16) {
            assertTrue("Lock $lock should be open", resp.doorStates[lock]!!)
        }
    }

    @Test
    fun `parsePollResponse returns null for invalid data`() {
        assertNull(WinnsenCodec.parsePollResponse(byteArrayOf(0x00)))
        assertNull(WinnsenCodec.parsePollResponse(byteArrayOf()))
    }

    // ===== decodeDoorStates =====

    @Test
    fun `decodeDoorStates maps bits to lock numbers correctly`() {
        // 0b0000_0000_0010_1010 = locks 2, 4, 6 open
        val states = WinnsenCodec.decodeDoorStates(0b0000_0000_0010_1010)
        assertFalse(states[1]!!)
        assertTrue(states[2]!!)
        assertFalse(states[3]!!)
        assertTrue(states[4]!!)
        assertFalse(states[5]!!)
        assertTrue(states[6]!!)
    }

    // ===== toHex =====

    @Test
    fun `toHex formats bytes correctly`() {
        val hex = WinnsenCodec.toHex(byteArrayOf(0x90.toByte(), 0x06, 0x05, 0x01, 0x01, 0x03))
        assertEquals("90 06 05 01 01 03", hex)
    }

    @Test
    fun `toHex handles empty array`() {
        assertEquals("", WinnsenCodec.toHex(byteArrayOf()))
    }

    // ===== Round-trip test =====

    @Test
    fun `open command round-trip - build then parse matching response`() {
        val cmd = WinnsenCodec.buildOpenCommand(station = 3, lock = 7)
        // Simulate a success response for station 3, lock 7
        val response = byteArrayOf(
            0x90.toByte(), 0x07, 0x85.toByte(),
            cmd[3], cmd[4], 0x01, 0x03
        )
        val parsed = WinnsenCodec.parseOpenResponse(response)!!
        assertEquals(3, parsed.station)
        assertEquals(7, parsed.lock)
        assertTrue(parsed.success)
    }
}

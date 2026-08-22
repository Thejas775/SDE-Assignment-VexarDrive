package com.thejas.fleetmanagementtask

import com.thejas.fleetmanagementtask.core.PingBuffer
import com.thejas.fleetmanagementtask.data.remote.dto.LocationPingRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PingBufferTest {

    private fun ping(index: Int) = LocationPingRequest(
        latitude = "12.9700%02d".format(index % 100),
        longitude = "77.590000",
        recordedAt = "2026-08-23T10:%02d:00Z".format(index % 60),
    )

    @Test
    fun `it starts empty`() {
        val buffer = PingBuffer()
        assertTrue(buffer.isEmpty)
        assertEquals(0, buffer.size)
    }

    @Test
    fun `pings come back in the order they were recorded`() {
        val buffer = PingBuffer()
        repeat(3) { buffer.add(ping(it)) }

        val batch = buffer.peek()

        assertEquals(3, batch.size)
        assertEquals(ping(0).recordedAt, batch.first().recordedAt)
        assertEquals(ping(2).recordedAt, batch.last().recordedAt)
    }

    @Test
    fun `peeking does not remove anything`() {
        val buffer = PingBuffer()
        repeat(3) { buffer.add(ping(it)) }

        buffer.peek()

        assertEquals(3, buffer.size)
    }

    @Test
    fun `a failed upload keeps the pings for the next attempt`() {
        val buffer = PingBuffer()
        repeat(3) { buffer.add(ping(it)) }

        val batch = buffer.peek()
        // upload fails: confirm() is never called
        assertEquals(3, buffer.size)
        assertEquals(batch.map { it.recordedAt }, buffer.peek().map { it.recordedAt })
    }

    @Test
    fun `confirming removes only what the server accepted`() {
        val buffer = PingBuffer()
        repeat(5) { buffer.add(ping(it)) }

        buffer.confirm(2)

        assertEquals(3, buffer.size)
        assertEquals(ping(2).recordedAt, buffer.peek().first().recordedAt)
    }

    @Test
    fun `confirming more than is held is harmless`() {
        val buffer = PingBuffer()
        repeat(2) { buffer.add(ping(it)) }

        buffer.confirm(10)

        assertTrue(buffer.isEmpty)
    }

    @Test
    fun `a batch is capped so one upload cannot be unbounded`() {
        val buffer = PingBuffer(capacity = 400)
        repeat(300) { buffer.add(ping(it)) }

        assertEquals(PingBuffer.MAX_BATCH, buffer.peek().size)
        assertEquals(300, buffer.size)
    }

    @Test
    fun `going over capacity drops the oldest readings`() {
        val buffer = PingBuffer(capacity = 3)
        repeat(5) { buffer.add(ping(it)) }

        assertEquals(3, buffer.size)
        assertEquals(2, buffer.dropped)
        // The three most recent survive.
        assertEquals(ping(2).recordedAt, buffer.peek().first().recordedAt)
        assertEquals(ping(4).recordedAt, buffer.peek().last().recordedAt)
    }

    @Test
    fun `clearing empties the buffer and the drop count`() {
        val buffer = PingBuffer(capacity = 2)
        repeat(5) { buffer.add(ping(it)) }

        buffer.clear()

        assertTrue(buffer.isEmpty)
        assertEquals(0, buffer.dropped)
        assertFalse(buffer.peek().isNotEmpty())
    }

    @Test
    fun `a long offline stretch then a flush drains in batches`() {
        val buffer = PingBuffer()
        repeat(250) { buffer.add(ping(it)) }

        var sent = 0
        while (!buffer.isEmpty) {
            val batch = buffer.peek()
            sent += batch.size
            buffer.confirm(batch.size)
        }

        assertEquals(250, sent)
        assertTrue(buffer.isEmpty)
    }
}

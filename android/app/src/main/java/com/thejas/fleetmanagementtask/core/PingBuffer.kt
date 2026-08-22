package com.thejas.fleetmanagementtask.core

import com.thejas.fleetmanagementtask.data.remote.dto.LocationPingRequest

/**
 * Holds GPS readings until the network takes them.
 *
 * Pings are only removed once the server has confirmed them, so a failed upload
 * simply retries on the next flush. The server deduplicates on
 * (trip_id, recorded_at), which is what makes a blind retry safe.
 */
class PingBuffer(private val capacity: Int = DEFAULT_CAPACITY) {

    private val pings = ArrayDeque<LocationPingRequest>()

    var dropped: Int = 0
        private set

    val size: Int get() = pings.size
    val isEmpty: Boolean get() = pings.isEmpty()

    @Synchronized
    fun add(ping: LocationPingRequest) {
        // A dead zone longer than the buffer costs the oldest readings; the
        // recent trail matters more for live tracking than the start of it.
        if (pings.size >= capacity) {
            pings.removeFirst()
            dropped++
        }
        pings.addLast(ping)
    }

    @Synchronized
    fun peek(max: Int = MAX_BATCH): List<LocationPingRequest> =
        pings.take(minOf(max, pings.size))

    /** Called only after the server has accepted them. */
    @Synchronized
    fun confirm(count: Int) {
        repeat(minOf(count, pings.size)) { pings.removeFirst() }
    }

    @Synchronized
    fun clear() {
        pings.clear()
        dropped = 0
    }

    companion object {
        const val DEFAULT_CAPACITY = 500
        const val MAX_BATCH = 100
    }
}

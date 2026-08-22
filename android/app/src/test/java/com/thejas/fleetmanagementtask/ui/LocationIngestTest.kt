package com.thejas.fleetmanagementtask.ui

import com.thejas.fleetmanagementtask.core.ApiResult
import com.thejas.fleetmanagementtask.data.remote.ApiFactory
import com.thejas.fleetmanagementtask.data.remote.FleetApi
import com.thejas.fleetmanagementtask.data.remote.dto.LocationPingRequest
import com.thejas.fleetmanagementtask.data.repository.LocationRepository
import com.thejas.fleetmanagementtask.fake.InMemoryTokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocationIngestTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var server: MockWebServer
    private lateinit var repository: LocationRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        server = MockWebServer().also { it.start() }
        val retrofit = ApiFactory.create(
            tokens = InMemoryTokenStore().apply { seed() },
            baseUrl = server.url("/api/v1/").toString(),
            onSessionExpired = {},
        )
        repository = LocationRepository(retrofit.create(FleetApi::class.java), dispatcher)
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    private fun ping(minute: Int) = LocationPingRequest(
        latitude = "12.971599",
        longitude = "77.594566",
        recordedAt = "2026-08-23T10:%02d:00Z".format(minute),
        speedKmph = "54.20",
    )

    @Test
    fun `a batch is posted with the trip id and every ping`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(202)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"accepted":3,"duplicates":0,"trip_status":"IN_PROGRESS"}""")
        )

        val result = repository.send("t-1", listOf(ping(1), ping(2), ping(3)))

        val data = (result as ApiResult.Success).data
        assertEquals(3, data.accepted)
        assertEquals("IN_PROGRESS", data.tripStatus)

        val request = server.takeRequest()
        assertEquals("/api/v1/locations", request.path)
        val body = request.body.readUtf8()
        assertTrue(body, body.contains("\"trip_id\":\"t-1\""))
        assertTrue(body, body.contains("\"recorded_at\":\"2026-08-23T10:01:00Z\""))
        assertTrue(body, body.contains("\"speed_kmph\":\"54.20\""))
    }

    @Test
    fun `resending the same batch reports duplicates, not new points`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(202)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"accepted":0,"duplicates":3,"trip_status":"IN_PROGRESS"}""")
        )

        val data = (repository.send("t-1", listOf(ping(1), ping(2), ping(3)))
            as ApiResult.Success).data

        assertEquals(0, data.accepted)
        assertEquals(3, data.duplicates)
    }

    @Test
    fun `the first accepted ping moves the trip to in progress`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(202)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"accepted":1,"duplicates":0,"trip_status":"IN_PROGRESS"}""")
        )

        val data = (repository.send("t-1", listOf(ping(1))) as ApiResult.Success).data

        assertEquals("IN_PROGRESS", data.tripStatus)
    }

    @Test
    fun `posting to a finished trip is a conflict`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(409)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error_message":"Trip is COMPLETED; location updates are not accepted"}""")
        )

        val result = repository.send("t-1", listOf(ping(1)))

        assertTrue((result as ApiResult.Failure).error.isConflict)
    }

    @Test
    fun `an unreachable server is a network failure, so the buffer is kept`() =
        runTest(dispatcher) {
            server.shutdown()

            val result = repository.send("t-1", listOf(ping(1)))

            assertTrue((result as ApiResult.Failure).error.isNetwork)
        }

    @Test
    fun `optional fields are omitted rather than sent as null`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(202)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"accepted":1,"duplicates":0,"trip_status":"IN_PROGRESS"}""")
        )

        repository.send(
            "t-1",
            listOf(
                LocationPingRequest(
                    latitude = "12.971599",
                    longitude = "77.594566",
                    recordedAt = "2026-08-23T10:00:00Z",
                )
            ),
        )

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body, !body.contains("\"heading\""))
        assertTrue(body, !body.contains("\"accuracy_m\""))
    }
}

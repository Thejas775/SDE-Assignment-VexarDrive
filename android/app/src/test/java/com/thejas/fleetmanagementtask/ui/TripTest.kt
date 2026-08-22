package com.thejas.fleetmanagementtask.ui

import com.thejas.fleetmanagementtask.core.ApiResult
import com.thejas.fleetmanagementtask.data.remote.ApiFactory
import com.thejas.fleetmanagementtask.data.remote.FleetApi
import com.thejas.fleetmanagementtask.data.remote.dto.TripCreateRequest
import com.thejas.fleetmanagementtask.data.repository.TripRepository
import com.thejas.fleetmanagementtask.fake.InMemoryTokenStore
import com.thejas.fleetmanagementtask.ui.trips.TripListViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TripTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var server: MockWebServer
    private lateinit var repository: TripRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        server = MockWebServer().also { it.start() }
        val retrofit = ApiFactory.create(
            tokens = InMemoryTokenStore().apply { seed() },
            baseUrl = server.url("/api/v1/").toString(),
            onSessionExpired = {},
        )
        repository = TripRepository(retrofit.create(FleetApi::class.java), dispatcher)
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    private fun json(code: Int, body: String) = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private val scheduledTrip = """
        {"id":"t-1","trip_number":"TRP1002",
         "vehicle":{"id":"v-1","registration_number":"KA-01-AB-1234","make":"Tata","model":"Ace"},
         "driver":{"id":"d-1","full_name":"Rahul Sharma","license_number":"KA0120230001234"},
         "source":"Bangalore","destination":"Chennai",
         "scheduled_start":"2026-08-23T08:00:00Z","scheduled_end":"2026-08-23T18:00:00Z",
         "status":"SCHEDULED","actual_start":null,"actual_end":null,
         "start_odometer":null,"end_odometer":null,"start_latitude":null,"start_longitude":null,
         "end_latitude":null,"end_longitude":null,"distance_km":null,"notes":null,
         "created_at":"2026-08-22T21:48:45.153928Z","duration_minutes":null}
    """.trimIndent()

    private val completedTrip = """
        {"id":"t-2","trip_number":"TRP1007",
         "vehicle":{"id":"v-1","registration_number":"KA-01-AB-1234","make":"Tata","model":"Ace"},
         "driver":{"id":"d-1","full_name":"Rahul Sharma","license_number":"KA0120230001234"},
         "source":"Bangalore","destination":"Chennai",
         "scheduled_start":"2026-08-23T08:00:00Z","scheduled_end":"2026-08-23T18:00:00Z",
         "status":"COMPLETED","actual_start":"2026-08-22T17:55:17.478250Z",
         "actual_end":"2026-08-22T17:55:18.051048Z","start_odometer":48250,"end_odometer":48596,
         "start_latitude":"12.971599","start_longitude":"77.594566",
         "end_latitude":"13.082680","end_longitude":"80.270721","distance_km":"346.00",
         "notes":null,"created_at":"2026-08-22T21:48:45.153928Z","duration_minutes":435}
    """.trimIndent()

    private fun tripPage(body: String) =
        json(200, """{"items":[$body],"total":1,"page":1,"page_size":20,"pages":1}""")

    @Test
    fun `a scheduled trip decodes with every actual field null`() = runTest(dispatcher) {
        server.enqueue(tripPage(scheduledTrip))

        val trip = TripListViewModel(repository).state.first { it.items.isNotEmpty() }.items.first()

        assertNull(trip.actualStart)
        assertNull(trip.startOdometer)
        assertNull(trip.distanceKm)
        assertNull(trip.durationMinutes)
        assertEquals("Bangalore → Chennai", trip.route)
        assertFalse(trip.isActive)
        assertTrue(trip.isCancellable)
    }

    @Test
    fun `a completed trip keeps distance as an exact string`() = runTest(dispatcher) {
        server.enqueue(tripPage(completedTrip))

        val trip = TripListViewModel(repository).state.first { it.items.isNotEmpty() }.items.first()

        assertEquals("346.00", trip.distanceKm)
        assertEquals(48250, trip.startOdometer)
        assertEquals(48596, trip.endOdometer)
        assertEquals(435, trip.durationMinutes)
        assertFalse(trip.isCancellable)
    }

    @Test
    fun `a status filter reaches the query string`() = runTest(dispatcher) {
        server.enqueue(tripPage(scheduledTrip))
        val viewModel = TripListViewModel(repository)
        viewModel.state.first { it.items.isNotEmpty() }
        server.takeRequest()

        server.enqueue(tripPage(completedTrip))
        viewModel.onStatusSelected("COMPLETED")
        viewModel.state.first { it.items.firstOrNull()?.tripNumber == "TRP1007" }

        assertTrue(server.takeRequest().path!!.contains("status=COMPLETED"))
    }

    @Test
    fun `creating a trip without an assignment is refused`() = runTest(dispatcher) {
        server.enqueue(
            json(409, """{"error_message":"Rahul Sharma is not assigned to KA-01-AB-1234 on 2026-08-23"}""")
        )

        val result = repository.create(
            TripCreateRequest("v-1", "d-1", "Bangalore", "Chennai",
                "2026-08-23T08:00:00Z", "2026-08-23T18:00:00Z")
        )

        val error = (result as ApiResult.Failure).error
        assertTrue(error.isConflict)
        assertTrue(error.message.contains("is not assigned to"))
    }

    @Test
    fun `a double booked driver is refused`() = runTest(dispatcher) {
        server.enqueue(
            json(409, """{"error_message":"Driver already has trip TRP1007 in that window"}""")
        )

        val result = repository.create(
            TripCreateRequest("v-1", "d-1", "A town", "B town",
                "2026-08-23T08:00:00Z", "2026-08-23T18:00:00Z")
        )

        assertTrue((result as ApiResult.Failure).error.isConflict)
        assertTrue(result.error.message.contains("TRP1007"))
    }

    @Test
    fun `an end before start comes back as a field error`() = runTest(dispatcher) {
        server.enqueue(
            json(
                422,
                """{"detail":[{"loc":["body","scheduled_end"],
                   "msg":"Value error, scheduled_end must be after scheduled_start"}]}"""
            )
        )

        val result = repository.create(
            TripCreateRequest("v-1", "d-1", "A town", "B town",
                "2026-08-23T18:00:00Z", "2026-08-23T08:00:00Z")
        )

        assertEquals(
            "scheduled_end must be after scheduled_start",
            (result as ApiResult.Failure).error.fieldErrors["scheduled_end"],
        )
    }

    @Test
    fun `cancelling a completed trip is refused`() = runTest(dispatcher) {
        server.enqueue(
            json(409, """{"error_message":"Cannot move a COMPLETED trip to CANCELLED"}""")
        )

        val result = repository.cancel("t-2", null)

        assertTrue((result as ApiResult.Failure).error.isConflict)
    }

    @Test
    fun `the route decodes as a plain array`() = runTest(dispatcher) {
        server.enqueue(
            json(
                200,
                """[{"id":"p-1","latitude":"12.970000","longitude":"77.590000",
                     "speed_kmph":"54.20","recorded_at":"2026-08-22T17:25:17Z"}]"""
            )
        )

        val points = (repository.route("t-2") as ApiResult.Success).data

        assertEquals(1, points.size)
        assertEquals("12.970000", points.first().latitude)
        assertEquals("54.20", points.first().speedKmph)
    }

    @Test
    fun `an empty route is a valid response`() = runTest(dispatcher) {
        server.enqueue(json(200, "[]"))

        val points = (repository.route("t-1") as ApiResult.Success).data

        assertTrue(points.isEmpty())
    }

    @Test
    fun `an active trip is flagged as active`() = runTest(dispatcher) {
        server.enqueue(tripPage(scheduledTrip.replace("\"SCHEDULED\"", "\"IN_PROGRESS\"")))

        val trip = TripListViewModel(repository).state.first { it.items.isNotEmpty() }.items.first()

        assertTrue(trip.isActive)
        assertTrue(trip.isCancellable)
    }
}

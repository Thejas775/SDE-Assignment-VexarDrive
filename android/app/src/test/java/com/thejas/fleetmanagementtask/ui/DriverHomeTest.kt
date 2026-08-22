package com.thejas.fleetmanagementtask.ui

import com.thejas.fleetmanagementtask.data.remote.ApiFactory
import com.thejas.fleetmanagementtask.data.remote.FleetApi
import com.thejas.fleetmanagementtask.data.repository.TripRepository
import com.thejas.fleetmanagementtask.data.repository.VehicleRepository
import com.thejas.fleetmanagementtask.fake.InMemoryTokenStore
import com.thejas.fleetmanagementtask.ui.driver.DriverHomeEvent
import com.thejas.fleetmanagementtask.ui.driver.DriverHomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DriverHomeTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var server: MockWebServer
    private lateinit var vehicles: VehicleRepository
    private lateinit var trips: TripRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        server = MockWebServer().also { it.start() }
        val retrofit = ApiFactory.create(
            tokens = InMemoryTokenStore().apply { seed() },
            baseUrl = server.url("/api/v1/").toString(),
            onSessionExpired = {},
        )
        val api = retrofit.create(FleetApi::class.java)
        vehicles = VehicleRepository(api, dispatcher)
        trips = TripRepository(api, dispatcher)
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    private fun ok(body: String) = MockResponse().setResponseCode(200)
        .setHeader("Content-Type", "application/json").setBody(body)

    private fun error(code: Int, message: String) = MockResponse().setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody("""{"error_message":"$message"}""")

    private val vehicle = """
        {"id":"v-1","registration_number":"KA-01-AB-1234","vehicle_type":"TRUCK","make":"Tata",
         "model":"Ace","year":2022,"fuel_type":"DIESEL","current_mileage":48250,
         "status":"AVAILABLE","insurance_expiry":"2029-09-05","registration_expiry":"2030-01-01",
         "insurance_expiring_soon":false,"registration_expiring_soon":false}
    """.trimIndent()

    private fun trip(status: String, startOdo: String = "null", distance: String = "null") = """
        {"id":"t-1","trip_number":"TRP1007",
         "vehicle":{"id":"v-1","registration_number":"KA-01-AB-1234","make":"Tata","model":"Ace"},
         "driver":{"id":"d-1","full_name":"Rahul Sharma","license_number":"KA01"},
         "source":"Bangalore","destination":"Chennai",
         "scheduled_start":"2026-08-23T08:00:00Z","scheduled_end":"2026-08-23T18:00:00Z",
         "status":"$status","actual_start":null,"actual_end":null,
         "start_odometer":$startOdo,"end_odometer":null,
         "start_latitude":null,"start_longitude":null,"end_latitude":null,"end_longitude":null,
         "distance_km":$distance,"notes":null,"duration_minutes":null}
    """.trimIndent()

    private fun page(body: String?) = ok(
        """{"items":[${body ?: ""}],"total":${if (body == null) 0 else 1},
            "page":1,"page_size":20,"pages":${if (body == null) 0 else 1}}"""
    )

    /** Routes by path so the two trip queries can answer differently. */
    private fun route(
        vehicleResponse: MockResponse,
        activeTrip: String?,
        scheduledTrip: String?,
    ) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.contains("my-vehicle") -> vehicleResponse
                    path.contains("active_only=true") -> page(activeTrip)
                    path.contains("trips/my") -> page(scheduledTrip)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
    }

    @Test
    fun `it shows the assigned vehicle and the next scheduled trip`() = runTest(dispatcher) {
        route(ok(vehicle), activeTrip = null, scheduledTrip = trip("SCHEDULED"))

        val state = DriverHomeViewModel(vehicles, trips).state.first { it.trip != null }

        assertEquals("KA-01-AB-1234", state.vehicle?.registrationNumber)
        assertEquals("TRP1007", state.trip?.tripNumber)
        assertTrue(state.canStart)
        assertFalse(state.canComplete)
    }

    @Test
    fun `a running trip takes priority over a scheduled one`() = runTest(dispatcher) {
        route(ok(vehicle), activeTrip = trip("IN_PROGRESS", startOdo = "48250"), scheduledTrip = null)

        val state = DriverHomeViewModel(vehicles, trips).state.first { it.trip != null }

        assertEquals("IN_PROGRESS", state.trip?.status)
        assertTrue(state.canComplete)
        assertFalse(state.canStart)
    }

    @Test
    fun `no assigned vehicle is not treated as an error state`() = runTest(dispatcher) {
        route(
            error(404, "No vehicle is currently assigned to you"),
            activeTrip = null,
            scheduledTrip = null,
        )

        val state = DriverHomeViewModel(vehicles, trips).state.first { !it.isLoading }

        assertNull(state.vehicle)
        assertNull(state.trip)
        assertFalse(state.hasTrip)
    }

    @Test
    fun `starting sends the odometer and coordinates`() = runTest(dispatcher) {
        route(ok(vehicle), activeTrip = null, scheduledTrip = trip("SCHEDULED"))
        val viewModel = DriverHomeViewModel(vehicles, trips)
        viewModel.state.first { it.trip != null }

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path.orEmpty().endsWith("/start") -> ok(trip("STARTED", "48250"))
                request.path.orEmpty().contains("my-vehicle") -> ok(vehicle)
                else -> page(null)
            }
        }
        viewModel.start(48250, "12.971599", "77.594566")

        val event = viewModel.events.first()
        assertTrue(event is DriverHomeEvent.Started)

        val body = generateSequence { server.takeRequest(1, java.util.concurrent.TimeUnit.SECONDS) }
            .firstOrNull { it.path.orEmpty().endsWith("/start") }
            ?.body?.readUtf8()
            .orEmpty()
        assertTrue(body, body.contains("\"start_odometer\":48250"))
        assertTrue(body, body.contains("\"latitude\":\"12.971599\""))
    }

    @Test
    fun `an odometer below the vehicle reading is rejected by the server`() = runTest(dispatcher) {
        route(ok(vehicle), activeTrip = null, scheduledTrip = trip("SCHEDULED"))
        val viewModel = DriverHomeViewModel(vehicles, trips)
        viewModel.state.first { it.trip != null }

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) =
                error(422, "Odometer 100 is below the recorded 48250 km")
        }
        viewModel.start(100, null, null)

        val event = viewModel.events.first()
        assertEquals(
            "Odometer 100 is below the recorded 48250 km",
            (event as DriverHomeEvent.Message).text,
        )
        assertFalse(viewModel.state.value.isWorking)
    }

    @Test
    fun `completing reports the distance the server calculated`() = runTest(dispatcher) {
        route(ok(vehicle), activeTrip = trip("IN_PROGRESS", "48250"), scheduledTrip = null)
        val viewModel = DriverHomeViewModel(vehicles, trips)
        viewModel.state.first { it.trip != null }

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path.orEmpty().endsWith("/complete") ->
                    ok(trip("COMPLETED", "48250", "\"346.00\""))
                request.path.orEmpty().contains("my-vehicle") -> ok(vehicle)
                else -> page(null)
            }
        }
        viewModel.complete(48596, null, null)

        val event = viewModel.events.first()
        assertEquals("346.00", (event as DriverHomeEvent.Completed).trip.distanceKm)
    }

    @Test
    fun `a trip can be started without a location fix`() = runTest(dispatcher) {
        route(ok(vehicle), activeTrip = null, scheduledTrip = trip("SCHEDULED"))
        val viewModel = DriverHomeViewModel(vehicles, trips)
        viewModel.state.first { it.trip != null }

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path.orEmpty().endsWith("/start") -> ok(trip("STARTED", "48250"))
                request.path.orEmpty().contains("my-vehicle") -> ok(vehicle)
                else -> page(null)
            }
        }
        viewModel.start(48250, null, null)

        assertTrue(viewModel.events.first() is DriverHomeEvent.Started)
    }

    @Test
    fun `tapping start twice only sends one request`() = runTest(dispatcher) {
        route(ok(vehicle), activeTrip = null, scheduledTrip = trip("SCHEDULED"))
        val viewModel = DriverHomeViewModel(vehicles, trips)
        viewModel.state.first { it.trip != null }

        var startCalls = 0
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path.orEmpty().endsWith("/start") -> {
                    startCalls++
                    ok(trip("STARTED", "48250"))
                }
                request.path.orEmpty().contains("my-vehicle") -> ok(vehicle)
                else -> page(null)
            }
        }
        viewModel.start(48250, null, null)
        viewModel.start(48250, null, null)
        viewModel.events.first()

        assertEquals(1, startCalls)
    }
}

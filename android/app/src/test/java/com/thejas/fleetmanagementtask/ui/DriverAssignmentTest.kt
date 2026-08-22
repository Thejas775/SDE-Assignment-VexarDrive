package com.thejas.fleetmanagementtask.ui

import com.thejas.fleetmanagementtask.core.ApiResult
import com.thejas.fleetmanagementtask.data.remote.ApiFactory
import com.thejas.fleetmanagementtask.data.remote.FleetApi
import com.thejas.fleetmanagementtask.data.remote.dto.AssignmentCreateRequest
import com.thejas.fleetmanagementtask.data.repository.AssignmentRepository
import com.thejas.fleetmanagementtask.data.repository.DriverRepository
import com.thejas.fleetmanagementtask.fake.InMemoryTokenStore
import com.thejas.fleetmanagementtask.ui.drivers.DriverListViewModel
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DriverAssignmentTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var server: MockWebServer
    private lateinit var drivers: DriverRepository
    private lateinit var assignments: AssignmentRepository

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
        drivers = DriverRepository(api, dispatcher)
        assignments = AssignmentRepository(api, dispatcher)
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

    private fun driver(assigned: Boolean) = """
        {"id":"d-1","user_id":"u-1","email":"rahul@fleet.in","full_name":"Rahul Sharma",
         "phone_number":"+919876543210","license_number":"KA0120230001234",
         "license_expiry":"2029-03-14","status":"ACTIVE","can_login":true,
         "assigned_vehicle":${if (assigned) """{"id":"v-1","registration_number":"KA-01-AB-1234","make":"Tata","model":"Ace"}""" else "null"},
         "created_at":"2026-08-22T21:48:44.213601Z",
         "license_expiring_soon":false,"license_expired":false}
    """.trimIndent()

    private fun driverPage(body: String) =
        json(200, """{"items":[$body],"total":1,"page":1,"page_size":20,"pages":1}""")

    @Test
    fun `the driver list loads and reports the total`() = runTest(dispatcher) {
        server.enqueue(driverPage(driver(assigned = true)))

        val state = DriverListViewModel(drivers).state.first { it.items.isNotEmpty() }

        assertEquals("Rahul Sharma", state.items.first().fullName)
        assertEquals(1, state.total)
        assertEquals("/api/v1/drivers?page=1&page_size=20", server.takeRequest().path)
    }

    @Test
    fun `an assigned vehicle is exposed for the row`() = runTest(dispatcher) {
        server.enqueue(driverPage(driver(assigned = true)))

        val state = DriverListViewModel(drivers).state.first { it.items.isNotEmpty() }

        assertEquals("KA-01-AB-1234 · Tata Ace", state.items.first().assignedVehicle?.label)
    }

    @Test
    fun `an unassigned driver decodes with a null vehicle`() = runTest(dispatcher) {
        server.enqueue(driverPage(driver(assigned = false)))

        val state = DriverListViewModel(drivers).state.first { it.items.isNotEmpty() }

        assertNull(state.items.first().assignedVehicle)
    }

    @Test
    fun `a status filter reaches the query string`() = runTest(dispatcher) {
        server.enqueue(driverPage(driver(assigned = true)))
        val viewModel = DriverListViewModel(drivers)
        viewModel.state.first { it.items.isNotEmpty() }
        server.takeRequest()

        server.enqueue(driverPage(driver(assigned = false)))
        viewModel.onStatusSelected("SUSPENDED")
        viewModel.state.first { it.items.firstOrNull()?.assignedVehicle == null }

        assertTrue(server.takeRequest().path!!.contains("status=SUSPENDED"))
    }

    // ------------------------------------------------ section 8 conflicts --

    @Test
    fun `an overlapping assignment returns a conflict naming the clash`() = runTest(dispatcher) {
        server.enqueue(
            json(
                409,
                """{"error_message":"KA-01-AB-1234 is already assigned from 2026-08-17 to 2026-08-25"}"""
            )
        )

        val result = assignments.create(
            AssignmentCreateRequest("v-1", "d-1", "2026-08-20", "2026-08-30")
        )

        val error = (result as ApiResult.Failure).error
        assertTrue(error.isConflict)
        assertEquals(
            "KA-01-AB-1234 is already assigned from 2026-08-17 to 2026-08-25",
            error.message,
        )
    }

    @Test
    fun `a driver already holding a vehicle is also a conflict`() = runTest(dispatcher) {
        server.enqueue(
            json(409, """{"error_message":"Rahul Sharma already has a vehicle from 2026-08-17 to 2026-08-25"}""")
        )

        val result = assignments.create(
            AssignmentCreateRequest("v-2", "d-1", "2026-08-20", "2026-08-30")
        )

        assertTrue((result as ApiResult.Failure).error.isConflict)
        assertTrue(result.error.message.contains("already has a vehicle"))
    }

    @Test
    fun `dates out of order come back as a field error`() = runTest(dispatcher) {
        server.enqueue(
            json(
                422,
                """{"detail":[{"loc":["body","end_date"],
                   "msg":"Value error, end_date cannot be before start_date"}]}"""
            )
        )

        val result = assignments.create(
            AssignmentCreateRequest("v-1", "d-1", "2026-08-30", "2026-08-20")
        )

        val error = (result as ApiResult.Failure).error
        assertEquals("end_date cannot be before start_date", error.fieldErrors["end_date"])
    }

    @Test
    fun `an accepted assignment decodes with its period`() = runTest(dispatcher) {
        server.enqueue(
            json(
                201,
                """{"id":"a-1",
                    "vehicle":{"id":"v-1","registration_number":"KA-01-AB-1234","make":"Tata","model":"Ace"},
                    "driver":{"id":"d-1","full_name":"Rahul Sharma","license_number":"KA0120230001234"},
                    "start_date":"2026-08-22","end_date":"2026-09-21","status":"ACTIVE",
                    "notes":null,"created_at":"2026-08-22T21:48:44.827761Z","is_current":true}"""
            )
        )

        val result = assignments.create(
            AssignmentCreateRequest("v-1", "d-1", "2026-08-22", "2026-09-21")
        )

        val data = (result as ApiResult.Success).data
        assertEquals("2026-08-22 → 2026-09-21", data.period)
        assertTrue(data.isCurrent)
    }

    @Test
    fun `an open ended assignment reads as open ended`() = runTest(dispatcher) {
        server.enqueue(
            json(
                201,
                """{"id":"a-2",
                    "vehicle":{"id":"v-1","registration_number":"KA-01","make":"Tata","model":"Ace"},
                    "driver":{"id":"d-1","full_name":"Rahul","license_number":"KA01"},
                    "start_date":"2026-08-22","end_date":null,"status":"ACTIVE",
                    "notes":null,"created_at":"2026-08-22T21:48:44.827761Z","is_current":true}"""
            )
        )

        val result = assignments.create(AssignmentCreateRequest("v-1", "d-1", "2026-08-22"))

        assertEquals("2026-08-22 → open ended", (result as ApiResult.Success).data.period)
    }

    @Test
    fun `suspending a driver with open trips is refused`() = runTest(dispatcher) {
        server.enqueue(
            json(409, """{"error_message":"Driver has 2 scheduled or running trip(s); reassign them first"}""")
        )

        val result = drivers.setStatus("d-1", "SUSPENDED")

        assertTrue((result as ApiResult.Failure).error.isConflict)
        assertTrue(result.error.message.contains("reassign them first"))
    }

    @Test
    fun `performance decodes numeric distances`() = runTest(dispatcher) {
        server.enqueue(
            json(
                200,
                """{"driver_id":"d-1","full_name":"Rahul Sharma","total_trips":2,
                    "completed_trips":1,"cancelled_trips":1,"total_distance_km":346.0,
                    "average_trip_duration_minutes":0,"average_distance_km":346.0,
                    "incidents_reported":0}"""
            )
        )

        val data = (drivers.performance("d-1") as ApiResult.Success).data

        assertEquals(346.0, data.totalDistanceKm, 0.001)
        assertEquals(2, data.totalTrips)
        assertNotNull(data.averageTripDurationMinutes)
    }
}

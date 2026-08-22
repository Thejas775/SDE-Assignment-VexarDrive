package com.thejas.fleetmanagementtask.ui

import com.thejas.fleetmanagementtask.core.ApiResult
import com.thejas.fleetmanagementtask.data.remote.ApiFactory
import com.thejas.fleetmanagementtask.data.remote.FleetApi
import com.thejas.fleetmanagementtask.data.remote.dto.IncidentCreateRequest
import com.thejas.fleetmanagementtask.data.repository.IncidentRepository
import com.thejas.fleetmanagementtask.fake.InMemoryTokenStore
import com.thejas.fleetmanagementtask.ui.incidents.IncidentListViewModel
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
class IncidentTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var server: MockWebServer
    private lateinit var repository: IncidentRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        server = MockWebServer().also { it.start() }
        val retrofit = ApiFactory.create(
            tokens = InMemoryTokenStore().apply { seed() },
            baseUrl = server.url("/api/v1/").toString(),
            onSessionExpired = {},
        )
        repository = IncidentRepository(retrofit.create(FleetApi::class.java), dispatcher)
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    private fun json(code: Int, body: String) = MockResponse()
        .setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    private fun incident(
        status: String = "OPEN",
        severity: String = "HIGH",
        assigned: String = "null",
        notes: String = "null",
    ) = """
        {"id":"i-1",
         "vehicle":{"id":"v-1","registration_number":"KA-01-AB-1234","make":"Tata","model":"Ace"},
         "trip_id":null,"trip_number":null,
         "reported_by":{"id":"u-2","full_name":"Rahul Sharma"},
         "assigned_to":$assigned,
         "title":"Brake noise on descent","description":"Grinding sound","severity":"$severity",
         "status":"$status","reported_at":"2026-08-23T10:00:00Z",
         "resolved_at":null,"resolution_notes":$notes}
    """.trimIndent()

    private fun page(body: String?) = json(
        200,
        """{"items":[${body ?: ""}],"total":${if (body == null) 0 else 1},
            "page":1,"page_size":20,"pages":${if (body == null) 0 else 1}}"""
    )

    @Test
    fun `the manager queue loads`() = runTest(dispatcher) {
        server.enqueue(page(incident()))

        val state = IncidentListViewModel(repository, mineOnly = false)
            .state.first { it.items.isNotEmpty() }

        assertEquals("Brake noise on descent", state.items.first().title)
        assertTrue(state.items.first().isOpen)
        assertEquals("/api/v1/incidents?page=1&page_size=50", server.takeRequest().path)
    }

    @Test
    fun `a driver sees only their own reports`() = runTest(dispatcher) {
        server.enqueue(page(incident()))

        IncidentListViewModel(repository, mineOnly = true).state.first { it.items.isNotEmpty() }

        assertEquals("/api/v1/incidents/my?page=1&page_size=50", server.takeRequest().path)
    }

    @Test
    fun `a status filter reaches the query string`() = runTest(dispatcher) {
        server.enqueue(page(incident()))
        val viewModel = IncidentListViewModel(repository, mineOnly = false)
        viewModel.state.first { it.items.isNotEmpty() }
        server.takeRequest()

        server.enqueue(page(incident(status = "RESOLVED", notes = "\"Pads replaced\"")))
        viewModel.onStatusSelected("RESOLVED")
        viewModel.state.first { it.items.firstOrNull()?.status == "RESOLVED" }

        assertTrue(server.takeRequest().path!!.contains("status=RESOLVED"))
    }

    @Test
    fun `reporting sends the vehicle, severity and trip`() = runTest(dispatcher) {
        server.enqueue(json(201, incident()))

        val result = repository.report(
            IncidentCreateRequest("v-1", "t-9", "Brake noise", "Grinding sound", "HIGH")
        )

        assertTrue(result is ApiResult.Success)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body, body.contains("\"vehicle_id\":\"v-1\""))
        assertTrue(body, body.contains("\"trip_id\":\"t-9\""))
        assertTrue(body, body.contains("\"severity\":\"HIGH\""))
    }

    @Test
    fun `a driver reporting on someone elses vehicle is forbidden`() = runTest(dispatcher) {
        server.enqueue(
            json(403, """{"error_message":"You may only report issues for your assigned vehicle"}""")
        )

        val result = repository.report(
            IncidentCreateRequest("v-2", null, "Brake noise", "Grinding sound", "HIGH")
        )

        val error = (result as ApiResult.Failure).error
        assertTrue(error.isForbidden)
        assertEquals("You may only report issues for your assigned vehicle", error.message)
    }

    @Test
    fun `resolving without notes is refused by the server`() = runTest(dispatcher) {
        server.enqueue(
            json(422, """{"error_message":"resolution_notes is required when resolving an incident"}""")
        )

        val result = repository.setStatus("i-1", "RESOLVED", null)

        assertTrue(result is ApiResult.Failure)
        assertEquals(422, (result as ApiResult.Failure).error.code)
    }

    @Test
    fun `resolving with notes returns the resolved incident`() = runTest(dispatcher) {
        server.enqueue(
            json(200, incident(status = "RESOLVED", notes = "\"Front pads replaced\""))
        )

        val data = (repository.setStatus("i-1", "RESOLVED", "Front pads replaced")
            as ApiResult.Success).data

        assertEquals("RESOLVED", data.status)
        assertEquals("Front pads replaced", data.resolutionNotes)
        assertFalse(data.isOpen)
        assertTrue(server.takeRequest().body.readUtf8().contains("Front pads replaced"))
    }

    @Test
    fun `assigning advances the status and names the assignee`() = runTest(dispatcher) {
        server.enqueue(
            json(
                200,
                incident(status = "IN_PROGRESS", assigned = """{"id":"u-1","full_name":"Priya Nair"}""")
            )
        )

        val data = (repository.assign("i-1", "u-1") as ApiResult.Success).data

        assertEquals("IN_PROGRESS", data.status)
        assertEquals("Priya Nair", data.assignedTo?.fullName)
        assertTrue(server.takeRequest().body.readUtf8().contains("\"assigned_to_id\":\"u-1\""))
    }

    @Test
    fun `reopening a resolved incident is refused`() = runTest(dispatcher) {
        server.enqueue(
            json(409, """{"error_message":"Cannot move a RESOLVED incident to IN_PROGRESS"}""")
        )

        val result = repository.setStatus("i-1", "IN_PROGRESS", null)

        assertTrue((result as ApiResult.Failure).error.isConflict)
    }

    @Test
    fun `a critical incident is flagged`() = runTest(dispatcher) {
        server.enqueue(page(incident(severity = "CRITICAL")))

        val state = IncidentListViewModel(repository, mineOnly = false)
            .state.first { it.items.isNotEmpty() }

        assertTrue(state.items.first().isCritical)
    }

    @Test
    fun `an unassigned incident decodes with a null assignee`() = runTest(dispatcher) {
        server.enqueue(page(incident()))

        val state = IncidentListViewModel(repository, mineOnly = false)
            .state.first { it.items.isNotEmpty() }

        assertNull(state.items.first().assignedTo)
    }

    @Test
    fun `an empty queue is reported as empty`() = runTest(dispatcher) {
        server.enqueue(page(null))

        val state = IncidentListViewModel(repository, mineOnly = false).state.first { !it.isLoading }

        assertTrue(state.isEmpty)
    }
}

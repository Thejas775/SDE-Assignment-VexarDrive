package com.thejas.fleetmanagementtask.ui

import com.thejas.fleetmanagementtask.R
import com.thejas.fleetmanagementtask.data.remote.ApiFactory
import com.thejas.fleetmanagementtask.data.remote.FleetApi
import com.thejas.fleetmanagementtask.data.repository.DashboardRepository
import com.thejas.fleetmanagementtask.fake.InMemoryTokenStore
import com.thejas.fleetmanagementtask.ui.dashboard.DashboardViewModel
import com.thejas.fleetmanagementtask.ui.dashboard.toStats
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var server: MockWebServer
    private lateinit var repository: DashboardRepository

    private val payload = """
        {"vehicles":{"total":13,"available":7,"on_trip":4,"in_maintenance":2,"inactive":0},
         "trips":{"active":4,"scheduled_today":0,"completed_today":3},
         "drivers_active":8,"distance_today_km":"1038.00","maintenance_due":13,
         "open_incidents":6,
         "expiring_documents":{"insurance":2,"registration":0,"driver_license":1},
         "recent_incidents":[{"id":"i-1","title":"Brake noise","severity":"CRITICAL",
           "status":"OPEN","registration_number":"KA-01-AB-1234","reported_at":"2026-08-22"}]}
    """.trimIndent()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        server = MockWebServer().also { it.start() }
        val retrofit = ApiFactory.create(
            tokens = InMemoryTokenStore().apply { seed() },
            baseUrl = server.url("/api/v1/").toString(),
            onSessionExpired = {},
        )
        repository = DashboardRepository(retrofit.create(FleetApi::class.java), dispatcher)
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

    @Test
    fun `it loads the dashboard on creation`() = runTest(dispatcher) {
        server.enqueue(json(200, payload))

        val viewModel = DashboardViewModel(repository)
        val state = viewModel.state.first { it.data != null }

        assertEquals(13, state.data!!.vehicles.total)
        assertEquals("1038.00", state.data!!.distanceTodayKm)
        assertFalse(state.isLoading)
        assertEquals("/api/v1/dashboard", server.takeRequest().path)
    }

    @Test
    fun `distance keeps its exact decimal string`() = runTest(dispatcher) {
        server.enqueue(json(200, payload))

        val state = DashboardViewModel(repository).state.first { it.data != null }

        // Not a Double: 1038.00 must not become 1038.0 on the way to the tile.
        assertEquals("1038.00", state.data!!.distanceTodayKm)
    }

    @Test
    fun `every section 12 metric becomes a tile`() = runTest(dispatcher) {
        server.enqueue(json(200, payload))

        val data = DashboardViewModel(repository).state.first { it.data != null }.data!!
        val stats = data.toStats()

        assertEquals(11, stats.size)
        fun valueOf(res: Int) = stats.first { it.labelRes == res }.value
        assertEquals("13", valueOf(R.string.stat_total_vehicles))
        assertEquals("7", valueOf(R.string.stat_available))
        assertEquals("4", valueOf(R.string.stat_on_trip))
        assertEquals("2", valueOf(R.string.stat_maintenance))
        assertEquals("0", valueOf(R.string.stat_inactive))
        assertEquals("4", valueOf(R.string.stat_active_trips))
        assertEquals("1038.00", valueOf(R.string.stat_distance_today))
        assertEquals("13", valueOf(R.string.stat_maintenance_due))
        assertEquals("3", valueOf(R.string.stat_expiring_docs))
    }

    @Test
    fun `expiring documents are summed across all three kinds`() = runTest(dispatcher) {
        server.enqueue(json(200, payload))

        val data = DashboardViewModel(repository).state.first { it.data != null }.data!!

        assertEquals(3, data.expiringDocuments.total)
    }

    @Test
    fun `a failure surfaces a message and no data`() = runTest(dispatcher) {
        server.enqueue(json(403, """{"error_message":"This action requires role: FLEET_MANAGER"}"""))

        val state = DashboardViewModel(repository).state.first { it.errorMessage != null }

        assertEquals("This action requires role: FLEET_MANAGER", state.errorMessage)
        assertNull(state.data)
        assertFalse(state.isLoading)
    }

    @Test
    fun `dismissing the message clears it`() = runTest(dispatcher) {
        server.enqueue(json(500, ""))

        val viewModel = DashboardViewModel(repository)
        viewModel.state.first { it.errorMessage != null }
        viewModel.messageShown()

        assertNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun `refresh keeps the previous numbers on screen`() = runTest(dispatcher) {
        server.enqueue(json(200, payload))
        val viewModel = DashboardViewModel(repository)
        viewModel.state.first { it.data != null }

        server.enqueue(json(200, payload.replace("\"total\":13", "\"total\":14")))
        viewModel.refresh()

        // The old snapshot stays visible while the new one is in flight.
        assertNotNull(viewModel.state.value.data)
        assertFalse(viewModel.state.value.isLoading)

        val refreshed = viewModel.state.first { it.data?.vehicles?.total == 14 }
        assertFalse(refreshed.isRefreshing)
    }

    @Test
    fun `an empty incident list is reported as empty`() = runTest(dispatcher) {
        val noIncidents = """
            {"vehicles":{"total":1,"available":1,"on_trip":0,"in_maintenance":0,"inactive":0},
             "trips":{"active":0,"scheduled_today":0,"completed_today":0},
             "drivers_active":0,"distance_today_km":"0.00","maintenance_due":0,
             "open_incidents":0,
             "expiring_documents":{"insurance":0,"registration":0,"driver_license":0},
             "recent_incidents":[]}
        """.trimIndent()
        server.enqueue(json(200, noIncidents))

        val state = DashboardViewModel(repository).state.first { it.data != null }

        assertTrue(state.showEmptyIncidents)
        assertEquals(0, state.data!!.recentIncidents.size)
    }
}

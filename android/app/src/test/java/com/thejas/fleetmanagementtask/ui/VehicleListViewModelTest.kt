package com.thejas.fleetmanagementtask.ui

import com.thejas.fleetmanagementtask.data.remote.ApiFactory
import com.thejas.fleetmanagementtask.data.remote.FleetApi
import com.thejas.fleetmanagementtask.data.repository.VehicleRepository
import com.thejas.fleetmanagementtask.fake.InMemoryTokenStore
import com.thejas.fleetmanagementtask.ui.vehicles.VehicleListViewModel
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
class VehicleListViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var server: MockWebServer
    private lateinit var repository: VehicleRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        server = MockWebServer().also { it.start() }
        val retrofit = ApiFactory.create(
            tokens = InMemoryTokenStore().apply { seed() },
            baseUrl = server.url("/api/v1/").toString(),
            onSessionExpired = {},
        )
        repository = VehicleRepository(retrofit.create(FleetApi::class.java), dispatcher)
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    private fun vehicle(id: String, reg: String) = """
        {"id":"$id","registration_number":"$reg","vehicle_type":"TRUCK","make":"Tata",
         "model":"Ace","year":2022,"fuel_type":"DIESEL","current_mileage":48596,
         "status":"AVAILABLE","insurance_expiry":"2029-09-05",
         "registration_expiry":"2030-01-01","created_at":"2026-08-22T21:48:43.682640Z",
         "updated_at":"2026-08-22T21:48:45.058944Z","insurance_expiring_soon":false,
         "registration_expiring_soon":false}
    """.trimIndent()

    private fun page(items: String, total: Int, page: Int, pages: Int) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody("""{"items":[$items],"total":$total,"page":$page,"page_size":20,"pages":$pages}""")

    @Test
    fun `it loads the first page on creation`() = runTest(dispatcher) {
        server.enqueue(page(vehicle("v-1", "KA-01-AB-1234"), total = 13, page = 1, pages = 2))

        val state = VehicleListViewModel(repository).state.first { it.items.isNotEmpty() }

        assertEquals(1, state.items.size)
        assertEquals(13, state.total)
        assertTrue(state.hasMore)
        val request = server.takeRequest()
        assertEquals("/api/v1/vehicles?page=1&page_size=20", request.path)
    }

    @Test
    fun `the next page is appended, not replaced`() = runTest(dispatcher) {
        server.enqueue(page(vehicle("v-1", "KA-01"), total = 2, page = 1, pages = 2))
        val viewModel = VehicleListViewModel(repository)
        viewModel.state.first { it.items.isNotEmpty() }

        server.enqueue(page(vehicle("v-2", "KA-02"), total = 2, page = 2, pages = 2))
        viewModel.loadNextPage()
        val state = viewModel.state.first { it.items.size == 2 }

        assertEquals(listOf("KA-01", "KA-02"), state.items.map { it.registrationNumber })
        assertFalse(state.hasMore)
    }

    @Test
    fun `it stops requesting once the last page is loaded`() = runTest(dispatcher) {
        server.enqueue(page(vehicle("v-1", "KA-01"), total = 1, page = 1, pages = 1))
        val viewModel = VehicleListViewModel(repository)
        viewModel.state.first { it.items.isNotEmpty() }
        val before = server.requestCount

        viewModel.loadNextPage()
        viewModel.loadNextPage()

        assertEquals(before, server.requestCount)
    }

    @Test
    fun `a status filter is sent as a query parameter and resets to page 1`() = runTest(dispatcher) {
        server.enqueue(page(vehicle("v-1", "KA-01"), total = 1, page = 1, pages = 1))
        val viewModel = VehicleListViewModel(repository)
        viewModel.state.first { it.items.isNotEmpty() }
        server.takeRequest()

        server.enqueue(page(vehicle("v-9", "KA-09"), total = 1, page = 1, pages = 1))
        viewModel.onStatusSelected("IN_MAINTENANCE")
        viewModel.state.first { it.items.firstOrNull()?.registrationNumber == "KA-09" }

        val path = server.takeRequest().path!!
        assertTrue(path, path.contains("status=IN_MAINTENANCE"))
        assertTrue(path, path.contains("page=1"))
    }

    @Test
    fun `filtering replaces the previous results instead of appending`() = runTest(dispatcher) {
        server.enqueue(page(vehicle("v-1", "KA-01"), total = 1, page = 1, pages = 1))
        val viewModel = VehicleListViewModel(repository)
        viewModel.state.first { it.items.isNotEmpty() }

        server.enqueue(page(vehicle("v-2", "KA-02"), total = 1, page = 1, pages = 1))
        viewModel.onStatusSelected("INACTIVE")
        val state = viewModel.state.first { it.items.firstOrNull()?.registrationNumber == "KA-02" }

        assertEquals(1, state.items.size)
    }

    @Test
    fun `search is debounced so typing does not fire a request per keystroke`() =
        runTest(dispatcher) {
            server.enqueue(page(vehicle("v-1", "KA-01"), total = 1, page = 1, pages = 1))
            val viewModel = VehicleListViewModel(repository)
            viewModel.state.first { it.items.isNotEmpty() }
            server.takeRequest()

            server.enqueue(page(vehicle("v-3", "KA-03"), total = 1, page = 1, pages = 1))
            "tata".forEach { viewModel.onSearchChanged(it.toString()) }
            viewModel.state.first { it.items.firstOrNull()?.registrationNumber == "KA-03" }

            // Four keystrokes, one request.
            assertEquals(1, server.requestCount - 1)
            assertTrue(server.takeRequest().path!!.contains("search=a"))
        }

    @Test
    fun `an empty result is reported as empty`() = runTest(dispatcher) {
        server.enqueue(page("", total = 0, page = 1, pages = 0))

        val state = VehicleListViewModel(repository).state.first { !it.isLoading }

        assertTrue(state.isEmpty)
        assertEquals(0, state.total)
    }

    @Test
    fun `a failure surfaces the server message`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error_message":"This action requires role: FLEET_MANAGER"}""")
        )

        val state = VehicleListViewModel(repository).state.first { it.errorMessage != null }

        assertEquals("This action requires role: FLEET_MANAGER", state.errorMessage)
        assertTrue(state.items.isEmpty())
    }

    @Test
    fun `dismissing the message clears it`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(500).setBody(""))
        val viewModel = VehicleListViewModel(repository)
        viewModel.state.first { it.errorMessage != null }

        viewModel.messageShown()

        assertNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun `replace swaps one row without refetching`() = runTest(dispatcher) {
        server.enqueue(page(vehicle("v-1", "KA-01"), total = 1, page = 1, pages = 1))
        val viewModel = VehicleListViewModel(repository)
        val loaded = viewModel.state.first { it.items.isNotEmpty() }
        val before = server.requestCount

        viewModel.replace(loaded.items.first().copy(status = "IN_MAINTENANCE"))

        assertEquals("IN_MAINTENANCE", viewModel.state.value.items.first().status)
        assertEquals(before, server.requestCount)
    }
}

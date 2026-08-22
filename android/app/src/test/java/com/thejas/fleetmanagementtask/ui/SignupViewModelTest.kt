package com.thejas.fleetmanagementtask.ui

import com.thejas.fleetmanagementtask.data.remote.ApiFactory
import com.thejas.fleetmanagementtask.data.remote.AuthApi
import com.thejas.fleetmanagementtask.data.repository.AuthRepository
import com.thejas.fleetmanagementtask.fake.InMemoryTokenStore
import com.thejas.fleetmanagementtask.ui.auth.ROLE_DRIVER
import com.thejas.fleetmanagementtask.ui.auth.ROLE_FLEET_MANAGER
import com.thejas.fleetmanagementtask.ui.auth.SignupEvent
import com.thejas.fleetmanagementtask.ui.auth.SignupStrings
import com.thejas.fleetmanagementtask.ui.auth.SignupViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
class SignupViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var server: MockWebServer
    private lateinit var tokens: InMemoryTokenStore
    private lateinit var viewModel: SignupViewModel

    private val strings = SignupStrings(
        nameRequired = "Enter your name",
        emailRequired = "Enter your email",
        emailInvalid = "That does not look like an email address",
        passwordShort = "At least 8 characters",
        passwordsDiffer = "Passwords do not match",
        phoneRequired = "Enter your phone number",
        licenceRequired = "Required",
        expiryRequired = "Required",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        server = MockWebServer().also { it.start() }
        tokens = InMemoryTokenStore()
        val retrofit = ApiFactory.create(
            tokens = tokens,
            baseUrl = server.url("/api/v1/").toString(),
            onSessionExpired = {},
        )
        viewModel = SignupViewModel(
            AuthRepository(retrofit.create(AuthApi::class.java), tokens, dispatcher)
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    private fun json(code: Int, body: String) = MockResponse()
        .setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    private fun user(role: String) = """
        {"id":"u-1","email":"rahul@fleet.in","full_name":"Rahul Sharma",
         "phone_number":"+919876543210","role":"$role","is_active":true}
    """.trimIndent()

    private fun tokenPayload(role: String) = """
        {"access_token":"a","refresh_token":"r","token_type":"bearer","expires_in":1800,
         "user":${user(role)}}
    """.trimIndent()

    private fun fillDriver() = with(viewModel) {
        onRoleChanged(ROLE_DRIVER)
        onNameChanged("Rahul Sharma")
        onEmailChanged("Rahul@Fleet.in")
        onPasswordChanged("pass-word-1")
        onConfirmChanged("pass-word-1")
        onPhoneChanged("+919876543210")
        onLicenceChanged(" ka01 2026 0005555 ")
        onExpiryChanged("2029-03-14")
    }

    @Test
    fun `driver is the default role and shows the licence fields`() {
        assertTrue(viewModel.state.value.isDriver)
    }

    @Test
    fun `choosing manager hides the driver fields`() {
        viewModel.onRoleChanged(ROLE_FLEET_MANAGER)
        assertFalse(viewModel.state.value.isDriver)
    }

    @Test
    fun `an empty form reports every required field and sends nothing`() = runTest(dispatcher) {
        viewModel.submit(strings)
        advanceUntilIdle()

        val errors = viewModel.state.value.errors
        assertEquals("Enter your name", errors.name)
        assertEquals("Enter your email", errors.email)
        assertEquals("At least 8 characters", errors.password)
        assertEquals("Enter your phone number", errors.phone)
        assertEquals("Required", errors.licenceNumber)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a manager is not asked for licence details`() = runTest(dispatcher) {
        viewModel.onRoleChanged(ROLE_FLEET_MANAGER)
        viewModel.onNameChanged("Priya Nair")
        viewModel.onEmailChanged("ops@fleet.in")
        viewModel.onPasswordChanged("pass-word-1")
        viewModel.onConfirmChanged("pass-word-1")

        viewModel.submit(strings)
        advanceUntilIdle()

        val errors = viewModel.state.value.errors
        assertNull(errors.phone)
        assertNull(errors.licenceNumber)
        assertNull(errors.licenceExpiry)
    }

    @Test
    fun `mismatched passwords are caught before the request`() = runTest(dispatcher) {
        fillDriver()
        viewModel.onConfirmChanged("something-else")

        viewModel.submit(strings)
        advanceUntilIdle()

        assertEquals("Passwords do not match", viewModel.state.value.errors.confirmPassword)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a short password does not also report a mismatch`() = runTest(dispatcher) {
        fillDriver()
        viewModel.onPasswordChanged("short")
        viewModel.onConfirmChanged("short")

        viewModel.submit(strings)
        advanceUntilIdle()

        assertEquals("At least 8 characters", viewModel.state.value.errors.password)
        assertNull(viewModel.state.value.errors.confirmPassword)
    }

    @Test
    fun `a malformed email is caught before the request`() = runTest(dispatcher) {
        fillDriver()
        viewModel.onEmailChanged("not-an-email")

        viewModel.submit(strings)
        advanceUntilIdle()

        assertEquals("That does not look like an email address", viewModel.state.value.errors.email)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a driver signup sends the licence details, normalised`() = runTest(dispatcher) {
        server.enqueue(json(201, user("DRIVER")))
        server.enqueue(json(200, tokenPayload("DRIVER")))
        fillDriver()

        viewModel.submit(strings)
        viewModel.events.first()

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body, body.contains("\"email\":\"rahul@fleet.in\""))
        assertTrue(body, body.contains("\"role\":\"DRIVER\""))
        assertTrue(body, body.contains("\"license_number\":\"KA0120260005555\""))
        assertTrue(body, body.contains("\"license_expiry\":\"2029-03-14\""))
    }

    @Test
    fun `a manager signup omits the licence fields entirely`() = runTest(dispatcher) {
        server.enqueue(json(201, user("FLEET_MANAGER")))
        server.enqueue(json(200, tokenPayload("FLEET_MANAGER")))
        viewModel.onRoleChanged(ROLE_FLEET_MANAGER)
        viewModel.onNameChanged("Priya Nair")
        viewModel.onEmailChanged("ops@fleet.in")
        viewModel.onPasswordChanged("pass-word-1")
        viewModel.onConfirmChanged("pass-word-1")

        viewModel.submit(strings)
        viewModel.events.first()

        val body = server.takeRequest().body.readUtf8()
        assertFalse(body, body.contains("license_number"))
        assertFalse(body, body.contains("license_expiry"))
    }

    @Test
    fun `signing up signs the user in and stores the session`() = runTest(dispatcher) {
        server.enqueue(json(201, user("DRIVER")))
        server.enqueue(json(200, tokenPayload("DRIVER")))
        fillDriver()

        viewModel.submit(strings)
        val event = viewModel.events.first()

        assertTrue(event is SignupEvent.Registered)
        assertEquals("DRIVER", (event as SignupEvent.Registered).role)
        assertEquals("a", tokens.accessToken)
        assertEquals("/api/v1/auth/register", server.takeRequest().path)
        assertEquals("/api/v1/auth/login", server.takeRequest().path)
    }

    @Test
    fun `a duplicate email becomes a message, not a field error`() = runTest(dispatcher) {
        server.enqueue(json(409, """{"error_message":"An account with this email already exists"}"""))
        fillDriver()

        viewModel.submit(strings)
        val event = viewModel.events.first()

        assertEquals(
            "An account with this email already exists",
            (event as SignupEvent.ShowMessage).message,
        )
        assertNull(tokens.accessToken)
    }

    @Test
    fun `a server field error lands on the matching input`() = runTest(dispatcher) {
        server.enqueue(
            json(
                422,
                """{"detail":[{"loc":["body","license_number"],
                   "msg":"String should have at least 4 characters"}]}"""
            )
        )
        fillDriver()

        viewModel.submit(strings)
        val errors = viewModel.state.first { it.errors.licenceNumber != null }.errors

        assertEquals("String should have at least 4 characters", errors.licenceNumber)
    }

    @Test
    fun `tapping create twice only registers once`() = runTest(dispatcher) {
        server.enqueue(json(201, user("DRIVER")))
        server.enqueue(json(200, tokenPayload("DRIVER")))
        fillDriver()

        viewModel.submit(strings)
        viewModel.submit(strings)
        viewModel.events.first()

        // register + login, not two registers
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `typing clears the previous errors`() = runTest(dispatcher) {
        viewModel.submit(strings)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.errors.any)

        viewModel.onNameChanged("R")

        assertFalse(viewModel.state.value.errors.any)
    }
}

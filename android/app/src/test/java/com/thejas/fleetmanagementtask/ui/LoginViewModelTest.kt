package com.thejas.fleetmanagementtask.ui

import com.thejas.fleetmanagementtask.data.remote.ApiFactory
import com.thejas.fleetmanagementtask.data.remote.AuthApi
import com.thejas.fleetmanagementtask.data.repository.AuthRepository
import com.thejas.fleetmanagementtask.fake.InMemoryTokenStore
import com.thejas.fleetmanagementtask.ui.auth.LoginEvent
import com.thejas.fleetmanagementtask.ui.auth.LoginViewModel
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
class LoginViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var server: MockWebServer
    private lateinit var tokens: InMemoryTokenStore
    private lateinit var viewModel: LoginViewModel

    private val emailRequired = "Enter your email"
    private val emailInvalid = "That does not look like an email address"
    private val passwordRequired = "Enter your password"

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
        viewModel = LoginViewModel(
            AuthRepository(retrofit.create(AuthApi::class.java), tokens, dispatcher)
        )
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

    private val tokenPayload = """
        {"access_token":"a","refresh_token":"r","token_type":"bearer","expires_in":1800,
         "user":{"id":"u-1","email":"ops@fleet.in","full_name":"Priya Nair",
                 "role":"FLEET_MANAGER","is_active":true}}
    """.trimIndent()

    private fun submit() = viewModel.submit(emailRequired, emailInvalid, passwordRequired)

    @Test
    fun `submit button stays disabled until both fields are filled`() {
        assertFalse(viewModel.state.value.canSubmit)
        viewModel.onEmailChanged("ops@fleet.in")
        assertFalse(viewModel.state.value.canSubmit)
        viewModel.onPasswordChanged("pass-word-1")
        assertTrue(viewModel.state.value.canSubmit)
    }

    @Test
    fun `an empty form is rejected without calling the server`() = runTest(dispatcher) {
        submit()
        advanceUntilIdle()

        assertEquals(emailRequired, viewModel.state.value.emailError)
        assertEquals(passwordRequired, viewModel.state.value.passwordError)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a malformed email is caught before the request`() = runTest(dispatcher) {
        viewModel.onEmailChanged("not-an-email")
        viewModel.onPasswordChanged("pass-word-1")
        submit()
        advanceUntilIdle()

        assertEquals(emailInvalid, viewModel.state.value.emailError)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `typing clears the previous error`() {
        viewModel.onEmailChanged("")
        submit()
        assertEquals(emailRequired, viewModel.state.value.emailError)

        viewModel.onEmailChanged("o")
        assertNull(viewModel.state.value.emailError)
    }

    @Test
    fun `a successful login emits LoggedIn and stores the session`() = runTest(dispatcher) {
        server.enqueue(json(200, tokenPayload))
        viewModel.onEmailChanged("ops@fleet.in")
        viewModel.onPasswordChanged("pass-word-1")

        submit()
        advanceUntilIdle()

        val event = viewModel.events.first()
        assertTrue(event is LoginEvent.LoggedIn)
        assertEquals("Priya Nair", (event as LoginEvent.LoggedIn).fullName)
        assertEquals("FLEET_MANAGER", event.role)
        assertEquals("a", tokens.accessToken)
        assertFalse(viewModel.state.value.isSubmitting)
    }

    @Test
    fun `wrong credentials become a message, not a field error`() = runTest(dispatcher) {
        server.enqueue(json(401, """{"error_message":"Incorrect email or password"}"""))
        viewModel.onEmailChanged("ops@fleet.in")
        viewModel.onPasswordChanged("wrong-password")

        submit()
        advanceUntilIdle()

        val event = viewModel.events.first()
        assertEquals(
            "Incorrect email or password",
            (event as LoginEvent.ShowMessage).message,
        )
        assertNull(viewModel.state.value.emailError)
        assertNull(viewModel.state.value.passwordError)
    }

    @Test
    fun `a server field error lands on the matching input`() = runTest(dispatcher) {
        server.enqueue(
            json(
                422,
                """{"detail":[{"loc":["body","password"],
                   "msg":"String should have at least 8 characters"}]}"""
            )
        )
        viewModel.onEmailChanged("ops@fleet.in")
        viewModel.onPasswordChanged("short")

        submit()
        // Retrofit resumes on OkHttp's callback thread, so wait for the real
        // state change rather than advancing the test scheduler.
        val state = viewModel.state.first { it.passwordError != null }

        assertEquals("String should have at least 8 characters", state.passwordError)
        assertNull(state.emailError)
    }

    @Test
    fun `an unreachable server surfaces a network message`() = runTest(dispatcher) {
        server.shutdown()
        viewModel.onEmailChanged("ops@fleet.in")
        viewModel.onPasswordChanged("pass-word-1")

        submit()
        advanceUntilIdle()

        val event = viewModel.events.first()
        assertEquals("No connection to the server", (event as LoginEvent.ShowMessage).message)
        assertFalse(viewModel.state.value.isSubmitting)
    }

    @Test
    fun `tapping submit twice only sends one request`() = runTest(dispatcher) {
        server.enqueue(json(200, tokenPayload))
        viewModel.onEmailChanged("ops@fleet.in")
        viewModel.onPasswordChanged("pass-word-1")

        submit()
        submit()
        viewModel.events.first()

        assertEquals(1, server.requestCount)
    }

    @Test
    fun `already signed in is reported from the token store`() {
        assertFalse(viewModel.isAlreadySignedIn)
        tokens.seed()
        assertTrue(viewModel.isAlreadySignedIn)
    }
}

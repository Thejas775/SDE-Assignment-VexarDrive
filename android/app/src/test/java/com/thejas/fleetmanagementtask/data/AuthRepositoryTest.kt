package com.thejas.fleetmanagementtask.data

import com.thejas.fleetmanagementtask.core.ApiError
import com.thejas.fleetmanagementtask.core.ApiResult
import com.thejas.fleetmanagementtask.data.remote.ApiFactory
import com.thejas.fleetmanagementtask.data.remote.AuthApi
import com.thejas.fleetmanagementtask.data.repository.AuthRepository
import com.thejas.fleetmanagementtask.fake.InMemoryTokenStore
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AuthRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var tokens: InMemoryTokenStore
    private lateinit var repository: AuthRepository
    private var sessionExpiredCount = 0

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        tokens = InMemoryTokenStore()
        sessionExpiredCount = 0
        val retrofit = ApiFactory.create(
            tokens = tokens,
            baseUrl = server.url("/api/v1/").toString(),
            onSessionExpired = { sessionExpiredCount++ },
        )
        repository = AuthRepository(retrofit.create(AuthApi::class.java), tokens)
    }

    @After
    fun tearDown() = server.shutdown()

    private fun json(code: Int, body: String) = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun tokenPayload(access: String, refresh: String) = """
        {"access_token":"$access","refresh_token":"$refresh","token_type":"bearer",
         "expires_in":1800,
         "user":{"id":"u-1","email":"ops@fleet.in","full_name":"Priya Nair",
                 "phone_number":null,"role":"FLEET_MANAGER","is_active":true}}
    """.trimIndent()

    @Test
    fun `login stores the session`() = runTest {
        server.enqueue(json(200, tokenPayload("access-1", "refresh-1")))

        val result = repository.login("  OPS@Fleet.in ", "pass-word-1")

        assertTrue(result is ApiResult.Success)
        assertEquals("Priya Nair", (result as ApiResult.Success).data.fullName)
        assertEquals("access-1", tokens.accessToken)
        assertEquals("refresh-1", tokens.refreshToken)
        assertEquals("FLEET_MANAGER", tokens.role)
    }

    @Test
    fun `login lowercases and trims the email before sending`() = runTest {
        server.enqueue(json(200, tokenPayload("a", "r")))

        repository.login("  OPS@Fleet.in ", "pass-word-1")

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body, body.contains("\"email\":\"ops@fleet.in\""))
    }

    @Test
    fun `a business failure surfaces the servers message`() = runTest {
        server.enqueue(json(401, """{"error_message":"Incorrect email or password"}"""))

        val result = repository.login("ops@fleet.in", "wrong")

        val error = (result as ApiResult.Failure).error
        assertEquals(401, error.code)
        assertEquals("Incorrect email or password", error.message)
        assertTrue(error.isUnauthorized)
        assertNull(tokens.accessToken)
    }

    @Test
    fun `validation errors are mapped per field`() = runTest {
        server.enqueue(
            json(
                422,
                """{"detail":[{"type":"string_too_short","loc":["body","password"],
                   "msg":"String should have at least 8 characters"}]}"""
            )
        )

        val result = repository.login("ops@fleet.in", "short")

        val error = (result as ApiResult.Failure).error
        assertEquals(422, error.code)
        assertEquals(
            "String should have at least 8 characters",
            error.fieldErrors["password"],
        )
    }

    @Test
    fun `a dropped connection becomes a network error`() = runTest {
        server.shutdown()

        val result = repository.login("ops@fleet.in", "pass-word-1")

        assertTrue((result as ApiResult.Failure).error.isNetwork)
        assertEquals(ApiError.CODE_NETWORK, result.error.code)
    }

    @Test
    fun `authenticated calls carry the bearer token`() = runTest {
        tokens.seed(access = "access-1")
        server.enqueue(
            json(
                200,
                """{"id":"u-1","email":"ops@fleet.in","full_name":"Priya Nair",
                    "role":"FLEET_MANAGER","is_active":true}"""
            )
        )

        repository.me()

        assertEquals("Bearer access-1", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `login does not send a stale bearer token`() = runTest {
        tokens.seed(access = "stale")
        server.enqueue(json(200, tokenPayload("fresh", "fresh-refresh")))

        repository.login("ops@fleet.in", "pass-word-1")

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `an expired access token is refreshed and the call replayed`() = runTest {
        tokens.seed(access = "expired", refresh = "refresh-1")
        server.enqueue(json(401, """{"error_message":"Token has expired"}"""))
        server.enqueue(json(200, tokenPayload("access-2", "refresh-2")))
        server.enqueue(
            json(
                200,
                """{"id":"u-1","email":"ops@fleet.in","full_name":"Priya Nair",
                    "role":"FLEET_MANAGER","is_active":true}"""
            )
        )

        val result = repository.me()

        assertTrue(result is ApiResult.Success)
        assertEquals("access-2", tokens.accessToken)
        assertEquals("refresh-2", tokens.refreshToken)

        assertEquals("Bearer expired", server.takeRequest().getHeader("Authorization"))
        assertEquals("/api/v1/auth/refresh", server.takeRequest().path)
        assertEquals("Bearer access-2", server.takeRequest().getHeader("Authorization"))
        assertEquals(0, sessionExpiredCount)
    }

    @Test
    fun `a rejected refresh clears the session and reports expiry`() = runTest {
        tokens.seed(access = "expired", refresh = "revoked")
        server.enqueue(json(401, """{"error_message":"Token has expired"}"""))
        server.enqueue(json(401, """{"error_message":"Refresh token is no longer valid"}"""))

        val result = repository.me()

        assertTrue(result is ApiResult.Failure)
        assertNull(tokens.accessToken)
        assertTrue(tokens.cleared)
        assertEquals(1, sessionExpiredCount)
    }

    @Test
    fun `logout clears the session even when the server is unreachable`() = runTest {
        tokens.seed()
        server.shutdown()

        val result = repository.logout()

        assertTrue(result is ApiResult.Failure)
        assertNull(tokens.accessToken)
        assertTrue(tokens.cleared)
    }

    @Test
    fun `logout sends the refresh token`() = runTest {
        tokens.seed(refresh = "refresh-1")
        server.enqueue(json(200, """{"message":"Logged out"}"""))

        repository.logout()

        val request = server.takeRequest()
        assertEquals("/api/v1/auth/logout", request.path)
        assertTrue(request.body.readUtf8().contains("refresh-1"))
    }

    @Test
    fun `unknown response fields do not break parsing`() = runTest {
        server.enqueue(
            json(
                200,
                """{"access_token":"a","refresh_token":"r","token_type":"bearer",
                    "expires_in":1800,"brand_new_field":"ignored",
                    "user":{"id":"u-1","email":"ops@fleet.in","full_name":"Priya Nair",
                            "role":"FLEET_MANAGER","is_active":true,"future":"also ignored"}}"""
            )
        )

        val result = repository.login("ops@fleet.in", "pass-word-1")

        assertNotNull((result as ApiResult.Success).data)
        assertEquals("ops@fleet.in", result.data.email)
    }
}

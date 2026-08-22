package com.thejas.fleetmanagementtask.data.remote

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.thejas.fleetmanagementtask.BuildConfig
import com.thejas.fleetmanagementtask.data.local.TokenStore
import com.thejas.fleetmanagementtask.data.remote.dto.RefreshRequest
import com.thejas.fleetmanagementtask.data.remote.dto.TokenDto
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object ApiFactory {

    val json = Json {
        ignoreUnknownKeys = true   // the API may add fields without breaking the app
        explicitNulls = false
        coerceInputValues = true
    }

    fun create(
        tokens: TokenStore,
        baseUrl: String = BuildConfig.BASE_URL,
        onSessionExpired: () -> Unit,
    ): Retrofit {
        val bare = bareClient()

        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokens))
            .addInterceptor(logging())
            .authenticator(
                TokenAuthenticator(
                    tokens = tokens,
                    refresh = { token -> refreshBlocking(bare, baseUrl, token) },
                    onSessionExpired = onSessionExpired,
                )
            )
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    /** No authenticator here, or a failing refresh would recurse forever. */
    private fun bareClient() = OkHttpClient.Builder()
        .addInterceptor(logging())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun refreshBlocking(
        client: OkHttpClient,
        baseUrl: String,
        refreshToken: String,
    ): RefreshOutcome {
        // kotlinx-serialization, not org.json: the latter is an Android
        // framework class and is stubbed out in JVM unit tests.
        val body = json.encodeToString(RefreshRequest.serializer(), RefreshRequest(refreshToken))
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(baseUrl + "auth/refresh")
            .post(body)
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                val payload = response.body?.string()
                if (!response.isSuccessful || payload == null) return RefreshOutcome.Failed
                val token = json.decodeFromString(TokenDto.serializer(), payload)
                RefreshOutcome.Success(token.accessToken, token.refreshToken)
            }
        }.getOrElse { RefreshOutcome.Failed }
    }

    private fun logging() = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
        redactHeader("Authorization")
    }
}

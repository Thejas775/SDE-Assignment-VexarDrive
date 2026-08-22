package com.thejas.fleetmanagementtask.data.remote

import com.thejas.fleetmanagementtask.data.local.TokenStore
import okhttp3.Interceptor
import okhttp3.Response

/** Attaches the access token to everything except the endpoints that mint one. */
class AuthInterceptor(private val tokens: TokenStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = tokens.accessToken

        if (token == null || isPublic(request.url.encodedPath)) {
            return chain.proceed(request)
        }
        return chain.proceed(
            request.newBuilder().header("Authorization", "Bearer $token").build()
        )
    }

    private fun isPublic(path: String) = PUBLIC_PATHS.any { path.endsWith(it) }

    private companion object {
        val PUBLIC_PATHS = listOf(
            "auth/login",
            "auth/register",
            "auth/refresh",
            "auth/forgot-password",
            "auth/reset-password",
        )
    }
}

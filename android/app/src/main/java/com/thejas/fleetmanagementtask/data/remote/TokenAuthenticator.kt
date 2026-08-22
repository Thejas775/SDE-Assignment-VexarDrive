package com.thejas.fleetmanagementtask.data.remote

import com.thejas.fleetmanagementtask.data.local.TokenStore
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Refreshes an expired access token and replays the failed request, so a
 * 30-minute expiry never surfaces in the UI.
 *
 * OkHttp calls this only after a 401. It runs on a background thread and must
 * block, which is why the refresh call is issued through a plain synchronous
 * client rather than the suspending Retrofit interface.
 */
class TokenAuthenticator(
    private val tokens: TokenStore,
    private val refresh: (String) -> RefreshOutcome,
    private val onSessionExpired: () -> Unit,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.header("Authorization") == null) return null
        if (responseCount(response) >= MAX_ATTEMPTS) return giveUp()

        synchronized(this) {
            val current = tokens.accessToken
            val attempted = response.request.header("Authorization")?.removePrefix("Bearer ")

            // Another thread may already have refreshed while this one waited.
            if (current != null && current != attempted) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $current")
                    .build()
            }

            val refreshToken = tokens.refreshToken ?: return giveUp()
            return when (val outcome = refresh(refreshToken)) {
                is RefreshOutcome.Success -> {
                    tokens.updateTokens(outcome.accessToken, outcome.refreshToken)
                    response.request.newBuilder()
                        .header("Authorization", "Bearer ${outcome.accessToken}")
                        .build()
                }
                RefreshOutcome.Failed -> giveUp()
            }
        }
    }

    private fun giveUp(): Request? {
        tokens.clear()
        onSessionExpired()
        return null
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private companion object {
        const val MAX_ATTEMPTS = 2
    }
}

sealed interface RefreshOutcome {
    data class Success(val accessToken: String, val refreshToken: String) : RefreshOutcome
    data object Failed : RefreshOutcome
}

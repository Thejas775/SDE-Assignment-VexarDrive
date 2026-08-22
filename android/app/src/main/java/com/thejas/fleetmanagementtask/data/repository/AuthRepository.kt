package com.thejas.fleetmanagementtask.data.repository

import com.thejas.fleetmanagementtask.core.ApiResult
import com.thejas.fleetmanagementtask.data.local.TokenStore
import com.thejas.fleetmanagementtask.data.remote.AuthApi
import com.thejas.fleetmanagementtask.data.remote.dto.ChangePasswordRequest
import com.thejas.fleetmanagementtask.data.remote.dto.ForgotPasswordDto
import com.thejas.fleetmanagementtask.data.remote.dto.ForgotPasswordRequest
import com.thejas.fleetmanagementtask.data.remote.dto.LoginRequest
import com.thejas.fleetmanagementtask.data.remote.dto.LogoutRequest
import com.thejas.fleetmanagementtask.data.remote.dto.ResetPasswordRequest
import com.thejas.fleetmanagementtask.data.remote.dto.UserDto
import com.thejas.fleetmanagementtask.data.remote.safeCall
import com.thejas.fleetmanagementtask.data.remote.safeCallUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

class AuthRepository(
    private val api: AuthApi,
    private val tokens: TokenStore,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    val isLoggedIn: Boolean get() = tokens.isLoggedIn
    val role: String? get() = tokens.role

    suspend fun login(email: String, password: String): ApiResult<UserDto> {
        val result = safeCall(io) { api.login(LoginRequest(email.trim().lowercase(), password)) }
        return when (result) {
            is ApiResult.Success -> {
                val token = result.data
                tokens.saveSession(
                    access = token.accessToken,
                    refresh = token.refreshToken,
                    userId = token.user.id,
                    role = token.user.role,
                )
                ApiResult.Success(token.user)
            }
            is ApiResult.Failure -> result
        }
    }

    suspend fun me(): ApiResult<UserDto> = safeCall(io) { api.me() }

    suspend fun logout(): ApiResult<Unit> {
        val refresh = tokens.refreshToken
        // The local session goes regardless: a driver tapping sign out offline
        // must still end up signed out on the device.
        val result = if (refresh != null) {
            safeCallUnit(io) { api.logout(LogoutRequest(refresh)) }
        } else {
            ApiResult.Success(Unit)
        }
        tokens.clear()
        return result
    }

    suspend fun changePassword(current: String, new: String): ApiResult<Unit> =
        safeCallUnit(io) { api.changePassword(ChangePasswordRequest(current, new)) }

    suspend fun forgotPassword(email: String): ApiResult<ForgotPasswordDto> =
        safeCall(io) { api.forgotPassword(ForgotPasswordRequest(email.trim().lowercase())) }

    suspend fun resetPassword(token: String, newPassword: String): ApiResult<Unit> =
        safeCallUnit(io) { api.resetPassword(ResetPasswordRequest(token, newPassword)) }
}

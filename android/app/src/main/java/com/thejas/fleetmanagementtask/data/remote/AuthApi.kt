package com.thejas.fleetmanagementtask.data.remote

import com.thejas.fleetmanagementtask.data.remote.dto.ChangePasswordRequest
import com.thejas.fleetmanagementtask.data.remote.dto.ForgotPasswordDto
import com.thejas.fleetmanagementtask.data.remote.dto.ForgotPasswordRequest
import com.thejas.fleetmanagementtask.data.remote.dto.LoginRequest
import com.thejas.fleetmanagementtask.data.remote.dto.LogoutRequest
import com.thejas.fleetmanagementtask.data.remote.dto.MessageDto
import com.thejas.fleetmanagementtask.data.remote.dto.RefreshRequest
import com.thejas.fleetmanagementtask.data.remote.dto.RegisterRequest
import com.thejas.fleetmanagementtask.data.remote.dto.ResetPasswordRequest
import com.thejas.fleetmanagementtask.data.remote.dto.TokenDto
import com.thejas.fleetmanagementtask.data.remote.dto.UserDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApi {

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<UserDto>

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): Response<TokenDto>

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): Response<TokenDto>

    @POST("auth/logout")
    suspend fun logout(@Body body: LogoutRequest): Response<MessageDto>

    @GET("auth/me")
    suspend fun me(): Response<UserDto>

    @POST("auth/change-password")
    suspend fun changePassword(@Body body: ChangePasswordRequest): Response<MessageDto>

    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequest): Response<ForgotPasswordDto>

    @POST("auth/reset-password")
    suspend fun resetPassword(@Body body: ResetPasswordRequest): Response<MessageDto>
}

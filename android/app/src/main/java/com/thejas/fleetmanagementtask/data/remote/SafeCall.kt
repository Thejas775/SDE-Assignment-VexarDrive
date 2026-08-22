package com.thejas.fleetmanagementtask.data.remote

import com.thejas.fleetmanagementtask.core.ApiError
import com.thejas.fleetmanagementtask.core.ApiResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException

/**
 * Turns a Retrofit call into an ApiResult: HTTP errors become typed failures
 * and IO problems become a network failure, so nothing above this layer has to
 * use try/catch.
 */
suspend fun <T : Any> safeCall(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    block: suspend () -> Response<T>,
): ApiResult<T> =
    withContext(dispatcher) {
        try {
            val response = block()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                ApiResult.Success(body)
            } else {
                ApiResult.Failure(
                    ApiErrorParser.parse(response.code(), response.errorBody()?.string())
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (io: IOException) {
            ApiResult.Failure(ApiError.network())
        } catch (other: Exception) {
            ApiResult.Failure(ApiError.unknown(other.message ?: "Unexpected error"))
        }
    }

/** For endpoints whose success body carries nothing worth keeping. */
suspend fun safeCallUnit(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    block: suspend () -> Response<*>,
): ApiResult<Unit> =
    withContext(dispatcher) {
        try {
            val response = block()
            if (response.isSuccessful) {
                ApiResult.Success(Unit)
            } else {
                ApiResult.Failure(
                    ApiErrorParser.parse(response.code(), response.errorBody()?.string())
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (io: IOException) {
            ApiResult.Failure(ApiError.network())
        } catch (other: Exception) {
            ApiResult.Failure(ApiError.unknown(other.message ?: "Unexpected error"))
        }
    }

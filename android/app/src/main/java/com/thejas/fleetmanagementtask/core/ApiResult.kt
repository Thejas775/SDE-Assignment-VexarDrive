package com.thejas.fleetmanagementtask.core

/** Every repository call returns this instead of throwing. */
sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

data class ApiError(
    val code: Int,
    val message: String,
    val fieldErrors: Map<String, String> = emptyMap(),
) {
    val isUnauthorized get() = code == 401
    val isForbidden get() = code == 403
    val isConflict get() = code == 409
    val isNetwork get() = code == CODE_NETWORK

    companion object {
        const val CODE_NETWORK = -1
        const val CODE_UNKNOWN = -2

        fun network(message: String = "No connection to the server") =
            ApiError(CODE_NETWORK, message)

        fun unknown(message: String = "Something went wrong") =
            ApiError(CODE_UNKNOWN, message)
    }
}

inline fun <T> ApiResult<T>.onSuccess(block: (T) -> Unit): ApiResult<T> {
    if (this is ApiResult.Success) block(data)
    return this
}

inline fun <T> ApiResult<T>.onFailure(block: (ApiError) -> Unit): ApiResult<T> {
    if (this is ApiResult.Failure) block(error)
    return this
}

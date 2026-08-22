package com.thejas.fleetmanagementtask.data.remote

import com.thejas.fleetmanagementtask.core.ApiError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The API speaks two error shapes: {"error_message": "..."} for business
 * failures, and FastAPI's {"detail":[{"loc":[...],"msg":"..."}]} for request
 * validation. Both are flattened into one ApiError so screens handle one type.
 */
object ApiErrorParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(code: Int, body: String?): ApiError {
        if (body.isNullOrBlank()) return ApiError(code, defaultMessage(code))
        return runCatching {
            val root = json.parseToJsonElement(body).jsonObject

            root["error_message"]?.jsonPrimitive?.content?.let { return ApiError(code, it) }

            val details = root["detail"] ?: return ApiError(code, defaultMessage(code))
            if (details is kotlinx.serialization.json.JsonPrimitive) {
                return ApiError(code, details.content)
            }

            val fields = mutableMapOf<String, String>()
            details.jsonArray.forEach { entry ->
                val item = entry.jsonObject
                val message = item["msg"]?.jsonPrimitive?.content ?: return@forEach
                val field = item["loc"]?.jsonArray?.lastOrNull()?.jsonPrimitive?.content
                if (field != null) fields[field] = message.removePrefix("Value error, ")
            }
            ApiError(
                code = code,
                message = fields.values.firstOrNull() ?: defaultMessage(code),
                fieldErrors = fields,
            )
        }.getOrElse { ApiError(code, defaultMessage(code)) }
    }

    private fun defaultMessage(code: Int) = when (code) {
        401 -> "Please sign in again"
        403 -> "You do not have permission to do that"
        404 -> "Not found"
        409 -> "That conflicts with existing data"
        422 -> "Please check the details you entered"
        in 500..599 -> "The server had a problem. Try again shortly."
        else -> "Something went wrong"
    }
}

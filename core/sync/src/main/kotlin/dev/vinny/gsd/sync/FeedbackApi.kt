package dev.vinny.gsd.sync

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

enum class FeedbackFailure { OFFLINE, RATE_LIMITED, SERVER, REJECTED }

/**
 * Anonymous opt-in feedback (parity spec P§6.1–§6.2): posts to the public feedback
 * collection with NO Authorization header, ever — the payload carries no account,
 * no device id, no task content, so there is nothing here to authenticate.
 */
class FeedbackApi(
    private val client: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = "https://api.vinny.io",
) {
    sealed interface Result {
        data object Sent : Result
        data class Failed(val kind: FeedbackFailure) : Result
    }

    private val jsonType = "application/json".toMediaType()
    private val lenient = Json { ignoreUnknownKeys = true }

    /** POST /api/collections/feedback/records — no auth header, ever (P§6.1). */
    suspend fun submit(payload: JsonObject): Result = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/collections/feedback/records")
                .post(payload.toString().toRequestBody(jsonType))
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> Result.Sent
                    // an earlier attempt with the same submission_id already landed — treat as success (P§6.1)
                    response.code == 400 && isDuplicateSubmission(body) -> Result.Sent
                    response.code == 429 -> Result.Failed(FeedbackFailure.RATE_LIMITED)
                    response.code >= 500 -> Result.Failed(FeedbackFailure.SERVER)
                    else -> Result.Failed(FeedbackFailure.REJECTED)
                }
            }
        } catch (error: IOException) {
            Result.Failed(FeedbackFailure.OFFLINE)
        }
    }

    /** Structured check on the parsed body's shape — never a substring scan (P§6.1). */
    private fun isDuplicateSubmission(body: String): Boolean = try {
        val data = lenient.parseToJsonElement(body).jsonObject["data"]?.jsonObject
        data?.get("submission_id")?.jsonObject?.get("code")?.jsonPrimitive?.content == "validation_not_unique"
    } catch (_: Exception) {
        false
    }
}

package dev.vinny.gsd.sync

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * PocketBase realtime over SSE (spec §9.6): connect unauthenticated, extract
 * the clientId from PB_CONNECT, subscribe with the bare-JWT header, then feed
 * `tasks` events to the caller. Blocks until the stream ends — the coordinator
 * owns reconnect backoff and the post-drop full sync.
 */
class SseClient(
    private val baseUrl: String,
    private val token: () -> String?,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build(),
) {
    class MissingClientId : Exception("PB_CONNECT carried no clientId")

    data class TaskEvent(val action: String, val record: WireTask)

    suspend fun run(onEvent: suspend (TaskEvent) -> Unit) {
        val request = Request.Builder()
            .url("$baseUrl/api/realtime")
            .header("Accept", "text/event-stream")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw PocketBaseApi.HttpError(response.code, "realtime connect")
            val source = response.body?.source() ?: return
            val parser = SseParser()
            var subscribed = false
            while (true) {
                val line = withContext(Dispatchers.IO) { source.readUtf8Line() } ?: break
                for (event in parser.feedLine(line)) {
                    when (event.event) {
                        "PB_CONNECT" -> {
                            val clientId = SseParser.clientId(event) ?: throw MissingClientId()
                            subscribe(clientId)
                            subscribed = true
                        }
                        "tasks" -> if (subscribed) decodeTaskEvent(event.data)?.let { onEvent(it) }
                    }
                }
            }
        }
    }

    private suspend fun subscribe(clientId: String) = withContext(Dispatchers.IO) {
        val body = """{"clientId":"$clientId","subscriptions":["tasks"]}"""
        val builder = Request.Builder()
            .url("$baseUrl/api/realtime")
            .post(body.toRequestBody("application/json".toMediaType()))
        token()?.let { builder.header("Authorization", it) }
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw PocketBaseApi.HttpError(response.code, "realtime subscribe")
        }
    }

    private fun decodeTaskEvent(data: String): TaskEvent? =
        try {
            val obj = Json.parseToJsonElement(data).jsonObject
            val action = obj["action"]?.jsonPrimitive?.content ?: return null
            val record = obj["record"]?.let { WireJson.decodeOrNull(it) } ?: return null
            TaskEvent(action, record)
        } catch (_: Exception) {
            null
        }
}

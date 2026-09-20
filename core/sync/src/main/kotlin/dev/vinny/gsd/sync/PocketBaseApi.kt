package dev.vinny.gsd.sync

import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class TaskListPage(
    val page: Int,
    val totalPages: Int,
    val totalItems: Int = items.size,
    val items: List<WireTask>,
    /** raw `updated` stamps aligned with items — the pull cursor source */
    val updatedStamps: List<String>,
)

@Serializable
data class OAuthProvider(
    val name: String,
    val displayName: String = "",
    val state: String = "",
    val authURL: String = "",
    val codeVerifier: String = "",
    val codeChallenge: String = "",
    val codeChallengeMethod: String = "",
)

@Serializable
data class AuthResult(val token: String, val record: AuthRecord)

@Serializable
data class AuthRecord(val id: String, val email: String = "")

/**
 * Minimal PocketBase client (spec §9). The auth header is the RAW JWT — no
 * `Bearer ` prefix — on every authenticated call including auth-refresh.
 */
class PocketBaseApi(
    private val baseUrl: String,
    private val token: () -> String?,
    private val client: OkHttpClient = OkHttpClient(),
) : SyncRemote, AccountRemote {
    class RateLimited : Exception("HTTP 429")
    class Unauthorized(code: Int) : Exception("HTTP $code")
    class HttpError(val code: Int, body: String) : Exception("HTTP $code: ${body.take(200)}")

    private val jsonType = "application/json".toMediaType()
    private val lenient = Json { ignoreUnknownKeys = true }

    private fun request(path: String, authenticated: Boolean = true): Request.Builder {
        val builder = Request.Builder().url("$baseUrl$path")
        if (authenticated) token()?.let { builder.header("Authorization", it) }
        return builder
    }

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            when {
                response.isSuccessful -> body
                response.code == 429 -> throw RateLimited()
                response.code == 401 || response.code == 403 -> throw Unauthorized(response.code)
                else -> throw HttpError(response.code, body)
            }
        }
    }

    override suspend fun listTasks(page: Int, updatedCursor: String): TaskListPage {
        val filter = URLEncoder.encode("updated >= \"$updatedCursor\"", "UTF-8")
        val raw = execute(
            request("/api/collections/tasks/records?page=$page&perPage=200&sort=updated,id&filter=$filter")
                .get().build(),
        )
        val envelope = Json.parseToJsonElement(raw).jsonObject
        val elements = envelope["items"]?.jsonArray ?: emptyList<JsonElement>()
        val decoded = elements.mapNotNull { element ->
            WireJson.decodeOrNull(element)?.let { wire ->
                wire to (element.jsonObject["updated"]?.jsonPrimitive?.content ?: "")
            }
        }
        return TaskListPage(
            page = envelope["page"]?.jsonPrimitive?.int ?: page,
            totalPages = envelope["totalPages"]?.jsonPrimitive?.int ?: 1,
            totalItems = envelope["totalItems"]?.jsonPrimitive?.int ?: decoded.size,
            items = decoded.map { it.first },
            updatedStamps = decoded.map { it.second },
        )
    }

    override suspend fun createTask(wire: WireTask): WireTask {
        val raw = execute(
            request("/api/collections/tasks/records")
                .post(WireJson.encode(wire.copy(id = "")).toRequestBody(jsonType)).build(),
        )
        return WireJson.decodeOrNull(raw) ?: throw HttpError(200, "unparseable create response")
    }

    override suspend fun updateTask(recordId: String, wire: WireTask): WireTask {
        // the body's id must match the record — "" reads as a primary-key change (400)
        val raw = execute(
            request("/api/collections/tasks/records/$recordId")
                .patch(WireJson.encode(wire.copy(id = recordId)).toRequestBody(jsonType)).build(),
        )
        return WireJson.decodeOrNull(raw) ?: wire
    }

    override suspend fun deleteTask(recordId: String) {
        execute(request("/api/collections/tasks/records/$recordId").delete().build())
    }

    /**
     * auth-refresh needs a valid token whatever the collection rules say, so a guest gets a 401
     * here even where a listing would answer 200 with zero items. The fresh token is not kept.
     */
    override suspend fun confirmIdentity(ownerId: String) {
        if (authRefresh().record.id != ownerId) throw IdentityMismatch()
    }

    suspend fun authMethods(): List<OAuthProvider> {
        val raw = execute(request("/api/collections/users/auth-methods", authenticated = false).get().build())
        val oauth = Json.parseToJsonElement(raw).jsonObject["oauth2"]?.jsonObject
        val providers = oauth?.get("providers")?.jsonArray ?: return emptyList()
        return providers.mapNotNull {
            try {
                lenient.decodeFromJsonElement(OAuthProvider.serializer(), it)
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun authWithOAuth2(
        provider: String,
        code: String,
        codeVerifier: String,
        redirectUrl: String,
    ): AuthResult {
        val body = buildJsonObject {
            put("provider", provider)
            put("code", code)
            put("codeVerifier", codeVerifier)
            put("redirectURL", redirectUrl)
        }
        val raw = execute(
            request("/api/collections/users/auth-with-oauth2", authenticated = false)
                .post(body.toString().toRequestBody(jsonType)).build(),
        )
        return lenient.decodeFromString(AuthResult.serializer(), raw)
    }

    suspend fun authRefresh(): AuthResult {
        val raw = execute(
            request("/api/collections/users/auth-refresh")
                .post(ByteArray(0).toRequestBody(null)).build(),
        )
        return lenient.decodeFromString(AuthResult.serializer(), raw)
    }

    override suspend fun deleteAccountViaRoute() {
        execute(request("/api/gsd/account").delete().build())
    }

    override suspend fun deleteUser(userId: String) {
        execute(request("/api/collections/users/records/$userId").delete().build())
    }
}

package dev.vinny.gsd.model

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** Wire/domain dates: ISO-8601 UTC, millisecond precision, `Z` suffix (spec §7.3). */
object InstantIso8601Serializer : KSerializer<Instant> {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(formatter.format(value.truncatedTo(ChronoUnit.MILLIS).atZone(ZoneOffset.UTC)))
    }

    override fun deserialize(decoder: Decoder): Instant =
        OffsetDateTime.parse(decoder.decodeString()).toInstant()
}

object TaskJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    fun encode(task: Task): String = json.encodeToString(Task.serializer(), task)

    /**
     * Lenient decode boundary (spec §7.3): a record failing required fields or
     * parsing is skipped and counted by the caller — never aborts a batch.
     */
    fun decodeOrNull(raw: JsonElement): Task? =
        try {
            json.decodeFromJsonElement(Task.serializer(), raw)
        } catch (_: Exception) {
            null
        }

    fun encodeIds(ids: List<String>): String =
        json.encodeToString(ListSerializer(String.serializer()), ids)

    fun decodeIds(raw: String): List<String> =
        try {
            json.decodeFromString(ListSerializer(String.serializer()), raw)
        } catch (_: Exception) {
            emptyList()
        }
}

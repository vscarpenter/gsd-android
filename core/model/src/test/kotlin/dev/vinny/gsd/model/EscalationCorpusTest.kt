package dev.vinny.gsd.model

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The shared 38-case escalation corpus (spec-escalation.md, D-11/D-12) is the rule's contract: a
 * byte-identical copy of gsd-taskmanager's fixture (md5 731c71e9...), also run by web and iOS.
 * Never edit the file.
 */
class EscalationCorpusTest {
    @Test
    fun `all corpus cases evaluate identically`() {
        val raw = checkNotNull(
            javaClass.getResourceAsStream("/fixtures/cross-platform/escalation-corpus.json"),
        ).readBytes().decodeToString()
        val cases = Json.parseToJsonElement(raw).jsonObject.getValue("cases").jsonArray
        assertEquals(38, cases.size)
        val failures = buildList {
            for (case in cases.map { it.jsonObject }) {
                val name = case.getValue("name").jsonPrimitive.content
                val window = case.getValue("windowDays").let { if (it is JsonNull) null else it.jsonPrimitive.int }
                val got = Escalation.evaluate(
                    task = taskFrom(case.getValue("task").jsonObject),
                    now = OffsetDateTime.parse(case.getValue("now").jsonPrimitive.content).toInstant(),
                    windowDays = window,
                    zone = ZoneId.of(case.getValue("zone").jsonPrimitive.content),
                )
                val expect = case.getValue("expect").jsonObject
                val want = EscalationResult(
                    escalating = expect.getValue("escalating").jsonPrimitive.boolean,
                    daysUntilDue = expect.getValue("daysUntilDue").nullableInt(),
                    reason = expect.getValue("reason").nullableString()?.let { EscalationReason.fromWire(it) },
                    target = expect.getValue("target").nullableString()?.let { EscalationTarget.fromWire(it) },
                )
                if (got != want) add("$name:\n  want $want\n  got  $got")
            }
        }
        assertEquals(emptyList(), failures, "corpus divergences:\n" + failures.joinToString("\n"))
    }

    private fun taskFrom(o: JsonObject): Task {
        val epoch = Instant.EPOCH
        return Task(
            title = "t",
            urgent = o.getValue("urgent").jsonPrimitive.boolean,
            important = o.getValue("important").jsonPrimitive.boolean,
            completed = o["completed"]?.jsonPrimitive?.boolean ?: false,
            createdAt = epoch,
            updatedAt = epoch,
            dueDate = o["dueDate"]?.nullableString()?.let(Instant::parse),
            savedAt = o["savedAt"]?.nullableString()?.let(Instant::parse),
        )
    }

    private fun kotlinx.serialization.json.JsonElement.nullableString(): String? =
        if (this is JsonNull) null else jsonPrimitive.content

    private fun kotlinx.serialization.json.JsonElement.nullableInt(): Int? =
        if (this is JsonNull) null else jsonPrimitive.int
}

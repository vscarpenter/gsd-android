package dev.vinny.gsd.model

import java.time.Instant
import java.time.LocalTime
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
 * The shared 74-case weekly sweep corpus (spec-weekly-sweep.md, D-14) is the rule's contract: a
 * byte-identical copy of gsd-taskmanager's fixture (md5 b10a904c...), also run by web and iOS.
 * Never edit the file.
 */
class SweepCorpusTest {
    private val cases: List<JsonObject> = Json.parseToJsonElement(
        checkNotNull(javaClass.getResourceAsStream("/fixtures/cross-platform/sweep-corpus.json"))
            .readBytes().decodeToString(),
    ).jsonObject.getValue("cases").jsonArray.map { it.jsonObject }

    @Test
    fun `the corpus has 74 cases`() {
        assertEquals(74, cases.size)
    }

    @Test
    fun `all corpus cases agree`() {
        val failures = cases.mapNotNull { case ->
            val (want, got) = run(case)
            if (want == got) null else "${case.str("name")}:\n  want $want\n  got  $got"
        }
        assertEquals(emptyList(), failures, "corpus divergences:\n" + failures.joinToString("\n"))
    }

    private fun run(case: JsonObject): Pair<Any?, Any?> {
        val zone = ZoneId.of(case.str("zone"))
        val now = OffsetDateTime.parse(case.str("now")).toInstant()
        val expect = case.getValue("expect").jsonObject
        return when (val op = case.str("op")) {
            "cards" -> {
                val window = case["windowDays"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.int
                val tasks = case.getValue("tasks").jsonArray.map { it.jsonObject }
                    .filter { (it.strOrNull("location") ?: "active") == "active" }
                    .map(::taskFrom)
                val want = expect.getValue("cards").jsonArray.map { it.jsonObject }
                    .map { SweepCard(it.str("id"), SweepReason.fromWire(it.str("reason"))) }
                want to WeeklySweep.cards(tasks, now, window, zone)
            }
            "entryShown" -> {
                val lastSwept = case.strOrNull("lastSweptAt")?.let { OffsetDateTime.parse(it).toInstant() }
                expect.getValue("shown").jsonPrimitive.boolean to
                    WeeklySweep.entryShown(lastSwept, case.getValue("cardCount").jsonPrimitive.int, now, zone)
            }
            "nextReminder" -> {
                val r = case.getValue("reminder").jsonObject
                val reminder = SweepReminder(
                    enabled = r.getValue("enabled").jsonPrimitive.boolean,
                    weekday = r.getValue("weekday").jsonPrimitive.int,
                    time = LocalTime.parse(r.str("time")),
                )
                expect.strOrNull("fire")?.let(Instant::parse) to WeeklySweep.nextReminder(reminder, now, zone)
            }
            else -> error("unknown corpus op $op")
        }
    }

    private fun taskFrom(o: JsonObject): Task {
        val default = Instant.parse("2026-11-07T00:00:00.000Z")
        return Task(
            id = o.str("id"),
            title = "t",
            urgent = o["urgent"]?.jsonPrimitive?.boolean ?: false,
            important = o["important"]?.jsonPrimitive?.boolean ?: false,
            completed = o["completed"]?.jsonPrimitive?.boolean ?: false,
            createdAt = o.strOrNull("createdAt")?.let(Instant::parse) ?: default,
            updatedAt = o.strOrNull("updatedAt")?.let(Instant::parse) ?: default,
            dueDate = o.strOrNull("dueDate")?.let(Instant::parse),
            savedAt = o.strOrNull("savedAt")?.let(Instant::parse),
        )
    }

    private fun JsonObject.str(key: String): String = getValue(key).jsonPrimitive.content

    private fun JsonObject.strOrNull(key: String): String? = get(key)?.takeUnless { it is JsonNull }?.jsonPrimitive?.content
}

package dev.vinny.gsd.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The shared due-time corpus pins decision D-1 across clients: a byte-identical copy of
 * gsd-taskmanager's fixture, also run by the web and iOS suites. Android is the source of truth for
 * the rule, so a failure here is a question about the corpus or the rule. Never edit the file alone.
 */
class DueTimeCorpusTest {
    private data class Classified(val allDay: Boolean, val localDate: String, val displayTime: String?)

    private val displayTime = DateTimeFormatter.ofPattern("HH:mm")

    @Test
    fun `all corpus cases match`() {
        val raw = checkNotNull(
            javaClass.getResourceAsStream("/fixtures/cross-platform/due-time-corpus.json"),
        ).readBytes().decodeToString()
        val cases = Json.parseToJsonElement(raw).jsonObject.getValue("cases").jsonArray.map { it.jsonObject }
        assertEquals(CASE_COUNT, cases.size)
        val failures = cases.mapNotNull { case ->
            val (want, got) = compare(case)
            if (got == want) null else "${case.text("name")}:\n  want $want\n  got  $got"
        }
        assertEquals(emptyList(), failures, "corpus divergences:\n" + failures.joinToString("\n"))
    }

    /** The case's expected value and what the model produced, parsed so format differences can't fail a case. */
    private fun compare(case: JsonObject): Pair<Any?, Any?> {
        val zone = ZoneId.of(case.text("zone"))
        val expect = case.getValue("expect").jsonObject
        return when (val op = case.text("op")) {
            "classify" -> expectedClassified(expect) to classify(checkNotNull(case.instant("due")), zone)
            "onDate" -> {
                val date = LocalDate.parse(case.text("date"))
                checkNotNull(expect.instant("due")) to DueTime.onDate(case.instant("due"), date, zone)
            }
            "atTime" -> {
                val time = case.textOrNull("time")?.let(LocalTime::parse)
                checkNotNull(expect.instant("due")) to DueTime.atTime(checkNotNull(case.instant("due")), time, zone)
            }
            "timedReminderFire", "allDayReminderFire" -> expect.instant("fire") to fire(case, zone)
            else -> error("unknown op $op")
        }
    }

    private fun expectedClassified(expect: JsonObject) = Classified(
        allDay = expect.getValue("allDay").jsonPrimitive.boolean,
        localDate = expect.text("localDate"),
        displayTime = expect.textOrNull("displayTime"),
    )

    private fun classify(due: Instant, zone: ZoneId): Classified {
        val time = DueTime.timeOf(due, zone)
        return Classified(time == null, due.atZone(zone).toLocalDate().toString(), time?.let(displayTime::format))
    }

    private fun fire(case: JsonObject, zone: ZoneId): Instant? {
        val now = checkNotNull(case.instant("now"))
        val task = Task(
            title = "corpus",
            urgent = false,
            important = false,
            createdAt = now,
            updatedAt = now,
            dueDate = case.instant("due"),
            notifyBefore = case.getValue("notifyBefore").jsonPrimitive.intOrNull,
            snoozedUntil = case["snoozedUntil"]?.let { case.instant("snoozedUntil") },
        )
        val settings = ReminderSettings(
            defaultReminderMinutes = case.getValue("defaultReminder").jsonPrimitive.int,
            quietHoursStart = case["quietHoursStart"]?.let { case.textOrNull("quietHoursStart") }?.let(LocalTime::parse),
            quietHoursEnd = case["quietHoursEnd"]?.let { case.textOrNull("quietHoursEnd") }?.let(LocalTime::parse),
        )
        return ReminderPlanner.fireTime(task, settings, now, zone)
    }

    private fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.content

    private fun JsonObject.textOrNull(key: String) = getValue(key).jsonPrimitive.contentOrNull

    private fun JsonObject.instant(key: String) = textOrNull(key)?.let(Instant::parse)

    private companion object {
        const val CASE_COUNT = 118
    }
}

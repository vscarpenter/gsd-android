package dev.vinny.gsd.model

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The shared 123-case top three corpus (spec-top-three.md, D-13) is the rule's contract: a byte-identical
 * copy of gsd-taskmanager's fixture (md5 07458077...), also run by web and iOS. Never edit the file.
 */
class TopThreeCorpusTest {
    private val cases: List<JsonObject> = Json.parseToJsonElement(
        checkNotNull(javaClass.getResourceAsStream("/fixtures/cross-platform/top-three-corpus.json"))
            .readBytes().decodeToString(),
    ).jsonObject.getValue("cases").jsonArray.map { it.jsonObject }

    @Test
    fun `the corpus has 123 cases`() {
        assertEquals(123, cases.size)
    }

    @Test
    fun `all corpus cases agree`() {
        val failures = cases.mapNotNull { case ->
            val name = case.str("name")
            val (want, got) = run(case)
            if (want == got) null else "$name:\n  want $want\n  got  $got"
        }
        assertEquals(emptyList(), failures, "corpus divergences:\n" + failures.joinToString("\n"))
    }

    /** Returns (expected, actual) in a comparable shape for one case. */
    private fun run(case: JsonObject): Pair<Any?, Any?> {
        val expect = case.getValue("expect").jsonObject
        return when (val op = case.str("op")) {
            "picks" -> {
                val r = TopThree.todaysPicks(active(case), now(case), zone(case))
                listOf(expect.strings("picks"), expect.strings("overflow"), expect.bool("needsTidy")) to
                    listOf(r.picks, r.overflow, r.needsTidy)
            }
            "nextRank" ->
                expect.intOrNull("rank") to TopThree.nextRank(active(case), now(case), zone(case))
            "tidy" -> {
                val want = expect.getValue("writes").jsonArray.map { it.jsonObject }.map {
                    TopThree.TidyWrite(it.str("id"), it.strOrNull("focusDate"), it.intOrNull("focusRank"))
                }
                want to TopThree.tidy(active(case), now(case), zone(case))
            }
            "carryOver" -> {
                val r = TopThree.carryOver(active(case), now(case), zone(case))
                (expect.strOrNull("fromDate") to expect.strings("ids")) to (r.fromDate to r.ids)
            }
            "suggest" -> {
                val window = case["windowDays"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.int
                expect.strings("ids") to TopThree.suggest(active(case), now(case), window, zone(case))
            }
            "fromWire" -> {
                val wire = case.getValue("wire").jsonObject
                val got = TopThree.fromWire(wire.strOrNull("focus_date"), wire.doubleOrNull("focus_rank"))
                FocusFields(expect.strOrNull("focusDate"), expect.intOrNull("focusRank")) to got
            }
            "toWire" -> {
                val task = case.getValue("task").jsonObject
                val got = TopThree.toWire(FocusFields(task.strOrNull("focusDate"), task.intOrNull("focusRank")))
                TopThree.WireFocus(expect.str("focus_date"), expect.int("focus_rank")) to got
            }
            "pushFocus" -> {
                val current = case.getValue("current").takeUnless { it is JsonNull }?.jsonObject
                val got = TopThree.pushFocus(current?.let { FocusFields(it.strOrNull("focusDate"), it.intOrNull("focusRank")) })
                val want = if (expect["omit"] != null) null
                else TopThree.WireFocus(expect.str("focus_date"), expect.int("focus_rank"))
                want to got
            }
            "pullMerge" -> {
                val got = TopThree.pullMerge(
                    storedContract = case.getValue("storedContract").takeUnless { it is JsonNull }?.jsonPrimitive?.int,
                    tombstoned = case.bool("tombstoned"),
                    local = case.getValue("local").takeUnless { it is JsonNull }?.jsonObject?.let(::mergeRow),
                    remote = mergeRow(case.getValue("remote").jsonObject),
                )
                val want = TopThree.PullMergeResult(
                    action = TopThree.PullAction.fromWire(expect.str("action")),
                    conflict = expect.bool("conflict"),
                    row = expect.getValue("row").takeUnless { it is JsonNull }?.jsonObject?.let(::mergeRow),
                )
                want to got
            }
            else -> error("unknown corpus op $op")
        }
    }

    private fun now(case: JsonObject): Instant = OffsetDateTime.parse(case.str("now")).toInstant()

    private fun zone(case: JsonObject): ZoneId = ZoneId.of(case.str("zone"))

    /** Only active rows reach a selector: archived and trashed rows live in their own stores. */
    private fun active(case: JsonObject): List<Task> = case.getValue("tasks").jsonArray
        .map { it.jsonObject }
        .filter { (it.strOrNull("location") ?: "active") == "active" }
        .map(::taskFrom)

    private fun taskFrom(o: JsonObject): Task {
        val default = Instant.parse("2026-10-01T00:00:00.000Z")
        return Task(
            id = o.str("id"),
            title = "t",
            urgent = o.boolOrDefault("urgent"),
            important = o.boolOrDefault("important"),
            completed = o.boolOrDefault("completed"),
            createdAt = o.strOrNull("createdAt")?.let(Instant::parse) ?: default,
            updatedAt = o.strOrNull("updatedAt")?.let(Instant::parse) ?: default,
            dueDate = o.strOrNull("dueDate")?.let(Instant::parse),
            savedAt = o.strOrNull("savedAt")?.let(Instant::parse),
            focusDate = o.strOrNull("focusDate"),
            // Task.focusRank is an Int, so a fractional rank can't exist on the model; 0 is out of range too.
            focusRank = o.doubleOrNull("focusRank")?.let { if (it % 1.0 == 0.0) it.toInt() else 0 },
        )
    }

    private fun mergeRow(o: JsonObject) = TopThree.MergeRow(
        updatedAt = Instant.parse(o.str("updatedAt")),
        focus = FocusFields(o.strOrNull("focusDate"), o.intOrNull("focusRank")),
        savedAt = o.strOrNull("savedAt")?.let(Instant::parse),
    )

    private fun JsonObject.str(key: String): String = getValue(key).jsonPrimitive.content

    private fun JsonObject.strOrNull(key: String): String? = get(key)?.takeUnless { it is JsonNull }?.jsonPrimitive?.content

    private fun JsonObject.int(key: String): Int = getValue(key).jsonPrimitive.int

    private fun JsonObject.intOrNull(key: String): Int? = get(key)?.takeUnless { it is JsonNull }?.jsonPrimitive?.int

    private fun JsonObject.doubleOrNull(key: String): Double? = get(key)?.takeUnless { it is JsonNull }?.jsonPrimitive?.double

    private fun JsonObject.bool(key: String): Boolean = getValue(key).jsonPrimitive.boolean

    private fun JsonObject.boolOrDefault(key: String): Boolean = get(key)?.jsonPrimitive?.boolean ?: false

    private fun JsonObject.strings(key: String): List<String> = getValue(key).jsonArray.map { (it as JsonElement).jsonPrimitive.content }
}

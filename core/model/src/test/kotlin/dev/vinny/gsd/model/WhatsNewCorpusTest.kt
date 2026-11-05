package dev.vinny.gsd.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The shared What's New corpus (gsd-taskmanager tasks/spec-whats-new.md, D-15) is the show rule's
 * contract: a byte-identical copy of gsd-taskmanager's fixture (md5 7a8d4ac6...), also run by web
 * and iOS. Never edit the file here; change the web authority and copy it.
 */
class WhatsNewCorpusTest {
    @Test
    fun `all corpus cases decide identically`() {
        val raw = checkNotNull(
            javaClass.getResourceAsStream("/fixtures/cross-platform/whats-new-corpus.json"),
        ).readBytes().decodeToString()
        val root = Json.parseToJsonElement(raw).jsonObject
        assertEquals(1, root.getValue("version").jsonPrimitive.int)
        val releases = root.getValue("releases").jsonArray.map {
            val o = it.jsonObject
            WhatsNew.Entry(o.getValue("version").jsonPrimitive.content, o.getValue("notable").jsonPrimitive.boolean)
        }
        val cases = root.getValue("cases").jsonArray
        assertEquals(21, cases.size)
        val failures = buildList {
            for (case in cases.map { it.jsonObject }) {
                val name = case.getValue("name").jsonPrimitive.content
                val got = WhatsNew.decide(
                    releases = releases,
                    currentVersion = case.getValue("currentVersion").jsonPrimitive.content,
                    lastSeenVersion = case.getValue("lastSeenVersion").nullableString(),
                    existingUser = case.getValue("existingUser").jsonPrimitive.boolean,
                )
                val expect = case.getValue("expect").jsonObject
                val want = WhatsNew.Decision(
                    show = expect.getValue("show").jsonPrimitive.boolean,
                    headlineVersion = expect.getValue("headlineVersion").nullableString(),
                    unseenCount = expect.getValue("unseenCount").jsonPrimitive.int,
                    recordVersion = expect.getValue("recordVersion").nullableString(),
                )
                if (got != want) add("$name:\n  want $want\n  got  $got")
            }
        }
        assertEquals(emptyList(), failures, "corpus divergences:\n" + failures.joinToString("\n"))
    }

    private fun JsonElement.nullableString(): String? = if (this is JsonNull) null else jsonPrimitive.content
}

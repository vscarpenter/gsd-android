package dev.vinny.gsd.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.boolean

/**
 * The shared 40-case corpus is the parser contract (parity spec P§5) — a
 * byte-identical copy of gsd-taskmanager's fixture, also run by the iOS and
 * MCP-server suites. Appendix C's vectors are superseded; never edit the file.
 */
class CaptureParserCorpusTest {
    @Test
    fun `all corpus cases parse identically`() {
        val raw = checkNotNull(
            javaClass.getResourceAsStream("/fixtures/cross-platform/capture-parser-corpus.json"),
        ).readBytes().decodeToString()
        val cases = Json.parseToJsonElement(raw).jsonObject.getValue("cases").jsonArray
        assertEquals(40, cases.size)
        val failures = buildList {
            for (case in cases.map { it.jsonObject }) {
                val name = case.getValue("name").jsonPrimitive.content
                val expect = case.getValue("expect").jsonObject
                val got = CaptureParser.parse(case.getValue("input").jsonPrimitive.content)
                val want = ParsedCapture(
                    title = expect.getValue("title").jsonPrimitive.content,
                    urgent = expect.getValue("urgent").jsonPrimitive.boolean,
                    important = expect.getValue("important").jsonPrimitive.boolean,
                    tags = expect.getValue("tags").jsonArray.map { it.jsonPrimitive.content },
                    urls = expect.getValue("urls").jsonArray.map { it.jsonPrimitive.content },
                )
                if (got != want) add("$name:\n  want $want\n  got  $got")
            }
        }
        assertEquals(emptyList(), failures, "corpus divergences:\n" + failures.joinToString("\n"))
    }
}

# GSD Android — Cross-Platform Parity Implementation Plan (August 2026 catch-up)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring gsd-android to contract parity with the web and iOS clients as of 2026-08-29: backup envelope 2.1.0, trash, sync tombstone suppression, the 40-case capture-parser corpus, the feedback contract, and the widened Apple sign-in note.

**Architecture:** All behavior rules come from `docs/2026-08-29-cross-platform-parity-spec.md` (the parity spec — it wins over `docs/gsd-android-spec.md` until the final task folds the amendments in). Work flows along the existing module boundary: pure contract logic in `:core:model` (JVM tests), persistence in `:core:store` (Room v1→v2 migration, the repo's first), sync guards in `:core:sync`, surfaces in `:app`. The cross-platform fixtures (backup pair + parser corpus) are copied **byte-identical** from `gsd-taskmanager` and run in the JVM suite.

**Tech Stack:** unchanged — Kotlin 2.4 · AGP 9.2 · Compose BOM 2026.06 · Room 2.8.4 (KSP2) · kotlinx.serialization · OkHttp (`:core:sync`) · JUnit4 + kotlin-test on JVM.

**Spec:** `docs/2026-08-29-cross-platform-parity-spec.md` (cited below as *P§n*). The original product spec `docs/gsd-android-spec.md` stays authoritative for everything the parity spec doesn't touch.

## Global Constraints

- **Fixtures are the contract.** `capture-parser-corpus.json`, `ios-backup-export.json`, `web-backup-2.1.0.json` are copied byte-identical from `/Users/vinnycarpenter/Projects/GSD/gsd-taskmanager/tests/fixtures/cross-platform/` — never hand-edit them. Where prose and fixture disagree, the fixture wins (P§5).
- **Envelope:** version is the **string** `"2.1.0"`; absent-key ≠ empty-array; import cap 10,000 counts `tasks + archivedTasks + deletedTasks`; 10 MB byte check first (P§2).
- **Trash:** 30-day retention, start-of-day cutoff (iOS's rule; web uses a rolling instant), on-cutoff row kept; remote `delete` enqueued **before** the local move; restore preserves the original timestamps (faithful undo, matches web+iOS) and enqueues `create`; delete-forever/empty/purge enqueue nothing; trash never syncs (P§3).
- **Tombstone suppression:** remote strictly newer than `archivedAt`/`deletedAt` resurrects; anything else (equal, older, missing, unparseable) drops — fail closed (P§4).
- **Feedback:** exactly 7 snake_case keys, pinned by test; `POST https://api.vinny.io/api/collections/feedback/records`, no auth header; `validation_not_unique` 400 = success (P§6).
- **TDD red/green/refactor per task; `./gradlew :core:model:test` stays sub-second** (it is the gate). Conventional commits per the `creating-git-commits` skill. All UI strings in `strings.xml`.
- **Never let persisted `quadrant` drift from the flags** — backups *write* it, import always recomputes it.

## File Structure (locked in by this plan)

```
core/model/src/main/kotlin/dev/vinny/gsd/model/
├── CaptureParser.kt          # MODIFY: corpus-aligned grammar (Task 1)
├── Backup.kt                 # CREATE: envelope types (Task 2)
├── Interchange.kt            # MODIFY: export/parse v2.1.0 (Tasks 2–3)
└── Feedback.kt               # CREATE: payload contract (Task 9)
core/model/src/test/resources/fixtures/cross-platform/   # CREATE: 3 fixtures (Tasks 1, 4)
core/store/src/main/kotlin/dev/vinny/gsd/store/
├── DormantEntities.kt        # MODIFY: + DeletedTaskEntity (Task 5)
├── GsdDatabase.kt            # MODIFY: v2 + MIGRATION_1_2 (Task 5)
├── TrashStore.kt             # CREATE (Task 5)
├── TaskStore.kt              # MODIFY: delete → trash (Task 5)
├── LocalErase.kt             # MODIFY: clear trash (Task 5)
├── BackupAssembler.kt        # CREATE: gather 7 stores (Task 6)
└── ImportApplier.kt          # CREATE: per-store mode scopes (Task 6)
core/sync/src/main/kotlin/dev/vinny/gsd/sync/
├── SyncPorts.kt              # MODIFY: tombstone lookups (Task 8)
├── SyncEngine.kt / Sse.kt    # MODIFY: suppression guard (Task 8)
├── RoomSyncLocalStore.kt     # MODIFY: implement lookups (Task 8)
└── FeedbackApi.kt            # CREATE (Task 10)
app/src/main/kotlin/dev/vinny/gsd/
├── browse/TrashScreen.kt     # CREATE (Task 7)
├── feedback/FeedbackScreen.kt# CREATE (Task 10)
├── settings/SettingsScreen.kt# MODIFY: export/import wiring, Feedback row (Tasks 6, 10)
├── settings/AccountGroup.kt  # MODIFY: two-tier Apple note (Task 11)
├── matrix/MatrixViewModel.kt # MODIFY: undo → trash restore (Task 7)
└── nav/*                     # MODIFY: gsd://trash, feedback route (Tasks 7, 10)
```

---

### Task 1: Capture-parser corpus alignment (`:core:model`)

**Files:**
- Create: `core/model/src/test/resources/fixtures/cross-platform/capture-parser-corpus.json` (byte-copy)
- Create: `core/model/src/test/kotlin/dev/vinny/gsd/model/CaptureParserCorpusTest.kt`
- Modify: `core/model/src/main/kotlin/dev/vinny/gsd/model/CaptureParser.kt`

**Interfaces:**
- Consumes: `CaptureParser.parse(input: String): ParsedCapture` (exists; signature unchanged).
- Produces: same signature, corpus-conformant behavior. Later tasks rely on nothing new here.

- [x] **Step 1: Copy the corpus byte-identically**

```bash
cp /Users/vinnycarpenter/Projects/GSD/gsd-taskmanager/tests/fixtures/cross-platform/capture-parser-corpus.json \
   /Users/vinnycarpenter/Projects/GSD/gsd-android/core/model/src/test/resources/fixtures/cross-platform/
diff /Users/vinnycarpenter/Projects/GSD/gsd-taskmanager/tests/fixtures/cross-platform/capture-parser-corpus.json \
     /Users/vinnycarpenter/Projects/GSD/gsd-android/core/model/src/test/resources/fixtures/cross-platform/capture-parser-corpus.json
```

Expected: `diff` silent. (Create the directory first; `src/test/resources` is on the JVM test classpath automatically for a `kotlin.jvm` module.)

- [x] **Step 2: Write the corpus runner (failing test)**

Corpus shape: `{ "note": "...", "cases": [ { "name", "input", "expect": { "title", "urgent", "important", "tags", "urls" } } ] }` — 40 cases.

```kotlin
package dev.vinny.gsd.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull

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
```

- [x] **Step 3: Run it — confirm it fails on the settled decisions**

Run: `./gradlew :core:model:test --tests '*CaptureParserCorpusTest*'`
Expected: FAIL listing divergent cases (Unicode tags, duplicate URLs, boundary anchoring, over-length tag handling are the likely deltas from the July grammar).

- [x] **Step 4: Align the parser — implement to the fixture**

The nine settled decisions (P§5) as implementation guidance; where this sketch and a corpus case disagree, **the case wins**:

```kotlin
object CaptureParser {
    // Boundary-anchored: a URL token starts the string or follows whitespace.
    private val urlPattern = Regex("""(?:^|(?<=\s))https?://\S+""")
    private val doubleBang = Regex("""(?<=^|\s)!!(?=\s|$|#)""")
    private val singleBang = Regex("""(?<=^|\s)!(?=\s|$|#)""")
    private val star = Regex("""(?<=^|\s)\*(?=\s|$|#)""")
    // Unicode word chars start a tag; '-' allowed as continuation only.
    // Unbounded match, then filter: 1–30 keeps, >30 drops but still strips.
    private val tagPattern = Regex("""#([\p{L}\p{M}\p{N}_][\p{L}\p{M}\p{N}_-]*)""")
    private val whitespace = Regex("""\s+""")

    fun parse(input: String): ParsedCapture {
        val urls = LinkedHashSet<String>()                    // duplicates collapse
        var working = urlPattern.replace(input) { match ->
            val safe = UrlSanitizer.sanitize(match.value)
            if (safe != null) { urls += safe; "" } else match.value
        }
        val both = doubleBang.containsMatchIn(working)
        working = doubleBang.replace(working, " ")             // all repeats strip
        val urgent = both || singleBang.containsMatchIn(working)
        working = singleBang.replace(working, " ")
        val important = both || star.containsMatchIn(working)
        working = star.replace(working, " ")

        val tags = tagPattern.findAll(working)
            .map { it.groupValues[1] }
            .filter { it.length in 1..30 }                    // over-length drops…
            .map { it.lowercase() }                           // …dedupe is case-insensitive
            .distinct()
            .take(20)                                          // cap by truncation, never reject
            .toList()
        working = tagPattern.replace(working, " ")             // …but every match strips

        val title = working.replace(whitespace, " ").trim()
        return ParsedCapture(
            title = if (title.isEmpty() && urls.isNotEmpty()) "Review link below" else title,
            urgent = urgent, important = important,
            tags = tags, urls = urls.toList(),
        )
    }
}
```

`UrlSanitizer` stays as-is (raw trimmed storage and the `>= 2048` guard are already corpus-correct).

- [x] **Step 5: Run the corpus + the whole model suite**

Run: `./gradlew :core:model:test`
Expected: PASS, including the pre-existing `CaptureParserTest`. If a pre-existing vector now contradicts the corpus, **the corpus wins** — update that vector citing P§5 (Appendix C is superseded).

- [x] **Step 6: Commit**

`feat(parser): align capture grammar with the 40-case cross-platform corpus`

---

### Task 2: Backup envelope types + export 2.1.0 (`:core:model`)

**Files:**
- Create: `core/model/src/main/kotlin/dev/vinny/gsd/model/Backup.kt`
- Modify: `core/model/src/main/kotlin/dev/vinny/gsd/model/Interchange.kt`
- Test: `core/model/src/test/kotlin/dev/vinny/gsd/model/InterchangeTest.kt` (extend)

**Interfaces:**
- Consumes: `Task`, `SmartView` (FilterCriteria.kt:81 — `{id, name, icon, criteria, isBuiltIn, createdAt, updatedAt}`), `TaskJson.encode`, `Quadrant.id`.
- Produces (used by Tasks 3, 4, 6):

```kotlin
data class ArchivedTask(val task: Task, val archivedAt: Instant)
data class DeletedTask(val task: Task, val deletedAt: Instant)
data class BackupNotificationSettings(
    val enabled: Boolean, val defaultReminder: Int, val soundEnabled: Boolean,
    val quietHoursStart: String?, val quietHoursEnd: String?,   // "HH:mm" or null=omit
    val permissionAsked: Boolean, val updatedAt: Instant,
)
data class BackupArchiveSettings(val enabled: Boolean, val archiveAfterDays: Int)
data class BackupAppPreferences(val pinnedSmartViewIds: List<String>)
/** null list/singleton = key absent from the document (P§2.2: absent ≠ empty). */
data class BackupEnvelope(
    val tasks: List<Task>,
    val archivedTasks: List<ArchivedTask>? = null,
    val deletedTasks: List<DeletedTask>? = null,
    val smartViews: List<SmartView>? = null,
    val notificationSettings: BackupNotificationSettings? = null,
    val archiveSettings: BackupArchiveSettings? = null,
    val appPreferences: BackupAppPreferences? = null,
)
fun Interchange.export(envelope: BackupEnvelope, exportedAt: Instant): String  // replaces export(tasks, at)
```

- [x] **Step 1: Write the failing export tests** (extend `InterchangeTest`)

```kotlin
@Test
fun `export writes the 2_1_0 envelope with all seven stores`() {
    val task = sampleTask(id = "task-000000000000000001")   // reuse the file's existing builder
    val out = Interchange.export(
        BackupEnvelope(
            tasks = listOf(task),
            archivedTasks = listOf(ArchivedTask(task.copy(id = "arch-00000000000000001", completed = true), Instant.parse("2026-06-01T10:00:00.000Z"))),
            deletedTasks = listOf(DeletedTask(task.copy(id = "trash-0000000000000001"), Instant.parse("2026-08-27T09:00:00.000Z"))),
            smartViews = listOf(SmartView("view-00000001", "Overdue work", "overdue", FilterCriteria(overdue = true), false, Instant.parse("2026-07-01T09:00:00.000Z"), Instant.parse("2026-07-15T09:00:00.000Z"))),
            notificationSettings = BackupNotificationSettings(true, 15, true, "22:00", "07:00", false, Instant.parse("2026-08-18T12:00:00.000Z")),
            archiveSettings = BackupArchiveSettings(enabled = false, archiveAfterDays = 30),
            appPreferences = BackupAppPreferences(listOf("view-00000001")),
        ),
        exportedAt = Instant.parse("2026-08-29T12:00:00.000Z"),
    )
    val root = Json.parseToJsonElement(out).jsonObject
    assertEquals("2.1.0", root.getValue("version").jsonPrimitive.content)         // string, not int
    assertEquals(
        listOf("appPreferences", "archiveSettings", "archivedTasks", "deletedTasks",
               "exportedAt", "notificationSettings", "smartViews", "tasks", "version"),
        root.keys.toList(),                                                        // sorted keys
    )
    val taskRecord = root.getValue("tasks").jsonArray[0].jsonObject
    assertEquals(task.quadrant.id, taskRecord.getValue("quadrant").jsonPrimitive.content) // quadrant written
    assertEquals("2026-06-01T10:00:00.000Z",
        root.getValue("archivedTasks").jsonArray[0].jsonObject.getValue("archivedAt").jsonPrimitive.content)
    assertEquals("2026-08-27T09:00:00.000Z",
        root.getValue("deletedTasks").jsonArray[0].jsonObject.getValue("deletedAt").jsonPrimitive.content)
    val prefs = root.getValue("appPreferences").jsonObject
    assertEquals("preferences", prefs.getValue("id").jsonPrimitive.content)
    assertEquals(5, prefs.getValue("maxPinnedViews").jsonPrimitive.int)            // web-schema constants
    assertEquals(true, prefs.getValue("smartViewsEnabled").jsonPrimitive.boolean)
    val notif = root.getValue("notificationSettings").jsonObject
    assertEquals("settings", notif.getValue("id").jsonPrimitive.content)
    assertEquals("22:00", notif.getValue("quietHoursStart").jsonPrimitive.content)
}

@Test
fun `absent stores are omitted keys, not empty arrays`() {
    val out = Interchange.export(BackupEnvelope(tasks = emptyList()), Instant.parse("2026-08-29T12:00:00.000Z"))
    val root = Json.parseToJsonElement(out).jsonObject
    assertEquals(setOf("exportedAt", "tasks", "version"), root.keys)
}
```

- [x] **Step 2: Run — expect FAIL** (`Interchange.export(BackupEnvelope, …)` unresolved).

Run: `./gradlew :core:model:test --tests '*InterchangeTest*'`

- [x] **Step 3: Implement `Backup.kt` (types above) and the export**

In `Interchange`, replace `export(tasks, exportedAt)`:

```kotlin
fun export(envelope: BackupEnvelope, exportedAt: Instant): String {
    val root = buildJsonObject {
        put("version", JsonPrimitive("2.1.0"))
        put("exportedAt", JsonPrimitive(iso(exportedAt)))
        put("tasks", JsonArray(envelope.tasks.map { taskRecord(it) }))
        envelope.archivedTasks?.let { rows ->
            put("archivedTasks", JsonArray(rows.map { taskRecord(it.task, "archivedAt" to iso(it.archivedAt)) }))
        }
        envelope.deletedTasks?.let { rows ->
            put("deletedTasks", JsonArray(rows.map { taskRecord(it.task, "deletedAt" to iso(it.deletedAt)) }))
        }
        envelope.smartViews?.let { views -> put("smartViews", JsonArray(views.map { smartViewRecord(it) })) }
        envelope.notificationSettings?.let { put("notificationSettings", notificationRecord(it)) }
        envelope.archiveSettings?.let {
            put("archiveSettings", buildJsonObject {
                put("id", JsonPrimitive("settings"))
                put("enabled", JsonPrimitive(it.enabled))
                put("archiveAfterDays", JsonPrimitive(it.archiveAfterDays))
            })
        }
        envelope.appPreferences?.let {
            put("appPreferences", buildJsonObject {
                put("id", JsonPrimitive("preferences"))
                put("pinnedSmartViewIds", JsonArray(it.pinnedSmartViewIds.map(::JsonPrimitive)))
                put("maxPinnedViews", JsonPrimitive(5))          // web-schema-required constants
                put("smartViewsEnabled", JsonPrimitive(true))    // (P§2.1; iOS exports them too)
            })
        }
    }
    return pretty.encodeToString(JsonElement.serializer(), sortKeys(root))
}

/** A domain task record plus the backup-only derived quadrant (import ignores it). */
private fun taskRecord(task: Task, vararg extras: Pair<String, String>): JsonObject {
    val base = TaskJson.json.parseToJsonElement(TaskJson.encode(task)).jsonObject
    return JsonObject(base + ("quadrant" to JsonPrimitive(task.quadrant.id)) +
        extras.associate { (k, v) -> k to JsonPrimitive(v) })
}

private fun smartViewRecord(view: SmartView): JsonObject = buildJsonObject {
    put("id", JsonPrimitive(view.id))
    put("name", JsonPrimitive(view.name))
    put("icon", JsonPrimitive(view.icon))
    put("criteria", TaskJson.json.encodeToJsonElement(FilterCriteria.serializer(), view.criteria))
    put("isBuiltIn", JsonPrimitive(view.isBuiltIn))
    put("createdAt", JsonPrimitive(iso(view.createdAt)))
    put("updatedAt", JsonPrimitive(iso(view.updatedAt)))
}

private fun notificationRecord(s: BackupNotificationSettings): JsonObject = buildJsonObject {
    put("id", JsonPrimitive("settings"))
    put("enabled", JsonPrimitive(s.enabled))
    put("defaultReminder", JsonPrimitive(s.defaultReminder))
    put("soundEnabled", JsonPrimitive(s.soundEnabled))
    s.quietHoursStart?.let { put("quietHoursStart", JsonPrimitive(it)) }   // omit when off
    s.quietHoursEnd?.let { put("quietHoursEnd", JsonPrimitive(it)) }
    put("permissionAsked", JsonPrimitive(s.permissionAsked))
    put("updatedAt", JsonPrimitive(iso(s.updatedAt)))
}
```

Update the one production call site (`app/.../settings/SettingsScreen.kt:78`) minimally for now — `Interchange.export(BackupEnvelope(tasks = tasks), Instant.now())` — Task 6 replaces it with the full assembler. Fix any test callers of the old signature the same way.

- [x] **Step 4: Run** `./gradlew :core:model:test` → PASS. Also `./gradlew :app:assembleDebug` compiles.

- [x] **Step 5: Commit** — `feat(model): export the seven-store 2.1.0 backup envelope`

---

### Task 3: Envelope import — lenient, version-tolerant, three-array cap (`:core:model`)

**Files:**
- Modify: `core/model/src/main/kotlin/dev/vinny/gsd/model/Interchange.kt`
- Test: `core/model/src/test/kotlin/dev/vinny/gsd/model/InterchangeTest.kt` (extend)

**Interfaces:**
- Produces (used by Tasks 4, 6): `Interchange.parse(raw, limitBytes = 10*1024*1024, limitTasks = 10_000): BackupImport` where

```kotlin
data class BackupImport(val envelope: BackupEnvelope, val version: String, val skipped: Int)
```

`envelope.archivedTasks == null` means the key was absent; `emptyList()` means present-and-empty (Task 6 relies on that distinction for replace-mode clears). `Interchange.TooLarge` semantics unchanged.

- [x] **Step 1: Write the failing import tests**

```kotlin
@Test
fun `parse accepts int, string, and absent versions`() {
    assertEquals("1", Interchange.parse("""{"tasks": [], "version": 1, "exportedAt": "2026-01-01T00:00:00.000Z"}""").version)
    assertEquals("2.1.0", Interchange.parse("""{"tasks": [], "version": "2.1.0", "exportedAt": "2026-01-01T00:00:00.000Z"}""").version)
    assertEquals("1", Interchange.parse("""{"tasks": []}""").version)   // absent → "1" (iOS-style leniency, P§2.2)
}

@Test
fun `cap counts all three task-bearing arrays`() {
    fun records(n: Int, prefix: String) = (1..n).joinToString(",") {
        """{"id":"$prefix-${it.toString().padStart(6, '0')}","title":"t","urgent":false,"important":false,
            "createdAt":"2026-01-01T00:00:00.000Z","updatedAt":"2026-01-01T00:00:00.000Z"}"""
    }
    val overCap = """{"tasks":[${records(4000, "a")}],"archivedTasks":[${records(3001, "b")}],
        "deletedTasks":[${records(3000, "c")}],"version":"2.1.0","exportedAt":"2026-01-01T00:00:00.000Z"}"""
    assertFailsWith<Interchange.TooLarge> { Interchange.parse(overCap) }        // 10,001
}

@Test
fun `absent stores decode to null, present-empty to empty lists`() {
    val absent = Interchange.parse("""{"tasks": [], "version": "2.1.0", "exportedAt": "2026-01-01T00:00:00.000Z"}""")
    assertNull(absent.envelope.archivedTasks)
    val present = Interchange.parse("""{"tasks": [], "archivedTasks": [], "version": "2.1.0", "exportedAt": "2026-01-01T00:00:00.000Z"}""")
    assertEquals(emptyList(), present.envelope.archivedTasks)
}

@Test
fun `incoming quadrant is ignored and recomputed from the flags`() {
    val parsed = Interchange.parse("""{"tasks":[{"id":"task-1","title":"t","urgent":true,"important":true,
        "quadrant":"not-urgent-not-important",
        "createdAt":"2026-01-01T00:00:00.000Z","updatedAt":"2026-01-01T00:00:00.000Z"}],
        "version":"2.1.0","exportedAt":"2026-01-01T00:00:00.000Z"}""")
    assertEquals(Quadrant.DO_FIRST, parsed.envelope.tasks.single().quadrant)
}

@Test
fun `archived and deleted rows carry their timestamps, defaulting to now when unparseable`() {
    val parsed = Interchange.parse("""{"tasks":[],
        "archivedTasks":[{"id":"arch-1","title":"t","urgent":false,"important":false,
            "createdAt":"2026-01-01T00:00:00.000Z","updatedAt":"2026-01-01T00:00:00.000Z",
            "archivedAt":"2026-06-01T10:00:00.000Z"}],
        "deletedTasks":[{"id":"tr-1","title":"t","urgent":false,"important":false,
            "createdAt":"2026-01-01T00:00:00.000Z","updatedAt":"2026-01-01T00:00:00.000Z",
            "deletedAt":"garbage"}],
        "version":"2.1.0","exportedAt":"2026-01-01T00:00:00.000Z"}""")
    assertEquals(Instant.parse("2026-06-01T10:00:00.000Z"), parsed.envelope.archivedTasks!!.single().archivedAt)
    // unparseable deletedAt: the row imports anyway; timestamp defaulted (P§2.2) — assert non-null, not a value
    assertNotNull(parsed.envelope.deletedTasks!!.single().deletedAt)
}
```

- [x] **Step 2: Run — expect FAIL** (no `BackupImport`, cap counts one array). `./gradlew :core:model:test --tests '*InterchangeTest*'`

- [x] **Step 3: Implement `parse` v2**

```kotlin
fun parse(raw: String, limitBytes: Int = 10 * 1024 * 1024, limitTasks: Int = 10_000): BackupImport {
    if (raw.toByteArray(Charsets.UTF_8).size > limitBytes) throw TooLarge("Import exceeds size limit")
    val root = Json.parseToJsonElement(raw).jsonObject
    val taskEntries = root["tasks"]?.jsonArray ?: JsonArray(emptyList())
    val archivedEntries = root["archivedTasks"]?.jsonArray
    val deletedEntries = root["deletedTasks"]?.jsonArray
    val total = taskEntries.size + (archivedEntries?.size ?: 0) + (deletedEntries?.size ?: 0)
    if (total > limitTasks) throw TooLarge("Import exceeds $limitTasks tasks")   // before decode work

    var skipped = 0
    fun decodeTasks(entries: JsonArray): List<Task> = entries.mapNotNull {
        TaskJson.decodeOrNull(it) ?: run { skipped++; null }
    }
    fun timestamp(element: JsonElement, key: String): Instant =
        (element as? JsonObject)?.get(key)?.jsonPrimitive?.contentOrNull
            ?.let { runCatching(Instant::parse).getOrNull() } ?: Instant.now()

    val version = when (val v = root["version"]) {
        null -> "1"
        else -> v.jsonPrimitive.content
    }
    return BackupImport(
        envelope = BackupEnvelope(
            tasks = decodeTasks(taskEntries),
            archivedTasks = archivedEntries?.mapNotNull { el ->
                TaskJson.decodeOrNull(el)?.let { ArchivedTask(it, timestamp(el, "archivedAt")) }
                    ?: run { skipped++; null }
            },
            deletedTasks = deletedEntries?.mapNotNull { el ->
                TaskJson.decodeOrNull(el)?.let { DeletedTask(it, timestamp(el, "deletedAt")) }
                    ?: run { skipped++; null }
            },
            smartViews = root["smartViews"]?.jsonArray?.mapNotNull(::smartViewOrNull),
            notificationSettings = root["notificationSettings"]?.let(::notificationOrNull),
            archiveSettings = root["archiveSettings"]?.let(::archiveSettingsOrNull),
            appPreferences = root["appPreferences"]?.let(::appPreferencesOrNull),
        ),
        version = version,
        skipped = skipped,
    )
}
```

The four `…OrNull` decoders are lenient (`runCatching`, defaults per the parity spec table P§2.1; a malformed singleton decodes to `null` = treated as absent; a smart-view row with `isBuiltIn: true` or invalid criteria is dropped). `Instant.now()` in `timestamp` is acceptable here — Interchange is a codec, not a store; determinism-sensitive callers pass data through, and the fallback only fires on corrupt input. Delete the old `ImportResult` type; migrate existing callers/tests (`SettingsScreen.kt:246` reads `parsed.envelope.tasks` + `parsed.skipped` until Task 6 rewires it).

- [x] **Step 4: Run** `./gradlew :core:model:test` → PASS; `./gradlew :app:assembleDebug` compiles.

- [x] **Step 5: Commit** — `feat(model): lenient 2.1.0 envelope import with the three-array cap`

---

### Task 4: Cross-platform backup fixture pair (`:core:model`)

**Files:**
- Create: `core/model/src/test/resources/fixtures/cross-platform/ios-backup-export.json` (byte-copy)
- Create: `core/model/src/test/resources/fixtures/cross-platform/web-backup-2.1.0.json` (byte-copy)
- Create: `core/model/src/test/kotlin/dev/vinny/gsd/model/CrossPlatformBackupTest.kt`

**Interfaces:**
- Consumes: `Interchange.parse` / `Interchange.export` from Tasks 2–3, exactly as specified there.
- Produces: nothing new — this task is the third-client enforcement of the shared fixture discipline (P§2.3).

- [x] **Step 1: Copy both fixtures byte-identically**

```bash
cp /Users/vinnycarpenter/Projects/GSD/gsd-taskmanager/tests/fixtures/cross-platform/ios-backup-export.json \
   /Users/vinnycarpenter/Projects/GSD/gsd-taskmanager/tests/fixtures/cross-platform/web-backup-2.1.0.json \
   /Users/vinnycarpenter/Projects/GSD/gsd-android/core/model/src/test/resources/fixtures/cross-platform/
```

Verify with `diff` per file (silent output).

- [x] **Step 2: Write the round-trip test (failing only if Tasks 2–3 got something wrong — that is its job)**

```kotlin
package dev.vinny.gsd.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Both suites' real-export fixtures, imported by the third client (P§2.3).
 * Same-client round-trips are a mirror, not a check — these files are what
 * caught three real mismatches between web and iOS within minutes.
 */
class CrossPlatformBackupTest {
    private fun fixture(name: String): String = checkNotNull(
        javaClass.getResourceAsStream("/fixtures/cross-platform/$name"),
    ).readBytes().decodeToString()

    @Test
    fun `the iOS export imports losslessly`() {
        val parsed = Interchange.parse(fixture("ios-backup-export.json"))
        assertEquals(0, parsed.skipped)
        assertEquals("2.1.0", parsed.version)
        assertEquals(2, parsed.envelope.tasks.size)
        assertEquals(1, parsed.envelope.archivedTasks!!.size)
        assertEquals(1, parsed.envelope.deletedTasks!!.size)
        assertEquals(1, parsed.envelope.smartViews!!.size)
        val rich = parsed.envelope.tasks.first { it.id == "web-task-1" }
        assertEquals(Quadrant.DO_FIRST, rich.quadrant)                 // recomputed, not read
        assertEquals(2, rich.subtasks.size)
        assertEquals(1, rich.timeEntries.size)                         // rich entries survive
        assertEquals(Instant.parse("2026-08-27T09:00:00.000Z"),
            parsed.envelope.deletedTasks!!.single().deletedAt)
        assertEquals("22:00", parsed.envelope.notificationSettings!!.quietHoursStart)
        assertEquals(60, parsed.envelope.archiveSettings!!.archiveAfterDays)
        assertEquals(listOf("custom-view-1"), parsed.envelope.appPreferences!!.pinnedSmartViewIds)
    }

    @Test
    fun `the web export imports losslessly`() {
        val parsed = Interchange.parse(fixture("web-backup-2.1.0.json"))
        assertEquals(0, parsed.skipped)
        assertEquals("2.1.0", parsed.version)
        assertTrue(parsed.envelope.tasks.isNotEmpty())
    }

    @Test
    fun `an Android re-export of the iOS fixture reparses to the same envelope`() {
        val first = Interchange.parse(fixture("ios-backup-export.json"))
        val reExported = Interchange.export(first.envelope, Instant.parse("2026-08-29T12:00:00.000Z"))
        val second = Interchange.parse(reExported)
        assertEquals(first.envelope, second.envelope)                  // content-stable round trip
        val root = Json.parseToJsonElement(reExported).jsonObject
        assertEquals("2.1.0", root.getValue("version").jsonPrimitive.content)
    }
}
```

Adjust the web-fixture assertions to that file's actual contents on first run (read the file, assert its real counts — precision over vagueness; do not weaken the iOS assertions).

- [x] **Step 3: Run** `./gradlew :core:model:test --tests '*CrossPlatformBackupTest*'` → PASS (fix Task 2/3 code if not — the fixture wins).

- [x] **Step 4: Commit** — `test(model): adopt the cross-platform backup fixture pair`

---

### Task 5: Trash — Room v2, `TrashStore`, delete rewiring (`:core:store`)

**Files:**
- Modify: `core/store/src/main/kotlin/dev/vinny/gsd/store/DormantEntities.kt` (add `DeletedTaskEntity`)
- Modify: `core/store/src/main/kotlin/dev/vinny/gsd/store/GsdDatabase.kt` (version 2, DAO, migration)
- Create: `core/store/src/main/kotlin/dev/vinny/gsd/store/TrashStore.kt`
- Modify: `core/store/src/main/kotlin/dev/vinny/gsd/store/TaskStore.kt` (delete → trash move)
- Modify: `core/store/src/main/kotlin/dev/vinny/gsd/store/LocalErase.kt` (clear trash)
- Test: `core/store/src/androidTest/kotlin/dev/vinny/gsd/store/TrashStoreTest.kt` (create), `MigrationTest.kt` (create), `ReplaceEraseTest.kt` (extend)

**Interfaces:**
- Consumes: `SyncQueueEntity.tombstone(taskId, now)`, `SyncQueueEntity.forTask(SyncOp.CREATE, task, now)`, `TaskEntity`/`toTask()`/`toEntity()` mappers, the `ArchivedTaskEntity` column list (mirror it).
- Produces (used by Tasks 6, 7, 8):

```kotlin
@Entity(tableName = "deleted_tasks", indices = [Index("deletedAt")])
data class DeletedTaskEntity(/* every ArchivedTaskEntity column, with deletedAt: Long instead of archivedAt */)

class TrashStore(private val db: GsdDatabase, private val clock: Clock) {
    fun observeTrash(): Flow<List<Pair<Task, Instant>>>       // task + deletedAt, newest first
    suspend fun byId(id: String): Task?
    suspend fun restore(id: String): Task?                    // → active, timestamps preserved + enqueue CREATE; null if absent
    suspend fun deleteForever(id: String)                     // no enqueue
    suspend fun empty()                                       // no enqueue
    suspend fun purgeExpired(): Int                           // 30-day start-of-day cutoff; returns purged count
}
// TaskStore.delete(id): Task?  — same signature; now moves the row into deleted_tasks
```

- [x] **Step 1: Add `room-testing` if absent** — in `gradle/libs.versions.toml` (`room-testing = { module = "androidx.room:room-testing", version.ref = "room" }`) and `core/store/build.gradle.kts` (`androidTestImplementation(libs.room.testing)`).

- [x] **Step 2: Write the failing migration test**

```kotlin
package dev.vinny.gsd.store

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), GsdDatabase::class.java)

    @Test
    fun migrate1To2_createsDeletedTasks_andPreservesRows() {
        helper.createDatabase("migration-test", 1).apply {
            execSQL("""INSERT INTO tasks (id, title, description, urgent, important, quadrant,
                completed, createdAt, updatedAt, recurrence, tagsJson, subtasksJson,
                dependenciesJson, notificationEnabled, notificationSent, timeEntriesJson)
                VALUES ('t1', 'survives', '', 0, 0, 'not-urgent-not-important',
                0, 1, 1, 'none', '[]', '[]', '[]', 1, 0, '[]')""")
            close()
        }
        val db = helper.runMigrationsAndValidate("migration-test", 2, true, GsdDatabase.MIGRATION_1_2)
        db.query("SELECT COUNT(*) FROM tasks").use { it.moveToFirst(); assert(it.getInt(0) == 1) }
        db.query("SELECT COUNT(*) FROM deleted_tasks").use { it.moveToFirst(); assert(it.getInt(0) == 0) }
    }
}
```

(Adjust the v1 INSERT column list against `core/store/schemas/dev.vinny.gsd.store.GsdDatabase/1.json` — the schema file is the truth for v1's exact columns and NOT NULLs.)

- [x] **Step 3: Run it** — `./gradlew :core:store:connectedAndroidTest --tests '*MigrationTest*'` → FAIL (`MIGRATION_1_2` unresolved).

- [x] **Step 4: Implement the schema change**

`DormantEntities.kt`: add `DeletedTaskEntity` — copy `ArchivedTaskEntity`'s declaration verbatim, rename table to `deleted_tasks`, replace `val archivedAt: Long` with `val deletedAt: Long`, index on `deletedAt` (drop the `completedAt` index — trash doesn't sweep on completion). Add the DAO alongside the entity:

```kotlin
@Dao
interface DeletedTaskDao {
    @Query("SELECT * FROM deleted_tasks ORDER BY deletedAt DESC")
    fun observeAll(): Flow<List<DeletedTaskEntity>>

    @Query("SELECT * FROM deleted_tasks WHERE id = :id")
    suspend fun byId(id: String): DeletedTaskEntity?

    @Query("SELECT id FROM deleted_tasks")
    suspend fun allIds(): List<String>

    @Upsert
    suspend fun upsert(entity: DeletedTaskEntity)

    @Query("DELETE FROM deleted_tasks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM deleted_tasks WHERE deletedAt < :cutoffMs")
    suspend fun purgeOlderThan(cutoffMs: Long): Int

    @Query("DELETE FROM deleted_tasks")
    suspend fun clear()
}
```

`GsdDatabase.kt`: add `DeletedTaskEntity::class` to `entities`, bump `version = 2`, add `abstract fun deletedTaskDao(): DeletedTaskDao`, and:

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS `deleted_tasks` (
            `id` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL,
            `urgent` INTEGER NOT NULL, `important` INTEGER NOT NULL, `quadrant` TEXT NOT NULL,
            `completed` INTEGER NOT NULL, `completedAt` INTEGER, `createdAt` INTEGER NOT NULL,
            `updatedAt` INTEGER NOT NULL, `dueDate` INTEGER, `recurrence` TEXT NOT NULL,
            `tagsJson` TEXT NOT NULL, `subtasksJson` TEXT NOT NULL, `dependenciesJson` TEXT NOT NULL,
            `parentTaskId` TEXT, `notifyBefore` INTEGER, `notificationEnabled` INTEGER NOT NULL,
            `notificationSent` INTEGER NOT NULL, `lastNotificationAt` INTEGER, `snoozedUntil` INTEGER,
            `estimatedMinutes` INTEGER, `timeEntriesJson` TEXT NOT NULL, `deletedAt` INTEGER NOT NULL,
            PRIMARY KEY(`id`))""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_deleted_tasks_deletedAt` ON `deleted_tasks` (`deletedAt`)")
    }
}
```

Register it in both builders (`.addMigrations(MIGRATION_1_2)` in `build`; in-memory builder needs nothing). After the first build, verify `core/store/schemas/.../2.json` was generated and its `deleted_tasks` definition matches the SQL above exactly (Room validates this in the migration test — a mismatch fails there).

- [x] **Step 5: Migration test green** — rerun Step 3's command → PASS.

- [x] **Step 6: Write the failing `TrashStoreTest`** (instrumented; mirror `ArchiveStoreTest`'s in-memory setup and fixed-`Clock` pattern)

```kotlin
@Test fun delete_movesToTrash_andEnqueuesTombstoneFirst() = runBlocking {
    val task = seedTask("t1")                       // reuse the suite's existing seeding helper
    taskStore.delete("t1")
    assertNull(db.taskDao().byId("t1"))
    assertEquals("t1", db.deletedTaskDao().byId("t1")?.id)
    val queued = db.syncQueueDao().pending().single { it.taskId == "t1" }
    assertEquals(SyncOp.DELETE, queued.operation)
}

@Test fun restore_reactivates_preservingTimestamps_andEnqueuesCreate() = runBlocking {
    val original = seedTask("t1"); taskStore.delete("t1")
    clock.advanceBy(1_000)                          // prove restore does NOT stamp updatedAt (P§3.1: faithful undo, matches web+iOS)
    val restored = trashStore.restore("t1")!!
    assertNull(db.deletedTaskDao().byId("t1"))
    assertEquals("t1", db.taskDao().byId("t1")?.id)
    assertEquals(original.updatedAt, restored.updatedAt)
    assertEquals(SyncOp.CREATE, db.syncQueueDao().pending().last { it.taskId == "t1" }.operation)
}

@Test fun purge_dropsStrictlyOlderThanStartOfDayCutoff_keepsOnCutoff() = runBlocking {
    val zone = ZoneId.systemDefault()
    val now = clock.instant()
    val cutoff = now.atZone(zone).toLocalDate().minusDays(30).atStartOfDay(zone).toInstant()
    seedTrashRow("on-cutoff", deletedAt = cutoff)
    seedTrashRow("older", deletedAt = cutoff.minusMillis(1))
    seedTrashRow("fresh", deletedAt = now)
    assertEquals(1, trashStore.purgeExpired())
    assertEquals(setOf("on-cutoff", "fresh"), db.deletedTaskDao().allIds().toSet())
}

@Test fun deleteForever_and_empty_enqueueNothing() = runBlocking {
    seedTask("t1"); seedTask("t2"); taskStore.delete("t1"); taskStore.delete("t2")
    val queuedBefore = db.syncQueueDao().pending().size
    trashStore.deleteForever("t1"); trashStore.empty()
    assertEquals(queuedBefore, db.syncQueueDao().pending().size)
    assertTrue(db.deletedTaskDao().allIds().isEmpty())
}
```

- [x] **Step 7: Run** → FAIL (`TrashStore` unresolved). **Step 8: Implement**

`TrashStore.kt` (the `ArchiveStore` pattern, inverted enqueue rules):

```kotlin
/**
 * Deletion is recoverable for 30 days (parity spec P§3). The remote delete was
 * already enqueued when the task entered the trash, so restore is the only
 * operation here that syncs (as a fresh CREATE); delete-forever, empty, and
 * purge touch nothing remote.
 */
class TrashStore(private val db: GsdDatabase, private val clock: Clock) {
    fun observeTrash(): Flow<List<Pair<Task, Instant>>> =
        db.deletedTaskDao().observeAll().map { rows ->
            rows.map { it.toTask() to Instant.ofEpochMilli(it.deletedAt) }
        }

    suspend fun byId(id: String): Task? = db.deletedTaskDao().byId(id)?.toTask()

    suspend fun restore(id: String): Task? {
        val row = db.deletedTaskDao().byId(id) ?: return null
        val task = row.toTask()   // timestamps preserved — faithful undo (P§3.1, matches web+iOS)
        db.withTransaction {
            db.syncQueueDao().insert(SyncQueueEntity.forTask(SyncOp.CREATE, task, clock.instant()))
            db.taskDao().upsert(task.toEntity())
            db.deletedTaskDao().deleteById(id)
        }
        return task
    }

    suspend fun deleteForever(id: String) = db.deletedTaskDao().deleteById(id)

    suspend fun empty() = db.deletedTaskDao().clear()

    suspend fun purgeExpired(): Int {
        val zone = ZoneId.systemDefault()
        val cutoff = clock.instant().atZone(zone).toLocalDate()
            .minusDays(30).atStartOfDay(zone).toInstant()
        return db.deletedTaskDao().purgeOlderThan(cutoff.toEpochMilli())   // < cutoff: on-cutoff kept
    }
}
```

`DeletedTaskEntity.toTask()` / `TaskEntity.toDeleted(deletedAt)` mappers: copy the `ArchiveStore.kt` mapper pair, swap the timestamp column. In `TaskStore.delete` (TaskStore.kt:108), inside the existing transaction, after the tombstone insert and before `deleteById`, add the move:

```kotlin
db.syncQueueDao().insert(SyncQueueEntity.tombstone(id, now))                 // existing line — enqueue FIRST
db.deletedTaskDao().upsert(checkNotNull(db.taskDao().byId(id)).toDeleted(deletedAt = now.toEpochMilli()))
db.taskDao().deleteById(id)                                                  // existing line
```

The dependency-scrub loop above it is unchanged. `LocalErase`: add `db.deletedTaskDao().clear()` next to the archive clear, and extend `ReplaceEraseTest` with an assertion that erase leaves the trash empty. Replace-mode import's per-id tombstones (TaskStore.kt:95-99) stay hard deletes — replace is an explicit store swap, not a user deletion (matches web replace semantics).

- [x] **Step 9: Run the store suite** — `./gradlew :core:store:connectedAndroidTest` → PASS; `./gradlew testDebugUnitTest` still green.

- [x] **Step 10: Commit** — `feat(store): recoverable trash with 30-day retention (Room v2)`

---

### Task 6: Backup assembly + import application across the seven stores

**Files:**
- Create: `core/store/src/main/kotlin/dev/vinny/gsd/store/BackupAssembler.kt`
- Create: `core/store/src/main/kotlin/dev/vinny/gsd/store/ImportApplier.kt`
- Modify: `app/src/main/kotlin/dev/vinny/gsd/settings/SettingsScreen.kt` (:78 export, :246 import)
- Modify: `app/src/main/kotlin/dev/vinny/gsd/AppContainer.kt` (construct + hand out both)
- Test: `core/store/src/androidTest/kotlin/dev/vinny/gsd/store/BackupRoundTripTest.kt` (create)

**Interfaces:**
- Consumes: `BackupEnvelope`/`BackupImport` (Tasks 2–3), `TrashStore` + `DeletedTaskDao` (Task 5), `SmartViewDao.clearCustom()`, the three singleton DAOs, `TaskStore`'s existing merge/replace entry points, `MergePlan.compute`.
- Produces (used by Task 7's screens and the settings wiring):

```kotlin
class BackupAssembler(private val db: GsdDatabase) {
    suspend fun assemble(): BackupEnvelope      // all seven stores; custom smart views only
}
enum class ImportMode { MERGE, REPLACE }
class ImportApplier(private val db: GsdDatabase, private val taskStore: TaskStore, private val clock: Clock) {
    suspend fun apply(import: BackupImport, mode: ImportMode): ImportSummary
}
data class ImportSummary(val imported: Int, val skipped: Int)
```

- [x] **Step 1: Write the failing round-trip test** (instrumented, in-memory DB)

```kotlin
@Test fun assemble_exportsCustomViewsOnly_andAllSingletons() = runBlocking { /* seed 1 built-in +
    1 custom view, settings rows, 1 archived, 1 trashed, 2 active; assemble();
    assert envelope counts: tasks 2, archived 1, deleted 1, smartViews 1 (the custom one),
    all three singletons non-null */ }

@Test fun replaceMode_presentEmptyArrayClearsStore_absentKeyLeavesIt() = runBlocking { /* seed archive;
    apply(envelope with archivedTasks = emptyList(), REPLACE) → archive empty;
    re-seed; apply(envelope with archivedTasks = null, REPLACE) → archive untouched */ }

@Test fun mergeMode_existingArchiveRowWinsOnCollision() = runBlocking { /* seed archived id "a1"
    title "local"; apply(envelope archived [a1 title "imported"], MERGE) → title still "local" */ }

@Test fun mergeMode_neverAdoptsSingletons() = runBlocking { /* seed local notification settings;
    apply(envelope with different notificationSettings, MERGE) → local row unchanged;
    same envelope in REPLACE → adopted (P§2.2: merge never adopts another device's configuration) */ }

@Test fun importedTombstoneStoresEnqueueNothing() = runBlocking { /* apply envelope with archived +
    deleted + views + singletons but no tasks; sync queue size unchanged */ }

@Test fun anIdNeverLandsInTwoStores() = runBlocking { /* envelope where "x" is in both tasks and
    archivedTasks; after MERGE apply, "x" is active and NOT archived (P§2.2 rule) */ }
```

- [x] **Step 2: Run** → FAIL (types unresolved). **Step 3: Implement**

`BackupAssembler.assemble()` — read every store, map to envelope types (entity `Long` millis → `Instant`; `ReminderSettings`' `LocalTime` fields → `"HH:mm"` strings via `toString()`; the `NotificationSettingsEntity.updatedAt` millis feed `BackupNotificationSettings.updatedAt`; a never-written singleton row assembles from its entity defaults). Filter `smartViews` to `!isBuiltIn`. Always emit all seven keys on a user-initiated export (full backup — `null` only ever appears on *import*).

`ImportApplier.apply(import, mode)`:

```kotlin
suspend fun apply(import: BackupImport, mode: ImportMode): ImportSummary {
    val env = import.envelope
    // tasks: the pre-existing semantics, untouched (TaskStore replace / MergePlan + insert)
    val imported = when (mode) {
        ImportMode.REPLACE -> taskStore.replaceAll(env.tasks)
        ImportMode.MERGE -> taskStore.insertMerged(MergePlan.compute(taskStore.all(), env.tasks))
    }
    val activeIds = taskStore.all().map { it.id }.toSet()
    env.archivedTasks?.let { rows ->
        if (mode == ImportMode.REPLACE) db.archivedTaskDao().clear()
        val keep = db.archivedTaskDao().allIds().toSet()          // add allIds() to the DAO
        rows.filter { it.task.id !in activeIds && it.task.id !in keep }
            .forEach { db.archivedTaskDao().upsert(it.task.toEntity().toArchived(it.archivedAt.toEpochMilli())) }
    }
    env.deletedTasks?.let { rows ->
        if (mode == ImportMode.REPLACE) db.deletedTaskDao().clear()
        val archived = db.archivedTaskDao().allIds().toSet()
        val keep = db.deletedTaskDao().allIds().toSet()
        rows.filter { it.task.id !in activeIds && it.task.id !in archived && it.task.id !in keep }
            .forEach { db.deletedTaskDao().upsert(it.task.toEntity().toDeleted(it.deletedAt.toEpochMilli())) }
    }
    env.smartViews?.let { views ->
        if (mode == ImportMode.REPLACE) db.smartViewDao().clearCustom()
        views.filter { !it.isBuiltIn }.forEach { db.smartViewDao().upsert(it.toEntity()) }
    }
    if (mode == ImportMode.REPLACE) {   // merge never adopts another device's configuration (P§2.2)
        env.notificationSettings?.let { db.notificationSettingsDao().upsert(it.toEntity()) }
        env.archiveSettings?.let { db.archiveSettingsDao().upsert(it.toEntity()) }
        env.appPreferences?.let { db.appPreferencesDao().upsert(it.toEntity()) }
    }
    return ImportSummary(imported, import.skipped)
}
```

Match `taskStore.replaceAll` / `insertMerged` / `all` to the *actual* existing TaskStore entry points the settings import already calls (read `SettingsScreen.kt:240-270` and reuse those exact functions — do not invent parallel paths; if today's code inlines the calls, lift them in, unchanged). Merge collision policy for tombstone stores is **skip** (existing wins) — never remap (P§2.2). None of the six non-task stores touch the sync queue.

- [x] **Step 4: Wire the app** — `AppContainer` constructs both; `SettingsScreen.kt:78` becomes `stream.write(Interchange.export(container.backupAssembler.assemble(), Instant.now()).toByteArray())`; `:246` becomes `container.importApplier.apply(Interchange.parse(raw), chosenMode)` and reports `"Imported N, skipped M"` from `ImportSummary`.

- [x] **Step 5: Run** — `./gradlew :core:store:connectedAndroidTest testDebugUnitTest :app:assembleDebug` → all green.

- [x] **Step 6: Commit** — `feat(backup): assemble and apply the seven-store envelope end to end`

---

### Task 7: Trash surfaces — undo = restore, Trash screen, purge scheduling (`:app`)

**Files:**
- Create: `app/src/main/kotlin/dev/vinny/gsd/browse/TrashScreen.kt`
- Modify: `app/src/main/kotlin/dev/vinny/gsd/browse/BrowseScreen.kt` (Trash row under Archive)
- Modify: `app/src/main/kotlin/dev/vinny/gsd/matrix/MatrixViewModel.kt` (undo path)
- Modify: `app/src/main/kotlin/dev/vinny/gsd/nav/GsdRoute.kt`, `nav/Destinations.kt`, `nav/GsdNavHost.kt` (`gsd://trash`)
- Modify: `app/src/main/kotlin/dev/vinny/gsd/notifications/MaintenanceWorker.kt`, `GsdApp.kt` (purge on sweep + launch)
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/kotlin/dev/vinny/gsd/nav/GsdRouteTest.kt` (extend), `app/src/androidTest/kotlin/dev/vinny/gsd/browse/TrashScreenTest.kt` (create)

**Interfaces:**
- Consumes: `TrashStore` (Task 5) exactly as declared there; the existing undo-snackbar plumbing in `MatrixViewModel` (find the `delete` call and its snapshot-undo lambda); the `BrowseScreen` Archive-row pattern; `GsdRoute` sealed routes.
- Produces: route `GsdRoute.Trash` ← `gsd://trash`; no new store surface.

- [x] **Step 1: Failing route test** — extend `GsdRouteTest` with `gsd://trash` → `GsdRoute.Trash` (copy the existing archive-route case). Run `./gradlew :app:testDebugUnitTest --tests '*GsdRouteTest*'` → FAIL.

- [x] **Step 2: Add the route** — `GsdRoute.Trash` object, parser case for host `trash`, nav destination rendering `TrashScreen`. Test → PASS.

- [x] **Step 3: Rewire undo** — in `MatrixViewModel` (and any other delete-with-undo call site — grep `UndoDelete`/snackbar usages): keep the snackbar exactly as it is (`Deleted "<title>"`, ~6 s / ~12 s TalkBack), but the Undo action now calls `trashStore.restore(id)` instead of re-creating from the held snapshot. The held snapshot shrinks to `(id, title)` for the message. Update `UndoDeleteTest` to assert the restored task came from the trash (delete → undo → task active again **and** trash empty).

- [x] **Step 4: Trash screen** — follow `ArchiveScreen.kt` as the template (dimmed read-only rows, leading-swipe Restore, trailing-swipe destructive with confirm, multi-select bulk bar, toolbar menu). Differences only: rows come from `trashStore.observeTrash()`; each row's metadata line shows retention remaining — `"29 days left"` in `ink3`, `"Last day"` in `alert` — computed from `deletedAt + 30 days` against the clock; trailing swipe = **Delete forever** (confirm: existing `archive_delete_confirm`-style string "This can't be undone."); toolbar menu = **Empty trash** (confirmed) instead of the auto-archive controls; bulk bar = Restore / Delete forever. Browse gains the row under Archive: icon `delete_outline` (add the Material Symbols glyph the same way Archive's `inventory_2` is wired), label "Trash", live count from `observeTrash()`. New strings (`strings.xml`): `trash_title`, `trash_row_label`, `trash_days_left`, `trash_last_day`, `trash_empty_state` ("Trash is empty"), `trash_empty_action` ("Empty trash"), `trash_empty_confirm`, `trash_restore`, `trash_delete_forever`.

- [x] **Step 5: Purge scheduling** — `MaintenanceWorker.doWork()` adds `container.trashStore.purgeExpired()` unconditionally (before the archive sweep, no settings gate — retention is not user-configurable); `GsdApp` launch path calls the same where the launch archive sweep runs.

- [x] **Step 6: Compose test** — `TrashScreenTest`: seed one trashed task; assert row + days-left text render; Restore returns it to active; Empty trash (through the confirm) leaves the empty state visible. Mirror `ArchiveScreenTest`'s scaffolding.

- [x] **Step 7: Run** — `./gradlew :app:testDebugUnitTest :app:connectedAndroidTest` → green.

- [x] **Step 8: Commit** — `feat(app): trash surface with undo-as-restore and retention purge`

---

### Task 8: Sync tombstone suppression (`:core:sync`)

**Files:**
- Modify: `core/sync/src/main/kotlin/dev/vinny/gsd/sync/SyncPorts.kt` (extend `SyncLocalStore`)
- Modify: `core/sync/src/main/kotlin/dev/vinny/gsd/sync/SyncEngine.kt` (pull-apply guard)
- Modify: `core/sync/src/main/kotlin/dev/vinny/gsd/sync/Sse.kt` (SSE-apply guard — same rule, one shared function)
- Modify: `core/sync/src/main/kotlin/dev/vinny/gsd/sync/RoomSyncLocalStore.kt` (implement the port)
- Test: `core/sync/src/test/kotlin/dev/vinny/gsd/sync/SyncEngineTest.kt` + `SseTest.kt` (extend; the suites' fake `SyncLocalStore` gains the new members)

**Interfaces:**
- Consumes: the existing `SyncLocalStore` port (SyncPorts.kt:32) and both apply paths' LWW upsert flow; `ArchivedTaskDao.byId`/`deleteById`, `DeletedTaskDao.byId`/`deleteById` (Task 5).
- Produces — port additions every fake and the Room implementation must provide:

```kotlin
interface SyncLocalStore {
    // …existing members…
    /** archivedAt/deletedAt in epoch millis, or null when the id is not tombstoned. */
    suspend fun tombstoneAtMs(id: String): Long?
    /** Remove the id from whichever tombstone store holds it (archive or trash). */
    suspend fun removeTombstone(id: String)
}
```

- [x] **Step 1: Failing engine tests** (extend `SyncEngineTest`'s fake store with an in-memory `tombstones: MutableMap<String, Long>`; the fake's `removeTombstone` deletes the entry)

```kotlin
@Test fun `pull drops a record whose tombstone is not strictly older`() {
    // tombstoneAtMs("t1") == remote client_updated_at  → equal: drop, fail closed
    // assert: no upsertFromRemote for t1, tombstone still present
}

@Test fun `pull resurrects when the remote edit is strictly newer than the tombstone`() {
    // tombstone at T, remote client_updated_at T+1 → removeTombstone("t1") called once,
    // then the normal LWW upsert applied
}

@Test fun `unparseable remote timestamp never resurrects`() {
    // record with client_updated_at "garbage" and a tombstone present → dropped entirely
}
```

Mirror the same three cases in `SseTest` for `tasks` create/update events (the SSE path must call the *same* guard function). Run `./gradlew :core:sync:test` → FAIL (port members missing).

- [x] **Step 2: Implement the guard once, call it from both paths**

```kotlin
/**
 * The web's isRemoteNewerThanArchive rule (parity spec P§4), applied to both
 * tombstone stores: only a remote edit STRICTLY newer than the local
 * archive/trash moment may resurrect a task; equal, older, or unparseable
 * never does — the tombstone stands.
 */
internal suspend fun resolveTombstone(local: SyncLocalStore, taskId: String, remoteUpdatedAtMs: Long?): Boolean {
    val tombstoneMs = local.tombstoneAtMs(taskId) ?: return true          // no tombstone: apply normally
    if (remoteUpdatedAtMs == null) return false                            // fail closed
    if (remoteUpdatedAtMs <= tombstoneMs) return false                     // suppressed
    local.removeTombstone(taskId)                                          // deliberate resurrection
    return true
}
```

In `SyncEngine`'s pull-apply loop, before the existing LWW upsert of each pulled record: `if (!resolveTombstone(local, task.id, remoteMs)) { continue-with-next-record }` — where `remoteMs` is the already-parsed `client_updated_at` millis the LWW comparison uses (`null` when unparseable; the existing skip-malformed handling stays first). In the SSE apply path (create/update actions, after the owner fail-close and echo filter, before the LWW upsert): same call, drop the event when false. SSE **delete** events and deletion reconciliation are untouched (P§4). `RoomSyncLocalStore`: implement the two members against both DAOs (`archivedTaskDao().byId(id)?.archivedAt ?: deletedTaskDao().byId(id)?.deletedAt`; remove from whichever holds it).

- [x] **Step 3: Run** — `./gradlew :core:sync:test` → PASS; full `./gradlew testDebugUnitTest` green.

- [x] **Step 4: Commit** — `feat(sync): archive and trash tombstones suppress pulled resurrections`

---

### Task 9: Feedback contract (`:core:model`)

**Files:**
- Create: `core/model/src/main/kotlin/dev/vinny/gsd/model/Feedback.kt`
- Test: `core/model/src/test/kotlin/dev/vinny/gsd/model/FeedbackTest.kt` (create)

**Interfaces:**
- Consumes: `NanoId.generate()` (draft submission ids — generated by callers, injected here).
- Produces (used by Task 10):

```kotlin
enum class Sentiment(val wire: String) { UP("up"), DOWN("down") }
enum class FeedbackCategory(val wire: String) { IDEA("idea"), PRAISE("praise"), GRIPE("gripe"), BUG("bug") }
data class RoadmapItem(val slug: String, val label: String, val description: String)
object Roadmap { val items: List<RoadmapItem>; fun isSlug(slug: String): Boolean }
data class FeedbackDraft(
    val sentiment: Sentiment? = null, val category: FeedbackCategory? = null,
    val message: String = "", val votes: List<String> = emptyList(),
    val submissionId: String,                      // stable for the draft's lifetime
) { val isEmpty: Boolean }
object FeedbackPayload {
    const val MAX_MESSAGE_LENGTH = 1000
    val FIELDS: List<String>                       // the pinned 7, in wire order
    /** Pure: everything environmental is injected; the preview and the request body are THIS object. */
    fun build(draft: FeedbackDraft, appVersion: String, submittedAt: Instant): JsonObject
}
```

- [x] **Step 1: Failing tests**

```kotlin
class FeedbackTest {
    private fun draft(vararg votes: String) = FeedbackDraft(
        sentiment = Sentiment.UP, category = FeedbackCategory.IDEA,
        message = "  more focus  ", votes = votes.toList(), submissionId = "sub-1",
    )
    private val at = Instant.parse("2026-08-29T12:00:00.000Z")

    @Test fun `payload contains exactly the seven pinned keys and nothing else`() {
        val payload = FeedbackPayload.build(draft(), appVersion = "android-0.8.0.10", submittedAt = at)
        assertEquals(
            listOf("submission_id", "sentiment", "category", "message", "votes", "app_version", "client_submitted_at"),
            payload.keys.toList(),
        )   // adding a field without updating the disclosure must FAIL here (P§6.1)
    }

    @Test fun `nulls encode as empty strings, message trims, votes dedupe and filter to shipped slugs`() {
        val payload = FeedbackPayload.build(
            FeedbackDraft(message = "hi", votes = listOf("wear-os", "wear-os", "ios-widgets", "bogus"), submissionId = "s"),
            "android-0.8.0.10", at,
        )
        assertEquals("", payload.getValue("sentiment").jsonPrimitive.content)
        assertEquals("", payload.getValue("category").jsonPrimitive.content)
        assertEquals(listOf("wear-os"), payload.getValue("votes").jsonArray.map { it.jsonPrimitive.content })
    }

    @Test fun `app version clamps to 20 chars`() {
        val payload = FeedbackPayload.build(draft(), "android-0.8.0-rc.1.veryverylong", at)
        assertEquals(20, payload.getValue("app_version").jsonPrimitive.content.length)
    }

    @Test fun `android ships the seven shared slugs plus wear-os`() {
        assertEquals(
            listOf("natural-language-dates", "calendar-sync", "task-templates", "weekly-review",
                   "focus-timer", "task-notes", "shared-lists", "wear-os"),
            Roadmap.items.map { it.slug },
        )   // per-client curation, P§6.1: never reuse a retired slug (ios-widgets, apple-watch are other clients')
    }

    @Test fun `draft emptiness gates send`() {
        assertTrue(FeedbackDraft(submissionId = "s").isEmpty)
        assertTrue(FeedbackDraft(message = "   ", submissionId = "s").isEmpty)
        assertFalse(FeedbackDraft(votes = listOf("wear-os"), submissionId = "s").isEmpty)
    }
}
```

- [x] **Step 2: Run** → FAIL (file absent). **Step 3: Implement** — `build` assembles the `JsonObject` in `FIELDS` order: `submission_id` = `draft.submissionId`, `sentiment`/`category` = wire value or `""`, `message` = `draft.message.trim().take(MAX_MESSAGE_LENGTH)`, `votes` = `draft.votes.distinct().filter(Roadmap::isSlug)`, `app_version` = `appVersion.take(20)`, `client_submitted_at` = the `iso(submittedAt)` formatter Interchange uses (extract it to a shared internal if needed). `Roadmap.items` carries the eight entries above with user-facing labels/descriptions adapted from the web's copy (`Roadmap` labels live in the model deliberately — the corpus of items *is* contract-ish data, not UI chrome; the wear-os entry: label "Wear OS app", description "Your Do First quadrant on your watch."). `isEmpty` = no sentiment, no category, blank trimmed message, no votes.

- [x] **Step 4: Run** `./gradlew :core:model:test` → PASS (and still sub-second). **Step 5: Commit** — `feat(model): the seven-key anonymous feedback payload`

---

### Task 10: Feedback submission + Settings surface (`:core:sync` + `:app`)

**Files:**
- Create: `core/sync/src/main/kotlin/dev/vinny/gsd/sync/FeedbackApi.kt`
- Test: `core/sync/src/test/kotlin/dev/vinny/gsd/sync/FeedbackApiTest.kt` (create; MockWebServer, like `PocketBaseApiTest`)
- Create: `app/src/main/kotlin/dev/vinny/gsd/feedback/FeedbackScreen.kt`, `app/src/main/kotlin/dev/vinny/gsd/feedback/FeedbackViewModel.kt`
- Modify: `app/src/main/kotlin/dev/vinny/gsd/prefs/GsdPrefs.kt` (draft persistence), `settings/SettingsScreen.kt` (Feedback row), `nav/*` (route), `AppContainer.kt`, `strings.xml`

**Interfaces:**
- Consumes: `FeedbackPayload.build`, `FeedbackDraft`, `Roadmap` (Task 9); `NanoId.generate()`; the app's OkHttp client from `:core:sync`; `BuildConfig.VERSION_NAME`/`VERSION_CODE` (Settings already reads BuildConfig).
- Produces:

```kotlin
class FeedbackApi(private val client: OkHttpClient, private val baseUrl: String = "https://api.vinny.io") {
    sealed interface Result { data object Sent : Result; data class Failed(val message: String) : Result }
    /** POST /api/collections/feedback/records — no auth header, ever (P§6.1). */
    suspend fun submit(payload: JsonObject): Result
}
```

- [x] **Step 1: Failing API tests** (MockWebServer)

```kotlin
@Test fun `submit posts JSON to the feedback collection with no auth header`() {
    server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
    val result = api.submit(payload)
    val recorded = server.takeRequest()
    assertEquals("/api/collections/feedback/records", recorded.path)
    assertEquals("application/json; charset=utf-8", recorded.getHeader("Content-Type"))
    assertNull(recorded.getHeader("Authorization"))          // anonymity is the contract
    assertEquals(FeedbackApi.Result.Sent, result)
}

@Test fun `a duplicate submission id collapses to success`() {
    server.enqueue(MockResponse().setResponseCode(400)
        .setBody("""{"data":{"submission_id":{"code":"validation_not_unique","message":"..."}}}"""))
    assertEquals(FeedbackApi.Result.Sent, api.submit(payload))   // earlier attempt landed (P§6.1)
}

@Test fun `other failures keep the draft`() {
    server.enqueue(MockResponse().setResponseCode(500))
    assertTrue(api.submit(payload) is FeedbackApi.Result.Failed)
}
```

- [x] **Step 2: Run** `./gradlew :core:sync:test --tests '*FeedbackApiTest*'` → FAIL. **Step 3: Implement** — plain OkHttp POST, body `payload.toString()`, media type `application/json`; 2xx → `Sent`; 400 whose parsed body has `data.submission_id.code == "validation_not_unique"` → `Sent` (the structured check both siblings ship — not a substring scan); anything else (including IOException) → `Failed(human message)`. No PocketBase auth plumbing anywhere near it. Tests → PASS.

- [x] **Step 4: Draft persistence** — `GsdPrefs` gains the draft fields (sentiment/category wire strings, message, votes CSV or JSON, submissionId), following the file's existing key pattern. A fresh draft mints `NanoId.generate()` once; the id survives process death and retries; success clears every field. Unit-test the round-trip if `GsdPrefs` has an existing test pattern; otherwise the ViewModel test covers it.

- [x] **Step 5: The screen** — `FeedbackScreen` reached from a new Settings row ("Feedback", between Data & Storage and About). Sections: sentiment (two toggle pills: 👍/👎 with labels), category chips (Idea/Praise/Gripe/Bug), message field (≤1000, count shown near the cap), "Vote for what's next" checklist from `Roadmap.items` (label + description, ≥48dp rows), and — non-negotiable (P§6.2) — a **"What gets sent"** disclosure: a monospace block rendering `FeedbackPayload.build(draft, appVersion, now)` pretty-printed, plus one `ink2` sentence ("No account, no device id, no task content."). Send button disabled while `draft.isEmpty`; on `Sent` clear the draft and show the calm confirmation ("Thanks — sent."); on `Failed` keep everything and surface retry. `appVersion` = `"android-${BuildConfig.VERSION_NAME}.${BuildConfig.VERSION_CODE}"`. All copy in `strings.xml` (`feedback_title`, `feedback_sentiment_*`, `feedback_category_*`, `feedback_message_hint`, `feedback_votes_header`, `feedback_disclosure_*`, `feedback_send`, `feedback_sent`, `feedback_failed`).

- [x] **Step 6: ViewModel test** (JVM, like `PaletteIndexTest`) — drives draft edits, asserts the preview object == the object handed to a fake `FeedbackApi`, asserts clear-on-success and keep-on-failure.

- [x] **Step 7: Run** — `./gradlew testDebugUnitTest :app:assembleDebug` green; screen smoke via `:app:connectedAndroidTest` if a Compose test is added (optional here — the contract lives in the JVM tests).

- [x] **Step 8: Commit** — `feat(app): anonymous opt-in feedback with payload disclosure`

---

### Task 11: Two-tier Apple sign-in note (`:app`)

**Files:**
- Modify: `app/src/main/kotlin/dev/vinny/gsd/settings/AccountGroup.kt` (note logic, AccountGroup.kt:72)
- Modify: `app/src/main/kotlin/dev/vinny/gsd/prefs/GsdPrefs.kt` + the auth-success path in `AccountGroup.launchProvider`/`AuthManager` callback (persist the provider)
- Modify: `app/src/main/res/values/strings.xml`
- Test: extend the existing settings/account test surface (or `GsdPrefs` unit test) for the tier selection

**Interfaces:**
- Consumes: the sign-in flow at AccountGroup.kt:43-50 (knows `provider` at launch), the relay check at :72.
- Produces: `GsdPrefs.lastAuthProvider: String?` persisted on successful token exchange; a pure helper for the copy tier:

```kotlin
enum class AccountNote { RELAY_SEPARATE_ACCOUNT, APPLE_MAY_DIFFER }
fun accountNote(provider: String?, email: String?): AccountNote? = when {
    email != null && email.endsWith("@privaterelay.appleid.com") -> AccountNote.RELAY_SEPARATE_ACCOUNT
    provider == "apple" -> AccountNote.APPLE_MAY_DIFFER
    else -> null
}
```

- [x] **Step 1: Failing unit test** for `accountNote` — relay email (any provider) → `RELAY_SEPARATE_ACCOUNT`; provider `apple` + normal email → `APPLE_MAY_DIFFER`; `google`/`github` + normal email → null; relay wins over provider. Put it wherever pure app-layer helpers are tested (`app/src/test/`).

- [x] **Step 2: Implement** — persist the provider on successful exchange (the `launchProvider(provider)` string, written alongside the token save); replace the single relay check at AccountGroup.kt:72 with `accountNote(...)` and render per tier, iOS copy verbatim (P§7, typographic apostrophes):
  - `account_note_relay`: "Signed in with a private relay email — this is a separate account from your web tasks."
  - `account_note_apple`: "Signed in with Apple. If you use Google or GitHub on the web, that’s a different account and won’t show the same tasks." (U+2019 apostrophes, byte-faithful to iOS `SettingsView.swift:243` — copy, never re-type)
  A pre-existing session with no persisted provider degrades to the relay-only behavior (provider `null`) — correct and honest.

- [x] **Step 3: Run** `./gradlew :app:testDebugUnitTest` → PASS. **Step 4: Commit** — `feat(settings): warn on every Apple sign-in, not only private relay`

---

### Task 12: Fold the amendments into the spec + handoff

**Files:**
- Modify: `docs/gsd-android-spec.md` (the P§8 amendment list), `README.md` (status), `CLAUDE.md` (plan pointer), `tasks/todo.md` ("Resuming From Here")
- Delete marker: none — `tasks/parity-plan.md` stays (checkboxes are the progress record)

- [x] **Step 1: Amend `docs/gsd-android-spec.md`** per parity-spec P§8, keeping its voice: §4.4+§8.16 (envelope 2.1.0 — replace the v1 envelope paragraph with the P§2.1 table, three-array cap, absent≠empty); §7.10 (add `deleted_tasks`, "Room v2"); §8.20 (retitle "Undo delete & Trash" — undo restores from the 30-day trash; add the Trash surface + purge rules from P§3); §9.4/§9.6 (add the tombstone-suppression paragraph from P§4); §8.2+Appendix C (corpus supersedes; nine decisions; fixture path); §8.17 (Feedback row; two-tier Apple note); §10.4 (auto-linking correction from P§7); §6.4 (`gsd://trash` row); Appendix B (feedback limits row; import-cap wording "counts tasks + archivedTasks + deletedTasks"). Cite `docs/2026-08-29-cross-platform-parity-spec.md` once at the top of each amended section (`> Amended 2026-08-29 — see the parity spec`).
- [x] **Step 2: README** — status paragraph gains one sentence: parity catch-up (envelope 2.1.0, trash, corpus parser, feedback) landed 2026-08/09; fixtures shared with the sibling repos.
- [ ] **Step 3: `CLAUDE.md`** — the Plan line becomes: `- **Plan:** tasks/plan.md (phases 0–7, complete) · tasks/parity-plan.md (Aug 2026 parity catch-up) · progress in tasks/todo.md`. **Deliberately skipped** — the controller ruled this out for Task 12: `CLAUDE.md` carries a pre-existing uncommitted user edit and must not be staged or altered. See the Task 12 report's Deviations section.
- [x] **Step 4: `tasks/todo.md`** — new "Parity catch-up (2026-08-29)" section listing the twelve tasks' done/remaining state and a fresh "Resuming From Here".
- [x] **Step 5: Full verification sweep** — `./gradlew :core:model:test testDebugUnitTest connectedAndroidTest :app:assembleRelease` all green; `:core:model:test` still sub-second.
- [x] **Step 6: Commit** — `docs: fold the August 2026 parity amendments into the spec`

---

## Self-review notes (already applied)

- **Spec coverage:** P§2 → Tasks 2–4, 6 · P§3 → Tasks 5, 7 · P§4 → Task 8 · P§5 → Task 1 · P§6 → Tasks 9–10 · P§7 → Task 11 · P§8 → Task 12 · P§1 (verified parity) → deliberately no tasks.
- **Ordering constraints:** Task 5 must precede 6, 7, 8 (they consume `TrashStore`/`DeletedTaskDao`); Task 2 precedes 3, 4, 6; Task 9 precedes 10. Tasks 1 and 11 are order-free. Suggested execution order is as numbered.
- **Type consistency spot-checks:** `BackupEnvelope`/`BackupImport` names match across Tasks 2/3/4/6; `TrashStore.restore(id): Task?` matches Task 7's undo usage; `tombstoneAtMs`/`removeTombstone` match Task 8's fake-store note; `FeedbackDraft.submissionId` (Task 9) is what Task 10 persists.
- **Known judgment calls carried from the spec:** replace-mode task tombstones stay hard deletes (Task 5, Step 8); `Roadmap` lives in `:core:model` (Task 9); export always emits all seven keys, import distinguishes absent from empty (Tasks 2/3/6).




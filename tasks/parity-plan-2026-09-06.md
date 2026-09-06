# GSD Android — Cross-Platform Parity Implementation Plan (September 2026 round)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring gsd-android level with what the web and iOS clients shipped after the 2026-08-29 parity round: the iOS #16 dashboard/browse/skeleton set, the off-list reminder rule, the web #527 contract hardening (smart-view bounds, bounded scans, three-verdict reconciliation, malformed-version guard, route-first account deletion, erase completeness), and the small feedback-surface gaps.

**Architecture:** Every rule comes from `docs/2026-09-06-cross-platform-parity-spec.md` (cited below as *S§n*), which wins over `docs/gsd-android-spec.md` until Task 15 folds the amendments in. Work flows along the existing module boundary: pure rules and contracts in `:core:model` (JVM tests, sub-second), persistence in `:core:store` (Room, instrumented), sync rules in `:core:sync` (JVM tests over the existing `FakeLocal`/`FakeRemote`), surfaces in `:app` (Compose, instrumented). Pure logic is pushed down so the app layer stays a thin render.

**Tech Stack:** unchanged — Kotlin 2.4 · AGP 9.3.2 (the uncommitted bump in `gradle/libs.versions.toml`; commit it in Task 15 if still pending) · Compose BOM 2026.06 · Room 2.8.4 (KSP2) · kotlinx.serialization · OkHttp + MockWebServer (`:core:sync`) · JUnit4 + kotlin-test on JVM · Compose UI test on device.

**Spec:** `docs/2026-09-06-cross-platform-parity-spec.md`. The original product spec `docs/gsd-android-spec.md` and the August parity spec stay authoritative for everything this one does not touch.

## Global Constraints

- **Day-based date rules, one clock.** Every dashboard rule derives from the single `now`/`zone` passed to `Analytics.compute`; `DeadlineStatus` compares `LocalDate`s in that zone (S§2.1). Never call `Instant.now()` inside a composable to classify a date.
- **Reduced motion gate** is `android.animation.ValueAnimator.areAnimatorsEnabled()`, the check `ConfettiOverlay` already uses; when false: no ring grow-in, no shimmer (S§2.2, S§4.2).
- **Skeleton semantics:** shown only while the task state is `null` (no snapshot yet); no timer, no minimum display time; one semantics node labelled "Loading" (S§4.1–4.2).
- **Search:** trimmed, lowercased substring over title/description/tags/subtask titles, no tokenization, no diacritic folding, no cap; Browse search is active-only (S§3.2).
- **List order** everywhere a filtered list renders: `TaskOrdering.forList` (S§3.3).
- **Reminder presets stay the superset** `0, 5, 15, 30, 60, 120, 1440`; an off-list stored value is rendered, never snapped (S§5).
- **Smart-view limits** (S§7.1): 100 views per import/export, id 1–128, name 1–80, icon ≤ 64, searchQuery ≤ 600, ≤ 200 filter tags each 1–30, ≤ 4 quadrants, ≤ 4 recurrences, ≤ 5 pinned ids each 1–128. Skips are counted, never fatal.
- **Remote listings fail closed** at `MAX_REMOTE_TASKS = 10_000`; a partial or inconsistent enumeration never drives deletion (S§8.1). A missing or malformed remote `client_updated_at` never authorizes overwrite (S§8.2).
- **Account deletion:** `DELETE /api/gsd/account` first; only a 404 falls back; the user record is deleted only after a complete empty listing (S§8.4).
- **Feedback failures** map IOException / 429 / ≥500 / other → OFFLINE / RATE_LIMITED / SERVER / REJECTED; the duplicate-400 stays success; the draft and submission id are always kept on failure (S§6.2).
- **Copy:** every user-visible string lives in `strings.xml`; product copy is quoted verbatim from the spec, including its em dashes.
- **TDD red/green/refactor per task; `./gradlew :core:model:test` stays sub-second** (it is the gate). Conventional commits via the `creating-git-commits` skill, one commit per task, `Claude-Session:` trailer only. Stage files explicitly — the working tree carries an unrelated uncommitted `CLAUDE.md` edit and the AGP bump; never `git add -A`.
- **Never let persisted `quadrant` drift from the flags** — unchanged from August.

## File Structure (locked in by this plan)

```
core/model/src/main/kotlin/dev/vinny/gsd/model/
├── Analytics.kt              # MODIFY: QuadrantStat, quadrantStats, percent, day-based upcoming (Task 1)
├── DeadlineStatus.kt         # CREATE: OVERDUE / TODAY / UPCOMING classifier (Task 1)
├── TaskOrdering.kt           # CREATE: forList(tasks, status) (Task 2)
├── FilterCriteria.kt         # MODIFY: trim searchQuery (Task 2)
├── ReminderOptions.kt        # CREATE: including(stored) + ReminderLabel.of(minutes) (Task 3)
├── SmartViewLimits.kt        # CREATE: the S§7.1 constants + violation(view) (Task 4)
├── Interchange.kt            # MODIFY: bounded smart-view import, skip counting, export clip (Task 4)
core/store/src/main/kotlin/dev/vinny/gsd/store/
├── ImportApplier.kt          # MODIFY: 100-cap over new ids, pinned truncation (Task 4)
├── BackupAssembler.kt        # MODIFY: export clip (Task 4)
core/sync/src/main/kotlin/dev/vinny/gsd/sync/
├── BoundedListing.kt         # CREATE: RemoteListingIncomplete + collect(...) (Task 5)
├── PocketBaseApi.kt          # MODIFY: totalItems, sort tie-break, deleteAccountViaRoute (Tasks 5, 8)
├── SyncPorts.kt              # MODIFY: TaskListPage.totalItems, abandonToTrash, AccountRemote (Tasks 5, 7, 8)
├── SyncEngine.kt             # MODIFY: bounded walks, malformed-version guard, three verdicts (Tasks 5–7)
├── RoomSyncLocalStore.kt     # MODIFY: abandonToTrash (Task 7)
├── AccountDeleter.kt         # CREATE: route-first deletion with bounded fallback (Task 8)
├── FeedbackApi.kt            # MODIFY: Failure kinds (Task 9)
app/src/main/kotlin/dev/vinny/gsd/
├── ui/Skeleton.kt            # CREATE: SkeletonBlock, Modifier.skeletonShimmer (Task 11)
├── ui/StatusBadge.kt         # CREATE: the two-style capsule (Task 11)
├── dashboard/DashboardSkeleton.kt        # CREATE (Task 11)
├── dashboard/QuadrantCompletionRings.kt  # CREATE (Task 12)
├── dashboard/DashboardScreen.kt          # MODIFY: null gate, rings, upcoming rows, editor host (Tasks 11, 12)
├── browse/TaskListSkeleton.kt            # CREATE (Task 11)
├── browse/BrowseViewModel.kt             # MODIFY: tasks: StateFlow<List<Task>?> (Task 11)
├── browse/SmartViewListScreen.kt         # MODIFY: skeleton gate, ordering, empty copy (Tasks 11, 13)
├── browse/BrowseScreen.kt                # MODIFY: root search + results (Task 13)
├── matrix/MatrixSkeleton.kt              # CREATE (Task 11)
├── matrix/MatrixViewModel.kt             # MODIFY: sections nullable (Task 11)
├── matrix/MatrixScreen.kt                # MODIFY: skeleton gate, SEND_FEEDBACK dispatch (Tasks 10, 11)
├── editor/EditorDepthSections.kt         # MODIFY: off-list reminder rendering (Task 14)
├── palette/PaletteIndex.kt               # MODIFY: SEND_FEEDBACK (Task 10)
├── feedback/FeedbackViewModel.kt, FeedbackScreen.kt   # MODIFY: failure kinds + copy (Task 9)
├── settings/SettingsScreen.kt, AccountGroup.kt        # MODIFY: erase clears draft; deletion outcomes (Tasks 8, 9)
├── sync/SyncCoordinator.kt               # MODIFY: delegate deleteAccount to AccountDeleter (Task 8)
app/src/main/res/values/strings.xml       # MODIFY: every new string + plurals (Tasks 9–14)
```

---

### Task 1: Analytics — `QuadrantStat`, `DeadlineStatus`, rounded percent, day-based upcoming (`:core:model`)

**Files:**
- Create: `core/model/src/main/kotlin/dev/vinny/gsd/model/DeadlineStatus.kt`
- Modify: `core/model/src/main/kotlin/dev/vinny/gsd/model/Analytics.kt`
- Test: `core/model/src/test/kotlin/dev/vinny/gsd/model/DeadlineStatusTest.kt` (create), `core/model/src/test/kotlin/dev/vinny/gsd/model/AnalyticsTest.kt` (modify)

**Interfaces:**
- Consumes: `Task`, `Quadrant.entries` (Q1→Q4 declaration order), existing `DashboardStats`.
- Produces: `enum class DeadlineStatus { OVERDUE, TODAY, UPCOMING }` with `fun of(due: Instant, now: Instant, zone: ZoneId): DeadlineStatus`; `data class QuadrantStat(quadrant: Quadrant, total: Int, completed: Int)` with `val active: Int`, `val completionRate: Float`; `DashboardStats.quadrantStats: List<QuadrantStat>`; `Analytics.percent(rate: Float): String`. Task 12 renders all of them.

- [ ] **Step 1: Write the failing `DeadlineStatus` tests** (iOS's four cases, S§11.1)

```kotlin
package dev.vinny.gsd.model

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class DeadlineStatusTest {
    private val zone: ZoneId = ZoneId.of("America/Chicago")
    private fun june(day: Int, hour: Int): Instant =
        LocalDateTime.of(2026, 6, day, hour, 0).atZone(zone).toInstant()

    @Test
    fun `due yesterday is overdue`() =
        assertEquals(DeadlineStatus.OVERDUE, DeadlineStatus.of(june(2, 12), june(3, 9), zone))

    @Test
    fun `due earlier today is today, not overdue`() =
        assertEquals(DeadlineStatus.TODAY, DeadlineStatus.of(june(3, 0), june(3, 9), zone))

    @Test
    fun `due later today is today`() =
        assertEquals(DeadlineStatus.TODAY, DeadlineStatus.of(june(3, 23), june(3, 9), zone))

    @Test
    fun `due tomorrow is upcoming even at 23h`() =
        assertEquals(DeadlineStatus.UPCOMING, DeadlineStatus.of(june(4, 0), june(3, 23), zone))
}
```

- [ ] **Step 2: Run it to confirm it fails for the right reason**

Run: `./gradlew :core:model:test --tests 'dev.vinny.gsd.model.DeadlineStatusTest'`
Expected: compilation failure — `Unresolved reference: DeadlineStatus`.

- [ ] **Step 3: Implement `DeadlineStatus`**

```kotlin
package dev.vinny.gsd.model

import java.time.Instant
import java.time.ZoneId

/**
 * Where a due date sits relative to today (S§2.1). Day-based in the injected zone so it
 * agrees with `Analytics.overdueCount`, `FilterCriteria.overdue`, and `TaskCard.dueLabel`:
 * only dates before the START of today are overdue; a task due today is TODAY, never overdue.
 */
enum class DeadlineStatus {
    OVERDUE, TODAY, UPCOMING;

    companion object {
        fun of(due: Instant, now: Instant, zone: ZoneId): DeadlineStatus {
            val dueDay = due.atZone(zone).toLocalDate()
            val today = now.atZone(zone).toLocalDate()
            return when {
                dueDay.isBefore(today) -> OVERDUE
                dueDay == today -> TODAY
                else -> UPCOMING
            }
        }
    }
}
```

- [ ] **Step 4: Run the four tests — expect PASS**

Run: `./gradlew :core:model:test --tests 'dev.vinny.gsd.model.DeadlineStatusTest'`

- [ ] **Step 5: Write the failing `AnalyticsTest` cases** (append inside the existing class; its `task(...)` helper and `now`/`zone` are reused)

```kotlin
    @Test
    fun `quadrant stats are always four, Q1 to Q4, completed over total, all time`() {
        val stats = Analytics.compute(
            listOf(
                task(urgent = true, important = true),
                task(urgent = true, important = true, completed = true, completedAt = now),
                task(urgent = false, important = true, completed = true, completedAt = now.minusSeconds(40L * 86_400)),
            ),
            windowDays = 7, now = now, zone = zone,
        )
        assertEquals(Quadrant.entries.toList(), stats.quadrantStats.map { it.quadrant })
        val q1 = stats.quadrantStats[0]
        assertEquals(2, q1.total); assertEquals(1, q1.completed); assertEquals(1, q1.active)
        assertEquals(0.5f, q1.completionRate)
        val q2 = stats.quadrantStats[1]
        assertEquals(1f, q2.completionRate) // a 40-day-old completion still counts: no window
        assertEquals(0f, stats.quadrantStats[3].completionRate) // empty quadrant → 0, not NaN
    }

    @Test
    fun `percent rounds half up and never truncates`() {
        assertEquals("68%", Analytics.percent(0.675f))
        assertEquals("67%", Analytics.percent(2f / 3f))
        assertEquals("0%", Analytics.percent(0f))
        assertEquals("100%", Analytics.percent(1f))
    }

    @Test
    fun `upcoming is day-based, includes a task due earlier today, and tie-breaks newest first`() {
        val zone = java.time.ZoneId.of("America/Chicago")
        val nine = java.time.LocalDateTime.of(2026, 6, 3, 9, 0).atZone(zone).toInstant()
        val midnight = java.time.LocalDateTime.of(2026, 6, 3, 0, 0).atZone(zone).toInstant()
        val tomorrow = java.time.LocalDateTime.of(2026, 6, 4, 12, 0).atZone(zone).toInstant()
        val yesterday = java.time.LocalDateTime.of(2026, 6, 2, 12, 0).atZone(zone).toInstant()
        val older = Task(title = "older", urgent = false, important = false, createdAt = nine.minusSeconds(600), updatedAt = nine, dueDate = tomorrow)
        val newer = Task(title = "newer", urgent = false, important = false, createdAt = nine.minusSeconds(60), updatedAt = nine, dueDate = tomorrow)
        val earlierToday = Task(title = "today", urgent = false, important = false, createdAt = nine, updatedAt = nine, dueDate = midnight)
        val late = Task(title = "late", urgent = false, important = false, createdAt = nine, updatedAt = nine, dueDate = yesterday)
        val stats = Analytics.compute(listOf(older, newer, earlierToday, late), 7, nine, zone)
        assertEquals(listOf("today", "newer", "older"), stats.upcoming.map { it.title })
        assertEquals(1, stats.overdueCount)
    }
```

- [ ] **Step 6: Run to confirm they fail** — `Unresolved reference: quadrantStats` / `percent`; the upcoming case fails on `[newer, older]` (today's task missing) once the others compile.

- [ ] **Step 7: Implement in `Analytics.kt`**

Add the type and field, the helper, and replace the `upcoming` computation:

```kotlin
/** Per-quadrant counts over the active table, all-time (S§2.1). `total` = active + completed. */
data class QuadrantStat(val quadrant: Quadrant, val total: Int, val completed: Int) {
    val active: Int get() = total - completed
    val completionRate: Float get() = if (total == 0) 0f else completed.toFloat() / total
}
```

`DashboardStats` gains `val quadrantStats: List<QuadrantStat>` (after `activeByQuadrant`). In `compute`:

```kotlin
        val upcoming = activeTasks
            .filter { task -> task.dueDate?.let { !it.atZone(zone).toLocalDate().isBefore(today) } == true }
            .sortedWith(compareBy<Task> { it.dueDate }.thenByDescending { it.createdAt })
            .take(5)
        // ...
            quadrantStats = Quadrant.entries.map { quadrant ->
                val inQuadrant = tasks.filter { it.quadrant == quadrant }
                QuadrantStat(quadrant, total = inQuadrant.size, completed = inQuadrant.count { it.completed })
            },
```

and on the `Analytics` object:

```kotlin
    /** "68%" — round half up; siblings round, they never truncate (S§2.1). */
    fun percent(rate: Float): String = "${(rate * 100f).roundToInt()}%"
```

(`import kotlin.math.roundToInt`.)

- [ ] **Step 8: Run the whole module suite — expect PASS and sub-second**

Run: `./gradlew :core:model:test`

- [ ] **Step 9: Commit**

```bash
git add core/model/src/main/kotlin/dev/vinny/gsd/model/DeadlineStatus.kt \
        core/model/src/main/kotlin/dev/vinny/gsd/model/Analytics.kt \
        core/model/src/test/kotlin/dev/vinny/gsd/model/DeadlineStatusTest.kt \
        core/model/src/test/kotlin/dev/vinny/gsd/model/AnalyticsTest.kt
git commit -m "feat(model): per-quadrant completion stats and a day-based deadline status"
```

---

### Task 2: `TaskOrdering.forList` and a trimmed search query (`:core:model`)

**Files:**
- Create: `core/model/src/main/kotlin/dev/vinny/gsd/model/TaskOrdering.kt`
- Modify: `core/model/src/main/kotlin/dev/vinny/gsd/model/FilterCriteria.kt:71-76`
- Test: `core/model/src/test/kotlin/dev/vinny/gsd/model/TaskOrderingTest.kt` (create), `core/model/src/test/kotlin/dev/vinny/gsd/model/FilterCriteriaTest.kt` (modify)

**Interfaces:**
- Consumes: `Task`, `FilterCriteria`, `FilterStatus` (the existing `status` enum on `FilterCriteria`: `ALL`, `ACTIVE`, `COMPLETED`).
- Produces: `object TaskOrdering { fun forList(tasks: List<Task>, status: FilterStatus): List<Task> }`. Task 13 uses it for Browse results and the smart-view list.

- [ ] **Step 1: Write the failing ordering tests**

```kotlin
package dev.vinny.gsd.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class TaskOrderingTest {
    private val t0 = Instant.parse("2026-07-19T12:00:00.000Z")
    private fun task(title: String, createdOffset: Long, due: Instant? = null, completedAt: Instant? = null) = Task(
        title = title, urgent = false, important = false, completed = completedAt != null,
        completedAt = completedAt, createdAt = t0.plusSeconds(createdOffset), updatedAt = t0, dueDate = due,
    )

    @Test
    fun `active lists sort due ascending, dated before undated, then newest first`() {
        val ordered = TaskOrdering.forList(
            listOf(
                task("undated-old", 0),
                task("due-later", 10, due = t0.plusSeconds(7_200)),
                task("undated-new", 20),
                task("due-soon-old", 30, due = t0.plusSeconds(3_600)),
                task("due-soon-new", 40, due = t0.plusSeconds(3_600)),
            ),
            status = FilterStatus.ACTIVE,
        )
        assertEquals(listOf("due-soon-new", "due-soon-old", "due-later", "undated-new", "undated-old"), ordered.map { it.title })
    }

    @Test
    fun `completed lists sort completedAt descending, nulls last, then newest first`() {
        val ordered = TaskOrdering.forList(
            listOf(
                task("done-early", 0, completedAt = t0.plusSeconds(100)),
                task("done-late", 10, completedAt = t0.plusSeconds(500)),
                task("no-stamp-old", 20).copy(completed = true),
                task("no-stamp-new", 30).copy(completed = true),
            ),
            status = FilterStatus.COMPLETED,
        )
        assertEquals(listOf("done-late", "done-early", "no-stamp-new", "no-stamp-old"), ordered.map { it.title })
    }
}
```

- [ ] **Step 2: Run to confirm it fails** — `Unresolved reference: TaskOrdering`.

- [ ] **Step 3: Implement `TaskOrdering`**

```kotlin
package dev.vinny.gsd.model

/** The shared order for every filtered list — iOS's `TaskFilter` rule (S§3.3). */
object TaskOrdering {
    fun forList(tasks: List<Task>, status: FilterStatus): List<Task> = when (status) {
        FilterStatus.COMPLETED -> tasks.sortedWith(
            compareByDescending<Task, java.time.Instant?>(nullsFirst()) { it.completedAt }
                .thenByDescending { it.createdAt },
        )
        else -> tasks.sortedWith(
            compareBy<Task, java.time.Instant?>(nullsLast()) { it.dueDate }
                .thenByDescending { it.createdAt },
        )
    }
}
```

Note on `nullsFirst()` under `compareByDescending`: descending reverses the comparator, so `nullsFirst` becomes nulls-last — which is what "nulls last" in the completed branch needs. The test pins the observable order; keep it.

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: Write the failing trim test** (append to `FilterCriteriaTest`; mirror its existing helper style)

```kotlin
    @Test
    fun `search query is trimmed and whitespace-only matches everything`() {
        val task = Task(title = "Ship the deck", urgent = true, important = true, createdAt = now, updatedAt = now)
        assertTrue(FilterCriteria(searchQuery = "  deck ").matches(task, listOf(task), now, zone))
        assertTrue(FilterCriteria(searchQuery = "   ").matches(task, listOf(task), now, zone))
        assertFalse(FilterCriteria(searchQuery = "  slides ").matches(task, listOf(task), now, zone))
    }
```

- [ ] **Step 6: Run to confirm it fails** — the `"  deck "` case fails today because the untrimmed query `"  deck "` is not a substring of `"ship the deck"`.

- [ ] **Step 7: Implement** — in `FilterCriteria.matches`, replace the two search lines:

```kotlin
        val q = searchQuery.trim().lowercase()
        if (q.isNotEmpty()) {
            val haystacks = sequenceOf(task.title, task.description) +
                task.tags.asSequence() + task.subtasks.asSequence().map { it.title }
            if (haystacks.none { q in it.lowercase() }) return false
        }
```

- [ ] **Step 8: Run the module suite — expect PASS, sub-second**

- [ ] **Step 9: Commit**

```bash
git add core/model/src/main/kotlin/dev/vinny/gsd/model/TaskOrdering.kt \
        core/model/src/main/kotlin/dev/vinny/gsd/model/FilterCriteria.kt \
        core/model/src/test/kotlin/dev/vinny/gsd/model/TaskOrderingTest.kt \
        core/model/src/test/kotlin/dev/vinny/gsd/model/FilterCriteriaTest.kt
git commit -m "feat(model): shared filtered-list ordering and a trimmed search query"
```

---
### Task 3: `ReminderOptions.including` and `ReminderLabel.of` (`:core:model`)

**Files:**
- Create: `core/model/src/main/kotlin/dev/vinny/gsd/model/ReminderOptions.kt`
- Test: `core/model/src/test/kotlin/dev/vinny/gsd/model/ReminderOptionsTest.kt` (create)

**Interfaces:**
- Produces: `object ReminderOptions { val presets: List<Int>; fun including(stored: Int?): List<Int> }` and `sealed interface ReminderLabel { AtTime; Minutes(n); Hours(n); Days(n) }` with `ReminderLabel.of(minutes: Int): ReminderLabel`. Task 14 maps the label shape to plural string resources.

- [ ] **Step 1: Write the failing tests** (iOS's four `NotificationSettingsTests` cases adapted to Android's superset, plus the label table from S§5)

```kotlin
package dev.vinny.gsd.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ReminderOptionsTest {
    private val presets = listOf(0, 5, 15, 30, 60, 120, 1440)

    @Test
    fun `options are the presets when nothing is stored`() =
        assertEquals(presets, ReminderOptions.including(null))

    @Test
    fun `an on-list stored value adds nothing`() {
        assertEquals(presets, ReminderOptions.including(60))
        assertEquals(presets, ReminderOptions.including(1440))
        assertEquals(presets, ReminderOptions.including(0))
    }

    @Test
    fun `an off-list stored value is offered in sorted position, never snapped`() {
        assertEquals(listOf(0, 5, 15, 30, 45, 60, 120, 1440), ReminderOptions.including(45))
        assertEquals(listOf(0, 5, 15, 30, 60, 120, 1440, 2880), ReminderOptions.including(2880))
        assertEquals(listOf(-10, 0, 5, 15, 30, 60, 120, 1440), ReminderOptions.including(-10))
    }

    @Test
    fun `labels follow the shared formula in order`() {
        assertEquals(ReminderLabel.AtTime, ReminderLabel.of(0))
        assertEquals(ReminderLabel.Minutes(1), ReminderLabel.of(1))
        assertEquals(ReminderLabel.Minutes(5), ReminderLabel.of(5))
        assertEquals(ReminderLabel.Minutes(45), ReminderLabel.of(45))
        assertEquals(ReminderLabel.Hours(1), ReminderLabel.of(60))
        assertEquals(ReminderLabel.Minutes(90), ReminderLabel.of(90))     // not 1.5 hours
        assertEquals(ReminderLabel.Hours(2), ReminderLabel.of(120))
        assertEquals(ReminderLabel.Days(1), ReminderLabel.of(1440))
        assertEquals(ReminderLabel.Hours(36), ReminderLabel.of(2160))     // 36 h, not 1.5 days
        assertEquals(ReminderLabel.Days(2), ReminderLabel.of(2880))
    }
}
```

- [ ] **Step 2: Run to confirm it fails** — `Unresolved reference: ReminderOptions`.

- [ ] **Step 3: Implement**

```kotlin
package dev.vinny.gsd.model

/**
 * The editor's reminder rows (S§5). Android keeps its superset of presets; an off-list
 * stored `notifyBefore` (anything synced from another client) is offered as its own row
 * in sorted position so opening + saving never silently rewrites it.
 */
object ReminderOptions {
    val presets: List<Int> = listOf(0, 5, 15, 30, 60, 120, 1440)

    fun including(stored: Int?): List<Int> =
        if (stored == null || stored in presets) presets else (presets + stored).sorted()
}

/** Pure label shape for a reminder offset — iOS's formula, exact and in order (S§5). */
sealed interface ReminderLabel {
    data object AtTime : ReminderLabel
    data class Minutes(val count: Int) : ReminderLabel
    data class Hours(val count: Int) : ReminderLabel
    data class Days(val count: Int) : ReminderLabel

    companion object {
        fun of(minutes: Int): ReminderLabel = when {
            minutes == 0 -> AtTime
            minutes == 1 -> Minutes(1)
            minutes < 60 || minutes % 60 != 0 -> Minutes(minutes)
            minutes < 1440 -> Hours(minutes / 60)
            minutes % 1440 != 0 -> Hours(minutes / 60)
            else -> Days(minutes / 1440)
        }
    }
}
```

- [ ] **Step 4: Run — expect PASS** · **Step 5: Commit**

```bash
git add core/model/src/main/kotlin/dev/vinny/gsd/model/ReminderOptions.kt \
        core/model/src/test/kotlin/dev/vinny/gsd/model/ReminderOptionsTest.kt
git commit -m "feat(model): reminder options render off-list stored offsets instead of snapping"
```

---

### Task 4: Smart-view bounds in the backup envelope (`:core:model` + `:core:store`)

**Files:**
- Create: `core/model/src/main/kotlin/dev/vinny/gsd/model/SmartViewLimits.kt`
- Modify: `core/model/src/main/kotlin/dev/vinny/gsd/model/Interchange.kt` (`parse` at ~96-137, `smartViewOrNull` at ~139-158, `appPreferencesOrNull` at ~180-185, the export writer at ~42)
- Modify: `core/store/src/main/kotlin/dev/vinny/gsd/store/ImportApplier.kt:72-75`, `core/store/src/main/kotlin/dev/vinny/gsd/store/BackupAssembler.kt:22,31`
- Test: `core/model/src/test/kotlin/dev/vinny/gsd/model/SmartViewLimitsTest.kt` (create), `InterchangeTest.kt` (modify), `core/store/src/androidTest/kotlin/dev/vinny/gsd/store/BackupRoundTripTest.kt` (modify)

**Interfaces:**
- Produces: `object SmartViewLimits { const val MAX_SMART_VIEWS = 100; ...; fun violation(view: SmartView): String? }` (null = within bounds); `BackupImport.skipped` now also counts dropped smart-view rows; `ImportApplier` enforces the 100 cap over **new ids**; `BackupAssembler` clips export at 100 in `createdAt, id` order.

- [ ] **Step 1: Write the failing limits tests**

```kotlin
package dev.vinny.gsd.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SmartViewLimitsTest {
    private val t0 = Instant.parse("2026-07-19T12:00:00.000Z")
    private fun view(
        id: String = "v1", name: String = "Focus", icon: String = "bolt",
        criteria: FilterCriteria = FilterCriteria(),
    ) = SmartView(id, name, icon, criteria, isBuiltIn = false, createdAt = t0, updatedAt = t0)

    @Test
    fun `a well-formed view has no violation`() = assertNull(SmartViewLimits.violation(view()))

    @Test
    fun `each bound names its field`() {
        assertEquals("id", SmartViewLimits.violation(view(id = "")))
        assertEquals("id", SmartViewLimits.violation(view(id = "x".repeat(129))))
        assertEquals("name", SmartViewLimits.violation(view(name = "n".repeat(81))))
        assertEquals("name", SmartViewLimits.violation(view(name = "")))
        assertEquals("icon", SmartViewLimits.violation(view(icon = "i".repeat(65))))
        assertEquals("searchQuery", SmartViewLimits.violation(view(criteria = FilterCriteria(searchQuery = "q".repeat(601)))))
        assertEquals("tags", SmartViewLimits.violation(view(criteria = FilterCriteria(tags = List(201) { "t$it" }))))
        assertEquals("tags", SmartViewLimits.violation(view(criteria = FilterCriteria(tags = listOf("t".repeat(31))))))
        assertNull(SmartViewLimits.violation(view(name = "n".repeat(80), criteria = FilterCriteria(tags = List(200) { "t$it" }))))
    }
}
```

- [ ] **Step 2: Run to confirm it fails** — `Unresolved reference: SmartViewLimits`.

- [ ] **Step 3: Implement `SmartViewLimits`**

```kotlin
package dev.vinny.gsd.model

/** Import/export bounds for custom smart views — the web's SCHEMA_LIMITS (S§7.1). */
object SmartViewLimits {
    const val MAX_SMART_VIEWS = 100
    const val ID_MAX = 128
    const val NAME_MAX = 80        // import bound; the editor keeps its 60
    const val ICON_MAX = 64
    const val SEARCH_MAX = 600
    const val FILTER_TAGS_MAX = 200
    const val TAG_MAX = 30
    const val PINNED_MAX = 5

    /** The first violated field name, or null when the view is within every bound. */
    fun violation(view: SmartView): String? = when {
        view.id.isEmpty() || view.id.length > ID_MAX -> "id"
        view.name.isEmpty() || view.name.length > NAME_MAX -> "name"
        view.icon.length > ICON_MAX -> "icon"
        view.criteria.searchQuery.length > SEARCH_MAX -> "searchQuery"
        view.criteria.tags.size > FILTER_TAGS_MAX || view.criteria.tags.any { it.isEmpty() || it.length > TAG_MAX } -> "tags"
        view.criteria.quadrants.size > 4 -> "quadrants"
        view.criteria.recurrence.size > 4 -> "recurrence"
        else -> null
    }
}
```

(If `FilterCriteria.quadrants` / `recurrence` are `Set`s in this codebase, `.size` still applies; keep the field names exactly as declared in `FilterCriteria.kt`.)

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: Write the failing `InterchangeTest` cases** (append; reuse the file's existing fixture-building helpers for a minimal envelope string)

```kotlin
    @Test
    fun `a smart view over a bound is skipped and counted`() {
        val longName = "n".repeat(81)
        val raw = """{"version":"2.1.0","tasks":[],"smartViews":[
            {"id":"ok","name":"Fine","icon":"bolt","isBuiltIn":false,"criteria":{},
             "createdAt":"2026-07-19T12:00:00.000Z","updatedAt":"2026-07-19T12:00:00.000Z"},
            {"id":"bad","name":"$longName","icon":"bolt","isBuiltIn":false,"criteria":{},
             "createdAt":"2026-07-19T12:00:00.000Z","updatedAt":"2026-07-19T12:00:00.000Z"}]}"""
        val import = Interchange.parse(raw)
        assertEquals(listOf("ok"), import.envelope.smartViews?.map { it.id })
        assertEquals(1, import.skipped)
    }

    @Test
    fun `a malformed smart view row now counts as skipped`() {
        val raw = """{"version":"2.1.0","tasks":[],"smartViews":[{"id":"x"}]}"""
        assertEquals(1, Interchange.parse(raw).skipped)
    }

    @Test
    fun `pinned ids are truncated to five valid entries on import`() {
        val ids = (1..7).map { "\"v$it\"" } + "\"\"" + "\"" + "x".repeat(129) + "\""
        val raw = """{"version":"2.1.0","tasks":[],"appPreferences":{"id":"preferences","pinnedSmartViewIds":[${ids.joinToString(",")}]}}"""
        assertEquals(listOf("v1", "v2", "v3", "v4", "v5"), Interchange.parse(raw).envelope.appPreferences?.pinnedSmartViewIds)
    }

    @Test
    fun `export clips smart views at 100 in createdAt then id order`() {
        val base = Instant.parse("2026-07-19T12:00:00.000Z")
        val views = (0 until 105).map { i ->
            SmartView("v${105 - i}", "V$i", "bolt", FilterCriteria(), false, base.plusSeconds((i % 3).toLong()), base)
        }
        val json = Interchange.export(BackupEnvelope(tasks = emptyList(), smartViews = views), exportedAt = base)
        val emitted = Json.parseToJsonElement(json).jsonObject.getValue("smartViews").jsonArray
        assertEquals(100, emitted.size)
        val first = emitted.first().jsonObject.getValue("id").jsonPrimitive.content
        assertEquals("v105", first) // createdAt = base (i % 3 == 0), lowest id among that bucket: "v105" < "v12" lexically? — see Step 6
    }
```

- [ ] **Step 6: Run to confirm failure, and fix the last assertion's expectation once you see the real lexical order** (ids compare as strings; the point being pinned is *determinism* — the same input always yields the same first element — so assert the full first three ids you observe after implementing the `compareBy({ it.createdAt }, { it.id })` clip, and keep them).

- [ ] **Step 7: Implement in `Interchange.kt`**

In `parse`, make `smartViews` count skips and apply bounds:

```kotlin
                smartViews = root["smartViews"]?.jsonArray?.mapNotNull { el ->
                    smartViewOrNull(el)?.takeIf { SmartViewLimits.violation(it) == null }
                        ?: run { skipped++; null }
                },
```

Note the existing `isBuiltIn: true → null` branch in `smartViewOrNull` now also counts as skipped — that is intended (a built-in row in a payload is a row this client will not store).

In `appPreferencesOrNull`:

```kotlin
            pinnedSmartViewIds = obj.getValue("pinnedSmartViewIds").jsonArray
                .map { it.jsonPrimitive.content }
                .filter { it.isNotEmpty() && it.length <= SmartViewLimits.ID_MAX }
                .take(SmartViewLimits.PINNED_MAX),
```

In the export writer (the `envelope.smartViews?.let { views -> ... }` line):

```kotlin
            envelope.smartViews?.let { views ->
                val clipped = views.sortedWith(compareBy({ it.createdAt }, { it.id })).take(SmartViewLimits.MAX_SMART_VIEWS)
                put("smartViews", JsonArray(clipped.map { smartViewRecord(it) }))
            }
```

- [ ] **Step 8: Run `:core:model:test` — expect PASS, sub-second**

- [ ] **Step 9: Write the failing instrumented cap test** (append to `BackupRoundTripTest`, following its existing in-memory `GsdDatabase` + `ImportApplier` setup)

```kotlin
    @Test
    fun importAdmitsOneHundredNewViewsAndSkipsTheRest_overwriteAtCapIsFree() = runBlocking {
        val t0 = Instant.parse("2026-07-19T12:00:00.000Z")
        fun view(id: String, name: String = id) =
            SmartView(id, name, "bolt", FilterCriteria(), false, t0, t0)
        // seed one existing custom view so an overwrite can be observed
        db.smartViewDao().upsert(view("existing", "Old name").toEntity())
        val incoming = (1..100).map { view("new$it") } + view("existing", "New name") + view("new101")
        val summary = applier.apply(BackupImport(BackupEnvelope(tasks = emptyList(), smartViews = incoming), "2.1.0", 0), ImportMode.MERGE)
        val stored = db.smartViewDao().observeAll().first().filter { !it.isBuiltIn }
        assertEquals(101, stored.size)                          // existing + 100 new
        assertEquals("New name", stored.first { it.id == "existing" }.name)
        assertNull(stored.firstOrNull { it.id == "new101" })   // the 101st new id is skipped
        assertEquals(1, summary.skipped)
    }
```

(`toEntity()` is `internal` to `:core:store`, so this test lives in that module's `androidTest`, which already imports it. Use the `applier`/`db` fields the file already declares; if `BackupImport`'s constructor differs, match the one in `Interchange.kt`.)

- [ ] **Step 10: Run on the emulator to confirm it fails** — `./gradlew :core:store:connectedAndroidTest --info` (or the single class via `-Pandroid.testInstrumentationRunnerArguments.class=dev.vinny.gsd.store.BackupRoundTripTest`). Expected: 102 stored, 0 skipped.

- [ ] **Step 11: Implement the cap in `ImportApplier`**

```kotlin
        env.smartViews?.let { views ->
            if (mode == ImportMode.REPLACE) db.smartViewDao().clearCustom()
            val existing = db.smartViewDao().observeAll().first().filter { !it.isBuiltIn }.map { it.id }.toMutableSet()
            var admitted = 0
            views.filter { !it.isBuiltIn }.forEach { view ->
                val isNew = view.id !in existing
                if (isNew && admitted >= SmartViewLimits.MAX_SMART_VIEWS) { skipped++; return@forEach }
                db.smartViewDao().upsert(view.toEntity())
                if (isNew) { existing += view.id; admitted++ }
            }
        }
```

`skipped` here is the local counter the applier already folds into `ImportSummary(imported, import.skipped)` — introduce `var skipped = import.skipped` at the top of `apply` and return it. In `BackupAssembler.assemble`, clip the same way as the writer so the envelope object itself is bounded:

```kotlin
            smartViews = viewRows.filter { !it.isBuiltIn }.map { it.toSmartView() }
                .sortedWith(compareBy({ it.createdAt }, { it.id })).take(SmartViewLimits.MAX_SMART_VIEWS),
```

- [ ] **Step 12: Run the instrumented store suite — expect PASS** (both cross-platform fixtures still round-trip, S§11.8)

- [ ] **Step 13: Commit**

```bash
git add core/model/src/main/kotlin/dev/vinny/gsd/model/SmartViewLimits.kt \
        core/model/src/main/kotlin/dev/vinny/gsd/model/Interchange.kt \
        core/model/src/test/kotlin/dev/vinny/gsd/model/SmartViewLimitsTest.kt \
        core/model/src/test/kotlin/dev/vinny/gsd/model/InterchangeTest.kt \
        core/store/src/main/kotlin/dev/vinny/gsd/store/ImportApplier.kt \
        core/store/src/main/kotlin/dev/vinny/gsd/store/BackupAssembler.kt \
        core/store/src/androidTest/kotlin/dev/vinny/gsd/store/BackupRoundTripTest.kt
git commit -m "feat(backup): bound custom smart views on import and export, count every skip"
```

---
### Task 5: Bounded, fail-closed remote listings (`:core:sync`)

**Files:**
- Create: `core/sync/src/main/kotlin/dev/vinny/gsd/sync/BoundedListing.kt`
- Modify: `core/sync/src/main/kotlin/dev/vinny/gsd/sync/PocketBaseApi.kt:20-26` (`TaskListPage`), `:79-98` (`listTasks`)
- Modify: `core/sync/src/main/kotlin/dev/vinny/gsd/sync/SyncEngine.kt:84-131` (pull + `fetchRemoteIndex`), `:199-205` (`reconcileDeletions`)
- Modify: `app/src/main/kotlin/dev/vinny/gsd/sync/SyncCoordinator.kt:210-227` (`eraseRemote`)
- Test: `core/sync/src/test/kotlin/dev/vinny/gsd/sync/BoundedListingTest.kt` (create), `SyncEngineTest.kt` (modify — its `FakeRemote.listResponder` hook already lets a test script page responses), `PocketBaseApiTest.kt` (modify)

**Interfaces:**
- Consumes: `SyncRemote.listTasks(page, updatedCursor): TaskListPage`.
- Produces: `TaskListPage.totalItems: Int` (new field, default `items.size` so existing fakes compile); `class RemoteListingIncomplete(reason: String) : Exception`; `object BoundedListing { const val MAX_REMOTE_TASKS = 10_000; suspend fun collect(cursor: String, fetch: suspend (page: Int, cursor: String) -> TaskListPage, onPage: suspend (TaskListPage) -> Unit = {}): List<WireTask> }` — walks every page, applies every S§8.1 rule, throws rather than returning partial data. Tasks 7 and 8 rely on `collect` and on the exception type.

- [ ] **Step 1: Write the failing `BoundedListingTest`**

```kotlin
package dev.vinny.gsd.sync

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BoundedListingTest {
    private fun wire(taskId: String, recordId: String = "r-$taskId") = WireTask(id = recordId, taskId = taskId)
    private fun page(page: Int, totalPages: Int, totalItems: Int, items: List<WireTask>) =
        TaskListPage(page = page, totalPages = totalPages, totalItems = totalItems, items = items, updatedStamps = items.map { "" })

    @Test
    fun `walks every page and returns the full listing`() = runTest {
        val pages = listOf(page(1, 2, 3, listOf(wire("a"), wire("b"))), page(2, 2, 3, listOf(wire("c"))))
        val all = BoundedListing.collect("cursor") { p, _ -> pages[p - 1] }
        assertEquals(listOf("a", "b", "c"), all.map { it.taskId })
    }

    @Test
    fun `over the cap fails closed before any page is trusted`() = runTest {
        val e = assertFailsWith<RemoteListingIncomplete> {
            BoundedListing.collect("c") { _, _ -> page(1, 51, 10_001, listOf(wire("a"))) }
        }
        assertEquals("totalItems 10001 exceeds cap 10000", e.message)
    }

    @Test
    fun `a wrong page number fails closed`() = runTest {
        assertFailsWith<RemoteListingIncomplete> {
            BoundedListing.collect("c") { p, _ -> page(if (p == 2) 3 else 1, 2, 2, listOf(wire("x$p"))) }
        }
    }

    @Test
    fun `totalItems changing mid-walk fails closed`() = runTest {
        assertFailsWith<RemoteListingIncomplete> {
            BoundedListing.collect("c") { p, _ -> if (p == 1) page(1, 2, 2, listOf(wire("a"))) else page(2, 2, 3, listOf(wire("b"))) }
        }
    }

    @Test
    fun `an empty page before the last page fails closed`() = runTest {
        assertFailsWith<RemoteListingIncomplete> {
            BoundedListing.collect("c") { p, _ -> if (p == 1) page(1, 2, 1, emptyList()) else page(2, 2, 1, listOf(wire("a"))) }
        }
    }

    @Test
    fun `a final count that differs from totalItems fails closed`() = runTest {
        assertFailsWith<RemoteListingIncomplete> {
            BoundedListing.collect("c") { _, _ -> page(1, 1, 2, listOf(wire("a"))) }
        }
    }

    @Test
    fun `a duplicate task id fails closed`() = runTest {
        assertFailsWith<RemoteListingIncomplete> {
            BoundedListing.collect("c") { _, _ -> page(1, 1, 2, listOf(wire("a", "r1"), wire("a", "r2"))) }
        }
    }
}
```

- [ ] **Step 2: Run to confirm it fails** — `Unresolved reference: BoundedListing` / no `totalItems` parameter.

Run: `./gradlew :core:sync:test --tests 'dev.vinny.gsd.sync.BoundedListingTest'`

- [ ] **Step 3: Implement**

`TaskListPage` gains `val totalItems: Int = items.size` (keep it after `totalPages`; existing fakes that omit it keep compiling). In `PocketBaseApi.listTasks`, read it and add the stable sort tie-break:

```kotlin
            request("/api/collections/tasks/records?page=$page&perPage=200&sort=updated,id&filter=$filter")
// ...
        return TaskListPage(
            page = envelope["page"]?.jsonPrimitive?.int ?: page,
            totalPages = envelope["totalPages"]?.jsonPrimitive?.int ?: 1,
            totalItems = envelope["totalItems"]?.jsonPrimitive?.int ?: decoded.size,
            items = decoded.map { it.first },
            updatedStamps = decoded.map { it.second },
        )
```

`BoundedListing.kt`:

```kotlin
package dev.vinny.gsd.sync

/** A remote enumeration that could not be trusted end to end; callers must not act on partial data (S§8.1). */
class RemoteListingIncomplete(reason: String) : Exception(reason)

/**
 * Walks every page of a task listing and returns all of it, or throws. A partial,
 * over-cap, or inconsistent listing may never drive deletion or reconciliation.
 */
object BoundedListing {
    const val MAX_REMOTE_TASKS = 10_000

    suspend fun collect(
        cursor: String,
        fetch: suspend (page: Int, cursor: String) -> TaskListPage,
        onPage: suspend (TaskListPage) -> Unit = {},
    ): List<WireTask> {
        val all = mutableListOf<WireTask>()
        val seen = HashSet<String>()
        var expectedTotal: Int? = null
        var page = 1
        while (true) {
            val result = fetch(page, cursor)
            if (result.totalItems > MAX_REMOTE_TASKS) throw RemoteListingIncomplete("totalItems ${result.totalItems} exceeds cap $MAX_REMOTE_TASKS")
            if (result.page != page) throw RemoteListingIncomplete("expected page $page, got ${result.page}")
            if (expectedTotal != null && result.totalItems != expectedTotal) throw RemoteListingIncomplete("totalItems changed mid-listing")
            expectedTotal = result.totalItems
            val isLast = result.page >= result.totalPages
            if (result.items.isEmpty() && !isLast) throw RemoteListingIncomplete("empty page $page before the last page")
            result.items.forEach { wire ->
                if (!seen.add(wire.taskId)) throw RemoteListingIncomplete("duplicate task_id ${wire.taskId}")
            }
            all += result.items
            if (all.size > MAX_REMOTE_TASKS) throw RemoteListingIncomplete("materialized ${all.size} exceeds cap")
            onPage(result)
            if (isLast) break
            page++
        }
        if (all.size != expectedTotal) throw RemoteListingIncomplete("listed ${all.size} of $expectedTotal")
        return all
    }
}
```

- [ ] **Step 4: Run — expect the seven tests to PASS**

- [ ] **Step 5: Write the failing engine tests** (append to `SyncEngineTest`; use its `FakeLocal`/`FakeRemote` and the `listResponder` hook)

```kotlin
    @Test
    fun `an over-cap remote listing aborts the run and deletes nothing locally`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        val t = Task(title = "keep me", urgent = true, important = true, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        local.tasks[t.id] = t
        remote.listResponder = { page, _ -> TaskListPage(page = page, totalPages = 1, totalItems = 10_001, items = emptyList(), updatedStamps = emptyList()) }
        val result = SyncEngine(remote, local, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), throttleMs = 0) { "owner" }.runSync(false)
        assertEquals("error", result.status)
        assertTrue(result.error!!.contains("exceeds cap"))
        assertEquals(listOf(t.id), local.tasks.keys.toList())   // reconciliation never ran
        assertTrue(local.remoteDeletes.isEmpty())
    }
```

- [ ] **Step 6: Run to confirm it fails** — today the empty over-cap page is trusted, reconciliation runs, and `keep me` is deleted (`remoteDeletes` non-empty).

- [ ] **Step 7: Route the engine's three walks through `BoundedListing`**

Pull (replace the `while (true)` page loop; the per-item apply body is unchanged and moves into `onPage`):

```kotlin
        BoundedListing.collect(cursor, remote::listTasks) { result ->
            result.items.forEachIndexed { index, wire -> /* existing apply body, verbatim */ }
        }
```

`fetchRemoteIndex` becomes:

```kotlin
    private suspend fun fetchRemoteIndex(): MutableMap<String, Pair<String, Instant?>> =
        BoundedListing.collect("1970-01-01 00:00:00.000Z", remote::listTasks)
            .associateTo(mutableMapOf()) { wire -> wire.taskId to (wire.id to PbDates.parse(wire.clientUpdatedAt)) }
```

`runSync` already catches every exception into `error`, so `RemoteListingIncomplete` from the pull ends the run before push and reconciliation. In `SyncCoordinator.eraseRemote`, replace its hand-rolled loop with `BoundedListing.collect("1970-01-01 00:00:00.000Z", remote::listTasks).map { it.id }` — a throw propagates to the caller, which already treats any failure as "do not erase locally".

- [ ] **Step 8: Run `:core:sync:test` — expect PASS (the whole §13.6 matrix still green)**

- [ ] **Step 9: Add the `PocketBaseApiTest` case pinning `totalItems` and the sort parameter** (MockWebServer; mirror the file's existing `listTasks` test): enqueue `{"page":1,"totalPages":1,"totalItems":7,"items":[]}` and assert `page.totalItems == 7` and that the recorded request path contains `sort=updated%2Cid` (or `sort=updated,id` — assert on the decoded query).

- [ ] **Step 10: Run, then commit**

```bash
git add core/sync/src/main/kotlin/dev/vinny/gsd/sync/BoundedListing.kt \
        core/sync/src/main/kotlin/dev/vinny/gsd/sync/PocketBaseApi.kt \
        core/sync/src/main/kotlin/dev/vinny/gsd/sync/SyncEngine.kt \
        app/src/main/kotlin/dev/vinny/gsd/sync/SyncCoordinator.kt \
        core/sync/src/test/kotlin/dev/vinny/gsd/sync/BoundedListingTest.kt \
        core/sync/src/test/kotlin/dev/vinny/gsd/sync/SyncEngineTest.kt \
        core/sync/src/test/kotlin/dev/vinny/gsd/sync/PocketBaseApiTest.kt
git commit -m "feat(sync): bounded, fail-closed remote listings drive pull, index, and erase"
```

---

### Task 6: A malformed remote version never authorizes overwrite (`:core:sync`)

**Files:**
- Modify: `core/sync/src/main/kotlin/dev/vinny/gsd/sync/SyncEngine.kt:157-166` (push CREATE/UPDATE branch)
- Test: `core/sync/src/test/kotlin/dev/vinny/gsd/sync/SyncEngineTest.kt` (modify)

**Interfaces:** none new. Uses the existing `index[item.taskId]: Pair<String, Instant?>` and `local.bumpQueueRetry(...)`.

- [ ] **Step 1: Write the failing test**

```kotlin
    @Test
    fun `a pending update is skipped and its retry bumped when the remote version is unparseable`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        val t = Task(title = "edit", urgent = true, important = true, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH.plusSeconds(10))
        local.tasks[t.id] = t
        local.queue += SyncQueueEntity(id = 1, taskId = t.id, operation = SyncOp.UPDATE, timestamp = 0, payloadJson = TaskJson.encode(t))
        remote.records["rec-1"] = WireTask(taskId = t.id, clientUpdatedAt = "not-a-date")
        val result = SyncEngine(remote, local, Clock.fixed(Instant.EPOCH.plusSeconds(60), ZoneOffset.UTC), throttleMs = 0) { "owner" }.runSync(false)
        assertEquals(0, result.pushed)
        assertEquals("not-a-date", remote.records.getValue("rec-1").clientUpdatedAt)   // untouched
        val row = local.queue.single()
        assertEquals(1, row.retryCount)
        assertEquals("Remote task version is missing or malformed", row.lastError)
        assertEquals("pending", row.status)
    }
```

(`TaskJson.encode` — use whatever the file's other push tests use to build `payloadJson`; the `WireTask` field for the remote stamp is `clientUpdatedAt` per `WireTask.kt`.)

- [ ] **Step 2: Run to confirm it fails** — today `pushed == 1` and the remote record is overwritten.

- [ ] **Step 3: Implement** — in the CREATE/UPDATE branch, between the payload decode and the LWW guard:

```kotlin
                        val remoteEntry = index[item.taskId]
                        if (remoteEntry != null && remoteEntry.second == null) {
                            // a missing or malformed remote version may protect, never authorize overwrite (S§8.2)
                            throw MalformedRemoteVersion()
                        }
                        val remoteStamp = remoteEntry?.second
```

and add, next to the engine class: `class MalformedRemoteVersion : Exception("Remote task version is missing or malformed")`. The existing generic `catch (failure: Exception)` then bumps the retry with that message, so five such drains reach `failed` (S§11.10) with no new code path.

- [ ] **Step 4: Run — expect PASS** · **Step 5: Commit**

```bash
git add core/sync/src/main/kotlin/dev/vinny/gsd/sync/SyncEngine.kt core/sync/src/test/kotlin/dev/vinny/gsd/sync/SyncEngineTest.kt
git commit -m "fix(sync): never overwrite a remote record whose version stamp is malformed"
```

---

### Task 7: Three-verdict remote-absence reconciliation (`:core:sync` + Room impl)

**Files:**
- Modify: `core/sync/src/main/kotlin/dev/vinny/gsd/sync/SyncPorts.kt` (`SyncLocalStore`), `SyncEngine.kt:199-205`, `RoomSyncLocalStore.kt`
- Modify: `core/store/src/main/kotlin/dev/vinny/gsd/store/SyncQueueDao.kt` (add `deleteForTask`)
- Test: `SyncEngineTest.kt` (modify: `FakeLocal` gains the new port + three cases), `core/store/src/androidTest/kotlin/dev/vinny/gsd/store/TrashStoreTest.kt` (modify: one Room case for `abandonToTrash`)

**Interfaces:**
- Produces: `SyncLocalStore.abandonToTrash(id: String, deletedAtMs: Long)` — in one transaction: move the active row to `deletedTasks` with `deletedAt = deletedAtMs`, delete every queue row for that task, enqueue nothing. `SyncQueueDao.deleteForTask(taskId: String)`.

- [ ] **Step 1: Extend `FakeLocal`** in `SyncEngineTest` (a compile step, not a behavior):

```kotlin
    val trashed = mutableMapOf<String, Long>()
    override suspend fun abandonToTrash(id: String, deletedAtMs: Long) {
        tasks.remove(id); trashed[id] = deletedAtMs; queue.removeAll { it.taskId == id }
    }
```

- [ ] **Step 2: Write the three failing verdict tests**

```kotlin
    private fun engineAt(remote: FakeRemote, local: FakeLocal, epochSeconds: Long) =
        SyncEngine(remote, local, Clock.fixed(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC), throttleMs = 0) { "owner" }

    @Test
    fun `absent remotely with a pending row is protected`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        val t = Task(title = "p", urgent = true, important = true, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        local.tasks[t.id] = t
        local.queue += SyncQueueEntity(id = 1, taskId = t.id, operation = SyncOp.UPDATE, timestamp = 0, payloadJson = TaskJson.encode(t), retryCount = 1, lastAttemptAt = 0)
        remote.failOnTaskIds += t.id   // push fails, row stays pending
        engineAt(remote, local, 1).runSync(false)
        assertTrue(t.id in local.tasks); assertTrue(local.trashed.isEmpty())
    }

    @Test
    fun `absent remotely with only failed rows is abandoned to trash and its queue rows removed`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        val t = Task(title = "f", urgent = true, important = true, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        local.tasks[t.id] = t
        local.queue += SyncQueueEntity(id = 1, taskId = t.id, operation = SyncOp.CREATE, timestamp = 0, payloadJson = TaskJson.encode(t), status = "failed")
        engineAt(remote, local, 123).runSync(false)
        assertNull(local.tasks[t.id])
        assertEquals(123_000L, local.trashed[t.id])
        assertTrue(local.queue.none { it.taskId == t.id })
        assertTrue(local.remoteDeletes.isEmpty())   // not the APPLY path
    }

    @Test
    fun `absent remotely with no rows is deleted locally without a trash entry`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        val t = Task(title = "gone", urgent = true, important = true, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        local.tasks[t.id] = t
        engineAt(remote, local, 1).runSync(false)
        assertEquals(listOf(t.id), local.remoteDeletes); assertTrue(local.trashed.isEmpty())
    }
```

- [ ] **Step 3: Run to confirm the middle case fails** — today the failed row protects forever: the task stays and `trashed` is empty.

- [ ] **Step 4: Implement** — port in `SyncPorts.kt`:

```kotlin
    /** ABANDON verdict (S§8.3): the content never reached the server — keep it recoverable. */
    suspend fun abandonToTrash(id: String, deletedAtMs: Long)
```

`SyncEngine.reconcileDeletions`:

```kotlin
    private suspend fun reconcileDeletions() {
        val remoteIds = fetchRemoteIndex().keys
        val pending = local.pendingQueue().map { it.taskId }.toSet()
        val failed = local.failedQueue().map { it.taskId }.toSet()
        val now = clock.instant().toEpochMilli()
        local.activeTasks().filter { it.id !in remoteIds }.forEach { task ->
            when {
                task.id in pending -> Unit                          // PROTECT
                task.id in failed -> local.abandonToTrash(task.id, now)  // ABANDON
                else -> local.deleteFromRemote(task.id)            // APPLY
            }
        }
    }
```

`SyncQueueDao`: `@Query("DELETE FROM sync_queue WHERE taskId = :taskId") suspend fun deleteForTask(taskId: String)` (use the table name the entity declares). `RoomSyncLocalStore`:

```kotlin
    override suspend fun abandonToTrash(id: String, deletedAtMs: Long) = db.withTransaction {
        val row = db.taskDao().byId(id) ?: return@withTransaction
        db.deletedTaskDao().upsert(row.toDeleted(deletedAtMs))
        db.taskDao().deleteById(id)
        db.syncQueueDao().deleteForTask(id)
    }
```

(`toDeleted` is `internal` to `:core:store`; if `:core:sync` cannot see it, expose a `TrashStore.abandon(id, deletedAtMs)` in `:core:store` with this body and call it here.)

- [ ] **Step 5: Run `:core:sync:test` — expect PASS**

- [ ] **Step 6: Room case** (append to `TrashStoreTest`): insert an active task and two queue rows for it, call `RoomSyncLocalStore(db).abandonToTrash(id, 5_000)`, assert `deletedTaskDao().byId(id)?.deletedAt == 5_000`, `taskDao().byId(id) == null`, `syncQueueDao().countForTask(id) == 0`, and `syncQueueDao().pending().isEmpty()` (nothing enqueued). Run on the emulator — expect PASS.

- [ ] **Step 7: Commit**

```bash
git add core/sync/src/main/kotlin/dev/vinny/gsd/sync/SyncPorts.kt core/sync/src/main/kotlin/dev/vinny/gsd/sync/SyncEngine.kt \
        core/sync/src/main/kotlin/dev/vinny/gsd/sync/RoomSyncLocalStore.kt core/store/src/main/kotlin/dev/vinny/gsd/store/SyncQueueDao.kt \
        core/sync/src/test/kotlin/dev/vinny/gsd/sync/SyncEngineTest.kt core/store/src/androidTest/kotlin/dev/vinny/gsd/store/TrashStoreTest.kt
git commit -m "feat(sync): abandon a retry-exhausted task to trash when it is absent remotely"
```

---
### Task 8: Account deletion — server route first, bounded fallback, surfaced outcome (`:core:sync` + `:app`)

**Files:**
- Create: `core/sync/src/main/kotlin/dev/vinny/gsd/sync/AccountDeleter.kt`
- Modify: `core/sync/src/main/kotlin/dev/vinny/gsd/sync/SyncPorts.kt` (add `AccountRemote`), `PocketBaseApi.kt:161-163` (implement it)
- Modify: `app/src/main/kotlin/dev/vinny/gsd/sync/SyncCoordinator.kt:230-237`, `app/src/main/kotlin/dev/vinny/gsd/settings/AccountGroup.kt:97-127`, `app/src/main/res/values/strings.xml`
- Test: `core/sync/src/test/kotlin/dev/vinny/gsd/sync/AccountDeleterTest.kt` (create), `PocketBaseApiTest.kt` (modify)

**Interfaces:**
- Produces: `interface AccountRemote { suspend fun deleteAccountViaRoute(); suspend fun deleteUser(userId: String) }` (`PocketBaseApi` implements it; the route call throws `PocketBaseApi.HttpError(404, …)` when absent); `sealed interface DeleteAccountOutcome { Deleted; ListingFailed; PartialAuthRejected; PartialRetry }`; `class AccountDeleter(tasks: SyncRemote, account: AccountRemote, throttleMs: Long = 100) { suspend fun delete(userId: String): DeleteAccountOutcome }`. `SyncCoordinator.deleteAccount` returns the outcome; `AccountGroup` renders it.

- [ ] **Step 1: Write the failing deleter tests** (a local `FakeAccount` plus the existing `FakeRemote`)

```kotlin
package dev.vinny.gsd.sync

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeAccount : AccountRemote {
    var routeStatus: Int? = 204          // null = transport failure
    var userDeleted = false
    var rejectUserDelete = false
    val routeCalls = mutableListOf<Unit>()
    override suspend fun deleteAccountViaRoute() {
        routeCalls += Unit
        when (val s = routeStatus) {
            204 -> Unit
            null -> throw java.io.IOException("offline")
            else -> throw PocketBaseApi.HttpError(s, "")
        }
    }
    override suspend fun deleteUser(userId: String) {
        if (rejectUserDelete) throw PocketBaseApi.Unauthorized(401)
        userDeleted = true
    }
}

class AccountDeleterTest {
    @Test
    fun `route 204 deletes without enumerating`() = runTest {
        val tasks = FakeRemote(); val account = FakeAccount()
        tasks.records["r1"] = WireTask(taskId = "a")
        assertEquals(DeleteAccountOutcome.Deleted, AccountDeleter(tasks, account, 0).delete("u1"))
        assertEquals(1, tasks.records.size)     // fallback never ran
        assertTrue(!account.userDeleted)         // the route owns the user delete
    }

    @Test
    fun `route 404 falls back, deletes every task, then the user`() = runTest {
        val tasks = FakeRemote(); val account = FakeAccount().apply { routeStatus = 404 }
        tasks.records["r1"] = WireTask(taskId = "a"); tasks.records["r2"] = WireTask(taskId = "b")
        assertEquals(DeleteAccountOutcome.Deleted, AccountDeleter(tasks, account, 0).delete("u1"))
        assertTrue(tasks.records.isEmpty()); assertTrue(account.userDeleted)
    }

    @Test
    fun `a transport failure on the route stops with nothing changed`() = runTest {
        val tasks = FakeRemote(); val account = FakeAccount().apply { routeStatus = null }
        tasks.records["r1"] = WireTask(taskId = "a")
        assertEquals(DeleteAccountOutcome.ListingFailed, AccountDeleter(tasks, account, 0).delete("u1"))
        assertEquals(1, tasks.records.size); assertTrue(!account.userDeleted)
    }

    @Test
    fun `a listing failure before any delete reports ListingFailed and touches nothing`() = runTest {
        val tasks = FakeRemote(); val account = FakeAccount().apply { routeStatus = 404 }
        tasks.records["r1"] = WireTask(taskId = "a")
        tasks.listResponder = { page, _ -> TaskListPage(page, 1, 10_001, emptyList(), emptyList()) }
        assertEquals(DeleteAccountOutcome.ListingFailed, AccountDeleter(tasks, account, 0).delete("u1"))
        assertEquals(1, tasks.records.size); assertTrue(!account.userDeleted)
    }

    @Test
    fun `an auth rejection after some deletes reports PartialAuthRejected`() = runTest {
        val tasks = FakeRemote(); val account = FakeAccount().apply { routeStatus = 404; rejectUserDelete = true }
        tasks.records["r1"] = WireTask(taskId = "a")
        assertEquals(DeleteAccountOutcome.PartialAuthRejected, AccountDeleter(tasks, account, 0).delete("u1"))
        assertTrue(tasks.records.isEmpty()); assertTrue(!account.userDeleted)
    }

    @Test
    fun `the user record is never deleted while a listing still returns rows`() = runTest {
        val tasks = FakeRemote(); val account = FakeAccount().apply { routeStatus = 404 }
        // a remote that always reports one row the client cannot delete
        tasks.listResponder = { page, _ -> TaskListPage(page, 1, 1, listOf(WireTask(id = "ghost", taskId = "g")), listOf("")) }
        assertEquals(DeleteAccountOutcome.PartialRetry, AccountDeleter(tasks, account, 0).delete("u1"))
        assertTrue(!account.userDeleted)
    }
}
```

(If `FakeRemote.deleteTask` throws for an unknown record id, make it a no-op for ids it does not hold — check the fake before running.)

- [ ] **Step 2: Run to confirm it fails** — `Unresolved reference: AccountDeleter` / `AccountRemote`.

- [ ] **Step 3: Implement** — port and outcome in `SyncPorts.kt`:

```kotlin
/** The two account-level calls deletion needs, separated from the task listing (S§8.4). */
interface AccountRemote {
    /** `DELETE /api/gsd/account`; throws `PocketBaseApi.HttpError(404, …)` where the hook is not deployed. */
    suspend fun deleteAccountViaRoute()
    suspend fun deleteUser(userId: String)
}

sealed interface DeleteAccountOutcome {
    data object Deleted : DeleteAccountOutcome
    /** Nothing was deleted; the listing (or the route's transport) failed first. */
    data object ListingFailed : DeleteAccountOutcome
    /** Some tasks were deleted, then the session was rejected; the account survives. */
    data object PartialAuthRejected : DeleteAccountOutcome
    /** Some tasks were deleted, then another failure or the pass budget ran out; the account survives. */
    data object PartialRetry : DeleteAccountOutcome
}
```

`AccountDeleter.kt`:

```kotlin
package dev.vinny.gsd.sync

import kotlinx.coroutines.delay

/**
 * Route-first account erasure with a bounded client-side fallback (S§8.4). Only a 404
 * on the route falls back — any other failure stops with nothing changed. The user
 * record is deleted only after a complete listing comes back empty; retrying is safe.
 */
class AccountDeleter(
    private val tasks: SyncRemote,
    private val account: AccountRemote,
    private val throttleMs: Long = 100,
) {
    private companion object { const val MAX_PASSES = 3; const val EPOCH = "1970-01-01 00:00:00.000Z" }

    suspend fun delete(userId: String): DeleteAccountOutcome {
        try {
            account.deleteAccountViaRoute()
            return DeleteAccountOutcome.Deleted
        } catch (absent: PocketBaseApi.HttpError) {
            if (absent.code != 404) return DeleteAccountOutcome.ListingFailed
        } catch (transport: Exception) {
            return DeleteAccountOutcome.ListingFailed
        }
        var erasedAny = false
        repeat(MAX_PASSES) {
            val listing = try {
                BoundedListing.collect(EPOCH, tasks::listTasks)
            } catch (failure: Exception) {
                return if (erasedAny) partial(failure) else DeleteAccountOutcome.ListingFailed
            }
            if (listing.isEmpty()) {
                return try { account.deleteUser(userId); DeleteAccountOutcome.Deleted } catch (failure: Exception) { partial(failure) }
            }
            for (wire in listing) {
                try { tasks.deleteTask(wire.id) } catch (failure: Exception) { return if (erasedAny) partial(failure) else DeleteAccountOutcome.ListingFailed }
                erasedAny = true
                if (throttleMs > 0) delay(throttleMs)
            }
        }
        return DeleteAccountOutcome.PartialRetry
    }

    private fun partial(failure: Exception): DeleteAccountOutcome =
        if (failure is PocketBaseApi.Unauthorized) DeleteAccountOutcome.PartialAuthRejected else DeleteAccountOutcome.PartialRetry
}
```

How the two partial cases resolve: the ghost-row test deletes successfully every pass yet the row keeps coming back, so `erasedAny` is true and the pass budget expiring yields `PartialRetry`; the auth test has `deleteUser` throw `Unauthorized` after an erased task, so `partial(Unauthorized)` yields `PartialAuthRejected`.

`PocketBaseApi` implements `AccountRemote`:

```kotlin
    override suspend fun deleteAccountViaRoute() {
        execute(request("/api/gsd/account").delete().build())
    }
    override suspend fun deleteUser(userId: String) {
        execute(request("/api/collections/users/records/$userId").delete().build())
    }
```

- [ ] **Step 4: Run `AccountDeleterTest` — expect the six cases to PASS**

- [ ] **Step 5: Wire the app** — `SyncCoordinator.deleteAccount` becomes:

```kotlin
    suspend fun deleteAccount(remote: SyncRemote, account: AccountRemote, eraseLocal: Boolean): DeleteAccountOutcome {
        val userId = auth.currentUserId() ?: throw PocketBaseApi.Unauthorized(401)
        auth.ensureFreshToken()
        val outcome = AccountDeleter(remote, account).delete(userId)
        if (outcome != DeleteAccountOutcome.Deleted) return outcome
        db.syncQueueDao().clear()
        auth.signOut()
        _signedIn.value = false
        if (eraseLocal) dev.vinny.gsd.store.LocalErase.eraseAll(db)
        return outcome
    }
```

In `AccountGroup`, replace both silent `runCatching { … }` blocks: collect the outcome into a `var deletionMessage by remember { mutableStateOf<Int?>(null) }` and render it under the Account actions in `GsdType.subhead`, `alert` for non-success. A thrown exception from the local erase after a successful remote delete maps to `account_delete_local_incomplete`. Strings (verbatim from S§8.4):

```xml
    <string name="account_delete_listing_failed">Couldn\'t list your synced tasks, so nothing was deleted. Check your connection and try again.</string>
    <string name="account_delete_partial_auth">Some synced tasks were deleted, but your account is still there. Sign in again, then run Delete account once more.</string>
    <string name="account_delete_partial_retry">Some synced tasks were deleted, but your account is still there. Try again to finish deleting it.</string>
    <string name="account_delete_local_incomplete">Cloud account deleted, but local erase was incomplete. Use Erase All Data to retry local cleanup.</string>
```

- [ ] **Step 6: `PocketBaseApiTest`**: enqueue a 404 for `DELETE /api/gsd/account` and assert `HttpError` with `code == 404`; enqueue 204 and assert no throw and the request carried an `Authorization` header.

- [ ] **Step 7: Run `:core:sync:test` and `./gradlew :app:assembleDebug`; then commit**

```bash
git add core/sync/src/main/kotlin/dev/vinny/gsd/sync/AccountDeleter.kt core/sync/src/main/kotlin/dev/vinny/gsd/sync/SyncPorts.kt \
        core/sync/src/main/kotlin/dev/vinny/gsd/sync/PocketBaseApi.kt app/src/main/kotlin/dev/vinny/gsd/sync/SyncCoordinator.kt \
        app/src/main/kotlin/dev/vinny/gsd/settings/AccountGroup.kt app/src/main/res/values/strings.xml \
        core/sync/src/test/kotlin/dev/vinny/gsd/sync/AccountDeleterTest.kt core/sync/src/test/kotlin/dev/vinny/gsd/sync/PocketBaseApiTest.kt
git commit -m "feat(account): route-first deletion with a bounded fallback and surfaced outcomes"
```

---

### Task 9: Feedback failure tiers and erase completeness (`:core:sync` + `:app`)

**Files:**
- Modify: `core/sync/src/main/kotlin/dev/vinny/gsd/sync/FeedbackApi.kt:25-50`
- Modify: `app/src/main/kotlin/dev/vinny/gsd/feedback/FeedbackViewModel.kt:21-25,101-104`, `app/src/main/kotlin/dev/vinny/gsd/feedback/FeedbackScreen.kt:169`
- Modify: `app/src/main/kotlin/dev/vinny/gsd/settings/SettingsScreen.kt:214-224`, `app/src/main/kotlin/dev/vinny/gsd/sync/SyncCoordinator.kt` (the `eraseLocal` branch from Task 8), `app/src/main/res/values/strings.xml`
- Test: `core/sync/src/test/kotlin/dev/vinny/gsd/sync/FeedbackApiTest.kt` (modify), `app/src/test/kotlin/dev/vinny/gsd/feedback/FeedbackViewModelTest.kt` (modify)

**Interfaces:**
- Produces: `enum class FeedbackFailure { OFFLINE, RATE_LIMITED, SERVER, REJECTED }`; `FeedbackApi.Result.Failed(val kind: FeedbackFailure)` (replaces the message string); `FeedbackSendState.Failed(val kind: FeedbackFailure)`.

- [ ] **Step 1: Write the failing API tests** (append to `FeedbackApiTest`, using its `server`/`api`/`payload` fixtures)

```kotlin
    @Test
    fun rateLimitMapsToRateLimited() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"message":"Feedback capacity is temporarily full."}"""))
        assertEquals(FeedbackApi.Result.Failed(FeedbackFailure.RATE_LIMITED), api.submit(payload))
    }

    @Test
    fun serverErrorMapsToServer() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        assertEquals(FeedbackApi.Result.Failed(FeedbackFailure.SERVER), api.submit(payload))
    }

    @Test
    fun otherBadRequestIsRejected() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"data":{"message":{"code":"validation_required"}}}"""))
        assertEquals(FeedbackApi.Result.Failed(FeedbackFailure.REJECTED), api.submit(payload))
    }

    @Test
    fun transportFailureMapsToOffline() = runBlocking {
        server.shutdown()
        assertEquals(FeedbackApi.Result.Failed(FeedbackFailure.OFFLINE), api.submit(payload))
    }
```

- [ ] **Step 2: Run to confirm it fails** — `Unresolved reference: FeedbackFailure`. (The existing duplicate-400 test must keep passing untouched.)

- [ ] **Step 3: Implement** in `FeedbackApi.kt`:

```kotlin
enum class FeedbackFailure { OFFLINE, RATE_LIMITED, SERVER, REJECTED }

    sealed interface Result {
        data object Sent : Result
        data class Failed(val kind: FeedbackFailure) : Result
    }
// in submit():
                when {
                    response.isSuccessful -> Result.Sent
                    response.code == 400 && isDuplicateSubmission(body) -> Result.Sent
                    response.code == 429 -> Result.Failed(FeedbackFailure.RATE_LIMITED)
                    response.code >= 500 -> Result.Failed(FeedbackFailure.SERVER)
                    else -> Result.Failed(FeedbackFailure.REJECTED)
                }
        } catch (error: IOException) {
            Result.Failed(FeedbackFailure.OFFLINE)
        }
```

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: View model + screen** — `FeedbackSendState.Failed(val kind: FeedbackFailure)`; in `send()`: `is FeedbackApi.Result.Failed -> _state.value = stateFor(current.draft, FeedbackSendState.Failed(result.kind))`. Add a `FeedbackViewModelTest` case per kind asserting the draft **and** `submissionId` are unchanged after a failure (the file already fakes `FeedbackApi`; extend the fake to return a chosen kind). In `FeedbackScreen`:

```kotlin
            is FeedbackSendState.Failed -> Text(
                stringResource(
                    when (sendState.kind) {
                        FeedbackFailure.OFFLINE -> R.string.feedback_failed_offline
                        FeedbackFailure.RATE_LIMITED -> R.string.feedback_failed_rate_limited
                        FeedbackFailure.SERVER -> R.string.feedback_failed_server
                        FeedbackFailure.REJECTED -> R.string.feedback_failed_rejected
                    },
                ),
                style = GsdType.body, color = gsd.alert,
            )
```

Strings (verbatim, S§6.2; retire `feedback_failed`):

```xml
    <string name="feedback_failed_offline">Couldn\'t reach the server. Your draft is saved — try again when you\'re online.</string>
    <string name="feedback_failed_rate_limited">Too many submissions right now. Your draft is saved — try again later.</string>
    <string name="feedback_failed_server">The server had a problem. Your draft is saved — try again shortly.</string>
    <string name="feedback_failed_rejected">The server refused this submission. Your draft is saved.</string>
```

- [ ] **Step 6: Erase clears the draft** — in `SettingsScreen`'s erase confirm (after `LocalErase.eraseAll(container.db)`) and in `SyncCoordinator.deleteAccount`'s `eraseLocal` branch, call `prefs.clearFeedbackDraft()`. `SyncCoordinator` does not hold `GsdPrefs` today; pass it in through `AppContainer` alongside `db` (constructor parameter `private val prefs: GsdPrefs`). Add a `FeedbackViewModelTest`-style JVM check is not possible for DataStore; cover it in the instrumented `app/src/androidTest/.../settings` suite if one exists for erase, else assert in `LaunchSmokeTest` that after `LocalErase.eraseAll + clearFeedbackDraft` the `feedbackDraft` flow emits `null`.

- [ ] **Step 7: Run `:core:sync:test`, `./gradlew :app:testDebugUnitTest`, assemble; commit**

```bash
git add core/sync/src/main/kotlin/dev/vinny/gsd/sync/FeedbackApi.kt core/sync/src/test/kotlin/dev/vinny/gsd/sync/FeedbackApiTest.kt \
        app/src/main/kotlin/dev/vinny/gsd/feedback/FeedbackViewModel.kt app/src/main/kotlin/dev/vinny/gsd/feedback/FeedbackScreen.kt \
        app/src/test/kotlin/dev/vinny/gsd/feedback/FeedbackViewModelTest.kt app/src/main/kotlin/dev/vinny/gsd/settings/SettingsScreen.kt \
        app/src/main/kotlin/dev/vinny/gsd/sync/SyncCoordinator.kt app/src/main/kotlin/dev/vinny/gsd/AppContainer.kt app/src/main/res/values/strings.xml
git commit -m "feat(feedback): tiered failure copy, and erase clears the saved draft"
```

---

### Task 10: Palette "Send feedback" action (`:app`)

**Files:**
- Modify: `app/src/main/kotlin/dev/vinny/gsd/palette/PaletteIndex.kt:6-10`, `app/src/main/kotlin/dev/vinny/gsd/matrix/MatrixScreen.kt:250-262`
- Test: `app/src/test/kotlin/dev/vinny/gsd/palette/PaletteIndexTest.kt` (modify)

- [ ] **Step 1: Failing test**

```kotlin
    @Test
    fun `send feedback is an action and matches by substring`() {
        assertTrue(PaletteAction.SEND_FEEDBACK in PaletteIndex.query("", emptyList(), emptyList()).actions)
        assertEquals(listOf(PaletteAction.SEND_FEEDBACK), PaletteIndex.query("feedb", emptyList(), emptyList()).actions)
    }
```

- [ ] **Step 2: Run to confirm it fails** — `Unresolved reference: SEND_FEEDBACK`.

- [ ] **Step 3: Implement** — add `SEND_FEEDBACK("Send feedback")` as the fourth entry; in `MatrixScreen`'s `onAction` `when`, add `dev.vinny.gsd.palette.PaletteAction.SEND_FEEDBACK -> onNavigate("feedback")`.

- [ ] **Step 4: Run `:app:testDebugUnitTest --tests '*PaletteIndexTest*'`; assemble; commit**

```bash
git add app/src/main/kotlin/dev/vinny/gsd/palette/PaletteIndex.kt app/src/main/kotlin/dev/vinny/gsd/matrix/MatrixScreen.kt app/src/test/kotlin/dev/vinny/gsd/palette/PaletteIndexTest.kt
git commit -m "feat(palette): send feedback from the command palette"
```

---
### Task 11: First-frame skeletons — primitive, loaded flags, three surfaces (`:app`)

**Files:**
- Create: `app/src/main/kotlin/dev/vinny/gsd/ui/Skeleton.kt`, `app/src/main/kotlin/dev/vinny/gsd/ui/StatusBadge.kt`, `app/src/main/kotlin/dev/vinny/gsd/dashboard/DashboardSkeleton.kt`, `app/src/main/kotlin/dev/vinny/gsd/browse/TaskListSkeleton.kt`, `app/src/main/kotlin/dev/vinny/gsd/matrix/MatrixSkeleton.kt`
- Modify: `app/src/main/kotlin/dev/vinny/gsd/matrix/MatrixViewModel.kt:49-52`, `MatrixScreen.kt:68,280-312`, `browse/BrowseViewModel.kt`, `browse/SmartViewListScreen.kt:42-60,66-100`, `dashboard/DashboardScreen.kt:48-63,65-80`, `app/src/main/res/values/strings.xml`
- Test: `app/src/androidTest/kotlin/dev/vinny/gsd/ui/SkeletonTest.kt` (create), `dashboard/DashboardTest.kt`, `browse/BrowseTest.kt`, `matrix/MatrixScreenTest.kt` (modify), `core/store/src/androidTest/kotlin/dev/vinny/gsd/store/TaskStoreTest.kt` (modify)

**Interfaces:**
- Produces: `@Composable fun SkeletonBlock(height: Dp, width: Dp? = null, shape: Shape = GsdShapes.small, modifier)`; `fun Modifier.skeletonShimmer(): Modifier`; `@Composable fun StatusBadge(text: String, style: BadgeStyle)` with `enum class BadgeStyle { ALERT, NEUTRAL }`; `DashboardSkeleton()`, `TaskListSkeleton(rows: Int = 6)`, `MatrixSkeleton(expanded: Boolean = false)`; `MatrixViewModel.sections: StateFlow<List<QuadrantSectionState>?>`; `BrowseViewModel.tasks: StateFlow<List<Task>?>`; `DashboardContent(..., loaded: Boolean)` replaces `isEmpty` with `loaded` + `tasks.isEmpty()` gating inside; `MatrixList(sections: List<QuadrantSectionState>?, ...)`; `ViewListContent(view, allTasks: List<Task>?, ...)`. Tasks 12–13 build on these signatures.

- [ ] **Step 1: Pin the store side** (append to `core/store` `TaskStoreTest`): on a fresh in-memory database, `store.observeTasks().first()` returns an empty list — the first snapshot arrives even with no rows, so a `null` initial value flips on load. Run on the emulator; it should pass already (Room emits the initial query), which makes it a regression pin rather than red/green.

- [ ] **Step 2: Failing UI tests** — `SkeletonTest`:

```kotlin
package dev.vinny.gsd.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import dev.vinny.gsd.dashboard.DashboardSkeleton
import dev.vinny.gsd.browse.TaskListSkeleton
import dev.vinny.gsd.matrix.MatrixSkeleton
import dev.vinny.gsd.theme.GsdTheme
import org.junit.Rule
import org.junit.Test

class SkeletonTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun eachSkeletonIsOneLoadingNode() {
        rule.setContent { GsdTheme { DashboardSkeleton() } }
        rule.onNodeWithContentDescription("Loading").assertIsDisplayed()
        rule.setContent { GsdTheme { TaskListSkeleton() } }
        rule.onAllNodesWithContentDescription("Loading").assertCountEquals(1)
        rule.setContent { GsdTheme { MatrixSkeleton() } }
        rule.onAllNodesWithContentDescription("Loading").assertCountEquals(1)
    }
}
```

`DashboardTest` — add `loadingShowsSkeletonNotEmptyState`: render `DashboardContent(stats = Analytics.compute(emptyList(), 7, now, ZoneOffset.UTC), window = 7, onWindowChange = {}, onOpenOverdue = {}, loaded = false, onOpenTask = {})` and assert `"Loading"` displayed and the `dashboard_empty` text absent; then the inverse with `loaded = true`. `BrowseTest` — `ViewListContent(view, allTasks = null, rowActions = { TaskRowActions() })` shows "Loading" and not "Nothing here". `MatrixScreenTest` — `MatrixList(sections = null, onToggleComplete = {}, onOpen = {})` shows "Loading" and not "No fires right now."; `buildSections(emptyList())` still shows the prompts. (Update the existing `DashboardTest` calls from `isEmpty = false` to `loaded = true`.)

- [ ] **Step 3: Run to confirm they fail** — unresolved references / parameter names.

- [ ] **Step 4: Implement the primitive** — `ui/Skeleton.kt`:

```kotlin
package dev.vinny.gsd.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.vinny.gsd.theme.GsdShapes
import dev.vinny.gsd.theme.LocalGsdColors

/** A placeholder block in the real layout's silhouette: sunken fill, the given corner shape (S§4.2). */
@Composable
fun SkeletonBlock(height: Dp, width: Dp? = null, shape: Shape = GsdShapes.small, modifier: Modifier = Modifier) {
    val gsd = LocalGsdColors.current
    Box(
        modifier = modifier
            .then(if (width == null) Modifier.fillMaxWidth() else Modifier.width(width))
            .height(height)
            .clip(shape)
            .background(gsd.sunken),
    )
}

/**
 * One light band sweeping the whole composition, masked to its own shapes so it never
 * streaks across gaps. Static when animators are disabled (S§4.2). Apply once, to the
 * outermost skeleton container.
 */
fun Modifier.skeletonShimmer(): Modifier = composed {
    if (!ValueAnimator.areAnimatorsEnabled()) return@composed this
    val band = LocalGsdColors.current.surface.copy(alpha = 0.75f)
    val transition = rememberInfiniteTransition(label = "skeleton")
    val progress by transition.animateFloat(
        initialValue = -0.5f, targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, delayMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sweep",
    )
    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val bandWidth = size.width * 0.5f
            val start = progress * size.width
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Transparent, 0.5f to band, 1f to Color.Transparent,
                    startX = start, endX = start + bandWidth,
                ),
                blendMode = BlendMode.SrcIn,
            )
        }
}

/** Collapse a whole skeleton into one "Loading" node for TalkBack. */
fun Modifier.loadingSemantics(label: String): Modifier = clearAndSetSemantics { contentDescription = label }
```

`ui/StatusBadge.kt`:

```kotlin
package dev.vinny.gsd.ui

enum class BadgeStyle { ALERT, NEUTRAL }

/** A short state label on a wash capsule; only overdue earns the alert pigment (S§2.3). */
@Composable
fun StatusBadge(text: String, style: BadgeStyle, modifier: Modifier = Modifier) {
    val gsd = LocalGsdColors.current
    val (ink, wash) = when (style) {
        BadgeStyle.ALERT -> gsd.alert to gsd.alertWash
        BadgeStyle.NEUTRAL -> gsd.ink2 to gsd.sunken
    }
    Text(
        text,
        style = GsdType.caption.copy(fontWeight = FontWeight.SemiBold),
        color = ink,
        maxLines = 1,
        modifier = modifier.clip(CircleShape).background(wash).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
```

(Imports: `androidx.compose.foundation.shape.CircleShape`, `androidx.compose.material3.Text`, `androidx.compose.ui.text.font.FontWeight`, `dev.vinny.gsd.theme.GsdType`.)

- [ ] **Step 5: The three silhouettes** — `dashboard/DashboardSkeleton.kt`:

```kotlin
@Composable
fun DashboardSkeleton(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.skeleton_loading)
    Column(
        modifier = modifier.fillMaxSize().padding(20.dp).loadingSemantics(label).skeletonShimmer(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                SkeletonBlock(height = 86.dp, shape = GsdShapes.input, modifier = Modifier.weight(1f))
                SkeletonBlock(height = 86.dp, shape = GsdShapes.input, modifier = Modifier.weight(1f))
            }
        }
        SkeletonBlock(height = 270.dp, shape = GsdShapes.card)
        SkeletonBlock(height = 320.dp, shape = GsdShapes.card)
    }
}
```

`browse/TaskListSkeleton.kt`:

```kotlin
@Composable
fun TaskListSkeleton(rows: Int = 6, modifier: Modifier = Modifier) {
    val gsd = LocalGsdColors.current
    val label = stringResource(R.string.skeleton_loading)
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 20.dp)
            .clip(GsdShapes.small).background(gsd.surface).loadingSemantics(label).skeletonShimmer(),
    ) {
        repeat(rows) { index ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(gsd.sunken))
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    SkeletonBlock(height = 14.dp, width = if (index % 2 == 0) 210.dp else 150.dp, shape = RoundedCornerShape(4.dp))
                    SkeletonBlock(height = 10.dp, width = 96.dp, shape = RoundedCornerShape(4.dp))
                }
            }
            if (index < rows - 1) HorizontalDivider(color = gsd.hairline, modifier = Modifier.padding(start = 50.dp))
        }
    }
}
```

`matrix/MatrixSkeleton.kt` — four sections, card counts `[3, 2, 2, 1]`:

```kotlin
private val cardCounts = listOf(3, 2, 2, 1) // the web's deliberately uneven silhouette (S§4.3)

@Composable
fun MatrixSkeleton(expanded: Boolean = false, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.skeleton_loading)
    val sections: @Composable (Quadrant, Int) -> Unit = { quadrant, cards ->
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBlock(height = 26.dp, width = 26.dp, shape = GsdShapes.tile)
                SkeletonBlock(height = 16.dp, width = 120.dp, shape = RoundedCornerShape(4.dp), modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.weight(1f))
                SkeletonBlock(height = 12.dp, width = 24.dp, shape = RoundedCornerShape(4.dp))
            }
            repeat(cards) { CardSilhouette(quadrant) }
        }
    }
    val body: @Composable ColumnScope.() -> Unit = {
        Quadrant.entries.forEachIndexed { i, q -> sections(q, cardCounts[i]) }
    }
    Column(
        modifier = modifier.fillMaxSize().padding(20.dp).loadingSemantics(label).skeletonShimmer(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        content = body,
    )
}

@Composable
private fun CardSilhouette(quadrant: Quadrant) {
    val gsd = LocalGsdColors.current
    Row(
        modifier = Modifier.fillMaxWidth().clip(GsdShapes.card).background(gsd.surface).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(3.dp).height(36.dp).clip(CircleShape).background(gsd.accent(quadrant)))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SkeletonBlock(height = 14.dp, width = 180.dp, shape = RoundedCornerShape(4.dp))
            SkeletonBlock(height = 10.dp, width = 96.dp, shape = RoundedCornerShape(4.dp))
        }
    }
}
```

At expanded width the caller places the same silhouettes in its 2×2 grid; keep `expanded` as a layout hint only (a `Row` of two `Column`s when true). String: `<string name="skeleton_loading">Loading</string>`.

- [ ] **Step 6: The gates**

`MatrixViewModel`:

```kotlin
    val sections: StateFlow<List<QuadrantSectionState>?> =
        combine(store.observeTasks(), prefs.showCompleted) { tasks, showCompleted -> buildSections(tasks, showCompleted) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
```

`MatrixList(sections: List<QuadrantSectionState>?, …)`: `if (sections == null) { MatrixSkeleton(modifier = modifier); return }`; every other reader of `sections` in `MatrixScreen` (bulk actions, drag targets) uses `sections.orEmpty()`.

`BrowseViewModel`: add `val tasks: StateFlow<List<Task>?> = store.observeTasks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)`; `SmartViewListScreen` passes `browseViewModel.tasks` (not `editorViewModel.allTasks`) as `allTasks`, and `ViewListContent(allTasks: List<Task>?)` renders `TaskListSkeleton()` when null before the search field's list.

`DashboardScreen`: `val tasks by container.store.observeTasks().collectAsStateWithLifecycle(initialValue = null)`; `stats = remember(tasks, window) { Analytics.compute(tasks.orEmpty(), …) }`; `DashboardContent(stats, window, onWindowChange, onOpenOverdue, loaded = tasks != null, onOpenTask = …)` with, inside: `if (!loaded) { DashboardSkeleton(modifier); return }` then `if (stats.active + stats.completed == 0) { /* existing empty state */ return }`. (`onOpenTask` is wired in Task 12; add the parameter now with a no-op default.)

- [ ] **Step 7: Run the four instrumented classes — expect PASS; then the whole `connectedAndroidTest`; commit**

```bash
git add app/src/main/kotlin/dev/vinny/gsd/ui app/src/main/kotlin/dev/vinny/gsd/dashboard/DashboardSkeleton.kt \
        app/src/main/kotlin/dev/vinny/gsd/browse/TaskListSkeleton.kt app/src/main/kotlin/dev/vinny/gsd/matrix/MatrixSkeleton.kt \
        app/src/main/kotlin/dev/vinny/gsd/matrix/MatrixViewModel.kt app/src/main/kotlin/dev/vinny/gsd/matrix/MatrixScreen.kt \
        app/src/main/kotlin/dev/vinny/gsd/browse/BrowseViewModel.kt app/src/main/kotlin/dev/vinny/gsd/browse/SmartViewListScreen.kt \
        app/src/main/kotlin/dev/vinny/gsd/dashboard/DashboardScreen.kt app/src/main/res/values/strings.xml \
        app/src/androidTest/kotlin/dev/vinny/gsd/ui/SkeletonTest.kt app/src/androidTest/kotlin/dev/vinny/gsd/dashboard/DashboardTest.kt \
        app/src/androidTest/kotlin/dev/vinny/gsd/browse/BrowseTest.kt app/src/androidTest/kotlin/dev/vinny/gsd/matrix/MatrixScreenTest.kt \
        core/store/src/androidTest/kotlin/dev/vinny/gsd/store/TaskStoreTest.kt
git commit -m "feat(app): first-frame skeletons on the matrix, dashboard, and smart-view lists"
```

---

### Task 12: Dashboard — rings card, deadline badges, live upcoming rows (`:app`)

**Files:**
- Create: `app/src/main/kotlin/dev/vinny/gsd/dashboard/QuadrantCompletionRings.kt`
- Modify: `app/src/main/kotlin/dev/vinny/gsd/dashboard/DashboardScreen.kt` (stat card at ~103; card order after `QuadrantBars(distribution)`; upcoming block at ~147-155; host a `TaskEditorSheet`), `app/src/main/res/values/strings.xml`
- Test: `app/src/androidTest/kotlin/dev/vinny/gsd/dashboard/DashboardTest.kt` (modify)

**Interfaces:**
- Consumes: `DashboardStats.quadrantStats`, `Analytics.percent`, `DeadlineStatus.of`, `StatusBadge`, `TaskEditorViewModel.openExisting(id)`.
- Produces: `@Composable fun QuadrantCompletionRings(stats: List<QuadrantStat>, overallRate: Float)`; `DashboardContent(..., now: Instant, zone: ZoneId, onOpenTask: (String) -> Unit)`.

- [ ] **Step 1: Failing tests** (append to `DashboardTest`)

```kotlin
    @Test
    fun ringsCardShowsPercentsAndAnEmDashForAnEmptyQuadrant() {
        val tasks = listOf(
            Task(title = "a", urgent = true, important = true, createdAt = now, updatedAt = now),
            Task(title = "b", urgent = true, important = true, completed = true, completedAt = now, createdAt = now, updatedAt = now),
        )
        val stats = Analytics.compute(tasks, 7, now, ZoneOffset.UTC)
        rule.setContent { GsdTheme { DashboardContent(stats, 7, {}, {}, loaded = true, now = now, zone = ZoneOffset.UTC, onOpenTask = {}) } }
        rule.onNodeWithText("Completion by Quadrant").assertIsDisplayed()
        rule.onNodeWithContentDescription("Completion rings, 50% of all tasks complete").assertIsDisplayed()
        rule.onNodeWithContentDescription("Do First: 50% complete").assertExists()
        rule.onNodeWithContentDescription("Schedule: no tasks").assertExists()
        rule.onAllNodesWithText("—").assertCountEquals(3)
    }

    @Test
    fun upcomingRowsShowDateTodayBadgeAndOpenTheEditor() {
        val zone = java.time.ZoneId.of("America/Chicago")
        val nine = java.time.LocalDateTime.of(2026, 6, 3, 9, 0).atZone(zone).toInstant()
        val today = Task(title = "due today", urgent = false, important = true, createdAt = nine, updatedAt = nine,
            dueDate = java.time.LocalDateTime.of(2026, 6, 3, 0, 0).atZone(zone).toInstant())
        val later = Task(title = "due friday", urgent = false, important = true, createdAt = nine, updatedAt = nine,
            dueDate = java.time.LocalDateTime.of(2026, 6, 5, 12, 0).atZone(zone).toInstant())
        val stats = Analytics.compute(listOf(today, later), 7, nine, zone)
        var opened: String? = null
        rule.setContent { GsdTheme { DashboardContent(stats, 7, {}, {}, loaded = true, now = nine, zone = zone, onOpenTask = { opened = it }) } }
        rule.onNodeWithText("Today").assertIsDisplayed()
        rule.onAllNodesWithText("Today").assertCountEquals(1)   // "due friday" carries no badge
        rule.onNodeWithText("due friday").performClick()
        assertEquals(later.id, opened)
    }
```

- [ ] **Step 2: Run to confirm failure** — parameter/reference errors, then missing nodes.

- [ ] **Step 3: Implement the rings**

```kotlin
package dev.vinny.gsd.dashboard

private val ringSize = 196.dp; private val ringWidth = 13.dp; private val ringGap = 5.dp

/** Four concentric completion rings, Q1 outermost, over their wash tracks (S§2.2). */
@Composable
fun QuadrantCompletionRings(stats: List<QuadrantStat>, overallRate: Float, modifier: Modifier = Modifier) {
    val gsd = LocalGsdColors.current
    val animate = remember { ValueAnimator.areAnimatorsEnabled() }
    val growth = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) { if (animate) growth.animateTo(1f, tween(900, easing = EaseOut)) }
    val overall = Analytics.percent(overallRate)
    val ringsLabel = stringResource(R.string.dashboard_rings_a11y, overall)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier.size(ringSize).align(Alignment.CenterHorizontally)
                .clearAndSetSemantics { contentDescription = ringsLabel },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = ringWidth.toPx(); val step = (ringWidth + ringGap).toPx() * 2
                stats.forEachIndexed { index, stat ->
                    val diameter = size.width - index * step
                    val inset = (size.width - diameter) / 2 + stroke / 2
                    val arcSize = Size(diameter - stroke, diameter - stroke)
                    val topLeft = Offset(inset, inset)
                    drawArc(gsd.wash(stat.quadrant), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                    drawArc(gsd.accent(stat.quadrant), -90f, 360f * stat.completionRate * growth.value, false, topLeft, arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(67.dp)) {
                Text(overall, style = GsdType.title.copy(fontFeatureSettings = "tnum"), color = gsd.ink, maxLines = 1, autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = 28.sp))
                Text(stringResource(R.string.dashboard_rings_complete), style = GsdType.caption, color = gsd.ink3)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            stats.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { pair.forEach { LegendCell(it, Modifier.weight(1f)) } }
            }
        }
    }
}

@Composable
private fun LegendCell(stat: QuadrantStat, modifier: Modifier) {
    val gsd = LocalGsdColors.current
    val value = if (stat.total == 0) "—" else Analytics.percent(stat.completionRate)
    val spoken = if (stat.total == 0) stringResource(R.string.dashboard_rings_legend_none, stat.quadrant.title)
        else stringResource(R.string.dashboard_rings_legend, stat.quadrant.title, Analytics.percent(stat.completionRate))
    Row(
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp),
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken },
    ) {
        Box(Modifier.size(11.dp).clip(RoundedCornerShape(3.dp)).background(gsd.accent(stat.quadrant)))
        Text(stat.quadrant.title, style = GsdType.subhead, color = gsd.ink2, modifier = Modifier.weight(1f))
        Text(value, style = GsdType.subhead.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"), color = gsd.ink)
    }
}
```

(If the Compose BOM in use predates `TextAutoSize`, use `BasicText(autoSize = …)` or a `minimumScale` via `softWrap = false` + `overflow = TextOverflow.Visible`; the requirement is a single line that shrinks, S§2.2. The em dash in the legend `Text` is the literal `—` U+2014 — the test counts three of them for three empty quadrants.)

Strings:

```xml
    <string name="dashboard_completion_by_quadrant">Completion by Quadrant</string>
    <string name="dashboard_rings_complete">complete</string>
    <string name="dashboard_rings_a11y">Completion rings, %1$s of all tasks complete</string>
    <string name="dashboard_rings_legend">%1$s: %2$s complete</string>
    <string name="dashboard_rings_legend_none">%1$s: no tasks</string>
    <string name="dashboard_badge_today">Today</string>
    <string name="dashboard_badge_overdue">Overdue</string>
```

- [ ] **Step 4: Wire the card and the rows in `DashboardScreen`**

Stat card: `Analytics.percent(stats.completionRate)` replaces `"${(stats.completionRate * 100).toInt()}%"`. After `QuadrantBars(distribution)`:

```kotlin
        Column(
            modifier = Modifier.fillMaxWidth().clip(GsdShapes.card).background(gsd.surface).border(1.dp, gsd.hairline, GsdShapes.card).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.dashboard_completion_by_quadrant), style = GsdType.quadrantTitle, color = gsd.ink)
            QuadrantCompletionRings(stats.quadrantStats, stats.completionRate)
        }
```

Upcoming rows (replace the `stats.upcoming.forEach { Text(...) }`):

```kotlin
                val formatter = remember(zone) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(zone) }
                stats.upcoming.forEachIndexed { index, task ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).clickable { onOpenTask(task.id) }.padding(vertical = 10.dp),
                    ) {
                        Text(task.title, style = GsdType.subhead, color = gsd.ink, modifier = Modifier.weight(1f))
                        task.dueDate?.let { due ->
                            when (DeadlineStatus.of(due, now, zone)) {
                                DeadlineStatus.TODAY -> StatusBadge(stringResource(R.string.dashboard_badge_today), BadgeStyle.NEUTRAL)
                                DeadlineStatus.OVERDUE -> StatusBadge(stringResource(R.string.dashboard_badge_overdue), BadgeStyle.ALERT)
                                DeadlineStatus.UPCOMING -> Unit
                            }
                            Text(formatter.format(due), style = GsdType.metadata, color = gsd.ink3, maxLines = 1)
                        }
                    }
                    if (index < stats.upcoming.lastIndex) HorizontalDivider(color = gsd.hairline)
                }
```

`DashboardScreen` computes `now`/`zone` once per recomposition of the task list (`remember(tasks) { Instant.now() }`, `ZoneId.systemDefault()`), passes them to both `Analytics.compute` and `DashboardContent`, hosts `val editorViewModel: TaskEditorViewModel = viewModel { TaskEditorViewModel(container.store) }` with `onOpenTask = editorViewModel::openExisting` and the same `TaskEditorSheet(viewModel = editorViewModel, onDismiss = {})` block `SmartViewListScreen` uses.

- [ ] **Step 5: Run `DashboardTest` — expect PASS; then commit**

```bash
git add app/src/main/kotlin/dev/vinny/gsd/dashboard/QuadrantCompletionRings.kt app/src/main/kotlin/dev/vinny/gsd/dashboard/DashboardScreen.kt \
        app/src/main/res/values/strings.xml app/src/androidTest/kotlin/dev/vinny/gsd/dashboard/DashboardTest.kt
git commit -m "feat(dashboard): completion rings, day-based deadline badges, and tappable upcoming rows"
```

---
### Task 13: Browse root search, shared ordering, search-miss copy (`:app`)

**Files:**
- Modify: `app/src/main/kotlin/dev/vinny/gsd/browse/BrowseScreen.kt:106-186` (`BrowseScreen`, `BrowseContent`), `app/src/main/kotlin/dev/vinny/gsd/browse/SmartViewListScreen.kt:66-115` (`ViewListContent`), `app/src/main/res/values/strings.xml`
- Test: `app/src/androidTest/kotlin/dev/vinny/gsd/browse/BrowseTest.kt` (modify)

**Interfaces:**
- Consumes: `FilterCriteria(status = FilterStatus.ACTIVE, searchQuery = q).matches(...)`, `TaskOrdering.forList`, `TaskRowWithMenu(task, badges, actions)`, `BrowseViewModel.tasks` (Task 11), a `TaskEditorViewModel` hosted by `BrowseScreen` as `SmartViewListScreen` does.
- Produces: `BrowseContent(..., tasks: List<Task>?, rowActions: (Task) -> TaskRowActions)`; `@Composable fun BrowseSearchResults(query: String, allTasks: List<Task>, rowActions: (Task) -> TaskRowActions)`; `ViewListContent` renders `TaskOrdering.forList(matches, effective.status)`.

- [ ] **Step 1: Failing tests** (append to `BrowseTest`; its `t0` and `buildBrowseState` helpers are reused)

```kotlin
    private fun content(state: BrowseState, tasks: List<Task>, onOpen: (String) -> Unit = {}) = rule.setContent {
        GsdTheme {
            BrowseContent(
                state = state, onOpenArchive = {}, trashCount = 0, onOpenTrash = {}, onOpenView = {}, onNewView = {},
                onTogglePin = {}, onEditView = {}, onDeleteView = {}, tasks = tasks,
                rowActions = { task -> TaskRowActions(onEdit = { onOpen(task.id) }) },
            )
        }
    }

    @Test
    fun oneCharacterQueryReplacesTheListWithActiveMatchesInSharedOrder() {
        val undated = Task(title = "Zebra", urgent = true, important = true, createdAt = t0, updatedAt = t0)
        val dated = Task(title = "Zip", urgent = false, important = true, createdAt = t0.minusSeconds(60), updatedAt = t0, dueDate = t0.plusSeconds(3_600))
        val done = Task(title = "Zed", urgent = true, important = true, completed = true, completedAt = t0, createdAt = t0, updatedAt = t0)
        val bySubtask = Task(title = "Plan", urgent = false, important = false, createdAt = t0.minusSeconds(120), updatedAt = t0,
            subtasks = listOf(Subtask(id = "s1", title = "buy zucchini")))
        val state = buildBrowseState(BuiltInSmartViews.all, emptyList(), listOf(undated, dated, done, bySubtask), t0, ZoneOffset.UTC)
        content(state, listOf(undated, dated, done, bySubtask))
        rule.onNodeWithTag("browse_search").performTextInput(" z ")
        rule.onNodeWithText("Archive").assertDoesNotExist()
        rule.onNodeWithText("Zed").assertDoesNotExist()                     // completed never appears
        val titles = rule.onAllNodes(hasTestTag("browse_result")).fetchSemanticsNodes().map { it.config[SemanticsProperties.Text].joinToString() }
        assertEquals(listOf("Zip", "Zebra", "Plan"), titles.map { it.substringBefore("\n") })   // dated first, then newest first
    }

    @Test
    fun searchMissShowsTheSharedEmptyCopy() {
        content(buildBrowseState(BuiltInSmartViews.all, emptyList(), emptyList(), t0, ZoneOffset.UTC), emptyList())
        rule.onNodeWithTag("browse_search").performTextInput("qq")
        rule.onNodeWithText("No tasks match \"qq\"").assertIsDisplayed()
        rule.onNodeWithText("Try a different word, tag, or quadrant.").assertIsDisplayed()
    }

    @Test
    fun whitespaceOnlyQueryKeepsTheList() {
        content(buildBrowseState(BuiltInSmartViews.all, emptyList(), emptyList(), t0, ZoneOffset.UTC), emptyList())
        rule.onNodeWithTag("browse_search").performTextInput("   ")
        rule.onNodeWithText("Archive").assertIsDisplayed()
    }
```

(If reading titles through semantics is awkward, give each result row `Modifier.testTag("browse_result")` **and** assert order via `onAllNodesWithTag("browse_result")[0].assertTextContains("Zip")`, `[1]` → "Zebra", `[2]` → "Plan".)

- [ ] **Step 2: Run to confirm failure** — no `tasks`/`rowActions` parameters, no `browse_search` node.

- [ ] **Step 3: Implement**

`BrowseScreen` hosts `val editorViewModel: TaskEditorViewModel = viewModel { TaskEditorViewModel(container.store) }`, collects `val tasks by viewModel.tasks.collectAsStateWithLifecycle()`, passes `tasks` and `rowActions = { task -> TaskRowActions(onEdit = { editorViewModel.openExisting(task.id) }, onToggleComplete = { viewModel.toggleCompleted(task.id) }) }`, and renders `TaskEditorSheet(viewModel = editorViewModel, onDismiss = {})` while `editorDraft != null` — the same block `SmartViewListScreen` has.

`BrowseContent` gains the two parameters and the field:

```kotlin
    var query by rememberSaveable { mutableStateOf("") }
    val trimmed = query.trim()
    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 4.dp)) {
            if (query.isEmpty()) Text(stringResource(R.string.browse_search_hint), style = GsdType.subhead, color = gsd.ink3)
            BasicTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                textStyle = GsdType.subhead.copy(color = gsd.ink),
                modifier = Modifier.fillMaxWidth().testTag("browse_search"),
            )
        }
        if (trimmed.isNotEmpty()) {
            BrowseSearchResults(query = trimmed, allTasks = tasks.orEmpty(), rowActions = rowActions)
        } else {
            LazyColumn(/* the existing list, unchanged */) { … }
        }
    }
```

`BrowseSearchResults` (same file):

```kotlin
@Composable
fun BrowseSearchResults(query: String, allTasks: List<Task>, rowActions: (Task) -> TaskRowActions, modifier: Modifier = Modifier) {
    val gsd = LocalGsdColors.current
    val now = Instant.now(); val zone = ZoneId.systemDefault()
    val criteria = FilterCriteria(status = FilterStatus.ACTIVE, searchQuery = query)
    val matches = TaskOrdering.forList(allTasks.filter { criteria.matches(it, allTasks, now, zone) }, FilterStatus.ACTIVE)
    if (matches.isEmpty()) {
        SearchMissEmptyState(query, modifier)
        return
    }
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(matches, key = { it.id }) { task ->
            val badges = TaskBadges(
                blocked = DependencyGraph.uncompletedBlockers(allTasks, task).size,
                blocking = DependencyGraph.blocking(allTasks, task).size,
            )
            Box(Modifier.testTag("browse_result")) { TaskRowWithMenu(task = task, badges = badges, actions = rowActions(task)) }
        }
    }
}

/** §5.10 empty state for a search miss; shared by Browse search and the in-view search (S§3.4). */
@Composable
fun SearchMissEmptyState(query: String, modifier: Modifier = Modifier) {
    val gsd = LocalGsdColors.current
    Column(modifier = modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(60.dp).clip(GsdShapes.input).background(gsd.sunken), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = gsd.ink3, modifier = Modifier.size(28.dp))
        }
        Text(stringResource(R.string.search_empty_title, query), style = GsdType.quadrantTitle, color = gsd.ink, textAlign = TextAlign.Center)
        Text(stringResource(R.string.search_empty_body), style = GsdType.subhead, color = gsd.ink2, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 280.dp))
    }
}
```

`ViewListContent`: `val matches = TaskOrdering.forList(allTasks.filter { effective.matches(it, allTasks, now, zone) }, effective.status)` and, in its empty branch, `query.isNotEmpty() -> SearchMissEmptyState(query.trim())` instead of the `view_empty_search` text. Strings (retire `view_empty_search`):

```xml
    <string name="browse_search_hint">Search all tasks</string>
    <string name="search_empty_title">No tasks match "%1$s"</string>
    <string name="search_empty_body">Try a different word, tag, or quadrant.</string>
```

- [ ] **Step 4: Run `BrowseTest` — expect PASS; run the full instrumented suite (the existing in-view search test now asserts the new copy — update it); commit**

```bash
git add app/src/main/kotlin/dev/vinny/gsd/browse/BrowseScreen.kt app/src/main/kotlin/dev/vinny/gsd/browse/SmartViewListScreen.kt \
        app/src/main/res/values/strings.xml app/src/androidTest/kotlin/dev/vinny/gsd/browse/BrowseTest.kt
git commit -m "feat(browse): search every active task from the Browse root, in the shared list order"
```

---

### Task 14: Editor renders an off-list stored reminder (`:app`)

**Files:**
- Modify: `app/src/main/kotlin/dev/vinny/gsd/editor/EditorDepthSections.kt:85-120`, `app/src/main/res/values/strings.xml`
- Test: `app/src/androidTest/kotlin/dev/vinny/gsd/editor/TaskEditorDepthTest.kt` (modify)

**Interfaces:**
- Consumes: `ReminderOptions.including`, `ReminderLabel.of` (Task 3).
- Produces: `@Composable fun reminderLabel(minutes: Int?): String` (private to the editor package) mapping `null` → "None" and each `ReminderLabel` to its resource.

- [ ] **Step 1: Failing test** (append to `TaskEditorDepthTest`, following how its other cases open a draft with a due date and read the `reminder_row` tag)

```kotlin
    @Test
    fun offListStoredReminderRendersAndSurvivesAnUntouchedSave() {
        val stored = Task(title = "synced", urgent = true, important = true, createdAt = t0, updatedAt = t0,
            dueDate = t0.plusSeconds(86_400), notifyBefore = 45)
        var saved: Task? = null
        rule.setContent { GsdTheme { ReminderSection(draft = stored, onEdit = { edit -> saved = edit(stored) }) } }
        rule.onNodeWithTag("reminder_row").assertTextEquals("45 minutes before")
        rule.onNodeWithTag("reminder_row").performClick()
        rule.onNodeWithText("30 minutes before").assertIsDisplayed()
        rule.onNodeWithText("45 minutes before").assertIsDisplayed()   // offered in sorted position
        rule.onNodeWithText("1 hour before").assertIsDisplayed()
        rule.onNodeWithText("45 minutes before").performClick()
        assertEquals(45, saved?.notifyBefore)                          // choosing it again changes nothing
    }

    @Test
    fun reminderLabelsFollowTheSharedFormula() {
        val stored = Task(title = "x", urgent = true, important = true, createdAt = t0, updatedAt = t0, dueDate = t0, notifyBefore = 2160)
        rule.setContent { GsdTheme { ReminderSection(draft = stored, onEdit = {}) } }
        rule.onNodeWithTag("reminder_row").assertTextEquals("36 hours before")
    }
```

- [ ] **Step 2: Run to confirm failure** — the row reads "None" today.

- [ ] **Step 3: Implement** — replace the fixed `reminderOptions` list and the label lookup:

```kotlin
@Composable
private fun reminderLabel(minutes: Int?): String = when (val label = minutes?.let(ReminderLabel::of)) {
    null -> stringResource(R.string.reminder_none)
    ReminderLabel.AtTime -> stringResource(R.string.reminder_at_time)
    is ReminderLabel.Minutes -> pluralStringResource(R.plurals.reminder_minutes_before, label.count, label.count)
    is ReminderLabel.Hours -> pluralStringResource(R.plurals.reminder_hours_before, label.count, label.count)
    is ReminderLabel.Days -> pluralStringResource(R.plurals.reminder_days_before, label.count, label.count)
}

@Composable
internal fun ReminderSection(draft: Task, onEdit: ((Task) -> Task) -> Unit) {
    val gsd = LocalGsdColors.current
    var open by remember { mutableStateOf(false) }
    val options: List<Int?> = listOf(null) + ReminderOptions.including(draft.notifyBefore)
    Box {
        Text(
            reminderLabel(draft.notifyBefore), style = GsdType.body, color = gsd.tint,
            modifier = Modifier.clickable { open = true }.testTag("reminder_row").padding(vertical = 4.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { minutes ->
                DropdownMenuItem(
                    text = { Text(reminderLabel(minutes), style = GsdType.body) },
                    onClick = { open = false; onEdit { it.copy(notifyBefore = minutes) } },
                )
            }
        }
    }
}
```

Strings — replace `reminder_5m … reminder_1d` with plurals (keep `reminder_none`, `reminder_at_time`):

```xml
    <plurals name="reminder_minutes_before">
        <item quantity="one">%1$d minute before</item>
        <item quantity="other">%1$d minutes before</item>
    </plurals>
    <plurals name="reminder_hours_before">
        <item quantity="one">%1$d hour before</item>
        <item quantity="other">%1$d hours before</item>
    </plurals>
    <plurals name="reminder_days_before">
        <item quantity="one">%1$d day before</item>
        <item quantity="other">%1$d days before</item>
    </plurals>
```

(`grep -rn 'reminder_5m\|reminder_1h' app/src` for any other reader of the retired strings — the notification text may use them; point those at the same helper or keep a non-composable `Context.reminderLabel(minutes)` twin in `notifications/`.)

- [ ] **Step 4: Run `TaskEditorDepthTest` — expect PASS; commit**

```bash
git add app/src/main/kotlin/dev/vinny/gsd/editor/EditorDepthSections.kt app/src/main/res/values/strings.xml \
        app/src/androidTest/kotlin/dev/vinny/gsd/editor/TaskEditorDepthTest.kt
git commit -m "feat(editor): render an off-list stored reminder instead of showing None"
```

---

### Task 15: Fold the amendments into the spec, hand off, full verification sweep

**Files:**
- Modify: `docs/gsd-android-spec.md` (§5.10, §6.1, §7.9, §8.1, §8.3, §8.13, §8.14, §8.15, §8.16, §8.17, §8.21, §9.4, §9.5, §13.3, Appendix B — per S§9), `README.md` (status paragraph), `tasks/todo.md` (this round's record + "Resuming From Here"), `gradle/libs.versions.toml` (commit the pending AGP `9.3.0 → 9.3.2` bump if it is still uncommitted; it is the version every green gate ran under)

- [ ] **Step 1: Amend the product spec** — at each section listed in S§9 add the marker line `> Amended 2026-09-06 — see docs/2026-09-06-cross-platform-parity-spec.md` and rewrite the section's prose to describe the shipped behavior (the parity spec stays the dated record of *why*). Concretely: §8.15 gains `quadrantStats` + the rings card + `DeadlineStatus` + the day-based upcoming rule + tap-to-editor; §8.3's Reminder bullet becomes "the eight presets plus any off-list stored value, labelled by the shared formula, never snapped"; §8.13/§8.14 gain `TaskOrdering.forList`, the Browse root search, the shared search-miss copy, and the palette action; §8.16 gains the §7.1 limits, skip counting, the export clip, and "erase clears the feedback draft"; §8.17 gains the four failure sentences and the nudge's declared absence; §8.21 gains the route-first deletion and the four outcome strings; §9.4 gains bounded fail-closed listings and the three verdicts; §9.5 gains the malformed-version guard; §5.10 + §13.3 gain the skeletons, badge, and the reduced-motion rule; Appendix B gains the limits table.

- [ ] **Step 2: Full verification sweep** — every gate green before the docs commit:

```bash
./gradlew :core:model:test            # sub-second gate
./gradlew testDebugUnitTest           # :core:sync + :app JVM suites
./gradlew connectedAndroidTest        # :core:store + :app instrumented (emulator)
./gradlew :app:assembleDebug
```

Record the exact commands and results in `tasks/todo.md` under a "Cross-platform parity catch-up (2026-09-06)" section listing the fourteen commits by hash, then a fresh "Resuming From Here (…)" block: done, next (owner decision on a mid-window closed-test release, S§10), blockers, assumptions.

- [ ] **Step 3: Distill and clean** — move anything learned into `tasks/lessons.md`, delete `tasks/implementation-notes.md` if this round used it, remove scratch files.

- [ ] **Step 4: Commit**

```bash
git add docs/gsd-android-spec.md README.md tasks/todo.md tasks/lessons.md gradle/libs.versions.toml
git commit -m "docs: fold the September 2026 parity amendments into the spec and record the round"
```

---

## Self-review against the spec (done 2026-09-06)

| Spec section | Task |
|---|---|
| S§2.1 analytics, `DeadlineStatus`, percent, upcoming | 1 |
| S§2.2 rings card · S§2.3 upcoming rows + badge | 12 (badge composable in 11) |
| S§3.1–3.2 Browse search + trim · S§3.3 ordering · S§3.4 empty copy | 2, 13 |
| S§4.1 loaded flag · S§4.2 primitive · S§4.3 three silhouettes | 11 |
| S§5 off-list reminders | 3, 14 |
| S§6.1 palette · S§6.2 failure tiers · S§6.3 erase · S§6.4 declared absence | 10, 9, 9, 15 |
| S§7.1–7.3 smart-view bounds | 4 |
| S§8.1 bounded listings · S§8.2 malformed version · S§8.3 three verdicts · S§8.4 account deletion | 5, 6, 7, 8 |
| S§9 amendments · S§11.13 sweep | 15 |

Type consistency checked: `FilterStatus` (not `TaskStatus`) throughout; `TaskListPage.totalItems` defaults so Task 5's fakes compile before Task 8; `SyncLocalStore.abandonToTrash` is added to `FakeLocal` in Task 7 before the engine calls it; `DashboardContent`'s `loaded`/`now`/`zone`/`onOpenTask` parameters are introduced in Task 11 (with defaults) and used in Task 12; `SearchMissEmptyState` is defined in Task 13 where both callers live.

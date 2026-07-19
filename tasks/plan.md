# GSD Android Implementation Plan — Phase 0 & 1 (detailed), Phases 2–7 (outline)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the GSD Task Manager as a native Android app (Kotlin + Jetpack Compose) per `gsd-android-spec.md`, starting with foundations (Phase 0) and the core local app (Phase 1).

**Architecture:** Four Gradle modules with enforced dependency direction — `:core:model` (pure JVM domain, zero Android deps) ← `:core:store` (Room + TaskStore mutation path) ← `:core:sync` (stub until Phase 5) ← `:app` (Compose UI, manual composition root). All behavior rules come from `gsd-android-spec.md` (the behavior authority); this plan references spec sections rather than duplicating authoritative tables.

**Tech Stack:** Kotlin 2.4.0 · AGP 9.2.0 · Gradle wrapper 9.6.1 · Compose BOM 2026.06.00 (Material 3) · Room 2.8.4 (KSP2) · kotlinx.serialization · kotlinx.coroutines · DataStore Preferences · JUnit4 + kotlin-test on JVM.

## Global Constraints

- **minSdk 31, targetSdk = latest stable** (spec §3). JDK 17 (installed at `/opt/homebrew/opt/openjdk@17`); if AGP 9.2 demands a newer JDK at first build, `brew install openjdk@21` and set `org.gradle.java.home`.
- **Application ID:** `dev.vinny.gsd`. Package root `dev.vinny.gsd`; modules use `dev.vinny.gsd.model` / `.store` / `.sync`.
- **Module dependency direction is law:** `:core:model` depends on nothing Android; violations fail the build (no `android` plugin on `:core:model`).
- **No Hilt/Koin** — manual composition root in the Application class (spec §3).
- **No dynamic color, no tonal elevation, no Material baseline purple** (spec §5). Tokens come verbatim from spec §5.1–§5.3.
- **Field limits are Appendix B of the spec** — title 1–80, description ≤600, tags ≤20 ×1–30 lowercase, subtasks ≤50 ×1–100, dependencies ≤50, time entries ≤1000, estimate 1–10080 (0 = unset).
- **All UI strings in `strings.xml`** — no concatenated sentences (spec §3).
- **TDD red/green/refactor for every task** (coding-standards.md). The gate is `./gradlew :core:model:test` staying fast.
- **Conventional commits** `<type>(<scope>): <description>` — commit after every green+refactor. Use the `creating-git-commits` skill conventions.
- **Never let persisted `quadrant` drift from the `urgent`/`important` flags** (spec §7.8) — quadrant is always derived.

## File Structure (locked in by this plan)

```
gsd-android/
├── settings.gradle.kts, build.gradle.kts, gradle.properties
├── gradle/libs.versions.toml, gradle/wrapper/
├── CLAUDE.md                     # thin pointer → spec + coding-standards
├── gsd-android-spec.md           # behavior authority (exists)
├── coding-standards.md           # process authority (exists)
├── tasks/                        # plan.md (this), todo.md, implementation-notes.md, lessons.md
├── core/model/src/main/kotlin/dev/vinny/gsd/model/
│   ├── NanoId.kt                 # ID generation (§7.1)
│   ├── Quadrant.kt               # enum + derivation (§7.8)
│   ├── Task.kt                   # Task, Subtask, TimeEntry, Recurrence (§7.2)
│   ├── TaskValidator.kt          # limits + normalization (§7.11)
│   ├── TaskJson.kt               # lenient camelCase codec (§7.3)
│   └── CaptureParser.kt          # shorthand grammar + UrlSanitizer (§8.2)
├── core/model/src/test/kotlin/dev/vinny/gsd/model/   # mirrors above, *Test.kt
├── core/store/src/main/kotlin/dev/vinny/gsd/store/
│   ├── GsdDatabase.kt            # Room DB v1, all §7.10 tables
│   ├── TaskEntity.kt / TaskDao.kt
│   ├── SyncQueueEntity.kt / SyncQueueDao.kt
│   └── TaskStore.kt              # single mutation path, injected Clock, enqueue-before-write
├── core/store/src/androidTest/  # migration + DAO instrumented tests
├── core/sync/                    # empty stub module until Phase 5
└── app/src/main/kotlin/dev/vinny/gsd/
    ├── GsdApp.kt                 # Application + composition root
    ├── MainActivity.kt
    ├── theme/ (GsdColors.kt, GsdType.kt, GsdShapes.kt, GsdTheme.kt)
    ├── matrix/ (MatrixScreen.kt, QuadrantSection.kt, TaskCard.kt, ConfettiOverlay.kt)
    ├── capture/ (CaptureBar.kt)
    ├── editor/ (TaskEditorSheet.kt)
    └── nav/ (GsdNavHost.kt, Destinations.kt)
```

---

# PHASE 0 — Foundations

### Task 1: Git repo + Gradle multi-module scaffold

**Files:**
- Create: `.gitignore`, `CLAUDE.md`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `core/model/build.gradle.kts`, `core/store/build.gradle.kts`, `core/sync/build.gradle.kts`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, gradle wrapper files

**Interfaces:**
- Produces: module paths `:core:model`, `:core:store`, `:core:sync`, `:app`; version catalog aliases used by every later task (`libs.kotlinx.serialization.json`, `libs.room.runtime`, `libs.room.ksp`, `libs.androidx.compose.bom`, …).

- [ ] **Step 1: Init repo and commit the pre-existing docs**

```bash
cd /Users/vinnycarpenter/Projects/gsd-android
git init -b main
printf '%s\n' '.gradle/' 'build/' '*/build/' 'local.properties' '.DS_Store' '.kotlin/' '*.hprof' > .gitignore
```

`CLAUDE.md` (create):

```markdown
# GSD Android

Native Android GSD Task Manager (Kotlin + Jetpack Compose).

- **Behavior authority:** `gsd-android-spec.md` — every rule, limit, token, and protocol detail. When code and spec disagree, the spec wins.
- **Process authority:** `coding-standards.md` — TDD red/green/refactor, conventional commits, task tiers.
- **Plan:** `tasks/plan.md` · progress in `tasks/todo.md`.
- Modules: `:core:model` (pure JVM — keep it Android-free), `:core:store` (Room), `:core:sync` (Phase 5), `:app`.
- Fast gate: `./gradlew :core:model:test` — keep it sub-second.
```

Commit (conventions per `creating-git-commits` skill): `chore: add spec, coding standards, plan, and repo scaffolding docs`

- [ ] **Step 2: Write the version catalog** — `gradle/libs.versions.toml`

```toml
[versions]
agp = "9.2.0"
kotlin = "2.4.0"
ksp = "2.4.0-2.0.4"          # verify tag: curl -s https://api.github.com/repos/google/ksp/releases | grep -o '"tag_name": "2.4.0[^"]*"' | head -3
coroutines = "1.10.2"
serialization = "1.9.0"
room = "2.8.4"
datastore = "1.1.7"
activityCompose = "1.11.0"
navigationCompose = "2.9.0"
composeBom = "2026.06.00"
coreSplashscreen = "1.0.1"

[libraries]
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test-junit", version.ref = "kotlin" }
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
room-testing = { module = "androidx.room:room-testing", version.ref = "room" }
datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigationCompose" }
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }
core-splashscreen = { module = "androidx.core:core-splashscreen", version.ref = "coreSplashscreen" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

- [ ] **Step 3: Write settings + root build files**

`settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "gsd-android"
include(":core:model", ":core:store", ":core:sync", ":app")
```

`build.gradle.kts` (root):

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
```

`gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx4g
android.useAndroidX=true
kotlin.code.style=official
```

- [ ] **Step 4: Write the four module build files**

`core/model/build.gradle.kts` — **pure JVM, no Android plugin** (this is the enforcement of the spec's zero-Android rule):

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

`core/store/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}
android {
    namespace = "dev.vinny.gsd.store"
    compileSdk = 36
    defaultConfig {
        minSdk = 31
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    kotlin { jvmToolchain(17) }
}
room { schemaDirectory("$projectDir/schemas") }
dependencies {
    api(project(":core:model"))
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.kotlin.test)
}
```

(If the `room {}` block needs the plugin: add `androidx.room` gradle plugin to the catalog at the Room version and apply it; otherwise use `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`.)

`core/sync/build.gradle.kts` — stub (same shape as `:core:store` minus Room, `namespace = "dev.vinny.gsd.sync"`, deps: `api(project(":core:store"))`). No source yet.

`app/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "dev.vinny.gsd"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.vinny.gsd"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    kotlin { jvmToolchain(17) }
    buildFeatures { compose = true }
}
dependencies {
    implementation(project(":core:store"))
    implementation(project(":core:sync"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.core.splashscreen)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}
```

`app/src/main/AndroidManifest.xml` (minimal for now; deep links/permissions come in their phases):

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:name=".GsdApp" android:label="GSD"
        android:supportsRtl="true" android:enableOnBackInvokedCallback="true">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

Placeholder `GsdApp.kt` / `MainActivity.kt` (fleshed out in Task 10):

```kotlin
// app/src/main/kotlin/dev/vinny/gsd/GsdApp.kt
package dev.vinny.gsd
import android.app.Application
class GsdApp : Application()
```

```kotlin
// app/src/main/kotlin/dev/vinny/gsd/MainActivity.kt
package dev.vinny.gsd
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text("GSD") }
    }
}
```

- [ ] **Step 5: Generate wrapper and verify the build**

```bash
gradle wrapper --gradle-version 9.6.1   # uses system Gradle once, only to create the wrapper
./gradlew projects                       # Expected: lists :app, :core:model, :core:store, :core:sync
./gradlew :core:model:test :app:assembleDebug   # Expected: BUILD SUCCESSFUL (no tests yet)
```

If AGP 9.2.0 rejects JDK 17 or Gradle 9.6.1, follow its error message (install `openjdk@21` / bump wrapper) and record the deviation in `tasks/implementation-notes.md`.

- [ ] **Step 6: Commit**

```bash
git add .gitignore CLAUDE.md settings.gradle.kts build.gradle.kts gradle.properties gradle/ gradlew gradlew.bat core/ app/
git commit -m "chore: scaffold four-module gradle project (agp 9.2, kotlin 2.4, compose bom 2026.06)"
```

---

### Task 2: NanoId generation (`:core:model`)

**Files:**
- Create: `core/model/src/main/kotlin/dev/vinny/gsd/model/NanoId.kt`
- Test: `core/model/src/test/kotlin/dev/vinny/gsd/model/NanoIdTest.kt`

**Interfaces:**
- Produces: `object NanoId { const val ALPHABET: String; fun generate(length: Int = 21, random: Random = secure): String }` — spec §7.1: alphabet `A-Za-z0-9_-`, lengths 21 (tasks/subtasks), 8 (time entries), 12 (smart views).

- [ ] **Step 1: Write the failing tests**

```kotlin
package dev.vinny.gsd.model

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NanoIdTest {
    @Test fun `default length is 21`() = assertEquals(21, NanoId.generate().length)

    @Test fun `explicit lengths are honored`() {
        assertEquals(8, NanoId.generate(8).length)
        assertEquals(12, NanoId.generate(12).length)
    }

    @Test fun `only url-safe alphabet characters`() {
        val allowed = ('A'..'Z') + ('a'..'z') + ('0'..'9') + listOf('-', '_')
        repeat(100) { NanoId.generate().forEach { c -> assertTrue(c in allowed, "bad char $c") } }
    }

    @Test fun `seeded random is deterministic`() {
        assertEquals(NanoId.generate(21, Random(42)), NanoId.generate(21, Random(42)))
    }

    @Test fun `no collisions in a 10k sample`() {
        val ids = (1..10_000).map { NanoId.generate() }.toSet()
        assertEquals(10_000, ids.size)
    }
}
```

- [ ] **Step 2: Run to verify failure** — `./gradlew :core:model:test` — Expected: FAIL, `Unresolved reference: NanoId`.

- [ ] **Step 3: Minimal implementation**

```kotlin
package dev.vinny.gsd.model

import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

object NanoId {
    const val ALPHABET: String =
        "useandom-26T198340PX75pxJACKVERYMINDBUSHWOLF_GQZbfghjklqvwyzrict"
    // ^ nanoid's shuffled url-safe alphabet; any order works — must be exactly the 64 chars 0-9A-Za-z-_

    private val secure: Random = SecureRandom().asKotlinRandom()

    fun generate(length: Int = 21, random: Random = secure): String =
        buildString(length) { repeat(length) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }
}
```

(Character *set* is what matters for the contract, not order — the alphabet test asserts membership. If the shuffled literal offends, use `"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"`.)

- [ ] **Step 4: Run to verify pass** — `./gradlew :core:model:test` — Expected: PASS (5 tests).

- [ ] **Step 5: Commit** — `git add core/model && git commit -m "feat(model): url-safe nanoid generation with injectable randomness"`

---

### Task 3: Quadrant enum + derivation (`:core:model`)

**Files:**
- Create: `core/model/src/main/kotlin/dev/vinny/gsd/model/Quadrant.kt`
- Test: `core/model/src/test/kotlin/dev/vinny/gsd/model/QuadrantTest.kt`

**Interfaces:**
- Produces: `enum class Quadrant(val id: String, val title: String)` with entries `DO_FIRST, SCHEDULE, DELEGATE, ELIMINATE` (declaration order = display order Q1→Q4, spec §7.8), `companion object { fun from(urgent: Boolean, important: Boolean): Quadrant; fun fromId(id: String): Quadrant? }`.

- [ ] **Step 1: Write the failing tests** — the §7.8 truth table, verbatim:

```kotlin
package dev.vinny.gsd.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QuadrantTest {
    @Test fun `derivation matches the spec truth table`() {
        assertEquals(Quadrant.DO_FIRST, Quadrant.from(urgent = true, important = true))
        assertEquals(Quadrant.SCHEDULE, Quadrant.from(urgent = false, important = true))
        assertEquals(Quadrant.DELEGATE, Quadrant.from(urgent = true, important = false))
        assertEquals(Quadrant.ELIMINATE, Quadrant.from(urgent = false, important = false))
    }

    @Test fun `wire ids are authoritative`() {
        assertEquals("urgent-important", Quadrant.DO_FIRST.id)
        assertEquals("not-urgent-important", Quadrant.SCHEDULE.id)
        assertEquals("urgent-not-important", Quadrant.DELEGATE.id)
        assertEquals("not-urgent-not-important", Quadrant.ELIMINATE.id)
    }

    @Test fun `display order is Q1 to Q4`() =
        assertEquals(listOf(Quadrant.DO_FIRST, Quadrant.SCHEDULE, Quadrant.DELEGATE, Quadrant.ELIMINATE),
            Quadrant.entries.toList())

    @Test fun `fromId round-trips and rejects junk`() {
        Quadrant.entries.forEach { assertEquals(it, Quadrant.fromId(it.id)) }
        assertNull(Quadrant.fromId("yearly-important"))
    }
}
```

- [ ] **Step 2: Run to verify failure** — Expected: FAIL, unresolved `Quadrant`.

- [ ] **Step 3: Minimal implementation**

```kotlin
package dev.vinny.gsd.model

enum class Quadrant(val id: String, val title: String) {
    DO_FIRST("urgent-important", "Do First"),
    SCHEDULE("not-urgent-important", "Schedule"),
    DELEGATE("urgent-not-important", "Delegate"),
    ELIMINATE("not-urgent-not-important", "Eliminate");

    companion object {
        fun from(urgent: Boolean, important: Boolean): Quadrant = when {
            urgent && important -> DO_FIRST
            !urgent && important -> SCHEDULE
            urgent -> DELEGATE
            else -> ELIMINATE
        }
        fun fromId(id: String): Quadrant? = entries.firstOrNull { it.id == id }
    }
}
```

- [ ] **Step 4: Run to verify pass** — `./gradlew :core:model:test` — Expected: PASS.
- [ ] **Step 5: Commit** — `git commit -m "feat(model): quadrant enum with flag derivation per spec 7.8"`

---

### Task 4: Domain types — Task, Subtask, TimeEntry, Recurrence (`:core:model`)

**Files:**
- Create: `core/model/src/main/kotlin/dev/vinny/gsd/model/Task.kt`
- Test: `core/model/src/test/kotlin/dev/vinny/gsd/model/TaskTest.kt`

**Interfaces:**
- Consumes: `NanoId.generate()`, `Quadrant.from()`.
- Produces (exact shapes later tasks rely on — spec §7.2):

```kotlin
enum class Recurrence(val wire: String) { NONE("none"), DAILY("daily"), WEEKLY("weekly"), MONTHLY("monthly") }
data class Subtask(val id: String, val title: String, val completed: Boolean = false)
data class TimeEntry(val id: String, val startedAt: Instant, val endedAt: Instant? = null, val notes: String? = null)
data class Task(
    val id: String = NanoId.generate(),
    val title: String,
    val description: String = "",
    val urgent: Boolean,
    val important: Boolean,
    val completed: Boolean = false,
    val completedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val dueDate: Instant? = null,
    val recurrence: Recurrence = Recurrence.NONE,
    val tags: List<String> = emptyList(),
    val subtasks: List<Subtask> = emptyList(),
    val dependencies: List<String> = emptyList(),
    val parentTaskId: String? = null,          // device-local, never synced
    val notifyBefore: Int? = null,
    val notificationEnabled: Boolean = true,
    val notificationSent: Boolean = false,     // device-local
    val lastNotificationAt: Instant? = null,   // device-local
    val snoozedUntil: Instant? = null,         // device-local
    val estimatedMinutes: Int? = null,
    val timeEntries: List<TimeEntry> = emptyList(),
) { val quadrant: Quadrant get() = Quadrant.from(urgent, important)
    val timeSpent: Int get() = timeEntries.sumOf { e -> e.endedAt?.let { ((it.toEpochMilli() - e.startedAt.toEpochMilli()) / 60_000L).toInt().coerceAtLeast(0) } ?: 0 } }
```

Note: `createdAt`/`updatedAt` have **no defaults** — the store stamps them via its injected clock (spec §7.2); forcing callers to pass them keeps time out of the domain layer.

- [ ] **Step 1: Write the failing tests**

```kotlin
package dev.vinny.gsd.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class TaskTest {
    private val t0 = Instant.parse("2026-06-15T08:30:00.500Z")
    private fun task(urgent: Boolean = false, important: Boolean = false) =
        Task(title = "t", urgent = urgent, important = important, createdAt = t0, updatedAt = t0)

    @Test fun `quadrant is derived from flags, never stored`() {
        assertEquals(Quadrant.DO_FIRST, task(urgent = true, important = true).quadrant)
        assertEquals(Quadrant.ELIMINATE, task().quadrant)
    }

    @Test fun `defaults match spec 7-2`() {
        val t = task()
        assertEquals("", t.description); assertEquals(false, t.completed)
        assertEquals(Recurrence.NONE, t.recurrence); assertEquals(emptyList(), t.tags)
        assertEquals(true, t.notificationEnabled); assertEquals(false, t.notificationSent)
    }

    @Test fun `timeSpent sums floor-minutes of completed entries only`() {
        val t = task().copy(timeEntries = listOf(
            TimeEntry("aaaa1111", t0, t0.plusSeconds(150)),   // 2.5 min -> 2
            TimeEntry("bbbb2222", t0, null),                  // running -> 0
        ))
        assertEquals(2, t.timeSpent)
    }
}
```

- [ ] **Step 2: Run to verify failure** — Expected: FAIL, unresolved `Task`.
- [ ] **Step 3: Implement** the types exactly as the Interfaces block above (it is the full implementation).
- [ ] **Step 4: Run to verify pass** — `./gradlew :core:model:test` — Expected: PASS.
- [ ] **Step 5: Commit** — `git commit -m "feat(model): task domain types with derived quadrant and timeSpent"`

---

### Task 5: Validation + normalization (`:core:model`)

**Files:**
- Create: `core/model/src/main/kotlin/dev/vinny/gsd/model/TaskValidator.kt`
- Test: `core/model/src/test/kotlin/dev/vinny/gsd/model/TaskValidatorTest.kt`

**Interfaces:**
- Produces:

```kotlin
sealed class TaskValidationError(message: String) : Exception(message) {
    class TitleLength : TaskValidationError("Title must be 1–80 characters")
    class DescriptionLength : TaskValidationError("Description is limited to 600 characters")
    class TooManyTags : TaskValidationError("At most 20 tags")
    class TagLength(tag: String) : TaskValidationError("Tag \"$tag\" must be 1–30 characters")
    class TooManySubtasks : TaskValidationError("At most 50 subtasks")
    class SubtaskTitleLength : TaskValidationError("Subtask titles must be 1–100 characters")
    class TooManyDependencies : TaskValidationError("At most 50 dependencies")
    class EstimateRange : TaskValidationError("Estimate must be 1–10080 minutes")
    class TooManyTimeEntries : TaskValidationError("At most 1000 time entries")
    class NoteLength : TaskValidationError("Time entry notes are limited to 200 characters")
    class IdTooShort : TaskValidationError("IDs must be at least 4 characters")
}
object TaskValidator {
    fun normalizeTags(raw: List<String>): List<String>   // trim, lowercase, drop empty, dedupe (order-preserving)
    fun validate(task: Task, now: Instant): Task          // returns normalized copy or throws TaskValidationError
}
```

Normalization applied by `validate` (spec §7.11): tags normalized; `estimatedMinutes == 0` → `null`; `snoozedUntil` clamped to `now + 365 days`. Everything else throws on violation — never silent truncation.

- [ ] **Step 1: Write the failing tests**

```kotlin
package dev.vinny.gsd.model

import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TaskValidatorTest {
    private val now = Instant.parse("2026-07-19T12:00:00.000Z")
    private fun task(title: String = "ok") =
        Task(title = title, urgent = false, important = false, createdAt = now, updatedAt = now)

    @Test fun `tags are trimmed lowercased deduped and empties dropped`() =
        assertEquals(listOf("auth", "work"), TaskValidator.normalizeTags(listOf(" Auth", "AUTH", "work", "  ")))

    @Test fun `title bounds 1-80`() {
        assertFailsWith<TaskValidationError.TitleLength> { TaskValidator.validate(task(""), now) }
        assertFailsWith<TaskValidationError.TitleLength> { TaskValidator.validate(task("x".repeat(81)), now) }
        TaskValidator.validate(task("x".repeat(80)), now) // no throw
    }

    @Test fun `description cap 600`() {
        assertFailsWith<TaskValidationError.DescriptionLength> {
            TaskValidator.validate(task().copy(description = "d".repeat(601)), now) }
    }

    @Test fun `tag count cap 20 and length 1-30`() {
        assertFailsWith<TaskValidationError.TooManyTags> {
            TaskValidator.validate(task().copy(tags = (1..21).map { "t$it" }), now) }
        assertFailsWith<TaskValidationError.TagLength> {
            TaskValidator.validate(task().copy(tags = listOf("x".repeat(31))), now) }
    }

    @Test fun `subtask caps 50 items and 1-100 title`() {
        assertFailsWith<TaskValidationError.TooManySubtasks> {
            TaskValidator.validate(task().copy(subtasks = (1..51).map { Subtask("id$it", "s") }), now) }
        assertFailsWith<TaskValidationError.SubtaskTitleLength> {
            TaskValidator.validate(task().copy(subtasks = listOf(Subtask("abcd", ""))), now) }
    }

    @Test fun `dependency cap 50`() {
        assertFailsWith<TaskValidationError.TooManyDependencies> {
            TaskValidator.validate(task().copy(dependencies = (1..51).map { "dep-$it" }), now) }
    }

    @Test fun `estimate 0 coerces to unset, range 1-10080 enforced`() {
        assertEquals(null, TaskValidator.validate(task().copy(estimatedMinutes = 0), now).estimatedMinutes)
        assertFailsWith<TaskValidationError.EstimateRange> {
            TaskValidator.validate(task().copy(estimatedMinutes = 10081), now) }
    }

    @Test fun `snooze clamped to 365 days out`() {
        val tooFar = now.plus(400, ChronoUnit.DAYS)
        assertEquals(now.plus(365, ChronoUnit.DAYS),
            TaskValidator.validate(task().copy(snoozedUntil = tooFar), now).snoozedUntil)
    }

    @Test fun `id shorter than 4 rejected`() {
        assertFailsWith<TaskValidationError.IdTooShort> { TaskValidator.validate(task().copy(id = "abc"), now) }
    }

    @Test fun `time entry caps`() {
        assertFailsWith<TaskValidationError.NoteLength> {
            TaskValidator.validate(task().copy(timeEntries =
                listOf(TimeEntry("abcd1234", now, now, notes = "n".repeat(201)))), now) }
    }
}
```

- [ ] **Step 2: Run to verify failure** — Expected: FAIL, unresolved `TaskValidator`.

- [ ] **Step 3: Minimal implementation**

```kotlin
package dev.vinny.gsd.model

import java.time.Instant
import java.time.temporal.ChronoUnit

object TaskValidator {
    fun normalizeTags(raw: List<String>): List<String> =
        raw.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()

    fun validate(task: Task, now: Instant): Task {
        if (task.id.length < 4) throw TaskValidationError.IdTooShort()
        if (task.title.isEmpty() || task.title.length > 80) throw TaskValidationError.TitleLength()
        if (task.description.length > 600) throw TaskValidationError.DescriptionLength()
        val tags = normalizeTags(task.tags)
        if (tags.size > 20) throw TaskValidationError.TooManyTags()
        tags.firstOrNull { it.length > 30 }?.let { throw TaskValidationError.TagLength(it) }
        if (task.subtasks.size > 50) throw TaskValidationError.TooManySubtasks()
        if (task.subtasks.any { it.title.isEmpty() || it.title.length > 100 }) throw TaskValidationError.SubtaskTitleLength()
        if (task.dependencies.size > 50) throw TaskValidationError.TooManyDependencies()
        val estimate = task.estimatedMinutes?.takeIf { it != 0 }
        if (estimate != null && estimate !in 1..10_080) throw TaskValidationError.EstimateRange()
        if (task.timeEntries.size > 1000) throw TaskValidationError.TooManyTimeEntries()
        if (task.timeEntries.any { (it.notes?.length ?: 0) > 200 }) throw TaskValidationError.NoteLength()
        val snoozeCap = now.plus(365, ChronoUnit.DAYS)
        val snooze = task.snoozedUntil?.let { if (it.isAfter(snoozeCap)) snoozeCap else it }
        return task.copy(tags = tags, estimatedMinutes = estimate, snoozedUntil = snooze)
    }
}
```

(`TaskValidationError` sealed class exactly as the Interfaces block — it lives in the same file.)

- [ ] **Step 4: Run to verify pass** — `./gradlew :core:model:test` — Expected: PASS.
- [ ] **Step 5: Commit** — `git commit -m "feat(model): task validation and normalization per appendix b limits"`

---

### Task 6: JSON codec — lenient camelCase Task serialization (`:core:model`)

**Files:**
- Create: `core/model/src/main/kotlin/dev/vinny/gsd/model/TaskJson.kt`
- Test: `core/model/src/test/kotlin/dev/vinny/gsd/model/TaskJsonTest.kt`

**Interfaces:**
- Produces: `object TaskJson { val json: Json; fun encode(task: Task): String; fun decodeOrNull(raw: JsonElement): Task? }` plus `object InstantIso8601Serializer : KSerializer<Instant>` (always emits UTC `Z`-suffixed, millisecond precision — spec §7.3). Used by the Room converters (Task 7) and import/export (Phase 3).
- Encoding rules (spec §7.3): camelCase; **`quadrant` never encoded** (it's a derived `get()` — kotlinx.serialization skips it automatically); null optionals omitted (`explicitNulls = false`); empty string/array encoded; unknown keys ignored.
- Lenient decode: required = `id, title, urgent, important, createdAt, updatedAt`; all else defaults. A record failing decode returns `null` (callers count skips — spec §7.3).

- [ ] **Step 1: Write the failing tests**

```kotlin
package dev.vinny.gsd.model

import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskJsonTest {
    private val t0 = Instant.parse("2026-06-15T08:30:00.500Z")
    private val task = Task(id = "abcdefghijklmnopqrstu", title = "Ship", urgent = true,
        important = true, createdAt = t0, updatedAt = t0)

    @Test fun `dates encode as iso-8601 utc with milliseconds`() {
        assertTrue("""2026-06-15T08:30:00.500Z""" in TaskJson.encode(task))
    }

    @Test fun `quadrant is never encoded`() {
        val obj = Json.parseToJsonElement(TaskJson.encode(task)).jsonObject
        assertNull(obj["quadrant"])
    }

    @Test fun `null optionals are omitted, empties are kept`() {
        val obj = Json.parseToJsonElement(TaskJson.encode(task)).jsonObject
        assertNull(obj["dueDate"]); assertNull(obj["completedAt"])
        assertEquals("\"\"", obj["description"].toString())
        assertEquals("[]", obj["tags"].toString())
    }

    @Test fun `lenient decode fills defaults from required-only record`() {
        val raw = Json.parseToJsonElement(
            """{"id":"abcd","title":"t","urgent":false,"important":true,
                "createdAt":"2026-06-15T08:30:00.500Z","updatedAt":"2026-06-15T08:30:00.500Z"}""")
        val t = TaskJson.decodeOrNull(raw)!!
        assertEquals("", t.description); assertEquals(false, t.completed)
        assertEquals(Recurrence.NONE, t.recurrence); assertEquals(true, t.notificationEnabled)
    }

    @Test fun `unknown keys are ignored`() {
        val raw = Json.parseToJsonElement(
            """{"id":"abcd","title":"t","urgent":false,"important":false,
                "createdAt":"2026-06-15T08:30:00.500Z","updatedAt":"2026-06-15T08:30:00.500Z",
                "vectorClock":{"a":1}}""")
        assertEquals("t", TaskJson.decodeOrNull(raw)!!.title)
    }

    @Test fun `missing required field yields null, not an exception`() {
        assertNull(TaskJson.decodeOrNull(Json.parseToJsonElement("""{"title":"no id"}""")))
    }

    @Test fun `round-trip preserves everything including parentTaskId`() {
        val full = task.copy(dueDate = t0, tags = listOf("work"), parentTaskId = "parentid1234",
            subtasks = listOf(Subtask("subid", "s", true)),
            timeEntries = listOf(TimeEntry("abcd1234", t0, t0.plusSeconds(120), "note")))
        assertEquals(full, TaskJson.decodeOrNull(Json.parseToJsonElement(TaskJson.encode(full))))
    }
}
```

- [ ] **Step 2: Run to verify failure** — Expected: FAIL, unresolved `TaskJson`.

- [ ] **Step 3: Implement** — annotate the Task-family types `@Serializable` (with `@Serializable(with = InstantIso8601Serializer::class)` on every `Instant`), then:

```kotlin
package dev.vinny.gsd.model

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

object InstantIso8601Serializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) {
        // Always UTC, always exactly millisecond precision (spec §7.3)
        val ms = value.truncatedTo(ChronoUnit.MILLIS)
        encoder.encodeString(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .format(ms.atZone(java.time.ZoneOffset.UTC)))
    }
    override fun deserialize(decoder: Decoder): Instant =
        java.time.OffsetDateTime.parse(decoder.decodeString()).toInstant()
}

object TaskJson {
    val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }
    fun encode(task: Task): String = json.encodeToString(Task.serializer(), task)
    fun decodeOrNull(raw: JsonElement): Task? =
        try { json.decodeFromJsonElement(Task.serializer(), raw) } catch (_: Exception) { null }
}
```

Swallowing the decode exception here is the spec's explicit lenient-import rule (§7.3: "skipped and counted, never aborts the batch") — the *caller* counts the null; this is a deliberate boundary, not error suppression.

- [ ] **Step 4: Run to verify pass** — `./gradlew :core:model:test` — Expected: PASS.
- [ ] **Step 5: Commit** — `git commit -m "feat(model): lenient camelcase json codec with iso-8601 ms instants"`

---

### Task 7: Room store v1 + TaskStore mutation path (`:core:store`)

**Files:**
- Create: `core/store/src/main/kotlin/dev/vinny/gsd/store/TaskEntity.kt`, `TaskDao.kt`, `SyncQueueEntity.kt`, `SyncQueueDao.kt`, `GsdDatabase.kt`, `TaskStore.kt`
- Test: `core/store/src/androidTest/kotlin/dev/vinny/gsd/store/TaskStoreTest.kt`

**Interfaces:**
- Consumes: `Task`, `TaskValidator.validate(task, now)`, `TaskJson.json` (collection columns are JSON TEXT — spec §7.10).
- Produces (what all UI and later sync code calls — **the only mutation path in the app**):

```kotlin
class TaskStore(private val db: GsdDatabase, private val clock: java.time.Clock) {
    fun observeTasks(): Flow<List<Task>>                  // all active tasks, updatedAt-stamped order irrelevant (UI sorts)
    suspend fun get(id: String): Task?
    suspend fun add(task: Task): Task                     // validates, stamps createdAt/updatedAt, enqueues CREATE
    suspend fun update(task: Task): Task                  // validates, stamps updatedAt, enqueues UPDATE
    suspend fun setCompleted(id: String, completed: Boolean): Task?  // sets completed/completedAt, enqueues UPDATE
    suspend fun delete(id: String): Task?                 // removes id from all dependencies first (§8.8), enqueues DELETE, returns snapshot for undo
}
enum class SyncOp { CREATE, UPDATE, DELETE }
```

- Room DB **version 1** declares every §7.10 table (`tasks`, `archived_tasks`, `smart_views`, `notification_settings`, `archive_settings`, `app_preferences`, `sync_queue`, `sync_metadata`, `sync_history`) so later phases add *rows*, not migrations. Only `tasks` + `sync_queue` get DAOs now — the other entity classes are columns-per-spec but dormant. Indices: `quadrant`, `completed`, `updatedAt`, `dueDate`.
- **Enqueue-before-write invariant (spec §9.4):** every mutation inserts its `sync_queue` row *and* the task row inside one `db.withTransaction {}` — queue row first.
- Unrecognized `recurrence` in a row degrades to `NONE`, never fails the fetch (spec §7.10).

- [ ] **Step 1: Write the failing instrumented tests** (in-memory DB; start an emulator first: `emulator -avd <name>` or via Android Studio)

```kotlin
package dev.vinny.gsd.store

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import kotlin.test.*
import dev.vinny.gsd.model.*

@RunWith(AndroidJUnit4::class)
class TaskStoreTest {
    private val fixed = Clock.fixed(Instant.parse("2026-07-19T12:00:00.000Z"), ZoneOffset.UTC)
    private lateinit var db: GsdDatabase
    private lateinit var store: TaskStore

    @BeforeTest fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), GsdDatabase::class.java).build()
        store = TaskStore(db, fixed)
    }
    @AfterTest fun tearDown() = db.close()

    private fun draft(title: String = "t") =
        Task(title = title, urgent = false, important = false,
             createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)  // store re-stamps

    @Test fun addStampsClockAndEnqueuesCreate() = runTest {
        val added = store.add(draft())
        assertEquals(fixed.instant(), added.createdAt)
        assertEquals(fixed.instant(), added.updatedAt)
        val queue = db.syncQueueDao().pending()
        assertEquals(1, queue.size)
        assertEquals(SyncOp.CREATE, queue.single().operation)
        assertEquals(added.id, queue.single().taskId)
    }

    @Test fun observeEmitsAfterMutation() = runTest {
        store.add(draft("visible"))
        assertEquals("visible", store.observeTasks().first().single().title)
    }

    @Test fun collectionsRoundTripThroughJsonColumns() = runTest {
        val rich = draft().copy(tags = listOf("work"), subtasks = listOf(Subtask("subid", "s")),
            dependencies = listOf("dep-id-1"), timeEntries = listOf(TimeEntry("abcd1234", fixed.instant())))
        val loaded = store.get(store.add(rich).id)!!
        assertEquals(listOf("work"), loaded.tags)
        assertEquals("s", loaded.subtasks.single().title)
        assertEquals("dep-id-1", loaded.dependencies.single())
        assertEquals("abcd1234", loaded.timeEntries.single().id)
    }

    @Test fun setCompletedStampsCompletedAt() = runTest {
        val id = store.add(draft()).id
        assertEquals(fixed.instant(), store.setCompleted(id, true)!!.completedAt)
        assertNull(store.setCompleted(id, false)!!.completedAt)
    }

    @Test fun deleteScrubsDependenciesAndEnqueuesDelete() = runTest {
        val blocker = store.add(draft("blocker"))
        val blocked = store.add(draft("blocked").copy(dependencies = listOf(blocker.id)))
        store.delete(blocker.id)
        assertEquals(emptyList(), store.get(blocked.id)!!.dependencies)
        assertTrue(db.syncQueueDao().pending().any { it.operation == SyncOp.DELETE && it.taskId == blocker.id })
    }

    @Test fun validationFailureLeavesNoRowAndNoQueueEntry() = runTest {
        assertFailsWith<TaskValidationError.TitleLength> { store.add(draft(title = "")) }
        assertEquals(0, store.observeTasks().first().size)
        assertEquals(0, db.syncQueueDao().pending().size)
    }
}
```

- [ ] **Step 2: Run to verify failure** — `./gradlew :core:store:connectedAndroidTest` — Expected: compile FAIL (unresolved `GsdDatabase`).

- [ ] **Step 3: Implement** — key excerpts (entity/DAO boilerplate follows directly):

```kotlin
// TaskEntity.kt — collections as JSON TEXT (spec §7.10), quadrant persisted-but-derived
@Entity(tableName = "tasks",
    indices = [Index("quadrant"), Index("completed"), Index("updatedAt"), Index("dueDate")])
data class TaskEntity(
    @PrimaryKey val id: String, val title: String, val description: String,
    val urgent: Boolean, val important: Boolean, val quadrant: String,
    val completed: Boolean, val completedAt: Long?, val createdAt: Long, val updatedAt: Long,
    val dueDate: Long?, val recurrence: String, val tagsJson: String, val subtasksJson: String,
    val dependenciesJson: String, val parentTaskId: String?, val notifyBefore: Int?,
    val notificationEnabled: Boolean, val notificationSent: Boolean, val lastNotificationAt: Long?,
    val snoozedUntil: Long?, val estimatedMinutes: Int?, val timeEntriesJson: String,
)
// Mapping Task <-> TaskEntity lives in TaskEntity.kt as toEntity()/toTask() extensions using
// TaskJson.json for the collection columns; recurrence decode falls back to NONE.
```

```kotlin
// TaskStore.kt — the enqueue-before-write invariant in one place
class TaskStore(private val db: GsdDatabase, private val clock: Clock) {
    fun observeTasks(): Flow<List<Task>> = db.taskDao().observeAll().map { rows -> rows.map { it.toTask() } }
    suspend fun get(id: String): Task? = db.taskDao().byId(id)?.toTask()

    suspend fun add(task: Task): Task {
        val now = clock.instant()
        val stamped = TaskValidator.validate(task, now).copy(createdAt = now, updatedAt = now)
        db.withTransaction {
            db.syncQueueDao().insert(SyncQueueEntity.forTask(SyncOp.CREATE, stamped, now))
            db.taskDao().upsert(stamped.toEntity())
        }
        return stamped
    }

    suspend fun update(task: Task): Task {
        val now = clock.instant()
        val stamped = TaskValidator.validate(task, now).copy(updatedAt = now)
        db.withTransaction {
            db.syncQueueDao().insert(SyncQueueEntity.forTask(SyncOp.UPDATE, stamped, now))
            db.taskDao().upsert(stamped.toEntity())
        }
        return stamped
    }

    suspend fun setCompleted(id: String, completed: Boolean): Task? {
        val current = get(id) ?: return null
        return update(current.copy(completed = completed,
            completedAt = if (completed) clock.instant() else null))
    }

    suspend fun delete(id: String): Task? {
        val snapshot = get(id) ?: return null
        val now = clock.instant()
        db.withTransaction {
            db.taskDao().all().filter { id in TaskJson.decodeIds(it.dependenciesJson) }.forEach { row ->
                val trimmed = TaskJson.decodeIds(row.dependenciesJson) - id
                db.syncQueueDao().insert(SyncQueueEntity.forEntity(SyncOp.UPDATE, row.id, now))
                db.taskDao().upsert(row.copy(dependenciesJson = TaskJson.encodeIds(trimmed),
                    updatedAt = now.toEpochMilli()))
            }
            db.syncQueueDao().insert(SyncQueueEntity.tombstone(id, now))
            db.taskDao().deleteById(id)
        }
        return snapshot
    }
}
```

(`TaskJson.decodeIds`/`encodeIds` are two-line `List<String>` JSON helpers added to `TaskJson`; `SyncQueueEntity` carries `{id, taskId, operation, timestamp, retryCount = 0, payloadJson?, status = "pending"}` per spec §9.5 — `forTask` serializes the full task as payload, `tombstone` has a null payload. Dormant §7.10 entities are plain `@Entity` classes with the spec's fields, registered in `GsdDatabase(version = 1, exportSchema = true)`.)

- [ ] **Step 4: Run to verify pass** — `./gradlew :core:store:connectedAndroidTest` — Expected: PASS (7 tests). Also verify `core/store/schemas/…/1.json` was exported and commit it (migration tests diff against it forever).
- [ ] **Step 5: Commit** — `git commit -m "feat(store): room v1 schema and taskstore with enqueue-before-write"`

---

### Task 8: Theme system — GSD tokens as Compose theme (`:app`)

**Files:**
- Create: `app/src/main/kotlin/dev/vinny/gsd/theme/GsdColors.kt`, `GsdType.kt`, `GsdShapes.kt`, `GsdTheme.kt`; `app/src/main/res/font/newsreader.ttf`, `newsreader_italic.ttf`

**Interfaces:**
- Produces: `GsdTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit)`; `LocalGsdColors: CompositionLocal<GsdColors>` exposing the **full token set** (`paper, sunken, surface, surface2, hairline, hairlineStrong, ink, ink2, ink3, inkOnAccent, shadow, success, alert, alertWash, tint`, plus `accent(quadrant)` and `wash(quadrant)`); typography roles `GsdType.largeTitle/title/quadrantTitle/cardTitle/body/subhead/metadata/caption`; shape tokens `GsdShapes.tile/small/input/card/sheet` (8/12/16/22/26 dp).
- **Hex values come verbatim from spec §5.1–§5.3 — copy every table value exactly; do not adjust.** MaterialTheme mapping per §5.3: `primary = tint`, `background = paper`, `surface = surface`, `surfaceVariant = sunken`, `outline = hairlineStrong`, `outlineVariant = hairline`, `error = alert`, `onBackground/onSurface = ink`, `onSurfaceVariant = ink2`, `onPrimary = inkOnAccent`. Tonal elevation 0 everywhere.

- [ ] **Step 1: Download the Newsreader variable fonts** (SIL OFL — bundling is licensed):

```bash
curl -L -o app/src/main/res/font/newsreader.ttf \
  "https://github.com/google/fonts/raw/main/ofl/newsreader/Newsreader%5Bopsz%2Cwght%5D.ttf"
curl -L -o app/src/main/res/font/newsreader_italic.ttf \
  "https://github.com/google/fonts/raw/main/ofl/newsreader/Newsreader-Italic%5Bopsz%2Cwght%5D.ttf"
```

- [ ] **Step 2: Write a failing Compose UI test** — `app/src/androidTest/kotlin/dev/vinny/gsd/theme/GsdThemeTest.kt`:

```kotlin
package dev.vinny.gsd.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class GsdThemeTest {
    @get:Rule val rule = createComposeRule()

    @Test fun lightSchemeMapsGsdTokens() {
        var primary = Color.Unspecified; var background = Color.Unspecified; var paper = Color.Unspecified
        rule.setContent { GsdTheme(darkTheme = false) {
            primary = MaterialTheme.colorScheme.primary
            background = MaterialTheme.colorScheme.background
            paper = LocalGsdColors.current.paper } }
        assertEquals(Color(0xFF2C6680), primary)      // tint = Tide (spec §5.3)
        assertEquals(Color(0xFFF4F1E9), background)   // paper (spec §5.2)
        assertEquals(background, paper)
    }

    @Test fun darkSchemeMapsGsdTokens() {
        var primary = Color.Unspecified; var ink = Color.Unspecified
        rule.setContent { GsdTheme(darkTheme = true) {
            primary = MaterialTheme.colorScheme.primary
            ink = LocalGsdColors.current.ink } }
        assertEquals(Color(0xFF6FAACB), primary)
        assertEquals(Color(0xFFF1ECE2), ink)
    }
}
```

- [ ] **Step 3: Run to verify failure** — `./gradlew :app:connectedAndroidTest` — Expected: compile FAIL (unresolved `GsdTheme`).

- [ ] **Step 4: Implement** — `GsdColors` as an `@Immutable data class` with light/dark instances holding every §5.2/§5.3 value plus `accent(q: Quadrant)`/`wash(q: Quadrant)` from §5.1; `GsdTheme` builds the Material `lightColorScheme()/darkColorScheme()` from the mapping above, provides `LocalGsdColors`, and wraps content in `CompositionLocalProvider`. `GsdType` defines the §5.4 roles (Newsreader `FontFamily` for display roles, `FontFamily.SansSerif` otherwise, tabular-figures `FontFeatureSettings` where the spec says monospaced digits). `GsdShapes` holds the five `RoundedCornerShape`s.

- [ ] **Step 5: Run to verify pass** — `./gradlew :app:connectedAndroidTest` — Expected: PASS.
- [ ] **Step 6: Commit** — `git commit -m "feat(app): gsd theme tokens, newsreader type, material3 mapping"`

**Phase 0 exit:** `./gradlew :core:model:test` green and fast; `:core:store:connectedAndroidTest` + `:app:connectedAndroidTest` green; schema 1.json committed; `tasks/todo.md` updated.

---

# PHASE 1 — Core local app

### Task 9: Capture parser + URL sanitizer (`:core:model`)

**Files:**
- Create: `core/model/src/main/kotlin/dev/vinny/gsd/model/CaptureParser.kt`
- Test: `core/model/src/test/kotlin/dev/vinny/gsd/model/CaptureParserTest.kt`

**Interfaces:**
- Produces:

```kotlin
data class ParsedCapture(val title: String, val urgent: Boolean, val important: Boolean,
                         val tags: List<String>, val urls: List<String>)
object CaptureParser { fun parse(input: String): ParsedCapture }   // grammar spec §8.2
object UrlSanitizer  { fun sanitize(candidate: String): String? }  // null = unsafe, leave in title
```

- Grammar (spec §8.2, security-relevant — replicate exactly): `!!` → urgent+important (word boundaries, precedence over `!`); `!` → urgent; `*` → important; `#tag` → lowercased, deduped, capped 20; http(s) URLs moved to description. Sanitizer: only http/https; reject embedded credentials; require valid hostname; reject length ≥ 2048; strip trailing `, ; : . ! ? )`. Title after cleaning: tokens/URLs removed, whitespace collapsed; if empty but ≥1 URL found → `"Review link below"`.

- [ ] **Step 1: Write the failing tests — Appendix C vectors verbatim, plus sanitizer edges**

```kotlin
package dev.vinny.gsd.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CaptureParserTest {
    @Test fun `appendix c vectors`() {
        with(CaptureParser.parse("Ship the deck !! #work")) {
            assertEquals("Ship the deck", title); assertEquals(true, urgent); assertEquals(true, important)
            assertEquals(listOf("work"), tags)
        }
        with(CaptureParser.parse("Call mom !")) {
            assertEquals("Call mom", title); assertEquals(true, urgent); assertEquals(false, important)
        }
        with(CaptureParser.parse("Plan trip *")) {
            assertEquals("Plan trip", title); assertEquals(false, urgent); assertEquals(true, important)
        }
        with(CaptureParser.parse("Read newsletter")) {
            assertEquals(false, urgent); assertEquals(false, important)   // lands in Q4
        }
        with(CaptureParser.parse("Check https://example.com/a then report !")) {
            assertEquals("Check then report", title); assertEquals(true, urgent)
            assertEquals(listOf("https://example.com/a"), urls)
        }
        with(CaptureParser.parse("Read https://example.com/doc.")) {
            assertEquals(listOf("https://example.com/doc"), urls)   // trailing '.' stripped
        }
        with(CaptureParser.parse("https://example.com")) {
            assertEquals("Review link below", title); assertEquals(listOf("https://example.com"), urls)
        }
        with(CaptureParser.parse("Fix login !!#auth #AUTH")) {
            assertEquals(listOf("auth"), tags); assertEquals(true, urgent); assertEquals(true, important)
        }
        with(CaptureParser.parse("Danger http://user:pass@evil.com")) {
            assertEquals("Danger http://user:pass@evil.com", title)   // rejected URL stays in title
            assertEquals(emptyList(), urls)
        }
    }

    @Test fun `bang inside a word is not a token`() =
        assertEquals("wow!ok", CaptureParser.parse("wow!ok").title)

    @Test fun `sanitizer rejects non-http schemes, credentials, over-long, hostless`() {
        assertNull(UrlSanitizer.sanitize("ftp://example.com"))
        assertNull(UrlSanitizer.sanitize("javascript:alert(1)"))
        assertNull(UrlSanitizer.sanitize("https://user:pass@x.com"))
        assertNull(UrlSanitizer.sanitize("https://" + "a".repeat(2050)))
        assertNull(UrlSanitizer.sanitize("https://"))
        assertEquals("https://x.dev/p", UrlSanitizer.sanitize("https://x.dev/p,"))
    }
}
```

- [ ] **Step 2: Run to verify failure** — `./gradlew :core:model:test` — Expected: FAIL, unresolved `CaptureParser`.

- [ ] **Step 3: Implement**

```kotlin
package dev.vinny.gsd.model

import java.net.URI

object UrlSanitizer {
    private val trailingPunctuation = charArrayOf(',', ';', ':', '.', '!', '?', ')')
    fun sanitize(candidate: String): String? {
        val trimmed = candidate.trimEnd(*trailingPunctuation)
        if (trimmed.length >= 2048) return null
        val uri = try { URI(trimmed) } catch (_: Exception) { return null }
        if (uri.scheme !in setOf("http", "https")) return null
        if (uri.userInfo != null) return null
        if (uri.host.isNullOrEmpty()) return null
        return trimmed
    }
}

object CaptureParser {
    private val urlPattern = Regex("""https?://\S+""")
    private val doubleBang = Regex("""(?<=^|\s)!!(?=\s|$)|(?<=^|\s)!!(?=#)""")
    private val singleBang = Regex("""(?<=^|\s)!(?=\s|$)|(?<=^|\s)!(?=#)""")
    private val star = Regex("""(?<=^|\s)\*(?=\s|$)""")
    private val tag = Regex("""(?<=^|\s|!)#([^\s#]{1,30})""")

    fun parse(input: String): ParsedCapture {
        var working = input
        val urls = mutableListOf<String>()
        urlPattern.findAll(working).forEach { m ->
            UrlSanitizer.sanitize(m.value)?.let { safe -> urls += safe }
        }
        urls.forEach { safe ->
            // remove the matched raw token whose sanitized form we kept
            working = urlPattern.replace(working) { m ->
                if (UrlSanitizer.sanitize(m.value) == safe && safe in urls) "" else m.value }
        }
        val urgentImportant = doubleBang.containsMatchIn(working)
        working = doubleBang.replace(working, "")
        val urgent = urgentImportant || singleBang.containsMatchIn(working)
        working = singleBang.replace(working, "")
        val important = urgentImportant || star.containsMatchIn(working)
        working = star.replace(working, "")
        val tags = tag.findAll(working).map { it.groupValues[1].lowercase() }.distinct().take(20).toList()
        working = tag.replace(working, "")
        val title = working.replace(Regex("""\s+"""), " ").trim()
        return ParsedCapture(
            title = title.ifEmpty { if (urls.isNotEmpty()) "Review link below" else "" },
            urgent = urgent, important = important, tags = tags, urls = urls)
    }
}
```

Iterate against the tests until all vectors pass — the regex word-boundary details (`!` before `#`, `wow!ok`) are exactly what the vectors pin down. Refactor for clarity once green.

- [ ] **Step 4: Run to verify pass** — `./gradlew :core:model:test` — Expected: PASS.
- [ ] **Step 5: Commit** — `git commit -m "feat(model): capture shorthand parser and url sanitizer with appendix c fixtures"`

---

### Task 10: App scaffold — composition root, prefs, bottom navigation

**Files:**
- Create: `app/src/main/kotlin/dev/vinny/gsd/AppContainer.kt`, `nav/Destinations.kt`, `nav/GsdNavHost.kt`, `prefs/GsdPrefs.kt`, `app/src/main/res/values/strings.xml`
- Modify: `GsdApp.kt`, `MainActivity.kt`
- Test: `app/src/androidTest/kotlin/dev/vinny/gsd/LaunchSmokeTest.kt`

**Interfaces:**
- Produces:

```kotlin
class AppContainer(context: Context) {                 // manual composition root (spec §3 — no Hilt/Koin)
    val db: GsdDatabase; val clock: Clock = Clock.systemUTC(); val store: TaskStore; val prefs: GsdPrefs
}
class GsdPrefs(private val dataStore: DataStore<Preferences>) {   // spec §6.7
    val showCompleted: Flow<Boolean>; val theme: Flow<ThemePref>; val hasOnboarded: Flow<Boolean>
    suspend fun setShowCompleted(v: Boolean); suspend fun setTheme(v: ThemePref); suspend fun setHasOnboarded(v: Boolean)
}
enum class ThemePref { SYSTEM, LIGHT, DARK }
enum class Destination(val route: String) { MATRIX("matrix"), BROWSE("browse"), DASHBOARD("dashboard"), SETTINGS("settings") }
```

- `MainActivity`: `enableEdgeToEdge()`, splash via `core-splashscreen`, `GsdTheme(darkTheme = <from prefs.theme>)`, `NavigationBar` with the four §6.1 destinations (icons: `grid_view`, `filter_list`, `bar_chart`, `settings` — Material Symbols per §5.7), `GsdNavHost` composing MatrixScreen + three placeholder screens. All labels from `strings.xml`.

- [ ] **Step 1: Write the failing launch smoke test**

```kotlin
package dev.vinny.gsd

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class LaunchSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun bottomNavShowsFourDestinationsAndSwitches() {
        rule.onNodeWithText("Matrix").assertIsDisplayed()
        rule.onNodeWithText("Browse").assertIsDisplayed()
        rule.onNodeWithText("Dashboard").assertIsDisplayed()
        rule.onNodeWithText("Settings").assertIsDisplayed()
        rule.onNodeWithText("Settings").performClick()
        rule.onNodeWithText("Appearance").assertIsDisplayed()   // Settings placeholder's first group
    }
}
```

- [ ] **Step 2: Run to verify failure** — `./gradlew :app:connectedAndroidTest` — Expected: FAIL (no nav yet).
- [ ] **Step 3: Implement** — `GsdApp` holds `lateinit var container: AppContainer`, created in `onCreate`. `AppContainer` builds the Room DB (`Room.databaseBuilder(context, GsdDatabase::class.java, "gsd.db")`), `TaskStore(db, clock)`, `GsdPrefs(context.dataStore)`. `MainActivity` reads the container from `application as GsdApp` — no globals. NavHost keeps one back-stack entry per tab (standard `NavigationBar` + `navigate(route) { launchSingleTop = true; popUpTo(start) { saveState = true }; restoreState = true }` pattern). Placeholder Browse/Dashboard screens show their §5.10-style empty states; Settings shows the "Appearance" group with the theme picker wired to `GsdPrefs` (the full §8.17 settings surface lands in Phase 3).
- [ ] **Step 4: Run to verify pass** — Expected: PASS.
- [ ] **Step 5: Commit** — `git commit -m "feat(app): composition root, datastore prefs, bottom navigation shell"`

---

### Task 11: Matrix screen (compact) + task card

**Files:**
- Create: `app/src/main/kotlin/dev/vinny/gsd/matrix/MatrixScreen.kt`, `MatrixViewModel.kt`, `QuadrantSection.kt`, `TaskCard.kt`
- Test: `app/src/androidTest/kotlin/dev/vinny/gsd/matrix/MatrixScreenTest.kt`

**Interfaces:**
- Consumes: `TaskStore.observeTasks()`, `GsdPrefs.showCompleted`, theme tokens.
- Produces:

```kotlin
class MatrixViewModel(private val store: TaskStore, prefs: GsdPrefs) : ViewModel() {
    val sections: StateFlow<List<QuadrantSectionState>>   // Q1..Q4 order; incomplete before completed (§8.1)
    fun toggleCompleted(id: String)
}
data class QuadrantSectionState(val quadrant: Quadrant, val activeCount: Int, val tasks: List<Task>)
@Composable fun TaskCard(task: Task, onToggleComplete: () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier)
```

- `TaskCard` renders the §5.6 anatomy: 3dp accent spine in `accent(task.quadrant)`, title (strikethrough + `ink3` when completed), 2-line description preview, tag chips (wash bg + accent text), metadata row (due date states per §5.6 — today in Tide semibold, overdue in `alert`), subtask progress capsule, trailing overflow + 28dp completion disc in a 48dp target. Disc tap **toggles complete — never opens the editor** (§5.6). Card `surface` fill, 1dp `hairline` border, §5.5 soft shadow, `card` radius.
- `MatrixScreen`: one `LazyColumn` (`item` per quadrant header, `items(tasks, key = { it.id })` per section), §5.5 spacing (32dp between quadrants), empty quadrants get the §5.10 dashed prompts with the exact copy (`"No fires right now."` etc.), fully-empty matrix shows "Capture your first task".
- TalkBack: card exposes one merged semantics node (title, quadrant, due, status) + custom accessibility actions for complete/edit (spec §13.3).

- [ ] **Step 1: Write the failing UI test**

```kotlin
package dev.vinny.gsd.matrix

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import dev.vinny.gsd.model.*

class MatrixScreenTest {
    @get:Rule val rule = createComposeRule()
    private val t0 = Instant.parse("2026-07-19T12:00:00.000Z")
    private fun task(title: String, urgent: Boolean, important: Boolean, completed: Boolean = false) =
        Task(title = title, urgent = urgent, important = important, completed = completed,
             createdAt = t0, updatedAt = t0)

    @Test fun tasksGroupIntoQuadrantSectionsInOrder() {
        val sections = buildSections(listOf(
            task("fire", urgent = true, important = true),
            task("plan", urgent = false, important = true)))
        rule.setContent { dev.vinny.gsd.theme.GsdTheme {
            MatrixList(sections, onToggleComplete = {}, onOpen = {}) } }
        rule.onNodeWithText("Do First").assertIsDisplayed()
        rule.onNodeWithText("fire").assertIsDisplayed()
        rule.onNodeWithText("Schedule").assertIsDisplayed()
        rule.onNodeWithText("No fires right now.").assertDoesNotExist()
    }

    @Test fun emptyQuadrantShowsTailoredPrompt() {
        rule.setContent { dev.vinny.gsd.theme.GsdTheme {
            MatrixList(buildSections(emptyList()), onToggleComplete = {}, onOpen = {}) } }
        rule.onNodeWithText("No fires right now.").assertIsDisplayed()
        rule.onNodeWithText("Nothing to drop.").assertIsDisplayed()
    }

    @Test fun discTapTogglesWithoutOpeningEditor() {
        var toggled: String? = null; var opened: String? = null
        rule.setContent { dev.vinny.gsd.theme.GsdTheme {
            MatrixList(buildSections(listOf(task("t", true, true))),
                onToggleComplete = { toggled = it }, onOpen = { opened = it }) } }
        rule.onNodeWithContentDescription("Mark complete").performClick()
        kotlin.test.assertEquals("t", rule.let { toggled?.let { _ -> "t" } })
        kotlin.test.assertEquals(null, opened)
    }
}
```

(`buildSections(tasks): List<QuadrantSectionState>` is a pure helper in `MatrixViewModel.kt` — grouping, Q1→Q4 order, incomplete-first sort — also unit-testable; `MatrixList` is the stateless list composable `MatrixScreen` wraps, which is what makes UI testing possible without the ViewModel.)

- [ ] **Step 2: Run to verify failure** — Expected: FAIL (unresolved composables).
- [ ] **Step 3: Implement** the components per the Interfaces block. Keep `TaskCard.kt` under ~200 lines by extracting `TagChip`, `MetadataRow`, `SubtaskProgress`, `CompletionDisc` as private composables in the same file.
- [ ] **Step 4: Run to verify pass** — `./gradlew :app:connectedAndroidTest` — Expected: PASS.
- [ ] **Step 5: Wire `MatrixScreen` into the nav host** replacing the placeholder; manual check on emulator: `./gradlew :app:installDebug`.
- [ ] **Step 6: Commit** — `git commit -m "feat(app): compact matrix screen with quadrant sections and task cards"`

---

### Task 12: Capture bar with live parse preview

**Files:**
- Create: `app/src/main/kotlin/dev/vinny/gsd/capture/CaptureBar.kt`, `CaptureViewModel.kt`
- Modify: `app/src/main/kotlin/dev/vinny/gsd/matrix/MatrixScreen.kt` (pin the bar at top)
- Test: `app/src/androidTest/kotlin/dev/vinny/gsd/capture/CaptureBarTest.kt`

**Interfaces:**
- Consumes: `CaptureParser.parse()`, `TaskStore.add()`, `Quadrant`.
- Produces:

```kotlin
data class CaptureState(val text: String = "", val override: Quadrant? = null) {
    val parsed: ParsedCapture get() = CaptureParser.parse(text)
    val effectiveQuadrant: Quadrant get() = override ?: Quadrant.from(parsed.urgent, parsed.important)
    val isAuto: Boolean get() = override == null
}
class CaptureViewModel(private val store: TaskStore) : ViewModel() {
    val state: StateFlow<CaptureState>
    fun onTextChange(text: String); fun cycleOverride()          // Auto → Q1 → Q2 → Q3 → Q4 → Auto (§8.2)
    fun submit(onError: (String) -> Unit)                        // adds task, clears text, keeps focus
    fun draftForEditor(): ParsedCapture                          // consumed by "Details" (§8.2)
}
```

- Visual per §5.10: pill on `paper`, `surface` fill, `hairline` border, placeholder `Capture a task…  (!!  *  #tag)` in `ink3`; trailing quadrant chip shows "Auto" + grid glyph, or the live quadrant name + symbol in its pigment; parsed `#tag` chips beneath in neutral `sunken`/`ink2`; quiet "Details" text button in `tint`.
- Submit: build the Task from `parsed` (urls newline-joined into description per §8.2), `store.add`; **validation failure restores the raw text inline** (§8.2); focus retained for serial capture. No flags + no override → Q4 (Eliminate).

- [ ] **Step 1: Write the failing UI test**

```kotlin
package dev.vinny.gsd.capture

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import dev.vinny.gsd.model.Quadrant
import kotlin.test.assertEquals

class CaptureBarTest {
    @get:Rule val rule = createComposeRule()

    @Test fun chipRestsAsAutoThenShowsLiveQuadrant() {
        var state by mutableStateOf(CaptureState())
        rule.setContent { dev.vinny.gsd.theme.GsdTheme {
            CaptureBarContent(state, onTextChange = { state = state.copy(text = it) },
                onCycleOverride = {}, onSubmit = {}, onDetails = {}) } }
        rule.onNodeWithText("Auto").assertIsDisplayed()
        rule.onNodeWithTag("capture_field").performTextInput("Ship it !!")
        rule.onNodeWithText("Do First").assertIsDisplayed()
    }

    @Test fun tagChipsRenderBeneathField() {
        rule.setContent { dev.vinny.gsd.theme.GsdTheme {
            CaptureBarContent(CaptureState(text = "Fix #auth #web"), onTextChange = {},
                onCycleOverride = {}, onSubmit = {}, onDetails = {}) } }
        rule.onNodeWithText("#auth").assertIsDisplayed()
        rule.onNodeWithText("#web").assertIsDisplayed()
    }

    @Test fun overrideCyclePropagates() {
        assertEquals(Quadrant.DO_FIRST, CaptureState(override = null).cycled().override)
        assertEquals(null, CaptureState(override = Quadrant.ELIMINATE).cycled().override)
    }
}
```

(`CaptureState.cycled()` is the pure cycle function the ViewModel delegates to — JVM-logic kept out of the composable.)

- [ ] **Step 2: Run to verify failure** — Expected: FAIL.
- [ ] **Step 3: Implement** `CaptureBarContent` (stateless) + `CaptureBar` (wires the ViewModel) + `CaptureState.cycled()`. Pin above the matrix `LazyColumn` in `MatrixScreen`.
- [ ] **Step 4: Run to verify pass**, then manual serial-capture check on emulator (`Ship the deck !! #work` → lands in Do First with tag chip).
- [ ] **Step 5: Commit** — `git commit -m "feat(app): capture bar with live quadrant preview and override cycle"`

---

### Task 13: Task editor sheet (Phase 1 fields)

**Files:**
- Create: `app/src/main/kotlin/dev/vinny/gsd/editor/TaskEditorSheet.kt`, `TaskEditorViewModel.kt`
- Modify: `matrix/MatrixScreen.kt` (card tap + "Details" open the sheet)
- Test: `app/src/androidTest/kotlin/dev/vinny/gsd/editor/TaskEditorTest.kt`

**Interfaces:**
- Consumes: `TaskStore.get/add/update`, `TaskValidator`, `NanoId`.
- Produces:

```kotlin
class TaskEditorViewModel(private val store: TaskStore) : ViewModel() {
    fun openNew(prefill: ParsedCapture?)      // reserves the task ID at editor-open (spec §8.3)
    fun openExisting(id: String)
    val draft: StateFlow<Task?>; val canSave: StateFlow<Boolean>   // false while title empty
    fun edit(transform: (Task) -> Task); fun save(onDone: () -> Unit, onError: (String) -> Unit)
}
@Composable fun TaskEditorSheet(viewModel: TaskEditorViewModel, onDismiss: () -> Unit)
```

- Compact presentation: `ModalBottomSheet` opening half-expanded, "Show all details" expands (§6.3). Phase 1 sections in §8.3 order: **Title** (multiline), **Quadrant** (2×2 picker — selected = accent border + wash fill; unselected = accent border at 35% alpha, §5.1), **Tags** (chip field, comma/enter commits, ≥48dp remove targets, autocomplete from existing tags), **Notes**, **Links** (read-only tappable http/https list auto-detected from notes). Remaining §8.3 sections (due date, reminder, repeat, subtasks, estimate, snooze, dependencies) are Phase 2 — the section `Column` is built to append them without restructuring.
- Save runs `TaskValidator` via the store; typed errors surface as human copy under the offending field (§7.11).

- [ ] **Step 1: Write the failing UI test**

```kotlin
package dev.vinny.gsd.editor

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import dev.vinny.gsd.model.*

class TaskEditorTest {
    @get:Rule val rule = createComposeRule()
    private val t0 = Instant.parse("2026-07-19T12:00:00.000Z")

    @Test fun saveDisabledWhileTitleEmpty() {
        rule.setContent { dev.vinny.gsd.theme.GsdTheme {
            EditorContent(draft = Task(title = "", urgent = false, important = false,
                createdAt = t0, updatedAt = t0), canSave = false, onEdit = {}, onSave = {}) } }
        rule.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test fun quadrantPickerReflectsAndUpdatesFlags() {
        var edited: Task? = null
        rule.setContent { dev.vinny.gsd.theme.GsdTheme {
            EditorContent(draft = Task(title = "t", urgent = false, important = false,
                createdAt = t0, updatedAt = t0), canSave = true,
                onEdit = { edited = it }, onSave = {}) } }
        rule.onNodeWithText("Do First").performClick()
        kotlin.test.assertEquals(true, edited?.urgent); kotlin.test.assertEquals(true, edited?.important)
    }

    @Test fun linksAutoDetectFromNotes() {
        rule.setContent { dev.vinny.gsd.theme.GsdTheme {
            EditorContent(draft = Task(title = "t", urgent = false, important = false,
                description = "see https://example.com/spec", createdAt = t0, updatedAt = t0),
                canSave = true, onEdit = {}, onSave = {}) } }
        rule.onNodeWithText("https://example.com/spec").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run to verify failure** — Expected: FAIL.
- [ ] **Step 3: Implement** (`EditorContent` stateless; `TaskEditorSheet` owns the `ModalBottomSheet` + ViewModel wiring; quadrant picker is a 2-column grid of four cells using `accent`/`wash` tokens).
- [ ] **Step 4: Run to verify pass**; manual check: capture bar "Details" pre-fills the sheet, editing an existing card round-trips.
- [ ] **Step 5: Commit** — `git commit -m "feat(app): task editor sheet with quadrant picker, tags, notes, links"`

---

### Task 14: Completion — disc, swipe, haptic, confetti

**Files:**
- Create: `app/src/main/kotlin/dev/vinny/gsd/matrix/ConfettiOverlay.kt`, `SwipeTaskRow.kt`
- Modify: `matrix/MatrixScreen.kt`, `MatrixViewModel.kt`
- Test: `app/src/androidTest/kotlin/dev/vinny/gsd/matrix/CompletionTest.kt` + JVM test for confetti particle seeding

**Interfaces:**
- Consumes: `TaskStore.setCompleted()`, theme tokens, `ValueAnimator.areAnimatorsEnabled()`.
- Produces: `SwipeTaskRow(task, onComplete, onSnooze, onDelete, content)` — leading full-swipe completes; trailing reveals Snooze (1 h quick-snooze — stub `onSnooze` until Phase 2's snooze task, wired but hidden behind a `snoozeEnabled = false` flag) and Delete. `ConfettiOverlay(trigger: CompletionEvent?)` draws §5.8's celebration on a Compose `Canvas`: 160 particles, 1.6 s, gravity 700, launch speed 150–460 upward-biased (−220), size 5–10dp, fade 1→0, colors = four quadrant pigments + success green; skipped entirely when animators are disabled.
- Completing fires exactly one `HapticFeedbackConstants.CONFIRM` (§5.8). Completed cards re-sort below incomplete and render dimmed/struck (§8.1).

- [ ] **Step 1: Write failing tests** — UI: leading swipe on a card calls `onComplete`; completed task renders after incomplete ones within its section; JVM: `ConfettiSpec.particles(seed)` produces 160 particles within the §5.8 parameter ranges (pure function seeded for determinism, rendering reads it).
- [ ] **Step 2: Run to verify failure** — Expected: FAIL.
- [ ] **Step 3: Implement** — `SwipeToDismissBox` with `confirmValueChange` committing complete on full leading swipe; confetti as a `Canvas` in a full-size overlay driven by `withFrameNanos`, particle physics from the pure `ConfettiSpec`.
- [ ] **Step 4: Run to verify pass**; manual: complete a task on the emulator — haptic + confetti + card re-sorts; enable "Remove animations" in system settings — confetti absent, state change instant.
- [ ] **Step 5: Commit** — `git commit -m "feat(app): completion interactions with confetti, haptic, and swipe"`

---

### Task 15: Delete + undo snackbar

**Files:**
- Modify: `matrix/MatrixScreen.kt`, `MatrixViewModel.kt`, `matrix/SwipeTaskRow.kt`
- Test: `app/src/androidTest/kotlin/dev/vinny/gsd/matrix/UndoDeleteTest.kt`

**Interfaces:**
- Consumes: `TaskStore.delete()` (returns the snapshot), `TaskStore.add()` (undo re-creates as a fresh write — new `updatedAt`, LWW-safe, spec §8.20).
- Produces: `MatrixViewModel.delete(id: String)` + `undoDelete()`; snackbar `Deleted "<title>"` with **Undo**, ~6 s (12 s under TalkBack — read `AccessibilityManager.isEnabled` + touch exploration). Delete commits immediately; no confirmation dialog for single deletes (§8.20).

- [ ] **Step 1: Write the failing test** — deleting via the row's trailing action removes the card and shows the snackbar; tapping Undo restores a card with the same title (and a fresh `updatedAt` — assert via store).
- [ ] **Step 2: Run to verify failure.**
- [ ] **Step 3: Implement** — ViewModel holds `pendingUndo: Task?`; `SnackbarHostState.showSnackbar(..., duration = Long)` with explicit dismiss timing; undo calls `store.add(snapshot.copy())` letting the store re-stamp.
- [ ] **Step 4: Run to verify pass.**
- [ ] **Step 5: Commit** — `git commit -m "feat(app): immediate delete with undo snackbar"`

---

### Task 16: Show-completed toggle + top app bar lockup

**Files:**
- Create: `app/src/main/kotlin/dev/vinny/gsd/matrix/GsdTopBar.kt`
- Modify: `matrix/MatrixScreen.kt`, `MatrixViewModel.kt`
- Test: extend `MatrixScreenTest.kt`

**Interfaces:**
- Consumes: `GsdPrefs.showCompleted` / `setShowCompleted`.
- Produces: top app bar with the §5.4 brand lockup (24dp mark placeholder vector + "GSD" sans bold ink + "·" ink3 + screen name ink2), actions: search (stub → Phase 3), show-completed toggle (eye icon), select-mode toggle (stub → Phase 3). `MatrixViewModel.sections` filters completed tasks out when `showCompleted == false`; quadrant header counts always count **active** only (§8.1).

- [ ] **Step 1: Write the failing test** — with `showCompleted = false` a completed task's title is absent; toggling shows it dimmed below incomplete tasks; header count unchanged by the toggle.
- [ ] **Step 2–4: Red → green → refactor** as above.
- [ ] **Step 5: Commit** — `git commit -m "feat(app): show-completed toggle and brand lockup top bar"`

---

### Task 17: Expanded width — 2×2 grid + drag-and-drop reclassify

**Files:**
- Create: `app/src/main/kotlin/dev/vinny/gsd/matrix/MatrixGrid.kt`
- Modify: `MainActivity.kt` (window size class), `nav/GsdNavHost.kt` (rail at medium, drawer at expanded — §6.2), `MatrixScreen.kt`
- Test: `app/src/androidTest/kotlin/dev/vinny/gsd/matrix/MatrixGridTest.kt`

**Interfaces:**
- Consumes: `MatrixViewModel.sections`, `TaskStore.update()` (reclassify = set `urgent`/`important` to the target quadrant's flags).
- Produces: `MatrixGrid(sections, onDropToQuadrant: (taskId, Quadrant) -> Unit, …)` — true 2×2 (Q1 TL, Q2 TR, Q3 BL, Q4 BR), each cell header + independent scroll, 16dp panel gap (§5.5); drag a card across cell boundaries via `Modifier.dragAndDropSource`/`dragAndDropTarget` carrying the task id; drop updates flags through the store. Compute `WindowSizeClass` in `MainActivity`, choose bottom bar (compact) / rail (medium) / permanent drawer (expanded).

- [ ] **Step 1: Write the failing test** — grid renders four cells in Q1–Q4 positions; simulated drop event calls `onDropToQuadrant` with the target quadrant; store update flips flags (verify via fake store).
- [ ] **Step 2–4: Red → green → refactor.** Manual: resizable emulator (tablet profile), drag a card Q1 → Q2, verify accent spine changes and it persists relaunch.
- [ ] **Step 5: Commit** — `git commit -m "feat(app): adaptive 2x2 matrix grid with drag-and-drop reclassify"`

---

### Task 18: Phase 1 exit — smoke suite, accessibility pass, handoff

- [ ] Run the full gate: `./gradlew :core:model:test :core:store:connectedAndroidTest :app:connectedAndroidTest` — all green.
- [ ] TalkBack manual pass on the matrix + editor (labels, reading order, custom actions present); font scale 2× reflow check; "Remove animations" check (spec §13.3).
- [ ] Re-read every changed file (coding-standards Exit Matrix): no debug code, TODOs, dead imports, naming drift.
- [ ] Update `tasks/todo.md` "Resuming From Here"; distill `tasks/implementation-notes.md` → `tasks/lessons.md`.
- [ ] Commit + tag: `git tag v0.1.0-phase1`.

---

# PHASES 2–7 — Milestone outlines (re-plan each at task level just before starting)

### Phase 2 — Task depth
**Scope:** due dates + §8.10 presets (This week = Friday, weekend → next Friday; Next week = Monday strictly after today) · recurrence engine (§8.5: spawn-on-complete, +1d/+7d/+1 month with month-end clamp, subtask + reminder-state reset, single-level `parentTaskId` lineage) · subtasks UI (§8.6) · snooze (§8.7: six presets, 1 h quick-snooze, 365 d cap) · dependencies + BFS cycle prevention (§8.8) · time tracking (§8.9: one running entry, floor-minutes, live `HH:MM:SS` card timer).
**Exit tests:** JVM fixtures for recurrence date math (incl. Jan 31 → Feb 28/29), BFS cycle rejection, duration formatting, due-date preset resolution across a weekend boundary; editor UI tests for each new section. **Risk:** date math and timezones — pin all preset logic to injected `Clock` + `ZoneId`.

### Phase 3 — Organization & insight
**Scope:** archive (§8.12: manual + auto sweep, restore, permanent delete; archive-as-active-record sync transitions stubbed until Phase 5) · smart views (§8.13: 9 built-ins with fixed IDs, custom CRUD, pin ≤5) · FilterCriteria pipeline (§7.9, ANDed) · search + command palette (§8.14: substring, sectioned Tasks/Smart Views/Actions/Navigation) · multi-select + bulk bar (§8.11) · analytics dashboard (§8.15 — no streaks, ever) · import/export + erase-local (§8.16: envelope v1, sorted keys, Replace/Merge with ID-remap through forward references, "RESET" guard) · onboarding/Help/About (§8.19) · Browse tab real UI.
**Exit tests:** filter-pipeline JVM fixtures per criterion; import merge ID-remap fixtures (collision + forward-reference); export round-trip; palette sectioning test. **Risk:** import Replace's enqueue-deletes-for-missing-IDs semantics — write the fixture first.

### Phase 4 — Notifications
**Scope:** exact alarms (§11.2: `setExactAndAllowWhileIdle`, `SCHEDULE_EXACT_ALARM` + graceful inexact fallback) · two channels (Reminders / silent) · contextual `POST_NOTIFICATIONS` request · quiet-hours deferral · `BOOT_COMPLETED`/`MY_PACKAGE_REPLACED` rescheduling · notification actions Complete + Snooze-1h (§11.4) · debounced (~1 s) reminder resync off the store change stream (§11.1) · WorkManager hourly maintenance (auto-archive sweep; sync hook dormant until Phase 5) · settings §7.4 (defaults 15 m, quiet hours 22:00–07:00).
**Exit tests:** JVM scheduling-decision fixtures (offset, snooze-overrides-due, quiet-hours deferral, reset-on-edit per §8.3); instrumented receiver test. **Risk:** OEM alarm throttling — verify on a physical device before calling done.

### Phase 5 — Sync (highest risk; budget accordingly)
**Scope:** minimal PocketBase client (REST via OkHttp + kotlinx.serialization; SSE per §9.6 frame rules; bare-JWT `Authorization` header — no `Bearer`) · OAuth2 PKCE via Custom Tabs for Google/GitHub/Apple (§10, `gsd://` callback, state validation) · Keystore-encrypted token + 7-day-skew refresh (§10.3) · SyncEngine: pull with cursor + 5 s rewind (§9.4), LWW at ms precision with equal→no-op (§9.3), push queue with backoff 5/10/30/60/300 s, 5-fails→failed, 429-aborts-push, ~100 ms throttle (§9.5), deletion reconciliation with queue protection (§9.4), device-local field preservation + rich-local time-entries preservation (§9.2), echo filtering + owner fail-closed (§9.6), 120 s cadence + 1.5 s mutation debounce, sync history (500 kept/50 shown) + health states (§9.7) · account switch/delete + erase-everywhere (§8.21, §9.8) · Settings Account section.
**Exit tests:** the full §13.6 sync matrix against **recorded fixtures**; early live-backend smoke against `https://api.vinny.io` with a scratch account. **Risks:** the two pre-phase blockers below; SSE parsing quirks; Apple OAuth form_post bounce.

**Pre-Phase-5 checklist (owner/backend items — verify before starting Phase 5):**
- [x] ~~Confirm `https://api.vinny.io/ios-oauth-redirect/` is client-agnostic~~ **Verified 2026-07-19 by direct probe:**
  GET returns a 144-byte static page — `location.replace("gsd://oauth-callback" + location.search)` — identical
  bytes under an Android UA (no sniffing possible; it's a static file). POST (Apple `form_post`) is handled
  server-side: **302 → `gsd://oauth-callback?code=…&state=…`** with body params promoted to the query string,
  also UA-independent. Both paths land on the one `gsd://oauth-callback` route Android will register.
  *Residual risk for the Phase 5 live test:* Chrome occasionally blocks JS `location.replace` to a custom
  scheme without a user gesture in Custom Tabs — if that bites, the fix is server-side: 302 the GET path
  exactly like the POST path already does.
- [ ] Web-accessible account-deletion URL exists for the Play Data Safety form (spec §16.3).
- [ ] A scratch PocketBase test account is available for live-fixture recording.

### Phase 6 — Native surfaces
**Scope:** Glance widget "Today's Focus" (§12.1: push-updated snapshot, 2×2/4×2, deep links) · share target (§12.2: ACTION_SEND text/plain, URL-derived titles, Q4 default, optional title fetch behind the setting) · static app shortcuts (§6.5) · `gsd://` deep-link hardening (§6.4: cold-start persistence, not-found state).
**Exit tests:** share-target title-derivation JVM fixtures; deep-link routing table test; widget snapshot rendering test.

### Phase 7 — Play readiness
**Scope:** adaptive icon + monochrome layer + splash (§5.9) · Data Safety form accuracy (§14) · accessibility audit (TalkBack, 2× font, reduced motion, contrast AA) · baseline profile · phone + tablet screenshots (2×2 grid is the tablet hero) · internal testing track.
**Exit:** Play internal-track build accepted; §14 checklist all green.

---

## Verification method (defined before coding, per coding-standards)

| Layer | Command | Cadence |
|---|---|---|
| Pure domain (`:core:model`) | `./gradlew :core:model:test` | every red/green cycle — must stay sub-second |
| Store (Room) | `./gradlew :core:store:connectedAndroidTest` | per store task (emulator) |
| UI (`:app`) | `./gradlew :app:connectedAndroidTest` | per UI task (emulator) |
| Manual | `./gradlew :app:installDebug` on resizable emulator | end of each UI task |

## Deviations & lessons
Log tactical deviations in `tasks/implementation-notes.md` as they happen; contract drift (any change to spec behavior) stops work for spec re-approval per coding-standards.

# PHASE 2 — task-level plan (planned 2026-07-19, executed in-session)

> Format note: tighter than the Phase 0–1 plan (interfaces + test surface, not full code) because
> the planner and executor are the same session; spec sections are the behavior contract.

### Task 2.1: Due-date presets — `core/model/.../DuePreset.kt` + test
`enum class DuePreset { NONE, TODAY, THIS_WEEK, NEXT_WEEK }` + `fun resolve(preset, today: LocalDate): LocalDate?` per §8.10: TODAY→today; THIS_WEEK→Friday of current week, Sat/Sun→*next* Friday; NEXT_WEEK→Monday strictly after today. Tests: each preset from a Wednesday; THIS_WEEK from Sat and Sun; NEXT_WEEK from Sunday and Monday.

### Task 2.2: Recurrence engine — `core/model/.../RecurrenceEngine.kt` + test
`fun nextDueDate(prior: Instant, recurrence, zone): Instant` (+1d / +7d / +1 calendar month with month-end clamp, preserving local time-of-day) and `fun spawnNext(completed: Task, now: Instant): Task?` — null for NONE; else new task: fresh 21-char id, `createdAt = updatedAt = now`, `completed=false`, `completedAt=null`, subtasks reset incomplete (fresh subtask ids NOT required — same ids, completed=false), `parentTaskId = completed.parentTaskId ?: completed.id` (single-level), reminder state reset (`notificationSent=false`, `lastNotificationAt=null`, `snoozedUntil=null`), `timeEntries=[]`? — **No**: §8.5 says the new instance carries the recurrence forward; time entries belong to the completed original; new instance starts with empty `timeEntries` and `timeSpent` derived. dueDate advanced from prior due (none → none). Tests: daily/weekly/monthly; Jan 31 → Feb 28 (2026) and Feb 29 (2028); lineage chains stay single-level; subtask reset; no-due-date spawn.

### Task 2.3: Dependency graph — `core/model/.../DependencyGraph.kt` + test
`object DependencyGraph` over `List<Task>`: `wouldCreateCycle(tasks, taskId, candidateDependencyId): Boolean` (BFS from candidate; reject if taskId reachable; self-reference always true), `uncompletedBlockers(tasks, task): List<Task>`, `isBlocked(tasks, task)`, `blocking(tasks, task): List<Task>` (reverse edges), `readySet(tasks): Set<String>` (incomplete, no uncompleted blocker). Tests: direct cycle, transitive cycle (A→B→C, add C→A), self-reference, diamond no-cycle, ready excludes blocked-by-incomplete only.

### Task 2.4: Time tracking — `core/model/.../TimeTracking.kt` + test
`fun startEntry(task, now, id): Task` (throws `TimerAlreadyRunning` if an entry has null endedAt), `fun stopEntry(task, now): Task` (sets endedAt on the running entry; no-op-throw `NoRunningTimer` if none), `fun runningEntry(task): TimeEntry?`, `fun formatMinutes(totalMinutes: Int): String` ("< 1m" for 0, "45m", "2h 5m", "2h"). Tests: second start rejected, stop computes floor minutes via existing `timeSpent`, formatting table.

### Task 2.5: Store depth ops — `core/store/.../TaskStore.kt` + instrumented tests
`setCompleted` gains recurrence: completing a task with recurrence ≠ NONE inserts the spawned instance **in the same transaction** (both rows enqueue CREATE/UPDATE before visibility). New: `suspend fun snooze(id, until: Instant?)` (validated, clamped), `suspend fun startTimer(id, now)`, `suspend fun stopTimer(id)` — all through `update()`. Tests: complete-recurring creates sibling with lineage + queue rows (UPDATE for original, CREATE for instance); uncomplete does not re-spawn; snooze caps at 365d; timer start/stop round-trip; second start throws.

### Task 2.6: Editor depth — `editor/TaskEditorSheet.kt` + sections + UI tests
§8.3 order: **Due date** (toggle + Material date picker + §8.10 preset chips) → **Reminder** (visible only with due date: None/At time/5/15/30m/1h/2h/1d → `notifyBefore`; data-only until Phase 4) → **Repeat** (Never/Daily/Weekly/Monthly segmented) → **Subtasks** (add field, toggle, swipe-delete, drag-reorder-by-buttons up/down for now) → **Estimate** (minutes field + "Tracked Xm of Ym", over-estimate in `alert`) → **Snooze** (six §8.7 presets + clear, shows current state) → **Dependencies** (searchable picker; entries that would cycle are disabled with explanation; lists blockers/blocked). Editing dueDate/notifyBefore resets reminder state per §8.3 (notificationSent=false, lastNotificationAt/snoozedUntil cleared). UI tests: preset chip sets date; repeat selection persists; subtask add+toggle; dependency cycle option disabled; estimate over-track renders alert color (semantics tag).

### Task 2.7: Card surfaces + context menu — `matrix/TaskCard.kt`, `matrix/TaskContextMenu.kt` + tests
Card metadata row completes §5.6: blocked (`lock` + count), blocking (`arrow_circle`? use `ArrowCircleRight` + count), running timer (pulsing dot + live `HH:MM:SS`, static under reduced motion, ticking only while visible), tracked total (`schedule` icon + formatted), snoozed (`bedtime` + remaining). Long-press → `DropdownMenu`: Edit, Complete/Uncomplete, Start/Stop timer, Snooze ▸ six presets, Duplicate (fresh ids for task+subtasks, incomplete, no time entries), Share (plain-text §8.18), Move to ▸ quadrants, Delete. Trailing swipe now reveals quick-snooze (1 h) + delete. Tests: menu opens with items; duplicate produces new id + reset subtasks (VM/store instrumented); share text format (JVM: `ShareText.render(task)`).
**Phase 2 exit:** full gate green; tag `v0.2.0-phase2`.

# PHASE 3 — task-level plan

### Task 3.1: FilterCriteria + built-ins — `core/model/.../FilterCriteria.kt`, `SmartViews.kt` + tests
`@Serializable data class FilterCriteria(quadrants: List<String> = [], status: Status = ALL, tags: List<String> = [], dueDateRange: DateRange? = null, overdue/dueToday/dueThisWeek/noDueDate/recentlyAdded/recentlyCompleted/readyToWork: Boolean = false, recurrence: List<Recurrence> = [], searchQuery: String = "")` — camelCase-Codable (§7.9). `fun FilterCriteria.matches(task, allTasks, now, zone): Boolean` — ANDed; substring search across title/description/tags/**subtask titles**, case-insensitive. `object BuiltInSmartViews { val all: List<SmartView> }` — nine §8.13 entries with exact IDs/criteria; `data class SmartView(id, name, icon, criteria, isBuiltIn, createdAt, updatedAt)`. Tests: one per criterion + combination AND + the nine built-ins' membership on a fixture task set.

### Task 3.2: Archive ops — `core/store/.../ArchiveStore.kt` + DAO + instrumented tests
`ArchivedTaskDao` (insert/all/byId/delete/search). `class ArchiveStore(db, clock)`: `archive(id)` (completed tasks only → move row to archived_tasks + `archivedAt=now`, enqueue sync DELETE per §8.12), `restore(id)` (move back, stamp updatedAt, enqueue CREATE), `deletePermanently(id)` (confirmed at UI), `sweep(archiveAfterDays): Int` (completedAt older than N days), settings via `archive_settings` row. Deletion reconciliation never touches archive (Phase 5 concern — documented). Tests: archive moves row + enqueues DELETE; restore re-creates + enqueues; sweep respects threshold; incomplete tasks refuse archive.

### Task 3.3: Smart-view store — `core/store/.../SmartViewStore.kt` + DAO + instrumented tests
Seeds the nine built-ins on first open (fixed ids, isBuiltIn=true, criteria JSON via kotlinx). Custom CRUD (12-char nanoid, name ≤60, icon from 10 curated tokens); pins ordered ≤5 in `app_preferences.pinnedSmartViewIdsJson`. Tests: seeding idempotent; custom CRUD round-trip; pin cap enforced; built-ins immutable (update refused).

### Task 3.4: Browse tab — `browse/BrowseScreen.kt`, `browse/SmartViewListScreen.kt`, `browse/SmartViewEditorScreen.kt` + UI tests
§6.1: Archive row top; sections Pinned / Built-in / Custom + "New Smart View"; live match counts (combine tasks flow + criteria). Open → filtered list using §8.1 rows (swipe/complete/menu all work) + in-list plain search + view-aware empty states (§8.13: overdue→green check "Nothing overdue", wins→trophy, search-miss, generic). Custom editor: name, icon picker (10), criteria controls (status segmented, quadrant multi-select, tag list, due-date toggles, range, recurrence kinds, ready-to-work, search text). Row swipes: pin/unpin leading; edit/delete trailing (custom only). Tests: sections render; count updates on task add; custom view create→appears→filter applies; pin ordering.

### Task 3.5: Archive screen — `browse/ArchiveScreen.kt` + UI test
Dimmed (72% alpha) read-only §5.6 cards; leading swipe Restore, trailing Delete-permanently (confirm dialog "This can't be undone."); search field; multi-select bulk bar (restore / delete-permanently confirmed); toolbar menu: auto-archive toggle, 30/60/90 picker, "Archive now" + status line. Tests: restore returns card to matrix; permanent delete confirmed; sweep-now moves eligible.

### Task 3.6: Search + command palette — `palette/CommandPalette.kt` + tests
Full-screen keyboard-first surface on compact (palette dialog at expanded): one query field; substring (not fuzzy), sectioned **Tasks · Smart Views · Actions · Navigation** (§8.14). Actions: New task, Toggle show completed, Toggle theme. Navigation: Matrix, Dashboard, Archive, Settings + each smart view. Hardware: Ctrl+K / Ctrl+F open, Ctrl+N new task, Ctrl+1–4 jump to quadrant, Esc dismisses (§6.6) via `Modifier.onPreviewKeyEvent` at the activity root. Matching logic pure: `PaletteIndex.query(q, tasks, views): PaletteResults` (JVM-tested). Tests: sectioning, substring casing, actions fire callbacks.

### Task 3.7: Multi-select + bulk bar — `matrix/SelectionState.kt`, `matrix/BulkActionBar.kt` + tests
Select mode via top-bar toggle or long-press when menu closed (§8.1): checkmarks replace discs, selection count, bottom bar: Complete, Move ▸, Add tags, Remove tags, Set due date, Delete (confirm). Each op per-task through the store with per-task validation; partial failures reported in snackbar ("Updated 4, failed 1"). Available on matrix + smart-view lists. Tests: selection toggling; bulk complete updates all; bulk delete confirms; partial-failure copy (VM-level).

### Task 3.8: Dashboard — `core/model/.../Analytics.kt` + `dashboard/DashboardScreen.kt` + tests
Pure metrics (§8.15): `Analytics.compute(tasks, window: 7|30|90, now, zone): DashboardStats` — active, completed, completionRate, trackedTotalMinutes, per-day created/completed series, per-quadrant active counts, top tags, minutes by quadrant, upcoming deadlines (sorted), overdueCount. **No streaks.** JVM tests over a fixture set. UI: Newsreader stat numerals, Canvas line/area trend, quadrant-pigment donut/bars, tappable upcoming rows → editor, overdue banner → (Phase 3.4's overdue view), "No stats yet" empty state; computation off main thread (flow on Default dispatcher).

### Task 3.9: Import/export — `core/model/.../Interchange.kt` + `settings/DataScreen.kt` + tests
`Interchange.export(tasks, exportedAt): String` — envelope `{tasks, exportedAt, version: 1}`, pretty, **sorted keys** (serialize per-task JsonObject → sorted map recursively). `Interchange.parse(raw, limitBytes = 10MB, limitTasks = 10_000): ImportResult(tasks, skipped)` — lenient §7.3. `MergePlan.compute(existing, imported, idGenerator): List<Task>` — colliding ids regenerated (checked against existing+imported+assigned), `dependencies` + `parentTaskId` remapped through the complete map incl. forward references. Replace semantics live in `TaskStore.replaceAll(imported)`: stamp updatedAt, enqueue UPDATE per imported + DELETE per missing id, swap atomically. Erase-local: type-"RESET" dialog + export-first prompt; clears tasks/archive/custom views/queue/pins/archive settings; keeps theme + hasOnboarded (§8.16). JVM tests: sorted-keys stability, size/count caps, merge remap with forward refs + collision, skip counting; instrumented: replaceAll queue contents, erase keeps prefs.

### Task 3.10: Settings + onboarding — `settings/SettingsScreen.kt`, `onboarding/OnboardingFlow.kt`, help/about + UI smoke
§8.17 groups minus Account (Phase 5) and Notifications (Phase 4): Appearance (theme, show completed), Sharing (fetch titles toggle + footer), Archive (toggle, 30/60/90, Archive Now + status), Data & Storage (export/import/erase), About (version, privacy blurb + policy link, support mail link, Field Guide, "Show onboarding again"). Onboarding: 4 §8.19 pages (Welcome/Matrix/Capture with monospace legend/Privacy) gated on `hasOnboarded`, skippable, cross-fade under reduced motion; final page primary "Start using GSD". Field Guide static screen. Tests: launch shows onboarding when flag false; settings toggles persist; export→import round-trip smoke via SAF is manual.
**Phase 3 exit:** full gate green; a11y spot-check; `tasks/todo.md` updated; tag `v0.3.0-phase3`.

# PHASE 4 — task-level plan (planned 2026-07-19, executed in-session)

### Task 4.1: ReminderPlanner — `core/model/.../ReminderPlanner.kt` + JVM test
`data class ReminderSettings(enabled, defaultReminderMinutes = 15, soundEnabled = true, quietHoursStart: LocalTime?, quietHoursEnd: LocalTime?)` (§7.4 domain form). `fun fireTime(task, settings, now, zone): Instant?` — null when: settings disabled, task.notificationEnabled false, completed, no dueDate, notificationSent, or computed time ≤ now. Otherwise `dueDate − (notifyBefore ?: default)` minutes; **`snoozedUntil` in the future replaces it entirely** (§11.1). Quiet hours: a fire time inside [start, end) (window may cross midnight) defers to the window's end. Tests: offset math incl. notifyBefore=0, snooze override, quiet-hours same-day + cross-midnight + boundary, each skip rule, past-fire-time null.

### Task 4.2: NotificationSettings store — `core/store/.../NotificationSettingsStore.kt` + DAO + instrumented test
`NotificationSettingsDao` (observe/get/upsert on the singleton row). `class NotificationSettingsStore(db, clock)`: `observe(): Flow<ReminderSettings>` with §7.4 defaults when the row is absent (enabled, 15 m, sound on, quiet hours null until enabled — UI default 22:00–07:00), `update(transform)` stamping `updatedAt`, `markPermissionAsked()`. Tests: defaults synthesized; update round-trip; permissionAsked persists.

### Task 4.3: ReminderScheduler — `app/.../notifications/ReminderScheduler.kt`
Wraps `AlarmManager`: `canScheduleExactAlarms()` → `setExactAndAllowWhileIdle`, else `setAndAllowWhileIdle` (graceful §11.2 fallback — surfaced in Settings). One `PendingIntent` identity per task (`requestCode = task.id.hashCode()`, action `dev.vinny.gsd.REMINDER`, data `task-<id>`) so reschedules replace. `fun resyncAll(tasks, settings, now)`: cancel-or-schedule per task from `ReminderPlanner.fireTime`. `class ReminderResync(store, settingsStore, scheduler, scope)`: combines both flows, 1 s debounce, resyncs — catches remote/SSE writes that bypass local mutation hooks (§11.1). Started from `GsdApp`. Verification: planner is JVM-tested; scheduler glue verified by build + a manual `adb shell am broadcast` smoke in Task 4.5 (AlarmManager end-to-end needs wall-clock waits — out of test scope, noted).

### Task 4.4: Receivers, channels, actions — `notifications/ReminderReceiver.kt`, `NotificationActionReceiver.kt`, `BootReceiver.kt`, `Channels.kt`
Channels at app start: `reminders` (DEFAULT importance) + `reminders_silent` (LOW, no sound); the in-app Sound toggle picks the channel at post time (§11.2). ReminderReceiver: reload task from store (skip if completed/deleted), post notification — title = task title, body = description, tap opens MainActivity with `extra_task_id` (the full `gsd://` router is Phase 6), actions **Complete** and **Snooze 1 hour** as broadcasts; then `store.update` marking `notificationSent = true`, `lastNotificationAt = now` (device-local). ActionReceiver: Complete → `store.setCompleted` (recurrence spawn included) + cancel notification; Snooze → `store.snooze(id, now+1h)` + cancel (resync reschedules). BootReceiver on `BOOT_COMPLETED` + `MY_PACKAGE_REPLACED` → full resync. Manifest: `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED` + receiver declarations. MainActivity: `extra_task_id` opens the task's editor.
Tests: instrumented — channels exist after init; action receiver completes a store task (broadcast directly). Boot path by code inspection + manual note.

### Task 4.5: Maintenance job + Settings UI + exit
`MaintenanceWorker` (WorkManager, ~hourly, battery-friendly constraints): auto-archive sweep when enabled (§11.5) — sync hook joins in Phase 5. Registered `KEEP` in `GsdApp`. Settings **Notifications** group (§8.17.5): Enable Reminders, Default Reminder (15 m/30 m/1 h/2 h/1 d), Sound, Quiet Hours toggle + pickers (default 22:00–07:00), permission status row — contextual `POST_NOTIFICATIONS` request on first enablement (§11.2), "Open system settings" when denied, exact-alarm status line when inexact. Deps: `androidx.work:work-runtime-ktx`. Exit: full gate; manual notification smoke on emulator (trigger ReminderReceiver via adb); tag `v0.4.0-phase4`.

# PHASE 5 — task-level plan (planned 2026-07-19, executed in-session)

> Architecture: every protocol rule lives in `:core:sync` as pure JVM-testable code (the module is an
> Android library, but its `src/test` unit tests run on the local JVM with fakes + MockWebServer).
> The Android glue (Custom Tabs, Keystore, lifecycle triggers) stays thin in `:app`. Spec §9–§10 is
> the byte-level contract; §13.6 lists the required test matrix. I have no backend credentials, so
> live validation is limited to unauthenticated URL-shape checks; full sign-in is a user smoke later.

### Task 5.1: Wire model + mapper — `core/sync/.../WireTask.kt`, `WireMapper.kt` + JVM tests
`@Serializable WireTask` with §9.1's exact snake_case names (`task_id`, `client_updated_at`, `time_entries` with **camelCase `startedAt`** inside, `""` for nil dates, `id` = PocketBase record id). `WireMapper.push(task, deviceId): WireTask` — flatten entries to `{id, startedAt, minutes=max(0,floor((end−start)/60))}`, running → 0, notes dropped; `updatedAt→client_updated_at`. `WireMapper.pull(wire, local: Task?): Task?` — lenient (only `task_id` required, unparseable `client_updated_at` → null/skip); no local → reconstruct (`endedAt = startedAt + minutes×60`, `parentTaskId=null`); local exists → remote wins but preserve `parentTaskId`, `notificationSent`, `lastNotificationAt`, `snoozedUntil`, **`timeSpent`+`timeEntries`**; quadrant recomputed; `estimated_minutes == 0` → unset. Tests: §13.6 lossy round-trip, preservation matrix, empty-string dates, unknown fields ignored.

### Task 5.2: JWT + dates + cursor — `core/sync/.../Jwt.kt`, `PbDates.kt` + JVM tests
`Jwt.decodePayload(token): {id, exp}` — base64url middle segment, no signature check (§10.3); missing `id` → error. `PbDates.parse(raw): Instant?` — lenient: space or `T` separator, with/without fraction, `Z`/offset; `PbDates.formatSpace(instant)` (cursor form) and `formatWire(instant)` (fractional-ms `T`/`Z`). `Cursor.advance(maxApplied): String` = maxApplied − 5 s in space form (§9.4). Tests: real-shaped JWT fixture, both date forms, rewind math.

### Task 5.3: SSE — `core/sync/.../SseParser.kt`, `SseApplyRules.kt` + JVM tests
`SseParser` — incremental line feed → events `{id?, event?, data}`: tolerate `data:no-space`, `:` comment heartbeats ignored, multiple `data:` join with `\n`, blank line delimits (§9.6). `PB_CONNECT` → clientId extract (missing → reconnect signal). `SseApplyRules.decide(action, record, ownUserId, ownDeviceId, pendingQueueTaskIds): Decision {ApplyUpsert, ApplyDelete, Drop(reason)}` — owner fail-closed (non-empty owner ≠ own → drop); create/update: drop when `device_id` == own (echo) or a local **delete** pending for that task; delete: drop when *any* queue item pending for the task (deletes are not echo-filtered — §9.6). Tests: framing torture cases, each drop rule, apply paths.

### Task 5.4: PocketBaseApi — `core/sync/.../PocketBaseApi.kt` + MockWebServer tests
OkHttp; **`Authorization: <raw token>` — no Bearer** (§9). `listTasks(page, cursorFilter)` → GET `/api/collections/tasks/records?page&perPage=200&sort=updated&filter=updated >= "{cursor}"` (URL-encoded), envelope decode; `create` (POST, `id:""`), `update` (PATCH by recordId), `delete` (204); typed `RateLimited` on 429; `authMethods`, `authWithOAuth2(provider, code, verifier, redirectURL)`, `authRefresh(token)`, `deleteUser(id)`. Tests: header exactness, filter encoding, envelope paging, 429 → typed error, error mapping. Plus one live unauthenticated GET of `/api/collections/users/auth-methods` to confirm shape (network reachable; skipped-not-failed offline).

### Task 5.5: SyncEngine — `core/sync/.../SyncEngine.kt` + JVM tests over fake ports
Ports: `SyncLocalStore` (tasks by id, upsert-preserving, delete, queue ops, metadata cursor, history insert/prune) implemented over the real DAOs in `:core:store` (thin adapter, Task 5.7); fakes for JVM tests. Engine `runSync(trigger)`: serialized (mutex); **pull** all pages → per record lenient decode → LWW (§9.3: remote newer → apply with preservation; equal/unparseable → no-op; count remote-wins as conflicts) → cursor = maxApplied − 5 s (unparseable `updated` applies but doesn't advance) → **push**: bulk remote index once, drain pending oldest-first gated by backoff (5/10/30/60/300 s via `lastAttemptAt`), LWW payload guard (skip when remote newer), create records live-index insert, 429 → abort loop leaving items pending, other errors bump retry, 5th failure → `failed` (kept); → **deletion reconcile** (last): fresh full index; delete local active tasks absent remotely and not protected by pending-or-failed queue items; archive untouched → **history** insert (§9.7 statuses error > partial > success, counts, duration, triggeredBy) + prune to 500. `requeueFailed()` for launch/foreground/network/manual triggers only. `Health.evaluate(offline, tokenExp, failedCount, oldestPendingAge)` precedence per §9.7. Tests: the full §13.6 matrix.

### Task 5.6: Auth — `core/sync/.../AuthManager.kt`, `app/.../auth/` + tests
`AuthManager(api, tokenStore, clock)`: `beginOAuth(provider)` → fetch auth-methods, hold `{state, codeVerifier}`, build URL (`authURL` + percent-encoded `https://api.vinny.io/ios-oauth-redirect/`); `completeOAuth(callbackUri)` → validate `state` (mismatch abort), exchange via `auth-with-oauth2`, persist token, decode `id` claim (absent → fail fast); `ensureFreshToken()` → refresh when exp − now < 7 d; transient failure (IOException/5xx) keeps stored token, definitive 401/403 clears it (§10.3). `TokenStore` port; `:app` impl = AES/GCM Keystore key, ciphertext in DataStore, never logged. Manifest: `gsd://` BROWSABLE intent filter on MainActivity (`oauth-callback` consumed by auth, ignored by the router per §6.4); Custom Tabs via `androidx.browser`. JVM tests: state mismatch, refresh skew boundary, transient-vs-definitive, JWT without id. Instrumented: TokenStore round-trip.

### Task 5.7: Coordinator + adapters — `core/sync/.../RoomSyncLocalStore.kt`, `app/.../sync/SyncCoordinator.kt`
Room adapter implements `SyncLocalStore` with §9.2 preservation upserts (reuse WireMapper). `SyncCoordinator`: one-at-a-time (coalesced) runs; triggers — app launch, foreground (lifecycle observer), network-regained (`ConnectivityManager` callback), 120 s cadence while foregrounded, 1.5 s debounced push after local mutations (observe `sync_queue` inserts), manual Sync Now; failed auto-requeue on the §9.5 trigger set; SSE lifecycle: foreground-only connect → events through `SseApplyRules` → LWW-upsert with preservation; stream end/error → full sync + reconnect backoff 1 s→30 s (healthy stream resets). Device identity: UUID + model name persisted in `sync_metadata` (§9.8). MaintenanceWorker gains opportunistic sync when signed in. Erase-everywhere (§9.8): list full index, DELETE each (~100 ms throttle), only then clear queue + local wipe; dead session throws (callers refuse local wipe on false success).
Instrumented: adapter preservation upsert; coordinator smoke (signed-out run is a no-op).

### Task 5.8: Account UI + exit
Settings **Account** (§8.17.3): signed out — three provider buttons + privacy hint; signed in — email (+ Apple private-relay note per §10.4), "Synced · N pending", health message when unhealthy, **Sync Now**, **Sync History** (totals + ~50 entries), **Sign Out**, **Delete Account…** (§8.21 two-option + cancel; remote erase → `DELETE /api/collections/users/records/{id}` → clear token). Account-switch prompt (§8.21) when sign-in resolves to a different owner with local active tasks: Keep (seed-upload merge) / Start fresh (erase local) / Cancel (sign out). Sync-status chip in the top bar (§5.10: hidden when idle-healthy-none-pending; spinner syncing; count pending; `sync_problem` alert/ochre). Erase-all-data flow gains the remote-first path when signed in (§8.16). Exit: full gate; UI tests for chip states + account section states; tag `v0.5.0-phase5`. Live end-to-end sign-in is a user smoke (needs real provider credentials).

*— end of plan —*






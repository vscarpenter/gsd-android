package dev.vinny.gsd.model

import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilterCriteriaTest {
    private val zone = ZoneOffset.UTC
    private val now = Instant.parse("2026-07-19T12:00:00.000Z") // a Sunday

    private fun task(
        id: String = NanoId.generate(),
        title: String = "t",
        urgent: Boolean = false,
        important: Boolean = false,
        completed: Boolean = false,
        completedAt: Instant? = null,
        createdAt: Instant = now.minusSeconds(100),
        dueDate: Instant? = null,
        tags: List<String> = emptyList(),
        recurrence: Recurrence = Recurrence.NONE,
        dependencies: List<String> = emptyList(),
        subtasks: List<Subtask> = emptyList(),
        description: String = "",
    ) = Task(
        id = id, title = title, description = description, urgent = urgent, important = important,
        completed = completed, completedAt = completedAt, createdAt = createdAt, updatedAt = createdAt,
        dueDate = dueDate, tags = tags, recurrence = recurrence, dependencies = dependencies,
        subtasks = subtasks,
    )

    private fun matches(criteria: FilterCriteria, task: Task, all: List<Task> = listOf(task)) =
        criteria.matches(task, all, now, zone)

    @Test
    fun `quadrant filter`() {
        val criteria = FilterCriteria(quadrants = listOf("urgent-important"))
        assertTrue(matches(criteria, task(urgent = true, important = true)))
        assertFalse(matches(criteria, task()))
    }

    @Test
    fun `status filter`() {
        assertTrue(matches(FilterCriteria(status = FilterStatus.ACTIVE), task()))
        assertFalse(matches(FilterCriteria(status = FilterStatus.ACTIVE), task(completed = true)))
        assertTrue(matches(FilterCriteria(status = FilterStatus.COMPLETED), task(completed = true)))
        assertTrue(matches(FilterCriteria(status = FilterStatus.ALL), task(completed = true)))
    }

    @Test
    fun `tags require all listed`() {
        val criteria = FilterCriteria(tags = listOf("work", "urgent"))
        assertTrue(matches(criteria, task(tags = listOf("work", "urgent", "extra"))))
        assertFalse(matches(criteria, task(tags = listOf("work"))))
    }

    @Test
    fun `overdue means incomplete and due before today`() {
        val criteria = FilterCriteria(overdue = true)
        assertTrue(matches(criteria, task(dueDate = now.minusSeconds(200_000))))
        assertFalse(matches(criteria, task(dueDate = now.minusSeconds(200_000), completed = true)))
        assertFalse(matches(criteria, task(dueDate = now))) // due today is not overdue
    }

    @Test
    fun `dueToday and dueThisWeek`() {
        assertTrue(matches(FilterCriteria(dueToday = true), task(dueDate = now.plusSeconds(3600))))
        assertFalse(matches(FilterCriteria(dueToday = true), task(dueDate = now.plusSeconds(200_000))))
        // this week = [today, today+7)
        assertTrue(matches(FilterCriteria(dueThisWeek = true), task(dueDate = now.plusSeconds(500_000))))
        assertFalse(matches(FilterCriteria(dueThisWeek = true), task(dueDate = now.plusSeconds(700_000))))
    }

    @Test
    fun `noDueDate and recurrence kinds`() {
        assertTrue(matches(FilterCriteria(noDueDate = true), task()))
        assertFalse(matches(FilterCriteria(noDueDate = true), task(dueDate = now)))
        assertTrue(matches(FilterCriteria(recurrence = listOf(Recurrence.DAILY, Recurrence.WEEKLY)),
            task(recurrence = Recurrence.DAILY)))
        assertFalse(matches(FilterCriteria(recurrence = listOf(Recurrence.DAILY)),
            task(recurrence = Recurrence.NONE)))
    }

    @Test
    fun `recentlyAdded and recentlyCompleted within seven days`() {
        assertTrue(matches(FilterCriteria(recentlyAdded = true), task(createdAt = now.minusSeconds(86_400))))
        assertFalse(matches(FilterCriteria(recentlyAdded = true), task(createdAt = now.minusSeconds(700_000))))
        assertTrue(matches(FilterCriteria(recentlyCompleted = true),
            task(completed = true, completedAt = now.minusSeconds(86_400))))
        assertFalse(matches(FilterCriteria(recentlyCompleted = true),
            task(completed = true, completedAt = now.minusSeconds(700_000))))
    }

    @Test
    fun `readyToWork excludes tasks with uncompleted blockers`() {
        val blocker = task(id = "blocker-000000000000")
        val blocked = task(id = "blocked-000000000000", dependencies = listOf(blocker.id))
        val all = listOf(blocker, blocked)
        assertTrue(matches(FilterCriteria(readyToWork = true), blocker, all))
        assertFalse(matches(FilterCriteria(readyToWork = true), blocked, all))
    }

    @Test
    fun `search spans title description tags and subtask titles`() {
        val criteria = FilterCriteria(searchQuery = "NEEDLE")
        assertTrue(matches(criteria, task(title = "has needle here")))
        assertTrue(matches(criteria, task(description = "the needle hides")))
        assertTrue(matches(criteria, task(tags = listOf("needleful"))))
        assertTrue(matches(criteria, task(subtasks = listOf(Subtask("s1", "find the needle")))))
        assertFalse(matches(criteria, task(title = "nothing")))
    }

    @Test
    fun `criteria are ANDed`() {
        val criteria = FilterCriteria(quadrants = listOf("urgent-important"), status = FilterStatus.ACTIVE)
        assertTrue(matches(criteria, task(urgent = true, important = true)))
        assertFalse(matches(criteria, task(urgent = true, important = true, completed = true)))
    }

    @Test
    fun `nine built-ins with exact ids`() {
        assertEquals(
            listOf(
                "today-focus", "this-week", "overdue", "no-deadline", "recently-added",
                "weeks-wins", "all-completed", "recurring", "ready-to-work",
            ),
            BuiltInSmartViews.all.map { it.id },
        )
        assertTrue(BuiltInSmartViews.all.all { it.isBuiltIn })
    }

    @Test
    fun `today-focus is q1 active`() {
        val view = BuiltInSmartViews.all.first { it.id == "today-focus" }
        assertTrue(matches(view.criteria, task(urgent = true, important = true)))
        assertFalse(matches(view.criteria, task(urgent = true, important = true, completed = true)))
        assertFalse(matches(view.criteria, task()))
    }

    @Test
    fun `search query is trimmed and whitespace-only matches everything`() {
        val task = Task(title = "Ship the deck", urgent = true, important = true, createdAt = now, updatedAt = now)
        assertTrue(FilterCriteria(searchQuery = "  deck ").matches(task, listOf(task), now, zone))
        assertTrue(FilterCriteria(searchQuery = "   ").matches(task, listOf(task), now, zone))
        assertFalse(FilterCriteria(searchQuery = "  slides ").matches(task, listOf(task), now, zone))
    }
}

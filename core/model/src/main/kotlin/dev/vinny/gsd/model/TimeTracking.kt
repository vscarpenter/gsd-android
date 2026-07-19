package dev.vinny.gsd.model

import java.time.Instant

/** Time tracking rules (spec §8.9): one running entry per task; whole-minute floors. */
object TimeTracking {
    class TimerAlreadyRunning : Exception("A timer is already running for this task")
    class NoRunningTimer : Exception("No timer is running for this task")

    fun runningEntry(task: Task): TimeEntry? = task.timeEntries.firstOrNull { it.endedAt == null }

    fun start(task: Task, now: Instant): Task {
        if (runningEntry(task) != null) throw TimerAlreadyRunning()
        return task.copy(timeEntries = task.timeEntries + TimeEntry(NanoId.generate(8), now))
    }

    fun stop(task: Task, now: Instant): Task {
        val running = runningEntry(task) ?: throw NoRunningTimer()
        return task.copy(
            timeEntries = task.timeEntries.map { if (it.id == running.id) it.copy(endedAt = now) else it },
        )
    }

    fun formatMinutes(totalMinutes: Int): String = when {
        totalMinutes < 1 -> "< 1m"
        totalMinutes < 60 -> "${totalMinutes}m"
        totalMinutes % 60 == 0 -> "${totalMinutes / 60}h"
        else -> "${totalMinutes / 60}h ${totalMinutes % 60}m"
    }
}

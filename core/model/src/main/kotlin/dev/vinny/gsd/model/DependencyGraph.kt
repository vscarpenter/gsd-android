package dev.vinny.gsd.model

/**
 * The blocking graph (spec §8.8). Edges point from a task to the tasks it
 * depends on; cycle prevention walks BFS from the candidate dependency and
 * rejects if the depending task is reachable.
 */
object DependencyGraph {
    fun wouldCreateCycle(tasks: List<Task>, taskId: String, candidateDependencyId: String): Boolean {
        if (taskId == candidateDependencyId) return true
        val byId = tasks.associateBy { it.id }
        val queue = ArrayDeque(listOf(candidateDependencyId))
        val visited = mutableSetOf<String>()
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current == taskId) return true
            if (!visited.add(current)) continue
            byId[current]?.dependencies?.forEach(queue::addLast)
        }
        return false
    }

    fun uncompletedBlockers(tasks: List<Task>, task: Task): List<Task> {
        val byId = tasks.associateBy { it.id }
        return task.dependencies.mapNotNull { byId[it] }.filter { !it.completed }
    }

    fun isBlocked(tasks: List<Task>, task: Task): Boolean =
        uncompletedBlockers(tasks, task).isNotEmpty()

    fun blocking(tasks: List<Task>, task: Task): List<Task> =
        tasks.filter { task.id in it.dependencies }

    /** Incomplete tasks with no uncompleted blocker — powers "Ready to Work". */
    fun readySet(tasks: List<Task>): Set<String> =
        tasks.filter { !it.completed && !isBlocked(tasks, it) }.map { it.id }.toSet()
}

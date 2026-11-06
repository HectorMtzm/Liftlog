package com.hectormtzm.liftlog.ui.workout

import com.hectormtzm.liftlog.data.WorkoutExerciseFull

/**
 * Name of the exercise whose set comes next after [doneSetId] is completed: the same exercise
 * while it has sets left, otherwise the next exercise (in workout order, wrapping around) that
 * still has unfinished sets. Null when every set is done.
 */
fun nextUpName(exercises: List<WorkoutExerciseFull>, doneSetId: Long): String? {
    fun remaining(we: WorkoutExerciseFull) = we.sets.any { !it.completed && it.id != doneSetId }
    val ordered = exercises.sortedBy { it.item.position }
    val current = ordered.indexOfFirst { we -> we.sets.any { it.id == doneSetId } }
    if (current < 0) return ordered.firstOrNull(::remaining)?.displayName
    if (remaining(ordered[current])) return ordered[current].displayName
    for (step in 1 until ordered.size) {
        val we = ordered[(current + step) % ordered.size]
        if (remaining(we)) return we.displayName
    }
    return null
}

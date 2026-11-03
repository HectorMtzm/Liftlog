package com.hectormtzm.liftlog.data

import androidx.room.withTransaction

class WorkoutRepository(
    private val db: LiftlogDatabase,
    private val settings: SettingsRepository,
) {
    private val dao = db.workoutDao()
    private val planDao = db.planDao()

    fun observeActive() = dao.observeActive()
    fun observeWorkout(id: Long) = dao.observeWorkout(id)
    fun observeExercises(workoutId: Long) = dao.observeExercises(workoutId)
    fun observeHistory() = dao.observeHistory()
    fun observeExerciseHistory(exerciseId: String) = dao.observeExerciseHistory(exerciseId)
    suspend fun getActive() = dao.getActive()

    /** Last completed sets of an exercise from a previous finished workout. */
    suspend fun previousSets(exerciseId: String, currentWorkoutId: Long) = dao.lastSets(exerciseId, currentWorkoutId)

    /** Starts a workout from a plan, pre-filling every set from the plan targets. */
    suspend fun startFromPlan(planId: Long): Long = db.withTransaction {
        dao.getActive()?.let { return@withTransaction it.id }
        val plan = planDao.getPlan(planId) ?: return@withTransaction -1L
        val workoutId = dao.insertWorkout(
            WorkoutEntity(planId = planId, name = plan.name, startedAt = System.currentTimeMillis())
        )
        planDao.getExercises(planId).forEachIndexed { index, pe ->
            val weId = dao.insertWorkoutExercise(
                WorkoutExerciseEntity(
                    workoutId = workoutId,
                    exerciseId = pe.item.exerciseId,
                    planExerciseId = pe.item.id,
                    position = index,
                    restSeconds = pe.item.restSeconds,
                )
            )
            pe.sets.sortedBy { it.position }.forEachIndexed { i, s ->
                dao.insertSet(WorkoutSetEntity(workoutExerciseId = weId, position = i, reps = s.reps, weightKg = s.weightKg))
            }
        }
        workoutId
    }

    suspend fun startEmpty(): Long = db.withTransaction {
        dao.getActive()?.let { return@withTransaction it.id }
        dao.insertWorkout(WorkoutEntity(planId = null, name = "Workout", startedAt = System.currentTimeMillis()))
    }

    suspend fun addExercises(workoutId: Long, exerciseIds: List<String>) = db.withTransaction {
        val rest = settings.current().defaultRestSeconds
        var pos = dao.maxExercisePosition(workoutId)
        exerciseIds.forEach { exId ->
            pos += 1
            val weId = dao.insertWorkoutExercise(
                WorkoutExerciseEntity(
                    workoutId = workoutId, exerciseId = exId, planExerciseId = null, position = pos, restSeconds = rest,
                )
            )
            val last = dao.lastSets(exId, workoutId)
            if (last.isNotEmpty()) {
                last.forEachIndexed { i, s ->
                    dao.insertSet(WorkoutSetEntity(workoutExerciseId = weId, position = i, reps = s.reps, weightKg = s.weightKg))
                }
            } else {
                repeat(3) { i -> dao.insertSet(WorkoutSetEntity(workoutExerciseId = weId, position = i, reps = 10, weightKg = 0.0)) }
            }
        }
    }

    /** Swaps the exercise of a workout entry for another one, keeping the set targets. */
    suspend fun replaceExercise(workoutExerciseId: Long, newExerciseId: String) = db.withTransaction {
        val we = dao.getWorkoutExercise(workoutExerciseId) ?: return@withTransaction
        dao.updateWorkoutExercises(listOf(we.copy(exerciseId = newExerciseId, planExerciseId = null)))
        dao.getSets(workoutExerciseId).forEach { if (it.isPr) dao.updateSet(it.copy(isPr = false)) }
    }

    suspend fun removeExercise(workoutExerciseId: Long) = dao.deleteWorkoutExercise(workoutExerciseId)

    suspend fun moveExercise(workoutId: Long, workoutExerciseId: Long, delta: Int) = db.withTransaction {
        val items = dao.getExercises(workoutId).map { it.item }.toMutableList()
        val from = items.indexOfFirst { it.id == workoutExerciseId }
        val to = from + delta
        if (from < 0 || to !in items.indices) return@withTransaction
        val moved = items.removeAt(from)
        items.add(to, moved)
        dao.updateWorkoutExercises(items.mapIndexed { i, it -> it.copy(position = i) })
    }

    suspend fun setRest(workoutExerciseId: Long, seconds: Int) {
        val we = dao.getWorkoutExercise(workoutExerciseId) ?: return
        dao.updateWorkoutExercises(listOf(we.copy(restSeconds = seconds)))
    }

    suspend fun addSet(workoutExerciseId: Long) {
        val sets = dao.getSets(workoutExerciseId)
        val last = sets.lastOrNull()
        dao.insertSet(
            WorkoutSetEntity(
                workoutExerciseId = workoutExerciseId,
                position = (last?.position ?: -1) + 1,
                reps = last?.reps ?: 10,
                weightKg = last?.weightKg ?: 0.0,
            )
        )
    }

    suspend fun removeSet(setId: Long) = dao.deleteSet(setId)

    /** Updates reps / weight of a set. Recomputes the PR flag when the set is already done. */
    suspend fun updateSet(setId: Long, reps: Int, weightKg: Double) = db.withTransaction {
        val s = dao.getSet(setId) ?: return@withTransaction
        var updated = s.copy(reps = reps, weightKg = weightKg)
        if (updated.completed) updated = updated.copy(isPr = isPersonalRecord(updated))
        dao.updateSet(updated)
    }

    /**
     * Toggles a set's completion. Returns the rest duration in seconds to start a timer
     * with when the set was just completed, or null when it was un-completed.
     */
    suspend fun toggleSet(setId: Long): SetToggleResult? = db.withTransaction {
        val s = dao.getSet(setId) ?: return@withTransaction null
        if (s.completed) {
            dao.updateSet(s.copy(completed = false, completedAt = null, isPr = false))
            null
        } else {
            val done = s.copy(completed = true, completedAt = System.currentTimeMillis())
            val pr = isPersonalRecord(done)
            dao.updateSet(done.copy(isPr = pr))
            val we = dao.getWorkoutExercise(s.workoutExerciseId)
            SetToggleResult(restSeconds = we?.restSeconds ?: 0, isPr = pr)
        }
    }

    /** A set is a PR when it beats the best weight or best estimated 1RM of all earlier sets. */
    private suspend fun isPersonalRecord(set: WorkoutSetEntity): Boolean {
        if (set.weightKg <= 0 || set.reps <= 0) return false
        val we = dao.getWorkoutExercise(set.workoutExerciseId) ?: return false
        val bestWeight = dao.bestWeight(we.exerciseId, set.id) ?: return false
        val bestE1rm = dao.bestE1rm(we.exerciseId, set.id) ?: 0.0
        val eps = 1e-6
        return set.weightKg > bestWeight + eps || estimatedOneRepMax(set.weightKg, set.reps) > bestE1rm + eps
    }

    /**
     * Finishes a workout: drops unfinished sets and empty exercises, stamps the end time and
     * (optionally) writes the lifted weights back to the plan. Returns false if nothing was logged
     * and the workout was discarded.
     */
    suspend fun finish(workoutId: Long): Boolean = db.withTransaction {
        dao.deleteIncompleteSets(workoutId)
        dao.deleteEmptyExercises(workoutId)
        val exercises = dao.getExercises(workoutId)
        val workout = dao.getWorkout(workoutId) ?: return@withTransaction false
        if (exercises.isEmpty()) {
            dao.deleteWorkout(workoutId)
            return@withTransaction false
        }
        dao.updateWorkout(workout.copy(finishedAt = System.currentTimeMillis()))
        if (settings.current().updatePlanWeights) {
            exercises.forEach { we ->
                val peId = we.item.planExerciseId ?: return@forEach
                if (planDao.getPlanExercise(peId) == null) return@forEach
                val planSets = planDao.getSets(peId)
                val done = we.sets.filter { it.completed }.sortedBy { it.position }
                planSets.forEachIndexed { i, ps ->
                    val lifted = done.getOrNull(i) ?: return@forEachIndexed
                    if (lifted.weightKg != ps.weightKg) planDao.updateSet(ps.copy(weightKg = lifted.weightKg))
                }
            }
        }
        true
    }

    suspend fun discard(workoutId: Long) = dao.deleteWorkout(workoutId)
    suspend fun delete(workoutId: Long) = dao.deleteWorkout(workoutId)
}

data class SetToggleResult(val restSeconds: Int, val isPr: Boolean)

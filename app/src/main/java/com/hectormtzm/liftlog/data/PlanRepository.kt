package com.hectormtzm.liftlog.data

import androidx.room.withTransaction

class PlanRepository(
    private val db: LiftlogDatabase,
    private val settings: SettingsRepository,
) {
    private val dao = db.planDao()
    private val workoutDao = db.workoutDao()

    fun observeSummaries() = dao.observeSummaries()
    fun observePlan(id: Long) = dao.observePlan(id)
    fun observeExercises(planId: Long) = dao.observeExercises(planId)

    suspend fun createPlan(name: String): Long =
        dao.insertPlan(PlanEntity(name = name.trim(), position = dao.maxPlanPosition() + 1))

    suspend fun renamePlan(id: Long, name: String) {
        val plan = dao.getPlan(id) ?: return
        dao.updatePlan(plan.copy(name = name.trim()))
    }

    suspend fun deletePlan(id: Long) = dao.deletePlan(id)

    suspend fun duplicatePlan(id: Long): Long = db.withTransaction {
        val plan = dao.getPlan(id) ?: return@withTransaction -1L
        val newId = dao.insertPlan(
            PlanEntity(name = plan.name + " (copy)", notes = plan.notes, position = dao.maxPlanPosition() + 1)
        )
        dao.getExercises(id).forEach { full ->
            val peId = dao.insertPlanExercise(full.item.copy(id = 0, planId = newId))
            full.sets.forEach { s -> dao.insertSet(s.copy(id = 0, planExerciseId = peId)) }
        }
        newId
    }

    /** Adds exercises with 3 sets of 10, seeding weight from the last time it was lifted. */
    suspend fun addExercises(planId: Long, exerciseIds: List<String>) = db.withTransaction {
        val rest = settings.current().defaultRestSeconds
        var pos = dao.maxExercisePosition(planId)
        exerciseIds.forEach { exId ->
            pos += 1
            val peId = dao.insertPlanExercise(
                PlanExerciseEntity(planId = planId, exerciseId = exId, position = pos, restSeconds = rest)
            )
            val last = workoutDao.lastSets(exId, -1)
            if (last.isNotEmpty()) {
                last.forEachIndexed { i, s ->
                    dao.insertSet(PlanSetEntity(planExerciseId = peId, position = i, reps = s.reps, weightKg = s.weightKg))
                }
            } else {
                repeat(3) { i ->
                    dao.insertSet(PlanSetEntity(planExerciseId = peId, position = i, reps = 10, weightKg = 0.0))
                }
            }
        }
    }

    suspend fun removeExercise(planExerciseId: Long) = dao.deletePlanExercise(planExerciseId)

    suspend fun moveExercise(planId: Long, planExerciseId: Long, delta: Int) = db.withTransaction {
        val items = dao.getExercises(planId).map { it.item }.toMutableList()
        val from = items.indexOfFirst { it.id == planExerciseId }
        val to = from + delta
        if (from < 0 || to !in items.indices) return@withTransaction
        val moved = items.removeAt(from)
        items.add(to, moved)
        dao.updatePlanExercises(items.mapIndexed { i, it -> it.copy(position = i) })
    }

    suspend fun setRest(planExerciseId: Long, seconds: Int) {
        val pe = dao.getPlanExercise(planExerciseId) ?: return
        dao.updatePlanExercises(listOf(pe.copy(restSeconds = seconds)))
    }

    /** Shows [name] for this exercise in the plan and its workouts; blank restores the original name. */
    suspend fun renameExercise(planExerciseId: Long, name: String) {
        val pe = dao.getPlanExercise(planExerciseId) ?: return
        dao.updatePlanExercises(listOf(pe.copy(customName = name.trim().ifBlank { null })))
    }

    suspend fun updateSet(set: PlanSetEntity) = dao.updateSet(set)

    suspend fun addSet(planExerciseId: Long) {
        val sets = dao.getSets(planExerciseId)
        val last = sets.lastOrNull()
        dao.insertSet(
            PlanSetEntity(
                planExerciseId = planExerciseId,
                position = (last?.position ?: -1) + 1,
                reps = last?.reps ?: 10,
                weightKg = last?.weightKg ?: 0.0,
            )
        )
    }

    suspend fun removeSet(setId: Long) = dao.deleteSet(setId)

    /** Applies the same reps / weight to every set of a plan exercise. */
    suspend fun applyToAllSets(planExerciseId: Long, count: Int, reps: Int, weightKg: Double) = db.withTransaction {
        val sets = dao.getSets(planExerciseId)
        sets.forEachIndexed { i, s ->
            if (i < count) dao.updateSet(s.copy(reps = reps, weightKg = weightKg)) else dao.deleteSet(s.id)
        }
        for (i in sets.size until count) {
            dao.insertSet(PlanSetEntity(planExerciseId = planExerciseId, position = i, reps = reps, weightKg = weightKg))
        }
    }
}

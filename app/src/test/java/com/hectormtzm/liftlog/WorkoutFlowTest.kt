package com.hectormtzm.liftlog

import androidx.test.core.app.ApplicationProvider
import com.hectormtzm.liftlog.data.ExerciseEntity
import com.hectormtzm.liftlog.data.WeightUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkoutFlowTest {
    private val app: LiftlogApp = ApplicationProvider.getApplicationContext()
    private val c get() = app.container

    @Test
    fun seedsExerciseLibrary() = runBlocking {
        c.exercises.seedIfNeeded()
        val all = c.exercises.observeAll().first()
        assertTrue("expected the bundled library, got ${all.size}", all.size > 800)
        val squat = c.exercises.get("Barbell_Squat")
        assertNotNull(squat)
        assertTrue(squat!!.primaryMuscleList.contains("quadriceps"))
    }

    @Test
    fun planWorkoutPrAndWriteBack() = runBlocking {
        c.db.exerciseDao().upsert(ExerciseEntity(id = "bench", name = "Bench Press", primaryMuscles = "chest"))
        val planId = c.plans.createPlan("Push")
        c.plans.addExercises(planId, listOf("bench"))
        val pe = c.plans.observeExercises(planId).first().single()
        assertEquals(3, pe.sets.size)
        assertEquals(c.settings.current().defaultRestSeconds, pe.item.restSeconds)
        c.plans.applyToAllSets(pe.item.id, 3, 8, WeightUnit.LB.toKg(135.0))
        c.plans.setRest(pe.item.id, 120)

        // Workout 1: pre-filled from the plan
        val w1 = c.workouts.startFromPlan(planId)
        var ex = c.workouts.observeExercises(w1).first().single()
        assertEquals(120, ex.item.restSeconds)
        assertEquals(listOf(8, 8, 8), ex.sets.map { it.reps })
        assertEquals("135", WeightUnit.LB.format(ex.sets[0].weightKg))

        // Starting again returns the same active workout
        assertEquals(w1, c.workouts.startFromPlan(planId))

        val first = ex.sets.sortedBy { it.position }
        val r = c.workouts.toggleSet(first[0].id)
        assertNotNull(r)
        assertEquals(120, r!!.restSeconds)
        assertFalse("first ever set is not a PR", r.isPr)
        // second set heavier -> PR against the first set
        c.workouts.updateSet(first[1].id, 8, WeightUnit.LB.toKg(145.0))
        assertTrue(c.workouts.toggleSet(first[1].id)!!.isPr)
        // un-toggle clears it
        assertNull(c.workouts.toggleSet(first[1].id))
        assertTrue(c.workouts.toggleSet(first[1].id)!!.isPr)
        // third set left undone -> dropped on finish
        assertTrue(c.workouts.finish(w1))
        assertNull(c.workouts.getActive())

        ex = c.workouts.observeExercises(w1).first().single()
        assertEquals(2, ex.sets.size)

        // Plan weights updated to what was lifted for the completed sets
        val planSets = c.plans.observeExercises(planId).first().single().sets.sortedBy { it.position }
        assertEquals(listOf("135", "145", "135"), planSets.map { WeightUnit.LB.format(it.weightKg) })

        // History summary
        val h = c.workouts.observeHistory().first().single()
        assertEquals(2, h.setCount)
        assertEquals(1, h.prCount)

        // Workout 2: previous sets come from workout 1
        val w2 = c.workouts.startFromPlan(planId)
        val prev = c.workouts.previousSets("bench", w2)
        assertEquals(2, prev.size)
        assertEquals("145", WeightUnit.LB.format(prev[1].weightKg))
        // finishing with nothing done discards the workout
        assertFalse(c.workouts.finish(w2))
        assertNull(c.workouts.getActive())
        assertEquals(1, c.workouts.observeHistory().first().size)
    }

    @Test
    fun unitsRoundTrip() {
        listOf(45.0, 135.0, 225.0, 62.5, 2.5).forEach { v ->
            assertEquals(WeightUnit.formatNumber(v), WeightUnit.LB.format(WeightUnit.LB.toKg(v)))
        }
        assertEquals("62.5", WeightUnit.formatNumber(62.5))
        assertEquals("100", WeightUnit.formatNumber(100.0))
    }

    @Test
    fun workoutChangesSyncToPlan() = runBlocking {
        c.db.exerciseDao().upsert(ExerciseEntity(id = "press", name = "Leg Press", primaryMuscles = "quadriceps"))
        val lb = WeightUnit.LB
        val planId = c.plans.createPlan("Legs")
        c.plans.addExercises(planId, listOf("press"))
        val pe = c.plans.observeExercises(planId).first().single()
        c.plans.applyToAllSets(pe.item.id, 3, 10, lb.toKg(200.0))
        c.plans.setRest(pe.item.id, 120)
        c.plans.renameExercise(pe.item.id, "Single leg leg press")

        val w = c.workouts.startFromPlan(planId)
        var we = c.workouts.observeExercises(w).first().single()
        assertEquals("Single leg leg press", we.displayName)
        assertEquals("Leg Press", we.exercise!!.name)
        val (s1, s2, s3) = we.sets.sortedBy { it.position }
        c.workouts.removeSet(s2.id)                                   // deleted -> removed from plan
        c.workouts.updateSet(s1.id, 10, lb.toKg(180.0))               // lighter -> plan keeps 200
        c.workouts.toggleSet(s1.id)
        // s3 skipped -> stays in plan at 200
        c.workouts.addSet(we.item.id)                                 // added -> appended to plan
        we = c.workouts.observeExercises(w).first().single()
        val added = we.sets.maxBy { it.position }
        c.workouts.updateSet(added.id, 8, lb.toKg(230.0))             // heavier -> 230
        c.workouts.toggleSet(added.id)
        c.workouts.setRest(we.item.id, 90)
        assertTrue(c.workouts.finish(w))

        val plan = c.plans.observeExercises(planId).first().single()
        assertEquals(90, plan.item.restSeconds)
        val sets = plan.sets.sortedBy { it.position }
        assertEquals(listOf("200", "200", "230"), sets.map { lb.format(it.weightKg) })
        assertEquals(listOf(10, 10, 8), sets.map { it.reps })
        assertEquals("Single leg leg press", plan.displayName)

        // Heavier on an existing set raises it; the next workout links to the new plan sets.
        val w2 = c.workouts.startFromPlan(planId)
        val first = c.workouts.observeExercises(w2).first().single().sets.minBy { it.position }
        c.workouts.updateSet(first.id, 10, lb.toKg(210.0))
        c.workouts.toggleSet(first.id)
        c.workouts.finish(w2)
        val after = c.plans.observeExercises(planId).first().single().sets.sortedBy { it.position }
        assertEquals(listOf("210", "200", "230"), after.map { lb.format(it.weightKg) })

        // Clearing the custom name restores the original.
        c.plans.renameExercise(pe.item.id, "")
        assertEquals("Leg Press", c.plans.observeExercises(planId).first().single().displayName)
    }
}

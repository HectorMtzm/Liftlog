package com.hectormtzm.liftlog.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT COUNT(*) FROM exercises WHERE id LIKE 'edb_%'")
    suspend fun datasetCount(): Int

    @Query("SELECT * FROM exercises WHERE id LIKE 'edb_%'")
    suspend fun datasetExercises(): List<ExerciseEntity>

    /** Built-in exercises from the earlier free-exercise-db library. */
    @Query("SELECT * FROM exercises WHERE isCustom = 0 AND id NOT LIKE 'edb_%'")
    suspend fun legacyBuiltIns(): List<ExerciseEntity>

    @Query("UPDATE plan_exercises SET exerciseId = :newId WHERE exerciseId = :oldId")
    suspend fun repointPlans(oldId: String, newId: String)

    @Query("UPDATE workout_exercises SET exerciseId = :newId WHERE exerciseId = :oldId")
    suspend fun repointWorkouts(oldId: String, newId: String)

    @Query(
        """SELECT (SELECT COUNT(*) FROM plan_exercises WHERE exerciseId = :id)
            + (SELECT COUNT(*) FROM workout_exercises WHERE exerciseId = :id)"""
    )
    suspend fun referenceCount(id: String): Int

    @Query("DELETE FROM exercises WHERE id = :id")
    suspend fun delete(id: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<ExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ExerciseEntity)

    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    fun observe(id: String): Flow<ExerciseEntity?>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun get(id: String): ExerciseEntity?

    @Query("DELETE FROM exercises WHERE id = :id AND isCustom = 1")
    suspend fun deleteCustom(id: String)
}

@Dao
interface PlanDao {
    @Query(
        """
        SELECT p.id, p.name,
            (SELECT COUNT(*) FROM plan_exercises pe WHERE pe.planId = p.id) AS exerciseCount,
            (SELECT MAX(w.startedAt) FROM workouts w WHERE w.planId = p.id AND w.finishedAt IS NOT NULL) AS lastPerformed
        FROM plans p ORDER BY p.position, p.id
        """
    )
    fun observeSummaries(): Flow<List<PlanSummary>>

    @Query("SELECT * FROM plans WHERE id = :id")
    fun observePlan(id: Long): Flow<PlanEntity?>

    @Query("SELECT * FROM plans WHERE id = :id")
    suspend fun getPlan(id: Long): PlanEntity?

    @Query("SELECT COALESCE(MAX(position), -1) FROM plans")
    suspend fun maxPlanPosition(): Int

    @Insert
    suspend fun insertPlan(plan: PlanEntity): Long

    @Update
    suspend fun updatePlan(plan: PlanEntity)

    @Query("DELETE FROM plans WHERE id = :id")
    suspend fun deletePlan(id: Long)

    @Transaction
    @Query("SELECT * FROM plan_exercises WHERE planId = :planId ORDER BY position")
    fun observeExercises(planId: Long): Flow<List<PlanExerciseFull>>

    @Transaction
    @Query("SELECT * FROM plan_exercises WHERE planId = :planId ORDER BY position")
    suspend fun getExercises(planId: Long): List<PlanExerciseFull>

    @Query("SELECT * FROM plan_exercises WHERE id = :id")
    suspend fun getPlanExercise(id: Long): PlanExerciseEntity?

    @Query("SELECT COALESCE(MAX(position), -1) FROM plan_exercises WHERE planId = :planId")
    suspend fun maxExercisePosition(planId: Long): Int

    @Insert
    suspend fun insertPlanExercise(item: PlanExerciseEntity): Long

    @Update
    suspend fun updatePlanExercises(items: List<PlanExerciseEntity>)

    @Query("DELETE FROM plan_exercises WHERE id = :id")
    suspend fun deletePlanExercise(id: Long)

    @Query("DELETE FROM plan_exercises WHERE exerciseId = :exerciseId")
    suspend fun deleteByExercise(exerciseId: String)

    @Query("SELECT * FROM plan_sets WHERE planExerciseId = :planExerciseId ORDER BY position")
    suspend fun getSets(planExerciseId: Long): List<PlanSetEntity>

    @Insert
    suspend fun insertSet(set: PlanSetEntity): Long

    @Update
    suspend fun updateSet(set: PlanSetEntity)

    @Query("DELETE FROM plan_sets WHERE id = :id")
    suspend fun deleteSet(id: Long)
}

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workouts WHERE finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeActive(): Flow<WorkoutEntity?>

    @Query("SELECT * FROM workouts WHERE finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActive(): WorkoutEntity?

    @Query("SELECT * FROM workouts WHERE id = :id")
    fun observeWorkout(id: Long): Flow<WorkoutEntity?>

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getWorkout(id: Long): WorkoutEntity?

    @Insert
    suspend fun insertWorkout(w: WorkoutEntity): Long

    @Update
    suspend fun updateWorkout(w: WorkoutEntity)

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteWorkout(id: Long)

    @Transaction
    @Query("SELECT * FROM workout_exercises WHERE workoutId = :workoutId ORDER BY position")
    fun observeExercises(workoutId: Long): Flow<List<WorkoutExerciseFull>>

    @Transaction
    @Query("SELECT * FROM workout_exercises WHERE workoutId = :workoutId ORDER BY position")
    suspend fun getExercises(workoutId: Long): List<WorkoutExerciseFull>

    @Query("SELECT * FROM workout_exercises WHERE id = :id")
    suspend fun getWorkoutExercise(id: Long): WorkoutExerciseEntity?

    @Query("SELECT COALESCE(MAX(position), -1) FROM workout_exercises WHERE workoutId = :workoutId")
    suspend fun maxExercisePosition(workoutId: Long): Int

    @Insert
    suspend fun insertWorkoutExercise(item: WorkoutExerciseEntity): Long

    @Update
    suspend fun updateWorkoutExercises(items: List<WorkoutExerciseEntity>)

    @Query("DELETE FROM workout_exercises WHERE id = :id")
    suspend fun deleteWorkoutExercise(id: Long)

    @Query("SELECT * FROM workout_sets WHERE id = :id")
    suspend fun getSet(id: Long): WorkoutSetEntity?

    @Query("SELECT * FROM workout_sets WHERE workoutExerciseId = :workoutExerciseId ORDER BY position")
    suspend fun getSets(workoutExerciseId: Long): List<WorkoutSetEntity>

    @Insert
    suspend fun insertSet(set: WorkoutSetEntity): Long

    @Update
    suspend fun updateSet(set: WorkoutSetEntity)

    @Query("DELETE FROM workout_sets WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Query(
        """
        SELECT MAX(s.weightKg) FROM workout_sets s
        JOIN workout_exercises we ON s.workoutExerciseId = we.id
        WHERE we.exerciseId = :exerciseId AND s.completed = 1 AND s.id != :excludeSetId
        """
    )
    suspend fun bestWeight(exerciseId: String, excludeSetId: Long): Double?

    @Query(
        """
        SELECT MAX(s.weightKg * (1 + s.reps / 30.0)) FROM workout_sets s
        JOIN workout_exercises we ON s.workoutExerciseId = we.id
        WHERE we.exerciseId = :exerciseId AND s.completed = 1 AND s.id != :excludeSetId AND s.reps > 0
        """
    )
    suspend fun bestE1rm(exerciseId: String, excludeSetId: Long): Double?

    /** Completed sets from the most recent finished workout that included this exercise. */
    @Query(
        """
        SELECT s.* FROM workout_sets s
        WHERE s.completed = 1 AND s.workoutExerciseId = (
            SELECT we.id FROM workout_exercises we
            JOIN workouts w ON we.workoutId = w.id
            WHERE we.exerciseId = :exerciseId AND w.finishedAt IS NOT NULL AND w.id != :excludeWorkoutId
            AND EXISTS (SELECT 1 FROM workout_sets s2 WHERE s2.workoutExerciseId = we.id AND s2.completed = 1)
            ORDER BY w.startedAt DESC, we.position DESC LIMIT 1
        )
        ORDER BY s.position
        """
    )
    suspend fun lastSets(exerciseId: String, excludeWorkoutId: Long): List<WorkoutSetEntity>

    @Query(
        """
        SELECT w.id, w.name, w.startedAt, w.finishedAt,
            (SELECT COUNT(*) FROM workout_sets s JOIN workout_exercises we ON s.workoutExerciseId = we.id
                WHERE we.workoutId = w.id AND s.completed = 1) AS setCount,
            (SELECT COALESCE(SUM(s.weightKg * s.reps), 0) FROM workout_sets s JOIN workout_exercises we ON s.workoutExerciseId = we.id
                WHERE we.workoutId = w.id AND s.completed = 1) AS volumeKg,
            (SELECT COUNT(*) FROM workout_sets s JOIN workout_exercises we ON s.workoutExerciseId = we.id
                WHERE we.workoutId = w.id AND s.completed = 1 AND s.isPr = 1) AS prCount,
            (SELECT COUNT(*) FROM workout_exercises we WHERE we.workoutId = w.id) AS exerciseCount
        FROM workouts w WHERE w.finishedAt IS NOT NULL ORDER BY w.startedAt DESC
        """
    )
    fun observeHistory(): Flow<List<WorkoutSummary>>

    @Query(
        """
        SELECT w.id AS workoutId, w.name AS workoutName, w.startedAt AS startedAt, s.id AS setId,
            s.position AS position, s.reps AS reps, s.weightKg AS weightKg, s.isPr AS isPr
        FROM workout_sets s
        JOIN workout_exercises we ON s.workoutExerciseId = we.id
        JOIN workouts w ON we.workoutId = w.id
        WHERE we.exerciseId = :exerciseId AND s.completed = 1 AND w.finishedAt IS NOT NULL
        ORDER BY w.startedAt DESC, we.position, s.position
        """
    )
    fun observeExerciseHistory(exerciseId: String): Flow<List<ExerciseSetRow>>

    @Query(
        """
        DELETE FROM workout_sets WHERE completed = 0 AND workoutExerciseId IN
            (SELECT id FROM workout_exercises WHERE workoutId = :workoutId)
        """
    )
    suspend fun deleteIncompleteSets(workoutId: Long)

    @Query(
        """
        DELETE FROM workout_exercises WHERE workoutId = :workoutId
            AND id NOT IN (SELECT workoutExerciseId FROM workout_sets)
        """
    )
    suspend fun deleteEmptyExercises(workoutId: Long)
}

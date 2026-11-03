package com.hectormtzm.liftlog.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** An exercise from the bundled library (free-exercise-db) or created by the user. */
@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String = "",
    val equipment: String = "",
    val level: String = "",
    val force: String = "",
    val mechanic: String = "",
    /** Comma separated. */
    val primaryMuscles: String = "",
    /** Comma separated. */
    val secondaryMuscles: String = "",
    /** Newline separated steps. */
    val instructions: String = "",
    /** Pipe separated relative image paths. */
    val images: String = "",
    val isCustom: Boolean = false,
) {
    val primaryMuscleList: List<String> get() = split(primaryMuscles, ',')
    val secondaryMuscleList: List<String> get() = split(secondaryMuscles, ',')
    val instructionList: List<String> get() = split(instructions, '\n')
    val imageList: List<String> get() = split(images, '|')

    private fun split(s: String, c: Char) = s.split(c).map { it.trim() }.filter { it.isNotEmpty() }
}

@Entity(tableName = "plans")
data class PlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val notes: String = "",
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "plan_exercises",
    foreignKeys = [ForeignKey(
        entity = PlanEntity::class,
        parentColumns = ["id"],
        childColumns = ["planId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("planId"), Index("exerciseId")],
)
data class PlanExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val exerciseId: String,
    val position: Int,
    val restSeconds: Int,
    val notes: String = "",
)

/** A target set inside a plan. Weight is always stored in kilograms. */
@Entity(
    tableName = "plan_sets",
    foreignKeys = [ForeignKey(
        entity = PlanExerciseEntity::class,
        parentColumns = ["id"],
        childColumns = ["planExerciseId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("planExerciseId")],
)
data class PlanSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planExerciseId: Long,
    val position: Int,
    val reps: Int,
    val weightKg: Double,
)

@Entity(tableName = "workouts", indices = [Index("planId"), Index("finishedAt")])
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long?,
    val name: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val notes: String = "",
)

@Entity(
    tableName = "workout_exercises",
    foreignKeys = [ForeignKey(
        entity = WorkoutEntity::class,
        parentColumns = ["id"],
        childColumns = ["workoutId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("workoutId"), Index("exerciseId")],
)
data class WorkoutExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: String,
    /** The plan exercise this came from, used to write weights back to the plan. */
    val planExerciseId: Long?,
    val position: Int,
    val restSeconds: Int,
)

/** A performed (or pending) set. Weight is always stored in kilograms. */
@Entity(
    tableName = "workout_sets",
    foreignKeys = [ForeignKey(
        entity = WorkoutExerciseEntity::class,
        parentColumns = ["id"],
        childColumns = ["workoutExerciseId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("workoutExerciseId")],
)
data class WorkoutSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutExerciseId: Long,
    val position: Int,
    val reps: Int,
    val weightKg: Double,
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val isPr: Boolean = false,
)

data class PlanExerciseFull(
    @Embedded val item: PlanExerciseEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity?,
    @Relation(parentColumn = "id", entityColumn = "planExerciseId")
    val sets: List<PlanSetEntity>,
)

data class WorkoutExerciseFull(
    @Embedded val item: WorkoutExerciseEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity?,
    @Relation(parentColumn = "id", entityColumn = "workoutExerciseId")
    val sets: List<WorkoutSetEntity>,
)

data class PlanSummary(
    val id: Long,
    val name: String,
    val exerciseCount: Int,
    val lastPerformed: Long?,
)

data class WorkoutSummary(
    val id: Long,
    val name: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val setCount: Int,
    val volumeKg: Double,
    val prCount: Int,
    val exerciseCount: Int,
)

/** One completed set of an exercise with the date of its workout. */
data class ExerciseSetRow(
    val workoutId: Long,
    val workoutName: String,
    val startedAt: Long,
    val setId: Long,
    val position: Int,
    val reps: Int,
    val weightKg: Double,
    val isPr: Boolean,
)

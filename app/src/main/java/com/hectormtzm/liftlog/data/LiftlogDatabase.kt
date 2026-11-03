package com.hectormtzm.liftlog.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ExerciseEntity::class,
        PlanEntity::class,
        PlanExerciseEntity::class,
        PlanSetEntity::class,
        WorkoutEntity::class,
        WorkoutExerciseEntity::class,
        WorkoutSetEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class LiftlogDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun planDao(): PlanDao
    abstract fun workoutDao(): WorkoutDao

    companion object {
        fun create(context: Context): LiftlogDatabase =
            Room.databaseBuilder(context, LiftlogDatabase::class.java, "liftlog.db").build()
    }
}

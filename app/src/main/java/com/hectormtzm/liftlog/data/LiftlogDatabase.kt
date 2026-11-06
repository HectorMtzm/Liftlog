package com.hectormtzm.liftlog.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.hectormtzm.liftlog.data.nutrition.FoodEntity
import com.hectormtzm.liftlog.data.nutrition.MealEntity
import com.hectormtzm.liftlog.data.nutrition.MealItemEntity
import com.hectormtzm.liftlog.data.nutrition.NutritionDao

@Database(
    entities = [
        ExerciseEntity::class,
        PlanEntity::class,
        PlanExerciseEntity::class,
        PlanSetEntity::class,
        WorkoutEntity::class,
        WorkoutExerciseEntity::class,
        WorkoutSetEntity::class,
        FoodEntity::class,
        MealEntity::class,
        MealItemEntity::class,
    ],
    version = 4,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4)],
)
abstract class LiftlogDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun planDao(): PlanDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun nutritionDao(): NutritionDao

    companion object {
        fun create(context: Context): LiftlogDatabase =
            Room.databaseBuilder(context, LiftlogDatabase::class.java, "liftlog.db").build()
    }
}

package com.hectormtzm.liftlog.data.nutrition

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NutritionDao {
    // ---- Foods
    @Query("SELECT COUNT(*) FROM foods WHERE source = 'usda'")
    suspend fun usdaCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFoods(items: List<FoodEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFood(item: FoodEntity)

    @Query("SELECT * FROM foods")
    suspend fun allFoods(): List<FoodEntity>

    @Query("SELECT * FROM foods WHERE id = :id")
    suspend fun getFood(id: String): FoodEntity?

    @Query("SELECT * FROM foods WHERE barcode = :barcode LIMIT 1")
    suspend fun foodByBarcode(barcode: String): FoodEntity?

    @Query("SELECT * FROM foods WHERE lastUsedAt IS NOT NULL ORDER BY lastUsedAt DESC LIMIT :limit")
    fun observeRecentFoods(limit: Int): Flow<List<FoodEntity>>

    @Query("UPDATE foods SET lastUsedAt = :time WHERE id = :id")
    suspend fun touchFood(id: String, time: Long)

    // ---- Meals
    @Transaction
    @Query("SELECT * FROM meals WHERE epochDay = :epochDay ORDER BY createdAt")
    fun observeDay(epochDay: Long): Flow<List<MealFull>>

    @Transaction
    @Query("SELECT * FROM meals WHERE id = :id")
    fun observeMeal(id: Long): Flow<MealFull?>

    @Transaction
    @Query("SELECT * FROM meals WHERE id = :id")
    suspend fun getMeal(id: Long): MealFull?

    @Query("SELECT * FROM meals WHERE epochDay = :epochDay AND mealType = :mealType AND source = 'manual' ORDER BY createdAt LIMIT 1")
    suspend fun manualMeal(epochDay: Long, mealType: String): MealEntity?

    @Insert
    suspend fun insertMeal(meal: MealEntity): Long

    @Update
    suspend fun updateMeal(meal: MealEntity)

    @Query("DELETE FROM meals WHERE id = :id")
    suspend fun deleteMeal(id: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM meal_items WHERE mealId = :mealId")
    suspend fun maxItemPosition(mealId: Long): Int

    @Insert
    suspend fun insertItem(item: MealItemEntity): Long

    @Update
    suspend fun updateItem(item: MealItemEntity)

    @Query("SELECT * FROM meal_items WHERE id = :id")
    suspend fun getItem(id: Long): MealItemEntity?

    @Query("DELETE FROM meal_items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("DELETE FROM meal_items WHERE mealId = :mealId")
    suspend fun deleteItems(mealId: Long)

    /** Daily calorie totals for a range of days (for the week strip). */
    @Query(
        """
        SELECT m.epochDay AS epochDay,
            COALESCE(SUM(i.quantity * i.gramsPerUnit * i.kcal100 / 100.0), 0) AS kcal
        FROM meals m LEFT JOIN meal_items i ON i.mealId = m.id
        WHERE m.epochDay BETWEEN :from AND :to
        GROUP BY m.epochDay
        """
    )
    fun observeDailyCalories(from: Long, to: Long): Flow<List<DayCalories>>
}

data class DayCalories(val epochDay: Long, val kcal: Double)

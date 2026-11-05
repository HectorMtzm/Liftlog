package com.hectormtzm.liftlog.data.nutrition

import android.content.Context
import androidx.room.withTransaction
import com.hectormtzm.liftlog.data.LiftlogDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.UUID

class NutritionRepository(
    private val context: Context,
    private val db: LiftlogDatabase,
    val photos: MealPhotos,
    val claude: ClaudeNutrition,
) {
    private val dao = db.nutritionDao()
    private val cacheLock = Mutex()
    @Volatile private var foodCache: List<FoodEntity>? = null

    fun observeDay(epochDay: Long) = dao.observeDay(epochDay)
    fun observeMeal(id: Long) = dao.observeMeal(id)
    fun observeRecentFoods(limit: Int = 25) = dao.observeRecentFoods(limit)
    fun observeDailyCalories(from: Long, to: Long) = dao.observeDailyCalories(from, to)

    // ------------------------------------------------------------------ catalog

    /** Loads the bundled USDA foods (assets/foods.json) into the database on first run. */
    suspend fun seedIfNeeded() = withContext(Dispatchers.IO) {
        if (dao.usdaCount() > 0) return@withContext
        val text = runCatching { context.assets.open(FOODS_ASSET).bufferedReader().use { it.readText() } }.getOrNull()
            ?: return@withContext
        dao.insertFoods(parseBundledFoods(text))
        foodCache = null
    }

    private suspend fun foods(): List<FoodEntity> = cacheLock.withLock {
        foodCache ?: dao.allFoods().also { foodCache = it }
    }

    /** Searches the local catalog (bundled + previously used + custom foods). */
    suspend fun searchLocal(query: String, limit: Int = 60): List<FoodEntity> = withContext(Dispatchers.Default) {
        rankFoods(foods(), query, limit)
    }

    suspend fun searchOnline(query: String): List<FoodEntity> = OpenFoodFacts.search(query)

    /** Barcode: local first, then Open Food Facts. The product is saved to the catalog. */
    suspend fun lookupBarcode(code: String): FoodEntity? {
        dao.foodByBarcode(code)?.let { return it }
        val food = OpenFoodFacts.byBarcode(code) ?: return null
        saveFood(food)
        return food
    }

    suspend fun saveFood(food: FoodEntity) {
        val existing = dao.getFood(food.id)
        dao.upsertFood(food.copy(lastUsedAt = existing?.lastUsedAt ?: food.lastUsedAt))
        foodCache = null
    }

    suspend fun createCustomFood(name: String, servingLabel: String, servingGrams: Double, m: Macros): FoodEntity {
        val g = servingGrams.takeIf { it > 0 } ?: 100.0
        val food = FoodEntity(
            id = "custom_" + UUID.randomUUID(),
            name = name.trim(),
            source = Source.CUSTOM,
            kcal100 = m.kcal * 100 / g, protein100 = m.protein * 100 / g,
            carbs100 = m.carbs * 100 / g, fat100 = m.fat * 100 / g,
            portions = FoodEntity.encodePortions(listOf(Portion(servingLabel.ifBlank { "1 serving" }, g))),
        )
        saveFood(food)
        return food
    }

    // ------------------------------------------------------------------ meals

    /** Adds a catalog food to the day's meal of [mealType] (or to [targetMealId]). Returns the meal id. */
    suspend fun addFood(
        epochDay: Long, mealType: MealType, targetMealId: Long?,
        food: FoodEntity, quantity: Double, unit: String, gramsPerUnit: Double,
    ): Long = db.withTransaction {
        if (dao.getFood(food.id) == null) dao.upsertFood(food)
        dao.touchFood(food.id, System.currentTimeMillis())
        val mealId = targetMealId ?: (dao.manualMeal(epochDay, mealType.name)?.id
            ?: dao.insertMeal(MealEntity(epochDay = epochDay, mealType = mealType.name, source = Source.MANUAL)))
        dao.insertItem(
            MealItemEntity(
                mealId = mealId, position = dao.maxItemPosition(mealId) + 1,
                name = food.displayName(), quantity = quantity, unit = unit, gramsPerUnit = gramsPerUnit,
                kcal100 = food.kcal100, protein100 = food.protein100, carbs100 = food.carbs100, fat100 = food.fat100,
                foodId = food.id,
            )
        )
        mealId
    }.also { foodCache = null }

    /** Creates a meal from a Claude estimate, or appends the items to [targetMealId]. */
    suspend fun saveEstimate(
        epochDay: Long, mealType: MealType, targetMealId: Long?,
        estimate: MealEstimate, source: String, photoPath: String?,
    ): Long = db.withTransaction {
        val mealId = targetMealId ?: dao.insertMeal(
            MealEntity(
                epochDay = epochDay, mealType = mealType.name,
                name = estimate.mealName, source = source, photoPath = photoPath, notes = estimate.notes,
            )
        )
        var pos = dao.maxItemPosition(mealId)
        estimate.items.forEach { e -> dao.insertItem(e.toItem(mealId, ++pos)) }
        mealId
    }

    /** Replaces a meal's items with a fresh estimate (after the user corrected them). */
    suspend fun replaceItems(mealId: Long, estimate: MealEstimate) = db.withTransaction {
        dao.deleteItems(mealId)
        estimate.items.forEachIndexed { i, e -> dao.insertItem(e.toItem(mealId, i)) }
        dao.getMeal(mealId)?.meal?.let { m ->
            if (estimate.notes.isNotBlank()) dao.updateMeal(m.copy(notes = estimate.notes))
        }
    }

    /** Past meals for re-adding, newest first, without repeats of the same foods and amounts. */
    suspend fun pastMeals(limit: Int = 300): List<MealFull> =
        dao.recentMeals(limit)
            .filter { it.items.isNotEmpty() }
            .distinctBy { m -> m.title.lowercase() to m.sortedItems.map { it.name.lowercase() to it.quantity } }

    /** Copies a past meal's items into a new meal (or into [targetMealId]). Returns the meal id. */
    suspend fun copyMeal(sourceMealId: Long, epochDay: Long, mealType: MealType, targetMealId: Long?): Long = db.withTransaction {
        val src = dao.getMeal(sourceMealId) ?: error("meal not found")
        val mealId = targetMealId ?: dao.insertMeal(
            MealEntity(epochDay = epochDay, mealType = mealType.name, name = src.meal.name, source = Source.COPY, notes = src.meal.notes)
        )
        var pos = dao.maxItemPosition(mealId)
        src.sortedItems.forEach { dao.insertItem(it.copy(id = 0, mealId = mealId, position = ++pos)) }
        src.items.mapNotNull { it.foodId }.distinct().forEach { dao.touchFood(it, System.currentTimeMillis()) }
        mealId
    }

    suspend fun updateItem(item: MealItemEntity) = dao.updateItem(item)
    suspend fun deleteItem(id: Long) = dao.deleteItem(id)
    suspend fun getMeal(id: Long) = dao.getMeal(id)

    suspend fun updateMeal(meal: MealEntity) = dao.updateMeal(meal)

    suspend fun deleteMeal(id: Long) {
        val m = dao.getMeal(id)?.meal ?: return
        dao.deleteMeal(id)
        photos.delete(m.photoPath)
    }

    /** Portions offered for an item: the food's servings, the current unit and grams. */
    suspend fun portionsFor(item: MealItemEntity): List<Portion> {
        val fromFood = item.foodId?.let { dao.getFood(it) }?.portionList.orEmpty()
        return (listOf(Portion(item.unit, item.gramsPerUnit)) + fromFood + Portion("g", 1.0))
            .filter { it.grams > 0 }
            .distinctBy { it.label }
    }

    companion object {
        const val FOODS_ASSET = "foods.json"

        /**
         * Bundled format: JSON array of [id, name, kcal, protein, carbs, fat, portions]
         * with nutrients per 100 g and portions encoded like [FoodEntity.portions].
         */
        internal fun parseBundledFoods(text: String): List<FoodEntity> {
            val arr = JSONArray(text)
            return (0 until arr.length()).map { i ->
                val a = arr.getJSONArray(i)
                FoodEntity(
                    id = "usda_" + a.getString(0),
                    name = a.getString(1),
                    source = Source.USDA,
                    kcal100 = a.getDouble(2), protein100 = a.getDouble(3),
                    carbs100 = a.getDouble(4), fat100 = a.getDouble(5),
                    portions = a.optString(6, ""),
                )
            }
        }

        private fun stem(w: String) = when {
            w.length > 4 && w.endsWith("ies") -> w.dropLast(3) + "y"
            w.length > 4 && w.endsWith("es") && !w.endsWith("ses") -> w.dropLast(2)
            w.length > 3 && w.endsWith("s") && !w.endsWith("ss") -> w.dropLast(1)
            else -> w
        }

        /** All query words must appear; recently used, then names starting with the words, then shorter names. */
        internal fun rankFoods(all: List<FoodEntity>, query: String, limit: Int): List<FoodEntity> {
            val tokens = query.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }.map(::stem)
            if (tokens.isEmpty()) return emptyList()
            val first = tokens.first()
            return all.asSequence()
                .mapNotNull { f ->
                    val hay = (f.name + " " + f.brand).lowercase()
                    if (!tokens.all { hay.contains(it) }) return@mapNotNull null
                    val name = f.name.lowercase()
                    val words = name.split(Regex("[^a-z0-9]+"))
                    var score = 0.0
                    if (f.lastUsedAt != null) score -= 1000
                    if (f.source == Source.CUSTOM) score -= 500
                    if (name.startsWith(first)) score -= 200
                    // Whole-word matches beat prefix matches ("egg" -> "Egg, whole" before "Eggnog").
                    tokens.forEach { t ->
                        score -= when {
                            words.any { it == t || stem(it) == t } -> 80.0
                            words.any { it.startsWith(t) } -> 30.0
                            else -> 0.0
                        }
                    }
                    score += name.length + name.count { it == ',' } * 4
                    f to score
                }
                .sortedBy { it.second }
                .take(limit)
                .map { it.first }
                .toList()
        }
    }
}

fun FoodEntity.displayName(): String = if (brand.isNotBlank()) "$name ($brand)" else name

internal fun EstimatedItem.toItem(mealId: Long, position: Int): MealItemEntity {
    val gpu = if (gramsPerUnit > 0) gramsPerUnit else 100.0
    val grams = quantity * gpu
    val f = if (grams > 0) 100.0 / grams else 0.0
    return MealItemEntity(
        mealId = mealId, position = position, name = name, quantity = quantity, unit = unit, gramsPerUnit = gpu,
        kcal100 = kcal * f, protein100 = protein * f, carbs100 = carbs * f, fat100 = fat * f,
    )
}

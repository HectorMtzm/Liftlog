package com.hectormtzm.liftlog.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.UUID

class ExerciseRepository(
    private val context: Context,
    private val db: LiftlogDatabase,
) {
    private val dao = db.exerciseDao()

    fun observeAll() = dao.observeAll()
    fun observe(id: String) = dao.observe(id)
    suspend fun get(id: String) = dao.get(id)

    /** Loads the bundled free-exercise-db dataset into the database on first run. */
    suspend fun seedIfNeeded() = withContext(Dispatchers.IO) {
        if (dao.builtInCount() > 0) return@withContext
        val text = context.assets.open("exercises.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(text)
        val items = ArrayList<ExerciseEntity>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            fun str(key: String) = if (o.isNull(key)) "" else o.optString(key, "")
            fun list(key: String): List<String> {
                val a = o.optJSONArray(key) ?: return emptyList()
                return (0 until a.length()).map { a.getString(it) }
            }
            items += ExerciseEntity(
                id = str("id"),
                name = str("name"),
                category = str("category"),
                equipment = str("equipment"),
                level = str("level"),
                force = str("force"),
                mechanic = str("mechanic"),
                primaryMuscles = list("primaryMuscles").joinToString(","),
                secondaryMuscles = list("secondaryMuscles").joinToString(","),
                instructions = list("instructions").joinToString("\n"),
                images = list("images").joinToString("|"),
                isCustom = false,
            )
        }
        dao.insertAll(items.filter { it.id.isNotBlank() && it.name.isNotBlank() })
    }

    suspend fun saveCustom(
        id: String?,
        name: String,
        primaryMuscle: String,
        equipment: String,
        category: String,
        instructions: String,
    ): String {
        val newId = id ?: "custom_" + UUID.randomUUID().toString()
        dao.upsert(
            ExerciseEntity(
                id = newId,
                name = name.trim(),
                primaryMuscles = primaryMuscle,
                equipment = equipment,
                category = category,
                instructions = instructions.trim(),
                isCustom = true,
            )
        )
        return newId
    }

    suspend fun deleteCustom(id: String) = db.withTransaction {
        db.planDao().deleteByExercise(id)
        dao.deleteCustom(id)
    }

    companion object {
        const val IMAGE_BASE_URL = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/"

        val MUSCLES = listOf(
            "abdominals", "abductors", "adductors", "biceps", "calves", "chest", "forearms",
            "glutes", "hamstrings", "lats", "lower back", "middle back", "neck", "quadriceps",
            "shoulders", "traps", "triceps",
        )
        val EQUIPMENT = listOf(
            "barbell", "dumbbell", "cable", "machine", "body only", "kettlebells", "bands",
            "e-z curl bar", "medicine ball", "exercise ball", "foam roll", "other",
        )
        val CATEGORIES = listOf(
            "strength", "stretching", "plyometrics", "powerlifting", "olympic weightlifting",
            "strongman", "cardio",
        )
    }
}

fun ExerciseEntity.imageUrl(index: Int = 0): String? =
    imageList.getOrNull(index)?.let { ExerciseRepository.IMAGE_BASE_URL + it }

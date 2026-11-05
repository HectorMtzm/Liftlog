package com.hectormtzm.liftlog.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.UUID

class ExerciseRepository(
    private val context: Context,
    private val db: LiftlogDatabase,
) {
    private val dao = db.exerciseDao()
    private val seedLock = Mutex()

    fun observeAll() = dao.observeAll()
    fun observe(id: String) = dao.observe(id)
    suspend fun get(id: String) = dao.get(id)

    /**
     * Loads the bundled exercise library (exercises-dataset) on first run. Installs that still
     * have the earlier free-exercise-db library are moved over: exercises with the same name are
     * re-pointed to the new entry, legacy ones still used by plans or history are kept (with their
     * photos), and the rest are removed.
     */
    suspend fun seedIfNeeded() = withContext(Dispatchers.IO) {
        seedLock.withLock {
            if (dao.datasetCount() > 0) return@withLock
            val items = parseBundled(context.assets.open("exercises.json").bufferedReader().use { it.readText() })
            db.withTransaction {
                dao.insertAll(items)
                migrateLegacy()
            }
        }
    }

    /** Re-points legacy free-exercise-db references to same-named dataset exercises. */
    internal suspend fun migrateLegacy() {
        val legacy = dao.legacyBuiltIns()
        if (legacy.isEmpty()) return
        val byName = dao.datasetExercises().associateBy { normalize(it.name) }
        legacy.forEach { old ->
            val match = byName[normalize(old.name)] ?: byName[normalize(old.name.substringBefore(" - "))]
            if (match != null) {
                dao.repointPlans(old.id, match.id)
                dao.repointWorkouts(old.id, match.id)
            }
            if (dao.referenceCount(old.id) == 0) dao.delete(old.id)
        }
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
        /** Media for the current library (exercises-dataset). (c) Gym visual, loaded at runtime. */
        const val DATASET_MEDIA_BASE_URL = "https://raw.githubusercontent.com/hasaneyldrm/exercises-dataset/HEAD/"
        /** Photos for legacy free-exercise-db entries still used by older plans. */
        const val LEGACY_IMAGE_BASE_URL = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/"
        const val MEDIA_ATTRIBUTION = "Exercise animations \u00A9 Gym visual \u2014 https://gymvisual.com/"

        /** Target muscles, as used by the dataset. */
        val MUSCLES = listOf(
            "abs", "abductors", "adductors", "biceps", "calves", "cardiovascular system", "delts",
            "forearms", "glutes", "hamstrings", "lats", "levator scapulae", "pectorals", "quads",
            "serratus anterior", "spine", "traps", "triceps", "upper back",
        )
        val EQUIPMENT = listOf(
            "body weight", "dumbbell", "barbell", "cable", "leverage machine", "smith machine",
            "kettlebell", "band", "resistance band", "ez barbell", "olympic barbell", "trap bar",
            "weighted", "assisted", "sled machine", "medicine ball", "stability ball", "bosu ball",
            "rope", "roller", "wheel roller", "hammer", "tire", "stationary bike", "elliptical machine",
            "stepmill machine", "skierg machine", "upper body ergometer",
        )
        /** Body parts (stored in ExerciseEntity.category). */
        val CATEGORIES = listOf(
            "chest", "back", "shoulders", "upper arms", "lower arms", "upper legs", "lower legs",
            "waist", "neck", "cardio",
        )

        internal fun normalize(name: String) = name.lowercase().filter { it.isLetterOrDigit() }

        /** Parses the bundled format written by tools/build_exercises.py. */
        internal fun parseBundled(text: String): List<ExerciseEntity> {
            val arr = JSONArray(text)
            return (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                fun list(key: String): List<String> {
                    val a = o.optJSONArray(key) ?: return emptyList()
                    return (0 until a.length()).map { a.getString(it) }
                }
                val id = o.optString("id"); val name = o.optString("name")
                if (id.isBlank() || name.isBlank()) return@mapNotNull null
                ExerciseEntity(
                    id = id,
                    name = name,
                    category = o.optString("bodyPart"),
                    equipment = o.optString("equipment"),
                    primaryMuscles = o.optString("target"),
                    secondaryMuscles = list("secondary").joinToString(","),
                    instructions = list("steps").joinToString("\n"),
                    images = listOf(o.optString("image"), o.optString("gif")).joinToString("|"),
                    isCustom = false,
                )
            }
        }
    }
}

val ExerciseEntity.isFromDataset: Boolean get() = id.startsWith("edb_")

/** Still image for lists: the dataset thumbnail, or a legacy photo. */
fun ExerciseEntity.imageUrl(index: Int = 0): String? =
    imageList.getOrNull(index)?.takeIf { it.isNotBlank() }?.let {
        (if (isFromDataset) ExerciseRepository.DATASET_MEDIA_BASE_URL else ExerciseRepository.LEGACY_IMAGE_BASE_URL) + it
    }

/** Animated demo (dataset exercises only). */
fun ExerciseEntity.gifUrl(): String? =
    if (isFromDataset) imageList.getOrNull(1)?.let { ExerciseRepository.DATASET_MEDIA_BASE_URL + it } else null

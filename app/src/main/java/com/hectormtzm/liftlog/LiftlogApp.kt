package com.hectormtzm.liftlog

import android.app.Application
import com.hectormtzm.liftlog.data.ExerciseRepository
import com.hectormtzm.liftlog.data.LiftlogDatabase
import com.hectormtzm.liftlog.data.PlanRepository
import com.hectormtzm.liftlog.data.SettingsRepository
import com.hectormtzm.liftlog.data.WorkoutRepository
import com.hectormtzm.liftlog.data.nutrition.ApiKeyStore
import com.hectormtzm.liftlog.data.nutrition.ClaudeNutrition
import com.hectormtzm.liftlog.data.nutrition.MealPhotos
import com.hectormtzm.liftlog.data.nutrition.NutritionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(val app: Application) {
    val db = LiftlogDatabase.create(app)
    val settings = SettingsRepository(app)
    val exercises = ExerciseRepository(app, db)
    val plans = PlanRepository(db, settings)
    val workouts = WorkoutRepository(db, settings)
    val apiKey = ApiKeyStore(app)
    val nutrition = NutritionRepository(app, db, MealPhotos(app), ClaudeNutrition({ apiKey.key.value }))
}

class LiftlogApp : Application() {
    lateinit var container: AppContainer
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch { container.exercises.seedIfNeeded() }
        appScope.launch { container.nutrition.seedIfNeeded() }
    }
}

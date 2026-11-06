package com.hectormtzm.liftlog.ui.workout

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hectormtzm.liftlog.AppContainer
import com.hectormtzm.liftlog.data.Settings
import com.hectormtzm.liftlog.data.WorkoutEntity
import com.hectormtzm.liftlog.data.WorkoutExerciseFull
import com.hectormtzm.liftlog.data.WorkoutSetEntity
import com.hectormtzm.liftlog.timer.RestTimer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WorkoutUi(
    val workout: WorkoutEntity,
    val exercises: List<WorkoutExerciseFull>,
    /** Previous completed sets per exerciseId, from the last finished workout. */
    val previous: Map<String, List<WorkoutSetEntity>>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutViewModel(private val c: AppContainer) : ViewModel() {
    private val app: Application get() = c.app
    private val previousCache = mutableMapOf<String, List<WorkoutSetEntity>>()

    /** null = loading, Loaded(null) = no active workout. */
    val ui: StateFlow<Loaded<WorkoutUi?>?> = c.workouts.observeActive()
        .map { it?.id }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(Loaded<WorkoutUi?>(null)) else observeWorkout(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private fun observeWorkout(id: Long): Flow<Loaded<WorkoutUi?>> =
        combine(c.workouts.observeWorkout(id), c.workouts.observeExercises(id)) { w, ex ->
            if (w == null || w.finishedAt != null) {
                Loaded<WorkoutUi?>(null)
            } else {
                ex.map { it.item.exerciseId }.distinct().forEach { exId ->
                    if (!previousCache.containsKey(exId)) previousCache[exId] = c.workouts.previousSets(exId, id)
                }
                Loaded<WorkoutUi?>(WorkoutUi(w, ex, previousCache.toMap()))
            }
        }

    val settings: StateFlow<Settings> =
        c.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Settings())

    fun toggleSet(set: WorkoutSetEntity) = viewModelScope.launch {
        val exercises = ui.value?.value?.exercises.orEmpty()
        val result = c.workouts.toggleSet(set.id)
        if (result != null && result.restSeconds > 0) {
            // Label the timer with what comes next, not the exercise just finished.
            RestTimer.start(app, result.restSeconds, nextUpName(exercises, set.id).orEmpty())
        }
    }

    fun updateSet(set: WorkoutSetEntity, reps: Int, weightKg: Double) =
        viewModelScope.launch { c.workouts.updateSet(set.id, reps, weightKg) }

    fun addSet(workoutExerciseId: Long) = viewModelScope.launch { c.workouts.addSet(workoutExerciseId) }
    fun removeSet(setId: Long) = viewModelScope.launch { c.workouts.removeSet(setId) }
    fun setRest(workoutExerciseId: Long, seconds: Int) = viewModelScope.launch { c.workouts.setRest(workoutExerciseId, seconds) }
    fun removeExercise(id: Long) = viewModelScope.launch { c.workouts.removeExercise(id) }
    fun move(workoutId: Long, id: Long, delta: Int) = viewModelScope.launch { c.workouts.moveExercise(workoutId, id, delta) }

    fun rename(workout: WorkoutEntity, name: String) = viewModelScope.launch {
        c.db.workoutDao().updateWorkout(workout.copy(name = name.trim()))
    }

    fun finish(workoutId: Long, onDone: (saved: Boolean) -> Unit) = viewModelScope.launch {
        RestTimer.stop(app)
        onDone(c.workouts.finish(workoutId))
    }

    fun discard(workoutId: Long, onDone: () -> Unit) = viewModelScope.launch {
        RestTimer.stop(app)
        c.workouts.discard(workoutId)
        onDone()
    }
}

data class Loaded<T>(val value: T)

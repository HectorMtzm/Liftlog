package com.hectormtzm.liftlog.ui.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hectormtzm.liftlog.AppContainer
import com.hectormtzm.liftlog.data.PlanEntity
import com.hectormtzm.liftlog.data.PlanExerciseFull
import com.hectormtzm.liftlog.data.PlanSetEntity
import com.hectormtzm.liftlog.data.PlanSummary
import com.hectormtzm.liftlog.data.Settings
import com.hectormtzm.liftlog.data.WorkoutEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlansViewModel(private val c: AppContainer) : ViewModel() {
    val plans: StateFlow<List<PlanSummary>?> =
        c.plans.observeSummaries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val active: StateFlow<WorkoutEntity?> =
        c.workouts.observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun create(name: String, onCreated: (Long) -> Unit) = viewModelScope.launch {
        onCreated(c.plans.createPlan(name))
    }

    fun start(planId: Long, onStarted: () -> Unit) = viewModelScope.launch {
        c.workouts.startFromPlan(planId)
        onStarted()
    }

    fun startEmpty(onStarted: () -> Unit) = viewModelScope.launch {
        c.workouts.startEmpty()
        onStarted()
    }
}

class PlanEditViewModel(private val c: AppContainer, val planId: Long) : ViewModel() {
    val plan: StateFlow<PlanEntity?> =
        c.plans.observePlan(planId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val exercises: StateFlow<List<PlanExerciseFull>?> =
        c.plans.observeExercises(planId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val settings: StateFlow<Settings> =
        c.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Settings())
    val active: StateFlow<WorkoutEntity?> =
        c.workouts.observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun rename(name: String) = viewModelScope.launch { c.plans.renamePlan(planId, name) }
    fun delete(onDone: () -> Unit) = viewModelScope.launch { c.plans.deletePlan(planId); onDone() }
    fun duplicate(onDone: (Long) -> Unit) = viewModelScope.launch { onDone(c.plans.duplicatePlan(planId)) }
    fun removeExercise(id: Long) = viewModelScope.launch { c.plans.removeExercise(id) }
    fun move(id: Long, delta: Int) = viewModelScope.launch { c.plans.moveExercise(planId, id, delta) }
    fun setRest(id: Long, seconds: Int) = viewModelScope.launch { c.plans.setRest(id, seconds) }
    fun updateSet(set: PlanSetEntity) = viewModelScope.launch { c.plans.updateSet(set) }
    fun addSet(planExerciseId: Long) = viewModelScope.launch { c.plans.addSet(planExerciseId) }
    fun removeSet(setId: Long) = viewModelScope.launch { c.plans.removeSet(setId) }
    fun applyAll(planExerciseId: Long, count: Int, reps: Int, weightKg: Double) =
        viewModelScope.launch { c.plans.applyToAllSets(planExerciseId, count, reps, weightKg) }

    fun start(onStarted: () -> Unit) = viewModelScope.launch {
        c.workouts.startFromPlan(planId)
        onStarted()
    }
}

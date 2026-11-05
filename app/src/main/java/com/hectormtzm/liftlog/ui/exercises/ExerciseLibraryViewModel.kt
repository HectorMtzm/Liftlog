package com.hectormtzm.liftlog.ui.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hectormtzm.liftlog.AppContainer
import com.hectormtzm.liftlog.data.ExerciseEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PickMode { BROWSE, PLAN, WORKOUT, REPLACE, PLAN_REPLACE }

data class LibraryFilter(
    val query: String = "",
    val muscle: String? = null,
    val equipment: String? = null,
    val customOnly: Boolean = false,
)

class ExerciseLibraryViewModel(private val c: AppContainer, val mode: PickMode, val targetId: Long) : ViewModel() {
    private val _filter = MutableStateFlow(LibraryFilter())
    val filter: StateFlow<LibraryFilter> = _filter.asStateFlow()

    private val _selected = MutableStateFlow<List<String>>(emptyList())
    val selected: StateFlow<List<String>> = _selected.asStateFlow()

    val results: StateFlow<List<ExerciseEntity>?> = combine(c.exercises.observeAll(), _filter) { all, f ->
        filter(all, f)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setQuery(q: String) = _filter.value.let { _filter.value = it.copy(query = q) }
    fun setMuscle(m: String?) = _filter.value.let { _filter.value = it.copy(muscle = m) }
    fun setEquipment(e: String?) = _filter.value.let { _filter.value = it.copy(equipment = e) }
    fun setCustomOnly(v: Boolean) = _filter.value.let { _filter.value = it.copy(customOnly = v) }

    fun toggle(id: String) {
        val cur = _selected.value
        _selected.value = if (id in cur) cur - id else cur + id
    }

    /** Applies the selection to the target plan / workout. */
    fun confirm(ids: List<String>, onDone: () -> Unit) = viewModelScope.launch {
        when (mode) {
            PickMode.PLAN -> c.plans.addExercises(targetId, ids)
            PickMode.WORKOUT -> c.workouts.addExercises(targetId, ids)
            PickMode.REPLACE -> ids.firstOrNull()?.let { c.workouts.replaceExercise(targetId, it) }
            PickMode.PLAN_REPLACE -> ids.firstOrNull()?.let { c.plans.replaceExercise(targetId, it) }
            PickMode.BROWSE -> Unit
        }
        onDone()
    }

    fun saveCustom(
        id: String?, name: String, muscle: String, equipment: String, category: String, instructions: String,
        onSaved: (String) -> Unit,
    ) = viewModelScope.launch {
        onSaved(c.exercises.saveCustom(id, name, muscle, equipment, category, instructions))
    }

    companion object {
        fun filter(all: List<ExerciseEntity>, f: LibraryFilter): List<ExerciseEntity> {
            val tokens = f.query.lowercase().split(' ', '-').filter { it.isNotBlank() }
            val matched = all.filter { e ->
                (!f.customOnly || e.isCustom) &&
                    (f.muscle == null || f.muscle in e.primaryMuscleList) &&
                    (f.equipment == null || e.equipment == f.equipment) &&
                    (tokens.isEmpty() || run {
                        val hay = (e.name + " " + e.equipment + " " + e.primaryMuscles).lowercase()
                        tokens.all { hay.contains(it) }
                    })
            }
            if (tokens.isEmpty()) return matched
            val q = f.query.trim().lowercase()
            // Names that start with the query first, then names containing all words, then the rest.
            return matched.sortedWith(
                compareBy<ExerciseEntity> {
                    val n = it.name.lowercase()
                    when {
                        n.startsWith(q) -> 0
                        tokens.all { t -> n.contains(t) } -> 1
                        else -> 2
                    }
                }.thenBy { it.name.length }
            )
        }
    }
}

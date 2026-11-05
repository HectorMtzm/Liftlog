package com.hectormtzm.liftlog.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class Settings(
    val unit: WeightUnit = WeightUnit.LB,
    val defaultRestSeconds: Int = 90,
    val updatePlanWeights: Boolean = true,
    val goals: NutritionGoals = NutritionGoals(),
    val claudeModel: ClaudeModel = ClaudeModel.OPUS,
)

/** Claude models offered for food estimates. */
enum class ClaudeModel(val id: String, val label: String, val note: String) {
    OPUS("claude-opus-5-5", "Opus 5.5", "Most accurate, about 1-2 cents per photo"),
    SONNET("claude-sonnet-5-5", "Sonnet 5.5", "Fast and accurate, about half the cost"),
    HAIKU("claude-haiku-5-5", "Haiku 5.5", "Fastest and cheapest, a fraction of a cent");

    companion object {
        fun parse(s: String?) = entries.firstOrNull { it.name == s } ?: OPUS
    }
}

data class NutritionGoals(
    val kcal: Int = 2200,
    val protein: Int = 160,
    val carbs: Int = 220,
    val fat: Int = 70,
)

class SettingsRepository(private val context: Context) {
    private val unitKey = stringPreferencesKey("unit")
    private val restKey = intPreferencesKey("default_rest")
    private val updatePlanKey = booleanPreferencesKey("update_plan_weights")
    private val kcalKey = intPreferencesKey("goal_kcal")
    private val proteinKey = intPreferencesKey("goal_protein")
    private val carbsKey = intPreferencesKey("goal_carbs")
    private val fatKey = intPreferencesKey("goal_fat")
    private val modelKey = stringPreferencesKey("claude_model")

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            unit = p[unitKey]?.let { runCatching { WeightUnit.valueOf(it) }.getOrNull() } ?: WeightUnit.LB,
            defaultRestSeconds = p[restKey] ?: 90,
            updatePlanWeights = p[updatePlanKey] ?: true,
            goals = NutritionGoals(
                kcal = p[kcalKey] ?: 2200,
                protein = p[proteinKey] ?: 160,
                carbs = p[carbsKey] ?: 220,
                fat = p[fatKey] ?: 70,
            ),
            claudeModel = ClaudeModel.parse(p[modelKey]),
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setUnit(unit: WeightUnit) = context.dataStore.edit { it[unitKey] = unit.name }
    suspend fun setDefaultRest(seconds: Int) = context.dataStore.edit { it[restKey] = seconds }
    suspend fun setUpdatePlanWeights(v: Boolean) = context.dataStore.edit { it[updatePlanKey] = v }
    suspend fun setClaudeModel(m: ClaudeModel) = context.dataStore.edit { it[modelKey] = m.name }
    suspend fun setGoals(g: NutritionGoals) = context.dataStore.edit {
        it[kcalKey] = g.kcal; it[proteinKey] = g.protein; it[carbsKey] = g.carbs; it[fatKey] = g.fat
    }
}

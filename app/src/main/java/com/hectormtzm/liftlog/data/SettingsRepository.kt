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
)

class SettingsRepository(private val context: Context) {
    private val unitKey = stringPreferencesKey("unit")
    private val restKey = intPreferencesKey("default_rest")
    private val updatePlanKey = booleanPreferencesKey("update_plan_weights")

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            unit = p[unitKey]?.let { runCatching { WeightUnit.valueOf(it) }.getOrNull() } ?: WeightUnit.LB,
            defaultRestSeconds = p[restKey] ?: 90,
            updatePlanWeights = p[updatePlanKey] ?: true,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setUnit(unit: WeightUnit) = context.dataStore.edit { it[unitKey] = unit.name }
    suspend fun setDefaultRest(seconds: Int) = context.dataStore.edit { it[restKey] = seconds }
    suspend fun setUpdatePlanWeights(v: Boolean) = context.dataStore.edit { it[updatePlanKey] = v }
}

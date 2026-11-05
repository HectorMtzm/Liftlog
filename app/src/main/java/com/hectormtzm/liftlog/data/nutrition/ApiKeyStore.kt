package com.hectormtzm.liftlog.data.nutrition

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the user's Anthropic API key. Kept in its own preferences file, which is excluded
 * from cloud backups (see res/xml/backup_rules.xml), so it never leaves the device.
 */
class ApiKeyStore(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val _key = MutableStateFlow(prefs.getString(KEY, "").orEmpty())
    val key: StateFlow<String> = _key.asStateFlow()

    fun set(value: String) {
        val v = value.trim()
        prefs.edit().putString(KEY, v).apply()
        _key.value = v
    }

    companion object {
        const val FILE = "secrets"
        private const val KEY = "anthropic_api_key"
    }
}

package com.example.nback

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicBoolean

private val recoveredCorruption = AtomicBoolean(false)
private val Context.levelDataStore by preferencesDataStore(
    name = "session_settings",
    corruptionHandler = ReplaceFileCorruptionHandler {
        recoveredCorruption.set(true)
        emptyPreferences()
    },
)

data class LoadedLevel(val level: Int = 2, val reset: Boolean = false, val modeMask: Int = 1, val typesReset: Boolean = false)
interface LevelSettings {
    suspend fun load(): LoadedLevel
    suspend fun save(level: Int, modeMask: Int = 1)
}

class StoredLevelSettings(
    private val store: DataStore<Preferences>,
    private val consumeCorruption: () -> Boolean = { false },
) : LevelSettings {
    override suspend fun load(): LoadedLevel {
        val preferences = store.data.first()
        val entry = preferences.asMap().entries.find { it.key.name == "selected_n" }
        val value = entry?.value
        val invalid = entry != null && (value !is Int || value !in 1..3)
        val types = preferences.asMap().entries.find { it.key.name == "selected_types" }?.value
        val invalidTypes = types != null && (types !is Int || types !in 1..7)
        val corrupt = consumeCorruption()
        return LoadedLevel(if (!invalid && value is Int) value else 2, corrupt || invalid,
            if (!invalidTypes && types is Int) types else 1, corrupt || invalidTypes)
    }

    override suspend fun save(level: Int, modeMask: Int) {
        require(level in 1..3 && modeMask in 1..7)
        store.edit {
            it[intPreferencesKey("selected_n")] = level
            it[intPreferencesKey("selected_types")] = modeMask
        }
    }

    companion object {
        fun from(context: Context): LevelSettings = StoredLevelSettings(context.applicationContext.levelDataStore) {
            recoveredCorruption.getAndSet(false)
        }
    }
}

enum class SettingsNotice { RESET, TYPES_RESET, BOTH_RESET, LOAD_FAILED, SAVE_FAILED }
data class SettingsState(
    val level: Int = 2,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val notice: SettingsNotice? = null,
    val modeMask: Int = 1,
)

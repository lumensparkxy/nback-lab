package com.maswadkar.nback.ads

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.adQuotaData by preferencesDataStore(name = "ad_quota")

class StoredAdQuota(private val context: Context) : AdQuotaStore {
    private val grace = intPreferencesKey("grace")
    private val completed = intPreferencesKey("completed")
    private val run = stringPreferencesKey("last_run")
    override suspend fun load(): AdQuota = context.adQuotaData.data.first().let {
        AdQuota(it[grace] ?: 0, it[completed] ?: 0, it[run] ?: "").checked()
    }
    override suspend fun save(value: AdQuota) {
        value.checked()
        context.adQuotaData.edit {
            it[grace] = value.grace; it[completed] = value.completed; it[run] = value.lastRun
        }
    }
}

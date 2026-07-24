package com.flipmate.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.flipMateDataStore by preferencesDataStore("flipmate_settings")
class DataStoreManager(private val context: Context) {
    private object Keys { val symbol=stringPreferencesKey("symbol"); val mode=stringPreferencesKey("mode"); val screenshot=booleanPreferencesKey("screenshot") }
    val symbol: Flow<String> = context.flipMateDataStore.data.map { it[Keys.symbol] ?: "BTC_USDT" }
    val mode: Flow<String> = context.flipMateDataStore.data.map { it[Keys.mode] ?: "SIM" }
    val screenshotProtection: Flow<Boolean> = context.flipMateDataStore.data.map { it[Keys.screenshot] ?: false }
    suspend fun setSymbol(value: String) { context.flipMateDataStore.edit { it[Keys.symbol] = value.trim().uppercase() } }
    suspend fun setMode(value: String) { require(value == "SIM" || value == "REAL"); context.flipMateDataStore.edit { it[Keys.mode] = value } }
    suspend fun setScreenshotProtection(value: Boolean) { context.flipMateDataStore.edit { it[Keys.screenshot] = value } }
}

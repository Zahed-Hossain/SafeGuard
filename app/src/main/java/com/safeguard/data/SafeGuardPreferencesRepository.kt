package com.safeguard.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "safeguard_preferences")

class SafeGuardPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val PROTECTION_ENABLED = booleanPreferencesKey("protection_enabled")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val BLOCKED_TODAY = intPreferencesKey("blocked_today")
        val BLOCKED_THIS_WEEK = intPreferencesKey("blocked_this_week")
        val BLOCKED_THIS_MONTH = intPreferencesKey("blocked_this_month")
        val TOTAL_BLOCKED = intPreferencesKey("total_blocked")

        // Categories
        val COUNT_ADULT = intPreferencesKey("count_adult")
        val COUNT_PORNOGRAPHY = intPreferencesKey("count_pornography")
        val COUNT_EXPLICIT = intPreferencesKey("count_explicit")
        val COUNT_NSFW = intPreferencesKey("count_nsfw")
        val COUNT_OTHER = intPreferencesKey("count_other")

        // Protection & Filtering Toggles
        val BLOCK_ADULT = booleanPreferencesKey("block_adult")
        val BLOCK_PORNOGRAPHY = booleanPreferencesKey("block_pornography")
        val BLOCK_EXPLICIT = booleanPreferencesKey("block_explicit")
        val BLOCK_NSFW = booleanPreferencesKey("block_nsfw")
        val BLOCK_ADULT_STREAMING = booleanPreferencesKey("block_adult_streaming")
        val BLOCK_GAMBLING = booleanPreferencesKey("block_gambling")
        val BLOCK_MALWARE = booleanPreferencesKey("block_malware")
        val SAFE_SEARCH_ENABLED = booleanPreferencesKey("safe_search_enabled")
        val AUTO_START_ON_BOOT = booleanPreferencesKey("auto_start_on_boot")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")

        // Privacy Controls
        val SAVE_STATISTICS = booleanPreferencesKey("save_statistics")
        val STORE_BLOCKED_DOMAIN_NAMES = booleanPreferencesKey("store_blocked_domain_names")

        // Updates
        val AUTO_UPDATES_ENABLED = booleanPreferencesKey("auto_updates_enabled")

        val LAST_STATS_RESET_DATE = longPreferencesKey("last_stats_reset_date")
        val PIN_PROTECTION_ENABLED = booleanPreferencesKey("pin_protection_enabled")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val PIN_HASH = stringPreferencesKey("pin_hash")
    }

    val isProtectionEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.PROTECTION_ENABLED] ?: false
    }

    val isOnboardingCompleted: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.ONBOARDING_COMPLETED] ?: false
    }

    val protectionStats: Flow<ProtectionStats> = context.dataStore.data.map { preferences ->
        ProtectionStats(
            blockedToday = preferences[PreferencesKeys.BLOCKED_TODAY] ?: 0,
            blockedThisWeek = preferences[PreferencesKeys.BLOCKED_THIS_WEEK] ?: 0,
            blockedThisMonth = preferences[PreferencesKeys.BLOCKED_THIS_MONTH] ?: 0,
            totalBlocked = preferences[PreferencesKeys.TOTAL_BLOCKED] ?: 0,
            adultCount = preferences[PreferencesKeys.COUNT_ADULT] ?: 0,
            pornographyCount = preferences[PreferencesKeys.COUNT_PORNOGRAPHY] ?: 0,
            explicitCount = preferences[PreferencesKeys.COUNT_EXPLICIT] ?: 0,
            nsfwCount = preferences[PreferencesKeys.COUNT_NSFW] ?: 0,
            otherCount = preferences[PreferencesKeys.COUNT_OTHER] ?: 0
        )
    }

    // Category filtering flags
    val blockAdult: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.BLOCK_ADULT] ?: true
    }

    val blockPornography: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.BLOCK_PORNOGRAPHY] ?: true
    }

    val blockExplicit: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.BLOCK_EXPLICIT] ?: true
    }

    val blockNsfw: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.BLOCK_NSFW] ?: true
    }

    val blockAdultStreaming: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.BLOCK_ADULT_STREAMING] ?: true
    }

    val blockGambling: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.BLOCK_GAMBLING] ?: true
    }

    val blockMalware: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.BLOCK_MALWARE] ?: true
    }

    val safeSearchEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.SAFE_SEARCH_ENABLED] ?: true
    }

    val autoStartOnBoot: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.AUTO_START_ON_BOOT] ?: true
    }

    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] ?: true
    }

    // Privacy settings (OFF by default for store blocked domain names)
    val saveStatistics: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.SAVE_STATISTICS] ?: true
    }

    val storeBlockedDomainNames: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.STORE_BLOCKED_DOMAIN_NAMES] ?: false
    }

    // Updates
    val autoUpdatesEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.AUTO_UPDATES_ENABLED] ?: true
    }

    val isPinProtectionEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.PIN_PROTECTION_ENABLED] ?: false
    }

    val pinSalt: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.PIN_SALT]
    }

    val pinHash: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.PIN_HASH]
    }

    suspend fun setProtectionEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PROTECTION_ENABLED] = enabled
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ONBOARDING_COMPLETED] = completed
        }
    }

    suspend fun incrementBlockedStats(category: String = "Adult") {
        context.dataStore.edit { preferences ->
            val savingStats = preferences[PreferencesKeys.SAVE_STATISTICS] ?: true
            if (!savingStats) return@edit

            val today = preferences[PreferencesKeys.BLOCKED_TODAY] ?: 0
            val week = preferences[PreferencesKeys.BLOCKED_THIS_WEEK] ?: 0
            val month = preferences[PreferencesKeys.BLOCKED_THIS_MONTH] ?: 0
            val total = preferences[PreferencesKeys.TOTAL_BLOCKED] ?: 0

            preferences[PreferencesKeys.BLOCKED_TODAY] = today + 1
            preferences[PreferencesKeys.BLOCKED_THIS_WEEK] = week + 1
            preferences[PreferencesKeys.BLOCKED_THIS_MONTH] = month + 1
            preferences[PreferencesKeys.TOTAL_BLOCKED] = total + 1

            when (category.lowercase()) {
                "adult" -> {
                    val count = preferences[PreferencesKeys.COUNT_ADULT] ?: 0
                    preferences[PreferencesKeys.COUNT_ADULT] = count + 1
                }
                "pornography", "porn" -> {
                    val count = preferences[PreferencesKeys.COUNT_PORNOGRAPHY] ?: 0
                    preferences[PreferencesKeys.COUNT_PORNOGRAPHY] = count + 1
                }
                "explicit" -> {
                    val count = preferences[PreferencesKeys.COUNT_EXPLICIT] ?: 0
                    preferences[PreferencesKeys.COUNT_EXPLICIT] = count + 1
                }
                "nsfw" -> {
                    val count = preferences[PreferencesKeys.COUNT_NSFW] ?: 0
                    preferences[PreferencesKeys.COUNT_NSFW] = count + 1
                }
                else -> {
                    val count = preferences[PreferencesKeys.COUNT_OTHER] ?: 0
                    preferences[PreferencesKeys.COUNT_OTHER] = count + 1
                }
            }
        }
    }

    suspend fun setBlockAdult(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BLOCK_ADULT] = enabled
        }
    }

    suspend fun setBlockPornography(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BLOCK_PORNOGRAPHY] = enabled
        }
    }

    suspend fun setBlockExplicit(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BLOCK_EXPLICIT] = enabled
        }
    }

    suspend fun setBlockNsfw(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BLOCK_NSFW] = enabled
        }
    }

    suspend fun setBlockAdultStreaming(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BLOCK_ADULT_STREAMING] = enabled
        }
    }

    suspend fun setBlockGambling(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BLOCK_GAMBLING] = enabled
        }
    }

    suspend fun setBlockMalware(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BLOCK_MALWARE] = enabled
        }
    }

    suspend fun setSafeSearchEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SAFE_SEARCH_ENABLED] = enabled
        }
    }

    suspend fun setAutoStartOnBoot(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_START_ON_BOOT] = enabled
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun setSaveStatistics(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SAVE_STATISTICS] = enabled
        }
    }

    suspend fun setStoreBlockedDomainNames(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.STORE_BLOCKED_DOMAIN_NAMES] = enabled
        }
    }

    suspend fun setAutoUpdatesEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_UPDATES_ENABLED] = enabled
        }
    }

    suspend fun setPinData(enabled: Boolean, salt: String?, hash: String?) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PIN_PROTECTION_ENABLED] = enabled
            if (salt != null) {
                preferences[PreferencesKeys.PIN_SALT] = salt
            } else {
                preferences.remove(PreferencesKeys.PIN_SALT)
            }
            if (hash != null) {
                preferences[PreferencesKeys.PIN_HASH] = hash
            } else {
                preferences.remove(PreferencesKeys.PIN_HASH)
            }
        }
    }

    suspend fun setPinProtectionEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PIN_PROTECTION_ENABLED] = enabled
        }
    }

    suspend fun resetStats() {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BLOCKED_TODAY] = 0
            preferences[PreferencesKeys.BLOCKED_THIS_WEEK] = 0
            preferences[PreferencesKeys.BLOCKED_THIS_MONTH] = 0
            preferences[PreferencesKeys.TOTAL_BLOCKED] = 0
            preferences[PreferencesKeys.COUNT_ADULT] = 0
            preferences[PreferencesKeys.COUNT_PORNOGRAPHY] = 0
            preferences[PreferencesKeys.COUNT_EXPLICIT] = 0
            preferences[PreferencesKeys.COUNT_NSFW] = 0
            preferences[PreferencesKeys.COUNT_OTHER] = 0
        }
    }

    suspend fun clearAllLocalData() {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BLOCKED_TODAY] = 0
            preferences[PreferencesKeys.BLOCKED_THIS_WEEK] = 0
            preferences[PreferencesKeys.BLOCKED_THIS_MONTH] = 0
            preferences[PreferencesKeys.TOTAL_BLOCKED] = 0
            preferences[PreferencesKeys.COUNT_ADULT] = 0
            preferences[PreferencesKeys.COUNT_PORNOGRAPHY] = 0
            preferences[PreferencesKeys.COUNT_EXPLICIT] = 0
            preferences[PreferencesKeys.COUNT_NSFW] = 0
            preferences[PreferencesKeys.COUNT_OTHER] = 0
            preferences[PreferencesKeys.STORE_BLOCKED_DOMAIN_NAMES] = false
        }
    }
}

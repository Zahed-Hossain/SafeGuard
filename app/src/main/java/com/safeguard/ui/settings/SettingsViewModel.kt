package com.safeguard.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safeguard.SafeGuardApplication
import com.safeguard.blocklist.repository.BlocklistRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SafeGuardApplication
    private val preferencesRepository = app.preferencesRepository
    val securityManager = app.securityManager
    private val blocklistRepository: BlocklistRepository = app.blocklistRepository

    // PROTECTION
    val isProtectionEnabled: StateFlow<Boolean> = preferencesRepository.isProtectionEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val autoStartOnBoot: StateFlow<Boolean> = preferencesRepository.autoStartOnBoot
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isPinProtected: StateFlow<Boolean> = securityManager.isPinProtected
    val hasPinSet: StateFlow<Boolean> = securityManager.hasPinSet

    // FILTERING
    val blockAdult: StateFlow<Boolean> = preferencesRepository.blockAdult
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val blockPornography: StateFlow<Boolean> = preferencesRepository.blockPornography
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val blockExplicit: StateFlow<Boolean> = preferencesRepository.blockExplicit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val blockNsfw: StateFlow<Boolean> = preferencesRepository.blockNsfw
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val blockAdultStreaming: StateFlow<Boolean> = preferencesRepository.blockAdultStreaming
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // PRIVACY
    val saveStatistics: StateFlow<Boolean> = preferencesRepository.saveStatistics
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val storeBlockedDomainNames: StateFlow<Boolean> = preferencesRepository.storeBlockedDomainNames
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // UPDATES
    val databaseVersion: StateFlow<String> = blocklistRepository.databaseVersion
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "1.0.0-builtin")

    val lastUpdated: StateFlow<String> = blocklistRepository.lastUpdated
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Initial Installation")

    val autoUpdateEnabled: StateFlow<Boolean> = blocklistRepository.autoUpdateEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setAutoStartOnBoot(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAutoStartOnBoot(enabled) }
    }

    fun setBlockAdult(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setBlockAdult(enabled) }
    }

    fun setBlockPornography(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setBlockPornography(enabled) }
    }

    fun setBlockExplicit(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setBlockExplicit(enabled) }
    }

    fun setBlockNsfw(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setBlockNsfw(enabled) }
    }

    fun setBlockAdultStreaming(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setBlockAdultStreaming(enabled) }
    }

    fun setSaveStatistics(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setSaveStatistics(enabled) }
    }

    fun setStoreBlockedDomainNames(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setStoreBlockedDomainNames(enabled) }
    }

    fun setAutoUpdateEnabled(enabled: Boolean) {
        viewModelScope.launch { blocklistRepository.setAutoUpdateEnabled(enabled) }
    }

    fun clearStatistics() {
        viewModelScope.launch {
            preferencesRepository.resetStats()
            app.database.blockEventDao().clearAll()
        }
    }

    fun clearLocalData() {
        viewModelScope.launch {
            preferencesRepository.clearAllLocalData()
            app.database.blockEventDao().clearAll()
        }
    }

    suspend fun setPin(pin: String): Boolean {
        return securityManager.setPin(pin)
    }

    suspend fun verifyPin(pin: String): Boolean {
        return securityManager.verifyPin(pin)
    }

    suspend fun disablePin(pin: String): Boolean {
        return securityManager.disablePinProtection(pin)
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        return securityManager.isIgnoringBatteryOptimizations()
    }

    fun getOpenVpnSettingsIntent() = securityManager.createOpenVpnSettingsIntent()

    fun getBatteryOptimizationIntent() = securityManager.createBatteryOptimizationIntent()
}

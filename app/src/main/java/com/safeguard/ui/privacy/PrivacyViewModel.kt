package com.safeguard.ui.privacy

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safeguard.SafeGuardApplication
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PrivacyViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SafeGuardApplication
    private val preferencesRepository = app.preferencesRepository
    private val database = app.database

    val saveStatistics: StateFlow<Boolean> = preferencesRepository.saveStatistics
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val storeBlockedDomainNames: StateFlow<Boolean> = preferencesRepository.storeBlockedDomainNames
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setSaveStatistics(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setSaveStatistics(enabled)
        }
    }

    fun setStoreBlockedDomainNames(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setStoreBlockedDomainNames(enabled)
        }
    }

    fun clearStatistics() {
        viewModelScope.launch {
            preferencesRepository.resetStats()
            database.blockEventDao().clearAll()
        }
    }

    fun clearLocalData() {
        viewModelScope.launch {
            preferencesRepository.clearAllLocalData()
            database.blockEventDao().clearAll()
        }
    }
}

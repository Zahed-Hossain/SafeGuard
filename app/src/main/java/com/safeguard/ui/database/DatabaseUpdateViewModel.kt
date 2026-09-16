package com.safeguard.ui.database

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safeguard.SafeGuardApplication
import com.safeguard.blocklist.repository.BlocklistRepository
import com.safeguard.blocklist.repository.UpdateResult
import com.safeguard.worker.BlocklistSyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DatabaseUpdateViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository: BlocklistRepository = (application as SafeGuardApplication).blocklistRepository

    val totalBlockedCount: StateFlow<Int> = repository.totalBlockedCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalWhitelistCount: StateFlow<Int> = repository.totalWhitelistCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val databaseVersion: StateFlow<String> = repository.databaseVersion
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "1.0.0-builtin")

    val lastUpdated: StateFlow<String> = repository.lastUpdated
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Initial Installation")

    val blocklistUrl: StateFlow<String> = repository.remoteUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BlocklistRepository.BLOCKLIST_URL_PLACEHOLDER)

    val autoUpdateEnabled: StateFlow<Boolean> = repository.autoUpdateEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _updateStatusMessage = MutableStateFlow<String?>(null)
    val updateStatusMessage: StateFlow<String?> = _updateStatusMessage.asStateFlow()

    private val _isSuccessStatus = MutableStateFlow(true)
    val isSuccessStatus: StateFlow<Boolean> = _isSuccessStatus.asStateFlow()

    fun setAutoUpdate(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoUpdateEnabled(enabled)
            if (enabled) {
                BlocklistSyncWorker.schedulePeriodicUpdate(getApplication())
            } else {
                BlocklistSyncWorker.cancelPeriodicUpdate(getApplication())
            }
        }
    }

    fun saveRemoteUrl(url: String) {
        viewModelScope.launch {
            if (!url.startsWith("https://", ignoreCase = true)) {
                _isSuccessStatus.value = false
                _updateStatusMessage.value = "URL must start with https://"
                return@launch
            }
            repository.setBlocklistUrl(url)
            _isSuccessStatus.value = true
            _updateStatusMessage.value = "Blocklist URL updated"
        }
    }

    fun checkForUpdatesNow() {
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            _updateStatusMessage.value = null

            val result = repository.updateFromRemote()
            _isCheckingUpdate.value = false

            when (result) {
                is UpdateResult.Success -> {
                    _isSuccessStatus.value = true
                    _updateStatusMessage.value = "Updated to version ${result.version} (${result.domainCount} domains, ${result.duplicateCount} duplicates filtered)."
                }
                is UpdateResult.Failure -> {
                    _isSuccessStatus.value = false
                    _updateStatusMessage.value = "${result.errorMessage} Kept last known good database safely."
                }
            }
        }
    }

    fun reloadBuiltinDatabase() {
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            repository.resetToDefaultBuiltin()
            _isCheckingUpdate.value = false
            _isSuccessStatus.value = true
            _updateStatusMessage.value = "Built-in database reloaded with default categories."
        }
    }

    fun dismissStatusMessage() {
        _updateStatusMessage.value = null
    }
}

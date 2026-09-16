package com.safeguard.ui.whitelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safeguard.SafeGuardApplication
import com.safeguard.blocklist.repository.BlocklistRepository
import com.safeguard.data.db.WhitelistDomain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WhitelistViewModel(
    private val repository: BlocklistRepository = SafeGuardApplication.instance.blocklistRepository,
    val securityManager: com.safeguard.security.SecurityManager = SafeGuardApplication.instance.securityManager
) : ViewModel() {

    val isPinProtected: StateFlow<Boolean> = securityManager.isPinProtected

    suspend fun verifyPin(pin: String): Boolean {
        return securityManager.verifyPin(pin)
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    val totalWhitelistCount: StateFlow<Int> = repository.totalWhitelistCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val displayedWhitelist: StateFlow<List<WhitelistDomain>> = combine(
        repository.allWhitelistDomains,
        _searchQuery
    ) { domains, query ->
        if (query.isBlank()) {
            domains
        } else {
            domains.filter { it.domain.contains(query.trim(), ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun addWhitelistDomain(rawInput: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val result = repository.addWhitelistDomain(rawInput)
            result.onSuccess {
                _actionMessage.value = "Whitelisted '${it.domain}'"
                onSuccess()
            }.onFailure {
                _actionMessage.value = it.message ?: "Failed to whitelist domain"
            }
        }
    }

    fun editWhitelistDomain(id: Long, rawInput: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val result = repository.editWhitelistDomain(id, rawInput)
            result.onSuccess {
                _actionMessage.value = "Updated whitelist domain"
                onSuccess()
            }.onFailure {
                _actionMessage.value = it.message ?: "Failed to update domain"
            }
        }
    }

    fun deleteWhitelistDomain(domain: WhitelistDomain) {
        viewModelScope.launch {
            repository.deleteWhitelistDomain(domain)
            _actionMessage.value = "Removed '${domain.domain}' from whitelist"
        }
    }
}

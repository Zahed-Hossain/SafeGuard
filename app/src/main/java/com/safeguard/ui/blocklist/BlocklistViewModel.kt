package com.safeguard.ui.blocklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safeguard.SafeGuardApplication
import com.safeguard.blocklist.BlockedCategory
import com.safeguard.blocklist.repository.BlocklistRepository
import com.safeguard.blocklist.repository.ImportResult
import com.safeguard.data.db.BlockedDomain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BlocklistViewModel(
    private val repository: BlocklistRepository = SafeGuardApplication.instance.blocklistRepository,
    val securityManager: com.safeguard.security.SecurityManager = SafeGuardApplication.instance.securityManager
) : ViewModel() {

    val isPinProtected: StateFlow<Boolean> = securityManager.isPinProtected

    suspend fun verifyPin(pin: String): Boolean {
        return securityManager.verifyPin(pin)
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedSourceFilter = MutableStateFlow("ALL") // "ALL", "CUSTOM", "BUILTIN", "REMOTE"
    val selectedSourceFilter: StateFlow<String> = _selectedSourceFilter.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    val totalBlockedCount: StateFlow<Int> = repository.totalBlockedCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val displayedDomains: StateFlow<List<BlockedDomain>> = combine(
        repository.allBlockedDomains,
        _searchQuery,
        _selectedSourceFilter
    ) { domains, query, filter ->
        domains.filter { domain ->
            val matchesFilter = when (filter) {
                "CUSTOM" -> domain.source == "CUSTOM"
                "BUILTIN" -> domain.source == "BUILTIN"
                "REMOTE" -> domain.source == "REMOTE"
                else -> true
            }
            val matchesQuery = query.isBlank() || domain.domain.contains(query.trim(), ignoreCase = true)
            matchesFilter && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSourceFilter(filter: String) {
        _selectedSourceFilter.value = filter
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun addCustomDomain(rawInput: String, category: BlockedCategory, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val result = repository.addCustomBlockedDomain(rawInput, category)
            result.onSuccess {
                _actionMessage.value = "Added '${it.domain}' to blocklist"
                onSuccess()
            }.onFailure {
                _actionMessage.value = it.message ?: "Failed to add domain"
            }
        }
    }

    fun editCustomDomain(id: Long, rawInput: String, category: BlockedCategory, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val result = repository.editBlockedDomain(id, rawInput, category)
            result.onSuccess {
                _actionMessage.value = "Updated domain successfully"
                onSuccess()
            }.onFailure {
                _actionMessage.value = it.message ?: "Failed to update domain"
            }
        }
    }

    fun deleteDomain(domain: BlockedDomain) {
        viewModelScope.launch {
            repository.deleteBlockedDomain(domain)
            _actionMessage.value = "Removed '${domain.domain}' from blocklist"
        }
    }

    fun importDomains(rawText: String, category: BlockedCategory, onResult: (ImportResult) -> Unit) {
        viewModelScope.launch {
            val result = repository.importDomains(rawText, category)
            _actionMessage.value = "Imported: ${result.addedCount} added, ${result.skippedCount} duplicates, ${result.malformedCount} invalid"
            onResult(result)
        }
    }

    fun exportDomains(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val filter = if (_selectedSourceFilter.value == "ALL") null else _selectedSourceFilter.value
            val json = repository.exportDomainsToJson(filter)
            onResult(json)
        }
    }
}

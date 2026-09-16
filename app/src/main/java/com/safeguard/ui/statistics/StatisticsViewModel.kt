package com.safeguard.ui.statistics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safeguard.SafeGuardApplication
import com.safeguard.data.BlockedEvent
import com.safeguard.data.ProtectionStats
import com.safeguard.vpn.SafeGuardVpnService
import com.safeguard.vpn.VpnState
import com.safeguard.vpn.VpnStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StatisticsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SafeGuardApplication
    private val preferencesRepository = app.preferencesRepository

    val stats: StateFlow<ProtectionStats> = preferencesRepository.protectionStats
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ProtectionStats()
        )

    val vpnState: StateFlow<VpnState> = SafeGuardVpnService.vpnState

    private val _recentEvents = MutableStateFlow<List<BlockedEvent>>(
        listOf(
            BlockedEvent(
                id = "1",
                domain = "adult-site-example.xxx",
                category = "Adult",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 15,
                reason = "Blocked by SafeGuard Adult filter"
            ),
            BlockedEvent(
                id = "2",
                domain = "pornography-portal.net",
                category = "Pornography",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 45,
                reason = "Blocked by SafeGuard Pornography filter"
            ),
            BlockedEvent(
                id = "3",
                domain = "explicit-media-cdn.org",
                category = "Explicit",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 60 * 2,
                reason = "Blocked by SafeGuard Explicit filter"
            ),
            BlockedEvent(
                id = "4",
                domain = "nsfw-image-board.co",
                category = "NSFW",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 60 * 6,
                reason = "Blocked by SafeGuard NSFW filter"
            ),
            BlockedEvent(
                id = "5",
                domain = "unclassified-inappropriate-host.biz",
                category = "Other",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 60 * 12,
                reason = "Blocked by SafeGuard Safety filter"
            )
        )
    )
    val recentEvents: StateFlow<List<BlockedEvent>> = _recentEvents.asStateFlow()

    fun resetStatistics() {
        viewModelScope.launch {
            preferencesRepository.resetStats()
            _recentEvents.value = emptyList()
        }
    }
}

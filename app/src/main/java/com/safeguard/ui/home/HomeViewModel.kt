package com.safeguard.ui.home

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safeguard.SafeGuardApplication
import com.safeguard.data.ProtectionStats
import com.safeguard.vpn.VpnState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SafeGuardApplication
    private val preferencesRepository = app.preferencesRepository
    private val vpnController = app.vpnController
    val securityManager = app.securityManager

    val isPinProtected: StateFlow<Boolean> = securityManager.isPinProtected
    val vpnState: StateFlow<VpnState> = vpnController.vpnState

    val stats: StateFlow<ProtectionStats> = preferencesRepository.protectionStats
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ProtectionStats()
        )

    val isProtectionEnabled: StateFlow<Boolean> = preferencesRepository.isProtectionEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    fun getVpnPermissionIntent(): Intent? {
        return vpnController.getVpnPermissionIntent()
    }

    fun isNetworkAvailable(): Boolean {
        return vpnController.isNetworkAvailable()
    }

    fun isAnotherVpnActive(): Boolean {
        return vpnController.isAnotherVpnActive()
    }

    fun isVpnPermissionGranted(): Boolean {
        return vpnController.isVpnPermissionGranted()
    }

    suspend fun verifyPin(pin: String): Boolean {
        return securityManager.verifyPin(pin)
    }

    fun reconnectProtection() {
        vpnController.clearError()
        if (!vpnController.isNetworkAvailable()) {
            vpnController.onNetworkUnavailable()
            return
        }
        if (vpnController.isAnotherVpnActive()) {
            vpnController.onAnotherVpnActive()
            return
        }
        val permissionIntent = vpnController.getVpnPermissionIntent()
        if (permissionIntent == null) {
            onVpnPermissionGranted()
        }
    }

    fun onVpnPermissionGranted() {
        viewModelScope.launch {
            preferencesRepository.setProtectionEnabled(true)
            vpnController.startProtection()
        }
    }

    fun onVpnPermissionDenied() {
        vpnController.onVpnPermissionDenied()
    }

    fun onAlreadyActive() {
        vpnController.onAlreadyActive()
    }

    fun onNetworkUnavailable() {
        vpnController.onNetworkUnavailable()
    }

    fun onAnotherVpnActive() {
        vpnController.onAnotherVpnActive()
    }

    fun stopProtection() {
        viewModelScope.launch {
            preferencesRepository.setProtectionEnabled(false)
            vpnController.stopProtection()
        }
    }

    fun clearError() {
        vpnController.clearError()
    }
}

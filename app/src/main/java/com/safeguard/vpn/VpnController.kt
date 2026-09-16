package com.safeguard.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.example.R
import kotlinx.coroutines.flow.StateFlow

class VpnController(private val context: Context) {

    val vpnState: StateFlow<VpnState> = SafeGuardVpnService.vpnState

    fun getVpnPermissionIntent(): Intent? {
        return VpnService.prepare(context)
    }

    fun isVpnPermissionGranted(): Boolean {
        return VpnService.prepare(context) == null
    }

    fun isNetworkAvailable(): Boolean {
        return SafeGuardVpnService.isNetworkAvailable(context)
    }

    fun isAnotherVpnActive(): Boolean {
        return SafeGuardVpnService.isAnotherVpnActive(context)
    }

    fun startProtection() {
        if (vpnState.value.status == VpnStatus.ACTIVE) {
            SafeGuardVpnService.setError(
                context.getString(R.string.error_vpn_already_active),
                VpnErrorCode.ALREADY_ACTIVE
            )
            return
        }

        if (!isNetworkAvailable()) {
            SafeGuardVpnService.setError(
                context.getString(R.string.error_network_unavailable),
                VpnErrorCode.NETWORK_UNAVAILABLE
            )
            return
        }

        if (isAnotherVpnActive()) {
            SafeGuardVpnService.setError(
                context.getString(R.string.error_another_vpn_active),
                VpnErrorCode.ANOTHER_VPN_ACTIVE
            )
            return
        }

        SafeGuardVpnService.start(context)
    }

    fun stopProtection() {
        SafeGuardVpnService.stop(context)
    }

    fun onVpnPermissionDenied() {
        SafeGuardVpnService.setError(
            context.getString(R.string.error_vpn_permission_denied),
            VpnErrorCode.PERMISSION_DENIED
        )
    }

    fun onAlreadyActive() {
        SafeGuardVpnService.setError(
            context.getString(R.string.error_vpn_already_active),
            VpnErrorCode.ALREADY_ACTIVE
        )
    }

    fun onNetworkUnavailable() {
        SafeGuardVpnService.setError(
            context.getString(R.string.error_network_unavailable),
            VpnErrorCode.NETWORK_UNAVAILABLE
        )
    }

    fun onAnotherVpnActive() {
        SafeGuardVpnService.setError(
            context.getString(R.string.error_another_vpn_active),
            VpnErrorCode.ANOTHER_VPN_ACTIVE
        )
    }

    fun clearError() {
        SafeGuardVpnService.clearError()
    }

    fun isProtectionActive(): Boolean {
        return vpnState.value.status == VpnStatus.ACTIVE
    }
}

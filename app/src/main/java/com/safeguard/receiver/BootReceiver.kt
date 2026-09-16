package com.safeguard.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import com.safeguard.SafeGuardApplication
import com.safeguard.vpn.SafeGuardVpnService
import com.safeguard.vpn.VpnStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Android standard BroadcastReceiver to re-engage SafeGuard protection upon
 * device boot completion or package update, strictly respecting user consent
 * and modern Android background execution limits.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        Log.i(TAG, "Received system broadcast: $action")
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as? SafeGuardApplication
                val preferencesRepo = app?.preferencesRepository
                    ?: com.safeguard.data.SafeGuardPreferencesRepository(context.applicationContext)

                val autoStartEnabled = preferencesRepo.autoStartOnBoot.first()
                val protectionWasActive = preferencesRepo.isProtectionEnabled.first()

                Log.d(TAG, "Boot check: autoStart=$autoStartEnabled, wasActive=$protectionWasActive")

                if (autoStartEnabled && protectionWasActive) {
                    // Verify that VPN permission is still granted by the system
                    val vpnIntent = VpnService.prepare(context)
                    if (vpnIntent == null) {
                        if (SafeGuardVpnService.vpnState.value.status != VpnStatus.ACTIVE) {
                            Log.i(TAG, "Re-engaging SafeGuard protection following system reboot")
                            SafeGuardVpnService.start(context)
                        }
                    } else {
                        Log.w(TAG, "VPN permission revoked or unconfigured; cannot auto-start")
                    }
                } else {
                    Log.i(TAG, "Auto-start skipped (autoStart=$autoStartEnabled, wasActive=$protectionWasActive)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling boot broadcast", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

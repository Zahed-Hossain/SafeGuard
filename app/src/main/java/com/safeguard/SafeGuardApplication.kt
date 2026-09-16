package com.safeguard

import android.app.Application
import com.safeguard.blocklist.BlocklistManager
import com.safeguard.blocklist.repository.BlocklistRepository
import com.safeguard.data.SafeGuardPreferencesRepository
import com.safeguard.data.db.SafeGuardDatabase
import com.safeguard.security.SecurityManager
import com.safeguard.util.NotificationHelper
import com.safeguard.vpn.SafeGuardVpnService
import com.safeguard.vpn.VpnController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SafeGuardApplication : Application() {

    companion object {
        lateinit var instance: SafeGuardApplication
            private set
    }

    lateinit var database: SafeGuardDatabase
        private set

    lateinit var blocklistRepository: BlocklistRepository
        private set

    lateinit var preferencesRepository: SafeGuardPreferencesRepository
        private set

    lateinit var blocklistManager: BlocklistManager
        private set

    lateinit var vpnController: VpnController
        private set

    lateinit var securityManager: SecurityManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = SafeGuardDatabase.getInstance(this)
        blocklistRepository = BlocklistRepository(this, database)
        preferencesRepository = SafeGuardPreferencesRepository(this)
        blocklistManager = BlocklistManager(
            domainFilter = blocklistRepository.domainFilter,
            repository = blocklistRepository
        )
        vpnController = VpnController(this)
        securityManager = SecurityManager(this, preferencesRepository)

        NotificationHelper.createNotificationChannel(this)

        // Modern Android-supported auto-start on app launch if user previously enabled protection
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val isEnabled = preferencesRepository.isProtectionEnabled.first()
            val autoStart = preferencesRepository.autoStartOnBoot.first()
            if (isEnabled && autoStart && vpnController.isVpnPermissionGranted() && !vpnController.isAnotherVpnActive()) {
                if (SafeGuardVpnService.vpnState.value.status != com.safeguard.vpn.VpnStatus.ACTIVE) {
                    SafeGuardVpnService.start(this@SafeGuardApplication)
                }
            }
        }
    }
}

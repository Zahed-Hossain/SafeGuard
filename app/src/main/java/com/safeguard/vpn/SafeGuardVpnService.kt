package com.safeguard.vpn

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import com.example.R
import com.safeguard.SafeGuardApplication
import com.safeguard.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * SafeGuard Native Android VPN Service.
 *
 * Implements privacy-preserving, on-device DNS & IP filtering to block adult and
 * inappropriate websites without inspecting HTTPS payload, performing MITM, or
 * installing custom root certificates.
 */
class SafeGuardVpnService : VpnService() {

    companion object {
        private const val TAG = "SafeGuardVpnService"
        const val ACTION_START = "com.safeguard.action.START_VPN"
        const val ACTION_STOP = "com.safeguard.action.STOP_VPN"

        // Virtual interface configuration (IPv4 and IPv6 dual-stack)
        private const val VPN_ADDRESS_IPV4 = "10.1.10.1"
        private const val VPN_PREFIX_IPV4 = 32
        private const val VPN_ROUTE_IPV4 = "0.0.0.0"
        private const val VPN_ROUTE_PREFIX_IPV4 = 0

        private const val VPN_ADDRESS_IPV6 = "fd00:1::1"
        private const val VPN_PREFIX_IPV6 = 128
        private const val VPN_ROUTE_IPV6 = "::"
        private const val VPN_ROUTE_PREFIX_IPV6 = 0
        private const val VPN_MTU = 1500

        // Cloudflare Family DNS (Blocks adult and malicious domains at DNS level, IPv4 & IPv6)
        private const val DNS_PRIMARY_IPV4 = "1.1.1.3"
        private const val DNS_SECONDARY_IPV4 = "1.0.0.3"
        private const val DNS_PRIMARY_IPV6 = "2606:4700:4700::1113"
        private const val DNS_SECONDARY_IPV6 = "2606:4700:4700::1003"

        private val _vpnState = MutableStateFlow(VpnState(VpnStatus.INACTIVE))
        val vpnState: StateFlow<VpnState> = _vpnState.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, SafeGuardVpnService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SafeGuardVpnService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun setError(errorMessage: String, errorCode: VpnErrorCode) {
            _vpnState.value = VpnState(
                status = VpnStatus.ERROR,
                errorMessage = errorMessage,
                errorCode = errorCode
            )
        }

        fun clearError() {
            if (_vpnState.value.status == VpnStatus.ERROR) {
                _vpnState.value = VpnState(status = VpnStatus.INACTIVE)
            }
        }

        /**
         * Verifies if device has an active network connection (Wi-Fi, Cellular, Ethernet).
         */
        fun isNetworkAvailable(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val activeNetwork = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

        /**
         * Checks if another VPN is currently active on the device.
         * Android supports only one active VPN interface at a time.
         */
        fun isAnotherVpnActive(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val activeNetwork = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
            val hasVpnTransport = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            return hasVpnTransport && _vpnState.value.status != VpnStatus.ACTIVE
        }
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val filterEngine: VpnFilterEngine = DefaultVpnFilterEngine()
    private var serviceScope = CoroutineScope(Dispatchers.IO + Job())

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "SafeGuardVpnService onCreate")
        NotificationHelper.createNotificationChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand received action: ${intent?.action}")
        when (intent?.action) {
            ACTION_START -> startVpn()
            ACTION_STOP -> stopVpn(wasRevoked = false)
            else -> {
                if (_vpnState.value.status != VpnStatus.ACTIVE) {
                    startVpn()
                }
            }
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (_vpnState.value.status == VpnStatus.ACTIVE) {
            Log.d(TAG, "VPN already active, ignoring start request")
            return
        }

        // 1. Check network connectivity
        if (!isNetworkAvailable(this)) {
            Log.w(TAG, "Cannot start VPN: Network unavailable")
            setError(
                getString(R.string.error_network_unavailable),
                VpnErrorCode.NETWORK_UNAVAILABLE
            )
            stopSelf()
            return
        }

        // 2. Check if another VPN is active
        if (isAnotherVpnActive(this)) {
            Log.w(TAG, "Cannot start VPN: Another VPN is active")
            setError(
                getString(R.string.error_another_vpn_active),
                VpnErrorCode.ANOTHER_VPN_ACTIVE
            )
            stopSelf()
            return
        }

        // 3. Verify VPN permission
        if (prepare(this) != null) {
            Log.w(TAG, "Cannot start VPN: VpnService.prepare() requires user consent")
            setError(
                getString(R.string.error_vpn_permission_denied),
                VpnErrorCode.PERMISSION_DENIED
            )
            stopSelf()
            return
        }

        try {
            // Post foreground notification immediately to satisfy Android background constraints
            val notification = NotificationHelper.buildVpnNotification(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NotificationHelper.NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
                )
            } else {
                startForeground(NotificationHelper.NOTIFICATION_ID, notification)
            }

            // Build the VPN interface with IPv4 and IPv6 dual-stack support
            val builder = Builder()
                .setSession("SafeGuard")
                .addAddress(VPN_ADDRESS_IPV4, VPN_PREFIX_IPV4)
                .addAddress(VPN_ADDRESS_IPV6, VPN_PREFIX_IPV6)
                .addRoute(VPN_ROUTE_IPV4, VPN_ROUTE_PREFIX_IPV4)
                .addRoute(VPN_ROUTE_IPV6, VPN_ROUTE_PREFIX_IPV6)
                .addDnsServer(DNS_PRIMARY_IPV4)
                .addDnsServer(DNS_SECONDARY_IPV4)
                .addDnsServer(DNS_PRIMARY_IPV6)
                .addDnsServer(DNS_SECONDARY_IPV6)
                .setMtu(VPN_MTU)
                .setBlocking(false)

            val app = application as? SafeGuardApplication
            app?.let {
                filterEngine.initialize(it.blocklistManager)
                // Dynamically sync SafeSearch user preference
                serviceScope.launch {
                    it.preferencesRepository.safeSearchEnabled.collect { enabled ->
                        filterEngine.setSafeSearchEnabled(enabled)
                    }
                }
            }

            vpnInterface = builder.establish()

            if (vpnInterface != null) {
                _vpnState.value = VpnState(
                    status = VpnStatus.ACTIVE,
                    connectedAtMillis = System.currentTimeMillis(),
                    errorCode = VpnErrorCode.NONE,
                    errorMessage = null
                )

                app?.let {
                    serviceScope.launch {
                        it.preferencesRepository.setProtectionEnabled(true)
                    }
                }

                // Launch filtering packet worker
                vpnInterface?.let { pfd ->
                    filterEngine.startFiltering(pfd) { blockedDomain ->
                        serviceScope.launch {
                            val checkResult = app?.blocklistManager?.isDomainBlocked(blockedDomain)
                            val category = checkResult?.category?.displayName ?: "Adult Content"
                            app?.preferencesRepository?.incrementBlockedStats(category)

                            // Respect user privacy setting: Store Blocked Domain Names is OFF by default
                            val storeDomains = app?.preferencesRepository?.storeBlockedDomainNames?.first() ?: false
                            if (storeDomains) {
                                app?.blocklistRepository?.logBlockEvent(category, blockedDomain)
                            }
                        }
                    }
                }

                Log.i(TAG, "SafeGuard VPN interface established successfully")
            } else {
                Log.e(TAG, "Failed to establish VPN interface (null returned by establish)")
                setError(
                    getString(R.string.error_vpn_connection_failed),
                    VpnErrorCode.CONNECTION_FAILED
                )
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while starting SafeGuard VPN", e)
            setError(
                getString(R.string.error_vpn_permission_denied),
                VpnErrorCode.PERMISSION_DENIED
            )
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error starting SafeGuard VPN", e)
            setError(
                e.localizedMessage ?: getString(R.string.error_vpn_connection_failed),
                VpnErrorCode.CONNECTION_FAILED
            )
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopVpn(wasRevoked: Boolean = false) {
        try {
            filterEngine.stopFiltering()
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface", e)
        }

        val app = application as? SafeGuardApplication
        app?.let {
            serviceScope.launch {
                it.preferencesRepository.setProtectionEnabled(false)
            }
        }

        if (wasRevoked) {
            if (isAnotherVpnActive(this)) {
                setError(
                    getString(R.string.error_another_vpn_active),
                    VpnErrorCode.ANOTHER_VPN_ACTIVE
                )
            } else if (VpnService.prepare(this) != null) {
                setError(
                    getString(R.string.error_vpn_permission_denied),
                    VpnErrorCode.PERMISSION_DENIED
                )
            } else {
                setError(
                    getString(R.string.error_vpn_service_stopped),
                    VpnErrorCode.SERVICE_STOPPED
                )
            }
        } else {
            _vpnState.value = VpnState(
                status = VpnStatus.INACTIVE,
                errorCode = VpnErrorCode.NONE,
                errorMessage = null
            )
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "SafeGuard VPN stopped (wasRevoked=$wasRevoked)")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "SafeGuardVpnService onDestroy")
        stopVpn(wasRevoked = false)
        serviceScope.cancel()
    }

    override fun onRevoke() {
        super.onRevoke()
        Log.w(TAG, "SafeGuard VPN permission revoked by user or system")
        stopVpn(wasRevoked = true)
    }
}

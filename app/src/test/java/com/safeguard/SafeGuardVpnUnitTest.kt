package com.safeguard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.safeguard.util.NotificationHelper
import com.safeguard.vpn.SafeGuardVpnService
import com.safeguard.vpn.VpnController
import com.safeguard.vpn.VpnErrorCode
import com.safeguard.vpn.VpnState
import com.safeguard.vpn.VpnStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SafeGuardVpnUnitTest {

    @Test
    fun testNotificationDetails() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        NotificationHelper.createNotificationChannel(context)
        val notification = NotificationHelper.buildVpnNotification(context)

        assertNotNull(notification)
        assertEquals("SAFEGUARD", context.getString(R.string.vpn_notification_title))
        assertEquals("Protection is active", context.getString(R.string.vpn_notification_text))
    }

    @Test
    fun testErrorMessagesDefinedInEnglish() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val permDenied = context.getString(R.string.error_vpn_permission_denied)
        assertTrue(permDenied.contains("permission was denied", ignoreCase = true))

        val alreadyActive = context.getString(R.string.error_vpn_already_active)
        assertTrue(alreadyActive.contains("already active", ignoreCase = true))

        val serviceStopped = context.getString(R.string.error_vpn_service_stopped)
        assertTrue(serviceStopped.contains("stopped", ignoreCase = true))

        val anotherVpn = context.getString(R.string.error_another_vpn_active)
        assertTrue(anotherVpn.contains("Another VPN is currently active", ignoreCase = true))

        val networkUnavailable = context.getString(R.string.error_network_unavailable)
        assertTrue(networkUnavailable.contains("No network connection", ignoreCase = true))
    }

    @Test
    fun testCentralVpnStateTransitions() {
        val controller = VpnController(ApplicationProvider.getApplicationContext())

        // Initial state should be INACTIVE
        SafeGuardVpnService.clearError()
        assertEquals(VpnStatus.INACTIVE, controller.vpnState.value.status)

        // Simulate permission denied error
        controller.onVpnPermissionDenied()
        assertEquals(VpnStatus.ERROR, controller.vpnState.value.status)
        assertEquals(VpnErrorCode.PERMISSION_DENIED, controller.vpnState.value.errorCode)

        // Clear error returns to INACTIVE
        controller.clearError()
        assertEquals(VpnStatus.INACTIVE, controller.vpnState.value.status)

        // Simulate another VPN active error
        controller.onAnotherVpnActive()
        assertEquals(VpnStatus.ERROR, controller.vpnState.value.status)
        assertEquals(VpnErrorCode.ANOTHER_VPN_ACTIVE, controller.vpnState.value.errorCode)

        // Simulate network unavailable error
        controller.onNetworkUnavailable()
        assertEquals(VpnStatus.ERROR, controller.vpnState.value.status)
        assertEquals(VpnErrorCode.NETWORK_UNAVAILABLE, controller.vpnState.value.errorCode)

        // Simulate VPN already active
        controller.onAlreadyActive()
        assertEquals(VpnErrorCode.ALREADY_ACTIVE, controller.vpnState.value.errorCode)

        // Reset
        controller.clearError()
        assertEquals(VpnStatus.INACTIVE, controller.vpnState.value.status)
    }
}

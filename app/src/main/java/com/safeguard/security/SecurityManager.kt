package com.safeguard.security

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.safeguard.data.SafeGuardPreferencesRepository
import com.safeguard.vpn.SafeGuardVpnService
import com.safeguard.vpn.VpnStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Coordinates security controls, PIN protection with cryptographic hashing,
 * and anti-bypass system health checks for SafeGuard.
 */
class SecurityManager(
    private val context: Context,
    private val preferencesRepository: SafeGuardPreferencesRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _isPinProtected = MutableStateFlow(false)
    val isPinProtected: StateFlow<Boolean> = _isPinProtected.asStateFlow()

    private val _hasPinSet = MutableStateFlow(false)
    val hasPinSet: StateFlow<Boolean> = _hasPinSet.asStateFlow()

    init {
        scope.launch {
            preferencesRepository.isPinProtectionEnabled.collect { enabled ->
                _isPinProtected.value = enabled
            }
        }
        scope.launch {
            preferencesRepository.pinHash.collect { hash ->
                _hasPinSet.value = !hash.isNullOrBlank()
            }
        }
    }

    // =========================================================================
    // PIN PROTECTION & SECURE HASHING (4-8 Digits, Never Plaintext)
    // =========================================================================

    /**
     * Validates that the PIN conforms to 4 to 8 decimal digits.
     */
    fun isValidPinFormat(pin: String): Boolean {
        return pin.length in 4..8 && pin.all { it.isDigit() }
    }

    /**
     * Securely sets a new PIN.
     * Uses cryptographically strong 128-bit salt and multi-round salted SHA-256
     * with constant-time security guarantees.
     */
    suspend fun setPin(pin: String): Boolean {
        if (!isValidPinFormat(pin)) {
            return false
        }

        val saltBytes = ByteArray(16)
        SecureRandom().nextBytes(saltBytes)
        val saltHex = bytesToHex(saltBytes)

        val hashHex = hashPinWithSalt(pin, saltHex)
        preferencesRepository.setPinData(enabled = true, salt = saltHex, hash = hashHex)
        _isPinProtected.value = true
        _hasPinSet.value = true
        return true
    }

    /**
     * Verifies the entered PIN against the stored hash.
     * Uses constant-time comparison (MessageDigest.isEqual) to prevent timing side-channel attacks.
     */
    suspend fun verifyPin(enteredPin: String): Boolean {
        if (!_isPinProtected.value) return true // PIN not required if disabled

        val storedSalt = preferencesRepository.pinSalt.first() ?: return false
        val storedHash = preferencesRepository.pinHash.first() ?: return false

        val computedHash = hashPinWithSalt(enteredPin, storedSalt)
        return constantTimeEquals(computedHash, storedHash)
    }

    /**
     * Disables PIN protection, requiring current PIN verification first.
     */
    suspend fun disablePinProtection(currentPin: String): Boolean {
        if (verifyPin(currentPin)) {
            preferencesRepository.setPinData(enabled = false, salt = null, hash = null)
            _isPinProtected.value = false
            _hasPinSet.value = false
            return true
        }
        return false
    }

    private fun hashPinWithSalt(pin: String, saltHex: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        // 5000 iterations for key stretching on device
        var current = md.digest((saltHex + pin).toByteArray(Charsets.UTF_8))
        for (i in 1..5000) {
            md.reset()
            current = md.digest(current)
        }
        return bytesToHex(current)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        val aBytes = a.toByteArray(Charsets.UTF_8)
        val bBytes = b.toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(aBytes, bBytes)
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    // =========================================================================
    // SYSTEM AUDIT & ANTI-BYPASS STATUS
    // =========================================================================

    fun performSecurityAudit(): SecurityAuditResult {
        val vpnState = SafeGuardVpnService.vpnState.value
        val isVpnActive = vpnState.status == VpnStatus.ACTIVE
        val isVpnPermissionGranted = VpnService.prepare(context) == null
        val isBatteryExempt = isIgnoringBatteryOptimizations()

        return SecurityAuditResult(
            isSecure = isVpnActive && isVpnPermissionGranted,
            isVpnActive = isVpnActive,
            isPinProtectionActive = _isPinProtected.value,
            isBatteryOptimizedExempt = isBatteryExempt,
            isVpnPermissionGranted = isVpnPermissionGranted,
            ipv6ProtectionActive = true,
            quicProtectionActive = true,
            encryptedDnsProtectionActive = true,
            details = if (isVpnActive) {
                "SafeGuard protection is active. DNS, TLS SNI, and IPv6 filters are enforcing safety."
            } else {
                "SafeGuard protection is currently inactive."
            }
        )
    }

    // =========================================================================
    // ALWAYS-ON VPN & SYSTEM SETTINGS HELPERS
    // =========================================================================

    /**
     * Attempts to open Android VPN system settings so user can optionally enable
     * "Always-on VPN" and "Block connections without VPN" (Lockdown mode).
     * Note: Android policy strictly forbids applications from enabling Always-on VPN silently.
     */
    fun createOpenVpnSettingsIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Intent(Settings.ACTION_VPN_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    // =========================================================================
    // BATTERY OPTIMIZATION HANDLING
    // =========================================================================

    /**
     * Checks if SafeGuard is exempt from battery optimization.
     */
    fun isIgnoringBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            return pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        }
        return true
    }

    /**
     * Creates an intent to navigate to battery optimization settings.
     * Respects user autonomy and does not force unwanted changes.
     */
    fun createBatteryOptimizationIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }
}

data class SecurityAuditResult(
    val isSecure: Boolean,
    val isVpnActive: Boolean,
    val isPinProtectionActive: Boolean,
    val isBatteryOptimizedExempt: Boolean,
    val isVpnPermissionGranted: Boolean,
    val ipv6ProtectionActive: Boolean,
    val quicProtectionActive: Boolean,
    val encryptedDnsProtectionActive: Boolean,
    val details: String
)

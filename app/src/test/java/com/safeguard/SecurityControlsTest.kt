package com.safeguard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.safeguard.data.SafeGuardPreferencesRepository
import com.safeguard.security.SecurityManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecurityControlsTest {

    private lateinit var context: Context
    private lateinit var preferencesRepository: SafeGuardPreferencesRepository
    private lateinit var securityManager: SecurityManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferencesRepository = SafeGuardPreferencesRepository(context)
        securityManager = SecurityManager(context, preferencesRepository)
    }

    @Test
    fun testPinFormatValidation() {
        // Valid PINs (4 to 8 decimal digits)
        assertTrue(securityManager.isValidPinFormat("1234"))
        assertTrue(securityManager.isValidPinFormat("12345"))
        assertTrue(securityManager.isValidPinFormat("123456"))
        assertTrue(securityManager.isValidPinFormat("1234567"))
        assertTrue(securityManager.isValidPinFormat("12345678"))

        // Invalid PINs: length outside 4..8
        assertFalse(securityManager.isValidPinFormat(""))
        assertFalse(securityManager.isValidPinFormat("1"))
        assertFalse(securityManager.isValidPinFormat("12"))
        assertFalse(securityManager.isValidPinFormat("123"))
        assertFalse(securityManager.isValidPinFormat("123456789"))

        // Invalid PINs: non-digit characters
        assertFalse(securityManager.isValidPinFormat("123a"))
        assertFalse(securityManager.isValidPinFormat("abcd"))
        assertFalse(securityManager.isValidPinFormat("12 34"))
        assertFalse(securityManager.isValidPinFormat("12-34"))
    }

    @Test
    fun testSetPinAndVerificationFlow() = runBlocking {
        val testPin = "4826"

        // 1. Set the PIN
        val setSuccess = securityManager.setPin(testPin)
        assertTrue("PIN should be accepted and set", setSuccess)

        // 2. Verify DataStore does NOT contain plaintext PIN
        val storedHash = preferencesRepository.pinHash.first()
        val storedSalt = preferencesRepository.pinSalt.first()

        assertNotNull("Salt must be stored", storedSalt)
        assertNotNull("Hash must be stored", storedHash)
        assertNotEquals("Plaintext PIN must NEVER be stored in hash field", testPin, storedHash)
        assertNotEquals("Plaintext PIN must NEVER be stored in salt field", testPin, storedSalt)
        assertTrue("Salt must be a non-empty hex string", storedSalt!!.length >= 32)
        assertTrue("Hash must be SHA-256 (64 hex characters)", storedHash!!.length == 64)

        // 3. Verify correct PIN succeeds
        val isCorrect = securityManager.verifyPin(testPin)
        assertTrue("Verification with correct PIN must succeed", isCorrect)

        // 4. Verify incorrect PIN fails
        val isWrongPin = securityManager.verifyPin("9999")
        assertFalse("Verification with incorrect PIN must fail", isWrongPin)

        val isAnotherWrongPin = securityManager.verifyPin("0000")
        assertFalse("Verification with incorrect PIN must fail", isAnotherWrongPin)

        // 5. Disable PIN protection with correct PIN
        val disableSuccess = securityManager.disablePinProtection(testPin)
        assertTrue("Disabling with correct PIN must succeed", disableSuccess)
        assertFalse("PIN protection state must now be false", securityManager.isPinProtected.value)

        // 6. When PIN protection is disabled, verifyPin returns true (no blocker)
        assertTrue("When disabled, verifyPin passes", securityManager.verifyPin("1234"))
    }

    @Test
    fun testSaltUniqueness() = runBlocking {
        val pin = "5555"

        // Set PIN first time
        securityManager.setPin(pin)
        val salt1 = preferencesRepository.pinSalt.first()
        val hash1 = preferencesRepository.pinHash.first()

        // Set same PIN second time (new random salt should be generated)
        securityManager.setPin(pin)
        val salt2 = preferencesRepository.pinSalt.first()
        val hash2 = preferencesRepository.pinHash.first()

        assertNotEquals("Each PIN generation must use a unique random salt", salt1, salt2)
        assertNotEquals("Hashes with different salts must differ even for identical PINs", hash1, hash2)
    }

    @Test
    fun testIntentCreationHelpers() {
        val vpnIntent = securityManager.createOpenVpnSettingsIntent()
        assertNotNull(vpnIntent)
        assertNotNull(vpnIntent.action)

        val batteryIntent = securityManager.createBatteryOptimizationIntent()
        assertNotNull(batteryIntent)
        assertNotNull(batteryIntent.action)
    }

    @Test
    fun testSecurityAuditIntegrity() {
        val audit = securityManager.performSecurityAudit()
        assertNotNull(audit)
        assertTrue("IPv6 protection must be reported as active", audit.ipv6ProtectionActive)
        assertTrue("QUIC protection must be reported as active", audit.quicProtectionActive)
        assertTrue("Encrypted DNS protection must be reported as active", audit.encryptedDnsProtectionActive)
        assertNotNull(audit.details)
    }
}

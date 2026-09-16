package com.safeguard

import android.content.Context
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.safeguard.data.SafeGuardPreferencesRepository
import com.safeguard.ui.blocked.BlockedWebsiteScreen
import com.safeguard.ui.theme.SafeGuardTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Part7UiAndFeatureTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var preferencesRepository: SafeGuardPreferencesRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferencesRepository = SafeGuardPreferencesRepository(context)
    }

    @Test
    fun testPrivacyPreferencesDefaultsAndUpdates() = runBlocking {
        // Save statistics defaults to true
        assertTrue(preferencesRepository.saveStatistics.first())

        // Store blocked domain names MUST be false by default
        assertFalse(preferencesRepository.storeBlockedDomainNames.first())

        preferencesRepository.setStoreBlockedDomainNames(true)
        assertTrue(preferencesRepository.storeBlockedDomainNames.first())

        preferencesRepository.setStoreBlockedDomainNames(false)
        assertFalse(preferencesRepository.storeBlockedDomainNames.first())
    }

    @Test
    fun testStatisticsIncrementAndClear() = runBlocking {
        preferencesRepository.resetStats()

        preferencesRepository.incrementBlockedStats("Adult")
        preferencesRepository.incrementBlockedStats("Pornography")
        preferencesRepository.incrementBlockedStats("Explicit")
        preferencesRepository.incrementBlockedStats("NSFW")
        preferencesRepository.incrementBlockedStats("Other")

        val stats = preferencesRepository.protectionStats.first()
        assertEquals(5, stats.totalBlocked)
        assertEquals(1, stats.adultCount)
        assertEquals(1, stats.pornographyCount)
        assertEquals(1, stats.explicitCount)
        assertEquals(1, stats.nsfwCount)
        assertEquals(1, stats.otherCount)

        preferencesRepository.clearAllLocalData()
        val cleared = preferencesRepository.protectionStats.first()
        assertEquals(0, cleared.totalBlocked)
        assertEquals(0, cleared.adultCount)
    }

    @Test
    fun testBlockedWebsiteScreenRendersProperly() {
        var navigatedBack = false

        composeTestRule.setContent {
            SafeGuardTheme(darkTheme = false) {
                BlockedWebsiteScreen(
                    domain = "adult-site.xxx",
                    category = "Pornography",
                    onReturnToHome = { navigatedBack = true }
                )
            }
        }

        composeTestRule.onNodeWithTag("blocked_website_title").assertExists()
        composeTestRule.onNodeWithTag("protection_active_indicator").assertExists()
        composeTestRule.onNodeWithTag("go_back_button").performScrollTo().performClick()
        assertTrue(navigatedBack)
    }

    @Test
    fun testBlockedWebsiteScreenDarkMode() {
        composeTestRule.setContent {
            SafeGuardTheme(darkTheme = true) {
                BlockedWebsiteScreen(
                    domain = "adult-site.xxx",
                    category = "Adult Content",
                    onReturnToHome = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("blocked_website_title").assertExists()
        composeTestRule.onNodeWithTag("go_back_button").assertExists()
    }
}

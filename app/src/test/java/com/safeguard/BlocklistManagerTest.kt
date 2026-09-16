package com.safeguard

import com.safeguard.blocklist.BlockedCategory
import com.safeguard.blocklist.BlocklistManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BlocklistManagerTest {

    private lateinit var blocklistManager: BlocklistManager

    @Before
    fun setup() {
        blocklistManager = BlocklistManager()
    }

    @Test
    fun testAdultDomainIsBlocked() {
        val result = blocklistManager.isDomainBlocked("exampleporn.com")
        assertTrue(result.isBlocked)
        assertEquals(BlockedCategory.ADULT, result.category)
    }

    @Test
    fun testGamblingDomainIsBlocked() {
        val result = blocklistManager.isDomainBlocked("bestonlinecasino.net")
        assertTrue(result.isBlocked)
        assertEquals(BlockedCategory.GAMBLING, result.category)
    }

    @Test
    fun testCleanDomainIsNotBlocked() {
        val result = blocklistManager.isDomainBlocked("wikipedia.org")
        assertFalse(result.isBlocked)
    }

    @Test
    fun testDisableCategoryAllowsDomain() {
        val result = blocklistManager.isDomainBlocked(
            domain = "online-casino.com",
            blockGambling = false
        )
        assertFalse(result.isBlocked)
    }
}

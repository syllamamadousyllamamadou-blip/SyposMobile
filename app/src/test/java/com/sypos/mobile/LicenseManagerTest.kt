package com.sypos.mobile

import com.sypos.mobile.util.LicenseManager
import org.junit.Assert.*
import org.junit.Test

class LicenseManagerTest {

    @Test
    fun testClockTamperingDetection() {
        val now = System.currentTimeMillis()
        
        // Normal past timestamp -> No tampering
        assertFalse(LicenseManager.isClockTampered(now - 3600000L))
        
        // Future timestamp (system clock turned backward by 1 day) -> Tampering detected!
        assertTrue(LicenseManager.isClockTampered(now + 86400000L))
    }

    @Test
    fun testLicenseExpirationLogic() {
        val now = System.currentTimeMillis()
        
        // Lifetime (0) -> Never expired
        assertFalse(LicenseManager.isLicenseExpired(0L))
        
        // Future expiration -> Not expired
        assertFalse(LicenseManager.isLicenseExpired(now + 1000000L))
        
        // Past expiration -> Expired
        assertTrue(LicenseManager.isLicenseExpired(now - 1000000L))
    }
}

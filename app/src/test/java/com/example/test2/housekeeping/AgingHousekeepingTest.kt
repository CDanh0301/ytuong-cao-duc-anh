package com.example.test2.housekeeping

import com.example.test2.services.AgingMonitorManager
import com.example.test2.shared.models.AgingSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgingHousekeepingTest {

    @Test
    fun testDailyNotificationDecisionAndDueCheck() {
        val dayMs = AgingMonitorManager.NOTIFY_INTERVAL_MS
        val t0 = 1_750_000_000_000L

        // N == 0 never notifies even if forced
        assertFalse(AgingMonitorManager.shouldSendDailyNotification(0, 0L, t0, force = true))
        assertFalse(AgingMonitorManager.shouldSendDailyNotification(0, 0L, t0, force = false))

        // First check with N > 0 notifies
        assertTrue(AgingMonitorManager.shouldSendDailyNotification(3, 0L, t0, force = false))

        // Within 24h window: do not spam
        assertFalse(AgingMonitorManager.shouldSendDailyNotification(3, t0, t0 + dayMs - 1000L, force = false))

        // Forced manual scan notifies immediately
        assertTrue(AgingMonitorManager.shouldSendDailyNotification(3, t0, t0 + 5000L, force = true))

        // After 24h: daily notification fires again
        assertTrue(AgingMonitorManager.shouldSendDailyNotification(3, t0, t0 + dayMs, force = false))

        // Due check
        assertTrue(AgingMonitorManager.isDueForDailyCheck(0L, t0))
        assertFalse(AgingMonitorManager.isDueForDailyCheck(t0, t0 + dayMs - 1L))
        assertTrue(AgingMonitorManager.isDueForDailyCheck(t0, t0 + dayMs))
    }

    @Test
    fun testEffectiveDefaultRetentionDaysByTier() {
        val settings = AgingSettings(retentionDays = 7)
        assertEquals(AgingSettings.DEFAULT_RETENTION_DAYS, settings.effectiveDefaultRetentionDays(isPro = false))
        assertEquals(7, settings.effectiveDefaultRetentionDays(isPro = true))
    }
}

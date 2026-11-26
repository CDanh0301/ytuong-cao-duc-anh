package com.example.test2.search

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.test2.MainActivity
import com.example.test2.database.AppDatabaseHelper
import com.example.test2.notifications.ScreenshotNotificationHelper
import com.example.test2.security.PrivacyVaultManager
import com.example.test2.shared.models.ActionCard
import com.example.test2.shared.models.ActionCardType
import com.example.test2.ui.AppTab
import com.example.test2.ui.PhotoAppViewModel
import com.example.test2.widget.PhotoSearchWidgetProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [QuickSearchActivity], [PhotoSearchWidgetProvider],
 * Action Card widget routing, Privacy Vault AppLock enforcement, and notification zero-regression.
 */
@RunWith(AndroidJUnit4::class)
class QuickSearchWidgetTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Before
    fun resetState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PrivacyVaultManager.setPromptedConsent(context, true)
        PrivacyVaultManager.setVaultEnabled(context, false)
    }

    @After
    fun cleanupState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PrivacyVaultManager.setVaultEnabled(context, false)
        AppDatabaseHelper.getInstance(context).purgePhotoData("widget_test_photo")
    }

    @Test
    fun quickSearchActivity_withVaultDisabled_opensUnlockedSearchScreen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, QuickSearchActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        ActivityScenario.launch<QuickSearchActivity>(intent).use { scenario ->
            composeRule.waitForIdle()
            scenario.onActivity { activity ->
                val vm = ViewModelProvider(activity)[PhotoAppViewModel::class.java]
                assertTrue(vm.isAppUnlocked.value)
            }
            composeRule.onNodeWithText("Tất cả").assertIsDisplayed()
        }
    }

    @Test
    fun quickSearchActivity_withVaultEnabled_enforcesAppLockScreen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PrivacyVaultManager.setVaultEnabled(context, true)

        val intent = Intent(context, QuickSearchActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        ActivityScenario.launch<QuickSearchActivity>(intent).use { scenario ->
            composeRule.waitForIdle()
            scenario.onActivity { activity ->
                val vm = ViewModelProvider(activity)[PhotoAppViewModel::class.java]
                assertTrue(vm.isVaultEnabled.value)
                assertFalse(vm.isAppUnlocked.value)
            }
            composeRule.onNodeWithText("Két ảnh bảo mật").assertIsDisplayed()
        }
    }

    @Test
    fun widgetActionCardIntent_routesMainActivityToMatchingFilter() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(PhotoSearchWidgetProvider.EXTRA_ACTION_CARD_TYPE, ActionCardType.PAYMENT.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            composeRule.waitForIdle()
            scenario.onActivity { activity ->
                val vm = ViewModelProvider(activity)[PhotoAppViewModel::class.java]
                assertEquals(AppTab.PHOTOS, vm.activeTab.value)
                assertEquals(ActionCardType.PAYMENT, vm.activeActionCardType.value)
            }
        }
    }

    @Test
    fun widgetState_keepsActionCardCountsVisibleWhenVaultEnabled() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PrivacyVaultManager.setVaultEnabled(context, true)
        val db = AppDatabaseHelper.getInstance(context)
        db.replaceActionCardsForPhoto(
            "widget_test_photo",
            listOf(
                ActionCard(
                    id = "card_addr_1",
                    photoId = "widget_test_photo",
                    type = ActionCardType.ADDRESS,
                    title = "123 Lê Lợi"
                )
            )
        )

        val state = PhotoSearchWidgetProvider.loadWidgetState(context)
        assertTrue(state.isVaultEnabled)
        assertTrue(state.addressCount >= 1)

        val compactOptions = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 48)
        }
        val expandedOptions = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 120)
        }
        assertNotNull(PhotoSearchWidgetProvider.buildRemoteViews(context, state, compactOptions))
        assertNotNull(PhotoSearchWidgetProvider.buildRemoteViews(context, state, expandedOptions))
    }

    @Test
    fun notificationExtras_zeroRegression_routeToPhotoAndHousekeeping() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(ScreenshotNotificationHelper.EXTRA_PHOTO_ID, "notif_photo_42")
            putExtra(ScreenshotNotificationHelper.EXTRA_TAB, "housekeeping")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            composeRule.waitForIdle()
            scenario.onActivity { activity ->
                val vm = ViewModelProvider(activity)[PhotoAppViewModel::class.java]
                assertEquals("notif_photo_42", vm.viewingPhotoId.value)
                assertEquals(AppTab.CLEANER, vm.activeTab.value)
            }
        }
    }
}

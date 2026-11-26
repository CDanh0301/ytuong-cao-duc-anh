package com.example.test2.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Verifies Home Screen Search Widget resources, Light/Dark translucent color definitions,
 * resizable metadata, clean removal of obsolete OS search integration, and helper logic.
 */
class PhotoSearchWidgetConfigTest {

    private fun resolveAppPath(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app", relativePath)
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Could not locate $relativePath from working dir ${File(".").absolutePath}")
    }

    @Test
    fun manifestAndXml_declareWidgetAndQuickSearchWithoutObsoleteSystemSearch() {
        val manifestText = resolveAppPath("src/main/AndroidManifest.xml").readText(Charsets.UTF_8)
        assertTrue(manifestText.contains("android:name=\".search.QuickSearchActivity\""))
        assertTrue(manifestText.contains("android:name=\".widget.PhotoSearchWidgetProvider\""))
        assertTrue(manifestText.contains("android.appwidget.action.APPWIDGET_UPDATE"))
        assertTrue(manifestText.contains("@xml/photo_search_widget_info"))
        assertTrue(manifestText.contains("android:launchMode=\"singleTop\""))

        // Obsolete system search components must be removed
        assertFalse(manifestText.contains("android.intent.action.SEARCH"))
        assertFalse(manifestText.contains("android.app.searchable"))
        assertFalse(manifestText.contains("android.app.shortcuts"))
        assertFalse(manifestText.contains("android:scheme=\"myapp\""))

        val widgetInfo = resolveAppPath("src/main/res/xml/photo_search_widget_info.xml").readText(Charsets.UTF_8)
        assertTrue(widgetInfo.contains("android:resizeMode=\"horizontal|vertical\""))
        assertTrue(widgetInfo.contains("android:targetCellWidth=\"4\""))
        assertTrue(widgetInfo.contains("android:targetCellHeight=\"1\""))
        assertTrue(widgetInfo.contains("android:updatePeriodMillis=\"0\""))
        assertTrue(widgetInfo.contains("android:widgetCategory=\"home_screen\""))
        assertTrue(widgetInfo.contains("@layout/widget_photo_search_compact"))
        assertTrue(widgetInfo.contains("@layout/widget_photo_search_preview"))
    }

    @Test
    fun lightAndDarkModeColors_andBothLocalesStrings_areConfigured() {
        val lightColors = resolveAppPath("src/main/res/values/colors.xml").readText(Charsets.UTF_8)
        val darkColors = resolveAppPath("src/main/res/values-night/colors.xml").readText(Charsets.UTF_8)
        val requiredColorKeys = listOf(
            "widget_bg_fill",
            "widget_bg_stroke",
            "widget_divider",
            "widget_text_primary",
            "widget_text_hint",
            "widget_icon_primary",
            "widget_icon_muted",
            "widget_pro_bg",
            "widget_pro_text",
            "widget_chip_address_bg",
            "widget_chip_address_text",
            "widget_chip_payment_bg",
            "widget_chip_payment_text",
            "widget_chip_contact_bg",
            "widget_chip_contact_text"
        )
        for (key in requiredColorKeys) {
            assertTrue("Missing $key in values/colors.xml", lightColors.contains("name=\"$key\""))
            assertTrue("Missing $key in values-night/colors.xml", darkColors.contains("name=\"$key\""))
        }
        // Light mode fill must remain translucent white (#E6FFFFFF)
        assertTrue(lightColors.contains("#E6FFFFFF"))

        val defaultStrings = resolveAppPath("src/main/res/values/strings.xml").readText(Charsets.UTF_8)
        val viStrings = resolveAppPath("src/main/res/values-vi/strings.xml").readText(Charsets.UTF_8)
        for (stringsText in listOf(defaultStrings, viStrings)) {
            assertTrue(stringsText.contains("name=\"search_bar_hint\""))
            assertTrue(stringsText.contains("name=\"widget_search_description\""))
            assertTrue(stringsText.contains("name=\"widget_chip_address\""))
            assertTrue(stringsText.contains("name=\"widget_chip_payment\""))
            assertTrue(stringsText.contains("name=\"widget_chip_contact\""))
            assertFalse(stringsText.contains("shortcut_search_short"))
            assertFalse(stringsText.contains("shortcut_search_long"))
        }
    }

    @Test
    fun widgetHelpers_selectLayoutByHeightAndPreserveCountsInVaultMode() {
        assertFalse(PhotoSearchWidgetProvider.resolveUseExpandedLayout(48))
        assertFalse(PhotoSearchWidgetProvider.resolveUseExpandedLayout(95))
        assertTrue(PhotoSearchWidgetProvider.resolveUseExpandedLayout(96))
        assertTrue(PhotoSearchWidgetProvider.resolveUseExpandedLayout(140))

        assertEquals("📍 Địa chỉ (0)", PhotoSearchWidgetProvider.formatChipLabel("📍 Địa chỉ", -2))
        assertEquals("💳 STK (7)", PhotoSearchWidgetProvider.formatChipLabel("💳 STK", 7))

        val vaultState = PhotoSearchWidgetState(
            addressCount = 4,
            paymentCount = 3,
            contactCount = 9,
            isVaultEnabled = true,
            isPro = true
        )
        assertTrue(vaultState.isVaultEnabled)
        assertEquals(4, vaultState.addressCount)
        assertEquals(3, vaultState.paymentCount)
        assertEquals(9, vaultState.contactCount)
    }
}

package com.example.test2.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import com.example.test2.MainActivity
import com.example.test2.R
import com.example.test2.database.AppDatabaseHelper
import com.example.test2.search.QuickSearchActivity
import com.example.test2.security.PrivacyVaultManager
import com.example.test2.shared.models.ActionCardType
import com.example.test2.storage.AndroidFilePersistenceDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

data class PhotoSearchWidgetState(
    val addressCount: Int = 0,
    val paymentCount: Int = 0,
    val contactCount: Int = 0,
    val isVaultEnabled: Boolean = false,
    val isPro: Boolean = false
)

/**
 * Resizable Home Screen Search Widget Provider (4x1 compact <-> 4x2 expanded).
 * Adapts to Light/Dark mode via day/night color resources, shows Action Card counts
 * even when Privacy Vault is enabled, and routes search taps to [QuickSearchActivity].
 */
class PhotoSearchWidgetProvider : AppWidgetProvider() {

    companion object {
        const val EXTRA_ACTION_CARD_TYPE = "com.example.test2.extra.WIDGET_ACTION_CARD_TYPE"
        const val EXPANDED_MIN_HEIGHT_DP = 96

        private const val WIDGET_PREFS = "photo_search_widget_prefs"
        private const val KEY_IS_PRO = "cached_is_pro"
        private const val REQ_CODE_SEARCH = 4100
        private const val REQ_CODE_ADDRESS = 4101
        private const val REQ_CODE_PAYMENT = 4102
        private const val REQ_CODE_CONTACT = 4103

        fun resolveUseExpandedLayout(minHeightDp: Int): Boolean {
            return minHeightDp >= EXPANDED_MIN_HEIGHT_DP
        }

        fun formatChipLabel(baseLabel: String, count: Int): String {
            return "$baseLabel (${count.coerceAtLeast(0)})"
        }

        fun cacheIsPro(context: Context, isPro: Boolean) {
            context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_IS_PRO, isPro)
                .apply()
        }

        fun loadWidgetState(context: Context): PhotoSearchWidgetState {
            val dbHelper = AppDatabaseHelper.getInstance(context)
            val counts = dbHelper.getActionCardsPhotoCountByType()
            val isVaultEnabled = PrivacyVaultManager.isVaultEnabled(context)

            val prefs = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
            val isPro = if (prefs.contains(KEY_IS_PRO)) {
                prefs.getBoolean(KEY_IS_PRO, false)
            } else {
                val parsedPro = readIsProFromPersistence(context)
                cacheIsPro(context, parsedPro)
                parsedPro
            }

            return PhotoSearchWidgetState(
                addressCount = counts[ActionCardType.ADDRESS] ?: 0,
                paymentCount = counts[ActionCardType.PAYMENT] ?: 0,
                contactCount = counts[ActionCardType.CONTACT] ?: 0,
                isVaultEnabled = isVaultEnabled,
                isPro = isPro
            )
        }

        private fun readIsProFromPersistence(context: Context): Boolean {
            return try {
                val raw = runBlocking { AndroidFilePersistenceDriver(context).loadData() }
                if (raw.isNullOrBlank()) {
                    false
                } else {
                    JSONObject(raw).optJSONObject("accountTier")?.optBoolean("isPro", false) ?: false
                }
            } catch (_: Exception) {
                false
            }
        }

        fun buildRemoteViews(
            context: Context,
            state: PhotoSearchWidgetState,
            options: Bundle?
        ): RemoteViews {
            val compactViews = createSingleRemoteViews(context, state, expanded = false)
            val expandedViews = createSingleRemoteViews(context, state, expanded = true)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return RemoteViews(
                    mapOf(
                        SizeF(180f, 48f) to compactViews,
                        SizeF(180f, EXPANDED_MIN_HEIGHT_DP.toFloat()) to expandedViews
                    )
                )
            }

            val minHeightDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0
            return if (resolveUseExpandedLayout(minHeightDp)) expandedViews else compactViews
        }

        private fun createSingleRemoteViews(
            context: Context,
            state: PhotoSearchWidgetState,
            expanded: Boolean
        ): RemoteViews {
            val layoutId = if (expanded) {
                R.layout.widget_photo_search_expanded
            } else {
                R.layout.widget_photo_search_compact
            }
            return RemoteViews(context.packageName, layoutId).apply {
                setViewVisibility(R.id.widget_pro_badge, if (state.isPro) View.VISIBLE else View.GONE)
                setViewVisibility(R.id.widget_vault_lock_icon, if (state.isVaultEnabled) View.VISIBLE else View.GONE)

                val searchPendingIntent = createSearchPendingIntent(context)
                setOnClickPendingIntent(R.id.widget_search_row, searchPendingIntent)
                if (!expanded) {
                    setOnClickPendingIntent(R.id.widget_root, searchPendingIntent)
                } else {
                    setTextViewText(
                        R.id.widget_chip_address,
                        formatChipLabel(context.getString(R.string.widget_chip_address), state.addressCount)
                    )
                    setTextViewText(
                        R.id.widget_chip_payment,
                        formatChipLabel(context.getString(R.string.widget_chip_payment), state.paymentCount)
                    )
                    setTextViewText(
                        R.id.widget_chip_contact,
                        formatChipLabel(context.getString(R.string.widget_chip_contact), state.contactCount)
                    )

                    setOnClickPendingIntent(
                        R.id.widget_chip_address,
                        createActionCardPendingIntent(context, ActionCardType.ADDRESS, REQ_CODE_ADDRESS)
                    )
                    setOnClickPendingIntent(
                        R.id.widget_chip_payment,
                        createActionCardPendingIntent(context, ActionCardType.PAYMENT, REQ_CODE_PAYMENT)
                    )
                    setOnClickPendingIntent(
                        R.id.widget_chip_contact,
                        createActionCardPendingIntent(context, ActionCardType.CONTACT, REQ_CODE_CONTACT)
                    )
                }
            }
        }

        private fun createSearchPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, QuickSearchActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return PendingIntent.getActivity(context, REQ_CODE_SEARCH, intent, flags)
        }

        private fun createActionCardPendingIntent(
            context: Context,
            type: ActionCardType,
            requestCode: Int
        ): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_ACTION_CARD_TYPE, type.name)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return PendingIntent.getActivity(context, requestCode, intent, flags)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val state = loadWidgetState(context)
                for (widgetId in appWidgetIds) {
                    val options = appWidgetManager.getAppWidgetOptions(widgetId)
                    val views = buildRemoteViews(context, state, options)
                    appWidgetManager.updateAppWidget(widgetId, views)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val state = loadWidgetState(context)
                val views = buildRemoteViews(context, state, newOptions)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

object PhotoSearchWidgetUpdater {
    fun updateAll(context: Context) {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
            val componentName = ComponentName(context, PhotoSearchWidgetProvider::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (widgetIds.isEmpty()) return

            val state = PhotoSearchWidgetProvider.loadWidgetState(context)
            for (widgetId in widgetIds) {
                val options = appWidgetManager.getAppWidgetOptions(widgetId)
                val views = PhotoSearchWidgetProvider.buildRemoteViews(context, state, options)
                appWidgetManager.updateAppWidget(widgetId, views)
            }
        } catch (_: Exception) {
        }
    }

    fun isRequestPinSupported(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return try {
            AppWidgetManager.getInstance(context)?.isRequestPinAppWidgetSupported == true
        } catch (_: Exception) {
            false
        }
    }

    fun requestPinWidget(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return try {
            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return false
            if (!appWidgetManager.isRequestPinAppWidgetSupported) return false
            val provider = ComponentName(context, PhotoSearchWidgetProvider::class.java)
            appWidgetManager.requestPinAppWidget(provider, null, null)
        } catch (_: Exception) {
            false
        }
    }
}

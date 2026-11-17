package com.example.test2.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.CalendarContract
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.test2.MainActivity

object ScreenshotNotificationHelper {
    const val CHANNEL_ID = "screenshot_schedule_channel"
    const val NOTIFICATION_ID = 1001

    const val CHANNEL_AGING_ID = "screenshot_aging_channel"
    const val NOTIFICATION_AGING_ID = 1002

    const val EXTRA_PHOTO_ID = "extra_photo_id"
    const val EXTRA_ACTION_SCHEDULE = "extra_action_schedule"
    const val EXTRA_TAB = "extra_tab"
    const val EXTRA_OPEN_HOUSEKEEPING = "extra_open_housekeeping"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Lên lịch sự kiện ảnh chụp màn hình",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Thông báo khi chụp màn hình chứa lịch trình để gợi ý lên lịch sự kiện"
                enableVibration(true)
                setShowBadge(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun showScreenshotNotification(
        context: Context,
        photoId: String,
        photoTitle: String,
        intent: String? = "EVENT",
        eventTitle: String? = null,
        eventTimeMillis: Long? = null,
        eventDescription: String? = null
    ) {
        // User requirement: ONLY notify for genuine EVENT or SHOPPING
        if (intent != "EVENT" && intent != "SHOPPING") {
            return
        }
        if (eventTitle.isNullOrBlank() && photoTitle.isBlank()) {
            return
        }

        createNotificationChannel(context)

        val cleanTitle = if (!eventTitle.isNullOrBlank()) eventTitle else photoTitle
        val isShopping = intent == "SHOPPING"

        // Default time: Shopping is +24 hours; Event is +1 hour (if no exact time parsed)
        // User can freely adjust the date/time before saving in the Calendar app
        val scheduleTime = eventTimeMillis ?: if (isShopping) {
            System.currentTimeMillis() + 24 * 3600_000L
        } else {
            System.currentTimeMillis() + 3600_000L
        }

        val notificationTitle = if (isShopping) "Gợi ý mua sắm & chi tiêu" else "Phát hiện sự kiện & lịch hẹn"
        val messageText = if (isShopping) {
            "Bạn có muốn đặt lịch mua cho \"$cleanTitle\" không?"
        } else {
            "Bạn có muốn đặt lịch hẹn cho \"$cleanTitle\" không?"
        }
        val actionText = if (isShopping) "Lên lịch mua" else "Lên lịch hẹn"
        val calendarEventTitle = if (isShopping) {
            if (cleanTitle.startsWith("Mua sắm:", ignoreCase = true)) cleanTitle else "Mua sắm: $cleanTitle"
        } else {
            cleanTitle
        }

        val resolvedDescription = eventDescription ?: com.example.test2.ai.SmartHeuristicParser.generateStructuredDescription(
            text = cleanTitle,
            intent = intent,
            title = calendarEventTitle
        )

        // 1. Direct Calendar intent for "Schedule Event" or "Schedule Purchase"
        // Pre-populates the Calendar app, allowing the user to adjust date, time, reminders before saving!
        val calendarIntent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, calendarEventTitle)
            putExtra(CalendarContract.Events.DESCRIPTION, resolvedDescription)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, scheduleTime)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, scheduleTime + 3600_000L)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val calendarPendingIntent = PendingIntent.getActivity(
            context,
            photoId.hashCode(),
            calendarIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. Open App Intent (tapping notification or "View Photo")
        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_PHOTO_ID, photoId)
        }
        val appPendingIntent = PendingIntent.getActivity(
            context,
            (photoId + "_app").hashCode(),
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(if (isShopping) android.R.drawable.ic_menu_agenda else android.R.drawable.ic_menu_camera)
            .setContentTitle(notificationTitle)
            .setContentText(messageText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(appPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_agenda,
                actionText,
                calendarPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_view,
                "Xem ảnh",
                appPendingIntent
            )

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // Notifications permission not granted yet
        }
    }

    fun createAgingNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_AGING_ID,
                "Dọn dẹp ảnh chụp màn hình & Rác bộ nhớ",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Thông báo định kỳ nhắc nhở dọn dẹp ảnh chụp màn hình cũ"
                enableVibration(false)
                setShowBadge(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun showAgingNotification(
        context: Context,
        clutterCount: Int,
        reclaimableBytes: Long
    ) {
        createAgingNotificationChannel(context)

        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_HOUSEKEEPING, true)
            putExtra(EXTRA_TAB, "housekeeping")
        }
        val appPendingIntent = PendingIntent.getActivity(
            context,
            20271,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sizeFormatted = formatFileSize(reclaimableBytes)
        val messageText = "Bạn có $clutterCount ảnh chụp màn hình cũ (~$sizeFormatted) sẵn sàng để dọn dẹp. Chạm để xem và dọn rác."

        val builder = NotificationCompat.Builder(context, CHANNEL_AGING_ID)
            .setSmallIcon(android.R.drawable.ic_menu_delete)
            .setContentTitle("Dọn dẹp ảnh chụp màn hình cũ")
            .setContentText("Bạn có $clutterCount ảnh chụp màn hình cũ (~$sizeFormatted) sẵn sàng để dọn dẹp.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(appPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_delete,
                "Xem & Dọn dẹp",
                appPendingIntent
            )

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_AGING_ID, builder.build())
        } catch (_: SecurityException) {
        }
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "0 KB"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1.0) {
            String.format(java.util.Locale.US, "%.1f MB", mb)
        } else {
            String.format(java.util.Locale.US, "%.0f KB", kb)
        }
    }
}

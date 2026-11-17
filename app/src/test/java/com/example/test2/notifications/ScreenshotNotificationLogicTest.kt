package com.example.test2.notifications

import com.example.test2.database.ScreenshotQueueItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotNotificationLogicTest {

    private fun evaluateShouldNotify(
        item: ScreenshotQueueItem,
        detectedIntent: String?,
        suggestedEventTitle: String?
    ): Boolean {
        return item.isLiveCapture &&
                (detectedIntent == "EVENT" || detectedIntent == "SHOPPING") &&
                !suggestedEventTitle.isNullOrBlank()
    }

    @Test
    fun testInitialScan_EventDetected_NeverNotifies() {
        val initialScanItem = ScreenshotQueueItem(
            id = 1L,
            photoId = "device_1001",
            filePath = "content://media/external/images/media/1001",
            dateAdded = System.currentTimeMillis() - 86400000L, // 1 ngày trước
            status = "PROCESSING",
            isLiveCapture = false // Quét khởi tạo
        )

        val shouldNotify = evaluateShouldNotify(
            item = initialScanItem,
            detectedIntent = "EVENT",
            suggestedEventTitle = "Lịch chiếu phim Lotte Cinema"
        )

        assertFalse("Quét khởi tạo không bao giờ được bắn thông báo", shouldNotify)
    }

    @Test
    fun testInitialScan_ShoppingDetected_NeverNotifies() {
        val initialScanItem = ScreenshotQueueItem(
            id = 2L,
            photoId = "device_1002",
            filePath = "content://media/external/images/media/1002",
            dateAdded = System.currentTimeMillis() - 3600000L * 5, // 5 giờ trước
            status = "PROCESSING",
            isLiveCapture = false // Quét khởi tạo
        )

        val shouldNotify = evaluateShouldNotify(
            item = initialScanItem,
            detectedIntent = "SHOPPING",
            suggestedEventTitle = "Nồi cơm điện Bluestone"
        )

        assertFalse("Quét khởi tạo phát hiện đồ mua không được bắn thông báo", shouldNotify)
    }

    @Test
    fun testLiveCapture_EventDetected_TriggersNotification() {
        val liveCaptureItem = ScreenshotQueueItem(
            id = 3L,
            photoId = "device_1003",
            filePath = "content://media/external/images/media/1003",
            dateAdded = System.currentTimeMillis() - 5000L, // Vừa chụp 5 giây trước
            status = "PROCESSING",
            isLiveCapture = true // Chụp mới thời gian thực
        )

        val shouldNotify = evaluateShouldNotify(
            item = liveCaptureItem,
            detectedIntent = "EVENT",
            suggestedEventTitle = "Lịch phỏng vấn Google"
        )

        assertTrue("Ảnh chụp mới phát hiện sự kiện phải bắn thông báo gợi ý", shouldNotify)
    }

    @Test
    fun testLiveCapture_ShoppingDetected_TriggersNotification() {
        val liveCaptureItem = ScreenshotQueueItem(
            id = 4L,
            photoId = "device_1004",
            filePath = "content://media/external/images/media/1004",
            dateAdded = System.currentTimeMillis() - 10000L, // Vừa chụp 10 giây trước
            status = "PROCESSING",
            isLiveCapture = true // Chụp mới thời gian thực
        )

        val shouldNotify = evaluateShouldNotify(
            item = liveCaptureItem,
            detectedIntent = "SHOPPING",
            suggestedEventTitle = "Bàn phím cơ Keychron K2"
        )

        assertTrue("Ảnh chụp mới phát hiện mua sắm phải bắn thông báo gợi ý", shouldNotify)
    }

    @Test
    fun testLiveCapture_OtherIntent_NeverNotifies() {
        val liveCaptureItem = ScreenshotQueueItem(
            id = 5L,
            photoId = "device_1005",
            filePath = "content://media/external/images/media/1005",
            dateAdded = System.currentTimeMillis() - 2000L,
            status = "PROCESSING",
            isLiveCapture = true
        )

        val shouldNotify = evaluateShouldNotify(
            item = liveCaptureItem,
            detectedIntent = "OTHER",
            suggestedEventTitle = "Tài liệu học tập"
        )

        assertFalse("Ảnh chụp mới nhưng không phải EVENT hay SHOPPING thì không bắn thông báo", shouldNotify)
    }

    @Test
    fun testLiveCapture_BlankEventTitle_NeverNotifies() {
        val liveCaptureItem = ScreenshotQueueItem(
            id = 6L,
            photoId = "device_1006",
            filePath = "content://media/external/images/media/1006",
            dateAdded = System.currentTimeMillis() - 2000L,
            status = "PROCESSING",
            isLiveCapture = true
        )

        val shouldNotify = evaluateShouldNotify(
            item = liveCaptureItem,
            detectedIntent = "EVENT",
            suggestedEventTitle = "   "
        )

        assertFalse("Tiêu đề sự kiện rỗng không được bắn thông báo", shouldNotify)
    }
}

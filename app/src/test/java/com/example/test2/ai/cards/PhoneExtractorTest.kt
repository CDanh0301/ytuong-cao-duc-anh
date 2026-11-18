package com.example.test2.ai.cards

import com.example.test2.ai.LocalActionCardExtractor
import com.example.test2.shared.models.ActionCardType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneExtractorTest {

    @Test
    fun testT01_MultiplePhoneFormats() {
        val ocrText = """
            Danh sách liên hệ:
            Di động 1: 0912 345 678
            Di động 2: (+84) 912 345 679
            Di động 3: +84 912 345 680
            Cố định: (028) 3822 1234
            Số phụ: 0912 34 56 81
        """.trimIndent()

        val cards = LocalActionCardExtractor.extractCardsFromText(
            photoId = "photo_t01",
            rawOcrText = ocrText
        )

        val contactCards = cards.filter { it.type == ActionCardType.CONTACT }
        assertTrue("Should extract multiple contact cards", contactCards.size >= 4)

        val phones = contactCards.map {
            JSONObject(it.payloadJson).getString("phoneNumber")
        }

        assertTrue("Should extract 0912345678", phones.any { it.contains("0912345678") || it.contains("912345678") })
        assertTrue("Should extract 02838221234", phones.any { it.contains("02838221234") })
    }

    @Test
    fun testT02_HotlineFullDigitsNotCut() {
        val ocrText = """
            Tổng đài hỗ trợ Sacombank
            Hotline: 1900 54 54 13
            Hotline VIP: 1800 58 88 22
            Hotline 8 số: 19001234
        """.trimIndent()

        val cards = LocalActionCardExtractor.extractCardsFromText(
            photoId = "photo_t02",
            rawOcrText = ocrText
        )

        val contactCards = cards.filter { it.type == ActionCardType.CONTACT }
        assertTrue(contactCards.isNotEmpty())

        val phones = contactCards.map {
            JSONObject(it.payloadJson).getString("phoneNumber")
        }

        // Must preserve full 10 digits for 1900 54 54 13 and not cut to 1900 5454
        assertTrue("Hotline 10 digits must be preserved", phones.contains("1900545413"))
        assertTrue("Hotline 1800 10 digits must be preserved", phones.contains("1800588822"))
        assertTrue("Hotline 8 digits must be preserved", phones.contains("19001234"))
    }

    @Test
    fun testT03_ExcludesAccountTrackingOrderDatesAndCurrency() {
        val ocrText = """
            Chi tiết đơn hàng
            STK 1020304050607
            mã vận đơn 0211234567890
            Ngày đặt hàng: 20261225
            Tổng tiền thanh toán: 1.900.000đ
        """.trimIndent()

        val cards = LocalActionCardExtractor.extractCardsFromText(
            photoId = "photo_t03",
            rawOcrText = ocrText
        )

        val contactCards = cards.filter { it.type == ActionCardType.CONTACT }
        assertTrue("Should NOT extract phone numbers from bank account, order code, year, or currency", contactCards.isEmpty())
    }

    @Test
    fun testT04_RejectsSlogansItemsAndChromeNoise() {
        val ocrText = """
            Miễn phí vận chuyển items 24 bài viết
            chrome book giá rẻ
            Hotline: 0912345678
        """.trimIndent()

        val cards = LocalActionCardExtractor.extractCardsFromText(
            photoId = "photo_t04",
            rawOcrText = ocrText
        )

        val contactCard = cards.find { it.type == ActionCardType.CONTACT }
        assertNotNull(contactCard)
        contactCard!!

        val payload = JSONObject(contactCard.payloadJson)
        val name = payload.getString("name")

        assertTrue("Name must NOT contain 'vận chuyển'", !name.contains("vận chuyển"))
        assertTrue("Name must NOT contain 'bài viết'", !name.contains("bài viết"))
        assertTrue("Name must NOT contain 'chrome'", !name.contains("chrome"))
        assertTrue("Name should be empty or valid", name.isBlank())
    }

    @Test
    fun testT05_HotlineFunctionalLabelsNotPersonName() {
        val ocrText = """
            Bệnh viện Quân Y
            Hotline cấp cứu: 024 3837 3747
        """.trimIndent()

        val cards = LocalActionCardExtractor.extractCardsFromText(
            photoId = "photo_t05",
            rawOcrText = ocrText
        )

        val contactCard = cards.find { it.type == ActionCardType.CONTACT }
        assertNotNull(contactCard)
        contactCard!!

        val payload = JSONObject(contactCard.payloadJson)
        val name = payload.getString("name")

        assertTrue("Functional labels like 'Hotline cấp cứu' must not be person names", name.isBlank() || !name.contains("cấp cứu"))
        assertEquals("02438373747", payload.getString("phoneNumber"))
    }
}

package com.example.test2.ai.cards

import com.example.test2.ai.LocalActionCardExtractor
import com.example.test2.shared.models.ActionCardType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressExtractorTest {

    @Test
    fun testT07_NewAndLegacyAdminUnits() {
        // Test Quy Nhơn / Gia Lai
        val ocrGiaLai = """
            Khách sạn Hoàng Yến
            Số 5 đường An Dương Vương, Phường Nguyễn Văn Cừ, Quy Nhơn
        """.trimIndent()
        val cardsGiaLai = LocalActionCardExtractor.extractCardsFromText("p_gl", ocrGiaLai)
        val addrGiaLai = cardsGiaLai.find { it.type == ActionCardType.ADDRESS }
        assertNotNull(addrGiaLai)
        val jsonGl = JSONObject(addrGiaLai!!.payloadJson)
        assertTrue(jsonGl.getString("city").isNotBlank())

        // Test Vũng Tàu / TP. Hồ Chí Minh
        val ocrVt = """
            Nhà hàng Gành Hào
            Số 03 Trần Phú, Phường 5, TP. Vũng Tàu
        """.trimIndent()
        val cardsVt = LocalActionCardExtractor.extractCardsFromText("p_vt", ocrVt)
        val addrVt = cardsVt.find { it.type == ActionCardType.ADDRESS }
        assertNotNull(addrVt)
        val jsonVt = JSONObject(addrVt!!.payloadJson)
        assertTrue(jsonVt.getString("city").isNotBlank())

        // Test New Administrative form without district: Phường Bến Thành, TP. Hồ Chí Minh
        val ocrNewAdmin = """
            Chi nhánh trung tâm
            Địa chỉ: Số 10 đường Lê Thánh Tôn, Phường Bến Thành, TP. Hồ Chí Minh
        """.trimIndent()
        val cardsNew = LocalActionCardExtractor.extractCardsFromText("p_new", ocrNewAdmin)
        val addrNew = cardsNew.find { it.type == ActionCardType.ADDRESS }
        assertNotNull(addrNew)
        val jsonNew = JSONObject(addrNew!!.payloadJson)
        assertTrue(jsonNew.getString("city").contains("Hồ Chí Minh"))
        assertEquals("current", jsonNew.getString("adminSystem"))
    }

    @Test
    fun testT08_RejectsSocialMediaMetricsNoise() {
        val ocrText = """
            Cộng đồng Lập trình viên Android Việt Nam
            24 bài viết trong nhóm hôm nay
            100k thành viên đang hoạt động
            Đăng bởi Nguyễn Văn A • 15 phút trước
            Thảo luận về Jetpack Compose và Kotlin 2.0
        """.trimIndent()

        val cards = LocalActionCardExtractor.extractCardsFromText(
            photoId = "photo_t08",
            rawOcrText = ocrText
        )

        val addrCard = cards.find { it.type == ActionCardType.ADDRESS }
        assertNull("Social media metrics must NEVER generate an ADDRESS card", addrCard)
    }

    @Test
    fun testT09_RejectsPhotoTitleFallbackAndClosedStateNoise() {
        val ocrText = """
            Hôm nay quán nghỉ phục vụ do bảo trì điện lưới
            Xin lỗi quý khách vì sự bất tiện này
            Hẹn gặp lại quý khách vào ngày mai
            
            Địa chỉ cơ sở: Số 88 đường Láng, Phường Thịnh Quang, Quận Đống Đa, Hà Nội
        """.trimIndent()

        val cards = LocalActionCardExtractor.extractCardsFromText(
            photoId = "photo_t09",
            rawOcrText = ocrText,
            photoTitle = "Screenshot_20261118_FB_POST_RESTAURANT"
        )

        val addrCard = cards.find { it.type == ActionCardType.ADDRESS }
        assertNotNull(addrCard)
        addrCard!!

        val json = JSONObject(addrCard.payloadJson)
        val placeName = json.getString("placeName")

        assertTrue("Place name must not fall back to photo title", !addrCard.title.contains("Screenshot_20261118"))
        assertTrue("Place name must not contain 'quán nghỉ'", !placeName.contains("quán nghỉ") && !placeName.contains("nghỉ phục vụ"))
    }

    @Test
    fun testT10_RobustAddressExtraction() {
        val ocrText = """
            Quan An Ngon
            Dia chi: So 18 pho Phan Boi Chau, Phuong Cua Nam, Quan Hoan Kiem, Ha Noi
        """.trimIndent()

        val cards = LocalActionCardExtractor.extractCardsFromText(
            photoId = "photo_t10",
            rawOcrText = ocrText
        )

        val addrCard = cards.find { it.type == ActionCardType.ADDRESS }
        assertNotNull(addrCard)
        val json = JSONObject(addrCard!!.payloadJson)
        assertEquals("Hà Nội", json.getString("city"))
    }
}

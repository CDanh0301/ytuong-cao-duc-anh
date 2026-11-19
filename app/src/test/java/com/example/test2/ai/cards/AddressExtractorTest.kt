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

    @Test
    fun testT11_LeMondeSteakVincomBaTrieuAddress() {
        val ocrText = """
            9:23
            google.com/search?client=...
            Le Monde Steak - Vincom Bà Triệu
            4,6 (799) · Bít tết · Mở cửa
            Tổng quan   Bài đánh giá   Ảnh
            Gọi điện   Đường đi   Chia sẻ   Trang web   Lưu
            Đặt qua mạng
            Đang mở cửa · Đóng cửa vào 22:00
            Vincom Center, GH - L5 - 01, Tòa A,
            Bà Triệu, Hai Bà Trưng, Hà Nội
            100000
            4,6 ★★★★★ 799 Bài đánh giá
            Xem thực đơn
            100.000-600.000 đ/người
        """.trimIndent()

        val cards = LocalActionCardExtractor.extractCardsFromText(
            photoId = "photo_lemonde",
            rawOcrText = ocrText,
            photoTitle = "Le Monde Steak"
        )

        val addrCard = cards.find { it.type == ActionCardType.ADDRESS }
        assertNotNull("Should extract address from Vincom Ba Trieu", addrCard)
        addrCard!!

        val json = JSONObject(addrCard.payloadJson)
        val fullAddr = json.getString("fullAddress")
        assertTrue("Must contain Vincom Center", fullAddr.contains("Vincom Center"))
        assertTrue("Must contain Tòa A", fullAddr.contains("Tòa A") || fullAddr.contains("GH - L5"))
        assertTrue("Must contain Bà Triệu", fullAddr.contains("Bà Triệu"))
        assertTrue("Must contain Hai Bà Trưng", fullAddr.contains("Hai Bà Trưng"))
        assertTrue("Must contain Hà Nội", fullAddr.contains("Hà Nội"))
        assertTrue("Must not contain zip code at end", !fullAddr.endsWith("100000"))
        assertEquals("Hà Nội", json.getString("city"))
    }

    @Test
    fun testT12_MooBeefSteakTranQuocToanAddress() {
        val ocrText = """
            9:24
            MOO BEEF STEAK TRẦN QUỐC TOẢN
            4,9 (6 N) · Nhà hàng · Mở cửa
            Tổng quan   Bài đánh giá   Ảnh
            Gọi điện   Đường đi   Chia sẻ   Trang web   Lưu
            Đặt bàn
            Phòng ăn riêng tư · Thực đơn dành cho trẻ em · Wi-Fi
            Đang mở cửa · Đóng cửa vào 14:00 · Mở lại vào 17:30
            59A P. Trần Quốc Toản, P, Cửa Nam,
            Hà Nội
            Ga Hà Nội
            4,9 ★★★★★ 5.983 Bài đánh giá
            Xem thực đơn
            0967 975 086
        """.trimIndent()

        val cards = LocalActionCardExtractor.extractCardsFromText(
            photoId = "photo_moobeef",
            rawOcrText = ocrText,
            photoTitle = "Moo Beef Steak"
        )

        val addrCard = cards.find { it.type == ActionCardType.ADDRESS }
        assertNotNull("Should extract address for Moo Beef Steak", addrCard)
        addrCard!!

        val json = JSONObject(addrCard.payloadJson)
        val fullAddr = json.getString("fullAddress")
        assertTrue("Must contain 59A P. Trần Quốc Toản", fullAddr.contains("59A P. Trần Quốc Toản"))
        assertTrue("Must contain Cửa Nam", fullAddr.contains("Cửa Nam"))
        assertEquals("Hà Nội", json.getString("city"))

        val contactCard = cards.find { it.type == ActionCardType.CONTACT }
        assertNotNull("Should extract contact phone", contactCard)
        val contactJson = JSONObject(contactCard!!.payloadJson)
        assertEquals("0967975086", contactJson.getString("phoneNumber"))
    }

    @Test
    fun testT13_SushiVietBachMaiLineWrappedAddress() {
        val ocrText = """
            10:21
            google.com/search?client=...
            Sushi Việt Gỏi Cuốn Việt
            4,5 (602) · Món ăn xuyên Á · Mở cửa
            Tổng quan   Bài đánh giá   Ảnh
            Gọi điện   Đường đi   Chia sẻ   Lưu
            Đang mở cửa · Đóng cửa vào 22:00
            151 Trần Đại Nghĩa, Bách Mai, Hà
            Nội
            4,5 ★★★★★ 602 Bài đánh giá
            Xem thực đơn
            100.000-200.000 đ/người
            0986 657 395
        """.trimIndent()

        val cards = LocalActionCardExtractor.extractCardsFromText(
            photoId = "photo_sushiviet",
            rawOcrText = ocrText,
            photoTitle = "Sushi Việt"
        )

        val addrCard = cards.find { it.type == ActionCardType.ADDRESS }
        assertNotNull("Should extract line-wrapped address for Sushi Viet", addrCard)
        addrCard!!

        val json = JSONObject(addrCard.payloadJson)
        val fullAddr = json.getString("fullAddress")
        assertTrue("Must contain 151 Trần Đại Nghĩa", fullAddr.contains("151 Trần Đại Nghĩa"))
        assertTrue("Must contain Bách Mai", fullAddr.contains("Bách Mai"))
        assertTrue("Must reconstruct Hà Nội without comma break", fullAddr.contains("Hà Nội"))
        assertEquals("Hà Nội", json.getString("city"))

        val contactCard = cards.find { it.type == ActionCardType.CONTACT }
        assertNotNull("Should extract phone", contactCard)
        val contactJson = JSONObject(contactCard!!.payloadJson)
        assertEquals("0986657395", contactJson.getString("phoneNumber"))
    }
}

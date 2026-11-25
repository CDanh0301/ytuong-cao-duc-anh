package com.example.test2.ai.cards

import com.example.test2.ai.LocalActionCardExtractor
import com.example.test2.ai.OcrLine
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
        assertTrue("Must NOT contain review count", !fullAddr.contains("Bài đánh giá") && !fullAddr.contains("5.983"))
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

    @Test
    fun testT14_FestivalEventList_StripsContentDescription() {
        val ocrText = """
            Festival Bảo tồn và Phát triển làng nghề toàn cầu lần thứ nhất
            o Thời gian: 13/11/2026 - 16/11/2026
            Địa điểm: Hoàng Thành Thăng Long (19C Hoàng Diệu, Ba Đinh, Hà Nội)
            Nội dung: Quy tụ nghệ nhân từ hơn 40 quốc gia với các hoạt động trưng bày
        """.trimIndent()

        val cards = LocalActionCardExtractor.extractCardsFromText("p_festival", ocrText)
        val addrCard = cards.find { it.type == ActionCardType.ADDRESS }
        assertNotNull("Should extract address for Hoang Thanh Thang Long", addrCard)
        addrCard!!

        val fullAddr = JSONObject(addrCard.payloadJson).getString("fullAddress")
        assertTrue("Must contain Hoang Thanh Thang Long", fullAddr.contains("Hoàng Thành Thăng Long"))
        assertTrue("Must contain Ba Dinh", fullAddr.contains("Ba Đinh") || fullAddr.contains("Ba Đình"))
        assertTrue("Must contain Ha Noi", fullAddr.contains("Hà Nội"))
        assertTrue("Must NOT contain Noi dung line", !fullAddr.contains("Nội dung") && !fullAddr.contains("Quy tụ"))
    }

    @Test
    fun testT15_FacebookPost_ExtractsMyDinhStripsLogInAndDates() {
        val ocrText = """
            facebook
            CTicket
            6d
            🔥 LỊCH TRÌNH PHÁ ĐẢO SỰ KIỆN LỚN NHẤT THẬP KỶ
            Săn ngay tấm vé tham gia Liên Quân 10 Fest
            Thời gian & Địa điểm sự kiện: Sân vận động Quốc gia Mỹ
            Đình, TP. Hà Nội | 07 - 08.11.2026
            Log In
            Open app
            Sự kiện cả thập kỷ mới có một lần
        """.trimIndent()

        val cards = LocalActionCardExtractor.extractCardsFromText("p_fb_lienquan", ocrText)
        val addrCard = cards.find { it.type == ActionCardType.ADDRESS }
        assertNotNull("Should extract address for My Dinh stadium", addrCard)
        addrCard!!

        val fullAddr = JSONObject(addrCard.payloadJson).getString("fullAddress")
        assertTrue("Must capture San van dong Quoc gia My Dinh", fullAddr.contains("Sân vận động Quốc gia Mỹ") && fullAddr.contains("Đình"))
        assertTrue("Must contain Ha Noi", fullAddr.contains("Hà Nội"))
        assertTrue("Must NOT contain Log In", !fullAddr.contains("Log In") && !fullAddr.contains("Open app"))
        assertTrue("Must NOT contain dates", !fullAddr.contains("07 - 08.11.2026"))
    }

    @Test
    fun testT16_TungsKitchen_StripsTrangWebAndZip() {
        val ocrText = """
            Tung's Kitchen Vietnamese Cuisine & Vegetarian Food
            4,9 ★ (22,2 N) Món ăn Việt Nam Mở cửa
            Tổng quan   Menu   Bài đánh giá   Ảnh
            Gọi điện   Đường đi   Chia sẻ   Trang web   Lưu
            Đang mở cửa · Đóng cửa vào 22:30
            2 P. Hàng Bông, Phố cổ Hà Nội, Hoàn Kiếm, Hà Nội 100000
            Trang web
            22.199 Bài đánh giá
        """.trimIndent()

        val cards = LocalActionCardExtractor.extractCardsFromText("p_tungs", ocrText)
        val addrCard = cards.find { it.type == ActionCardType.ADDRESS }
        assertNotNull("Should extract address for Tung's Kitchen", addrCard)
        addrCard!!

        val fullAddr = JSONObject(addrCard.payloadJson).getString("fullAddress")
        assertTrue("Must contain 2 P. Hàng Bông", fullAddr.contains("2 P. Hàng Bông"))
        assertTrue("Must contain Hoàn Kiếm", fullAddr.contains("Hoàn Kiếm"))
        assertTrue("Must contain Hà Nội", fullAddr.contains("Hà Nội"))
        assertTrue("Must NOT contain Trang web", !fullAddr.contains("Trang web"))
        assertTrue("Must NOT end with zip 100000", !fullAddr.endsWith("100000"))
    }

    @Test
    fun testT17_PoiCongVienThanhNienCaMau() {
        val ocrText = """
            Viettel Y-Fest 2026 (Cà Mau)
            Thời gian: 14/11/2026
            Địa điểm: Công viên Thanh Niên (phường Tân Thành, tỉnh Cà Mau)
            Nội dung: Nhạc hội kết hợp công nghệ số
        """.trimIndent()

        val cards = LocalActionCardExtractor.extractCardsFromText("p_yfest", ocrText)
        val addrCard = cards.find { it.type == ActionCardType.ADDRESS }
        assertNotNull("Should extract POI address for Cong vien Thanh Nien", addrCard)
        addrCard!!

        val json = JSONObject(addrCard.payloadJson)
        val fullAddr = json.getString("fullAddress")
        assertTrue("Must contain Cong vien Thanh Nien", fullAddr.contains("Công viên Thanh Niên"))
        assertTrue("Must contain Ca Mau", fullAddr.contains("Cà Mau"))
        assertTrue("Must NOT contain Noi dung", !fullAddr.contains("Nội dung"))
        assertEquals("Cà Mau", json.getString("city"))
    }

    @Test
    fun testT18_PoiSanVanDongMyDinhHaNoi() {
        val ocrText = """
            Ngày Văn hóa Việt Nam (Sân vận động Mỹ Đình)
            Thời gian: 24/11/2026 (18:00 - 23:00)
            Địa điểm: Sân vận động Quốc gia Mỹ Đinh (Hà Nội)
            Nội dung: Chương trình nghệ thuật
        """.trimIndent()

        val cards = LocalActionCardExtractor.extractCardsFromText("p_mydinh", ocrText)
        val addrCard = cards.find { it.type == ActionCardType.ADDRESS }
        assertNotNull("Should extract POI address for San van dong My Dinh", addrCard)
        addrCard!!

        val json = JSONObject(addrCard.payloadJson)
        val fullAddr = json.getString("fullAddress")
        assertTrue("Must contain San van dong Quoc gia My Dinh", fullAddr.contains("Sân vận động Quốc gia Mỹ Đinh"))
        assertTrue("Must contain Ha Noi", fullAddr.contains("Hà Nội"))
        assertTrue("Must NOT contain Noi dung", !fullAddr.contains("Nội dung"))
        assertEquals("Hà Nội", json.getString("city"))
    }

    @Test
    fun testT19_BenhVien198_StripsWebsiteAndHotline() {
        val ocrText = """
            BỆNH VIỆN 19-8
            LỊCH KHÁM BỆNH THEO YÊU CẦU
            Địa chi Số 9 Trán Binh, phuäng Tü iêm, TP, Hà Nội - Website: www.benhvien19-8.vn
            khách có nhu cáu đặt lịch khám, Xin vui lòng liên hệ qua Tống đài: 0243.8373747
        """.trimIndent()

        val cards = LocalActionCardExtractor.extractCardsFromText("p_bv198", ocrText)
        val addrCard = cards.find { it.type == ActionCardType.ADDRESS }
        assertNotNull("Should extract address for Benh vien 19-8", addrCard)
        addrCard!!

        val fullAddr = JSONObject(addrCard.payloadJson).getString("fullAddress")
        assertTrue("Must contain So 9 Tran Binh", fullAddr.contains("Số 9 Trán Binh"))
        assertTrue("Must contain Ha Noi", fullAddr.contains("Hà Nội"))
        assertTrue("Must NOT contain Website", !fullAddr.contains("Website") && !fullAddr.contains("benhvien19-8"))
        assertTrue("Must NOT contain Hotline text", !fullAddr.contains("Tống đài") && !fullAddr.contains("0243"))
    }

    @Test
    fun testT20_RejectsBenhVienNhiDongKhuPhongA7_FalsePositive() {
        val ocrText = """
            BỆNH VIỆN
            NHIĐỒNG THÀNH PHỐ
            KHU
            PHÒNG
            A7
        """.trimIndent()

        val cards = LocalActionCardExtractor.extractCardsFromText("p_bvnhi_noise", ocrText)
        val addrCard = cards.find { it.type == ActionCardType.ADDRESS }
        assertNull("Must NOT extract false positive 'Phố Khu' as street from hospital department table", addrCard)
    }

    @Test
    fun testT21_MultiAddress_FestivalImage_ExtractsAllThreeVenues() {
        val ocrText = """
            2:18
            G
            • Festival Bảo tồn và Phát triển làng nghề toàn cầu lần thứ nhất
              o Thời gian: 13/11/2026 - 16/11/2026
              o Địa điểm: Hoàng Thành Thăng Long (19C Hoàng Diệu, Ba Đình, Hà Nội)
              o Nội dung: Quy tụ nghệ nhân từ hơn 40 quốc gia với các hoạt động trưng bày, triển lãm thủ công mỹ nghệ và trải nghiệm văn hóa di sản.
            • Viettel Y-Fest 2026 (Cà Mau)
              o Thời gian: 14/11/2026
              o Địa điểm: Công viên Thanh Niên (phường Tân Thành, tỉnh Cà Mau)
              o Nội dung: Nhạc hội kết hợp công nghệ số với sự tham gia của dàn nghệ sĩ RHYDER, Quân A.P, Bùi Công Nam, Orange, Tăng Phúc, Tiêu Minh Phụng... Viettel Family
            • Ngày Văn hóa Việt Nam (Sân vận động Mỹ Đình)
              o Thời gian: 24/11/2026 (18:00 - 23:00)
              o Địa điểm: Sân vận động Quốc gia Mỹ Đình (Hà Nội)
              o Nội dung: Chương trình nghệ thuật tôn vinh bản sắc truyền thống kết hợp màn bắn pháo hoa tầm cao.
            + Hỏi bất cứ điều gì
        """.trimIndent()

        val cards = LocalActionCardExtractor.extractCardsFromText("p_festival_all3", ocrText)
        val addressCards = cards.filter { it.type == ActionCardType.ADDRESS }

        assertEquals("Must extract exactly 3 address cards for 3 separate events", 3, addressCards.size)

        // Event 1: Hoang Thanh Thang Long
        val addr1 = addressCards[0]
        assertEquals("📍 Hoàng Thành Thăng Long", addr1.title)
        val payload1 = JSONObject(addr1.payloadJson)
        val fullAddr1 = payload1.getString("fullAddress")
        assertTrue("Event 1 must contain 19C Hoang Dieu", fullAddr1.contains("19C Hoàng Diệu"))
        assertTrue("Event 1 must contain Ba Dinh", fullAddr1.contains("Ba Đình"))
        assertTrue("Event 1 must contain Ha Noi", fullAddr1.contains("Hà Nội"))
        assertTrue("Event 1 must NOT contain Noi dung", !fullAddr1.contains("Nội dung"))

        // Event 2: Cong vien Thanh Nien Ca Mau
        val addr2 = addressCards[1]
        assertEquals("📍 Công viên Thanh Niên", addr2.title)
        val payload2 = JSONObject(addr2.payloadJson)
        val fullAddr2 = payload2.getString("fullAddress")
        assertTrue("Event 2 must contain Cong vien Thanh Nien", fullAddr2.contains("Công viên Thanh Niên"))
        assertTrue("Event 2 must contain Ca Mau", fullAddr2.contains("Cà Mau"))
        assertTrue("Event 2 must NOT contain Noi dung", !fullAddr2.contains("Nội dung"))

        // Event 3: San van dong Quoc gia My Dinh
        val addr3 = addressCards[2]
        assertEquals("📍 Sân vận động Quốc gia Mỹ Đình", addr3.title)
        val payload3 = JSONObject(addr3.payloadJson)
        val fullAddr3 = payload3.getString("fullAddress")
        assertTrue("Event 3 must contain San van dong Quoc gia My Dinh", fullAddr3.contains("Sân vận động Quốc gia Mỹ Đình"))
        assertTrue("Event 3 must contain Ha Noi", fullAddr3.contains("Hà Nội"))
        assertTrue("Event 3 must NOT contain Noi dung", !fullAddr3.contains("Nội dung"))
    }

    @Test
    fun testT22_MultiAddress_SingleAddressImage_YieldsExactlyOneCard() {
        val ocrText = """
            2:18
            MOO BEEF STEAK TRẦN QUỐC TOẢN
            4,9 ★ (6 N) · Nhà hàng · Mở cửa
            Tổng quan   Bài đánh giá   Ảnh
            Gọi điện   Đường đi   Chia sẻ   Trang web   Lưu
            59A P. Trần Quốc Toản, P, Cửa Nam, Hà Nội
            4,9 ★★★★★ 5.983 Bài đánh giá
            Xem thực đơn
            0967 975 086
        """.trimIndent()

        val cards = LocalActionCardExtractor.extractCardsFromText("p_moobeef", ocrText)
        val addressCards = cards.filter { it.type == ActionCardType.ADDRESS }

        assertEquals("Must extract exactly 1 address card for single restaurant photo", 1, addressCards.size)
        assertEquals("📍 59A P. Trần Quốc Toản, P, Cửa Nam, Hà Nội", addressCards[0].title)
    }

    @Test
    fun testT23_ExtractAddresses_WithOcrLines_DoesNotThrowIndexOutOfBoundsOnFirstLine() {
        val ocrLines = listOf(
            OcrLine(text = "Số 10 đường Lê Thánh Tôn", top = 0.2f, bottom = 0.25f, blockId = 1),
            OcrLine(text = "Phường Bến Thành, Quận 1, TP. Hồ Chí Minh", top = 0.26f, bottom = 0.3f, blockId = 1),
            OcrLine(text = "Hotline: 0901234567", top = 0.4f, bottom = 0.45f, blockId = 2)
        )
        val rawText = ocrLines.joinToString("\n") { it.text }

        // Must run smoothly without IndexOutOfBoundsException: Index -1 out of bounds
        val addresses = AddressExtractor.extractAddresses(rawText, ocrLines)
        assertEquals(1, addresses.size)
        assertTrue(addresses[0].fullAddress.contains("TP. Hồ Chí Minh"))
    }
}

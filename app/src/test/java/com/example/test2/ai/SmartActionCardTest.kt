package com.example.test2.ai

import com.example.test2.shared.models.ActionCardType
import com.example.test2.shared.models.Photo
import com.example.test2.shared.models.PhotoFilter
import com.example.test2.shared.repository.PhotoRepository
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartActionCardTest {

    @Test
    fun testVietQrEmvCoParsing() {
        // Construct valid EMVCo payload for MB Bank (BIN 970422), account 0988776655, 50,000 VND
        val emvCo = "00020101021238540010A00000072701240006970422011009887766550208QRIBFTTA53037045405500005802VN5912NGUYEN VAN A62160812Tien an trua6304ABCD"

        val parsed = VietQrParser.parse(emvCo)
        assertNotNull("VietQR parser should successfully parse EMVCo string", parsed)
        parsed!!
        assertEquals("970422", parsed.bankBin)
        assertEquals("MB Bank", parsed.bankName)
        assertEquals("0988776655", parsed.accountNumber)
        assertEquals(50000.0, parsed.amount ?: 0.0, 0.01)
        assertEquals("NGUYEN VAN A", parsed.accountHolder)
        assertEquals("Tien an trua", parsed.content)
    }

    @Test
    fun testVietQrUrlParsing() {
        val url = "https://img.vietqr.io/image/970436-0011009988776-compact2.png?amount=250000&addInfo=Dong%20tien%20hoc&accountName=TRAN%20THI%20B"

        val parsed = VietQrParser.parse(url)
        assertNotNull("VietQR parser should parse URL format", parsed)
        parsed!!
        assertEquals("0011009988776", parsed.accountNumber)
        assertEquals(250000.0, parsed.amount ?: 0.0, 0.01)
        assertEquals("Dong tien hoc", parsed.content)
        assertEquals("TRAN THI B", parsed.accountHolder)
    }

    @Test
    fun testBankTransferTextExtraction() {
        val ocrText = """
            Thông báo chuyển tiền
            Ngân hàng: Vietcombank
            STK: 0011001234567
            Chủ TK: NGUYEN VAN A
            Số tiền: 500.000đ
            Nội dung: Tien phong thang 11
        """.trimIndent()

        val cards = LocalActionCardExtractor.extractCardsFromText(
            photoId = "photo_pay_1",
            rawOcrText = ocrText,
            photoTitle = "Screenshot_20261117_transfer"
        )

        val paymentCard = cards.find { it.type == ActionCardType.PAYMENT }
        assertNotNull("Should extract a PAYMENT ActionCard from bank transfer text", paymentCard)
        paymentCard!!

        assertTrue("Title should contain bank name", paymentCard.title.contains("Vietcombank"))
        assertTrue("Title should contain account number", paymentCard.title.contains("0011001234567"))

        val payload = JSONObject(paymentCard.payloadJson)
        assertEquals("Vietcombank", payload.getString("bankName"))
        assertEquals("0011001234567", payload.getString("accountNumber"))
        assertEquals("NGUYEN VAN A", payload.getString("accountHolder"))
        assertEquals(500000.0, payload.getDouble("amount"), 0.01)
        assertEquals("Tien phong thang 11", payload.getString("transferContent"))
    }

    @Test
    fun testAddressCardExtraction() {
        val ocrText = """
            Review Quán Cà Phê
            Phê La Cầu Giấy
            Địa chỉ: Số 24 đường Cầu Giấy, Phường Dịch Vọng, Quận Cầu Giấy, Hà Nội
            Hotline: 19001234
            Đồ uống rất ngon và đậm vị trà Ô Long.
        """.trimIndent()

        val cards = LocalActionCardExtractor.extractCardsFromText(
            photoId = "photo_addr_1",
            rawOcrText = ocrText,
            photoTitle = "Review Phê La Cầu Giấy"
        )

        val addressCard = cards.find { it.type == ActionCardType.ADDRESS }
        assertNotNull("Should extract an ADDRESS ActionCard from address text", addressCard)
        addressCard!!

        val payload = JSONObject(addressCard.payloadJson)
        val fullAddress = payload.getString("fullAddress")
        assertTrue("Address should contain street name", fullAddress.contains("Cầu Giấy"))
        assertEquals("Hà Nội", payload.getString("city"))
    }

    @Test
    fun testContactCardExtraction() {
        val ocrText = """
            Công ty Cổ phần Công nghệ ABC
            Người liên hệ: Nguyễn Văn Nam
            Email: nam.nguyen@techcompany.vn
            Số điện thoại: 0987654321
            Tuyển dụng kỹ sư Android cấp cao
        """.trimIndent()

        val cards = LocalActionCardExtractor.extractCardsFromText(
            photoId = "photo_contact_1",
            rawOcrText = ocrText,
            photoTitle = "Tuyen dung Android"
        )

        val contactCard = cards.find { it.type == ActionCardType.CONTACT }
        assertNotNull("Should extract a CONTACT ActionCard from contact text", contactCard)
        contactCard!!

        val payload = JSONObject(contactCard.payloadJson)
        assertEquals("0987654321", payload.getString("phoneNumber"))
        assertEquals("nam.nguyen@techcompany.vn", payload.getString("email"))
        assertTrue("Name or company should match", payload.getString("name").contains("Nguyễn Văn Nam"))
    }

    @Test
    fun testEventCardExtraction() {
        val ocrText = """
            THÔNG BÁO LỊCH HẸN KHÁM BỆNH
            Thời gian: 25/12/2026 09:30
            Bệnh viện 19-8
            Phòng khám số 12
        """.trimIndent()

        val cards = LocalActionCardExtractor.extractCardsFromText(
            photoId = "photo_event_1",
            rawOcrText = ocrText,
            photoTitle = "Lịch khám bệnh"
        )

        // Event ActionCard is deprecated to prevent redundancy with cat_events
        assertTrue("Event ActionCards should be deprecated in favor of cat_events", cards.none { it.type == ActionCardType.EVENT })

        // But SmartHeuristicParser still captures date/time for the native calendar capability
        val parsed = SmartHeuristicParser.parse(ocrText, "Lịch khám bệnh")
        assertTrue("Start time millis should be parsed by parser", (parsed.suggestedEventDateMillis ?: 0L) > 0)
    }

    @Test
    fun testBankTransferRejectsNewsWithoutAccountNumber() {
        val ocrText = """
            Báo điện tử VnExpress
            Ngân hàng Techcombank vừa công bố lợi nhuận quý 3 đạt mức kỷ lục.
            Nhiều chuyên gia tài chính đánh giá cao bước đi này của Techcombank.
        """.trimIndent()

        val cards = LocalActionCardExtractor.extractCardsFromText(
            photoId = "photo_news_1",
            rawOcrText = ocrText,
            photoTitle = "Tin tức tài chính"
        )

        val paymentCard = cards.find { it.type == ActionCardType.PAYMENT }
        org.junit.Assert.assertNull("Should NOT generate PAYMENT card for news articles without account number", paymentCard)
    }

    @Test
    fun testBankTransferRejectsMbDataUsage() {
        val ocrText = """
            Thông báo từ nhà mạng Viettel
            Gói cước ST120K của quý khách còn lại 500 MB data tốc độ cao.
            Tốc độ hiện tại: 15 MB/s. Hạn dùng đến 24:00 hôm nay.
        """.trimIndent()

        val cards = LocalActionCardExtractor.extractCardsFromText(
            photoId = "photo_data_1",
            rawOcrText = ocrText,
            photoTitle = "Goi cuoc Viettel"
        )

        val paymentCard = cards.find { it.type == ActionCardType.PAYMENT }
        org.junit.Assert.assertNull("Should NOT mistake '500 MB data' as MB Bank payment card", paymentCard)
    }

    @Test
    fun testMultiLineAddressReconstruction() {
        val ocrText = """
            Nhà hàng Kichi Kichi Cầu Giấy
            Địa chỉ: Số 12 ngõ 45
            Đường Trần Thái Tông, P. Dịch Vọng Hậu, Q. Cầu Giấy, Hà Nội
            Hotline đặt bàn: 19006622
            Buffet lẩu băng chuyền thơm ngon hấp dẫn.
        """.trimIndent()

        val cards = LocalActionCardExtractor.extractCardsFromText(
            photoId = "photo_addr_multiline",
            rawOcrText = ocrText,
            photoTitle = "Kichi Kichi"
        )

        val addressCard = cards.find { it.type == ActionCardType.ADDRESS }
        assertNotNull("Should extract reconstructed multi-line ADDRESS card", addressCard)
        addressCard!!

        val payload = JSONObject(addressCard.payloadJson)
        val fullAddress = payload.getString("fullAddress")
        assertTrue("Address should contain line 1 house/alley", fullAddress.contains("12 ngõ 45"))
        assertTrue("Address should contain line 2 street", fullAddress.contains("Trần Thái Tông"))
        assertTrue("Address should contain line 2 ward/district", fullAddress.contains("Dịch Vọng Hậu"))
        assertEquals("Hà Nội", payload.getString("city"))
    }

    @Test
    fun testAddressRejectsNegativeNoiseWords() {
        val ocrText = """
            LƯU Ý QUAN TRỌNG
            Thời hạn nộp báo cáo quý trước 17h, ngày 20/11/2026.
            Các cơ quan, đơn vị liên quan cần hoàn thiện hồ sơ.
        """.trimIndent()

        val cards = LocalActionCardExtractor.extractCardsFromText(
            photoId = "photo_noise_1",
            rawOcrText = ocrText,
            photoTitle = "Thong bao co quan"
        )

        val addressCard = cards.find { it.type == ActionCardType.ADDRESS }
        org.junit.Assert.assertNull("Should NOT mistake notice with 'quan trọng', 'liên quan' as address", addressCard)
    }

    @Test
    fun testContactRejectsProductNamePrefix() {
        val ocrText = """
            Chi tiết đơn hàng Tiki
            Tên sản phẩm: Tai nghe không dây Sony WF-1000XM5
            Hotline hỗ trợ: 0912345678
            Thời gian giao hàng dự kiến: 20/11/2026
        """.trimIndent()

        val cards = LocalActionCardExtractor.extractCardsFromText(
            photoId = "photo_order_1",
            rawOcrText = ocrText,
            photoTitle = "Don hang Tiki"
        )

        val contactCard = cards.find { it.type == ActionCardType.CONTACT }
        assertNotNull("Should extract contact card with phone", contactCard)
        contactCard!!

        val payload = JSONObject(contactCard.payloadJson)
        val name = payload.getString("name")
        assertTrue("Should NOT mistake product name as contact person name", !name.contains("sản phẩm") && !name.contains("Tai nghe"))
    }

    @Test
    fun testPhotoRepositoryActionCardFilter() = kotlinx.coroutines.runBlocking {
        val testScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        val driver = com.example.test2.shared.repository.InMemoryPersistenceDriver(
            """{"photos":[],"folders":[],"categories":[],"accountTier":{"isPro":false,"aiScansUsedThisMonth":0,"lastQuotaResetMillis":0},"agingSettings":{}}"""
        )
        val repository = PhotoRepository(driver, testScope)
        repository.loadInitialData()

        val p1 = repository.addPhoto(fileName = "p1.jpg", filePath = "/p1.jpg", title = "Photo 1")
        val p2 = repository.addPhoto(fileName = "p2.jpg", filePath = "/p2.jpg", title = "Photo 2")
        val p3 = repository.addPhoto(fileName = "p3.jpg", filePath = "/p3.jpg", title = "Photo 3")

        // Set action card filter to PAYMENT matching only p1 and p3
        repository.setActionCardFilter(ActionCardType.PAYMENT, setOf(p1.id, p3.id))

        val filtered = repository.filteredPhotos.value
        assertEquals(2, filtered.size)
        assertTrue(filtered.any { it.id == p1.id })
        assertTrue(filtered.any { it.id == p3.id })

        // Clear action card filter
        repository.setActionCardFilter(null, null)
        val allPhotos = repository.filteredPhotos.value
        assertEquals(3, allPhotos.size)
    }

    @Test
    fun testNaturalAddressWithoutKeyword() {
        val ocrText = """
            Tiệm Cà phê Tháng Ba
            24 Doãn Kế Thiện, Cầu Giấy, Hà Nội
            Mở cửa: 08:00 - 22:30
        """.trimIndent()

        val cards = LocalActionCardExtractor.extractCardsFromText(
            photoId = "photo_addr_natural",
            rawOcrText = ocrText,
            photoTitle = "Tiệm Cà phê Tháng Ba"
        )

        val addressCard = cards.find { it.type == ActionCardType.ADDRESS }
        assertNotNull("Should detect natural address starting directly with house number", addressCard)
        addressCard!!

        val payload = JSONObject(addressCard.payloadJson)
        val fullAddress = payload.getString("fullAddress")
        assertTrue("Address should contain '24 Doãn Kế Thiện'", fullAddress.contains("24 Doãn Kế Thiện"))
        assertEquals("Hà Nội", payload.getString("city"))
    }

    @Test
    fun testSlashAddressHcmc() {
        val ocrText = """
            Cơm tấm Phúc Lộc Thọ
            124/5A Nguyễn Trãi, Phường Bến Thành, Quận 1, TP.HCM
            Giao hàng tận nơi
        """.trimIndent()

        val cards = LocalActionCardExtractor.extractCardsFromText(
            photoId = "photo_addr_slash",
            rawOcrText = ocrText,
            photoTitle = "Cơm tấm"
        )

        val addressCard = cards.find { it.type == ActionCardType.ADDRESS }
        assertNotNull("Should detect address with alley/slash house number", addressCard)
        addressCard!!

        val payload = JSONObject(addressCard.payloadJson)
        val fullAddress = payload.getString("fullAddress")
        assertTrue("Address should contain slash number", fullAddress.contains("124/5A"))
        assertEquals("TP.HCM", payload.getString("city"))
    }

    @Test
    fun testSpaceSeparatedBankAccount() {
        val ocrText = """
            Ngân hàng Techcombank
            STK: 1903 4567 8901 23
            Chủ TK: NGUYEN VAN A
            Số tiền: 200.000đ
        """.trimIndent()

        val cards = LocalActionCardExtractor.extractCardsFromText(
            photoId = "photo_pay_space",
            rawOcrText = ocrText,
            photoTitle = "Techcombank Transfer"
        )

        val paymentCard = cards.find { it.type == ActionCardType.PAYMENT }
        assertNotNull("Should extract PAYMENT card for space-separated bank account", paymentCard)
        paymentCard!!

        val payload = JSONObject(paymentCard.payloadJson)
        assertEquals("19034567890123", payload.getString("accountNumber"))
        assertEquals("Techcombank", payload.getString("bankName"))
    }

    @Test
    fun testPhone433Format() {
        val ocrText = """
            Phòng Nhân sự - Công ty Cổ phần Alpha
            Người liên hệ: Trần Tuấn Anh
            Điện thoại: 0912 345 678
            Email: hr@alpha.vn
        """.trimIndent()

        val cards = LocalActionCardExtractor.extractCardsFromText(
            photoId = "photo_contact_433",
            rawOcrText = ocrText,
            photoTitle = "Lien he tuyen dung"
        )

        val contactCard = cards.find { it.type == ActionCardType.CONTACT }
        assertNotNull("Should extract CONTACT card with 4-3-3 phone format", contactCard)
        contactCard!!

        val payload = JSONObject(contactCard.payloadJson)
        assertEquals("0912345678", payload.getString("phoneNumber"))
    }

    @Test
    fun testLandlinePhone() {
        val ocrText = """
            Bệnh viện Bạch Mai
            Hotline cấp cứu: 024 3837 3747
            Địa chỉ: 78 Giải Phóng, Phương Mai, Đống Đa, Hà Nội
        """.trimIndent()

        val cards = LocalActionCardExtractor.extractCardsFromText(
            photoId = "photo_contact_landline",
            rawOcrText = ocrText,
            photoTitle = "Cap cuu"
        )

        val contactCard = cards.find { it.type == ActionCardType.CONTACT }
        assertNotNull("Should extract CONTACT card for landline phone", contactCard)
        contactCard!!

        val payload = JSONObject(contactCard.payloadJson)
        assertEquals("02438373747", payload.getString("phoneNumber"))
    }

    @Test
    fun testBroadEventKeywords() {
        val ocrText = """
            Hội thảo Trí tuệ nhân tạo GenAI 2026
            Thời gian: lúc 14h30 ngày 25/11/2026
            Địa điểm: Trung tâm Hội nghị Quốc gia
        """.trimIndent()

        val cards = LocalActionCardExtractor.extractCardsFromText(
            photoId = "photo_broad_event",
            rawOcrText = ocrText,
            photoTitle = "Hội thảo GenAI"
        )

        // Event ActionCard is deprecated to prevent redundancy with cat_events
        assertTrue("Event ActionCards should be deprecated in favor of cat_events", cards.none { it.type == ActionCardType.EVENT })

        val parsed = SmartHeuristicParser.parse(ocrText, "Hội thảo GenAI")
        assertTrue("Event date should be parsed by parser", (parsed.suggestedEventDateMillis ?: 0L) > 0)
    }

    @Test
    fun testPiiScrubberRedactsSpacedBankAccountAnd433Phone() {
        val rawText = "Techcombank STK: 1903 4567 8901 23, liên hệ 0912 345 678 để xác nhận."
        val result = PiiScrubber.scrub(rawText)

        assertTrue("Should mark as redacted", result.hasPiiRedacted)
        assertTrue("Bank account should be masked", result.scrubbedText.contains("STK_****0123"))
        assertTrue("Phone should be masked", result.scrubbedText.contains("***-***-678"))
        assertTrue("Raw STK should not be in scrubbed text", !result.scrubbedText.contains("1903 4567 8901 23"))
        assertTrue("Raw phone should not be in scrubbed text", !result.scrubbedText.contains("0912 345 678"))
    }
}

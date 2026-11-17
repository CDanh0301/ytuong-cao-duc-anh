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

        val eventCard = cards.find { it.type == ActionCardType.EVENT }
        assertNotNull("Should extract an EVENT ActionCard from appointment text", eventCard)
        eventCard!!

        val payload = JSONObject(eventCard.payloadJson)
        assertTrue("Start time millis should be parsed", payload.getLong("startTimeMillis") > 0)
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
}

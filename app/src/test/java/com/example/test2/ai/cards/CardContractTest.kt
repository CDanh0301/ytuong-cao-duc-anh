package com.example.test2.ai.cards

import com.example.test2.ai.LocalActionCardExtractor
import com.example.test2.shared.models.ActionCardType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardContractTest {

    @Test
    fun testCardContractPayloadKeysAndOrdering() {
        val ocrText = """
            Ngân hàng Vietcombank
            STK: 0011001234567
            Chủ TK: NGUYEN VAN A
            Số tiền: 500.000đ
            
            Nhà hàng Kichi Kichi
            Địa chỉ: 24 Doãn Kế Thiện, Cầu Giấy, Hà Nội
            
            Người liên hệ: Trần Tuấn Anh
            Điện thoại: 0912 345 678
            Email: hr@alpha.vn
            
            Hội thảo Trí tuệ nhân tạo GenAI 2026
            Thời gian: 25/11/2026 14:30
        """.trimIndent()

        val cards = LocalActionCardExtractor.extractCardsFromText(
            photoId = "photo_all_contract",
            rawOcrText = ocrText,
            photoTitle = "Screenshot_2026"
        )

        assertTrue("Should extract multiple cards", cards.size >= 3)

        // Strict ordering: PAYMENT -> ADDRESS -> CONTACT (EVENT card deprecated per system spec)
        val types = cards.map { it.type }
        val paymentIdx = types.indexOf(ActionCardType.PAYMENT)
        val addressIdx = types.indexOf(ActionCardType.ADDRESS)
        val contactIdx = types.indexOf(ActionCardType.CONTACT)

        assertTrue("Payment must come before Address", paymentIdx < addressIdx)
        assertTrue("Address must come before Contact", addressIdx < contactIdx)
        assertTrue("Event cards must be deprecated in favor of cat_events", cards.none { it.type == ActionCardType.EVENT })

        // Contract checks for PAYMENT
        val payCard = cards[paymentIdx]
        val payJson = JSONObject(payCard.payloadJson)
        assertTrue(payJson.has("bankName"))
        assertTrue(payJson.has("accountNumber"))
        assertTrue(payJson.has("accountHolder"))
        assertTrue(payJson.has("amount"))
        assertTrue(payJson.has("transferContent"))
        assertEquals(2, payJson.optInt("ev"))

        // Contract checks for ADDRESS
        val addrCard = cards[addressIdx]
        val addrJson = JSONObject(addrCard.payloadJson)
        assertTrue(addrJson.has("fullAddress"))
        assertTrue(addrJson.has("placeName"))
        assertTrue(addrJson.has("city"))
        assertTrue(addrJson.has("adminSystem"))
        assertTrue(addrCard.title.startsWith("📍"))
        assertEquals("Hà Nội", addrJson.getString("city"))
        assertEquals(2, addrJson.optInt("ev"))

        // Contract checks for CONTACT
        val contactCard = cards[contactIdx]
        val contactJson = JSONObject(contactCard.payloadJson)
        assertTrue(contactJson.has("name"))
        assertTrue(contactJson.has("phoneNumber"))
        assertTrue(contactJson.has("email"))
        assertTrue(contactJson.has("company"))
        assertTrue(contactJson.has("phoneKind"))
        assertTrue(contactCard.title.startsWith("👤"))
        assertEquals("0912345678", contactJson.getString("phoneNumber"))
        assertEquals(2, contactJson.optInt("ev"))

        // Contract verification that EVENT cards are omitted per user spec
        assertTrue("No EVENT card in result list", cards.none { it.type == ActionCardType.EVENT })
    }
}

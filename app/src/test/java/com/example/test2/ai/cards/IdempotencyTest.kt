package com.example.test2.ai.cards

import com.example.test2.ai.LocalActionCardExtractor
import com.example.test2.shared.models.ActionCardType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdempotencyTest {

    @Test
    fun testT11_DeterministicCardIdsAndIdempotentExtraction() {
        val ocrText = """
            Ngân hàng BIDV
            STK: 123456789012
            Chủ TK: TRAN THI C
            
            Nhà hàng Phở Thìn
            Địa chỉ: 13 Lò Đúc, Phường Phạm Đình Hổ, Quận Hai Bà Trưng, Hà Nội
            
            Người liên hệ: Anh Hùng
            Số điện thoại: 0903123456
        """.trimIndent()

        val photoId = "photo_deterministic_1"

        val run1 = LocalActionCardExtractor.extractCardsFromText(photoId, ocrText)
        val run2 = LocalActionCardExtractor.extractCardsFromText(photoId, ocrText)
        val run3 = LocalActionCardExtractor.extractCardsFromText(photoId, ocrText)

        assertEquals("Same number of cards across multiple runs", run1.size, run2.size)
        assertEquals("Same number of cards across multiple runs", run2.size, run3.size)

        // Verify IDs are identical across runs (Deterministic ID)
        for (i in run1.indices) {
            assertEquals("Card ID must be identical across runs", run1[i].id, run2[i].id)
            assertEquals("Card ID must be identical across runs", run2[i].id, run3[i].id)
            assertEquals(run1[i].type, run2[i].type)
            assertEquals(run1[i].title, run2[i].title)
        }

        // Verify strict ordering: Payment -> Address -> Contact
        assertEquals(ActionCardType.PAYMENT, run1[0].type)
        assertEquals(ActionCardType.ADDRESS, run1[1].type)
        assertEquals(ActionCardType.CONTACT, run1[2].type)
    }

    @Test
    fun testT12_ExtractorVersionInPayload() {
        val ocrText = "Hotline CSKH: 1900 54 54 13"
        val cards = LocalActionCardExtractor.extractCardsFromText("photo_ev_test", ocrText)

        assertTrue(cards.isNotEmpty())
        for (card in cards) {
            val json = JSONObject(card.payloadJson)
            assertTrue("Payload must have 'ev' key", json.has("ev"))
            assertEquals(CardIds.EXTRACTOR_VERSION, json.getInt("ev"))
        }
    }
}

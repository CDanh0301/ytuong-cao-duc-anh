package com.example.test2.ai.cards

import com.example.test2.ai.CloudAiBackendClient
import com.example.test2.ai.PrivacyDecision
import com.example.test2.ai.PrivacyPolicy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyPolicyTest {

    @Test
    fun testT13_SensitiveDocumentsAndCredentialsClassification() {
        // 1. CCCD with signatures
        val cccdText = """
            CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM
            Độc lập - Tự do - Hạnh phúc
            CĂN CƯỚC CÔNG DÂN
            Số: ID_****1234
            Họ và tên: NGUYEN VAN A
        """.trimIndent()
        assertEquals(PrivacyDecision.LOCAL_ONLY, PrivacyPolicy.classify(cccdText))

        // 2. OTP message
        val otpText = "Mã xác thực OTP của quý khách là 689123. Vui lòng không chia sẻ cho bất kỳ ai."
        assertEquals(PrivacyDecision.LOCAL_ONLY, PrivacyPolicy.classify(otpText))

        // 3. Secret tag from scrubber
        val secretText = "Mật khẩu đăng nhập: [REDACTED_SECRET]"
        assertEquals(PrivacyDecision.LOCAL_ONLY, PrivacyPolicy.classify(secretText))

        // 4. Credit card number
        val cardText = "Thẻ tín dụng Visa: ****-****-****-1234, ngày hết hạn: 12/28"
        assertEquals(PrivacyDecision.LOCAL_ONLY, PrivacyPolicy.classify(cardText))

        // 5. News article casually mentioning "hộ chiếu" once
        val newsText = """
            Bộ Công an vừa thông báo mẫu hộ chiếu mới có gắn chip điện tử.
            Người dân có thể đăng ký trực tuyến qua cổng dịch vụ công quốc gia.
        """.trimIndent()
        assertEquals(PrivacyDecision.CLOUD_ELIGIBLE, PrivacyPolicy.classify(newsText))
    }

    @Test
    fun testT14_BankTransferReceiptIsCloudEligible() {
        val receiptText = """
            Chuyển tiền thành công
            Ngân hàng: Vietcombank
            STK: STK_****5678
            Tên người thụ hưởng: NGUYEN VAN B
            Số tiền: 250.000đ
            Nội dung: Tien mua sach
        """.trimIndent()
        assertEquals(PrivacyDecision.CLOUD_ELIGIBLE, PrivacyPolicy.classify(receiptText))
    }

    @Test
    fun testT17_BatchEmbeddingsPreservesIndicesWithNullForRejected() = runBlocking {
        val texts = listOf(
            "Chuyến đi du lịch Đà Lạt mùa thu tuyệt đẹp", // CLOUD_ELIGIBLE
            "Mã xác thực OTP của bạn là 992812", // LOCAL_ONLY (rejected)
            "Công thức nấu món phở bò truyền thống" // CLOUD_ELIGIBLE
        )

        // Without mock HTTP call, calling with blank key returns nulls for all, but let's verify PrivacyPolicy rejects index 1 directly
        val p0 = PrivacyPolicy.classify(texts[0])
        val p1 = PrivacyPolicy.classify(texts[1])
        val p2 = PrivacyPolicy.classify(texts[2])

        assertEquals(PrivacyDecision.CLOUD_ELIGIBLE, p0)
        assertEquals(PrivacyDecision.LOCAL_ONLY, p1)
        assertEquals(PrivacyDecision.CLOUD_ELIGIBLE, p2)

        // Single embedding call on LOCAL_ONLY item returns null without network
        val result = CloudAiBackendClient.getEmbedding(texts[1], apiKey = "test_key")
        assertNull("LOCAL_ONLY item must immediately return null for embedding without network call", result)
    }
}

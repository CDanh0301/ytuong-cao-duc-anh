package com.example.test2.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PiiScrubberExpandedTest {

    @Test
    fun testStrongPasswordsWithSpecialCharsAndLongLengthAreRedacted() {
        val text = "Tài khoản admin, mật khẩu là Abc@123456!XYZ, API_KEY=sk_live_99887766aabbcc"
        val res = PiiScrubber.scrub(text)
        assertTrue(res.hasPiiRedacted)
        assertFalse(res.scrubbedText.contains("Abc@123456!XYZ"))
        assertFalse(res.scrubbedText.contains("sk_live_99887766aabbcc"))
        assertTrue(res.scrubbedText.contains("[REDACTED_SECRET]"))
        assertEquals(PrivacyDecision.LOCAL_ONLY, PrivacyPolicy.classify(res.scrubbedText))
    }

    @Test
    fun testOtpAndPinWithVietnameseFillersAndCvvAreRedacted() {
        val otpText = "Mã xác thực OTP của bạn là 689123, mã PIN của quý khách là 4567"
        val otpRes = PiiScrubber.scrub(otpText)
        assertTrue(otpRes.hasPiiRedacted)
        assertFalse(otpRes.scrubbedText.contains("689123"))
        assertFalse(otpRes.scrubbedText.contains("4567"))
        assertEquals(PrivacyDecision.LOCAL_ONLY, PrivacyPolicy.classify(otpRes.scrubbedText))

        val cvvText = "Thẻ Visa ****-****-****-1234, CVV: 842, Security code 912"
        val cvvRes = PiiScrubber.scrub(cvvText)
        assertTrue(cvvRes.hasPiiRedacted)
        assertFalse(cvvRes.scrubbedText.contains("842"))
        assertFalse(cvvRes.scrubbedText.contains("912"))
        assertEquals(PrivacyDecision.LOCAL_ONLY, PrivacyPolicy.classify(cvvRes.scrubbedText))
    }

    @Test
    fun testStandaloneBankAccountInBankingContextIsRedactedWhilePreservingAmount() {
        val text = "Ngân hàng Vietcombank Chuyển khoản thành công tới 19034567890123 Số tiền 5.000.000 VND Nội dung Tien nha"
        val localRes = PiiScrubber.scrub(text)
        assertTrue(localRes.hasPiiRedacted)
        assertFalse(localRes.scrubbedText.contains("19034567890123"))
        assertTrue(localRes.scrubbedText.contains("STK_****0123"))
        assertTrue("Currency amount must stay intact", localRes.scrubbedText.contains("5.000.000 VND"))

        val cloudRes = PiiScrubber.scrubForCloud(text)
        assertFalse("Zero trailing digits may leave device", cloudRes.scrubbedText.contains("0123"))
        assertTrue(cloudRes.scrubbedText.contains("[BANK_ACCOUNT]"))
        assertTrue("Currency amount must stay intact for Gemini", cloudRes.scrubbedText.contains("5.000.000 VND"))
        assertEquals(PrivacyDecision.CLOUD_ELIGIBLE, PrivacyPolicy.classify(cloudRes.scrubbedText))
    }

    @Test
    fun testPassportAndSpacedCccdAreRedactedAndBlockedLocally() {
        val passportText = "Hộ chiếu số C1234567 cấp tại Cục Quản lý xuất nhập cảnh"
        val passportCloud = PiiScrubber.scrubForCloud(passportText)
        assertTrue(passportCloud.hasPiiRedacted)
        assertFalse(passportCloud.scrubbedText.contains("C1234567"))
        assertTrue(passportCloud.scrubbedText.contains("[PASSPORT_NO]"))
        assertEquals(PrivacyDecision.LOCAL_ONLY, PrivacyPolicy.classify(passportCloud.scrubbedText))

        val spacedCccdText = "Số CCCD: 001 200 012 345 Họ và tên: NGUYEN VAN A"
        val cccdLocal = PiiScrubber.scrub(spacedCccdText)
        assertTrue(cccdLocal.scrubbedText.contains("ID_****2345"))
        val cccdCloud = PiiScrubber.scrubForCloud(spacedCccdText)
        assertFalse(cccdCloud.scrubbedText.contains("2345"))
        assertTrue(cccdCloud.scrubbedText.contains("[ID_NUMBER]"))
        assertEquals(PrivacyDecision.LOCAL_ONLY, PrivacyPolicy.classify(cccdCloud.scrubbedText))
    }

    @Test
    fun testScrubForCloudRemovesAllTrailingDigitsFromMaskedTokens() {
        val raw = "Techcombank STK: 19034567890123, SĐT: 0912345678, Email: a@b.com, CCCD: 001200012345"
        val cloud = PiiScrubber.scrubForCloud(raw).scrubbedText
        assertFalse(cloud.contains("0123"))
        assertFalse(cloud.contains("678"))
        assertFalse(cloud.contains("2345"))
        assertFalse(cloud.contains("a@b.com"))
        assertTrue(cloud.contains("[BANK_ACCOUNT]"))
        assertTrue(cloud.contains("[PHONE_NUMBER]"))
        assertTrue(cloud.contains("[EMAIL_REDACTED]"))
        assertTrue(cloud.contains("[ID_NUMBER]"))
    }

    @Test
    fun testNoFalsePositivesOnPricesDatesOrdersZoomAndVenues() {
        val commercialAndEvent = """
            Shopee Đơn hàng #9981273 Tổng thanh toán 1.250.000 đ Giảm 50k dùng code giảm giá SHOPEE50
            Zoom Meeting ID 889 1234 5678 ngày 20/11/2026 lúc 08:30 tại Lotte Cinema
            Bệnh viện 19-8 Số 9 Trần Bình, Hà Nội - Moo Beef Steak Trần Quốc Toản
        """.trimIndent()
        val cloud = PiiScrubber.scrubForCloud(commercialAndEvent)
        assertFalse("Commercial/event content must not trigger false redaction", cloud.hasPiiRedacted)
        assertTrue(cloud.scrubbedText.contains("1.250.000 đ"))
        assertTrue(cloud.scrubbedText.contains("20/11/2026"))
        assertTrue(cloud.scrubbedText.contains("08:30"))
        assertTrue(cloud.scrubbedText.contains("#9981273"))
        assertTrue(cloud.scrubbedText.contains("889 1234 5678"))
        assertTrue(cloud.scrubbedText.contains("SHOPEE50"))
        assertTrue(cloud.scrubbedText.contains("Bệnh viện 19-8"))
        assertEquals(PrivacyDecision.CLOUD_ELIGIBLE, PrivacyPolicy.classify(cloud.scrubbedText))
    }
}

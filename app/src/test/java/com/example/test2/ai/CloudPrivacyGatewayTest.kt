package com.example.test2.ai

import com.example.test2.data.local.prefs.CloudAiConfig
import com.example.test2.data.remote.BatchPhotoItem
import com.example.test2.data.remote.CategoryCandidateSnippet
import com.example.test2.data.remote.GeminiApiService
import com.example.test2.data.remote.ParsedScreenshotData
import com.example.test2.shared.models.Category
import com.example.test2.shared.models.Photo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudPrivacyGatewayTest {

    @Test
    fun testPhotoTitleIsScrubbedAndCheckedByPrivacyPolicy() {
        val sensitiveTitle = CloudPrivacyGateway.prepareForAnalysis(
            rawOrScrubbedText = "Ảnh chụp màn hình",
            photoTitle = "CCCD mặt trước 001200012345"
        )
        assertEquals(PrivacyDecision.LOCAL_ONLY, sensitiveTitle.decision)

        val phoneTitle = CloudPrivacyGateway.prepareForAnalysis(
            rawOrScrubbedText = "Hóa đơn mua cà phê 45.000đ",
            photoTitle = "Khách hàng 0912345678"
        )
        assertEquals(PrivacyDecision.CLOUD_ELIGIBLE, phoneTitle.decision)
        assertFalse(phoneTitle.scrubbedTitle.contains("0912345678"))
        assertFalse(phoneTitle.scrubbedTitle.contains("678"))
        assertTrue(phoneTitle.scrubbedTitle.contains("[PHONE_NUMBER]"))
    }

    @Test
    fun testPlaceholderOnlyOrSensitiveSearchQuerySkipsCloudEmbedding() {
        val phoneQuery = CloudPrivacyGateway.prepareTextForEmbedding("0912345678")
        assertFalse(
            "Pure phone number query should skip cloud embedding and stay on local FTS5",
            phoneQuery.isMeaningfulForCloud
        )

        val otpQuery = CloudPrivacyGateway.prepareTextForEmbedding("Mã xác thực OTP 689123")
        assertEquals(PrivacyDecision.LOCAL_ONLY, otpQuery.decision)
        assertFalse(otpQuery.isMeaningfulForCloud)

        val semanticQuery = CloudPrivacyGateway.prepareTextForEmbedding("nồi cơm điện bluestone")
        assertEquals(PrivacyDecision.CLOUD_ELIGIBLE, semanticQuery.decision)
        assertTrue(semanticQuery.isMeaningfulForCloud)
    }

    @Test
    fun testSafeHttpsUrlValidation() {
        assertTrue(CloudPrivacyGateway.isSafeHttpsUrl("https://ai-proxy.example.com/v1beta/models"))
        assertFalse(CloudPrivacyGateway.isSafeHttpsUrl("http://ai-proxy.example.com/v1beta/models"))
        assertFalse(CloudPrivacyGateway.isSafeHttpsUrl(""))
        assertFalse(CloudPrivacyGateway.isSafeHttpsUrl("https://invalid url.com"))
    }

    @Test
    fun testCloudAiConfigReadyInProxyModeWithoutClientApiKey() {
        val proxyOnly = CloudAiConfig(
            apiKey = "",
            modelName = "gemini-3.5-flash-lite",
            customEndpoint = "",
            isEnabled = true,
            proxyBaseUrl = "https://ai-proxy.example.com/v1beta/models"
        )
        assertTrue(proxyOnly.isCloudReady())

        val unconfigured = CloudAiConfig(
            apiKey = "",
            modelName = "gemini-3.5-flash-lite",
            customEndpoint = "",
            isEnabled = true,
            proxyBaseUrl = ""
        )
        assertFalse(unconfigured.isCloudReady())
    }

    @Test
    fun testVerifyCustomCategoryCandidatesRejectsLocalOnlyWithoutNetwork() = runBlocking {
        val sensitiveCandidates = listOf(
            CategoryCandidateSnippet(
                id = "p_cccd",
                title = "CCCD cá nhân",
                textSnippet = "CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM CĂN CƯỚC CÔNG DÂN ID_****1234"
            ),
            CategoryCandidateSnippet(
                id = "p_otp",
                title = "Tin nhắn ngân hàng",
                textSnippet = "Mã xác thực OTP của bạn là 998877"
            )
        )
        val matched = CloudAiBackendClient.verifyCustomCategoryCandidates(
            category = Category(id = "cat_docs", name = "Giấy tờ"),
            candidates = sensitiveCandidates,
            apiKey = "fake_key_never_used",
            proxyBaseUrl = ""
        )
        assertTrue("All LOCAL_ONLY items must be filtered out locally", matched.isEmpty())
    }

    @Test
    fun testRetroactiveCategorizerBlocksLocalOnlyFromCloudWhileKeepingLocalMatching() = runBlocking {
        val capturedCloudCandidates = mutableListOf<CategoryCandidateSnippet>()

        val fakeGemini = object : GeminiApiService {
            override suspend fun analyzeWithFallback(
                scrubbedText: String,
                photoTitle: String,
                categories: List<Category>,
                isPro: Boolean,
                hasFace: Boolean,
                visualLabels: List<String>
            ) = ParsedScreenshotData(detectedIntent = "OTHER")

            override suspend fun analyzeBatchWithGemini(
                items: List<BatchPhotoItem>,
                categories: List<Category>,
                isPro: Boolean
            ) = emptyMap<String, ParsedScreenshotData>()

            override suspend fun getEmbedding(
                text: String,
                apiKey: String,
                modelName: String,
                taskType: String
            ): FloatArray? = null

            override suspend fun getBatchEmbeddings(
                texts: List<String>,
                apiKey: String,
                modelName: String,
                taskType: String
            ): List<FloatArray?> = texts.map { null }

            override suspend fun verifyCustomCategoryCandidates(
                category: Category,
                candidates: List<CategoryCandidateSnippet>,
                apiKey: String,
                modelName: String
            ): List<String> {
                capturedCloudCandidates.addAll(candidates)
                return candidates.map { it.id }
            }
        }

        val cccdPhoto = Photo(
            id = "p_cccd_borderline",
            fileName = "cccd.png",
            filePath = "/tmp/cccd.png",
            title = "Giấy tờ cá nhân",
            description = "CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM CĂN CƯỚC CÔNG DÂN 001200012345",
            folderId = "folder_screenshots",
            dateAdded = 1000L,
            isScreenshot = true,
            piiScrubbedText = null // Intentionally null to test description scrubbing & PrivacyPolicy guard
        )

        val regularPhoto = Photo(
            id = "p_coffee_borderline",
            fileName = "receipt.png",
            filePath = "/tmp/receipt.png",
            title = "Hóa đơn 0912345678",
            folderId = "folder_screenshots",
            dateAdded = 2000L,
            isScreenshot = true,
            piiScrubbedText = "Highlands Coffee hóa đơn thanh toán 55.000đ STK: STK_****5678"
        )

        val customCategory = Category(id = "cat_coffee", name = "Highlands")
        RetroactiveCategorizer.findMatchingPhotosForCategory(
            category = customCategory,
            photos = listOf(cccdPhoto, regularPhoto),
            apiKey = "",
            geminiApiService = fakeGemini,
            isCloudAvailable = true
        )

        assertFalse(
            "LOCAL_ONLY CCCD photo must NEVER be sent to Gemini verifyCustomCategoryCandidates",
            capturedCloudCandidates.any { it.id == "p_cccd_borderline" }
        )
        for (sent in capturedCloudCandidates) {
            assertFalse("Raw phone digits must not reach cloud", sent.title.contains("0912345678"))
            assertFalse("Trailing 4 bank digits must not reach cloud", sent.textSnippet.contains("5678"))
        }

        // Verify LOCAL_ONLY CCCD still matches locally when category matches its content
        val idCategory = Category(id = "cat_id", name = "Căn cước")
        val localMatches = RetroactiveCategorizer.findMatchingPhotosForCategory(
            category = idCategory,
            photos = listOf(cccdPhoto),
            apiKey = "",
            geminiApiService = fakeGemini,
            isCloudAvailable = true
        )
        assertTrue("LOCAL_ONLY photo must still match via on-device engine", localMatches.contains("p_cccd_borderline"))
    }

    @Test
    fun testTimestampPhotoTitleAndEverydayProductSpecsRemainCloudEligible() {
        val result = CloudPrivacyGateway.prepareForAnalysis(
            rawOrScrubbedText = "Pin dự phòng Anker 20000mAh sạc nhanh 65W giá 850.000đ mã vạch 8934563123456",
            photoTitle = "1764231847123"
        )
        assertEquals(PrivacyDecision.CLOUD_ELIGIBLE, result.decision)
        assertFalse(result.scrubbedText.contains("[REDACTED_SECRET]"))
        assertFalse(result.scrubbedText.contains("[CREDIT_CARD]"))
    }
}

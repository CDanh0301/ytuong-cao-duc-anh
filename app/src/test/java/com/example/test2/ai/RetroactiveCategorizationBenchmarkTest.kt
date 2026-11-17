package com.example.test2.ai

import com.example.test2.shared.models.Category
import com.example.test2.shared.models.Photo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetroactiveCategorizationBenchmarkTest {

    private fun createPhoto(
        id: String,
        title: String,
        piiScrubbedText: String? = null,
        semanticTags: List<String> = emptyList(),
        suggestedEventTitle: String? = null,
        targetCategoryId: String? = null,
        isScreenshot: Boolean = true
    ): Photo {
        return Photo(
            id = id,
            fileName = "$title.png",
            filePath = "/storage/emulated/0/DCIM/$title.png",
            title = title,
            folderId = "folder_screenshots",
            dateAdded = System.currentTimeMillis(),
            isScreenshot = isScreenshot,
            isProcessed = true,
            piiScrubbedText = piiScrubbedText,
            semanticTags = semanticTags,
            suggestedEventTitle = suggestedEventTitle
        )
    }

    @Test
    fun testTC1_ElectricityBillWithDefaultScreenshotFilenameIsMatched() {
        // TC1: User creates custom category "Hóa đơn điện". Photo has default Android screenshot title
        // but OCR text contains explicit electricity bill content.
        val category = Category(
            id = "cat_electric_bill",
            name = "Hóa đơn điện",
            colorHex = "#3B82F6"
        )
        val photo = createPhoto(
            id = "photo_tc1",
            title = "Screenshot_20261111_102030",
            piiScrubbedText = """
                TỔNG CÔNG TY ĐIỆN LỰC MIỀN BẮC
                CÔNG TY TNHH MTV ĐIỆN LỰC HÀ NỘI
                GIẤY BÁO TIỀN ĐIỆN VÀ THÔNG TIN THANH TOÁN
                Hóa đơn tiền điện tháng 11/2026
                Khách hàng: NGUYEN VAN A
                Mã khách hàng: PD01000234567
                Tổng số tiền thanh toán: 1.250.000 VNĐ
                Hạn thanh toán: 20/11/2026
            """.trimIndent()
        )

        val score = RetroactiveCategorizer.evaluatePhotoConfidence(
            photo = photo,
            category = category,
            ftsMatched = true
        )

        assertTrue(
            "TC1: Electricity bill must be matched (score was $score, expected >= 45.0)",
            score >= 45.0f
        )
    }

    @Test
    fun testTC2_CasualChatPassingMentionIsNotMatched() {
        // TC2: User creates custom category "Cà phê" (short single word).
        // Photo is a casual chat mentioning "cà phê" once in passing. Must NOT be falsely grouped!
        val category = Category(
            id = "cat_coffee",
            name = "Cà phê",
            colorHex = "#8B5CF6"
        )
        val photo = createPhoto(
            id = "photo_tc2",
            title = "Screenshot_20261111_ZaloChat",
            piiScrubbedText = """
                Anh: Dự án hôm nay thế nào rồi em?
                Em: Dạ cũng tạm ổn rồi anh ạ, đang hoàn thiện báo cáo tuần.
                Anh: Thế chiều tầm 4h làm cốc cà phê không em ơi?
                Em: Ok anh nhé, hẹn ở quán cũ ạ.
                Anh: Nhớ mang tài liệu theo nhé.
            """.trimIndent()
        )

        val score = RetroactiveCategorizer.evaluatePhotoConfidence(
            photo = photo,
            category = category,
            ftsMatched = false
        )

        assertFalse(
            "TC2: Casual chat mention must NOT be matched (score was $score, expected < 45.0)",
            score >= 45.0f
        )
    }

    @Test
    fun testTC3_CoffeeShopMenuWithCoOccurrencesIsMatched() {
        // TC3: Photo is a legitimate coffee shop menu. Even with single word "Cà phê",
        // it appears in header with related drink concepts (co-occurrences).
        val category = Category(
            id = "cat_coffee",
            name = "Cà phê",
            colorHex = "#8B5CF6"
        )
        val photo = createPhoto(
            id = "photo_tc3",
            title = "Screenshot_20261111_CoffeeMenu",
            piiScrubbedText = """
                THE COFFEE HOUSE - MENU ĐỒ UỐNG
                DANH MỤC CÀ PHÊ & TRÀ ĐẶC BIỆT
                1. Cà phê sữa đá - 39.000đ
                2. Cà phê đen đá - 35.000đ
                3. Bạc xỉu đá - 45.000đ
                4. Trà sen vàng macchiato - 49.000đ
            """.trimIndent(),
            semanticTags = listOf("menu", "coffee", "beverage")
        )

        val score = RetroactiveCategorizer.evaluatePhotoConfidence(
            photo = photo,
            category = category,
            ftsMatched = true
        )

        assertTrue(
            "TC3: Legitimate coffee shop menu must be matched (score was $score, expected >= 45.0)",
            score >= 45.0f
        )
    }

    @Test
    fun testTC4_LongNewsArticleWithIncidentalWordIsNotMatched() {
        // TC4: 500-word news article on socio-economics containing casual mention of "xe máy".
        // Category is single short word "Xe". Must NOT be falsely grouped!
        val category = Category(
            id = "cat_vehicle",
            name = "Xe",
            colorHex = "#10B981"
        )
        val photo = createPhoto(
            id = "photo_tc4",
            title = "Screenshot_20261111_VnExpress",
            piiScrubbedText = """
                Kinh tế vĩ mô quý 4 ghi nhận mức tăng trưởng ổn định.
                Bộ Tài chính vừa công bố báo cáo tổng kết tình hình thu ngân sách nhà nước tháng 10.
                Theo đó, chỉ số giá tiêu dùng CPI được kiểm soát tốt dưới ngưỡng 4%.
                Nhiều chuyên gia nhận định sức mua thị trường đang dần hồi phục sau các biện pháp kích cầu.
                Tuy nhiên, việc giá xăng dầu biến động có thể tác động đến chi phí đi lại của người đi xe máy trong thời gian tới.
                Ngân hàng Nhà nước tiếp tục duy trì chính sách tiền tệ chủ động, linh hoạt.
                Các doanh nghiệp xuất nhập khẩu được khuyến nghị chủ động phòng ngừa rủi ro tỷ giá.
                Thị trường bất động sản cũng đang có dấu hiệu khởi sắc ở phân khúc nhà ở xã hội.
            """.trimIndent()
        )

        val score = RetroactiveCategorizer.evaluatePhotoConfidence(
            photo = photo,
            category = category,
            ftsMatched = false
        )

        assertFalse(
            "TC4: Long news article with incidental single word must NOT be matched (score was $score, expected < 45.0)",
            score >= 45.0f
        )
    }

    @Test
    fun testTC5_VisualOnlyPlantPhotoWithMatchingTagsIsMatched() {
        // TC5: Photo of indoor succulent / plants with NO OCR text, but strong ML Kit visual tags.
        // Category "Cây cảnh".
        val category = Category(
            id = "cat_plants",
            name = "Cây cảnh",
            colorHex = "#22C55E"
        )
        val photo = createPhoto(
            id = "photo_tc5",
            title = "IMG_20261111_083045",
            piiScrubbedText = "",
            semanticTags = listOf("plant", "houseplant", "succulent", "flora", "leaf")
        )

        val score = RetroactiveCategorizer.evaluatePhotoConfidence(
            photo = photo,
            category = category,
            ftsMatched = false
        )

        assertTrue(
            "TC5: Visual-only plant photo must be matched via visual concept agreement (score was $score, expected >= 45.0)",
            score >= 45.0f
        )
    }

    @Test
    fun testTC6_BankTransferSlipIsNotMatchedToLearningCategory() {
        // TC6: Bank transfer receipt with intent "SHOPPING" / payment, but category is "Tài liệu học tập".
        val category = Category(
            id = "cat_learning",
            name = "Tài liệu học tập",
            colorHex = "#F59E0B"
        )
        val photo = createPhoto(
            id = "photo_tc6",
            title = "Screenshot_20261111_Vietcombank",
            piiScrubbedText = """
                VIETCOMBANK DIGITAL BANKING
                GIAO DỊCH THÀNH CÔNG
                Số tiền: 500.000 VND
                Nội dung chuyển khoản: Nguyen Van A chuyen tien mua do the thao
                Thời gian: 11/11/2026 14:20:15
                Mã giao dịch: VCB123456789
            """.trimIndent(),
            semanticTags = listOf("receipt", "banking")
        )

        val score = RetroactiveCategorizer.evaluatePhotoConfidence(
            photo = photo,
            category = category,
            ftsMatched = false
        )

        assertFalse(
            "TC6: Unrelated bank transfer slip must NOT be matched to learning category (score was $score, expected < 45.0)",
            score >= 45.0f
        )
    }

    @Test
    fun testTC7_CustomShopeeOrderCategoryIsMatched() {
        // TC7: User creates custom category "Đơn hàng Shopee".
        // Screenshot has standard Shopee delivery success screen.
        val category = Category(
            id = "cat_shopee_custom",
            name = "Đơn hàng Shopee",
            colorHex = "#EE4D2D"
        )
        val photo = createPhoto(
            id = "photo_tc7",
            title = "Screenshot_20261111_ShopeeOrder",
            piiScrubbedText = """
                Shopee
                Đơn hàng SPX123456789
                Giao hàng thành công
                Người nhận: Nguyen Van A
                Sản phẩm: Tai nghe không dây Bluetooth TWS
                Tổng thanh toán: 350.000đ
                Đánh giá sản phẩm để nhận Shopee Xu
            """.trimIndent(),
            semanticTags = listOf("shopping", "ecommerce", "receipt")
        )

        val score = RetroactiveCategorizer.evaluatePhotoConfidence(
            photo = photo,
            category = category,
            ftsMatched = true
        )

        assertTrue(
            "TC7: Custom Shopee category must be matched (score was $score, expected >= 45.0)",
            score >= 45.0f
        )
    }

    @Test
    fun testTC8_CustomLegoCategoryWithKeywordMatches() {
        // TC8: User creates custom category "Lego". Photo title has Lego and OCR text mentions Lego set.
        val category = Category(
            id = "cat_lego_custom",
            name = "Lego",
            colorHex = "#F59E0B"
        )
        val photo = createPhoto(
            id = "photo_tc8",
            title = "IMG_20261111_LegoTechnic",
            piiScrubbedText = """
                LEGO Technic 42096 Porsche 911 RSR
                Age: 10+
                Pieces: 1580 pcs
                Building Kit Model Sports Car
            """.trimIndent(),
            semanticTags = listOf("toy", "lego", "model")
        )

        val score = RetroactiveCategorizer.evaluatePhotoConfidence(
            photo = photo,
            category = category,
            ftsMatched = true
        )

        assertTrue(
            "TC8: Custom Lego category must match photo with Lego keywords (score was $score, expected >= 45.0)",
            score >= 45.0f
        )
    }

    @Test
    fun testTC9_UnrelatedTextDoesNotMatchCustomCategory() {
        // TC9: User creates custom category "Gym & Fitness". Photo is a work email or coding screenshot.
        val category = Category(
            id = "cat_fitness",
            name = "Gym & Fitness",
            colorHex = "#10B981"
        )
        val photo = createPhoto(
            id = "photo_tc9",
            title = "Screenshot_20261111_CodeReview",
            piiScrubbedText = """
                Pull Request #142: Fix memory leak in Android Jetpack Compose
                Reviewed by senior developer
                All unit tests passed successfully.
                Branch: feature/retroactive-categorization
            """.trimIndent(),
            semanticTags = listOf("code", "software")
        )

        val score = RetroactiveCategorizer.evaluatePhotoConfidence(
            photo = photo,
            category = category,
            ftsMatched = false
        )

        assertFalse(
            "TC9: Unrelated code screenshot must NOT match Gym & Fitness (score was $score, expected < 45.0)",
            score >= 45.0f
        )
    }

    @Test
    fun testTC10_ArticleBelongingToLearningCategoryIsNotPenalized() {
        // TC10: Long academic document (length > 200, > 40 words) for category "Tài liệu học tập".
        // The phrase appears in the body (not in top 3 header lines).
        // Under previous logic, Guardrail A subtracted 20 points, dragging it below threshold.
        // Under improved logic, document-category guardrail exemption preserves score >= 45.0.
        val category = Category(
            id = "cat_study_materials",
            name = "Tài liệu học tập",
            colorHex = "#3B82F6"
        )
        val photo = createPhoto(
            id = "photo_tc10",
            title = "Screenshot_20261111_ResearchPaper",
            piiScrubbedText = """
                TRƯỜNG ĐẠI HỌC BÁCH KHOA HÀ NỘI
                VIỆN CÔNG NGHỆ THÔNG TIN VÀ TRUYỀN THÔNG
                HỆ THỐNG QUẢN LÝ DỮ LIỆU PHÂN TÁN
                
                Nội dung đề cương và tài liệu học tập môn học Kiến trúc Máy tính nâng cao.
                Sinh viên cần nghiên cứu kỹ các chương về bộ nhớ đệm Cache và đường ống phân luồng Pipeline.
                Bài tập lớn sẽ chiếm 40% trọng số điểm tổng kết của học kỳ này.
                Các nhóm sinh viên cần hoàn thành nộp báo cáo đúng thời hạn quy định trên cổng thông tin.
                Mọi thắc mắc liên hệ trực tiếp giảng viên hướng dẫn trong giờ hành chính.
            """.trimIndent()
        )

        val score = RetroactiveCategorizer.evaluatePhotoConfidence(
            photo = photo,
            category = category,
            ftsMatched = true
        )

        assertTrue(
            "TC10: Learning document should not be penalized by Guardrail A (score was $score, expected >= 45.0)",
            score >= 45.0f
        )
    }

    @Test
    fun testTC11_BatchVerificationHandlesMoreThanEightCandidates() = kotlinx.coroutines.runBlocking {
        // TC11: 12 photos matching custom category "Hóa đơn điện" at borderline confidence.
        // Under previous logic, max candidates was capped at 8, dropping photos 9..12.
        // Under improved batching and local fallback logic, all 12 photos are accurately evaluated.
        val category = Category(
            id = "cat_bills",
            name = "Hóa đơn điện",
            colorHex = "#F59E0B"
        )

        val photos = (1..12).map { index ->
            createPhoto(
                id = "photo_batch_$index",
                title = "Screenshot_20261111_Bill_$index",
                piiScrubbedText = """
                    Tổng công ty điện lực EVN thông báo tiền điện kỳ $index
                    Mã khách hàng: KH00$index
                    Số tiền cần thanh toán: 350.000đ
                """.trimIndent(),
                semanticTags = listOf("receipt", "bill")
            )
        }

        val matchedIds = RetroactiveCategorizer.findMatchingPhotosForCategory(
            category = category,
            photos = photos,
            apiKey = null // Tests on-device SmartHeuristic fallback path
        )

        assertTrue(
            "TC11: All 12 candidate photos must be matched (got ${matchedIds.size} matched)",
            matchedIds.size == 12
        )
    }

    @Test
    fun testTC12_OnDemandLexicalTokenMatchEnhancesConfidence() {
        // TC12: Photo has not been indexed in FTS yet (ftsMatched = false passed by caller),
        // but contains relevant category tokens. Lexical matching accurately identifies it.
        val category = Category(
            id = "cat_contracts",
            name = "Hợp đồng kinh tế",
            colorHex = "#6366F1"
        )
        val photo = createPhoto(
            id = "photo_tc12",
            title = "Screenshot_20261111_ContractPage",
            piiScrubbedText = """
                CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM
                Độc lập - Tự do - Hạnh phúc
                HỢP ĐỒNG KINH TẾ DỊCH VỤ PHẦN MỀM
                Số: 102/2026/HĐKT-PM
                Bên A: Công ty Cổ phần Công nghệ ABC
                Bên B: Công ty TNHH Giải pháp Phần mềm XYZ
            """.trimIndent()
        )

        val score = RetroactiveCategorizer.evaluatePhotoConfidence(
            photo = photo,
            category = category,
            ftsMatched = false
        )

        assertTrue(
            "TC12: Contract document should be matched with score >= 45.0 (score was $score)",
            score >= 45.0f
        )
    }
}

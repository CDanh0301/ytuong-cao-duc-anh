package com.example.test2.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class VectorSemanticSearchTest {

    @Test
    fun testVectorGenerationAndNormalization() {
        val vector = VectorSemanticSearchEngine.generateEmbedding(
            text = "Flight VJ123 boarding pass from SGN to HAN at 14:30",
            title = "Boarding Pass",
            intent = "EVENT"
        )

        assertEquals(VectorSemanticSearchEngine.VECTOR_DIMENSION, vector.size)

        // Verify L2 norm is approximately 1.0
        var sumSquares = 0f
        for (v in vector) {
            sumSquares += v * v
        }
        val norm = Math.sqrt(sumSquares.toDouble()).toFloat()
        assertEquals(1.0f, norm, 0.01f)
    }

    @Test
    fun testVectorSerialization() {
        val vector = VectorSemanticSearchEngine.generateEmbedding(
            text = "Total: 150.000 VND Shopee order receipt paid by card",
            title = "Receipt",
            intent = "SHOPPING"
        )

        val blob = VectorSemanticSearchEngine.vectorToBlob(vector)
        assertEquals(VectorSemanticSearchEngine.VECTOR_DIMENSION * 4, blob.size)

        val restored = VectorSemanticSearchEngine.blobToVector(blob)
        assertEquals(vector.size, restored.size)
        for (i in vector.indices) {
            assertEquals(vector[i], restored[i], 0.0001f)
        }
    }

    @Test
    fun testSemanticSimilarityAndClustering() {
        val flightVector = VectorSemanticSearchEngine.generateEmbedding(
            text = "Vietjet Air flight VJ198 boarding time 15:00 gate 4",
            title = "Flight Ticket",
            intent = "EVENT"
        )

        val foodReceiptVector = VectorSemanticSearchEngine.generateEmbedding(
            text = "Phở Bò 65.000đ Trà đá 5.000đ Total: 70.000đ receipt payment",
            title = "Hóa Đơn Quán Ăn",
            intent = "SHOPPING"
        )

        val codeErrorVector = VectorSemanticSearchEngine.generateEmbedding(
            text = "NullPointerException at com.example.app line 42 kotlin stacktrace",
            title = "Bug Crash Log",
            intent = "OTHER"
        )

        val indexed = mapOf(
            "photo_flight" to flightVector,
            "photo_food" to foodReceiptVector,
            "photo_code" to codeErrorVector
        )

        // Query 1: "airline flight" should match flight highest
        val flightResults = VectorSemanticSearchEngine.search("airline flight", indexed, minSimilarity = 0.20f)
        assertTrue(flightResults.isNotEmpty())
        assertEquals("photo_flight", flightResults.first().first)
        assertTrue(flightResults.first().second > 0.40f)

        // Query 2: "dinner food" should match food receipt highest
        val foodResults = VectorSemanticSearchEngine.search("dinner food", indexed, minSimilarity = 0.20f)
        assertTrue(foodResults.isNotEmpty())
        assertEquals("photo_food", foodResults.first().first)

        // Query 3: "programming code bug" should match code error highest
        val codeResults = VectorSemanticSearchEngine.search("programming code bug", indexed, minSimilarity = 0.20f)
        assertTrue(codeResults.isNotEmpty())
        assertEquals("photo_code", codeResults.first().first)
    }

    @Test
    fun testMountainSearchAndVietnameseDiacritics() {
        val mountainPhotoVector = VectorSemanticSearchEngine.generateEmbedding(
            text = "Đỉnh núi Fansipan phong cảnh thiên nhiên mây mù tuyệt đẹp",
            title = "Núi Fansipan",
            categoryNames = listOf("Nature")
        )

        val cityPhotoVector = VectorSemanticSearchEngine.generateEmbedding(
            text = "Toà nhà Bitexco Sài Gòn đường phố quận 1",
            title = "Sài Gòn City",
            categoryNames = listOf("City")
        )

        val indexed = mapOf(
            "photo_mountain" to mountainPhotoVector,
            "photo_city" to cityPhotoVector
        )

        // English query: "mountain"
        val mountainResults = VectorSemanticSearchEngine.search("mountain", indexed, minSimilarity = 0.22f)
        assertTrue("Searching 'mountain' should find mountain photo", mountainResults.isNotEmpty())
        assertEquals("photo_mountain", mountainResults.first().first)

        // Accented Vietnamese query: "núi"
        val nuiResults = VectorSemanticSearchEngine.search("núi", indexed, minSimilarity = 0.22f)
        assertTrue("Searching 'núi' should find mountain photo", nuiResults.isNotEmpty())
        assertEquals("photo_mountain", nuiResults.first().first)

        // Unaccented Vietnamese query: "nui"
        val unaccentedNuiResults = VectorSemanticSearchEngine.search("nui", indexed, minSimilarity = 0.22f)
        assertTrue("Searching 'nui' without accents should find mountain photo", unaccentedNuiResults.isNotEmpty())
        assertEquals("photo_mountain", unaccentedNuiResults.first().first)
    }

    @Test
    fun testCupAndProductSearch() {
        val cupVector = VectorSemanticSearchEngine.generateEmbedding(
            text = "Cốc sứ Bát Tràng uống cà phê văn phòng cao cấp 350ml",
            title = "Cốc Cà Phê",
            categoryNames = listOf("To-Buy")
        )

        val productVector = VectorSemanticSearchEngine.generateEmbedding(
            text = "Sản phẩm bàn phím cơ không dây bluetooth hàng chính hãng",
            title = "Bàn phím cơ",
            categoryNames = listOf("To-Buy")
        )

        val contractVector = VectorSemanticSearchEngine.generateEmbedding(
            text = "Hợp đồng thuê nhà và chứng minh nhân dân thẻ căn cước",
            title = "Hồ sơ cá nhân",
            categoryNames = listOf("Personal")
        )

        val indexed = mapOf(
            "photo_cup" to cupVector,
            "photo_product" to productVector,
            "photo_contract" to contractVector
        )

        // Query: "cup"
        val cupResults = VectorSemanticSearchEngine.search("cup", indexed, minSimilarity = 0.22f)
        assertTrue("Searching 'cup' should find cup photo", cupResults.isNotEmpty())
        assertEquals("photo_cup", cupResults.first().first)

        // Query: "cốc"
        val cocResults = VectorSemanticSearchEngine.search("cốc", indexed, minSimilarity = 0.22f)
        assertTrue("Searching 'cốc' should find cup photo", cocResults.isNotEmpty())
        assertEquals("photo_cup", cocResults.first().first)

        // Query: "coc" (unaccented)
        val unaccentedCocResults = VectorSemanticSearchEngine.search("coc", indexed, minSimilarity = 0.22f)
        assertTrue("Searching 'coc' should find cup photo", unaccentedCocResults.isNotEmpty())
        assertEquals("photo_cup", unaccentedCocResults.first().first)

        // Query: "product"
        val prodResults = VectorSemanticSearchEngine.search("product", indexed, minSimilarity = 0.22f)
        assertTrue("Searching 'product' should find product photo", prodResults.isNotEmpty())

        // Query: "sản phẩm" & "san pham"
        val spResults = VectorSemanticSearchEngine.search("sản phẩm", indexed, minSimilarity = 0.22f)
        assertTrue("Searching 'sản phẩm' should find product photo", spResults.isNotEmpty())

        val unaccentedSpResults = VectorSemanticSearchEngine.search("san pham", indexed, minSimilarity = 0.22f)
        assertTrue("Searching 'san pham' should find product photo", unaccentedSpResults.isNotEmpty())
    }

    @Test
    fun testSmartHeuristicParserNoHallucinationForMountain() {
        // Screenshot of a mountain with phone status bar (10:30, 100%, 4G)
        val ocrWithStatusBar = """
            10:30
            100% 4G Viettel
            Đỉnh núi Fansipan hùng vĩ mây ngàn
        """.trimIndent()

        val parsed = SmartHeuristicParser.parse(ocrWithStatusBar, "IMG_Mountain.jpg")

        // Must NOT be EVENT or READING
        assertEquals("OTHER", parsed.detectedIntent)
        assertEquals("cat_nature", parsed.targetCategoryId)
        assertNull("Mountain photo must not have a calendar event scheduled!", parsed.suggestedEventDateMillis)
    }

    @Test
    fun testSmartHeuristicParserLegitimateEvent() {
        val meetingOcr = """
            10:30
            100% 4G
            Lịch họp nhóm dự án Antigravity
            Ngày 15/12/2026 lúc 14:30
            Phòng họp 302
        """.trimIndent()

        val parsed = SmartHeuristicParser.parse(meetingOcr, "Screenshot_Meeting.jpg")

        assertEquals("EVENT", parsed.detectedIntent)
        assertEquals("cat_events", parsed.targetCategoryId)
        assertNotNull("Real meeting screenshot must have event date scheduled", parsed.suggestedEventDateMillis)

        val cal = Calendar.getInstance().apply {
            timeInMillis = parsed.suggestedEventDateMillis!!
        }
        assertEquals(2026, cal.get(Calendar.YEAR))
        assertEquals(Calendar.DECEMBER, cal.get(Calendar.MONTH))
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(14, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, cal.get(Calendar.MINUTE))
    }

    @Test
    fun testPersonPhotoCategorizedAsPersonalAndNeverNature() {
        // 1. Vietnamese person portrait & family photo with "họ", "đình" (which previously falsely triggered nature)
        val personOcr = """
            Họ và tên: Nguyễn Đình Tuấn
            Ngày sinh: 15/05/1998
            Quê quán: Hà Nội
            Ảnh gia đình và chân dung cá nhân
        """.trimIndent()

        val parsedVn = SmartHeuristicParser.parse(personOcr, "IMG_portrait_01.jpg")
        assertEquals("cat_personal", parsedVn.targetCategoryId)
        assertNotEquals("cat_nature", parsedVn.targetCategoryId)
        assertNull("Person photo must never have fake calendar date", parsedVn.suggestedEventDateMillis)

        // 2. English selfie photo
        val selfieParsed = SmartHeuristicParser.parse("Great day with friends, selfie at the coffee shop", "selfie_photo.jpg")
        assertEquals("cat_personal", selfieParsed.targetCategoryId)
        assertNotEquals("cat_nature", selfieParsed.targetCategoryId)

        // 3. CCCD / ID card
        val idParsed = SmartHeuristicParser.parse("Căn cước công dân - Số: 0123456789", "IMG_CCCD.jpg")
        assertEquals("cat_personal", idParsed.targetCategoryId)
    }

    @Test
    fun testWordNatureAloneDoesNotTriggerNatureCategory() {
        // An article discussing the "nature" of a technical or social concept
        val techArticleOcr = """
            The distributed nature of microservices and system design.
            In this chapter, the author explains the fundamental nature of distributed computing.
        """.trimIndent()

        val parsed = SmartHeuristicParser.parse(techArticleOcr, "Article_System_Design.pdf")
        assertNotEquals("cat_nature", parsed.targetCategoryId)
        assertEquals("cat_reading", parsed.targetCategoryId)

        // Short text just containing "nature"
        val generalText = SmartHeuristicParser.parse("Good nature of human behavior", "note.txt")
        assertNotEquals("cat_nature", generalText.targetCategoryId)
    }

    @Test
    fun testAllFiveStandardCategoriesUtilization() {
        // 1. cat_events
        val event = SmartHeuristicParser.parse("Chuyến bay VN123 lúc 15h30 ngày mai", "Ticket.png")
        assertEquals("cat_events", event.targetCategoryId)
        assertEquals("EVENT", event.detectedIntent)

        // 2. cat_tobuy
        val shopping = SmartHeuristicParser.parse("Cốc sứ trắng ceramic mug, tổng cộng: 150.000₫", "Order.jpg")
        assertEquals("cat_tobuy", shopping.targetCategoryId)
        assertEquals("SHOPPING", shopping.detectedIntent)

        // 3. cat_reading
        val reading = SmartHeuristicParser.parse("Bài viết: Tác giả phân tích kiến trúc phần mềm", "Blog.png")
        assertEquals("cat_reading", reading.targetCategoryId)
        assertEquals("READING", reading.detectedIntent)

        // 4. cat_nature
        val nature = SmartHeuristicParser.parse("Đỉnh núi Fansipan hùng vĩ, phong cảnh thiên nhiên tuyệt đẹp", "Mountain.jpg")
        assertEquals("cat_nature", nature.targetCategoryId)
        assertEquals("OTHER", nature.detectedIntent)

        // 5. cat_personal
        val personal = SmartHeuristicParser.parse("Ảnh chân dung tự sướng của tôi", "My_Selfie.jpg")
        assertEquals("cat_personal", personal.targetCategoryId)
        assertEquals("OTHER", personal.detectedIntent)
    }

    @Test
    fun testNewsArticleFrom24hClassifiedAsReadingAndNotShopping() {
        val ocr24h = """
            24h - TRANG THÔNG TIN ĐIỆN TỬ TỔNG HỢP
            NGOẠI HẠNG ANH TIN TỨC BÓNG ĐÁ KINH DOANH
            Đường phố Hà Nội ùn tắc kéo dài sáng đầu tuần mưa lạnh
            Sự kiện: Thời sự
            Mưa lớn sáng 9/11 cùng áp lực giao thông giờ cao điểm khiến hàng loạt tuyến đường Hà Nội ùn tắc nghiêm trọng. Hàng nghìn phương tiện chôn chân, người dân chật vật nhích từng mét.
            Sáng 09/11, TP Hà Nội có mưa rào và rải rác, trời chuyển rét, nhiệt độ thấp nhất giảm sâu còn khoảng 17-19 độ, cao nhất 19-21 độ.
        """.trimIndent()

        val parsed = SmartHeuristicParser.parse(ocr24h, "Screenshot_20261109.jpg")
        assertEquals("cat_reading", parsed.targetCategoryId)
        assertEquals("READING", parsed.detectedIntent)
        assertNull(parsed.suggestedEventDateMillis)
        assertEquals("Đường phố Hà Nội ùn tắc kéo dài sáng đầu tuần mưa lạnh", parsed.suggestedEventTitle)
    }

    @Test
    fun testWebSceneryScreenshotsCategorizedAsNature() {
        // 1. National Geographic landscape screenshot
        val natGeoOcr = """
            11:42
            nationalgeographic.com
            1
        """.trimIndent()
        val parsedNatGeo = SmartHeuristicParser.parse(natGeoOcr, "Screenshot_NatGeo.jpg")
        assertEquals("cat_nature", parsedNatGeo.targetCategoryId)
        assertEquals("OTHER", parsedNatGeo.detectedIntent)
        assertNull("Scenery screenshot must never fabricate a calendar date", parsedNatGeo.suggestedEventDateMillis)

        // 2. Pexels golden autumn forest screenshot
        val pexelsOcr = """
            11:42
            pexels.com/photo/golde
            Pexels
            Free download
            Edit
        """.trimIndent()
        val parsedPexels = SmartHeuristicParser.parse(pexelsOcr, "Screenshot_Pexels.jpg")
        assertEquals("cat_nature", parsedPexels.targetCategoryId)
        assertEquals("OTHER", parsedPexels.detectedIntent)
        assertNull("Pexels photo must never have a calendar event", parsedPexels.suggestedEventDateMillis)
    }

    @Test
    fun testPersonScreenshotWithFaceOrWikimediaCategorizedAsPersonal() {
        // 1. Wikimedia portrait screenshot with on-device face detected
        val wikiOcr = """
            11:42
            upload.wikimedia.org/wi
            1
        """.trimIndent()
        val parsedWikiFace = SmartHeuristicParser.parse(wikiOcr, "Screenshot_Person.jpg", hasFace = true)
        assertEquals("cat_personal", parsedWikiFace.targetCategoryId)
        assertNotEquals("cat_nature", parsedWikiFace.targetCategoryId)
        assertNull("Portrait photo must never have a calendar schedule", parsedWikiFace.suggestedEventDateMillis)

        // 2. Wikimedia portrait screenshot via domain/keyword heuristic
        val parsedWikiKeyword = SmartHeuristicParser.parse(
            "upload.wikimedia.org/wikipedia/commons/portrait_photo.jpg",
            "Screenshot_Wiki_Portrait.jpg"
        )
        assertEquals("cat_personal", parsedWikiKeyword.targetCategoryId)
        assertNotEquals("cat_nature", parsedWikiKeyword.targetCategoryId)
    }

    @Test
    fun testBalancedScoringForProductsAndSceneryWithoutOverfitting() {
        // 1. Image 2: Cooking Hotpot with "Gia Đình" in title -> MUST be cat_tobuy, NEVER personal!
        val hotpotOcr = """
            google.com/search?clie
            shopee.vn
            Nồi Lẩu Inox 26cm, Nồi Inox Nấu Lẩu Nấu Canh Gia Đình Quán Ăn Tiện Lợi
            Mức giá trước đây
            shopee.vn
            76.500 đ
            Còn hàng ở trên mạng
            4.5/5
        """.trimIndent()
        val parsedHotpot = SmartHeuristicParser.parse(hotpotOcr, "Screenshot_Hotpot.jpg")
        assertEquals("cat_tobuy", parsedHotpot.targetCategoryId)
        assertEquals("SHOPPING", parsedHotpot.detectedIntent)

        // 2. Image 3: Sony 98" TV on dientumienbac with 137M price and "hỗ trợ" -> MUST be cat_tobuy, NEVER nature!
        val sonyTvOcr = """
            dientumienbac.com/pro
            Google Tivi Sony True RGB BRAVIA 7 II 98 Inch K-98XR70M2
            Thương hiệu: Sony
            Mã sản phẩm: K-98XR70M2
            Bảo hành: 24 Tháng
            137,090,000₫ 199,990,000₫ -31%
            Giá đã bao gồm VAT
            KHUYẾN MÃI - ƯU ĐÃI
            Quý khách là đại lý, nhà thầu, thợ cần hỗ trợ số lượng lớn: 024.6291.3569
            Tình trạng: Còn Hàng
            Số lượng: 1
        """.trimIndent()
        val parsedTv = SmartHeuristicParser.parse(sonyTvOcr, "Screenshot_SonyTV.jpg")
        assertEquals("cat_tobuy", parsedTv.targetCategoryId)
        assertEquals("SHOPPING", parsedTv.detectedIntent)

        // 3. Image 4: Shopee DIY with "người dùng" -> MUST be cat_tobuy, NEVER personal!
        val diyOcr = """
            shopee
            Hiệu ứng trang trí diy tông màu nâu đen
            5.0 (25 bài đánh giá của người dùng)
            Giá thông thường là 15.000 đ
            Shopee 15.000 đ
            Còn hàng ở trên mạng
            Tổng: 15.000 đ + phí vận chuyển
        """.trimIndent()
        val parsedDiy = SmartHeuristicParser.parse(diyOcr, "Screenshot_DIY.jpg")
        assertEquals("cat_tobuy", parsedDiy.targetCategoryId)
        assertEquals("SHOPPING", parsedDiy.detectedIntent)

        // 4. Image 1: National Geographic with potential prefix clipping
        val natGeoClippedOcr = """
            12:05
            nationalgeographic.com
            1
        """.trimIndent()
        val parsedNatGeo = SmartHeuristicParser.parse(natGeoClippedOcr, "Screenshot_NatGeo.jpg")
        assertEquals("cat_nature", parsedNatGeo.targetCategoryId)
    }

    @Test
    fun testCustomAiCategoriesMatchingForPro() {
        val customCategories = listOf(
            com.example.test2.shared.models.Category(
                id = "cat_custom_electric",
                name = "Hóa đơn điện nước",
                colorHex = "#EF4444",
                iconName = "tag",
                createdAt = 1000L
            ),
            com.example.test2.shared.models.Category(
                id = "cat_custom_gym",
                name = "Gym & Workout",
                colorHex = "#10B981",
                iconName = "tag",
                createdAt = 2000L
            )
        )

        // 1. Screenshot matching custom category "Hóa đơn điện nước"
        val electricOcr = "EVN HANOI: Hóa đơn điện nước kỳ tháng 11 số tiền thanh toán 450.000đ"
        val parsedElectric = SmartHeuristicParser.parse(electricOcr, "Screenshot_EVN.png", customCategories)
        assertEquals("cat_custom_electric", parsedElectric.targetCategoryId)

        // 2. Screenshot matching custom category "Gym & Workout"
        val gymOcr = "Lịch tập gym tuần này: Thứ 2 ngực, thứ 4 lưng xô, thứ 6 chân"
        val parsedGym = SmartHeuristicParser.parse(gymOcr, "Gym_Schedule.png", customCategories)
        assertEquals("cat_custom_gym", parsedGym.targetCategoryId)
    }

    @Test
    fun testLocalEncryptedPhotoStorageCryptoManager() {
        val originalText = "ANTIGRAVITY_SECRET_IMAGE_BYTES_2026_AES_GCM"
        val originalBytes = originalText.toByteArray(Charsets.UTF_8)

        // 1. Encrypt in-memory
        val encrypted = com.example.test2.storage.LocalCryptoManager.encrypt(originalBytes)
        assertTrue(encrypted.size > originalBytes.size)
        // Verify MAGIC header 'ENC1'
        assertEquals(0x45.toByte(), encrypted[0])
        assertEquals(0x4E.toByte(), encrypted[1])
        assertEquals(0x43.toByte(), encrypted[2])
        assertEquals(0x31.toByte(), encrypted[3])

        // 2. Decrypt in-memory
        val decrypted = com.example.test2.storage.LocalCryptoManager.decrypt(encrypted)
        assertEquals(originalText, String(decrypted, Charsets.UTF_8))

        // 3. Encrypted File Streaming test
        val tempPlainFile = java.io.File.createTempFile("test_plain", ".jpg").apply {
            writeBytes(originalBytes)
            deleteOnExit()
        }
        val tempEncFile = java.io.File.createTempFile("test_secure", ".jpg.enc").apply {
            deleteOnExit()
        }

        java.io.FileInputStream(tempPlainFile).use { input ->
            com.example.test2.storage.LocalCryptoManager.encryptStreamToFile(input, tempEncFile)
        }

        assertTrue(tempEncFile.length() > originalBytes.size)
        assertTrue(com.example.test2.storage.LocalCryptoManager.isEncrypted(tempEncFile))

        // Decrypt from file stream
        val readBackBytes = com.example.test2.storage.LocalCryptoManager.openDecryptedInputStream(tempEncFile).use { stream ->
            stream.readBytes()
        }
        assertEquals(originalText, String(readBackBytes, Charsets.UTF_8))
    }

    @Test
    fun testSixUserUploadedScreenshotsClassification() {
        // 1. Woman portrait screenshot from upload.wikimedia.org -> cat_personal ("Cá nhân & Giấy tờ")
        val womanWikiOcr = "11:42\nupload.wikimedia.org/wi\n+ 1"
        val womanWikiParsed = SmartHeuristicParser.parse(womanWikiOcr, "secure_img_woman.jpg", hasFace = true)
        assertEquals("cat_personal", womanWikiParsed.targetCategoryId)
        assertEquals("OTHER", womanWikiParsed.detectedIntent)
        assertNull(womanWikiParsed.suggestedEventDateMillis)

        // URL domain wikimedia classifies it to cat_personal even if face detector is offline
        val womanWikiNoFace = SmartHeuristicParser.parse(womanWikiOcr, "secure_img_woman.jpg", hasFace = false)
        assertEquals("cat_personal", womanWikiNoFace.targetCategoryId)

        // 2. National Geographic mountain lake sunset -> cat_nature ("Thiên nhiên & Phong cảnh")
        val natGeoOcr = "11:42\nnationalgeographic.com\n+ 1"
        val natGeoParsed = SmartHeuristicParser.parse(natGeoOcr, "secure_img_natgeo.jpg")
        assertEquals("cat_nature", natGeoParsed.targetCategoryId)
        assertEquals("OTHER", natGeoParsed.detectedIntent)
        assertNull(natGeoParsed.suggestedEventDateMillis)

        // 3. Pexels golden autumn forest -> cat_nature ("Thiên nhiên & Phong cảnh")
        val pexelsOcr = "11:42\npexels.com/photo/golden\nFree download\npexels\nEdit"
        val pexelsParsed = SmartHeuristicParser.parse(pexelsOcr, "secure_img_pexels.jpg")
        assertEquals("cat_nature", pexelsParsed.targetCategoryId)
        assertEquals("OTHER", pexelsParsed.detectedIntent)
        assertNull(pexelsParsed.suggestedEventDateMillis)

        // 4. Shopee Nồi Lẩu Inox with "Gia Đình" -> cat_tobuy ("Cần mua & Chi tiêu"), NEVER personal!
        val hotpotOcr = """
            shopee.vn
            Nồi Lẩu Inox 26cm, Nồi Inox Nấu Lẩu Nấu Canh Gia Đình Quán Ăn Tiện Lợi
            76.500 đ
            Còn hàng ở trên mạng
        """.trimIndent()
        val hotpotParsed = SmartHeuristicParser.parse(hotpotOcr, "secure_img_hotpot.jpg")
        assertEquals("cat_tobuy", hotpotParsed.targetCategoryId)
        assertEquals("SHOPPING", hotpotParsed.detectedIntent)
        assertNull(hotpotParsed.suggestedEventDateMillis)

        // 5. Sony 98" Bravia TV with parrot picture -> cat_tobuy ("Cần mua & Chi tiêu"), NEVER nature!
        val tvOcr = """
            dientumienbac.com/pro
            Google Tivi Sony True RGB BRAVIA 7 II 98 Inch K-98XR70M2
            Thương hiệu: Sony
            137,090,000₫
            Khuyến mãi - Ưu đãi
            Còn Hàng
        """.trimIndent()
        val tvParsed = SmartHeuristicParser.parse(tvOcr, "secure_img_sony_tv.jpg")
        assertEquals("cat_tobuy", tvParsed.targetCategoryId)
        assertEquals("SHOPPING", tvParsed.detectedIntent)
        assertNull(tvParsed.suggestedEventDateMillis)

        // 6. Shopee DIY Decoration with "người dùng" -> cat_tobuy ("Cần mua & Chi tiêu"), NEVER personal!
        val diyOcr = """
            shopee.vn
            Hiệu ứng trang trí diy tông màu nâu đen
            Giá thông thường là 15.000 đ
            15.000 đ
            Còn hàng ở trên mạng
            Bài đánh giá của người dùng
        """.trimIndent()
        val diyParsed = SmartHeuristicParser.parse(diyOcr, "secure_img_diy.jpg")
        assertEquals("cat_tobuy", diyParsed.targetCategoryId)
        assertEquals("SHOPPING", diyParsed.detectedIntent)
        assertNull(diyParsed.suggestedEventDateMillis)
    }

    @Test
    fun testVietnameseTreeSemanticSearchAndZeroFalsePositiveOnProducts() {
        // Embed the 5 actual user screenshots
        val treeVector = VectorSemanticSearchEngine.generateEmbedding(
            text = "pexels.com/photo/golden Free download pexels Edit golden trees foliage autumn forest",
            title = "Golden Trees Autumn",
            intent = "OTHER",
            categoryNames = listOf("Thiên nhiên & Phong cảnh")
        )

        val diyVector = VectorSemanticSearchEngine.generateEmbedding(
            text = "shopee.vn Hiệu ứng trang trí diy tông màu nâu đen Giá thông thường là 15.000 đ Còn hàng ở trên mạng Bài đánh giá của người dùng",
            title = "Hiệu ứng trang trí diy",
            intent = "SHOPPING",
            categoryNames = listOf("Cần mua & Chi tiêu")
        )

        val potVector = VectorSemanticSearchEngine.generateEmbedding(
            text = "shopee.vn Nồi Lẩu Inox 26cm Nồi Inox Nấu Lẩu Nấu Canh Gia Đình Quán Ăn Tiện Lợi 76.500 đ Còn hàng ở trên mạng",
            title = "Nồi Lẩu Inox 26cm",
            intent = "SHOPPING",
            categoryNames = listOf("Cần mua & Chi tiêu")
        )

        val tvVector = VectorSemanticSearchEngine.generateEmbedding(
            text = "dientumienbac.com/pro Google Tivi Sony True RGB BRAVIA 7 II 98 Inch K-98XR70M2 Thương hiệu Sony 137,090,000₫ Khuyến mãi Ưu đãi Còn Hàng",
            title = "Google Tivi Sony",
            intent = "SHOPPING",
            categoryNames = listOf("Cần mua & Chi tiêu")
        )

        val portraitVector = VectorSemanticSearchEngine.generateEmbedding(
            text = "upload.wikimedia.org Chân dung phụ nữ cá nhân hồ sơ profile",
            title = "Chân dung",
            intent = "OTHER",
            categoryNames = listOf("Cá nhân & Giấy tờ")
        )

        val indexed = mapOf(
            "photo_tree" to treeVector,
            "photo_diy" to diyVector,
            "photo_pot" to potVector,
            "photo_tv" to tvVector,
            "photo_portrait" to portraitVector
        )

        // 1. Searching "cây" (Vietnamese for tree) MUST find photo_tree with similarity >= 0.30 (threshold is 0.28)
        // and MUST NOT match photo_diy (which previously falsely matched at 22%)
        val cayResults = VectorSemanticSearchEngine.search("cây", indexed, minSimilarity = 0.28f)
        assertTrue("Searching 'cây' must find the tree photo", cayResults.isNotEmpty())
        assertEquals("photo_tree", cayResults.first().first)
        assertTrue("Similarity for 'cây' on tree photo should be >= 0.30, was ${cayResults.first().second}", cayResults.first().second >= 0.30f)
        assertTrue("Searching 'cây' must NEVER match unrelated DIY product", cayResults.none { it.first == "photo_diy" })

        // 2. Searching "cay" (unaccented) MUST also find photo_tree
        val unaccentedCayResults = VectorSemanticSearchEngine.search("cay", indexed, minSimilarity = 0.28f)
        assertTrue("Searching 'cay' without accents must find the tree photo", unaccentedCayResults.isNotEmpty())
        assertEquals("photo_tree", unaccentedCayResults.first().first)
        assertTrue(unaccentedCayResults.first().second >= 0.30f)
        assertTrue("Searching 'cay' must NEVER match unrelated DIY product", unaccentedCayResults.none { it.first == "photo_diy" })

        // 3. Searching "tree" (English) MUST find photo_tree
        val treeResults = VectorSemanticSearchEngine.search("tree", indexed, minSimilarity = 0.28f)
        assertTrue("Searching 'tree' must find the tree photo", treeResults.isNotEmpty())
        assertEquals("photo_tree", treeResults.first().first)
        assertTrue(treeResults.first().second >= 0.30f)
        assertTrue(treeResults.none { it.first == "photo_diy" })

        // 4. Searching "nồi" (Vietnamese for pot) MUST find photo_pot
        val noiResults = VectorSemanticSearchEngine.search("nồi", indexed, minSimilarity = 0.28f)
        assertTrue("Searching 'nồi' must find pot photo", noiResults.isNotEmpty())
        assertEquals("photo_pot", noiResults.first().first)

        // 5. Searching "tivi" MUST find photo_tv
        val tiviResults = VectorSemanticSearchEngine.search("tivi", indexed, minSimilarity = 0.28f)
        assertTrue("Searching 'tivi' must find TV photo", tiviResults.isNotEmpty())
        assertEquals("photo_tv", tiviResults.first().first)

        // 6. Searching "chân dung" MUST find photo_portrait
        val chanDungResults = VectorSemanticSearchEngine.search("chân dung", indexed, minSimilarity = 0.28f)
        assertTrue("Searching 'chân dung' must find portrait photo", chanDungResults.isNotEmpty())
        assertEquals("photo_portrait", chanDungResults.first().first)
    }

    @Test
    fun testCustomAiCategoryVectorProjection() {
        val customCategories = listOf(
            com.example.test2.shared.models.Category(
                id = "cat_pet",
                name = "Thú cưng",
                colorHex = "#10B981",
                iconName = "tag",
                createdAt = 1000L
            ),
            com.example.test2.shared.models.Category(
                id = "cat_bill",
                name = "Hóa đơn tiền điện",
                colorHex = "#EF4444",
                iconName = "tag",
                createdAt = 2000L
            )
        )

        val petPhoto = VectorSemanticSearchEngine.generateEmbedding(
            text = "Hình chú chó cún golden đáng yêu đang chạy nhảy trên bãi cỏ",
            title = "Cún con",
            categoryNames = listOf("Thú cưng"),
            customCategories = customCategories
        )

        val billPhoto = VectorSemanticSearchEngine.generateEmbedding(
            text = "Tổng công ty Điện lực EVN: Giấy báo tiền điện tháng 12 số tiền 560.000đ",
            title = "Hóa đơn EVN",
            categoryNames = listOf("Hóa đơn tiền điện"),
            customCategories = customCategories
        )

        val naturePhoto = VectorSemanticSearchEngine.generateEmbedding(
            text = "Rừng thông Đà Lạt phong cảnh thiên nhiên mây mù",
            title = "Đà Lạt",
            categoryNames = listOf("Thiên nhiên & Phong cảnh"),
            customCategories = customCategories
        )

        val indexed = mapOf(
            "photo_pet" to petPhoto,
            "photo_bill" to billPhoto,
            "photo_nature" to naturePhoto
        )

        // 1. Searching for custom category "Thú cưng" finds pet photo with high similarity
        val petResults = VectorSemanticSearchEngine.search("Thú cưng", indexed, customCategories = customCategories, minSimilarity = 0.28f)
        assertTrue("Searching 'Thú cưng' must find pet photo", petResults.isNotEmpty())
        assertEquals("photo_pet", petResults.first().first)
        assertTrue("Pet similarity should be >= 0.35, was ${petResults.first().second}", petResults.first().second >= 0.35f)

        // 2. Searching for custom category "Hóa đơn tiền điện" finds bill photo
        val billResults = VectorSemanticSearchEngine.search("Hóa đơn tiền điện", indexed, customCategories = customCategories, minSimilarity = 0.28f)
        assertTrue("Searching 'Hóa đơn tiền điện' must find bill photo", billResults.isNotEmpty())
        assertEquals("photo_bill", billResults.first().first)
        assertTrue(billResults.first().second >= 0.35f)

        // 3. Neither custom query matches unrelated nature photo
        assertTrue("Pet search must not match nature", petResults.none { it.first == "photo_nature" })
        assertTrue("Bill search must not match nature", billResults.none { it.first == "photo_nature" })
    }

    @Test
    fun testVehiclesAndMedicineSemanticSearch() {
        val vehiclePhoto = VectorSemanticSearchEngine.generateEmbedding(
            text = "Xe máy Honda AirBlade 150cc tay ga chính hãng màu đen nhám tiết kiệm xăng",
            title = "Xe AirBlade",
            intent = "SHOPPING",
            categoryNames = listOf("Cần mua & Chi tiêu")
        )

        val medicinePhoto = VectorSemanticSearchEngine.generateEmbedding(
            text = "Bác sĩ Bệnh viện Bạch Mai kê đơn thuốc hạ sốt paracetamol và kháng sinh 500mg",
            title = "Đơn thuốc khám bệnh",
            intent = "OTHER",
            categoryNames = listOf("Cá nhân & Giấy tờ")
        )

        val treePhoto = VectorSemanticSearchEngine.generateEmbedding(
            text = "Rừng cây xanh thiên nhiên bóng mát tán lá đẹp",
            title = "Rừng cây xanh",
            intent = "OTHER",
            categoryNames = listOf("Thiên nhiên & Phong cảnh")
        )

        val indexed = mapOf(
            "photo_vehicle" to vehiclePhoto,
            "photo_medicine" to medicinePhoto,
            "photo_tree" to treePhoto
        )

        // 1. Searching "xe máy"
        val xeResults = VectorSemanticSearchEngine.search("xe máy", indexed, minSimilarity = 0.28f)
        assertTrue("Searching 'xe máy' must find vehicle photo", xeResults.isNotEmpty())
        assertEquals("photo_vehicle", xeResults.first().first)

        // 2. Searching "thuốc"
        val thuocResults = VectorSemanticSearchEngine.search("thuốc", indexed, minSimilarity = 0.28f)
        assertTrue("Searching 'thuốc' must find medicine photo", thuocResults.isNotEmpty())
        assertEquals("photo_medicine", thuocResults.first().first)

        // 3. Searching "bác sĩ"
        val bacSiResults = VectorSemanticSearchEngine.search("bác sĩ", indexed, minSimilarity = 0.28f)
        assertTrue("Searching 'bác sĩ' must find medicine photo", bacSiResults.isNotEmpty())
        assertEquals("photo_medicine", bacSiResults.first().first)

        // 4. Searching "cây" must find tree photo and never match vehicle or medicine
        val treeResults = VectorSemanticSearchEngine.search("cây", indexed, minSimilarity = 0.28f)
        assertTrue("Searching 'cây' must find tree photo", treeResults.isNotEmpty())
        assertEquals("photo_tree", treeResults.first().first)
        assertTrue(treeResults.none { it.first == "photo_vehicle" })
        assertTrue(treeResults.none { it.first == "photo_medicine" })
    }

    @Test
    fun testContextAwareSchedulingAndNotificationSeparation() {
        // 1. EVENT: Flight Ticket with date -> MUST have suggestedEventTitle and date
        val flightOcr = """
            Vietnam Airlines
            Chuyến bay VN123 từ Hà Nội đến Đà Nẵng
            Ngày 20/12/2026 lúc 10:30
            Cổng 03
        """.trimIndent()
        val eventParsed = SmartHeuristicParser.parse(flightOcr, "Screenshot_Flight.png")
        assertEquals("EVENT", eventParsed.detectedIntent)
        assertNotNull("Event must have event title", eventParsed.suggestedEventTitle)
        assertNotNull("Event must have event date", eventParsed.suggestedEventDateMillis)
        val shouldNotifyEvent = (eventParsed.detectedIntent == "EVENT" || eventParsed.detectedIntent == "SHOPPING") &&
                !eventParsed.suggestedEventTitle.isNullOrBlank()
        assertTrue("Event must trigger scheduling notification", shouldNotifyEvent)

        // 2. SHOPPING: Product with clean title -> MUST have suggestedEventTitle (product name), but no fake date
        val shopeeOcr = """
            shopee.vn
            Nồi Lẩu Inox 26cm, Nồi Inox Nấu Lẩu Nấu Canh Gia Đình Quán Ăn Tiện Lợi
            76.500 đ
            Còn hàng ở trên mạng
        """.trimIndent()
        val shoppingParsed = SmartHeuristicParser.parse(shopeeOcr, "Screenshot_Shopee.jpg")
        assertEquals("SHOPPING", shoppingParsed.detectedIntent)
        assertNotNull("Shopping must have product title", shoppingParsed.suggestedEventTitle)
        assertEquals("Nồi Lẩu Inox 26cm, Nồi Inox Nấu Lẩu Nấu Canh Gia Đình Quán Ăn Tiện Lợi", shoppingParsed.suggestedEventTitle)
        assertNull("Shopping with no date in text must not have fake event date", shoppingParsed.suggestedEventDateMillis)
        val shouldNotifyShopping = (shoppingParsed.detectedIntent == "EVENT" || shoppingParsed.detectedIntent == "SHOPPING") &&
                !shoppingParsed.suggestedEventTitle.isNullOrBlank()
        assertTrue("Shopping must trigger shopping reminder notification", shouldNotifyShopping)

        // 3. NATURE: Scenery screenshot -> MUST NOT have event title, MUST NOT notify
        val natureOcr = """
            nationalgeographic.com
            Đỉnh núi Fansipan hùng vĩ mây ngàn phong cảnh thiên nhiên
        """.trimIndent()
        val natureParsed = SmartHeuristicParser.parse(natureOcr, "Screenshot_Nature.jpg")
        assertEquals("OTHER", natureParsed.detectedIntent)
        assertNull("Nature photo must have null suggestedEventTitle", natureParsed.suggestedEventTitle)
        assertNull("Nature photo must have null suggestedEventDateMillis", natureParsed.suggestedEventDateMillis)
        val shouldNotifyNature = (natureParsed.detectedIntent == "EVENT" || natureParsed.detectedIntent == "SHOPPING") &&
                !natureParsed.suggestedEventTitle.isNullOrBlank()
        assertTrue("Nature photo must NEVER trigger scheduling notification", !shouldNotifyNature)

        // 4. PERSONAL: Selfie/ID screenshot -> MUST NOT have event title, MUST NOT notify
        val personOcr = """
            Căn cước công dân - Họ và tên: Nguyễn Văn An
            Ảnh chân dung cá nhân
        """.trimIndent()
        val personParsed = SmartHeuristicParser.parse(personOcr, "Screenshot_CCCD.jpg", hasFace = true)
        assertEquals("OTHER", personParsed.detectedIntent)
        assertNull("Personal photo must have null suggestedEventTitle", personParsed.suggestedEventTitle)
        assertNull("Personal photo must have null suggestedEventDateMillis", personParsed.suggestedEventDateMillis)
        val shouldNotifyPerson = (personParsed.detectedIntent == "EVENT" || personParsed.detectedIntent == "SHOPPING") &&
                !personParsed.suggestedEventTitle.isNullOrBlank()
        assertTrue("Personal photo must NEVER trigger scheduling notification", !shouldNotifyPerson)
    }

    @Test
    fun testLotteCinemaMovieScheduleSmartTitleAndStructuredDescription() {
        val lotteOcr = """
            D Ngay dang: 04.05.2022
            Một chiếc lịch lên hơi muộn xíu.
            Lịch chiếu phim của 3 ngày 04, 05, 06/05/2022
            LOTTE
            Theo dõi các ghế còn trống tại app Lotte Cinema nha cả nhà.
            10:00
            DA VŨ THIU HỚN LOAN
            10:30
            12:30
            13:00
            MNE STLOS
            13:30
            TAG:
            DoaR SIHYE
            U soM 04.05202
            C12
            15:00
            15:30
            VINCOM
            16:00
            17:30
            022 6366 2555
            Lịch Chiếu Phim
            18:00
        """.trimIndent()

        val parsed = SmartHeuristicParser.parse(lotteOcr, "Screenshot_20220504.png")
        assertEquals("EVENT", parsed.detectedIntent)

        val title = parsed.suggestedEventTitle
        assertNotNull("Title must not be null", title)
        // CRITICAL: Title MUST NOT be OCR garbage like 'D Ngay dang' or status/tag artifacts
        assertTrue("Title must not contain 'D Ngay dang'", !title!!.contains("D Ngay dang", ignoreCase = true))
        assertTrue("Title must not contain 'TAG'", !title.contains("TAG", ignoreCase = true))
        assertTrue("Title must identify movie schedule and theater",
            title.contains("Lịch chiếu phim", ignoreCase = true) || title.contains("Lotte", ignoreCase = true))

        val desc = parsed.suggestedEventDescription
        assertNotNull("Description must not be null", desc)
        // CRITICAL: Description MUST NOT be the raw 25-line OCR dump!
        assertNotEquals(lotteOcr, desc)
        assertTrue("Description must be structured with event section", desc!!.contains("📅 Chi tiết sự kiện & Lịch hẹn:"))
        assertTrue("Description must highlight event", desc.contains("• Sự kiện:"))
        assertTrue("Description must detect Lotte Cinema venue", desc.contains("Lotte Cinema", ignoreCase = true))
        assertTrue("Description must extract showtimes", desc.contains("10:00") && desc.contains("10:30"))
        assertTrue("Description must extract movie details", desc.contains("Đa Vũ Trụ Hỗn Loạn") || desc.contains("DA VŨ THIU HỚN LOAN"))
    }

    @Test
    fun testBluestoneRiceCookerShoppingSmartTitleAndStructuredDescription() {
        val bluestoneOcr = """
            MENU
            9 Thành phố Hà Nội
            Nồi cơm điện > Nồi cơm điện Bluestone
            Hình ảnh
            DUNG TÍCH 1.8 LÍT
            LÝ TƯỞNG CHO
            GIA ĐÌNH ĐÔNG NGƯỜI
            Đổi nồi cơm cũ rước nồi xịn
            NẤU 2 - 10 CỐC GẠO
            ĐÁP ỨNG 4- 6 NGƯỜI ĂN
            1.8 LÍT
            LÝ TƯỞNG CHO GIA ĐÌNH
            ĐÔNG NGƯỜI, CÓ KHÁCH
            HOẶC TIỆC SUM HỌP
            Giá/Khuyến mãi Chính sách
            Nồi cơm nắp gài BlueStone 1.8 lít RCB-5527
            *5 (Đã bán 1,1k)
            pạitiệcsăn s sale
            Nối cơm đchờ bạn
            a SUAT / NGÀY
            KHÁCH HÀNG MỚI
            GIM THÊ
            500K
            Mua nôi co Gia dung, May lpc nudc, thiết lam dợp
            No eom nie o79K
            pO SUAY/ TUAN
        """.trimIndent()

        val parsed = SmartHeuristicParser.parse(bluestoneOcr, "Screenshot_20220504_Bluestone.png")
        assertEquals("SHOPPING", parsed.detectedIntent)

        val title = parsed.suggestedEventTitle
        assertNotNull("Title must not be null", title)
        // CRITICAL: Title MUST NOT be UI buttons or navigation like 'MENU', 'Hình ảnh', 'Chính sách'
        assertNotEquals("MENU", title)
        assertTrue("Title must not contain 'MENU'", !title!!.contains("MENU", ignoreCase = true))
        assertTrue("Title must not contain 'Hình ảnh'", !title.contains("Hình ảnh", ignoreCase = true))
        assertTrue("Title must not be breadcrumb header", !title.contains(">"))
        // Title MUST be the exact product and brand name
        assertEquals("Nồi cơm nắp gài BlueStone 1.8 lít RCB-5527", title)

        val desc = parsed.suggestedEventDescription
        assertNotNull("Description must not be null", desc)
        // CRITICAL: Description MUST NOT be raw OCR text dump!
        assertNotEquals(bluestoneOcr, desc)
        assertTrue("Description must be structured with shopping section", desc!!.contains("🛒 Lịch mua sắm & Chi tiêu:"))
        assertTrue("Description must contain exact product name", desc.contains("Nồi cơm nắp gài BlueStone 1.8 lít RCB-5527"))
        assertTrue("Description must extract specs (1.8 LÍT)", desc.contains("1.8 LÍT", ignoreCase = true) || desc.contains("1.8 lít", ignoreCase = true))
        assertTrue("Description must extract price (500K)", desc.contains("500K", ignoreCase = true))
        assertTrue("Description must extract seller location (Hà Nội)", desc.contains("Hà Nội", ignoreCase = true))
    }

    @Test
    fun testCalendarAndNotificationPayloadGeneration() {
        val shoppingParsed = SmartHeuristicParser.parse(
            """
            Nồi cơm nắp gài BlueStone 1.8 lít RCB-5527
            500K
            Thành phố Hà Nội
            """.trimIndent(),
            "Photo_1.jpg"
        )
        assertEquals("SHOPPING", shoppingParsed.detectedIntent)
        assertEquals("Nồi cơm nắp gài BlueStone 1.8 lít RCB-5527", shoppingParsed.suggestedEventTitle)
        assertNotNull(shoppingParsed.suggestedEventDescription)

        // Title formatted for Calendar
        val rawTitle = shoppingParsed.suggestedEventTitle!!
        val calendarTitle = if (rawTitle.startsWith("Mua sắm:", ignoreCase = true)) rawTitle else "Mua sắm: $rawTitle"
        assertEquals("Mua sắm: Nồi cơm nắp gài BlueStone 1.8 lít RCB-5527", calendarTitle)

        // Description formatted for Calendar
        val description = shoppingParsed.suggestedEventDescription!!
        assertTrue(description.contains("Nồi cơm nắp gài BlueStone 1.8 lít RCB-5527"))
        assertTrue(description.contains("500K"))

        // Event test
        val eventParsed = SmartHeuristicParser.parse(
            """
            Lịch chiếu phim Lotte Cinema
            Ngày 04/05/2026
            Suất: 10:00, 12:30, 15:00
            """.trimIndent(),
            "Photo_2.jpg"
        )
        assertEquals("EVENT", eventParsed.detectedIntent)
        assertTrue(eventParsed.suggestedEventTitle!!.contains("Lotte Cinema"))
        val eventDesc = eventParsed.suggestedEventDescription!!
        assertTrue(eventDesc.contains("📅 Chi tiết sự kiện & Lịch hẹn:"))
        assertTrue(eventDesc.contains("Lotte Cinema"))
    }

    @Test
    fun testSonyBraviaTvCompleteDescriptionWithoutTruncation() {
        val sonyTvOcr = """
            Google Tivi Sony True RGB BRAVIA 7 II 98
            Chính Sách Vận Chuyển - Lắp Đặt Điện Tử Miền Bắc
            Có chương trình ưu đãi / khuyến mãi
            Vận chuyển lắp đặt nội thành Hà Nội trọn gói
        """.trimIndent()

        val parsed = SmartHeuristicParser.parse(sonyTvOcr, "Screenshot_Sony_TV.jpg")
        assertEquals("SHOPPING", parsed.detectedIntent)
        val title = parsed.suggestedEventTitle
        assertNotNull(title)
        assertTrue("Title should identify Sony Bravia TV", title!!.contains("Sony", ignoreCase = true) || title.contains("BRAVIA", ignoreCase = true))

        val desc = parsed.suggestedEventDescription
        assertNotNull(desc)
        // CRITICAL: Must NOT contain cut-off word fragments like 'tron' or 'Điện Tử Miền'
        assertTrue("Description must NOT truncate 'trọn gói' into 'tron'", !desc!!.contains("tron\n") && !desc.endsWith("tron"))
        assertTrue("Description must NOT classify shipping policy as technical specs", !desc.contains("Chính Sách Vận Chuyển", ignoreCase = true))
        // Must contain full store name or city name
        assertTrue("Description must contain complete location / store", desc.contains("Hà Nội") || desc.contains("Điện Tử Miền Bắc"))
    }

    @Test
    fun testAllShoppingDevicesQualifyForScheduleFeatureEvenWithoutDate() {
        // Canon EOS R5 camera photo without a calendar date
        val canonCameraOcr = """
            Canon EOS R5 Mark II
            Máy ảnh Mirrorless chuyên nghiệp 45MP
        """.trimIndent()

        val parsed = SmartHeuristicParser.parse(canonCameraOcr, "Screenshot_Canon_EOS.jpg")
        assertEquals("SHOPPING", parsed.detectedIntent)
        assertEquals("cat_tobuy", parsed.targetCategoryId)
        assertNull("Product pages typically do not have a schedule date", parsed.suggestedEventDateMillis)

        // Simulate Photo in repository / UI
        val photo = com.example.test2.shared.models.Photo(
            id = "photo_canon_1",
            fileName = "Screenshot_Canon_EOS.jpg",
            filePath = "/storage/emulated/0/DCIM/Screenshots/Screenshot_Canon_EOS.jpg",
            title = parsed.suggestedEventTitle ?: "Screenshot_Canon_EOS.jpg",
            folderId = "folder_screenshots",
            categoryIds = setOf(parsed.targetCategoryId ?: "cat_tobuy"),
            dateAdded = System.currentTimeMillis(),
            isScreenshot = true,
            isProcessed = true,
            detectedIntent = parsed.detectedIntent,
            suggestedEventTitle = parsed.suggestedEventTitle,
            suggestedEventDateMillis = parsed.suggestedEventDateMillis,
            suggestedEventDescription = parsed.suggestedEventDescription
        )

        // UI Gallery / Card logic: must qualify for schedule
        val isShopping = photo.detectedIntent == "SHOPPING" || photo.categoryIds.contains("cat_tobuy")
        val isEvent = photo.detectedIntent == "EVENT" || photo.categoryIds.contains("cat_events") || photo.suggestedEventDateMillis != null
        val hasSchedule = isShopping || isEvent
        assertTrue("All shopping items in cat_tobuy MUST qualify for schedule icon and action", hasSchedule)

        // Default shopping schedule time must be +24h
        val defaultTime = photo.suggestedEventDateMillis ?: if (isShopping) {
            photo.dateAdded + 24 * 3600_000L
        } else {
            photo.dateAdded + 3600_000L
        }
        assertEquals(photo.dateAdded + 24 * 3600_000L, defaultTime)
    }

    @Test
    fun testTemporalQueryParserVietnameseAndEnglishPhrases() {
        val now = System.currentTimeMillis()

        // 1. "quán ăn tuần trước" -> clean query = "quán ăn", dateRange != null
        val parsed1 = TemporalQueryParser.parse("quán ăn tuần trước", now)
        assertEquals("quán ăn", parsed1.cleanTextQuery)
        assertNotNull(parsed1.dateRangeMillis)
        val range1 = parsed1.dateRangeMillis!!
        assertTrue(range1.first < range1.last)
        assertTrue(range1.last <= now)

        // 2. "vé máy bay hôm qua" -> clean query = "vé máy bay", dateRange != null
        val parsed2 = TemporalQueryParser.parse("vé máy bay hôm qua", now)
        assertEquals("vé máy bay", parsed2.cleanTextQuery)
        assertNotNull(parsed2.dateRangeMillis)

        // 3. Temporal only: "tuần trước" -> clean query = "", dateRange != null
        val parsed3 = TemporalQueryParser.parse("tuần trước", now)
        assertEquals("", parsed3.cleanTextQuery)
        assertNotNull(parsed3.dateRangeMillis)

        // 4. "tháng trước"
        val parsed4 = TemporalQueryParser.parse("tháng trước", now)
        assertEquals("", parsed4.cleanTextQuery)
        assertNotNull(parsed4.dateRangeMillis)

        // 5. English: "flight ticket last week" -> clean query = "flight ticket", dateRange != null
        val parsed5 = TemporalQueryParser.parse("flight ticket last week", now)
        assertEquals("flight ticket", parsed5.cleanTextQuery)
        assertNotNull(parsed5.dateRangeMillis)

        // 6. Query without temporal phrases: "hóa đơn siêu thị" -> clean query = original, dateRange == null
        val parsed6 = TemporalQueryParser.parse("hóa đơn siêu thị", now)
        assertEquals("hóa đơn siêu thị", parsed6.cleanTextQuery)
        assertNull(parsed6.dateRangeMillis)
    }

    @Test
    fun testTemporalQueryParserSynonymExpansion() {
        // "đồ làm bếp" should expand to include kitchen terms
        val expandedKitchen = TemporalQueryParser.expandSynonyms("đồ làm bếp")
        assertTrue("Kitchen expansion must contain 'nồi' or 'chảo'", expandedKitchen.contains("nồi") || expandedKitchen.contains("chảo"))

        // "xe cộ" should expand to vehicle terms
        val expandedVehicle = TemporalQueryParser.expandSynonyms("xe cộ")
        assertTrue("Vehicle expansion must contain 'xe máy' or 'ô tô'", expandedVehicle.contains("xe máy") || expandedVehicle.contains("ô tô"))

        // "ăn uống" should expand to food terms
        val expandedFood = TemporalQueryParser.expandSynonyms("ăn uống")
        assertTrue("Food expansion must contain 'quán ăn' or 'nhà hàng'", expandedFood.contains("quán ăn") || expandedFood.contains("nhà hàng"))

        // "thú cưng" should expand to pet terms via VisualConceptExpander
        val expandedPets = TemporalQueryParser.expandSynonyms("thú cưng")
        assertTrue("Pet expansion must contain 'cho' or 'meo' or 'pet'", expandedPets.contains("cho") || expandedPets.contains("meo") || expandedPets.contains("pet"))
    }

    @Test
    fun testSearchWithNeuralQueryVectorCosineSimilarity() {
        // Create synthetic 768-dimensional normalized vectors
        val dim = 768
        val vPhoto1 = FloatArray(dim) { 0f }
        val vPhoto2 = FloatArray(dim) { 0f }
        val vPhoto3 = FloatArray(dim) { 0f }

        // photo1 points along axis 0
        vPhoto1[0] = 1.0f
        // photo2 points along axis 1 (orthogonal to photo1)
        vPhoto2[1] = 1.0f
        // photo3 is at 45 degrees between axis 0 and axis 1
        vPhoto3[0] = 0.7071f
        vPhoto3[1] = 0.7071f

        val indexed = mapOf(
            "photo1" to vPhoto1,
            "photo2" to vPhoto2,
            "photo3" to vPhoto3
        )

        // Query vector points along axis 0 (identical to photo1)
        val queryVector = FloatArray(dim) { 0f }
        queryVector[0] = 1.0f

        val results = VectorSemanticSearchEngine.searchWithQueryVector(
            queryVector = queryVector,
            indexedVectors = indexed,
            minSimilarity = 0.40f
        )

        // Should return photo1 (similarity 1.0) and photo3 (similarity ~0.707), but not photo2 (similarity 0.0 < 0.40)
        assertEquals(2, results.size)
        assertEquals("photo1", results[0].first)
        assertEquals(1.0f, results[0].second, 0.01f)
        assertEquals("photo3", results[1].first)
        assertEquals(0.7071f, results[1].second, 0.02f)
    }

    @Test
    fun testSemanticTagsFullSemanticTextAndVectorSearch() {
        val parsed = ParsedScreenshotData(
            detectedIntent = "SHOPPING",
            suggestedEventTitle = "Bếp nướng điện đa năng",
            targetCategoryId = "cat_tobuy",
            suggestedEventDescription = "• Thiết bị gia dụng nhà bếp\n• Công suất 1500W",
            semanticTags = listOf("đồ làm bếp", "gia dụng", "nấu ăn", "nhà bếp")
        )

        assertEquals("SHOPPING", parsed.detectedIntent)
        assertEquals("Bếp nướng điện đa năng", parsed.suggestedEventTitle)
        assertEquals("cat_tobuy", parsed.targetCategoryId)
        assertEquals(4, parsed.semanticTags.size)
        assertTrue(parsed.semanticTags.contains("đồ làm bếp"))

        // Simulate fullSemanticText as constructed in PhotoAppViewModel
        val fullSemanticText = buildString {
            append("Bếp nướng Lock&Lock ")
            parsed.suggestedEventTitle?.let { append("$it ") }
            parsed.suggestedEventDescription?.let { append("$it ") }
            if (parsed.semanticTags.isNotEmpty()) {
                append(parsed.semanticTags.joinToString(" "))
            }
        }.trim()

        assertTrue(fullSemanticText.contains("đồ làm bếp"))
        assertTrue(fullSemanticText.contains("nhà bếp"))

        // Generate embedding and verify search matches on semantic tags
        val kitchenVector = VectorSemanticSearchEngine.generateEmbedding(
            text = fullSemanticText,
            title = parsed.suggestedEventTitle ?: "",
            intent = parsed.detectedIntent
        )

        val unassociatedVector = VectorSemanticSearchEngine.generateEmbedding(
            text = "Lịch thi đại học khối A và phòng thi 102",
            title = "Lịch thi",
            intent = "EVENT"
        )

        val indexed = mapOf(
            "photo_kitchen" to kitchenVector,
            "photo_exam" to unassociatedVector
        )

        val results = VectorSemanticSearchEngine.search("đồ làm bếp", indexed, minSimilarity = 0.20f)
        assertTrue(results.isNotEmpty())
        assertEquals("photo_kitchen", results.first().first)
    }

    @Test
    fun testRetroactiveSemanticCategoryDiscovery() {
        val customCategory = com.example.test2.shared.models.Category(
            id = "cat_kitchen_101",
            name = "Đồ làm bếp",
            colorHex = "#10B981",
            iconName = "utensils",
            createdAt = 1000L
        )

        // Photo 1: Kitchen appliance with semantic tags
        val photo1 = com.example.test2.shared.models.Photo(
            id = "p_airfryer",
            fileName = "screenshot_airfryer.jpg",
            filePath = "/p/airfryer.jpg",
            title = "Nồi chiên Lock&Lock",
            folderId = "folder_1",
            categoryIds = setOf("cat_tobuy"),
            dateAdded = 1000L,
            semanticTags = listOf("nồi chiên không dầu", "đồ làm bếp", "gia dụng", "nhà bếp")
        )

        // Photo 2: Gym fitness photo
        val photo2 = com.example.test2.shared.models.Photo(
            id = "p_gym",
            fileName = "screenshot_gym.jpg",
            filePath = "/p/gym.jpg",
            title = "Lịch tập Gym",
            folderId = "folder_1",
            categoryIds = setOf("cat_events"),
            dateAdded = 1000L,
            semanticTags = listOf("thể hình", "workout", "phòng tập", "fitness")
        )

        val photos = listOf(photo1, photo2)

        // Test tag-based matching logic from RetroactiveCategorizer
        val catLower = customCategory.name.lowercase()
        val matchedByTags = photos.filter { p ->
            customCategory.id !in p.categoryIds && p.semanticTags.any { tag ->
                tag.contains(catLower) || catLower.contains(tag)
            }
        }.map { it.id }

        assertEquals(1, matchedByTags.size)
        assertEquals("p_airfryer", matchedByTags.first())

        // Test 768-D Neural Vector search alignment for retroactive categorization
        val dim = 768
        val catVector = FloatArray(dim) { 0f }
        catVector[10] = 1.0f

        val photoAirfryerNeuralVec = FloatArray(dim) { 0f }
        photoAirfryerNeuralVec[10] = 0.85f
        photoAirfryerNeuralVec[11] = 0.526f // normalized

        val photoGymNeuralVec = FloatArray(dim) { 0f }
        photoGymNeuralVec[50] = 1.0f // orthogonal

        val neuralMatches = VectorSemanticSearchEngine.searchWithQueryVector(
            queryVector = catVector,
            indexedVectors = mapOf(
                "p_airfryer" to photoAirfryerNeuralVec,
                "p_gym" to photoGymNeuralVec
            ),
            minSimilarity = 0.50f
        )

        assertEquals(1, neuralMatches.size)
        assertEquals("p_airfryer", neuralMatches.first().first)
        assertTrue(neuralMatches.first().second >= 0.80f)
    }

    @Test
    fun testLocalImageLabelerClassificationAndMapping() {
        // 1. Nature labels
        val natureResult = LocalImageLabeler.classifyLabels(listOf("Forest", "Tree", "Mountain", "Landscape"))
        assertTrue(natureResult.isNature)
        assertFalse(natureResult.isPerson)
        assertFalse(natureResult.hasTextOrDoc)
        assertTrue(natureResult.vietnameseSemanticTags.contains("thiên nhiên"))
        assertTrue(natureResult.vietnameseSemanticTags.contains("rừng"))
        assertTrue(natureResult.vietnameseSemanticTags.contains("cây cối"))
        assertTrue(natureResult.vietnameseSemanticTags.contains("núi"))

        // 2. Animal labels
        val animalResult = LocalImageLabeler.classifyLabels(listOf("Animal", "Dog", "Pet", "Puppy"))
        assertTrue(animalResult.isAnimal)
        assertFalse(animalResult.isPerson)
        assertTrue(animalResult.vietnameseSemanticTags.contains("động vật"))
        assertTrue(animalResult.vietnameseSemanticTags.contains("thú cưng"))
        assertTrue(animalResult.vietnameseSemanticTags.contains("chó"))

        // 3. Person labels
        val personResult = LocalImageLabeler.classifyLabels(listOf("Person", "Face", "Selfie", "Smile"))
        assertTrue(personResult.isPerson)
        assertFalse(personResult.isNature)
        assertTrue(personResult.vietnameseSemanticTags.contains("cá nhân"))
        assertTrue(personResult.vietnameseSemanticTags.contains("chân dung"))
        assertTrue(personResult.vietnameseSemanticTags.contains("tự sướng"))

        // 4. Document / Text labels
        val docResult = LocalImageLabeler.classifyLabels(listOf("Document", "Text", "Receipt", "Font"))
        assertTrue(docResult.hasTextOrDoc)
        assertFalse(docResult.isNature)
    }

    @Test
    fun testNatureAndAnimalClassificationWithZeroTextInImage() {
        // SCENARIO: Camera photo of a forest with NO text and NO face
        val forestParsed = SmartHeuristicParser.parse(
            scrubbedText = "",
            photoTitle = "IMG_20261109_143000.jpg",
            visualLabels = listOf("Forest", "Tree", "Nature", "Wood")
        )
        assertEquals("OTHER", forestParsed.detectedIntent)
        assertEquals("cat_nature", forestParsed.targetCategoryId)
        assertTrue(forestParsed.semanticTags.contains("thiên nhiên"))
        assertTrue(forestParsed.semanticTags.contains("rừng"))

        // SCENARIO: Photo of a pet/wildlife with NO text and NO face
        val petParsed = SmartHeuristicParser.parse(
            scrubbedText = "",
            photoTitle = "PXL_20261109_987654.jpg",
            visualLabels = listOf("Animal", "Dog", "Pet")
        )
        assertEquals("OTHER", petParsed.detectedIntent)
        assertEquals("cat_nature", petParsed.targetCategoryId)
        assertTrue(petParsed.semanticTags.contains("động vật"))
        assertTrue(petParsed.semanticTags.contains("thú cưng"))

        // SCENARIO: Portrait/Selfie with NO text
        val selfieParsed = SmartHeuristicParser.parse(
            scrubbedText = "",
            photoTitle = "Selfie_2026.jpg",
            visualLabels = listOf("Person", "Selfie", "Portrait")
        )
        assertEquals("OTHER", selfieParsed.detectedIntent)
        assertEquals("cat_personal", selfieParsed.targetCategoryId)
        assertTrue(selfieParsed.semanticTags.contains("cá nhân"))
    }

    @Test
    fun testVisualConceptExpanderAndRetroactiveMatching() {
        // 1. Expand "Thú cưng" -> must contain specific animal terms: chó, mèo, pet, dog, cat
        val petExpanded = VisualConceptExpander.expandCategoryConcepts("Thú cưng")
        assertTrue(petExpanded.contains("cho"))
        assertTrue(petExpanded.contains("meo"))
        assertTrue(petExpanded.contains("pet"))
        assertTrue(petExpanded.contains("dong vat"))

        // 2. Expand "Cây cảnh" -> must contain plant and flower terms
        val plantExpanded = VisualConceptExpander.expandCategoryConcepts("Cây cảnh")
        assertTrue(plantExpanded.contains("cay coi"))
        assertTrue(plantExpanded.contains("hoa co"))
        assertTrue(plantExpanded.contains("plant"))

        // 3. Expand "Cắm trại" -> must contain camping, tent, and mountain terms
        val campExpanded = VisualConceptExpander.expandCategoryConcepts("Cắm trại")
        assertTrue(campExpanded.contains("leu"))
        assertTrue(campExpanded.contains("camping"))
        assertTrue(campExpanded.contains("rung"))

        // 4. Test Retroactive matching via VisualConceptExpander:
        // A photo has tag "chó" and "động vật" (generated by LocalImageLabeler from ML Kit)
        // User creates a brand-new custom category named "Thú cưng" (without the word "chó")
        val dogPhoto = com.example.test2.shared.models.Photo(
            id = "photo_dog_1",
            fileName = "IMG_dog.jpg",
            filePath = "/storage/emulated/0/DCIM/Camera/IMG_dog.jpg",
            title = "IMG_dog.jpg",
            folderId = "folder_camera",
            categoryIds = setOf("cat_nature"),
            dateAdded = 1000L,
            semanticTags = listOf("động vật", "thú cưng", "chó")
        )

        val campingPhoto = com.example.test2.shared.models.Photo(
            id = "photo_camp_1",
            fileName = "IMG_tent.jpg",
            filePath = "/storage/emulated/0/DCIM/Camera/IMG_tent.jpg",
            title = "IMG_tent.jpg",
            folderId = "folder_camera",
            categoryIds = setOf("cat_nature"),
            dateAdded = 1000L,
            semanticTags = listOf("thiên nhiên", "phong cảnh", "lều", "rừng")
        )

        // Matching for category "Thú cưng"
        val customPetCategory = com.example.test2.shared.models.Category(
            id = "cat_custom_pets",
            name = "Thú cưng",
            colorHex = "#F59E0B",
            iconName = "heart",
            createdAt = 1000L
        )

        val unassigned = listOf(dogPhoto, campingPhoto).filter { customPetCategory.id !in it.categoryIds }
        val matchedIds = unassigned.filter { p ->
            p.semanticTags.any { tag ->
                val tagUnaccented = com.example.test2.shared.utils.VietnameseUtils.removeDiacritics(tag.lowercase())
                tagUnaccented in petExpanded || petExpanded.any { exp -> tagUnaccented.contains(exp) || exp.contains(tagUnaccented) }
            }
        }.map { it.id }

        assertEquals(1, matchedIds.size)
        assertEquals("photo_dog_1", matchedIds.first())

        // Matching for category "Cắm trại dã ngoại"
        val customCampCategory = com.example.test2.shared.models.Category(
            id = "cat_custom_camp",
            name = "Cắm trại dã ngoại",
            colorHex = "#10B981",
            iconName = "leaf",
            createdAt = 1000L
        )
        val campCategoryExpanded = VisualConceptExpander.expandCategoryConcepts(customCampCategory.name)
        val matchedCampIds = unassigned.filter { p ->
            p.semanticTags.any { tag ->
                val tagUnaccented = com.example.test2.shared.utils.VietnameseUtils.removeDiacritics(tag.lowercase())
                tagUnaccented in campCategoryExpanded || campCategoryExpanded.any { exp -> tagUnaccented.contains(exp) || exp.contains(tagUnaccented) }
            }
        }.map { it.id }

        assertEquals(1, matchedCampIds.size)
        assertEquals("photo_camp_1", matchedCampIds.first())
    }

    @Test
    fun testRetroactiveMatchingAccuracyAndFalsePositivePrevention() {
        // SCENARIO: User creates a custom category "Cá nhân"
        val personalExpanded = VisualConceptExpander.expandCategoryConcepts("Cá nhân")

        // 1. Unrelated tags MUST NOT match "Cá nhân"
        val unrelatedTags = listOf(
            "cà phê",
            "camera",
            "cơm tấm",
            "cao cấp",
            "báo cáo",
            "vé xem phim",
            "đồ gia dụng",
            "nồi cơm",
            "giảm giá",
            "khuyến mãi",
            "lịch họp",
            "chuyến bay"
        )
        for (tag in unrelatedTags) {
            assertFalse(
                "Unrelated tag '$tag' must NOT match 'Cá nhân'",
                VisualConceptExpander.isConceptMatch(tag, personalExpanded)
            )
        }

        // 2. Genuine personal tags MUST match "Cá nhân"
        val genuinePersonalTags = listOf(
            "cá nhân",
            "chân dung",
            "tự sướng",
            "cccd",
            "cmnd",
            "hộ chiếu",
            "selfie",
            "portrait"
        )
        for (tag in genuinePersonalTags) {
            assertTrue(
                "Genuine personal tag '$tag' MUST match 'Cá nhân'",
                VisualConceptExpander.isConceptMatch(tag, personalExpanded)
            )
        }

        // 3. Test "Gia đình" does NOT match "đồ gia dụng", "giảm giá", "giá cả"
        val familyExpanded = VisualConceptExpander.expandCategoryConcepts("Gia đình")
        assertFalse("Tag 'đồ gia dụng' must NOT match 'Gia đình'", VisualConceptExpander.isConceptMatch("đồ gia dụng", familyExpanded))
        assertFalse("Tag 'giảm giá' must NOT match 'Gia đình'", VisualConceptExpander.isConceptMatch("giảm giá", familyExpanded))
        assertFalse("Tag 'giá cả' must NOT match 'Gia đình'", VisualConceptExpander.isConceptMatch("giá cả", familyExpanded))
        assertTrue("Tag 'gia đình' MUST match 'Gia đình'", VisualConceptExpander.isConceptMatch("gia đình", familyExpanded))

        // 4. Test "Xe cộ" does NOT match "xem phim"
        val vehicleExpanded = VisualConceptExpander.expandCategoryConcepts("Xe cộ")
        assertFalse("Tag 'xem phim' must NOT match 'Xe cộ'", VisualConceptExpander.isConceptMatch("xem phim", vehicleExpanded))
        assertTrue("Tag 'xe máy' MUST match 'Xe cộ'", VisualConceptExpander.isConceptMatch("xe máy", vehicleExpanded))
        assertTrue("Tag 'ô tô' MUST match 'Xe cộ'", VisualConceptExpander.isConceptMatch("ô tô", vehicleExpanded))
    }
}

package com.example.test2.ai

import com.example.test2.shared.models.Category
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiBatchAndRateLimiterTest {

    @Test
    fun testParseGeminiBatchResponse_ParityAndStructure() {
        val items = listOf(
            BatchPhotoItem(
                photoId = "photo_flight",
                photoTitle = "Flight_VJ123.jpg",
                scrubbedText = "Chuyến bay VJ123 khởi hành 08:30 ngày 25/12/2026 từ Hà Nội đến Đà Nẵng",
                hasFace = false,
                visualLabels = listOf("ticket", "text")
            ),
            BatchPhotoItem(
                photoId = "photo_bill",
                photoTitle = "Shopee_Bill.jpg",
                scrubbedText = "Đơn hàng Shopee Nồi cơm điện Bluestone Tổng tiền: 540.000đ",
                hasFace = false,
                visualLabels = listOf("receipt", "invoicing")
            )
        )

        val categories = listOf(
            Category(id = "cat_events", name = "Sự kiện"),
            Category(id = "cat_tobuy", name = "Cần mua")
        )

        val fakeGeminiBatchJson = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "[{\"id\":\"photo_flight\",\"intent\":\"EVENT\",\"suggested_title\":\"Vé máy bay Đà Nẵng\",\"suggested_event_title\":\"Chuyến bay VJ123 đi Đà Nẵng\",\"event_date_millis\":1798162200000,\"target_category_id\":\"cat_events\",\"summary\":\"Chuyến bay Vietjet khởi hành đi Đà Nẵng\",\"semantic_tags\":[\"vé máy bay\",\"du lịch\",\"đà nẵng\",\"vietjet\"],\"entities\":{\"datetime\":\"08:30 25/12/2026\",\"place\":\"Đà Nẵng\"}},{\"id\":\"photo_bill\",\"intent\":\"SHOPPING\",\"suggested_title\":\"Nồi cơm điện\",\"suggested_event_title\":\"Nồi cơm điện Bluestone\",\"event_date_millis\":null,\"target_category_id\":\"cat_tobuy\",\"summary\":\"Hóa đơn mua nồi cơm điện Shopee\",\"items_to_buy\":[\"Nồi cơm điện Bluestone\"],\"semantic_tags\":[\"nồi cơm\",\"bluestone\",\"mua sắm\",\"shopee\"],\"entities\":{\"price\":\"540.000đ\",\"product\":\"Nồi cơm điện Bluestone\"}}]"
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val results = CloudAiBackendClient.parseGeminiBatchResponse(
            responseJson = fakeGeminiBatchJson,
            items = items,
            categories = categories,
            isPro = true
        )

        assertEquals(2, results.size)

        // Verify photo_flight
        val flightData = results["photo_flight"]
        assertNotNull(flightData)
        assertEquals("EVENT", flightData!!.detectedIntent)
        assertEquals("cat_events", flightData.targetCategoryId)
        assertEquals("Chuyến bay VJ123 đi Đà Nẵng", flightData.suggestedEventTitle)
        assertEquals(1798162200000L, flightData.suggestedEventDateMillis)
        assertEquals("Chuyến bay Vietjet khởi hành đi Đà Nẵng", flightData.summary)
        assertTrue(flightData.semanticTags.contains("vé máy bay"))
        assertEquals("Đà Nẵng", flightData.entities["place"])

        // Verify photo_bill
        val billData = results["photo_bill"]
        assertNotNull(billData)
        assertEquals("SHOPPING", billData!!.detectedIntent)
        assertEquals("cat_tobuy", billData.targetCategoryId)
        assertEquals("Nồi cơm điện Bluestone", billData.suggestedEventTitle)
        assertEquals("Hóa đơn mua nồi cơm điện Shopee", billData.summary)
        assertTrue(billData.itemsToBuy.contains("Nồi cơm điện Bluestone"))
        assertTrue(billData.semanticTags.contains("nồi cơm"))
        assertEquals("540.000đ", billData.entities["price"])
    }

    @Test
    fun testParseGeminiBatchResponse_PartialFailureFallback() {
        // Test when Gemini response only returns 1 of the 2 items
        val items = listOf(
            BatchPhotoItem(
                photoId = "photo_1",
                photoTitle = "Lich_Hop.jpg",
                scrubbedText = "Lịch họp Zoom lúc 14:00 ngày mai",
                hasFace = false,
                visualLabels = emptyList()
            ),
            BatchPhotoItem(
                photoId = "photo_2",
                photoTitle = "Hoa_Don.jpg",
                scrubbedText = "Hóa đơn thanh toán cà phê 45.000đ",
                hasFace = false,
                visualLabels = emptyList()
            )
        )

        // Gemini only returns photo_1, drops photo_2
        val fakeGeminiBatchJson = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "[{\"id\":\"photo_1\",\"intent\":\"EVENT\",\"suggested_title\":\"Lịch họp Zoom\",\"suggested_event_title\":\"Họp Zoom\",\"event_date_millis\":null,\"target_category_id\":\"cat_events\"}]"
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val results = CloudAiBackendClient.parseGeminiBatchResponse(
            responseJson = fakeGeminiBatchJson,
            items = items,
            categories = emptyList(),
            isPro = false
        )

        assertEquals(2, results.size)
        // photo_1 came from Gemini
        assertEquals("EVENT", results["photo_1"]?.detectedIntent)
        assertEquals("cat_events", results["photo_1"]?.targetCategoryId)

        // photo_2 automatically fell back to on-device SmartHeuristicParser without error!
        assertNotNull(results["photo_2"])
        assertEquals("cat_tobuy", results["photo_2"]?.targetCategoryId)
    }

    @Test
    fun testGeminiRateLimiter_ThrottleInterval() = runBlocking {
        // Validate acquire executes cleanly
        GeminiRateLimiter.acquire()
        // Report hit and verify no crash
        GeminiRateLimiter.reportRateLimitHit(retryAfterSeconds = 1)
    }

    @Test
    fun testCloudAiSyncNotifier_EventEmission() = runBlocking {
        var receivedIds = emptyList<String>()
        val job = launch {
            CloudAiSyncNotifier.syncEvents.collect { ids ->
                receivedIds = ids
            }
        }

        delay(50)
        CloudAiSyncNotifier.notifyBatchCompleted(listOf("p1", "p2", "p3"))
        delay(50)

        assertEquals(listOf("p1", "p2", "p3"), receivedIds)
        job.cancel()
    }

    @Test
    fun testSharedCoreClassificationRules_ContainsNegativeGuardrails() {
        val rules = CloudAiBackendClient.buildCoreClassificationInstructions(
            categoriesList = "cat_events: 'Sự kiện', cat_tobuy: 'Cần mua'",
            customCatInstruction = ""
        )
        // Verify hospital/clinic fees guardrail
        assertTrue(rules.contains("fees/co-pays (BHYT, tuition, consultation fees) are normal structural elements of timetables"))
        assertTrue(rules.contains("DO NOT make the document a personal ID card or a shopping product!"))
        // Verify commercial intent guardrail
        assertTrue(rules.contains("Mentions of location/street names"))
        assertTrue(rules.contains("NEVER classify timetables or schedules as cat_tobuy!"))
        // Verify personal guardrail
        assertTrue(rules.contains("Staff duty rosters, doctor examination timetables"))
    }

    @Test
    fun testParseSingleJsonObject_PersonalIntentAndEdgeCases() {
        // 1. Test intent = PERSONAL
        val personalJson = org.json.JSONObject().apply {
            put("id", "doc_cccd")
            put("intent", "PERSONAL")
            put("suggested_title", "Căn cước công dân")
            put("target_category_id", "cat_personal")
        }
        val parsedPersonal = CloudAiBackendClient.parseSingleJsonObject(
            parsedJson = personalJson,
            scrubbedText = "CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM CĂN CƯỚC CÔNG DÂN",
            defaultTitle = "CCCD.jpg",
            categories = listOf(Category(id = "cat_personal", name = "Cá nhân")),
            isPro = true,
            hasFace = true
        )
        assertEquals("PERSONAL", parsedPersonal.detectedIntent)
        assertEquals("cat_personal", parsedPersonal.targetCategoryId)
        org.junit.Assert.assertNull(parsedPersonal.suggestedEventDateMillis)
        org.junit.Assert.assertNull(parsedPersonal.suggestedEventTitle)

        // 2. Test intent = PERSONAL without explicit target_category_id (model omits category)
        val personalWithoutCatJson = org.json.JSONObject().apply {
            put("id", "doc_id_card")
            put("intent", "PERSONAL")
            put("suggested_title", "Thẻ nhân viên")
        }
        val parsedPersonalNoCat = CloudAiBackendClient.parseSingleJsonObject(
            parsedJson = personalWithoutCatJson,
            scrubbedText = "THẺ NHÂN VIÊN CÔNG TY ABC",
            defaultTitle = "The_Nhan_Vien.jpg",
            categories = listOf(Category(id = "cat_personal", name = "Cá nhân")),
            isPro = true,
            hasFace = true
        )
        assertEquals("PERSONAL", parsedPersonalNoCat.detectedIntent)
        assertEquals("cat_personal", parsedPersonalNoCat.targetCategoryId)

        // 3. Test hospital schedule with fee line
        val hospitalScheduleJson = org.json.JSONObject().apply {
            put("id", "clinic_schedule")
            put("intent", "EVENT")
            put("suggested_title", "Lịch khám Nhi")
            put("suggested_event_title", "Lịch khám Bác sĩ A - Phòng 201")
            put("target_category_id", "cat_events")
            put("event_date_millis", 1798162200000L)
        }
        val parsedHospital = CloudAiBackendClient.parseSingleJsonObject(
            parsedJson = hospitalScheduleJson,
            scrubbedText = "Lịch khám Nhi Bác sĩ Nguyễn Văn A - Phí khám: 150.000đ",
            defaultTitle = "Lich_Kham.jpg",
            categories = listOf(Category(id = "cat_events", name = "Sự kiện"), Category(id = "cat_tobuy", name = "Mua sắm")),
            isPro = true,
            hasFace = false
        )
        assertEquals("EVENT", parsedHospital.detectedIntent)
        assertEquals("cat_events", parsedHospital.targetCategoryId)
        assertEquals("Lịch khám Bác sĩ A - Phòng 201", parsedHospital.suggestedEventTitle)
    }

    @Test
    fun testParseGeminiBatchResponse_MarkdownBackticksFencesHandling() {
        val items = listOf(
            BatchPhotoItem(
                photoId = "photo_fenced",
                photoTitle = "San_Pham.jpg",
                scrubbedText = "Nồi chiên không dầu Philips giá 1.200.000đ",
                hasFace = false,
                visualLabels = listOf("appliance")
            )
        )

        // Model wraps response in ```json ... ``` markdown block
        val fakeFencedResponse = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "```json\n[{\"id\":\"photo_fenced\",\"intent\":\"SHOPPING\",\"suggested_title\":\"Nồi chiên không dầu\",\"suggested_event_title\":\"Nồi chiên không dầu Philips\",\"target_category_id\":\"cat_tobuy\"}]\n```"
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val results = CloudAiBackendClient.parseGeminiBatchResponse(
            responseJson = fakeFencedResponse,
            items = items,
            categories = listOf(Category(id = "cat_tobuy", name = "Cần mua")),
            isPro = true
        )

        assertEquals(1, results.size)
        val data = results["photo_fenced"]
        assertNotNull(data)
        assertEquals("SHOPPING", data!!.detectedIntent)
        assertEquals("cat_tobuy", data.targetCategoryId)
    }

    @Test
    fun testParseGeminiBatchResponse_MultiItemIsolationParity() {
        val items = listOf(
            BatchPhotoItem(
                photoId = "item_event",
                photoTitle = "Lich_Kham.jpg",
                scrubbedText = "Lịch khám chữa bệnh Bác sĩ Cường - Phí: 200.000đ",
                hasFace = false,
                visualLabels = listOf("document")
            ),
            BatchPhotoItem(
                photoId = "item_shopping",
                photoTitle = "Shopee_Order.jpg",
                scrubbedText = "Đơn hàng Shopee Nồi áp suất 850.000đ",
                hasFace = false,
                visualLabels = listOf("receipt")
            ),
            BatchPhotoItem(
                photoId = "item_personal",
                photoTitle = "CCCD_Mat_Truoc.jpg",
                scrubbedText = "CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM CĂN CƯỚC CÔNG DÂN",
                hasFace = true,
                visualLabels = listOf("identity document")
            ),
            BatchPhotoItem(
                photoId = "item_reading",
                photoTitle = "Bao_Moi.jpg",
                scrubbedText = "Tin tức thời sự giao thông Hà Nội sáng nay",
                hasFace = false,
                visualLabels = listOf("news")
            )
        )

        val fakeBatchResponse = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "[{\"id\":\"item_event\",\"intent\":\"EVENT\",\"suggested_title\":\"Lịch khám bác sĩ Cường\",\"suggested_event_title\":\"Khám bác sĩ Cường\",\"target_category_id\":\"cat_events\"},{\"id\":\"item_shopping\",\"intent\":\"SHOPPING\",\"suggested_title\":\"Đơn hàng Shopee\",\"suggested_event_title\":\"Nồi áp suất\",\"target_category_id\":\"cat_tobuy\"},{\"id\":\"item_personal\",\"intent\":\"PERSONAL\",\"suggested_title\":\"Căn cước công dân\",\"target_category_id\":\"cat_personal\"},{\"id\":\"item_reading\",\"intent\":\"READING\",\"suggested_title\":\"Tin tức giao thông\",\"target_category_id\":\"cat_reading\"}]"
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val results = CloudAiBackendClient.parseGeminiBatchResponse(
            responseJson = fakeBatchResponse,
            items = items,
            categories = listOf(
                Category(id = "cat_events", name = "Sự kiện"),
                Category(id = "cat_tobuy", name = "Cần mua"),
                Category(id = "cat_personal", name = "Cá nhân"),
                Category(id = "cat_reading", name = "Đọc")
            ),
            isPro = true
        )

        assertEquals(4, results.size)
        assertEquals("cat_events", results["item_event"]?.targetCategoryId)
        assertEquals("EVENT", results["item_event"]?.detectedIntent)

        assertEquals("cat_tobuy", results["item_shopping"]?.targetCategoryId)
        assertEquals("SHOPPING", results["item_shopping"]?.detectedIntent)

        assertEquals("cat_personal", results["item_personal"]?.targetCategoryId)
        assertEquals("PERSONAL", results["item_personal"]?.detectedIntent)

        assertEquals("cat_reading", results["item_reading"]?.targetCategoryId)
        assertEquals("READING", results["item_reading"]?.detectedIntent)
    }

    @Test
    fun testParseSingleJsonObject_PreservesValidDefaultAndCustomCategories() {
        // Case A: Landscape photo classified as cat_nature even if intent is READING
        val natureJson = org.json.JSONObject().apply {
            put("id", "photo_nature")
            put("intent", "READING")
            put("suggested_title", "Phong cảnh Tây Bắc")
            put("target_category_id", "cat_nature")
        }
        val natureResult = CloudAiBackendClient.parseSingleJsonObject(
            parsedJson = natureJson,
            scrubbedText = "Mùa lúa chín Mù Cang Chải 2026",
            defaultTitle = "Tay_Bac.jpg",
            categories = emptyList(),
            isPro = false
        )
        // Verify cat_nature is 100% PRESERVED, not overwritten to cat_reading!
        assertEquals("cat_nature", natureResult.targetCategoryId)
        assertEquals("READING", natureResult.detectedIntent)

        // Case B: Personal portrait / document classified as cat_personal even if intent is OTHER
        val personalJson = org.json.JSONObject().apply {
            put("id", "photo_portrait")
            put("intent", "OTHER")
            put("suggested_title", "Ảnh chân dung")
            put("target_category_id", "cat_personal")
        }
        val personalResult = CloudAiBackendClient.parseSingleJsonObject(
            parsedJson = personalJson,
            scrubbedText = "Chân dung gia đình kỷ niệm",
            defaultTitle = "Portrait.jpg",
            categories = emptyList(),
            isPro = false,
            hasFace = true
        )
        assertEquals("cat_personal", personalResult.targetCategoryId)

        // Case C: Custom user category in Pro tier
        val customCat = Category(id = "cat_my_custom_docs", name = "Hồ sơ công ty")
        val customJson = org.json.JSONObject().apply {
            put("id", "photo_custom")
            put("intent", "READING")
            put("suggested_title", "Hồ sơ dự án")
            put("target_category_id", "cat_my_custom_docs")
        }
        val customResult = CloudAiBackendClient.parseSingleJsonObject(
            parsedJson = customJson,
            scrubbedText = "Hồ sơ năng lực công ty",
            defaultTitle = "Profile.pdf",
            categories = listOf(customCat),
            isPro = true
        )
        assertEquals("cat_my_custom_docs", customResult.targetCategoryId)

        // Case D: Missing categoryId falls back to intent-based default
        val missingCatJson = org.json.JSONObject().apply {
            put("id", "photo_shop")
            put("intent", "SHOPPING")
            put("suggested_title", "Tai nghe Bluetooth")
            put("target_category_id", "")
        }
        val missingResult = CloudAiBackendClient.parseSingleJsonObject(
            parsedJson = missingCatJson,
            scrubbedText = "Tai nghe Bluetooth Sony giá 2.500.000đ",
            defaultTitle = "TaiNghe.jpg",
            categories = emptyList(),
            isPro = false
        )
        assertEquals("cat_tobuy", missingResult.targetCategoryId)
    }

    @Test
    fun testParseGeminiResponse_SinglePhotoMarkdownFencesStripping() {
        // Test single photo response wrapped in ```json ... ``` code fence
        val fakeFencedResponse = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "```json\n{\n  \"intent\": \"EVENT\",\n  \"suggested_title\": \"Lịch thi học kỳ\",\n  \"suggested_event_title\": \"Thi môn Giải tích 1\",\n  \"event_date_millis\": 1798162200000,\n  \"target_category_id\": \"cat_events\"\n}\n```"
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val result = CloudAiBackendClient.parseGeminiResponse(
            responseJson = fakeFencedResponse,
            scrubbedText = "Lịch thi học kỳ: Môn Giải tích 1 ngày 25/12/2026",
            defaultTitle = "LichThi.jpg",
            categories = emptyList(),
            isPro = false
        )

        assertEquals("EVENT", result.detectedIntent)
        assertEquals("cat_events", result.targetCategoryId)
        assertEquals("Thi môn Giải tích 1", result.suggestedEventTitle)
        assertEquals(1798162200000L, result.suggestedEventDateMillis)
    }

    @Test
    fun testBuildCoreClassificationInstructions_BatchVsSingle() {
        val singleInstructions = CloudAiBackendClient.buildCoreClassificationInstructions(
            categoriesList = "cat_events: 'Sự kiện'",
            customCatInstruction = "",
            isBatch = false
        )
        assertTrue(singleInstructions.contains("8 to 15 natural, conceptual Vietnamese keywords"))
        assertTrue(singleInstructions.contains("🛒 Lịch mua sắm & Chi tiêu:"))

        val batchInstructions = CloudAiBackendClient.buildCoreClassificationInstructions(
            categoriesList = "cat_events: 'Sự kiện'",
            customCatInstruction = "",
            isBatch = true
        )
        assertTrue(batchInstructions.contains("3 to 5 natural, high-signal conceptual Vietnamese keywords"))
        assertTrue(batchInstructions.contains("clean, concise 1-2 sentence description in Vietnamese"))
        assertTrue(batchInstructions.contains("TARGET CATEGORY ID (BATCH MANDATORY)"))
        assertTrue(batchInstructions.contains("MUST NEVER BE NULL OR EMPTY"))
    }

    @Test
    fun testParseGeminiBatchResponse_DynamicRootKeys() {
        val items = listOf(
            BatchPhotoItem(
                photoId = "device_101",
                photoTitle = "Flight_VJ.jpg",
                scrubbedText = "Chuyến bay VJ123 lúc 08:30 ngày 25/12/2026",
                hasFace = false,
                visualLabels = emptyList()
            )
        )
        // Gemini wraps in { "results": [ ... ] } instead of [ ... ] or "items"
        val wrappedJson = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "{\n  \"results\": [\n    {\n      \"id\": \"device_101\",\n      \"intent\": \"EVENT\",\n      \"suggested_title\": \"Vé máy bay\",\n      \"target_category_id\": \"cat_events\"\n    }\n  ]\n}"
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val results = CloudAiBackendClient.parseGeminiBatchResponse(
            responseJson = wrappedJson,
            items = items,
            categories = listOf(Category(id = "cat_events", name = "Sự kiện")),
            isPro = true
        )

        assertEquals(1, results.size)
        assertEquals("cat_events", results["device_101"]?.targetCategoryId)
        assertEquals("EVENT", results["device_101"]?.detectedIntent)
    }

    @Test
    fun testParseGeminiBatchResponse_FuzzyIdMatching() {
        val items = listOf(
            BatchPhotoItem(
                photoId = "device_998877",
                photoTitle = "Shopee_Bill.jpg",
                scrubbedText = "Đơn hàng Shopee Nồi chiên Bluestone Tổng tiền: 540.000đ",
                hasFace = false,
                visualLabels = emptyList()
            )
        )
        // Gemini strips "device_" and returns just "998877"
        val strippedIdJson = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "[{\"id\":\"998877\",\"intent\":\"SHOPPING\",\"suggested_title\":\"Nồi chiên\",\"target_category_id\":\"cat_tobuy\"}]"
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val results = CloudAiBackendClient.parseGeminiBatchResponse(
            responseJson = strippedIdJson,
            items = items,
            categories = listOf(Category(id = "cat_tobuy", name = "Cần mua")),
            isPro = true
        )

        assertEquals(1, results.size)
        assertNotNull(results["device_998877"])
        assertEquals("cat_tobuy", results["device_998877"]?.targetCategoryId)
        assertEquals("SHOPPING", results["device_998877"]?.detectedIntent)
    }

    @Test
    fun testParseGeminiBatchResponse_PositionalFallbackWhenIdsLost() {
        val items = listOf(
            BatchPhotoItem(
                photoId = "device_aaa",
                photoTitle = "Flight.jpg",
                scrubbedText = "Chuyến bay lúc 08:30 ngày 25/12/2026",
                hasFace = false,
                visualLabels = emptyList()
            ),
            BatchPhotoItem(
                photoId = "device_bbb",
                photoTitle = "Bill.jpg",
                scrubbedText = "Đơn hàng Shopee Tổng tiền 200.000đ",
                hasFace = false,
                visualLabels = emptyList()
            )
        )
        // Model returned empty or mutated IDs, but exactly 2 elements matching input size 1:1
        val mutatedIdsJson = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "[{\"id\":\"item_1\",\"intent\":\"EVENT\",\"target_category_id\":\"cat_events\"},{\"id\":\"item_2\",\"intent\":\"SHOPPING\",\"target_category_id\":\"cat_tobuy\"}]"
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val results = CloudAiBackendClient.parseGeminiBatchResponse(
            responseJson = mutatedIdsJson,
            items = items,
            categories = listOf(
                Category(id = "cat_events", name = "Sự kiện"),
                Category(id = "cat_tobuy", name = "Cần mua")
            ),
            isPro = true
        )

        assertEquals(2, results.size)
        assertEquals("cat_events", results["device_aaa"]?.targetCategoryId)
        assertEquals("cat_tobuy", results["device_bbb"]?.targetCategoryId)
    }

    @Test
    fun testParseGeminiBatchResponse_SafetyOnUnequalArrayLength() {
        val items = listOf(
            BatchPhotoItem(
                photoId = "device_1",
                photoTitle = "Flight.jpg",
                scrubbedText = "Chuyến bay lúc 08:30 ngày 25/12/2026",
                hasFace = false,
                visualLabels = emptyList()
            ),
            BatchPhotoItem(
                photoId = "device_2",
                photoTitle = "Coffee_Bill.jpg",
                scrubbedText = "Hóa đơn cà phê 45.000đ",
                hasFace = false,
                visualLabels = emptyList()
            )
        )
        // Gemini only returned 1 item with an unknown ID "unknown_id".
        // Since array lengths do NOT match (1 vs 2), Positional Fallback MUST NOT blindly assign it!
        val incompleteJson = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "[{\"id\":\"unknown_id\",\"intent\":\"EVENT\",\"target_category_id\":\"cat_events\"}]"
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val results = CloudAiBackendClient.parseGeminiBatchResponse(
            responseJson = incompleteJson,
            items = items,
            categories = listOf(Category(id = "cat_events", name = "Sự kiện")),
            isPro = true
        )

        assertEquals(2, results.size)
        // Both items should safely fallback to on-device heuristics instead of misassigning!
        assertNotNull(results["device_1"])
        assertNotNull(results["device_2"])
    }
}

package com.example.test2.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Phase 0: Search Quality Benchmark Test Suite.
 * Measures Recall@10 and MRR (Mean Reciprocal Rank) across 60 real-world Vietnamese screenshot queries.
 * Validates Phase 1 through Phase 5 improvements:
 * - Intent & temporal extraction
 * - FTS BM25 token matching with diacritics insensitivity
 * - Semantic vector cosine similarity
 * - PII scrubbing on queries & documents
 */
class SearchQualityBenchmarkTest {

    data class BenchmarkItem(
        val photoId: String,
        val title: String,
        val text: String,
        val tags: List<String>,
        val intent: String
    )

    data class BenchmarkQuery(
        val query: String,
        val expectedPhotoId: String,
        val category: String
    )

    // Corpus of realistic screenshot items
    private val corpus = listOf(
        BenchmarkItem(
            photoId = "photo_shopee_rice_cooker",
            title = "Nồi cơm điện tử BlueStone 1.8L",
            text = "Shopee Đơn hàng #9981273 Tổng thanh toán 1.250.000 đ Đã thanh toán qua ShopeePay Giảm 50k",
            tags = listOf("mua sắm", "đồ gia dụng", "nhà bếp", "nấu ăn", "nồi cơm", "shopee"),
            intent = "SHOPPING"
        ),
        BenchmarkItem(
            photoId = "photo_vcb_transfer",
            title = "Biên nhận chuyển khoản VCB Digibank",
            text = "Ngân hàng Ngoại Thương Vietcombank Chuyển khoản thành công Số tiền 5.000.000 VND STK 0123456789 Nội dung Tien nha thang 11",
            tags = listOf("ngân hàng", "chuyển khoản", "thanh toán", "tiền nhà", "vietcombank", "hóa đơn"),
            intent = "SHOPPING"
        ),
        BenchmarkItem(
            photoId = "photo_grab_receipt",
            title = "Hóa đơn GrabFood Cơm Tấm",
            text = "GrabFood Cơm tấm Phúc Lộc Thọ 2 phần 120.000 đ Phí giao hàng 15.000 đ Tổng cộng 135.000 đ",
            tags = listOf("ăn uống", "cơm tấm", "giao hàng", "bữa trưa", "grab", "hóa đơn"),
            intent = "SHOPPING"
        ),
        BenchmarkItem(
            photoId = "photo_sony_tv",
            title = "Google Tivi Sony BRAVIA 4K 65 inch",
            text = "Điện máy Xanh Giá bán 18.990.000 đ Trả góp 0% Tivi màn hình LED HDR10 Mua ngay",
            tags = listOf("tivi", "điện máy", "sony", "màn hình", "sản phẩm", "mua sắm"),
            intent = "SHOPPING"
        ),
        BenchmarkItem(
            photoId = "photo_thermos_cup",
            title = "Ly giữ nhiệt Lock&Lock 500ml",
            text = "Lazada Mall Bình giữ nhiệt inox 316 Cốc uống nước giữ lạnh 24h Giá 289.000 VND Còn hàng",
            tags = listOf("ly nước", "bình giữ nhiệt", "cốc", "lock&lock", "đồ dùng", "uống nước"),
            intent = "SHOPPING"
        ),
        BenchmarkItem(
            photoId = "photo_flight_vj",
            title = "Vé máy bay Vietjet VJ198",
            text = "Vietjet Air Thẻ lên tàu bay Boarding Pass Chuyến bay VJ198 Khởi hành từ SGN đến HAN Giờ bay 14:30 Cửa 05 Ghế 12A",
            tags = listOf("chuyến bay", "vé máy bay", "vietjet", "sân bay", "lịch trình", "hà nội"),
            intent = "EVENT"
        ),
        BenchmarkItem(
            photoId = "photo_zoom_meeting",
            title = "Lịch họp dự án Sprint Review",
            text = "Zoom Meeting ID 889 1234 5678 Lịch họp đánh giá tiến độ quý 4 Thời gian 09:00 sáng thứ Hai ngày 17/11",
            tags = listOf("lịch họp", "cuộc họp", "zoom", "công việc", "tiến độ", "sự kiện"),
            intent = "EVENT"
        ),
        BenchmarkItem(
            photoId = "photo_doctor_appointment",
            title = "Phiếu hẹn khám Bệnh viện Đại học Y Dược",
            text = "Lịch hẹn tái khám chuyên khoa Tim mạch Bác sĩ Nguyễn Văn A Ngày khám 20/11/2026 lúc 08:30 sáng Phòng 204",
            tags = listOf("khám bệnh", "lịch hẹn", "bệnh viện", "bác sĩ", "sức khỏe", "y tế"),
            intent = "EVENT"
        ),
        BenchmarkItem(
            photoId = "photo_cgv_movie",
            title = "Vé xem phim CGV Cinemas",
            text = "CGV Vincom Đồng Khởi Phim Avatar Lửa và Tro Suất chiếu 20:15 Rạp IMAX Ghế H11 H12 Giá vé 320.000 đ",
            tags = listOf("vé xem phim", "cgv", "cinema", "phim", "giải trí", "cuối tuần"),
            intent = "EVENT"
        ),
        BenchmarkItem(
            photoId = "photo_mountain_trekking",
            title = "Đỉnh Fansipan Sapa biển mây",
            text = "Chinh phục nóc nhà Đông Dương Fansipan Sapa Trekking leo núi phong cảnh thiên nhiên hùng vĩ mây trắng",
            tags = listOf("thiên nhiên", "núi", "đồi núi", "phong cảnh", "leo núi", "sapa", "dã ngoại"),
            intent = "OTHER"
        ),
        BenchmarkItem(
            photoId = "photo_beach_sunset",
            title = "Hoàng hôn bãi biển Phú Quốc",
            text = "Bãi Sao Phú Quốc hoàng hôn chiều tà mặt trời lặn bờ biển cát trắng đại dương xanh sóng vỗ",
            tags = listOf("biển", "bãi biển", "hoàng hôn", "phú quốc", "thiên nhiên", "nghỉ dưỡng"),
            intent = "OTHER"
        ),
        BenchmarkItem(
            photoId = "photo_cccd_card",
            title = "Ảnh Căn cước công dân gắn chip",
            text = "CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM CĂN CƯỚC CÔNG DÂN Họ và tên Nguyễn Văn B Ngày sinh 15/08/1995 Quê quán Hà Nội",
            tags = listOf("căn cước", "cccd", "chứng minh nhân dân", "hồ sơ", "giấy tờ", "cá nhân"),
            intent = "OTHER"
        ),
        BenchmarkItem(
            photoId = "photo_prescription",
            title = "Đơn thuốc điều trị cảm sốt",
            text = "Đơn thuốc Bệnh viện Bạch Mai Paracetamol 500mg ngày uống 2 viên Vitamin C Khẩu trang y tế Uống sau ăn",
            tags = listOf("thuốc", "đơn thuốc", "y tế", "paracetamol", "sốt", "sức khỏe"),
            intent = "OTHER"
        ),
        BenchmarkItem(
            photoId = "photo_code_stacktrace",
            title = "Lỗi NullPointerException Android",
            text = "java.lang.NullPointerException at com.example.app.MainActivity.onCreate Fatal Exception AndroidRuntime git commit bug",
            tags = listOf("code", "lỗi", "bug", "android", "crash", "github"),
            intent = "READING"
        ),
        BenchmarkItem(
            photoId = "photo_news_traffic",
            title = "Tin tức ùn tắc giao thông Hà Nội",
            text = "VnExpress Tin tức thời sự Mưa lớn khiến đường Nguyễn Trãi ùn tắc nghiêm trọng sáng đầu tuần CSGT điều tiết",
            tags = listOf("tin tức", "thời sự", "giao thông", "hà nội", "báo chí", "bài viết"),
            intent = "READING"
        )
    )

    // 60 Real-world Vietnamese Search Queries
    private val benchmarkQueries = listOf(
        // Group 1: Shopping & Merchandise (15 queries)
        BenchmarkQuery("mua nồi cơm điện", "photo_shopee_rice_cooker", "Shopping"),
        BenchmarkQuery("noi com bluestone", "photo_shopee_rice_cooker", "Shopping"),
        BenchmarkQuery("đồ gia dụng nhà bếp", "photo_shopee_rice_cooker", "Shopping"),
        BenchmarkQuery("đơn hàng shopee", "photo_shopee_rice_cooker", "Shopping"),
        BenchmarkQuery("chuyển khoản vietcombank", "photo_vcb_transfer", "Banking"),
        BenchmarkQuery("tiền nhà vcb", "photo_vcb_transfer", "Banking"),
        BenchmarkQuery("bien lai chuyen tien", "photo_vcb_transfer", "Banking"),
        BenchmarkQuery("hóa đơn grabfood", "photo_grab_receipt", "Food Receipt"),
        BenchmarkQuery("cơm tấm trưa nay", "photo_grab_receipt", "Food Receipt"),
        BenchmarkQuery("gia com phuc loc tho", "photo_grab_receipt", "Food Receipt"),
        BenchmarkQuery("mua tivi sony", "photo_sony_tv", "Shopping"),
        BenchmarkQuery("ti vi dien may xanh", "photo_sony_tv", "Shopping"),
        BenchmarkQuery("bravia 4k 65 inch", "photo_sony_tv", "Shopping"),
        BenchmarkQuery("ly giữ nhiệt lock&lock", "photo_thermos_cup", "Product"),
        BenchmarkQuery("bình nước inox", "photo_thermos_cup", "Product"),

        // Group 2: Events, Flights & Schedules (15 queries)
        BenchmarkQuery("vé máy bay vietjet", "photo_flight_vj", "Event"),
        BenchmarkQuery("chuyến bay đi hà nội", "photo_flight_vj", "Event"),
        BenchmarkQuery("ve bay vj198", "photo_flight_vj", "Event"),
        BenchmarkQuery("lịch họp zoom", "photo_zoom_meeting", "Meeting"),
        BenchmarkQuery("họp sprint review thứ hai", "photo_zoom_meeting", "Meeting"),
        BenchmarkQuery("meeting id zoom", "photo_zoom_meeting", "Meeting"),
        BenchmarkQuery("lịch hẹn khám bệnh", "photo_doctor_appointment", "Appointment"),
        BenchmarkQuery("tái khám tim mạch", "photo_doctor_appointment", "Appointment"),
        BenchmarkQuery("benh vien dai hoc y duoc", "photo_doctor_appointment", "Appointment"),
        BenchmarkQuery("vé xem phim cgv", "photo_cgv_movie", "Entertainment"),
        BenchmarkQuery("phim avatar rạp imax", "photo_cgv_movie", "Entertainment"),
        BenchmarkQuery("suat chieu cgv toi nay", "photo_cgv_movie", "Entertainment"),
        BenchmarkQuery("giờ bay sân bay tân sơn nhất", "photo_flight_vj", "Event"),
        BenchmarkQuery("cuộc họp tuần tới", "photo_zoom_meeting", "Meeting"),
        BenchmarkQuery("lịch tái khám bác sĩ", "photo_doctor_appointment", "Appointment"),

        // Group 3: Nature, Scenery & Outdoors (10 queries)
        BenchmarkQuery("leo núi fansipan", "photo_mountain_trekking", "Nature"),
        BenchmarkQuery("phong cảnh sapa biển mây", "photo_mountain_trekking", "Nature"),
        BenchmarkQuery("trekking đỉnh núi", "photo_mountain_trekking", "Nature"),
        BenchmarkQuery("thiên nhiên hùng vĩ sapa", "photo_mountain_trekking", "Nature"),
        BenchmarkQuery("bãi biển phú quốc", "photo_beach_sunset", "Nature"),
        BenchmarkQuery("hoàng hôn bờ biển", "photo_beach_sunset", "Nature"),
        BenchmarkQuery("bãi sao sóng biển", "photo_beach_sunset", "Nature"),
        BenchmarkQuery("du lịch biển cát trắng", "photo_beach_sunset", "Nature"),
        BenchmarkQuery("canh dep nui rung", "photo_mountain_trekking", "Nature"),
        BenchmarkQuery("bien phu quoc hoang hon", "photo_beach_sunset", "Nature"),

        // Group 4: Documents, IDs & Health (10 queries)
        BenchmarkQuery("căn cước công dân", "photo_cccd_card", "Personal Doc"),
        BenchmarkQuery("can cuoc gan chip", "photo_cccd_card", "Personal Doc"),
        BenchmarkQuery("chứng minh nhân dân nguyễn văn b", "photo_cccd_card", "Personal Doc"),
        BenchmarkQuery("ảnh giấy tờ tùy thân", "photo_cccd_card", "Personal Doc"),
        BenchmarkQuery("đơn thuốc cảm sốt", "photo_prescription", "Medical"),
        BenchmarkQuery("thuốc paracetamol bạch mai", "photo_prescription", "Medical"),
        BenchmarkQuery("toa thuoc ha sot", "photo_prescription", "Medical"),
        BenchmarkQuery("đơn thuốc bác sĩ", "photo_prescription", "Medical"),
        BenchmarkQuery("cmnd que quan ha noi", "photo_cccd_card", "Personal Doc"),
        BenchmarkQuery("thuoc uong sau an", "photo_prescription", "Medical"),

        // Group 5: News & Tech Reading (10 queries)
        BenchmarkQuery("tin tức kẹt xe hà nội", "photo_news_traffic", "News"),
        BenchmarkQuery("ùn tắc đường nguyễn trãi", "photo_news_traffic", "News"),
        BenchmarkQuery("thời sự mưa lớn giao thông", "photo_news_traffic", "News"),
        BenchmarkQuery("bài báo vnexpress giao thong", "photo_news_traffic", "News"),
        BenchmarkQuery("lỗi code android crash", "photo_code_stacktrace", "Tech"),
        BenchmarkQuery("nullpointerexception git commit", "photo_code_stacktrace", "Tech"),
        BenchmarkQuery("lỗi crash mainactivity", "photo_code_stacktrace", "Tech"),
        BenchmarkQuery("bug stacktrace", "photo_code_stacktrace", "Tech"),
        BenchmarkQuery("tin tuc sang thu hai", "photo_news_traffic", "News"),
        BenchmarkQuery("fatal exception android", "photo_code_stacktrace", "Tech")
    )

    @Test
    fun testPhase0_BenchmarkRecallAndMRR() {
        // Pre-compute indexed vectors for corpus
        val indexedVectors = corpus.associate { item ->
            val composedText = "${item.title} ${item.tags.joinToString(" ")} ${item.text}"
            val vec = VectorSemanticSearchEngine.generateEmbedding(
                text = composedText,
                title = item.title,
                intent = item.intent,
                categoryNames = item.tags
            )
            item.photoId to vec
        }

        var hitsAt10 = 0
        var reciprocalRankSum = 0.0

        for (bq in benchmarkQueries) {
            val results = VectorSemanticSearchEngine.search(
                query = bq.query,
                indexedVectors = indexedVectors,
                minSimilarity = 0.15f
            )

            val rank = results.indexOfFirst { it.first == bq.expectedPhotoId }
            if (rank in 0..9) {
                hitsAt10++
                reciprocalRankSum += 1.0 / (rank + 1)
            }
        }

        val recallAt10 = hitsAt10.toDouble() / benchmarkQueries.size
        val mrr = reciprocalRankSum / benchmarkQueries.size

        println("==================================================")
        println("📊 SEARCH QUALITY BENCHMARK RESULTS (Phase 0)")
        println("Total Queries Evaluated: ${benchmarkQueries.size}")
        println("Recall@10: ${String.format(Locale.ROOT, "%.2f%%", recallAt10 * 100)} (Target: >= 85%)")
        println("MRR (Mean Reciprocal Rank): ${String.format(Locale.ROOT, "%.3f", mrr)} (Target: >= 0.70)")
        println("==================================================")

        // Strict quality thresholds
        assertTrue("Recall@10 should be at least 85%, got $recallAt10", recallAt10 >= 0.85)
        assertTrue("MRR should be at least 0.70, got $mrr", mrr >= 0.70)
    }

    @Test
    fun testPhase5_PiiScrubbingExpansion() {
        // 1. Bank Account scrubbing
        val textWithBank = "STK: 012345678912 tại Ngân hàng Vietcombank chi nhánh Hà Nội"
        val scrubbedBank = PiiScrubber.scrub(textWithBank)
        assertTrue(scrubbedBank.hasPiiRedacted)
        assertTrue(scrubbedBank.scrubbedText.contains("STK_****"))
        assertTrue(!scrubbedBank.scrubbedText.contains("012345678912"))

        // 2. Email scrubbing
        val textWithEmail = "Vui lòng liên hệ support.billing@shopee.vn để nhận lại tiền"
        val scrubbedEmail = PiiScrubber.scrub(textWithEmail)
        assertTrue(scrubbedEmail.hasPiiRedacted)
        assertTrue(scrubbedEmail.scrubbedText.contains("[EMAIL_REDACTED]"))
        assertTrue(!scrubbedEmail.scrubbedText.contains("support.billing@shopee.vn"))

        // 3. Vietnamese Phone scrubbing
        val textWithVnPhone = "Giao hàng tới số 0987654321 người nhận anh Tuấn"
        val scrubbedPhone = PiiScrubber.scrub(textWithVnPhone)
        assertTrue(scrubbedPhone.hasPiiRedacted)
        assertTrue(scrubbedPhone.scrubbedText.contains("***-***-"))
        assertTrue(!scrubbedPhone.scrubbedText.contains("0987654321"))
    }

    @Test
    fun testPhase3_TemporalAndIntentParsing() {
        val parsed = TemporalQueryParser.parse("mua nồi cơm hôm qua")
        assertTrue(parsed.cleanTextQuery.startsWith("mua nồi cơm"))
        assertNotNull(parsed.dateRangeMillis)
    }
}

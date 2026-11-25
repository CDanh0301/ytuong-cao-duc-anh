package com.example.test2.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextualClassificationTest {

    @Test
    fun testHospitalSchedulesAreClassifiedAsEvents() {
        val img1 = """
            Screenshot_20261110-1027
            IVIE - BÁC SĨ ƠI
            BỆNH VIỆN 19-8
            LỊCH KHÁM THEO YÊU CẦU
            TUẦN TỪ 17/8 ĐẾN 21/8/2026
            PHÒNG/BUỔI THỨ 2 (17/8) THỨ 3 (18/8) THỨ 4 (19/8) THỨ 5 (20/8) THỨ 6 (21/8)
            PHÒNG KHÁM SỨC KHỎE TỔNG QUÁT THEO YÊU CẦU
            SÁNG: 7H30 - 11H30
            CHIỀU: 13H30 - 16H30
            BS CKI Bùi Thanh Tú
            Nghỉ lễ 19/8
            BS CKI Dương Thị Ngọ
            PGS.TS.BS Nguyễn Thị Lan
            Địa chỉ: Số 9 Trần Bình, phường Từ Liêm, TP. Hà Nội - Website: www.benhvien19-8.vn
            Khách có nhu cầu đặt lịch khám, Xin vui lòng liên hệ qua Tổng đài: 0243.837.3747 hoặc truy cập trên ứng dụng IVIE - Bác sĩ ơi
            Lịch khám theo yêu cầu tại Bệnh viện 19-8 tuần từ 10/8 đến...
            Truy cập
            Chia sẻ
            Lưu
            BỆNH VIỆN ĐA KHOA TỈNH THANH HÓA
            DANH SÁCH CÁC CHUYÊN GIA KHÁM BỆNH THEO YÊU CẦU
            Facebook
            LỊCH KHÁM BỆNH THEO YÊU CẦU
        """.trimIndent()

        val img2 = """
            Screenshot_20261110-1027
            Bệnh viện đa khoa TTH Hà Tĩnh
            BỆNH VIỆN ĐA KHOA TTH HÀ TĨNH
            TTH HA TINH GENERAL HOSPITAL
            LỊCH KHÁM BỆNH
            TỪ NGÀY 20/9/2021
            THỨ 2 THỨ 3 THỨ 4 THỨ 5 THỨ 6 THỨ 7 CHỦ NHẬT
            TIẾP NHẬN KHÁM & ĐIỀU TRỊ BHYT KHÁM DỊCH VỤ
            SÁNG: 7H00 - 11H30
            CHIỀU: 13H15 - 17H00
            Gọi ngay cho chúng tôi!
            0911.92.91.92
            THÔNG BÁO LỊCH KHÁM BỆNH - Bệnh viện đa khoa TTH Hà Tĩnh
            Người tạo: PVNA
            Truy cập
            Chia sẻ
            Lưu
            BỆNH VIỆN VIỆT NAM - THỤY ĐIỂN UÔNG BÍ
            ĐĂNG KÝ KHÁM BỆNH TỪ XA
            GỌI ĐIỆN ĐẾN SỐ: 02036.507.252
            HƯỚNG DẪN ĐẶT LỊCH KHÁM QUA ĐIỆN THOẠI
            THÔNG BÁO
            THAY ĐỔI THỜI GIAN KHÁM CHỮA BỆNH
            ÁP DỤNG TỪ 01/10/2025
            Bệnh viện Nội tiết Trung...
        """.trimIndent()

        val img3 = """
            Screenshot_20261110-1027
            Bệnh Viện Đa Khoa Nguyễn...
            LỊCH LÀM VIỆC TUẦN PHÒNG KHÁM NỘI THEO YÊU CẦU
            KHOA KHÁM BỆNH
            Từ 15/06/2026 đến 19/06/2026
            Thứ Ngày Buổi Họ tên BS phụ trách
            Thứ 2 15/06/2026 Sáng BS Hoàng Phùng Long
            ThsBSCKII: Huỳnh Thị Ngọc Trang
            Chiều BS Hoàng Phùng Long
            Thứ 3 16/06/2026 Sáng BS Đặng Thị Nam
            BSCKI: Ngô Cao Thanh Hằng
            Chiều BS Đặng Thị Nam
            Thứ 4 17/06/2026 Sáng BSCKI: Võ Kim Chi
            Chiều BSCKI: Võ Kim Chi
            Thứ 5 18/06/2026 Sáng BSCKI: Ngô Cao Thanh Hằng
            BS Đặng Thị Nam
            Chiều BS Đặng Thị Nam
            Thứ 6 19/06/2026 Sáng ThsBSCKII: Huỳnh Thị Ngọc Trang
            BS Đặng Thị Nam
            Chiều BS Đặng Thị Nam
            Tận tâm - Chuyên nghiệp - Vì sức khỏe cộng đồng
            THÔNG BÁO LỊCH LÀM VIỆC PHÒNG KHÁM NỘI THEO YÊU...
            Truy cập
            Chia sẻ
            Lưu
            LỊCH LÀM VIỆC BÁC SĨ
            Trung tâm An dưỡng Hoàng...
            PHÒNG KHÁM ĐA KHOA THÀNH CÔNG
            LỊCH HOẠT ĐỘNG CÁC...
            Phòng khám đa khoa Thành...
            TRẠM Y TẾ XÃ HÒA PHÚ
            LỊCH KHÁM SÀNG LỌC HOẶC KHÁM SỨC KHỎE ĐỊNH KỲ
            KHÁM CHỮA BỆNH Cùng chuyên gia TRUNG ƯƠNG
            Lịch khám Thứ 7 & Chủ Nhật
            Ngày 31.10.2026 & 01.11.2026
        """.trimIndent()

        val img4 = """
            Screenshot_20261110-1027
            Bệnh Viện Nhi Đồng Thành Phố
            GIỜ NHẬN BỆNH
            GIỜ KHÁM BỆNH
            A. Thứ 2 đến thứ 6
            Ban Mai: 05g45 - 7g00 Ban Mai: 06g00 - 07g30
            Sáng: 06g30 - 11g15 Sáng: 07g00 - 11g30
            Trưa: 11g15 - 12g15 Trưa: 11g30 - 12g30
            Chiều: 12g15 - 15g45 Chiều: 12g30 - 16g00
            Tăng cường: 15g45 - 19g15 Tăng cường: 16g00 - 19g30
            Cha Mẹ Bận Rộn Cha Mẹ Bận Rộn
            Sáng: 07g00 - 10g30 Sáng: 07g30 - 11g30
            Chiều: 12g30 - 15g00 Chiều: 13g00 - 16g00
            Tiêm chủng Tiêm chủng
            Sáng: 07g00 - 11g00 Sáng: 07g00 - 11g30
            Chiều: 12g00 - 15g30 Chiều: 12g30 - 16g00
            B. Thứ 7, CN & Lễ Tết B. Thứ 7, CN & Lễ Tết
            Khu Tiêu chuẩn Khu Tiêu chuẩn
            Tăng cường: 15g45 - 19g15 Tăng cường: 16g00 - 19g30
            Khu Robot Khu Robot
            Sáng: 06g30 - 11g15 Sáng: 7g00 - 11g30
            Chiều: 11g15 - 15g45 Chiều: 11g30 - 16g00
            Tăng cường: 15g45 - 19g15 Tăng cường: 16g00 - 19g30
            Khu Siêu nhân Khu Siêu nhân
            Ban Mai: 05g45 - 7g00 Ban Mai: 06g00 - 07g30
            Sáng: 06g30 - 11g15 Sáng: 7g00 - 11g30
            Chiều: 11g15 - 15g45 Chiều: 11g30 - 16g00
            Tăng cường: 15g45 - 19g15 Tăng cường: 16g00 - 19g30
            Khu Cha Mẹ Bận Rộn Khu Cha Mẹ Bận Rộn
            Sáng: 06g30 - 10g30 Sáng: 07g00 - 11g30
            Tiêm chủng Tiêm chủng
            Sáng: 07g00 - 11g00 Sáng: 07g00 - 11g30
            Chiều: 11g00 - 15g00 Chiều: 11g30 - 15g30
            Lịch khám chữa bệnh - Bệnh Viện Nhi Đồng Thành Phố
            Truy cập
            Lưu
            PHÒNG CHUYÊN KHOA CA KHÁM GIÁ KHÁM GHI CHÚ
            A7 Cấp cứu ngoại 24/24 BHYT & 50.000 đ 150.000 đ
            A11 Ngoại chỉnh hình - buôn nón Thứ 2, Thứ 4 - Dưới 6 tuổi BHYT & 150.000 đ
            LỊCH KHÁM CHỮA BỆNH CỦA CÁC BÁC SĨ CHUYÊN GIA HÀ NỘI
        """.trimIndent()

        val p1 = SmartHeuristicParser.parse(img1, "Screenshot_20261110-1027", hasFace = false)
        val p2 = SmartHeuristicParser.parse(img2, "Screenshot_20261110-1027", hasFace = false)
        val p3 = SmartHeuristicParser.parse(img3, "Screenshot_20261110-1027", hasFace = true) // Even with face in thumbnail!
        val p4 = SmartHeuristicParser.parse(img4, "Screenshot_20261110-1027", hasFace = true)

        assertEquals("cat_events", p1.targetCategoryId)
        assertEquals("EVENT", p1.detectedIntent)

        assertEquals("cat_events", p2.targetCategoryId)
        assertEquals("EVENT", p2.detectedIntent)

        assertEquals("cat_events", p3.targetCategoryId)
        assertEquals("EVENT", p3.detectedIntent)

        assertEquals("cat_events", p4.targetCategoryId)
        assertEquals("EVENT", p4.detectedIntent)
    }

    @Test
    fun testSchoolTimetableIsClassifiedAsEvent() {
        val schoolSchedule = """
            TRƯỜNG ĐẠI HỌC BÁCH KHOA HÀ NỘI
            THỜI KHÓA BIỂU HỌC KỲ 1 NĂM HỌC 2026-2027
            Thứ 2: Tiết 1 - 3 (06h45 - 09h10) Giải tích 1 - Giảng viên: PGS.TS Trần Văn Hùng - Phòng D9-301
            Thứ 4: Tiết 4 - 6 (09h20 - 11h45) Lập trình Di động Android - Giảng viên: ThS Lê Quang Minh
            Thứ 6: Tiết 7 - 9 (12h30 - 15h00) Cấu trúc Dữ liệu & Giải thuật
        """.trimIndent()

        val p = SmartHeuristicParser.parse(schoolSchedule, "Screenshot_TKB")
        assertEquals("cat_events", p.targetCategoryId)
        assertEquals("EVENT", p.detectedIntent)
    }

    @Test
    fun testECommerceShoppingProductIsClassifiedAsToBuy() {
        val productPage = """
            Shopee Mall
            Nồi chiên không dầu Philips 6.2 lít HD9270/90
            5.0 Đánh giá (12.4k lượt bán)
            ₫ 2.490.000
            ₫ 3.890.000 -36%
            Voucher giảm 150k
            Miễn phí vận chuyển toàn quốc
            Thêm vào giỏ hàng
            Mua ngay
        """.trimIndent()

        val p = SmartHeuristicParser.parse(productPage, "Screenshot_Shopee")
        assertEquals("cat_tobuy", p.targetCategoryId)
        assertEquals("SHOPPING", p.detectedIntent)
    }

    @Test
    fun testGenuineIdentityDocumentIsClassifiedAsPersonal() {
        val cccd = """
            CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM
            Độc lập - Tự do - Hạnh phúc
            CĂN CƯỚC CÔNG DÂN
            Số / No: 038096001234
            Họ và tên / Full name: NGUYỄN VĂN AN
            Ngày sinh / Date of birth: 15/08/1995
            Giới tính / Sex: Nam
            Quốc tịch / Nationality: Việt Nam
            Quê quán / Place of origin: Thanh Hóa
            Nơi thường trú: Ba Đình, Hà Nội
        """.trimIndent()

        val p = SmartHeuristicParser.parse(cccd, "Screenshot_CCCD", hasFace = true)
        assertEquals("cat_personal", p.targetCategoryId)
        assertEquals("OTHER", p.detectedIntent)
    }

    @Test
    fun testNewsArticleIsClassifiedAsReading() {
        val news = """
            VnExpress - Báo điện tử tiếng Việt nhiều người xem nhất
            Thời sự: Ùn tắc nghiêm trọng kéo dài tại cửa ngõ phía Nam Hà Nội sáng đầu tuần
            Thứ hai, 16/11/2026, 08:30 (GMT+7)
            Mưa lạnh kết hợp lưu lượng phương tiện đổ về thủ đô khiến các tuyến đường Giải Phóng, Ngọc Hồi kẹt cứng hàng kilomet.
            Tác giả: Tuấn Anh - Báo điện tử VnExpress
        """.trimIndent()

        val p = SmartHeuristicParser.parse(news, "Screenshot_News")
        assertEquals("cat_reading", p.targetCategoryId)
        assertEquals("READING", p.detectedIntent)
    }

    @Test
    fun testNatureLandscapeIsClassifiedAsNature() {
        val nature = """
            National Geographic - Nature Photography
            Dãy núi Hoàng Liên Sơn hùng vĩ trong sương sớm
            Phong cảnh thiên nhiên hoang dã Tây Bắc với những cánh rừng cây bạt ngàn và thung lũng tuyệt đẹp.
            Photo by Unsplash Landscape Collection
        """.trimIndent()

        val p = SmartHeuristicParser.parse(nature, "Screenshot_Nature")
        assertEquals("cat_nature", p.targetCategoryId)
        assertEquals("OTHER", p.detectedIntent)
    }

    @Test
    fun testNewlyCapturedPhotoClassifiedImmediatelyIntoCustomCategoryWithoutExactNameMatch() {
        val customCat = com.example.test2.shared.models.Category(
            id = "cat_custom_coffee",
            name = "Cà phê & Đồ uống",
            colorHex = "#6366F1",
            iconName = "tag"
        )

        // Receipt contains "Starbucks Coffee", "Latte", "Macchiato" without containing the literal Vietnamese words "cà phê" or "đồ uống"
        val starbucksReceipt = """
            Starbucks Coffee
            Store #1204 - Vincom Center
            1x Caramel Macchiato (Tall)
            1x Iced Green Tea Latte
            Subtotal: 185.000 VND
            Total: 185.000 VND
            Thank you for visiting!
        """.trimIndent()

        val parsed = SmartHeuristicParser.parse(
            scrubbedText = starbucksReceipt,
            photoTitle = "Screenshot_Starbucks.png",
            customCategories = listOf(customCat)
        )

        assertEquals("cat_custom_coffee", parsed.targetCategoryId)
    }

    @Test
    fun testNewlyCapturedPhotoVehicleClassifiedImmediatelyIntoVehicleCategory() {
        val vehicleCat = com.example.test2.shared.models.Category(
            id = "cat_custom_vehicles",
            name = "Xe cộ & Đi lại",
            colorHex = "#10B981",
            iconName = "car"
        )

        // Text contains motorcycle maintenance details
        val motorbikeDoc = """
            Hóa đơn dịch vụ bảo dưỡng
            Thay nhớt Castrol Power 1
            Kiểm tra lốp xe máy Honda Wave Alpha
            Tiền công sửa chữa: 50.000 đ
            Tổng thanh toán: 170.000 đ
        """.trimIndent()

        val parsed = SmartHeuristicParser.parse(
            scrubbedText = motorbikeDoc,
            photoTitle = "Screenshot_BaoDuong.png",
            customCategories = listOf(vehicleCat)
        )

        assertEquals("cat_custom_vehicles", parsed.targetCategoryId)
    }

    @Test
    fun testDiningAndMapsCategorizationConsistency() {
        val samples = listOf(
            """
                Tung's Kitchen Vietnamese Cuisine & Vegetarian Food
                4,9 ★ (22,2 N) Món ăn Việt Nam Mở cửa
                Tổng quan Gọi điện Menu Đường đi Đặt đến lấy
                Xem thực đơn
                Đang mở cửa · Đóng cửa vào 22:30
                2 P. Hàng Bông, Phố cổ Hà Nội
                100.000-300.000 đ/người
                22.199 Bài đánh giá
            """.trimIndent(),
            """
                MOO BEEF STEAK TRẦN QUỐC TOẢN
                4,9 ★ (6 N) Nhà hàng Mở cửa
                Tổng quan Gọi điện Bài đánh giá Đường đi
                Xem thực đơn Đặt bàn
                Đang mở cửa Đóng cửa vào 14:00 Mở lại vào 17:30
                59A P. Trần Quốc Toản, P. Cửa Nam, Hà Nội
                5.983 Bài đánh giá
            """.trimIndent(),
            """
                Le Monde Steak - Vincom Bà Triệu
                4,6 ★ (799) Bít tết Mở cửa
                Tổng quan Gọi điện Bài đánh giá Đường đi
                Đặt qua mạng Xem thực đơn
                Đang mở cửa · Đóng cửa vào 22:00
                Vincom Center, Bà Triệu, Hai Bà Trưng, Hà Nội
                100.000-600.000 đ/người
                799 Bài đánh giá
            """.trimIndent(),
            """
                Sushi Việt Gỏi Cuốn Việt
                4,5 ★ (602) Món ăn xuyên Á Mở cửa
                Tổng quan Gọi điện Bài đánh giá Đường đi
                Xem thực đơn
                Đang mở cửa Đóng cửa vào 22:00
                151 Trần Đại Nghĩa, Bạch Mai, Hà Nội
                100.000-200.000 đ/người
                602 Bài đánh giá
            """.trimIndent()
        )

        for (sample in samples) {
            val parsed = SmartHeuristicParser.parse(
                scrubbedText = sample,
                photoTitle = "Screenshot_GoogleMaps.png"
            )
            assertEquals("All dining & maps places must consistently classify into cat_tobuy", "cat_tobuy", parsed.targetCategoryId)
            assertEquals("All dining & maps places must have SHOPPING intent", "SHOPPING", parsed.detectedIntent)
        }
    }
}

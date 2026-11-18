package com.example.test2.ai

internal object HeuristicKeywords {

    // Strict action keywords required to qualify as an EVENT or Schedule
    val EVENT_ACTION_KEYWORDS = listOf(
        // Multi-word schedule & timetable phrases
        "lịch khám", "lich kham", "lịch làm việc", "lich lam viec",
        "giờ nhận bệnh", "gio nhan benh", "giờ khám", "gio kham", "giờ khám bệnh", "gio kham benh",
        "lịch trực", "lich truc", "thời khóa biểu", "thoi khoa bieu",
        "lịch hoạt động", "lich hoat dong", "lịch chiếu", "lich chieu",
        "chuyến bay", "chuyen bay", "boarding pass", "vé bay", "ve bay",
        "vé xem phim", "ve xem phim", "lịch hẹn", "lich hen", "lịch họp", "lich hop",
        "doctor appointment", "meeting schedule", "conference call", "sinh nhật", "sinh nhat",
        "hội thảo", "hoi thao", "khám bệnh", "kham benh", "đặt chỗ", "dat cho",
        // Standalone whole words
        "meeting", "họp", "hop", "zoom", "calendar", "schedule", "appointment",
        "lịch", "flight", "ticket", "webinar", "party", "showtime", "booking", "reservation"
    )

    // Identity Document official legal headers & titles
    val IDENTITY_DOC_SIGNATURES = listOf(
        "cộng hòa xã hội chủ nghĩa việt nam", "cong hoa xa hoi chu nghia viet nam",
        "độc lập tự do hạnh phúc", "doc lap tu do hanh phuc",
        "căn cước công dân", "can cuoc cong dan", "căn cước", "can cuoc", "cccd", "cmnd",
        "chứng minh nhân dân", "chung minh nhan dan", "chứng minh thư", "chung minh thu",
        "giấy phép lái xe", "giay phep lai xe", "gplx", "bằng lái xe", "bang lai xe",
        "hộ chiếu", "ho chieu", "passport", "sổ hộ khẩu", "so ho khau",
        "giấy khai sinh", "giay khai sinh", "thẻ căn cước", "the can cuoc",
        "thẻ sinh viên", "the sinh vien", "thẻ nhân viên", "the nhan vien",
        "thẻ bảo hiểm y tế", "the bao hiem y te",
        "identity card", "id card", "driver license", "driving license", "driver's license", "national id"
    )

    // Personal & People: Portraits, selfies, personal IDs, CCCD, family, profiles, bio/wiki photos
    val PERSONAL_KEYWORDS = listOf(
        // Vietnamese phrases & IDs
        "chân dung", "chan dung", "ảnh chân dung", "anh chan dung",
        "tự sướng", "tu suong", "ảnh tự sướng", "anh tu suong",
        "cá nhân", "ca nhan", "trang cá nhân", "trang ca nhan", "hồ sơ cá nhân", "ho so ca nhan",
        "căn cước công dân", "can cuoc cong dan", "cccd", "cmnd",
        "chứng minh nhân dân", "chung minh nhan dan",
        "hộ chiếu", "ho chieu", "passport", "bằng lái xe", "bang lai xe",
        "giấy phép lái xe", "giay phep lai xe", "gplx", "thẻ sinh viên", "the sinh vien",
        "thẻ nhân viên", "the nhan vien", "thẻ bảo hiểm y tế", "the bao hiem y te",
        "ảnh gia đình", "anh gia dinh", "ảnh đại diện", "anh dai dien", "ảnh bạn bè",
        // English keywords
        "portrait", "portraits", "selfie", "selfies", "headshot",
        "identity card", "id card", "driver license", "driving license", "driver's license", "national id",
        "student id", "employee id", "family photo", "family portrait",
        "profile photo", "profile picture", "avatar", "resume", "curriculum vitae", "user profile", "my profile",
        // Domains and social profile cues
        "wikimedia", "wikipedia", "facebook", "instagram", "linkedin", "threads"
    )

    // Nature & Scenery: Landscapes, mountains, forests, outdoors, scenic photo sources
    val NATURE_KEYWORDS = listOf(
        // Specific compound phrases
        "thiên nhiên", "thien nhien", "cảnh thiên nhiên", "canh thien nhien",
        "phong cảnh", "phong canh", "cảnh đẹp", "canh dep",
        "đỉnh núi", "dinh nui", "đồi núi", "doi nui", "dãy núi", "day nui",
        "thác nước", "thac nuoc", "bãi biển", "bai bien", "bờ biển", "bo bien",
        "dã ngoại", "da ngoai", "leo núi", "leo nui", "rừng rậm", "rung ram",
        "thung lũng", "thung lung", "rừng cây", "rung cay", "cây cối", "cay coi",
        "mùa thu", "mua thu", "lá vàng", "la vang", "hoàng hôn", "hoang hon", "bình minh", "binh minh",
        "nature landscape", "nature scenery", "outdoor nature", "scenic view",
        "golden trees", "golden forest", "autumn forest",
        // Exact whole words (safe token matching via tokens.contains)
        "núi", "mountain", "mountains", "landscape", "landscapes", "scenery", "scenic",
        "waterfall", "waterfalls", "forest", "forests", "rừng", "trekking", "hiking",
        "hoang dã", "wilderness",
        "autumn", "golden", "leaves", "foliage",
        "lake", "lakes", "river", "rivers", "suối",
        "beach", "beaches", "valley", "valleys",
        // Scenery & Wallpaper portals
        "nationalgeographic", "natgeo", "geographic", "geography",
        "pexels", "unsplash", "pixabay", "wallpaper", "wallpapers"
    )

    // Product keywords with explicit compound phrases
    val PRODUCT_KEYWORDS = listOf(
        "sản phẩm", "san pham", "mặt hàng", "mat hang", "hàng hóa", "hang hoa",
        "cốc sứ", "coc su", "ly nước", "ly nuoc", "bình nước", "binh nuoc", "tách trà", "tach tra",
        "bình giữ nhiệt", "binh giu nhiet", "bình hoa", "bình đun", "bình nóng lạnh", "bình gas", "bình thủy",
        "product", "products", "item", "items", "merchandise", "goods", "cup", "cups",
        "mug", "mugs", "tumbler", "drinkware", "thermos",
        "thiết bị", "thiet bi", "dụng cụ", "dung cu",
        "nồi cơm", "noi com", "nồi chiên", "noi chien", "nồi áp suất", "noi ap suat",
        "nồi lẩu", "noi lau", "nồi inox", "noi inox", "nồi đất", "chảo chống dính", "chao chong dinh",
        "chảo", "chao", "tivi", "tv", "bravia", "sony",
        "máy ảnh", "may anh", "camera", "canon", "nikon", "sony alpha", "fujifilm", "ống kính", "ong kinh", "lens", "eos",
        "tai nghe", "bàn phím", "chuột máy tính", "laptop", "máy giặt", "tủ lạnh",
        "quần áo", "giày dép", "balo", "túi xách", "trang trí", "diy"
    )

    val SHOPPING_KEYWORDS = listOf(
        "tổng cộng", "tong cong", "đơn hàng", "don hang", "hóa đơn", "hoa don",
        "thanh toán", "thanh toan", "giỏ hàng", "gio hang", "chi phí", "chi phi",
        "chuyển khoản", "chuyen khoan", "total", "subtotal", "price", "order",
        "shopee", "lazada", "tiki", "amazon", "sendo", "dienmayxanh", "dientumienbac", "nguyenkim", "cellphones", "fptshop", "thegioididong",
        "receipt", "invoice", "₫", "$", "vnd", "paid", "cart",
        "còn hàng", "con hang", "hết hàng", "het hang", "mua ngay", "thêm vào giỏ", "them vao gio",
        "phí vận chuyển", "phi van chuyen", "vận chuyển", "van chuyen",
        "khuyến mãi", "khuyen mai", "ưu đãi", "uu dai", "bảo hành", "bao hanh",
        "mã sản phẩm", "ma san pham", "thương hiệu", "thuong hieu", "xuất xứ", "xuat xu",
        "vat", "số lượng", "so luong", "mức giá", "muc gia", "freeship"
    )

    val READING_KEYWORDS = listOf(
        // Journalism & Editorial phrases
        "bài viết", "bai viet", "tác giả", "tac gia", "trích dẫn", "trich dan",
        "tin tức", "tin tuc", "bài báo", "bai bao", "báo chí", "bao chi", "phóng sự", "phong su",
        "thời sự", "thoi su", "tin mới", "tin moi", "tin nóng", "tin nong", "điểm tin", "diem tin",
        "thông tin điện tử", "thong tin dien tu", "báo điện tử", "bao dien tu",
        "tạp chí", "tap chi", "toà soạn", "toa soan", "phóng viên", "phong vien", "nhà báo", "nha bao",
        "chuyên mục", "chuyen muc", "đọc tiếp", "doc tiep", "bản tin", "ban tin",
        "dự báo thời tiết", "du bao thoi tiet",
        "ùn tắc", "un tac", "kẹt xe", "ket xe", "giao thông giờ cao điểm",
        "mưa lớn sáng", "mưa lạnh", "mua lanh", "triều cường", "trieu cuong",
        "kinh tế xã hội", "thế giới", "the gioi", "pháp luật", "phap luat",
        "ngoại hạng anh", "ngoai hang anh",
        "đọc sách", "doc sach", "cuốn sách", "cuon sach", "trang sách", "quyen sach", "quyển sách",
        // English phrases & distinct single tokens
        "article", "author", "chapter 1", "chapter 2", "reading", "blog", "medium.com",
        "substack", "news", "editorial", "newsletter"
    )
}

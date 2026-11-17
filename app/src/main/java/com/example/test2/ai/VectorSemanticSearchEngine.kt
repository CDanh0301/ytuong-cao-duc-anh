package com.example.test2.ai

import com.example.test2.shared.models.Category
import com.example.test2.shared.utils.VietnameseUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.sqrt

/**
 * Advanced Vector Semantic Search Engine (IDEA.md Part 3 & Step 6 - Pro Tier).
 * Performs high-speed, 100% on-device semantic vector embedding and cosine similarity ranking.
 * Enables finding screenshots conceptually (e.g., searching "dinner" or "food" finds receipts/restaurant bills;
 * searching "cup" or "product" finds drinkware/merchandise; searching "mountain" finds landscapes/nature;
 * searching "cây" or "tree" finds nature/autumn photos; searching "nồi" or "tivi" finds specific products).
 * Fully optimized for English and Vietnamese (both accented and unaccented diacritics).
 */
object VectorSemanticSearchEngine {

    const val VECTOR_DIMENSION = 128

    internal data class SubConcept(
        val subDimOffset: Int,
        val keywords: Set<String>
    )

    internal data class SemanticCluster(
        val startIndex: Int,
        val dimCount: Int,
        val subConcepts: List<SubConcept>
    )

    // Semantic Concept Clusters mapped to specific sub-vector dimensions (14 dims each for 8 clusters = 112 dims)
    private val CONCEPT_CLUSTERS = listOf(
        // Cluster 0 (0..13): Events, Meetings, Flights, Schedules, Appointments
        SemanticCluster(
            startIndex = 0,
            dimCount = 14,
            subConcepts = listOf(
                SubConcept(0, setOf("flight", "flights", "airline", "airport", "boarding", "ticket", "pass", "bay", "chuyến bay", "chuyen bay", "vé bay", "ve bay", "vé máy bay", "ve may bay")),
                SubConcept(1, setOf("meeting", "meetings", "zoom", "meet", "webinar", "conference", "họp", "hop", "lịch họp", "lich hop", "hội thảo", "hoi thao")),
                SubConcept(2, setOf("appointment", "appointments", "booking", "reservation", "hẹn", "hen", "lịch hẹn", "lich hen", "khám bệnh", "kham benh", "đặt chỗ", "dat cho")),
                SubConcept(3, setOf("calendar", "schedule", "schedules", "lịch", "lich", "thời gian", "thoi gian", "timeline", "nhật ký", "nhat ky")),
                SubConcept(4, setOf("deadline", "deadlines", "hạn chót", "han chot", "thông báo", "thong bao", "nhắc nhở", "nhac nho")),
                SubConcept(5, setOf("am", "pm", "giờ", "gio", "phút", "phut", "sáng", "sang", "chiều", "chieu", "tối", "toi")),
                SubConcept(6, setOf("today", "tomorrow", "ngày", "ngay", "hôm nay", "hom nay", "mai", "tuần", "tuan", "tháng", "thang", "năm", "nam")),
                SubConcept(7, setOf("movie", "cinema", "show", "showtime", "party", "vé xem phim", "ve xem phim", "sinh nhật", "sinh nhat")),
                SubConcept(8, setOf("event", "events", "sự kiện", "su kien", "date", "dates", "time"))
            )
        ),
        // Cluster 1 (14..27): Shopping, Receipts, Invoices, Financial, Pricing
        SemanticCluster(
            startIndex = 14,
            dimCount = 14,
            subConcepts = listOf(
                SubConcept(0, setOf("receipt", "receipts", "invoice", "invoices", "bill", "bills", "hóa đơn", "hoa don", "biên lai", "bien lai")),
                SubConcept(1, setOf("price", "prices", "cost", "total", "subtotal", "giá", "gia", "mức giá", "muc gia", "tổng cộng", "tong cong", "tổng tiền", "tong tien")),
                SubConcept(2, setOf("vnd", "usd", "dollar", "dollars", "vnđ", "đồng", "dong", "đ")),
                SubConcept(3, setOf("payment", "paid", "transfer", "bank", "banking", "thanh toán", "thanh toan", "chuyển khoản", "chuyen khoan", "ngân hàng", "ngan hang", "tiền", "tien")),
                SubConcept(4, setOf("discount", "discounts", "voucher", "vouchers", "coupon", "coupons", "giảm giá", "giam gia", "khuyến mãi", "khuyen mai", "ưu đãi", "uu dai", "mã giảm", "ma giam")),
                SubConcept(5, setOf("shopee", "tiki", "lazada", "grab", "amazon", "sendo", "tiktok shop")),
                SubConcept(6, setOf("cart", "checkout", "giỏ hàng", "gio hang", "đặt hàng", "dat hang")),
                SubConcept(7, setOf("order", "orders", "đơn hàng", "don hang", "giao hàng", "giao hang", "ship", "freeship", "vận chuyển", "van chuyen", "phí vận chuyển", "phi van chuyen")),
                SubConcept(8, setOf("còn hàng", "con hang", "hết hàng", "het hang", "có sẵn", "co san", "in stock")),
                SubConcept(9, setOf("buy", "purchase", "shopping", "mua", "bán", "ban"))
            )
        ),
        // Cluster 2 (28..41): Products, Merchandise, Devices, Items, Household
        SemanticCluster(
            startIndex = 28,
            dimCount = 14,
            subConcepts = listOf(
                SubConcept(0, setOf("tivi", "ti vi", "tv", "sony", "bravia", "samsung", "lg", "màn hình", "man hinh", "television", "televisions", "screen", "screens")),
                SubConcept(1, setOf("nồi", "noi", "chảo", "chao", "lẩu", "lau", "nồi lẩu", "noi lau", "nồi inox", "noi inox", "inox", "bếp", "bep", "pot", "pots", "pan", "pans", "cookware")),
                SubConcept(2, setOf("máy tính", "may tinh", "laptop", "laptops", "bàn phím", "ban phim", "chuột", "chuot", "computer", "computers", "keyboard", "mouse")),
                SubConcept(3, setOf("tai nghe", "loa", "earphone", "earphones", "headphones", "headphone", "headset", "speaker", "speakers")),
                SubConcept(4, setOf("điện thoại", "dien thoai", "phone", "phones", "smartphone", "smartphones", "tablet", "tablets", "ipad")),
                SubConcept(5, setOf("quần áo", "quan ao", "áo", "ao", "quần", "quan", "váy", "vay", "giày", "giay", "dép", "dep", "clothes", "clothing", "shoes", "shirt")),
                SubConcept(6, setOf("túi", "tui", "balo", "túi xách", "tui xach", "bag", "bags", "backpack", "backpacks")),
                SubConcept(7, setOf("mỹ phẩm", "my pham", "son", "kem", "kem dưỡng", "kem duong", "cosmetics", "skincare")),
                SubConcept(8, setOf("trang trí", "trang tri", "diy", "thủ công", "thu cong", "decor", "craft", "crafts")),
                SubConcept(9, setOf("điện máy", "dien may", "đồ gia dụng", "do gia dung", "máy giặt", "may giat", "tủ lạnh", "tu lanh", "quạt", "quat", "appliance", "appliances")),
                SubConcept(10, setOf("hộp", "hop", "gói", "goi", "package", "packages", "box", "boxes", "parcel")),
                SubConcept(11, setOf("product", "products", "item", "items", "merchandise", "goods", "gadget", "device", "gear", "accessory", "sản phẩm", "san pham", "mặt hàng", "mat hang", "hàng hóa", "hang hoa", "hàng", "hang", "vật phẩm", "vat pham", "đồ dùng", "do dung", "thiết bị", "thiet bi", "dụng cụ", "dung cu")),
                SubConcept(12, setOf("xe", "xe máy", "xe may", "ô tô", "o to", "oto", "tay ga", "xe số", "xe so", "phương tiện", "phuong tien", "car", "cars", "motor", "motorcycle", "motorbike", "bike", "bicycle", "xe đạp", "xe dap", "mũ bảo hiểm", "mu bao hiem")),
                SubConcept(13, setOf("chó", "cho", "mèo", "meo", "cún", "cun", "mèo con", "meo con", "chó con", "cho con", "thú cưng", "thu cung", "pet", "pets", "puppy", "kitten", "dog", "cat"))
            )
        ),
        // Cluster 3 (42..55): Drinkware, Cups, Mugs, Glasses, Bottles, Containers
        SemanticCluster(
            startIndex = 42,
            dimCount = 14,
            subConcepts = listOf(
                SubConcept(0, setOf("cup", "cups", "mug", "mugs", "cốc", "coc", "cốc sứ", "coc su", "tách", "tach", "tách trà", "tach tra", "coffee cup", "tea cup")),
                SubConcept(1, setOf("glass", "glasses", "ly", "ly nước", "ly nuoc", "ly cà phê", "ly ca phe", "tumbler", "tumblers")),
                SubConcept(2, setOf("bottle", "bottles", "bình", "binh", "chai", "bình nước", "binh nuoc", "thermos")),
                SubConcept(3, setOf("dish", "dishes", "plate", "plates", "bowl", "bowls", "bát", "bat", "chén", "chen", "đĩa", "dia")),
                SubConcept(4, setOf("thìa", "thia", "muỗng", "muong", "đũa", "dua", "nĩa", "nia", "spoon", "fork", "chopsticks")),
                SubConcept(5, setOf("ấm", "am", "ấm chén", "am chen", "ấm trà", "am tra", "teapot", "kettle")),
                SubConcept(6, setOf("drinkware", "uống", "uong", "nước", "nuoc", "beverage", "container"))
            )
        ),
        // Cluster 4 (56..69): Tasks, Action Items, To-Dos, Checklists, Reminders
        SemanticCluster(
            startIndex = 56,
            dimCount = 14,
            subConcepts = listOf(
                SubConcept(0, setOf("todo", "to-do", "checklist", "việc cần làm", "viec can lam", "việc", "viec", "công việc", "cong viec")),
                SubConcept(1, setOf("homework", "assignment", "assignments", "bài tập", "bai tap", "bài vở", "bai vo")),
                SubConcept(2, setOf("project", "projects", "plan", "plans", "dự án", "du an", "kế hoạch", "ke hoach", "mục tiêu", "muc tieu")),
                SubConcept(3, setOf("deadline", "deadlines", "urgent", "priority", "hạn chót", "han chot", "khẩn cấp", "khan cap")),
                SubConcept(4, setOf("reminder", "reminders", "remember", "note", "notes", "nhắc nhở", "nhac nho", "ghi nhớ", "ghi nho", "ghi chú", "ghi chu")),
                SubConcept(5, setOf("finish", "done", "complete", "hoàn thành", "hoan thanh", "xong", "tiến độ", "tien do", "nộp", "nop", "nộp bài", "nop bai", "báo cáo", "bao cao")),
                SubConcept(6, setOf("task", "tasks", "action", "actions", "nhiệm vụ", "nhiem vu"))
            )
        ),
        // Cluster 5 (70..83): Nature, Trees, Forests, Mountains, Landscapes, Outdoors, Scenery
        SemanticCluster(
            startIndex = 70,
            dimCount = 14,
            subConcepts = listOf(
                SubConcept(0, setOf("cây", "cay", "cây cối", "cay coi", "cây xanh", "cay xanh", "cây cảnh", "cay canh", "tán cây", "tan cay", "thân cây", "than cay", "lá", "la", "lá cây", "la cay", "lá vàng", "la vang", "tree", "trees", "plant", "plants", "leaf", "leaves", "foliage", "gỗ", "wood", "woods")),
                SubConcept(1, setOf("rừng", "rung", "rừng cây", "rung cay", "rừng rậm", "rung ram", "forest", "forests", "autumn", "mùa thu", "mua thu", "golden trees", "golden forest")),
                SubConcept(2, setOf("hoa", "bông hoa", "bong hoa", "cỏ", "co", "vườn", "vuon", "thực vật", "thuc vat", "flower", "flowers", "garden", "grass")),
                SubConcept(3, setOf("núi", "nui", "đồi núi", "doi nui", "đỉnh núi", "dinh nui", "dãy núi", "day nui", "đồi", "doi", "mountain", "mountains", "hill", "hills", "peak", "summit")),
                SubConcept(4, setOf("hồ", "ho", "hồ nước", "ho nuoc", "sông", "song", "suối", "suoi", "thác nước", "thac nuoc", "lake", "lakes", "river", "rivers", "stream", "waterfall", "waterfalls")),
                SubConcept(5, setOf("biển", "bien", "bãi biển", "bai bien", "bờ biển", "bo bien", "đại dương", "dai duong", "beach", "beaches", "ocean", "sea")),
                SubConcept(6, setOf("bầu trời", "bau troi", "mây", "may", "hoàng hôn", "hoang hon", "bình minh", "binh minh", "sunset", "sunrise", "sky", "clouds")),
                SubConcept(7, setOf("thung lũng", "thung lung", "valley", "valleys", "landscape", "landscapes", "scenery", "scenic")),
                SubConcept(8, setOf("dã ngoại", "da ngoai", "leo núi", "leo nui", "cắm trại", "cam trai", "trekking", "hiking", "camping", "outdoor", "outdoors", "wilderness")),
                SubConcept(9, setOf("thiên nhiên", "thien nhien", "phong cảnh", "phong canh", "cảnh đẹp", "canh dep", "cảnh quan", "canh quan", "nature"))
            )
        ),
        // Cluster 6 (84..97): Food, Dining, Drinks, Coffee, Restaurants, Cafes, Menus
        SemanticCluster(
            startIndex = 84,
            dimCount = 14,
            subConcepts = listOf(
                SubConcept(0, setOf("cà phê", "ca phe", "cafe", "trà", "tra", "trà sữa", "tra sua", "quán cà phê", "coffee", "tea")),
                SubConcept(1, setOf("phở", "pho", "bún", "bun", "cơm", "com", "lẩu", "lau", "nướng", "nuong", "bánh", "banh")),
                SubConcept(2, setOf("breakfast", "lunch", "dinner", "meal", "bữa sáng", "bua sang", "bữa trưa", "bua trua", "bữa tối", "bua toi")),
                SubConcept(3, setOf("restaurant", "restaurants", "quán", "quan", "quán ăn", "quan an", "nhà hàng", "nha hang", "tiệm ăn", "tiem an")),
                SubConcept(4, setOf("menu", "menus", "thực đơn", "thuc don", "món ăn", "mon an", "món", "mon", "thức ăn", "thuc an", "food", "dining")),
                SubConcept(5, setOf("drink", "drinks", "đồ uống", "do uong", "uống", "uong", "nước ngọt", "nuoc ngot", "bia", "beer")),
                SubConcept(6, setOf("address", "map", "street", "location", "directions", "địa chỉ", "dia chi", "địa điểm", "dia diem", "đường", "duong")),
                SubConcept(7, setOf("hotel", "hotels", "travel", "khách sạn", "khach san", "resort"))
            )
        ),
        // Cluster 7 (98..111): Reading, Articles, Books, Documents, IDs, Personal & People
        SemanticCluster(
            startIndex = 98,
            dimCount = 14,
            subConcepts = listOf(
                SubConcept(0, setOf("chân dung", "chan dung", "ảnh chân dung", "anh chan dung", "selfie", "selfies", "tự sướng", "tu suong", "ảnh tự sướng", "anh tu suong", "khuôn mặt", "khuon mat", "mặt", "mat", "portrait", "portraits", "headshot", "avatar", "ảnh đại diện", "anh dai dien")),
                SubConcept(1, setOf("người", "nguoi", "con người", "con nguoi", "phụ nữ", "phu nu", "đàn ông", "dan ong", "con gái", "con gai", "con trai", "con trai", "gia đình", "gia dinh", "bạn bè", "ban be", "person", "people", "family")),
                SubConcept(2, setOf("cccd", "cmnd", "căn cước", "can cuoc", "căn cước công dân", "can cuoc cong dan", "chứng minh nhân dân", "chung minh nhan dan", "hộ chiếu", "ho chieu", "passport", "passports", "bằng lái", "bang lai", "bằng lái xe", "bang lai xe", "id", "identity", "card", "identity card")),
                SubConcept(3, setOf("cá nhân", "ca nhan", "hồ sơ", "ho so", "trang cá nhân", "profile", "resume", "cv")),
                SubConcept(4, setOf("sách", "sach", "book", "books", "đọc", "doc", "reading", "chapter", "chương", "chuong", "author", "tác giả", "tac gia")),
                SubConcept(5, setOf("bài viết", "bai viet", "bài báo", "bai bao", "tin tức", "tin tuc", "báo", "bao", "article", "articles", "blog", "news")),
                SubConcept(6, setOf("tài liệu", "tai lieu", "văn bản", "van ban", "kiến thức", "kien thuc", "contract", "hợp đồng", "hop dong", "document", "documents")),
                SubConcept(7, setOf("code", "github", "git", "error", "bug", "lỗi", "loi", "stacktrace")),
                SubConcept(8, setOf("họ tên", "ho ten", "ngày sinh", "ngay sinh", "quê quán", "que quan")),
                SubConcept(9, setOf("thuốc", "thuoc", "đơn thuốc", "don thuoc", "bác sĩ", "bac si", "paracetamol", "panadol", "hạ sốt", "ha sot", "khám bệnh", "kham benh", "bệnh viện", "benh vien", "y tế", "y te", "dược phẩm", "duoc pham", "toa thuốc", "toa thuoc", "viên uống", "vien uong", "khẩu trang", "khau trang", "medicine", "doctor", "hospital", "pharmacy")),
                SubConcept(10, setOf("học", "hoc", "trường", "truong", "lớp", "lop", "điểm", "diem", "thi", "đại học", "dai hoc", "sinh viên", "sinh vien", "học sinh", "hoc sinh", "bài giảng", "bai giang", "giáo trình", "giao trinh", "study", "exam", "university", "school", "lecture", "student"))
            )
        )
    )

    /**
     * Generates a 128-dimensional dense semantic embedding vector for text content.
     * Dimensions 0..111: 8 Concept Clusters (14 dims each)
     * Dimensions 112..119: Dynamic Custom AI Categories Projection Slots (Pro Tier)
     * Dimensions 120..127: Lexical Token Hashing (secondary signature)
     * The resulting vector is normalized (L2 norm = 1.0).
     */
    fun generateEmbedding(
        text: String,
        title: String = "",
        intent: String = "",
        categoryNames: List<String> = emptyList(),
        customCategories: List<Category> = emptyList()
    ): FloatArray {
        val vector = FloatArray(VECTOR_DIMENSION)
        val combinedText = buildString {
            if (title.isNotBlank()) append(title).append(" ").append(title).append(" ") // double weight on title
            if (intent.isNotBlank()) append(intent).append(" ")
            for (cat in categoryNames) append(cat).append(" ")
            append(text)
        }.lowercase(Locale.ROOT)

        val unaccentedCombinedText = VietnameseUtils.removeDiacritics(combinedText)
        val tokens = tokenize(combinedText)
        val unaccentedTokens = tokens.map { VietnameseUtils.removeDiacritics(it) }.toSet()
        val tokenSet = tokens.toSet()

        if (tokens.isEmpty()) {
            return vector
        }

        // 1. Concept Cluster activations (Dimensions 0..111)
        for (cluster in CONCEPT_CLUSTERS) {
            var totalClusterScore = 0f

            for (sub in cluster.subConcepts) {
                var subScore = 0f
                for (kw in sub.keywords) {
                    if (kw.contains(" ")) {
                        if (combinedText.contains(kw) || unaccentedCombinedText.contains(kw)) {
                            subScore += 3.0f
                        }
                    } else {
                        if (tokenSet.contains(kw) || unaccentedTokens.contains(kw)) {
                            subScore += 1.8f
                        } else {
                            // Check prefix match for compound roots
                            if (kw.length >= 3 && tokens.any { it.startsWith(kw) || kw.startsWith(it) }) {
                                subScore += 0.8f
                            }
                        }
                    }
                }
                if (subScore > 0f) {
                    totalClusterScore += subScore
                    vector[cluster.startIndex + sub.subDimOffset] += subScore * 2.2f
                }
            }

            if (totalClusterScore > 0f) {
                // Shared base floor across all dimensions in this cluster (broad conceptual affinity)
                val baseFloor = totalClusterScore * 0.30f
                for (i in 0 until cluster.dimCount) {
                    vector[cluster.startIndex + i] += baseFloor
                }
            }
        }

        // 2. Intent-specific and category-specific boost
        val lowerIntent = intent.lowercase(Locale.ROOT)
        val lowerCats = categoryNames.joinToString(" ").lowercase(Locale.ROOT)
        val unaccentedCats = VietnameseUtils.removeDiacritics(lowerCats)

        if (lowerIntent == "event" || lowerCats.contains("event") || unaccentedCats.contains("lich")) {
            boostRange(vector, 0, 14, 2.0f)
        }
        if (lowerIntent == "shopping" || lowerCats.contains("buy") || unaccentedCats.contains("mua")) {
            boostRange(vector, 14, 28, 2.0f) // Shopping
            boostRange(vector, 28, 42, 2.0f) // Products
        }
        if (lowerCats.contains("nature") || unaccentedCats.contains("nui") || unaccentedCats.contains("thien nhien") || unaccentedCats.contains("cay") || unaccentedCats.contains("phong canh")) {
            boostRange(vector, 70, 84, 2.5f) // Nature & Mountain & Trees
        }
        if (lowerIntent == "task" || lowerCats.contains("task") || unaccentedCats.contains("viec")) {
            boostRange(vector, 56, 70, 2.0f) // Tasks
        }
        if (lowerIntent == "reading" || lowerCats.contains("reading") || unaccentedCats.contains("doc")) {
            boostRange(vector, 98, 112, 2.0f) // Reading
        }
        if (lowerCats.contains("personal") || unaccentedCats.contains("ca nhan") || lowerCats.contains("nguoi") || unaccentedCats.contains("chan dung")) {
            boostRange(vector, 98, 112, 2.0f) // Personal & Documents
        }
        if (lowerIntent == "food" || lowerCats.contains("food") || lowerCats.contains("dining") || unaccentedCats.contains("an uong") || unaccentedCombinedText.contains("quan an") || unaccentedCombinedText.contains("nha hang")) {
            boostRange(vector, 84, 98, 2.0f) // Food & Dining
        }

        // 3. Dynamic Custom AI Categories Projection (Dimensions 112..119) - Pro Tier
        if (customCategories.isNotEmpty()) {
            for (cat in customCategories) {
                val slot = 112 + (Math.abs(cat.name.lowercase(Locale.ROOT).hashCode()) % 8)
                val catNameLower = cat.name.lowercase(Locale.ROOT)
                val unaccentedCatName = VietnameseUtils.removeDiacritics(catNameLower)
                val isMatched = combinedText.contains(catNameLower) ||
                        unaccentedCombinedText.contains(unaccentedCatName) ||
                        categoryNames.any { it.equals(cat.name, ignoreCase = true) || it.equals(cat.id, ignoreCase = true) }
                if (isMatched) {
                    vector[slot] += 3.5f
                }
            }
        }

        // 4. Term frequency hashing into general dimensions (120..127) as secondary lexical signature
        for (token in tokens) {
            val unaccentedToken = VietnameseUtils.removeDiacritics(token)
            val hash = (unaccentedToken.hashCode() and 0x7fffffff) % 8
            val dim = 120 + hash
            vector[dim] += 0.10f
        }

        // 5. L2 Normalization
        normalizeVector(vector)
        return vector
    }

    private fun boostRange(vector: FloatArray, start: Int, endExclusive: Int, multiplier: Float) {
        for (i in start until endExclusive) {
            if (vector[i] > 0f) {
                vector[i] = vector[i] * multiplier
            }
        }
    }

    private fun normalizeVector(vector: FloatArray) {
        var sumSquares = 0f
        for (v in vector) {
            sumSquares += v * v
        }
        val norm = sqrt(sumSquares)
        if (norm > 0.00001f) {
            for (i in vector.indices) {
                vector[i] = vector[i] / norm
            }
        }
    }

    /**
     * Calculates cosine similarity between two unit-normalized vectors.
     * Returns a float between 0.0 and 1.0.
     */
    fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        if (v1.size != v2.size || v1.isEmpty()) return 0f
        var dot = 0f
        for (i in v1.indices) {
            dot += v1[i] * v2[i]
        }
        return dot.coerceIn(0f, 1f)
    }

    /**
     * Searches indexed photo vectors against a query string.
     * Returns a list of (photoId to similarityScore) ranked by similarity.
     */
    fun search(
        query: String,
        indexedVectors: Map<String, FloatArray>,
        customCategories: List<Category> = emptyList(),
        minSimilarity: Float = 0.28f
    ): List<Pair<String, Float>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || indexedVectors.isEmpty()) return emptyList()

        val queryVector = generateEmbedding(
            text = trimmed,
            title = trimmed,
            customCategories = customCategories
        )
        val results = mutableListOf<Pair<String, Float>>()

        for ((photoId, photoVector) in indexedVectors) {
            val sim = cosineSimilarity(queryVector, photoVector)
            if (sim >= minSimilarity) {
                results.add(photoId to sim)
            }
        }

        return results.sortedByDescending { it.second }
    }

    /**
     * Searches indexed vectors against an arbitrary precomputed query vector (e.g., from Cloud Embedding API).
     */
    fun searchWithQueryVector(
        queryVector: FloatArray,
        indexedVectors: Map<String, FloatArray>,
        minSimilarity: Float = 0.45f
    ): List<Pair<String, Float>> {
        if (queryVector.isEmpty() || indexedVectors.isEmpty()) return emptyList()
        val results = mutableListOf<Pair<String, Float>>()
        for ((photoId, photoVector) in indexedVectors) {
            val sim = cosineSimilarity(queryVector, photoVector)
            if (sim >= minSimilarity) {
                results.add(photoId to sim)
            }
        }
        return results.sortedByDescending { it.second }
    }

    private val CATEGORY_PROTOTYPES = mapOf(
        "cat_events" to "lịch trình thời gian sự kiện cuộc họp lịch hẹn chuyến bay vé máy bay lịch làm việc lịch khám ca trực thứ hai thứ ba thứ tư thứ năm thứ sáu thứ bảy chủ nhật sáng chiều tối giờ phút ngày tháng năm calendar schedule meeting appointment event flight timetable",
        "cat_tobuy" to "mua sắm chi tiêu giá tiền sản phẩm đơn hàng hóa đơn thanh toán giỏ hàng mua ngay đặt hàng shopee lazada tiki chuyển khoản ưu đãi voucher freeship price cart order receipt discount purchase",
        "cat_reading" to "đọc bài viết tin tức bài báo phóng sự tạp chí tác giả tòa soạn tin mới báo chí điểm tin thời sự giao thông thời tiết triều cường article news editorial reading author newsletter",
        "cat_nature" to "thiên nhiên phong cảnh núi non đồi núi rừng rậm biển bãi biển hoàng hôn bình minh thác nước hồ nước phong cảnh đẹp scenic landscape nature mountain forest wallpaper ocean lake river",
        "cat_personal" to "cá nhân giấy tờ tùy thân căn cước công dân chứng minh nhân dân hộ chiếu bằng lái xe cccd cmnd gplx ảnh chân dung tự sướng profile identity card selfie portrait passport"
    )

    private val categoryVectors: Map<String, FloatArray> by lazy {
        CATEGORY_PROTOTYPES.mapValues { (catId, text) ->
            generateEmbedding(
                text = text,
                title = text,
                intent = when (catId) {
                    "cat_events" -> "EVENT"
                    "cat_tobuy" -> "SHOPPING"
                    "cat_reading" -> "READING"
                    else -> "OTHER"
                },
                categoryNames = listOf(catId)
            )
        }
    }

    /**
     * Holistic on-device classification: Computes the 128D semantic embedding of the screenshot
     * and evaluates cosine similarity against category prototypes.
     * Returns top category ID and similarity score if confidence threshold is met.
     */
    fun classifyByCategoryVector(text: String, title: String): Pair<String, Float>? {
        if (text.isBlank() && title.isBlank()) return null
        val docVector = generateEmbedding(text = text, title = title)
        var bestCat: String? = null
        var bestSim = -1f
        var secondBestSim = -1f

        for ((catId, protoVector) in categoryVectors) {
            val sim = cosineSimilarity(docVector, protoVector)
            if (sim > bestSim) {
                secondBestSim = bestSim
                bestSim = sim
                bestCat = catId
            } else if (sim > secondBestSim) {
                secondBestSim = sim
            }
        }

        val chosenCat = bestCat ?: return null
        return if (bestSim >= 0.35f && (bestSim - secondBestSim) >= 0.05f) {
            chosenCat to bestSim
        } else if (bestSim >= 0.48f) {
            chosenCat to bestSim
        } else {
            null
        }
    }

    private fun tokenize(text: String): List<String> {
        val rawTokens = text
            .replace(Regex("[^a-zA-Z0-9àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() && it.length > 1 }

        val allTokens = mutableListOf<String>()
        for (t in rawTokens) {
            allTokens.add(t)
            val unaccented = VietnameseUtils.removeDiacritics(t)
            if (unaccented != t) {
                allTokens.add(unaccented)
            }
        }
        return allTokens
    }

    // --- Byte Serialization for SQLite BLOB storage ---

    fun vectorToBlob(vector: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(vector.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (v in vector) {
            buffer.putFloat(v)
        }
        return buffer.array()
    }

    fun blobToVector(blob: ByteArray): FloatArray {
        val count = blob.size / 4
        val vector = FloatArray(count)
        val buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) {
            vector[i] = buffer.float
        }
        return vector
    }
}

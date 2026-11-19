package com.example.test2.ai.cards

import com.example.test2.shared.utils.VietnameseUtils
import java.util.Locale

object AdminUnits {

    // Preserved for legacy contract compatibility (exact order as tested)
    val LEGACY_VN_CITIES = listOf(
        "Hà Nội", "Hồ Chí Minh", "TP.HCM", "TP HCM", "Sài Gòn", "Đà Nẵng", "Hải Phòng",
        "Cần Thơ", "Bình Dương", "Đồng Nai", "Nha Trang", "Đà Lạt", "Huế", "Vũng Tàu",
        "Bắc Ninh", "Quảng Ninh", "Hải Dương", "Nam Định", "Thanh Hóa", "Nghệ An",
        "Lâm Đồng", "Khánh Hòa", "Bình Thuận", "Kiên Giang", "An Giang", "Cà Mau"
    )

    val KNOWN_DISTRICTS = listOf(
        // Hà Nội
        "Ba Đình", "Hoàn Kiếm", "Tây Hồ", "Long Biên", "Cầu Giấy", "Đống Đa", "Hai Bà Trưng", "Hoàng Mai",
        "Thanh Xuân", "Sóc Sơn", "Đông Anh", "Gia Lâm", "Nam Từ Liêm", "Bắc Từ Liêm", "Thanh Trì", "Hà Đông",
        "Sơn Tây", "Ba Vì", "Phúc Thọ", "Đan Phượng", "Hoài Đức", "Quốc Oai", "Thạch Thất", "Chương Mỹ",
        "Thanh Oai", "Thường Tín", "Phú Xuyên", "Ứng Hòa", "Mỹ Đức", "Mê Linh",
        // TP.HCM
        "Quận 1", "Quận 3", "Quận 4", "Quận 5", "Quận 6", "Quận 7", "Quận 8", "Quận 10", "Quận 11", "Quận 12",
        "Q1", "Q.1", "Q3", "Q.3", "Q4", "Q.4", "Q5", "Q.5", "Q6", "Q.6", "Q7", "Q.7", "Q8", "Q.8", "Q10", "Q.10", "Q11", "Q.11", "Q12", "Q.12",
        "Bình Thạnh", "Gò Vấp", "Phú Nhuận", "Tân Bình", "Tân Phú", "Bình Tân", "Thủ Đức",
        "Củ Chi", "Hóc Môn", "Bình Chánh", "Nhà Bè", "Cần Giờ",
        // Đà Nẵng
        "Hải Châu", "Thanh Khê", "Sơn Trà", "Ngũ Hành Sơn", "Liên Chiểu", "Cẩm Lệ", "Hòa Vang",
        // Hải Phòng, Cần Thơ, Bình Dương, Đồng Nai
        "Hồng Bàng", "Ngô Quyền", "Lê Chân", "Ninh Kiều", "Bình Thủy", "Cái Răng",
        "Thủ Dầu Một", "Dĩ An", "Thuận An", "Bến Cát", "Tân Uyên", "Biên Hòa", "Long Thành"
    )

    // 34 New Administrative Units & common aliases
    data class ProvinceEntry(
        val officialName: String,
        val aliases: List<String>
    )

    val PROVINCES = listOf(
        ProvinceEntry("Hà Nội", listOf("Hà Nội", "Ha Noi", "HN", "Thủ đô Hà Nội", "TP Hà Nội", "TP. Hà Nội")),
        ProvinceEntry("TP. Hồ Chí Minh", listOf("TP. Hồ Chí Minh", "TP.HCM", "TP HCM", "TPHCM", "Hồ Chí Minh", "Ho Chi Minh", "Sài Gòn", "Sai Gon", "SG", "Bình Dương", "Binh Duong", "Bà Rịa", "Vũng Tàu", "Vung Tau", "Bà Rịa - Vũng Tàu")),
        ProvinceEntry("TP. Đà Nẵng", listOf("TP. Đà Nẵng", "Đà Nẵng", "Da Nang", "ĐN", "Quảng Nam", "Quang Nam", "Tam Kỳ")),
        ProvinceEntry("TP. Hải Phòng", listOf("TP. Hải Phòng", "Hải Phòng", "Hai Phong", "HP", "Hải Dương", "Hai Duong")),
        ProvinceEntry("TP. Cần Thơ", listOf("TP. Cần Thơ", "Cần Thơ", "Can Tho", "Sóc Trăng", "Soc Trang", "Hậu Giang", "Hau Giang")),
        ProvinceEntry("Huế", listOf("Huế", "Thừa Thiên Huế", "Thua Thien Hue", "TP. Huế", "TP Huế")),
        ProvinceEntry("Lai Châu", listOf("Lai Châu", "Lai Chau")),
        ProvinceEntry("Điện Biên", listOf("Điện Biên", "Dien Bien")),
        ProvinceEntry("Sơn La", listOf("Sơn La", "Son La")),
        ProvinceEntry("Lạng Sơn", listOf("Lạng Sơn", "Lang Son")),
        ProvinceEntry("Quảng Ninh", listOf("Quảng Ninh", "Quang Ninh", "Hạ Long")),
        ProvinceEntry("Thanh Hóa", listOf("Thanh Hóa", "Thanh Hoa")),
        ProvinceEntry("Nghệ An", listOf("Nghệ An", "Nghe An", "Vinh")),
        ProvinceEntry("Hà Tĩnh", listOf("Hà Tĩnh", "Ha Tinh")),
        ProvinceEntry("Cao Bằng", listOf("Cao Bằng", "Cao Bang")),
        ProvinceEntry("Tuyên Quang", listOf("Tuyên Quang", "Tuyen Quang", "Hà Giang", "Ha Giang")),
        ProvinceEntry("Lào Cai", listOf("Lào Cai", "Lao Cai", "Yên Bái", "Yen Bai", "Sa Pa")),
        ProvinceEntry("Thái Nguyên", listOf("Thái Nguyên", "Thai Nguyen", "Bắc Kạn", "Bac Kan")),
        ProvinceEntry("Phú Thọ", listOf("Phú Thọ", "Phu Tho", "Vĩnh Phúc", "Vinh Phuc", "Hòa Bình", "Hoa Binh")),
        ProvinceEntry("Bắc Ninh", listOf("Bắc Ninh", "Bac Ninh", "Bắc Giang", "Bac Giang")),
        ProvinceEntry("Hưng Yên", listOf("Hưng Yên", "Hung Yen", "Thái Bình", "Thai Binh")),
        ProvinceEntry("Ninh Bình", listOf("Ninh Bình", "Ninh Binh", "Hà Nam", "Ha Nam", "Nam Định", "Nam Dinh")),
        ProvinceEntry("Quảng Trị", listOf("Quảng Trị", "Quang Tri", "Quảng Bình", "Quang Binh", "Đồng Hới")),
        ProvinceEntry("Quảng Ngãi", listOf("Quảng Ngãi", "Quang Ngai", "Kon Tum")),
        ProvinceEntry("Gia Lai", listOf("Gia Lai", "Bình Định", "Binh Dinh", "Quy Nhơn", "Pleiku")),
        ProvinceEntry("Khánh Hòa", listOf("Khánh Hòa", "Khanh Hoa", "Nha Trang", "Ninh Thuận", "Ninh Thuan", "Phan Rang")),
        ProvinceEntry("Lâm Đồng", listOf("Lâm Đồng", "Lam Dong", "Đà Lạt", "Da Lat", "Đắk Nông", "Dak Nong", "Bình Thuận", "Binh Thuan", "Phan Thiết")),
        ProvinceEntry("Đắk Lắk", listOf("Đắk Lắk", "Dak Lak", "Đắc Lắc", "Buôn Ma Thuột", "Phú Yên", "Phu Yen", "Tuy Hòa")),
        ProvinceEntry("Đồng Nai", listOf("Đồng Nai", "Dong Nai", "Biên Hòa", "Bình Phước", "Binh Phuoc")),
        ProvinceEntry("Tây Ninh", listOf("Tây Ninh", "Tay Ninh", "Long An", "Tân An")),
        ProvinceEntry("Vĩnh Long", listOf("Vĩnh Long", "Vinh Long", "Bến Tre", "Ben Tre", "Trà Vinh", "Tra Vinh")),
        ProvinceEntry("Đồng Tháp", listOf("Đồng Tháp", "Dong Thap", "Cao Lãnh", "Tiền Giang", "Tien Giang", "Mỹ Tho")),
        ProvinceEntry("Cà Mau", listOf("Cà Mau", "Ca Mau", "Bạc Liêu", "Bac Lieu")),
        ProvinceEntry("An Giang", listOf("An Giang", "Long Xuyên", "Kiên Giang", "Kien Giang", "Rạch Giá", "Phú Quốc"))
    )

    /**
     * Resolves city name preserving legacy order first, then checking new province aliases.
     */
    fun detectCity(address: String): String {
        val lower = address.lowercase(Locale.getDefault())
        // Ignore "Hà Nội" when it is part of the street name "Xa Lộ Hà Nội" in southern VN
        val lowerWithoutStreet = lower.replace("xa lộ hà nội", "").replace("xa lo ha noi", "")

        // 1. Try legacy list first (guarantees backward compatibility for existing tests like "TP.HCM", "Hà Nội")
        for (city in LEGACY_VN_CITIES) {
            val cLower = city.lowercase(Locale.getDefault())
            if (lowerWithoutStreet.contains(cLower)) {
                return city
            }
        }

        // 2. Try aliases in the 34 provinces list
        val unacc = VietnameseUtils.removeDiacritics(lowerWithoutStreet)
        for (prov in PROVINCES) {
            for (alias in prov.aliases) {
                val aLower = alias.lowercase(Locale.getDefault())
                val aUnacc = VietnameseUtils.removeDiacritics(aLower)
                if (Regex("""(?i)(?:^|[\s,;./\-]|\b)${Regex.escape(aLower)}(?:$|[\s,;./\-]|\b)""").containsMatchIn(lowerWithoutStreet) ||
                    Regex("""(?i)(?:^|[\s,;./\-]|\b)${Regex.escape(aUnacc)}(?:$|[\s,;./\-]|\b)""").containsMatchIn(unacc)) {
                    return prov.officialName
                }
            }
        }
        return ""
    }

    /**
     * Checks if line contains any administrative anchor.
     */
    fun hasAdminAnchor(line: String): Boolean {
        val lower = line.lowercase(Locale.getDefault())
        val unacc = VietnameseUtils.removeDiacritics(lower)

        // Administrative keywords: Phường, Xã, Thị trấn, Quận, Huyện, Thị xã, Tỉnh, TP, P., Q.
        if (Regex("""(?i)(?:^|[\s,;./\-]|\b)(?:phường|xã|thị\s*trấn|quận|huyện|thị\s*xã|tỉnh|thành\s*phố|q\s*\d+|(?:p|q|tp)\.|\b(?:tp|q\s*\d+|p\s*\d+)\b)(?:$|[\s,;./\-]|\b)""").containsMatchIn(line)) {
            return true
        }

        // Known districts
        if (KNOWN_DISTRICTS.any { lower.contains(it.lowercase(Locale.getDefault())) }) {
            return true
        }

        // Provinces and aliases
        for (prov in PROVINCES) {
            for (alias in prov.aliases) {
                val aLower = alias.lowercase(Locale.getDefault())
                val aUnacc = VietnameseUtils.removeDiacritics(aLower)
                if (Regex("""(?i)(?:^|[\s,;./\-]|\b)${Regex.escape(aLower)}(?:$|[\s,;./\-]|\b)""").containsMatchIn(lower) ||
                    Regex("""(?i)(?:^|[\s,;./\-]|\b)${Regex.escape(aUnacc)}(?:$|[\s,;./\-]|\b)""").containsMatchIn(unacc)) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Determines admin system classification: legacy, current, or unknown.
     */
    fun detectAdminSystem(address: String): String {
        val lower = address.lowercase(Locale.getDefault())
        val hasDistrict = Regex("""(?i)\b(?:quận|huyện|thị\s*xã|q\.|q\s*\d+)\b""").containsMatchIn(lower) ||
                KNOWN_DISTRICTS.any { lower.contains(it.lowercase(Locale.getDefault())) }

        val hasWard = Regex("""(?i)\b(?:phường|xã|thị\s*trấn|p\.)\b""").containsMatchIn(lower)
        val hasProvince = detectCity(address).isNotBlank()

        return when {
            hasDistrict -> "legacy"
            hasWard && hasProvince -> "current"
            else -> "unknown"
        }
    }
}

package com.example.test2.shared.utils

/**
 * Utility functions for Vietnamese text processing and diacritics stripping.
 * Ensures consistent search matching between accented (e.g., "núi", "cốc", "sản phẩm")
 * and unaccented (e.g., "nui", "coc", "san pham") terms.
 */
object VietnameseUtils {

    /**
     * Strips all Vietnamese tonal diacritics and replaces 'đ'/'Đ' with 'd'/'D'.
     * Fast, lightweight, pure Kotlin implementation with no external JVM dependencies.
     */
    fun removeDiacritics(input: String): String {
        if (input.isEmpty()) return input
        val sb = StringBuilder(input.length)
        for (ch in input) {
            val replacement = when (ch) {
                'à', 'á', 'ạ', 'ả', 'ã', 'â', 'ầ', 'ấ', 'ậ', 'ẩ', 'ẫ', 'ă', 'ằ', 'ắ', 'ặ', 'ẳ', 'ẵ' -> 'a'
                'À', 'Á', 'Ạ', 'Ả', 'Ã', 'Â', 'Ầ', 'Ấ', 'Ậ', 'Ẩ', 'Ẫ', 'Ă', 'Ằ', 'Ắ', 'Ặ', 'Ẳ', 'Ẵ' -> 'A'
                'è', 'é', 'ẹ', 'ẻ', 'ẽ', 'ê', 'ề', 'ế', 'ệ', 'ể', 'ễ' -> 'e'
                'È', 'É', 'Ẹ', 'Ẻ', 'Ẽ', 'Ê', 'Ề', 'Ế', 'Ệ', 'Ể', 'Ễ' -> 'E'
                'ì', 'í', 'ị', 'ỉ', 'ĩ' -> 'i'
                'Ì', 'Í', 'Ị', 'Ỉ', 'Ĩ' -> 'I'
                'ò', 'ó', 'ọ', 'ỏ', 'õ', 'ô', 'ồ', 'ố', 'ộ', 'ổ', 'ỗ', 'ơ', 'ờ', 'ớ', 'ợ', 'ở', 'ỡ' -> 'o'
                'Ò', 'Ó', 'Ọ', 'Ỏ', 'Õ', 'Ô', 'Ồ', 'Ố', 'Ộ', 'Ổ', 'Ỗ', 'Ơ', 'Ờ', 'Ớ', 'Ợ', 'Ở', 'Ỡ' -> 'O'
                'ù', 'ú', 'ụ', 'ủ', 'ũ', 'ư', 'ừ', 'ứ', 'ự', 'ử', 'ữ' -> 'u'
                'Ù', 'Ú', 'Ụ', 'Ủ', 'Ũ', 'Ư', 'Ừ', 'Ứ', 'Ự', 'Ử', 'Ữ' -> 'U'
                'ỳ', 'ý', 'ỵ', 'ỷ', 'ỹ' -> 'y'
                'Ỳ', 'Ý', 'Ỵ', 'Ỷ', 'Ỹ' -> 'Y'
                'đ' -> 'd'
                'Đ' -> 'D'
                else -> ch
            }
            sb.append(replacement)
        }
        return sb.toString()
    }
}

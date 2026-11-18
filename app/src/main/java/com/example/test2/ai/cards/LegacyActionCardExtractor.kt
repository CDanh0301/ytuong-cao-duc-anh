package com.example.test2.ai.cards

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.test2.ai.SmartHeuristicParser
import com.example.test2.ai.VietQrParser
import com.example.test2.shared.models.ActionCard
import com.example.test2.shared.models.ActionCardType
import com.example.test2.shared.utils.VietnameseUtils
import com.example.test2.storage.LocalCryptoManager
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

object LegacyActionCardExtractor {

    private val barcodeScanner by lazy {
        BarcodeScanning.getClient()
    }

    // Curated bank patterns with strict word boundaries to avoid false positives
    private val KNOWN_BANKS_MAP = mapOf(
        "Vietcombank" to Regex("""(?i)\b(?:Vietcombank|VCB)\b"""),
        "Techcombank" to Regex("""(?i)\b(?:Techcombank|TCB)\b"""),
        "MB Bank" to Regex("""(?i)\b(?:MB\s*Bank|MBBank|Ngân\s*hàng\s*MB)\b"""),
        "BIDV" to Regex("""(?i)\bBIDV\b"""),
        "VietinBank" to Regex("""(?i)\b(?:VietinBank|CTG)\b"""),
        "VPBank" to Regex("""(?i)\b(?:VPBank|VPB)\b"""),
        "ACB" to Regex("""(?i)\bACB\b"""),
        "TPBank" to Regex("""(?i)\b(?:TPBank|TPB)\b"""),
        "Sacombank" to Regex("""(?i)\b(?:Sacombank|STB)\b"""),
        "VIB" to Regex("""(?i)\bVIB\b"""),
        "HDBank" to Regex("""(?i)\b(?:HDBank|HDB)\b"""),
        "OCB" to Regex("""(?i)\bOCB\b"""),
        "SHB" to Regex("""(?i)\bSHB\b"""),
        "SeABank" to Regex("""(?i)\bSeABank\b"""),
        "MSB" to Regex("""(?i)\b(?:MSB|Ngân\s*hàng\s*MSB|Hàng\s*Hải)\b"""),
        "LPBank" to Regex("""(?i)\b(?:LPBank|LienVietPostBank)\b"""),
        "Nam A Bank" to Regex("""(?i)\bNam\s*A\s*Bank\b"""),
        "Eximbank" to Regex("""(?i)\b(?:Eximbank|EIB)\b"""),
        "PVcomBank" to Regex("""(?i)\bPVcomBank\b"""),
        "Agribank" to Regex("""(?i)\bAgribank\b"""),
        "SaigonBank" to Regex("""(?i)\bSaigonBank\b"""),
        "DongABank" to Regex("""(?i)\bDongABank\b"""),
        "BaoVietBank" to Regex("""(?i)\bBaoVietBank\b"""),
        "Kienlongbank" to Regex("""(?i)\bKienlongbank\b"""),
        "BVBank" to Regex("""(?i)\bBVBank\b"""),
        "VietABank" to Regex("""(?i)\bVietABank\b"""),
        "VietBank" to Regex("""(?i)\bVietBank\b"""),
        "NCB" to Regex("""(?i)\bNCB\b"""),
        "ABBANK" to Regex("""(?i)\bABBANK\b"""),
        "GPBank" to Regex("""(?i)\bGPBank\b"""),
        "Shinhan Bank" to Regex("""(?i)\bShinhan\s*Bank\b"""),
        "Cake by VPBank" to Regex("""(?i)\b(?:Cake\s*by\s*VPBank|Ngân\s*hàng\s*Cake)\b"""),
        "Timo" to Regex("""(?i)\b(?:Timo|Timo\s*Bank)\b"""),
        "ViettelPay" to Regex("""(?i)\b(?:ViettelPay|Viettel\s*Money)\b"""),
        "VNPT Money" to Regex("""(?i)\bVNPT\s*Money\b"""),
        "MoMo" to Regex("""(?i)\b(?:MoMo|Ví\s*MoMo)\b"""),
        "ZaloPay" to Regex("""(?i)\b(?:ZaloPay|Ví\s*ZaloPay)\b""")
    )

    private val VN_CITIES = listOf(
        "Hà Nội", "Hồ Chí Minh", "TP.HCM", "TP HCM", "Sài Gòn", "Đà Nẵng", "Hải Phòng",
        "Cần Thơ", "Bình Dương", "Đồng Nai", "Nha Trang", "Đà Lạt", "Huế", "Vũng Tàu",
        "Bắc Ninh", "Quảng Ninh", "Hải Dương", "Nam Định", "Thanh Hóa", "Nghệ An",
        "Lâm Đồng", "Khánh Hòa", "Bình Thuận", "Kiên Giang", "An Giang", "Cà Mau"
    )

    private val KNOWN_DISTRICTS = listOf(
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

    // Negative words list (words that frequently appear in non-address context)
    private val NEGATIVE_WORDS = listOf(
        "quan trọng", "quan trong", "liên quan", "lien quan", "tổng quan", "tong quan",
        "quản lý", "quan ly", "tham quan", "kết quả", "ket qua", "cơ quan", "co quan",
        "dinh dưỡng", "dinh duong", "đường huyết", "duong huyet", "bồi dưỡng", "boi duong",
        "ngoài ra", "ngoai ra", "ngỡ ngàng", "ngo ngang", "nghi ngờ", "nghi ngo", "ngon miệng", "ngon mieng",
        "tuyển dụng", "tuyen dung", "mức lương", "muc luong", "thu nhập", "thu nhap",
        "thời tiết", "thoi tiet", "dự báo", "du bao", "tin tức", "tin tuc",
        "kẹt xe", "ket xe", "tắc đường", "tac duong", "nhiệt độ", "nhiet do"
    )

    private val ADDR_START_CUES = listOf(
        Regex("""(?i)\b(?:địa\s*chỉ|dia\s*chi|address|location|cơ\s*sở|chi\s*nhánh|store|shop|quán|tiệm|nhà\s*hàng)\b"""),
        // Standalone house numbers at line start: e.g. "24 Doãn Kế Thiện", "72 Lê Thánh Tôn", "18 Tam Trinh"
        Regex("""^\s*(\d+[a-zA-Z]?)\s+([A-ZÀ-Ỹa-zà-ỹ0-9]+)"""),
        // Slash/alley house numbers: e.g. "124/5A Nguyễn Trãi", "18/2/3 Lê Văn Sỹ"
        Regex("""\b(\d+(?:/\d+[a-zA-Z]?)+)\s+([A-ZÀ-Ỹa-zà-ỹ0-9]+)"""),
        // Explicit number prefixes: "Số 24", "So 24"
        Regex("""(?i)\b(?:số|so)\s*\d+"""),
        // Alleys: "ngõ 105", "ngách 2", "hẻm 50", "kiệt 12"
        Regex("""(?i)\b(?:ngõ|ngách|hẻm|kiệt)\s*\d+"""),
        // Explicit street prefixes: "đường Doãn Kế Thiện", "phố Huế"
        Regex("""(?i)\b(?:đường|phố|duong|pho)\s+[A-ZÀ-Ỹ0-9]"""),
        // Buildings/Residences: "Tòa Keangnam", "Chung cư Royal City", "KĐT Vinhomes"
        Regex("""(?i)\b(?:toà\s*nhà|tòa\s*nhà|tầng\s*\d+|kđt|chung\s*cư|residence|tower|plaza|building)\s+[A-Z0-9À-Ỹ]""")
    )

    private val ADDR_ADMIN_CUES = listOf(
        Regex("""(?i)\b(?:phường|xã|thị\s*trấn|p\.)\s+[A-ZÀ-Ỹ0-9]"""),
        Regex("""(?i)\b(?:quận|huyện|thị\s*xã|q\.)\s+[A-ZÀ-Ỹ0-9]"""),
        Regex("""(?i)\b(?:thành\s*phố|tp\.|tỉnh)\s+[A-ZÀ-Ỹ]"""),
        Regex("""(?i)\b(?:Q\s*\d+|Q\.\s*\d+)\b""")
    )

    private val VN_PHONE_REGEXES = listOf(
        // Mobile 4-3-3: 0912 345 678, 0988.123.456, 0903-888-999
        Regex("""(?<!\d)(?:(?:\+84|0)(?:3[2-9]|5[25689]|7[06-9]|8[1-9]|9[0-9]))\d[\s.-]\d{3}[\s.-]\d{3}(?!\d)"""),
        // Mobile 3-3-4: 091 234 5678, 098.123.4567
        Regex("""(?<!\d)(?:(?:\+84|0)(?:3[2-9]|5[25689]|7[06-9]|8[1-9]|9[0-9]))[\s.-]\d{3}[\s.-]\d{4}(?!\d)"""),
        // Mobile continuous: 0912345678, +84912345678
        Regex("""(?<!\d)(?:(?:\+84|0)(?:3[2-9]|5[25689]|7[06-9]|8[1-9]|9[0-9]))\d{7}(?!\d)"""),
        // Landlines Hanoi/HCMC: 024 3837 3747, 028.3822.1234, 02438373747
        Regex("""(?<!\d)02[48][\s.-]?\d{4}[\s.-]?\d{4}(?!\d)"""),
        // Other provinces landlines: 0222 382 1234
        Regex("""(?<!\d)02\d{2}[\s.-]?\d{3}[\s.-]?\d{3,4}(?!\d)"""),
        // Hotlines 1900 / 1800: 1900 1234, 1800.6622, 19001234
        Regex("""(?<!\d)(?:1900|1800)[\s.-]?\d{4}(?!\d)""")
    )

    private val BROAD_EVENT_KEYWORDS = listOf(
        "lịch hẹn", "lich hen", "lịch khám", "lich kham", "hẹn khám", "tái khám",
        "thời khóa biểu", "thoi khoa bieu", "lịch thi", "lich thi", "lịch học", "lich hoc", "buổi học", "khai giảng",
        "hội thảo", "hoi thao", "webinar", "workshop", "hội nghị", "talkshow", "cuộc họp", "cuoc hop", "meeting", "phỏng vấn",
        "vé concert", "liveshow", "show diễn", "vé xem phim", "suất chiếu", "rạp chiếu", "trận đấu", "vé bóng đá",
        "chuyến bay", "flight", "vé máy bay", "vé tàu", "vé xe", "check-in", "check-out", "lịch khởi hành", "boarding pass",
        "tiệc cưới", "lễ thành hôn", "sinh nhật", "kỷ niệm", "tiệc tất niên", "hẹn gặp"
    )

    private val EMAIL_REGEX = Regex("(?i)\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b")
    private val AMOUNT_REGEX = Regex("""(?i)\b(\d{1,3}(?:[\.,]\d{3})+|\d+)\s*(?:đ|₫|vnd|vnđ|k)(?!\w)""")

    private fun findFirstPhoneNumber(text: String): String? {
        for (regex in VN_PHONE_REGEXES) {
            val match = regex.find(text)
            if (match != null) {
                return match.value.trim()
            }
        }
        return null
    }

    /**
     * Main entry point: extracts all Smart Action Cards from screenshot image file and OCR raw text.
     */
    suspend fun extractCards(
        context: Context,
        filePath: String,
        photoId: String,
        rawOcrText: String,
        photoTitle: String = ""
    ): List<ActionCard> = withContext(Dispatchers.IO) {
        val cards = mutableListOf<ActionCard>()

        // 1. Try scanning QR code on image
        val qrCodes = scanQrCodes(context, filePath)
        var paymentExtractedFromQr = false
        for (qr in qrCodes) {
            val vietQr = VietQrParser.parse(qr)
            if (vietQr != null) {
                val subtitleParts = mutableListOf<String>()
                if (vietQr.amount != null && vietQr.amount > 0) {
                    subtitleParts.add("${formatCurrency(vietQr.amount)}đ")
                }
                if (!vietQr.content.isNullOrBlank()) {
                    subtitleParts.add(vietQr.content)
                }
                val subtitle = subtitleParts.joinToString(" • ").ifBlank { null }

                val payloadJson = JSONObject().apply {
                    put("bankName", vietQr.bankName)
                    put("bankBin", vietQr.bankBin)
                    put("accountNumber", vietQr.accountNumber)
                    put("accountHolder", vietQr.accountHolder ?: "")
                    put("amount", vietQr.amount ?: 0.0)
                    put("transferContent", vietQr.content ?: "")
                    put("rawQrString", vietQr.rawQrString)
                }.toString()

                cards.add(
                    ActionCard(
                        id = UUID.randomUUID().toString(),
                        photoId = photoId,
                        type = ActionCardType.PAYMENT,
                        title = "${vietQr.bankName} • ${vietQr.accountNumber}${if (!vietQr.accountHolder.isNullOrBlank()) " (${vietQr.accountHolder})" else ""}",
                        subtitle = subtitle,
                        payloadJson = payloadJson,
                        createdAt = System.currentTimeMillis()
                    )
                )
                paymentExtractedFromQr = true
                break
            }
        }

        // 2-5: Extract cards from text
        val textCards = extractCardsFromText(photoId, rawOcrText, photoTitle)
        for (textCard in textCards) {
            // If payment was already extracted from QR, skip text-based payment card
            if (textCard.type == ActionCardType.PAYMENT && paymentExtractedFromQr) {
                continue
            }
            cards.add(textCard)
        }

        cards
    }

    /**
     * Text-only extraction: useful for pure OCR text analysis and JVM unit tests.
     */
    fun extractCardsFromText(
        photoId: String,
        rawOcrText: String,
        photoTitle: String = ""
    ): List<ActionCard> {
        val cards = mutableListOf<ActionCard>()
        if (rawOcrText.isBlank()) return cards

        // 1. Payment from text
        val paymentCard = extractPaymentFromText(photoId, rawOcrText)
        if (paymentCard != null) {
            cards.add(paymentCard)
        }

        // 2. Address & Location
        val addressCard = extractAddressFromText(photoId, rawOcrText, photoTitle)
        if (addressCard != null) {
            cards.add(addressCard)
        }

        // 3. Contact (Phone, Email, Contact Name, Company)
        val contactCard = extractContactFromText(photoId, rawOcrText)
        if (contactCard != null) {
            cards.add(contactCard)
        }

        // 4. Event / Appointment
        val eventCard = extractEventFromText(photoId, rawOcrText, photoTitle)
        if (eventCard != null) {
            cards.add(eventCard)
        }

        return cards
    }

    private suspend fun scanQrCodes(context: Context, filePath: String): List<String> = withContext(Dispatchers.IO) {
        val result = mutableListOf<String>()
        try {
            val bitmap = loadDecryptedBitmap(context, filePath) ?: return@withContext emptyList()
            val image = InputImage.fromBitmap(bitmap, 0)

            val barcodes = suspendCancellableCoroutine<List<Barcode>> { cont ->
                barcodeScanner.process(image)
                    .addOnSuccessListener { list ->
                        if (cont.isActive) cont.resume(list)
                    }
                    .addOnFailureListener {
                        if (cont.isActive) cont.resume(emptyList())
                    }
            }
            bitmap.recycle()

            for (barcode in barcodes) {
                val raw = barcode.rawValue
                if (!raw.isNullOrBlank()) {
                    result.add(raw)
                }
            }
        } catch (_: Exception) {
        }
        result
    }

    private fun loadDecryptedBitmap(context: Context, filePath: String): Bitmap? {
        return try {
            val file = File(filePath)
            if (file.exists() && LocalCryptoManager.isEncrypted(file)) {
                LocalCryptoManager.openDecryptedInputStream(file).use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } else if (filePath.startsWith("content://")) {
                val uri = Uri.parse(filePath)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } else if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    // --- Payment Extraction from Text ---
    private fun extractPaymentFromText(photoId: String, text: String): ActionCard? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }

        // Find bank name using strict word boundary patterns
        var detectedBank: String? = null
        for ((bankName, bankRegex) in KNOWN_BANKS_MAP) {
            if (bankRegex.containsMatchIn(text)) {
                detectedBank = bankName
                break
            }
        }

        // 1. Find Account Number (8 to 19 digits) with explicit prefix (tolerating horizontal spaces, dots, dashes)
        val explicitAccRegex = Regex("""(?i)\b(?:stk|số\s*tk|so\s*tk|tài\s*khoản(?:\s*thụ\s*hưởng|\s*nhận|\s*đích)?|tai\s*khoan(?:\s*thu\s*huong|\s*nhan|\s*dich)?|account(?:\s*no\.?|\s*number)?|acct|tk\s*thụ\s*hưởng|tk\s*thu\s*huong|tk\s*nhận|tk\s*nhan|đến\s*tài\s*khoản|den\s*tai\s*khoan|số\s*thẻ\s*/\s*tk|so\s*the\s*/\s*tk)\s*[:=]?\s*([0-9][0-9 .\-]{6,22}[0-9])""")
        var accountNumber: String? = null
        val explicitMatch = explicitAccRegex.find(text)
        if (explicitMatch != null) {
            val rawDigits = explicitMatch.groupValues[1].filter { it.isDigit() }
            if (rawDigits.length in 8..19) {
                accountNumber = rawDigits
            }
        }

        // 2. If not found via prefix, but a bank was detected, look for 8-19 digit number sequences
        if (accountNumber == null && detectedBank != null) {
            val fallbackAccRegex = Regex("""\b([0-9]{3,4}[ .\-][0-9]{3,4}[ .\-][0-9]{3,6}|[0-9]{8,19})\b""")
            val candidates = fallbackAccRegex.findAll(text).map { match ->
                match.groupValues[1].filter { it.isDigit() }
            }.filter { it.length in 8..19 }.toList()

            // First priority: candidates that do not look like years or standard phone numbers
            accountNumber = candidates.firstOrNull { digits ->
                !digits.startsWith("19") && !digits.startsWith("20") &&
                !(digits.length == 10 && (digits.startsWith("03") || digits.startsWith("05") || digits.startsWith("07") || digits.startsWith("08") || digits.startsWith("09")))
            }

            // Second priority: if only phone-like numbers exist, check if line has transfer keywords
            if (accountNumber == null) {
                val transferKeywords = listOf("chuyển", "chuyen", "thụ hưởng", "thu huong", "stk", "tài khoản", "tai khoan", "chuyển khoản", "chuyen khoan")
                accountNumber = candidates.firstOrNull { digits ->
                    !digits.startsWith("19") && !digits.startsWith("20") &&
                    lines.any { line -> line.contains(digits) && transferKeywords.any { kw -> line.lowercase(Locale.getDefault()).contains(kw) } }
                }
            }
        }

        // CRITICAL GUARD: Never create a payment card without a valid account number!
        if (accountNumber.isNullOrBlank()) {
            return null
        }

        val bankDisplayName = detectedBank ?: "Chuyển khoản ngân hàng"
        val resolvedAcc = accountNumber

        // Find Account Holder Name (Uppercase line near account or keyword "Chủ TK")
        var accountHolder: String? = null
        val holderRegex = Regex("""(?i)(?:chủ\s*tk|chu\s*tk|chủ\s*tài\s*khoản|tên\s*tk|người\s*nhận|tên\s*người\s*thụ\s*hưởng)\s*[:=]?\s*([^\n\r]+)""")
        val holderMatch = holderRegex.find(text)
        if (holderMatch != null) {
            accountHolder = holderMatch.groupValues[1].trim()
        } else {
            // Find uppercase word sequence
            for (line in lines) {
                if (line.matches(Regex("""^[A-Z\s]{5,35}$""")) && !line.contains("NGAN HANG") && !line.contains("VIETNAM") && !line.contains("BANK")) {
                    accountHolder = line.trim()
                    break
                }
            }
        }

        // Find amount
        var amount: Double? = null
        val amountMatch = AMOUNT_REGEX.find(text)
        if (amountMatch != null) {
            val numStr = amountMatch.groupValues[1].replace(".", "").replace(",", "")
            amount = numStr.toDoubleOrNull()
        }

        // Find transfer content
        val contentRegex = Regex("""(?i)(?:nội\s*dung|noi\s*dung|lời\s*nhắn|ndck|nd\s*ck|memo|ref)\s*[:=]?\s*([^\n\r]+)""")
        val contentMatch = contentRegex.find(text)
        val content = contentMatch?.groupValues?.getOrNull(1)?.trim()?.take(80)

        val subtitleParts = mutableListOf<String>()
        if (amount != null && amount > 0) {
            subtitleParts.add("${formatCurrency(amount)}đ")
        }
        if (!content.isNullOrBlank()) {
            subtitleParts.add(content)
        }
        val subtitle = subtitleParts.joinToString(" • ").ifBlank { null }

        val payloadJson = JSONObject().apply {
            put("bankName", bankDisplayName)
            put("accountNumber", resolvedAcc)
            put("accountHolder", accountHolder ?: "")
            put("amount", amount ?: 0.0)
            put("transferContent", content ?: "")
        }.toString()

        val title = "$bankDisplayName • $resolvedAcc${if (!accountHolder.isNullOrBlank()) " ($accountHolder)" else ""}"

        return ActionCard(
            id = UUID.randomUUID().toString(),
            photoId = photoId,
            type = ActionCardType.PAYMENT,
            title = title,
            subtitle = subtitle,
            payloadJson = payloadJson,
            createdAt = System.currentTimeMillis()
        )
    }

    // --- Address Extraction from Text ---
    private fun extractAddressFromText(photoId: String, text: String, photoTitle: String): ActionCard? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return null

        var bestAddressLine: String? = null
        var bestScore = 0

        for (i in lines.indices) {
            val line = lines[i]
            val lowerLine = line.lowercase(Locale.getDefault())

            // Skip lines with negative noise words
            if (NEGATIVE_WORDS.any { lowerLine.contains(it) }) continue

            val hasStartCue = ADDR_START_CUES.any { it.containsMatchIn(line) }
            val hasExplicitPrefix = line.contains(Regex("""(?i)\b(?:địa\s*chỉ|dia\s*chi|address|location)\s*[:=]"""))

            if (hasStartCue || hasExplicitPrefix) {
                // Reconstruct multi-line address: check up to 2 subsequent lines
                val mergedParts = mutableListOf(line)
                var nextIdx = i + 1
                while (nextIdx < lines.size && nextIdx <= i + 2) {
                    val nextLine = lines[nextIdx]
                    val nextLower = nextLine.lowercase(Locale.getDefault())
                    if (NEGATIVE_WORDS.any { nextLower.contains(it) }) break

                    val hasAdminCue = ADDR_ADMIN_CUES.any { it.containsMatchIn(nextLine) } ||
                            VN_CITIES.any { nextLower.contains(it.lowercase(Locale.getDefault())) } ||
                            KNOWN_DISTRICTS.any { nextLower.contains(it.lowercase(Locale.getDefault())) }

                    if (hasAdminCue && nextLine.length in 4..100) {
                        mergedParts.add(nextLine)
                        nextIdx++
                    } else {
                        break
                    }
                }

                val fullConstructed = mergedParts.joinToString(", ")
                val score = calculateAddressScore(fullConstructed)
                if (score >= 4 && score > bestScore) {
                    bestScore = score
                    bestAddressLine = cleanAddressPrefix(fullConstructed)
                }
            }
        }

        if (bestAddressLine == null) return null

        // Try extracting place name (restaurant, cafe, store, company name)
        var placeName: String? = null
        val placePrefixes = listOf("quán", "cửa hàng", "nhà hàng", "tiệm", "cafe", "cà phê", "phê la", "kichi", "kfc", "lotteria", "highlands", "the coffee house", "chi nhánh", "cơ sở")
        for (line in lines) {
            val lower = line.lowercase(Locale.getDefault())
            val unacc = VietnameseUtils.removeDiacritics(lower)
            if (placePrefixes.any { lower.contains(it) || unacc.contains(it) } && !bestAddressLine.contains(line, ignoreCase = true) && line.length in 4..60) {
                placeName = line.trim()
                break
            }
        }

        if (placeName == null && photoTitle.isNotBlank() && !photoTitle.startsWith("Screenshot_", ignoreCase = true)) {
            placeName = photoTitle
        }

        val cardTitle = if (!placeName.isNullOrBlank()) {
            "📍 $placeName"
        } else {
            "📍 ${bestAddressLine.take(50)}"
        }

        // Detect city from address string
        val lowerAddr = bestAddressLine.lowercase(Locale.getDefault())
        val detectedCity = VN_CITIES.firstOrNull { lowerAddr.contains(it.lowercase(Locale.getDefault())) } ?: ""

        val payloadJson = JSONObject().apply {
            put("fullAddress", bestAddressLine)
            put("placeName", placeName ?: "")
            put("city", detectedCity)
        }.toString()

        return ActionCard(
            id = UUID.randomUUID().toString(),
            photoId = photoId,
            type = ActionCardType.ADDRESS,
            title = cardTitle,
            subtitle = bestAddressLine,
            payloadJson = payloadJson,
            createdAt = System.currentTimeMillis()
        )
    }

    private fun calculateAddressScore(text: String): Int {
        val lower = text.lowercase(Locale.getDefault())
        if (NEGATIVE_WORDS.any { lower.contains(it) }) return -1

        var score = 0
        if (Regex("""(?i)\b(?:địa\s*chỉ|dia\s*chi|address|location)\b""").containsMatchIn(text)) score += 3
        if (Regex("""(?i)\b(?:số|so)\s*\d+|\b\d+[a-zA-Z]?\s+[a-zA-ZÀ-ỹ]""").containsMatchIn(text)) score += 2
        // Slash house number e.g. 124/5A Nguyễn Trãi
        if (Regex("""\b\d+(?:/\d+[a-zA-Z]?)+\s+[a-zA-ZÀ-ỹ]""").containsMatchIn(text)) score += 2
        if (Regex("""(?i)(?:\b(?:ngõ|ngách|hẻm|kiệt)\s*\d+|\b(?:đường|phố|duong|pho)\s+[A-ZÀ-Ỹ0-9])""").containsMatchIn(text)) score += 2
        if (Regex("""(?i)\b(?:phường|xã|thị\s*trấn|p\.)\s+[A-ZÀ-Ỹ0-9]""").containsMatchIn(text)) score += 2
        if (Regex("""(?i)\b(?:quận|huyện|thị\s*xã|q\.)\s+[A-ZÀ-Ỹ0-9]""").containsMatchIn(text)) score += 2
        if (KNOWN_DISTRICTS.any { lower.contains(it.lowercase(Locale.getDefault())) }) score += 2
        if (VN_CITIES.any { lower.contains(it.lowercase(Locale.getDefault())) }) score += 2

        return score
    }

    private fun cleanAddressPrefix(line: String): String {
        return line
            .replace(Regex("""^[📍•\-\*]\s*"""), "")
            .replace(Regex("""^(?:địa\s*chỉ|dia\s*chi|location|address|add|ở|o|tại|tai)\s*[:=.-]?\s*""", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    // --- Contact Extraction from Text ---
    private fun extractContactFromText(photoId: String, text: String): ActionCard? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }

        // Find phone numbers
        val rawPhone = findFirstPhoneNumber(text)
        val phoneNumber = rawPhone?.replace(" ", "")?.replace("-", "")?.replace(".", "")

        // Find email
        val emailMatch = EMAIL_REGEX.find(text)
        val email = emailMatch?.value?.trim()

        if (phoneNumber == null && email == null) {
            return null
        }

        // Find contact name or company
        var contactName: String? = null
        var companyName: String? = null
        val namePrefixes = Regex("""(?i)(?:họ\s*(?:và\s*)?tên|ho\s*(?:va\s*)?ten|người\s*liên\s*hệ|nguoi\s*lien\s*he|người\s*nhận|nguoi\s*nhan|liên\s*hệ|lien\s*he|contact(?:\s*person)?|recruiter|hr|mr\.?|ms\.?|mrs\.?)\s*[:=]?\s*([^\n\r]+)""")
        val nameMatch = namePrefixes.find(text)
        if (nameMatch != null) {
            val candidate = nameMatch.groupValues[1].trim().take(40)
            val lowerCandidate = candidate.lowercase(Locale.getDefault())
            if (!lowerCandidate.startsWith("sản phẩm") && !lowerCandidate.startsWith("san pham") &&
                !lowerCandidate.startsWith("hàng") && !lowerCandidate.startsWith("hang") &&
                !lowerCandidate.startsWith("bài") && !lowerCandidate.startsWith("bai") &&
                !lowerCandidate.startsWith("công ty") && !lowerCandidate.startsWith("cong ty") &&
                !candidate.matches(Regex("""^[0-9\s.-]+$"""))) {
                contactName = candidate
            }
        }

        // Fallback for contact name: inspect 1-2 lines above phone number (business card / signature)
        if (contactName == null && rawPhone != null) {
            val phoneLineIdx = lines.indexOfFirst { it.contains(rawPhone) }
            if (phoneLineIdx > 0) {
                for (offset in 1..2) {
                    val prevIdx = phoneLineIdx - offset
                    if (prevIdx >= 0) {
                        val candidate = lines[prevIdx].trim()
                        val lower = candidate.lowercase(Locale.getDefault())
                        val words = candidate.split(Regex("""\s+""")).filter { it.isNotBlank() }
                        val isNameCandidate = words.size in 2..5 &&
                                !lower.contains("công ty") && !lower.contains("ngân hàng") &&
                                !lower.contains("địa chỉ") && !lower.contains("email") &&
                                !lower.contains("website") && !lower.contains("hotline") &&
                                !lower.contains("tel") && !lower.contains("phone") &&
                                candidate.all { it.isLetter() || it.isWhitespace() }
                        if (isNameCandidate) {
                            contactName = candidate
                            break
                        }
                    }
                }
            }
        }

        val companyRegex = Regex("""(?i)(?:công\s*ty|cong\s*ty|tnhh|cổ\s*phần|co\s*phan|corp|corporation|group|doanh\s*nghiệp)\s*[:=]?\s*([^\n\r]+)""")
        val companyMatch = companyRegex.find(text)
        if (companyMatch != null) {
            companyName = companyMatch.value.trim().take(60)
        }

        val titleParts = mutableListOf<String>()
        if (!contactName.isNullOrBlank()) {
            titleParts.add(contactName)
        } else if (!companyName.isNullOrBlank()) {
            titleParts.add(companyName)
        } else if (!phoneNumber.isNullOrBlank()) {
            titleParts.add(phoneNumber)
        } else if (!email.isNullOrBlank()) {
            titleParts.add(email)
        }

        val subtitleParts = mutableListOf<String>()
        if (!phoneNumber.isNullOrBlank() && titleParts.firstOrNull() != phoneNumber) {
            subtitleParts.add(phoneNumber)
        }
        if (!email.isNullOrBlank()) {
            subtitleParts.add(email)
        }
        if (!companyName.isNullOrBlank() && titleParts.firstOrNull() != companyName) {
            subtitleParts.add(companyName)
        }

        val payloadJson = JSONObject().apply {
            put("name", contactName ?: "")
            put("phoneNumber", phoneNumber ?: "")
            put("email", email ?: "")
            put("company", companyName ?: "")
        }.toString()

        return ActionCard(
            id = UUID.randomUUID().toString(),
            photoId = photoId,
            type = ActionCardType.CONTACT,
            title = "👤 " + titleParts.joinToString(" • ").ifBlank { "Thông tin liên hệ" },
            subtitle = subtitleParts.joinToString(" • ").ifBlank { null },
            payloadJson = payloadJson,
            createdAt = System.currentTimeMillis()
        )
    }

    // --- Event / Schedule Extraction from Text ---
    private fun extractEventFromText(photoId: String, text: String, photoTitle: String): ActionCard? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val isTimetable = SmartHeuristicParser.isTimetableOrSchedule(photoTitle, text)
        val lowerText = text.lowercase(Locale.getDefault())
        val unaccText = VietnameseUtils.removeDiacritics(lowerText)

        val hasEventKeyword = BROAD_EVENT_KEYWORDS.any { lowerText.contains(it) || unaccText.contains(it) }

        val parsed = SmartHeuristicParser.parse(text, photoTitle)
        val dateMillis = parsed.suggestedEventDateMillis ?: SmartHeuristicParser.extractDateTime(text)

        // Require actual event/schedule context + valid date, avoid casual dates
        if (!isTimetable && !hasEventKeyword) {
            return null
        }
        if (dateMillis == null && !isTimetable) {
            return null
        }

        val titleCandidate = parsed.suggestedEventTitle ?: lines.firstOrNull { line ->
            val l = line.lowercase(Locale.getDefault())
            val u = VietnameseUtils.removeDiacritics(l)
            BROAD_EVENT_KEYWORDS.any { l.contains(it) || u.contains(it) } && line.length in 5..60
        } ?: lines.firstOrNull { it.length in 5..60 } ?: if (photoTitle.isNotBlank() && !photoTitle.startsWith("Screenshot_", ignoreCase = true)) photoTitle else "Lịch hẹn"
        val resolvedTitle = if (titleCandidate.startsWith("Screenshot_", ignoreCase = true)) "Lịch hẹn" else titleCandidate

        val resolvedDateMillis = dateMillis ?: System.currentTimeMillis()

        val timeStr = if (dateMillis != null) {
            val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            sdf.format(java.util.Date(dateMillis))
        } else null

        val payloadJson = JSONObject().apply {
            put("title", resolvedTitle)
            put("startTimeMillis", resolvedDateMillis)
            put("description", parsed.suggestedEventDescription ?: "")
        }.toString()

        return ActionCard(
            id = UUID.randomUUID().toString(),
            photoId = photoId,
            type = ActionCardType.EVENT,
            title = "📅 $resolvedTitle",
            subtitle = timeStr,
            payloadJson = payloadJson,
            createdAt = System.currentTimeMillis()
        )
    }

    private fun formatCurrency(amount: Double): String {
        return try {
            val formatter = java.text.DecimalFormat("#,###")
            formatter.format(amount)
        } catch (_: Exception) {
            amount.toLong().toString()
        }
    }
}

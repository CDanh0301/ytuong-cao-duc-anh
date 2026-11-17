package com.example.test2.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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

object LocalActionCardExtractor {

    private val barcodeScanner by lazy {
        BarcodeScanning.getClient()
    }

    // Common banks in Vietnam
    private val KNOWN_BANKS = listOf(
        "Vietcombank", "VCB", "Techcombank", "TCB", "MB Bank", "MBBank", "MB",
        "BIDV", "VietinBank", "CTG", "VPBank", "VPB", "ACB", "TPBank", "TPB",
        "Sacombank", "STB", "VIB", "HDBank", "HDB", "OCB", "SHB", "SeABank",
        "MSB", "LPBank", "LienVietPostBank", "Nam A Bank", "Eximbank", "EIB",
        "PVcomBank", "Agribank", "SaigonBank", "DongABank", "BaoVietBank",
        "Kienlongbank", "BVBank", "VietABank", "VietBank", "NCB", "ABBANK",
        "Cake by VPBank", "Cake", "Timo", "ViettelPay", "VNPT Money", "MoMo", "ZaloPay"
    )

    private val VN_CITIES = listOf(
        "Hà Nội", "Hồ Chí Minh", "TP.HCM", "TP HCM", "Sài Gòn", "Đà Nẵng", "Hải Phòng",
        "Cần Thơ", "Bình Dương", "Đồng Nai", "Nha Trang", "Đà Lạt", "Huế", "Vũng Tàu",
        "Bắc Ninh", "Quảng Ninh", "Hải Dương", "Nam Định", "Thanh Hóa", "Nghệ An",
        "Lâm Đồng", "Khánh Hòa", "Bình Thuận", "Kiên Giang", "An Giang", "Cà Mau"
    )

    private val VN_PHONE_REGEX = Regex("(?<!\\d)(?:(?:\\+84|0)(?:3[2-9]|5[25689]|7[06-9]|8[1-9]|9[0-9]))[\\s.-]?\\d{3}[\\s.-]?\\d{4}(?!\\d)")
    private val EMAIL_REGEX = Regex("(?i)\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b")
    private val AMOUNT_REGEX = Regex("""(?i)\b(\d{1,3}(?:[\.,]\d{3})+|\d+)\s*(?:đ|₫|vnd|vnđ|k)(?!\w)""")

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
        val lowerText = text.lowercase(Locale.getDefault())
        val unaccentedText = VietnameseUtils.removeDiacritics(lowerText)

        // Find bank name
        var detectedBank: String? = null
        for (bank in KNOWN_BANKS) {
            val bankLower = bank.lowercase(Locale.getDefault())
            if (lowerText.contains(bankLower) || unaccentedText.contains(VietnameseUtils.removeDiacritics(bankLower))) {
                detectedBank = bank
                break
            }
        }

        // Find Account Number (8 to 16 digits)
        val accRegex = Regex("""(?i)\b(?:stk|số\s*tk|so\s*tk|tài\s*khoản|tai\s*khoan|account(?:\s*no\.?|\s*number)?|acct)\s*[:=]?\s*([0-9]{8,16})\b""")
        var accountNumber = accRegex.find(text)?.groupValues?.getOrNull(1)

        if (accountNumber == null && detectedBank != null) {
            // Find any 9-16 digit sequence near bank name or keywords
            val fallbackAccRegex = Regex("""\b([0-9]{9,16})\b""")
            val candidates = fallbackAccRegex.findAll(text).map { it.groupValues[1] }
            accountNumber = candidates.firstOrNull { digits ->
                // Avoid matching timestamp or phone number
                !digits.startsWith("03") && !digits.startsWith("05") && !digits.startsWith("07") &&
                !digits.startsWith("08") && !digits.startsWith("09") && digits.length in 9..15
            }
        }

        if (accountNumber == null && detectedBank == null) {
            return null
        }

        val bankDisplayName = detectedBank ?: "Chuyển khoản ngân hàng"
        val resolvedAcc = accountNumber ?: ""

        // Find Account Holder Name (Uppercase line near account or keyword "Chủ TK")
        var accountHolder: String? = null
        val holderRegex = Regex("""(?i)(?:chủ\s*tk|chu\s*tk|chủ\s*tài\s*khoản|tên\s*tk|người\s*nhận|tên\s*người\s*thụ\s*hưởng)\s*[:=]?\s*([^\n\r]+)""")
        val holderMatch = holderRegex.find(text)
        if (holderMatch != null) {
            accountHolder = holderMatch.groupValues[1].trim()
        } else {
            // Find uppercase word sequence
            val upperRegex = Regex("""\b([A-Z\s]{4,30})\b""")
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

        val title = if (resolvedAcc.isNotBlank()) {
            "$bankDisplayName • $resolvedAcc${if (!accountHolder.isNullOrBlank()) " ($accountHolder)" else ""}"
        } else {
            bankDisplayName
        }

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
        val lowerText = text.lowercase(Locale.getDefault())
        val unaccentedText = VietnameseUtils.removeDiacritics(lowerText)

        // Check address indicators: số nhà, đường, phố, ngõ, phường, quận, quận/huyện, tp
        val addressCues = listOf("đường", "phố", "ngõ", "ngách", "hẻm", "phường", "quận", "huyện", "thị xã", "p.", "q.")
        val unaccCues = listOf("duong", "pho", "ngo", "ngach", "hem", "phuong", "quan", "huyen", "thi xa")
        val hasAddressCue = addressCues.any { lowerText.contains(it) } || unaccCues.any { unaccentedText.contains(it) }

        var detectedCity: String? = null
        for (city in VN_CITIES) {
            if (lowerText.contains(city.lowercase(Locale.getDefault())) || unaccentedText.contains(VietnameseUtils.removeDiacritics(city.lowercase(Locale.getDefault())))) {
                detectedCity = city
                break
            }
        }

        if (!hasAddressCue && detectedCity == null) {
            return null
        }

        // Find candidate address line
        var bestAddressLine: String? = null
        for (line in lines) {
            val lowerLine = line.lowercase(Locale.getDefault())
            val unaccLine = VietnameseUtils.removeDiacritics(lowerLine)
            val matchCount = addressCues.count { lowerLine.contains(it) } + unaccCues.count { unaccLine.contains(it) }
            if (matchCount >= 1 && (line.contains(",") || line.contains("-") || Regex("""\d+""").containsMatchIn(line))) {
                if (line.length in 10..150) {
                    bestAddressLine = cleanAddressPrefix(line)
                    break
                }
            }
        }

        if (bestAddressLine == null && detectedCity != null) {
            // Fallback: look for line containing city
            for (line in lines) {
                if (line.contains(detectedCity, ignoreCase = true) && line.length in 12..120) {
                    bestAddressLine = cleanAddressPrefix(line)
                    break
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
            if (placePrefixes.any { lower.contains(it) || unacc.contains(it) } && line != bestAddressLine && line.length in 4..60) {
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
            "📍 ${bestAddressLine.take(45)}"
        }

        val payloadJson = JSONObject().apply {
            put("fullAddress", bestAddressLine)
            put("placeName", placeName ?: "")
            put("city", detectedCity ?: "")
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

    private fun cleanAddressPrefix(line: String): String {
        return line.replace(Regex("""^(?:địa\s*chỉ|dia\s*chi|location|address|add|ở|o|tại|tai)\s*[:=.-]?\s*""", RegexOption.IGNORE_CASE), "").trim()
    }

    // --- Contact Extraction from Text ---
    private fun extractContactFromText(photoId: String, text: String): ActionCard? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }

        // Find phone numbers
        val phoneMatch = VN_PHONE_REGEX.find(text)
        val phoneNumber = phoneMatch?.value?.replace(" ", "")?.replace("-", "")?.replace(".", "")

        // Find email
        val emailMatch = EMAIL_REGEX.find(text)
        val email = emailMatch?.value?.trim()

        if (phoneNumber == null && email == null) {
            return null
        }

        // Find contact name or company
        var contactName: String? = null
        var companyName: String? = null
        val namePrefixes = Regex("""(?i)(?:họ\s*tên|ho\s*ten|liên\s*hệ|lien\s*he|người\s*liên\s*hệ|contact|tên|ten|recruiter|hr|mr\.?|ms\.?|mrs\.?)\s*[:=]?\s*([^\n\r]+)""")
        val nameMatch = namePrefixes.find(text)
        if (nameMatch != null) {
            contactName = nameMatch.groupValues[1].trim().take(40)
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
        val isTimetable = SmartHeuristicParser.isTimetableOrSchedule(photoTitle, text)
        val lowerText = text.lowercase(Locale.getDefault())
        val unaccText = VietnameseUtils.removeDiacritics(lowerText)

        val eventKeywords = listOf("lịch hẹn", "lich hen", "lịch khám", "lich kham", "vé xem phim", "ve xem phim", "chuyến bay", "chuyen bay", "lịch họp", "lich hop", "appointment", "schedule", "boarding pass")
        val hasEventKeyword = eventKeywords.any { lowerText.contains(it) || unaccText.contains(it) }

        val parsed = SmartHeuristicParser.parse(text, photoTitle)
        val hasDate = parsed.suggestedEventDateMillis != null

        if (!isTimetable && !hasEventKeyword && !hasDate) {
            return null
        }

        val title = parsed.suggestedEventTitle ?: if (photoTitle.isNotBlank() && !photoTitle.startsWith("Screenshot_", ignoreCase = true)) photoTitle else "Lịch hẹn"
        val dateMillis = parsed.suggestedEventDateMillis ?: System.currentTimeMillis()

        val timeStr = if (parsed.suggestedEventDateMillis != null) {
            val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            sdf.format(java.util.Date(parsed.suggestedEventDateMillis))
        } else null

        val payloadJson = JSONObject().apply {
            put("title", title)
            put("startTimeMillis", dateMillis)
            put("description", parsed.suggestedEventDescription ?: "")
        }.toString()

        return ActionCard(
            id = UUID.randomUUID().toString(),
            photoId = photoId,
            type = ActionCardType.EVENT,
            title = "📅 $title",
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

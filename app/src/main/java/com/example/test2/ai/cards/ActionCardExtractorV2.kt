package com.example.test2.ai.cards

import android.content.Context
import android.graphics.Bitmap
import com.example.test2.ai.OcrLine
import com.example.test2.ai.SmartHeuristicParser
import com.example.test2.ai.VietQrParser
import com.example.test2.shared.models.ActionCard
import com.example.test2.shared.models.ActionCardType
import com.example.test2.shared.utils.VietnameseUtils
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale
import kotlin.coroutines.resume

object ActionCardExtractorV2 {

    private val barcodeScanner by lazy {
        BarcodeScanning.getClient()
    }

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

    suspend fun extractCardsFromBitmapAndText(
        bitmap: Bitmap?,
        photoId: String,
        rawOcrText: String,
        photoTitle: String = "",
        ocrLines: List<OcrLine>? = null
    ): List<ActionCard> = withContext(Dispatchers.IO) {
        val cards = mutableListOf<ActionCard>()
        var paymentFromQr: ActionCard? = null

        // 1. Scan QR from decoded bitmap if available
        if (bitmap != null) {
            val qrCodes = scanQrCodesFromBitmap(bitmap)
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
                        put("ev", CardIds.EXTRACTOR_VERSION)
                    }.toString()

                    val cardId = CardIds.generateId(photoId, ActionCardType.PAYMENT, "${vietQr.bankName}${vietQr.accountNumber}")
                    paymentFromQr = ActionCard(
                        id = cardId,
                        photoId = photoId,
                        type = ActionCardType.PAYMENT,
                        title = "${vietQr.bankName} • ${vietQr.accountNumber}${if (!vietQr.accountHolder.isNullOrBlank()) " (${vietQr.accountHolder})" else ""}",
                        subtitle = subtitle,
                        payloadJson = payloadJson,
                        createdAt = 0L
                    )
                    break
                }
            }
        }

        // 2. Extract text-based cards
        val textCards = extractCardsFromText(photoId, rawOcrText, photoTitle, ocrLines)

        // Add QR payment card first if found, otherwise keep text payment card
        if (paymentFromQr != null) {
            cards.add(paymentFromQr)
            for (tc in textCards) {
                if (tc.type != ActionCardType.PAYMENT) {
                    cards.add(tc)
                }
            }
        } else {
            cards.addAll(textCards)
        }

        // Re-assign created_at to guarantee order: Payment -> Address -> Contact(s) -> Event
        val baseTime = System.currentTimeMillis()
        cards.mapIndexed { index, card ->
            card.copy(createdAt = baseTime + index)
        }
    }

    fun extractCardsFromText(
        photoId: String,
        rawOcrText: String,
        photoTitle: String = "",
        ocrLines: List<OcrLine>? = null
    ): List<ActionCard> {
        val cards = mutableListOf<ActionCard>()
        if (rawOcrText.isBlank()) return cards

        // 1. Payment
        val paymentCard = extractPaymentFromText(photoId, rawOcrText)
        if (paymentCard != null) {
            cards.add(paymentCard)
        }

        // 2. Address
        val addressCard = extractAddressCard(photoId, rawOcrText, ocrLines)
        if (addressCard != null) {
            cards.add(addressCard)
        }

        // 3. Contact(s)
        val contactCards = extractContactCards(photoId, rawOcrText, ocrLines)
        cards.addAll(contactCards)

        // 4. Event - Deprecated: Calendar events are managed exclusively via cat_events and Photo.suggestedEventDateMillis
        // val eventCard = extractEventFromText(photoId, rawOcrText, photoTitle)

        val baseTime = System.currentTimeMillis()
        return cards.mapIndexed { index, card ->
            card.copy(createdAt = baseTime + index)
        }
    }

    private fun extractAddressCard(
        photoId: String,
        rawOcrText: String,
        ocrLines: List<OcrLine>?
    ): ActionCard? {
        val result = AddressExtractor.extractAddress(rawOcrText, ocrLines) ?: return null

        val title = if (result.placeName.isNotBlank()) {
            "📍 ${result.placeName}"
        } else {
            "📍 ${result.fullAddress.take(50)}"
        }

        val payloadJson = JSONObject().apply {
            put("fullAddress", result.fullAddress)
            put("placeName", result.placeName)
            put("city", result.city)
            put("adminSystem", result.adminSystem)
            put("ev", CardIds.EXTRACTOR_VERSION)
        }.toString()

        val cardId = CardIds.generateId(photoId, ActionCardType.ADDRESS, result.fullAddress)

        return ActionCard(
            id = cardId,
            photoId = photoId,
            type = ActionCardType.ADDRESS,
            title = title,
            subtitle = result.fullAddress,
            payloadJson = payloadJson,
            createdAt = 0L
        )
    }

    private fun extractContactCards(
        photoId: String,
        rawOcrText: String,
        ocrLines: List<OcrLine>?
    ): List<ActionCard> {
        val phones = PhoneExtractor.extractPhones(rawOcrText)
        val nameMap = ContactNamer.resolveNames(rawOcrText, phones, ocrLines)
        val company = ContactNamer.extractCompany(rawOcrText)
        val email = EMAIL_REGEX.find(rawOcrText)?.value?.trim()

        val cards = mutableListOf<ActionCard>()

        if (phones.isNotEmpty()) {
            for ((index, phone) in phones.withIndex()) {
                val assignedName = nameMap[phone.canonical]
                val assignedEmail = if (index == 0) email else null
                val assignedCompany = if (index == 0) company else null

                val titleParts = mutableListOf<String>()
                if (!assignedName.isNullOrBlank()) {
                    titleParts.add(assignedName)
                } else if (!assignedCompany.isNullOrBlank()) {
                    titleParts.add(assignedCompany)
                } else {
                    titleParts.add(phone.payloadNumber)
                }

                val subtitleParts = mutableListOf<String>()
                if (titleParts.firstOrNull() != phone.payloadNumber) {
                    subtitleParts.add(phone.payloadNumber)
                }
                if (!assignedEmail.isNullOrBlank()) {
                    subtitleParts.add(assignedEmail)
                }
                if (!assignedCompany.isNullOrBlank() && titleParts.firstOrNull() != assignedCompany) {
                    subtitleParts.add(assignedCompany)
                }

                val payloadJson = JSONObject().apply {
                    put("name", assignedName ?: "")
                    put("phoneNumber", phone.payloadNumber)
                    put("email", assignedEmail ?: "")
                    put("company", assignedCompany ?: "")
                    put("phoneKind", phone.kind.name)
                    put("ev", CardIds.EXTRACTOR_VERSION)
                }.toString()

                val cardId = CardIds.generateId(photoId, ActionCardType.CONTACT, phone.canonical)

                cards.add(
                    ActionCard(
                        id = cardId,
                        photoId = photoId,
                        type = ActionCardType.CONTACT,
                        title = "👤 " + titleParts.joinToString(" • ").ifBlank { "Thông tin liên hệ" },
                        subtitle = subtitleParts.joinToString(" • ").ifBlank { null },
                        payloadJson = payloadJson,
                        createdAt = 0L
                    )
                )
            }
        } else if (!email.isNullOrBlank()) {
            // Email-only contact card
            val payloadJson = JSONObject().apply {
                put("name", "")
                put("phoneNumber", "")
                put("email", email)
                put("company", company ?: "")
                put("phoneKind", "")
                put("ev", CardIds.EXTRACTOR_VERSION)
            }.toString()

            val cardId = CardIds.generateId(photoId, ActionCardType.CONTACT, email)

            cards.add(
                ActionCard(
                    id = cardId,
                    photoId = photoId,
                    type = ActionCardType.CONTACT,
                    title = "👤 $email",
                    subtitle = company,
                    payloadJson = payloadJson,
                    createdAt = 0L
                )
            )
        }

        return cards
    }

    private fun extractPaymentFromText(photoId: String, text: String): ActionCard? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }

        var detectedBank: String? = null
        for ((bankName, bankRegex) in KNOWN_BANKS_MAP) {
            if (bankRegex.containsMatchIn(text)) {
                detectedBank = bankName
                break
            }
        }

        val explicitAccRegex = Regex("""(?i)\b(?:stk|số\s*tk|so\s*tk|tài\s*khoản(?:\s*thụ\s*hưởng|\s*nhận|\s*đích)?|tai\s*khoan(?:\s*thu\s*huong|\s*nhan|\s*dich)?|account(?:\s*no\.?|\s*number)?|acct|tk\s*thụ\s*hưởng|tk\s*thu\s*huong|tk\s*nhận|tk\s*nhan|đến\s*tài\s*khoản|den\s*tai\s*khoan|số\s*thẻ\s*/\s*tk|so\s*the\s*/\s*tk)\s*[:=]?\s*([0-9][0-9 .\-]{6,22}[0-9])""")
        var accountNumber: String? = null
        val explicitMatch = explicitAccRegex.find(text)
        if (explicitMatch != null) {
            val rawDigits = explicitMatch.groupValues[1].filter { it.isDigit() }
            if (rawDigits.length in 8..19) {
                accountNumber = rawDigits
            }
        }

        if (accountNumber == null && detectedBank != null) {
            val fallbackAccRegex = Regex("""\b([0-9]{3,4}[ .\-][0-9]{3,4}[ .\-][0-9]{3,6}|[0-9]{8,19})\b""")
            val candidates = fallbackAccRegex.findAll(text).map { match ->
                match.groupValues[1].filter { it.isDigit() }
            }.filter { it.length in 8..19 }.toList()

            accountNumber = candidates.firstOrNull { digits ->
                !digits.startsWith("19") && !digits.startsWith("20") &&
                !(digits.length == 10 && (digits.startsWith("03") || digits.startsWith("05") || digits.startsWith("07") || digits.startsWith("08") || digits.startsWith("09")))
            }

            if (accountNumber == null) {
                val transferKeywords = listOf("chuyển", "chuyen", "thụ hưởng", "thu huong", "stk", "tài khoản", "tai khoan", "chuyển khoản", "chuyen khoan")
                accountNumber = candidates.firstOrNull { digits ->
                    !digits.startsWith("19") && !digits.startsWith("20") &&
                    lines.any { line -> line.contains(digits) && transferKeywords.any { kw -> line.lowercase(Locale.getDefault()).contains(kw) } }
                }
            }
        }

        if (accountNumber.isNullOrBlank()) {
            return null
        }

        val bankDisplayName = detectedBank ?: "Chuyển khoản ngân hàng"
        val resolvedAcc = accountNumber

        var accountHolder: String? = null
        val holderRegex = Regex("""(?i)(?:chủ\s*tk|chu\s*tk|chủ\s*tài\s*khoản|tên\s*tk|người\s*nhận|tên\s*người\s*thụ\s*hưởng)\s*[:=]?\s*([^\n\r]+)""")
        val holderMatch = holderRegex.find(text)
        if (holderMatch != null) {
            accountHolder = holderMatch.groupValues[1].trim()
        } else {
            for (line in lines) {
                if (line.matches(Regex("""^[A-Z\s]{5,35}$""")) && !line.contains("NGAN HANG") && !line.contains("VIETNAM") && !line.contains("BANK")) {
                    accountHolder = line.trim()
                    break
                }
            }
        }

        var amount: Double? = null
        val amountMatch = AMOUNT_REGEX.find(text)
        if (amountMatch != null) {
            val numStr = amountMatch.groupValues[1].replace(".", "").replace(",", "")
            amount = numStr.toDoubleOrNull()
        }

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
            put("ev", CardIds.EXTRACTOR_VERSION)
        }.toString()

        val title = "$bankDisplayName • $resolvedAcc${if (!accountHolder.isNullOrBlank()) " ($accountHolder)" else ""}"
        val cardId = CardIds.generateId(photoId, ActionCardType.PAYMENT, "$bankDisplayName$resolvedAcc")

        return ActionCard(
            id = cardId,
            photoId = photoId,
            type = ActionCardType.PAYMENT,
            title = title,
            subtitle = subtitle,
            payloadJson = payloadJson,
            createdAt = 0L
        )
    }

    private fun extractEventFromText(photoId: String, text: String, photoTitle: String): ActionCard? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val isTimetable = SmartHeuristicParser.isTimetableOrSchedule(photoTitle, text)
        val lowerText = text.lowercase(Locale.getDefault())
        val unaccText = VietnameseUtils.removeDiacritics(lowerText)

        val hasEventKeyword = BROAD_EVENT_KEYWORDS.any { lowerText.contains(it) || unaccText.contains(it) }

        val parsed = SmartHeuristicParser.parse(text, photoTitle)
        val dateMillis = parsed.suggestedEventDateMillis ?: SmartHeuristicParser.extractDateTime(text)

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
            put("ev", CardIds.EXTRACTOR_VERSION)
        }.toString()

        val cardId = CardIds.generateId(photoId, ActionCardType.EVENT, "$resolvedTitle+$resolvedDateMillis")

        return ActionCard(
            id = cardId,
            photoId = photoId,
            type = ActionCardType.EVENT,
            title = "📅 $resolvedTitle",
            subtitle = timeStr,
            payloadJson = payloadJson,
            createdAt = 0L
        )
    }

    private suspend fun scanQrCodesFromBitmap(bitmap: Bitmap): List<String> = withContext(Dispatchers.IO) {
        val result = mutableListOf<String>()
        try {
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

    private fun formatCurrency(amount: Double): String {
        return try {
            val formatter = java.text.DecimalFormat("#,###")
            formatter.format(amount)
        } catch (_: Exception) {
            amount.toLong().toString()
        }
    }
}

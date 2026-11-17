package com.example.test2.ai

data class ParsedVietQr(
    val bankName: String,
    val bankBin: String,
    val accountNumber: String,
    val accountHolder: String? = null,
    val amount: Double? = null,
    val content: String? = null,
    val rawQrString: String
)

object VietQrParser {

    private val BANK_BINS = mapOf(
        "970436" to "Vietcombank",
        "970415" to "VietinBank",
        "970418" to "BIDV",
        "970405" to "Agribank",
        "970407" to "Techcombank",
        "970422" to "MB Bank",
        "970432" to "VPBank",
        "970416" to "ACB",
        "970423" to "TPBank",
        "970403" to "Sacombank",
        "970441" to "VIB",
        "970437" to "HDBank",
        "970448" to "OCB",
        "970443" to "SHB",
        "970400" to "SaigonBank",
        "970406" to "DongABank",
        "970429" to "SCB",
        "970440" to "SeABank",
        "970428" to "Nam A Bank",
        "970431" to "Eximbank",
        "970414" to "OceanBank",
        "970412" to "PVcomBank",
        "970426" to "MSB",
        "970427" to "VietABank",
        "970433" to "VietBank",
        "970454" to "BVBank",
        "970449" to "LPBank",
        "970452" to "Kienlongbank",
        "970438" to "BaoVietBank",
        "970458" to "UOB",
        "970419" to "NCB",
        "970425" to "ABBANK",
        "970408" to "GPBank",
        "970424" to "Shinhan Bank",
        "970468" to "Cake by VPBank",
        "970466" to "Timo",
        "546034" to "ViettelPay",
        "546035" to "VNPT Money"
    )

    fun getBankNameByBin(bin: String): String {
        return BANK_BINS[bin.trim()] ?: "Ngân hàng ($bin)"
    }

    /**
     * Parses an EMVCo TLV string into a map of Tag -> Value.
     */
    fun parseTlv(emv: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        var index = 0
        val len = emv.length
        while (index + 4 <= len) {
            val tag = emv.substring(index, index + 2)
            val lengthStr = emv.substring(index + 2, index + 4)
            val length = lengthStr.toIntOrNull() ?: break
            index += 4
            if (index + length > len) break
            val value = emv.substring(index, index + length)
            result[tag] = value
            index += length
        }
        return result
    }

    /**
     * Parses a QR code string (standard EMVCo / Napas VietQR or VietQR URL).
     */
    fun parse(qrString: String): ParsedVietQr? {
        val trimmed = qrString.trim()
        if (trimmed.isEmpty()) return null

        // 1. Check EMVCo format (starts with 000201...)
        if (trimmed.startsWith("000201") || trimmed.contains("A000000727")) {
            return parseEmvCo(trimmed)
        }

        // 2. Check VietQR URL format: e.g. https://img.vietqr.io/image/MB-0988123456-compact2.jpg?amount=50000&addInfo=An%20trua
        if (trimmed.contains("vietqr.io") || trimmed.contains("vietqr.net")) {
            return parseVietQrUrl(trimmed)
        }

        return null
    }

    private fun parseEmvCo(emv: String): ParsedVietQr? {
        val rootTags = parseTlv(emv)
        val tag38 = rootTags["38"] ?: return null
        val sub38 = parseTlv(tag38)

        // GUID A000000727 (Napas)
        val guid = sub38["00"]
        if (guid != null && !guid.equals("A000000727", ignoreCase = true)) {
            // Not a VietQR NAPAS payload
        }

        val tag01 = sub38["01"] ?: return null
        val sub01 = parseTlv(tag01)
        val bankBin = sub01["00"] ?: ""
        val accountNumber = sub01["01"] ?: return null

        if (bankBin.isBlank() && accountNumber.isBlank()) return null

        val bankName = getBankNameByBin(bankBin)

        // Amount (Tag 54)
        val amount = rootTags["54"]?.toDoubleOrNull()

        // Merchant Name (Tag 59)
        val accountHolder = rootTags["59"]?.trim()?.ifBlank { null }

        // Content / Message in Tag 62 Sub-tag 08
        val content = rootTags["62"]?.let { tag62 ->
            val sub62 = parseTlv(tag62)
            sub62["08"]?.trim()?.ifBlank { null }
        }

        return ParsedVietQr(
            bankName = bankName,
            bankBin = bankBin,
            accountNumber = accountNumber,
            accountHolder = accountHolder,
            amount = amount,
            content = content,
            rawQrString = emv
        )
    }

    private fun parseVietQrUrl(url: String): ParsedVietQr? {
        try {
            val urlWithoutScheme = url.substringAfter("://")
            val pathAndQuery = urlWithoutScheme.substringAfter("/", "")
            val path = pathAndQuery.substringBefore("?")
            val queryString = if (pathAndQuery.contains("?")) pathAndQuery.substringAfter("?") else ""

            val segments = path.split("/").filter { it.isNotBlank() }
            val filePart = segments.lastOrNull()?.substringBefore(".") ?: return null
            val parts = filePart.split("-")
            if (parts.size >= 2) {
                val bankCode = parts[0].uppercase()
                val accNumber = parts[1]

                val params = mutableMapOf<String, String>()
                if (queryString.isNotBlank()) {
                    for (pair in queryString.split("&")) {
                        val kv = pair.split("=", limit = 2)
                        if (kv.isNotEmpty()) {
                            val key = java.net.URLDecoder.decode(kv[0], "UTF-8")
                            val value = if (kv.size > 1) java.net.URLDecoder.decode(kv[1], "UTF-8") else ""
                            params[key] = value
                        }
                    }
                }

                val amount = params["amount"]?.toDoubleOrNull()
                val content = params["addInfo"] ?: params["description"]
                val accountName = params["accountName"]

                val resolvedBankName = if (bankCode.all { it.isDigit() }) {
                    getBankNameByBin(bankCode)
                } else {
                    bankCode
                }

                return ParsedVietQr(
                    bankName = resolvedBankName,
                    bankBin = if (bankCode.all { it.isDigit() }) bankCode else "",
                    accountNumber = accNumber,
                    accountHolder = accountName,
                    amount = amount,
                    content = content,
                    rawQrString = url
                )
            }
        } catch (_: Exception) {
        }
        return null
    }
}

package com.example.test2.ai

import com.example.test2.shared.utils.VietnameseUtils
import java.util.Calendar
import java.util.Locale

data class ParsedSearchQuery(
    val cleanTextQuery: String,
    val dateRangeMillis: LongRange?,
    val timeDescription: String? = null
)

object TemporalQueryParser {

    /**
     * Parses natural language query to extract temporal intent (e.g., "tuần trước", "tháng trước", "hôm qua")
     * and separates it from the pure semantic concept query.
     */
    fun parse(rawQuery: String, currentEpochMillis: Long = System.currentTimeMillis()): ParsedSearchQuery {
        val trimmed = rawQuery.trim()
        if (trimmed.isBlank()) {
            return ParsedSearchQuery(cleanTextQuery = "", dateRangeMillis = null)
        }

        val lower = trimmed.lowercase(Locale.getDefault())
        val unaccented = VietnameseUtils.removeDiacritics(lower)

        var matchedRange: LongRange? = null
        var matchedPhrase: String? = null
        var matchedDesc: String? = null

        // 1. Check specific temporal patterns
        when {
            // Hôm nay / Today
            lower.contains("hôm nay") || unaccented.contains("hom nay") || lower.contains("today") -> {
                matchedPhrase = if (lower.contains("hôm nay")) "hôm nay" else if (unaccented.contains("hom nay")) "hom nay" else "today"
                matchedRange = getDayRange(currentEpochMillis, 0)
                matchedDesc = "Hôm nay"
            }

            // Hôm qua / Yesterday
            lower.contains("hôm qua") || unaccented.contains("hom qua") || lower.contains("yesterday") -> {
                matchedPhrase = if (lower.contains("hôm qua")) "hôm qua" else if (unaccented.contains("hom qua")) "hom qua" else "yesterday"
                matchedRange = getDayRange(currentEpochMillis, -1)
                matchedDesc = "Hôm qua"
            }

            // Tuần trước / Tuần qua / Last week
            lower.contains("tuần trước") || unaccented.contains("tuan truoc") ||
            lower.contains("tuần qua") || unaccented.contains("tuan qua") ||
            lower.contains("last week") -> {
                matchedPhrase = when {
                    lower.contains("tuần trước") -> "tuần trước"
                    unaccented.contains("tuan truoc") -> "tuan truoc"
                    lower.contains("tuần qua") -> "tuần qua"
                    unaccented.contains("tuan qua") -> "tuan qua"
                    else -> "last week"
                }
                matchedRange = getLastWeekRange(currentEpochMillis)
                matchedDesc = "Tuần trước"
            }

            // Tuần này / This week
            lower.contains("tuần này") || unaccented.contains("tuan nay") || lower.contains("this week") -> {
                matchedPhrase = when {
                    lower.contains("tuần này") -> "tuần này"
                    unaccented.contains("tuan nay") -> "tuan nay"
                    else -> "this week"
                }
                matchedRange = getThisWeekRange(currentEpochMillis)
                matchedDesc = "Tuần này"
            }

            // Tháng trước / Last month
            lower.contains("tháng trước") || unaccented.contains("thang truoc") || lower.contains("last month") -> {
                matchedPhrase = when {
                    lower.contains("tháng trước") -> "tháng trước"
                    unaccented.contains("thang truoc") -> "thang truoc"
                    else -> "last month"
                }
                matchedRange = getLastMonthRange(currentEpochMillis)
                matchedDesc = "Tháng trước"
            }

            // Tháng này / This month
            lower.contains("tháng này") || unaccented.contains("thang nay") || lower.contains("this month") -> {
                matchedPhrase = when {
                    lower.contains("tháng này") -> "tháng này"
                    unaccented.contains("thang nay") -> "thang nay"
                    else -> "this month"
                }
                matchedRange = getThisMonthRange(currentEpochMillis)
                matchedDesc = "Tháng này"
            }

            // Năm ngoái / Last year
            lower.contains("năm ngoái") || unaccented.contains("nam ngoai") ||
            lower.contains("năm trước") || unaccented.contains("nam truoc") ||
            lower.contains("last year") -> {
                matchedPhrase = when {
                    lower.contains("năm ngoái") -> "năm ngoái"
                    unaccented.contains("nam ngoai") -> "nam ngoai"
                    lower.contains("năm trước") -> "năm trước"
                    unaccented.contains("nam truoc") -> "nam truoc"
                    else -> "last year"
                }
                matchedRange = getLastYearRange(currentEpochMillis)
                matchedDesc = "Năm ngoái"
            }

            // Gần đây / Recent
            lower.contains("gần đây") || unaccented.contains("gan day") || lower.contains("recent") -> {
                matchedPhrase = when {
                    lower.contains("gần đây") -> "gần đây"
                    unaccented.contains("gan day") -> "gan day"
                    else -> "recent"
                }
                val sevenDaysAgo = currentEpochMillis - (7L * 24 * 60 * 60 * 1000)
                matchedRange = sevenDaysAgo..currentEpochMillis
                matchedDesc = "7 ngày qua"
            }
        }

        // 2. Clean query string by removing temporal pattern
        var cleaned = trimmed
        if (matchedPhrase != null) {
            val regex = Regex("(?i)\\b" + Regex.escape(matchedPhrase) + "\\b")
            cleaned = cleaned.replace(regex, " ").replace(Regex("\\s+"), " ").trim()
        }

        // 3. Synonym expansion for specific generic concepts
        val expanded = expandSynonyms(cleaned)

        return ParsedSearchQuery(
            cleanTextQuery = if (expanded.isNotBlank()) expanded else cleaned,
            dateRangeMillis = matchedRange,
            timeDescription = matchedDesc
        )
    }

    internal fun expandSynonyms(query: String): String {
        val lower = query.lowercase(Locale.getDefault())
        val unaccented = VietnameseUtils.removeDiacritics(lower)
        val hardcoded = when {
            unaccented == "do lam bep" || lower == "đồ làm bếp" -> "$query nồi chảo bếp gia dụng"
            unaccented == "xe co" || lower == "xe cộ" -> "$query xe máy ô tô phương tiện"
            unaccented == "an uong" || lower == "ăn uống" -> "$query nhà hàng quán ăn món ăn"
            else -> null
        }
        if (hardcoded != null) return hardcoded

        // Visual concept expansion for nature, pets, scenery and travel
        val clusterTerms = VisualConceptExpander.getVisualClusterTerms(query)
        if (clusterTerms.isNotEmpty()) {
            val extraTerms = clusterTerms.take(8)
            return "$query ${extraTerms.joinToString(" ")}"
        }
        return query
    }

    private fun getDayRange(epochMillis: Long, offsetDays: Int): LongRange {
        val cal = Calendar.getInstance().apply {
            timeInMillis = epochMillis
            add(Calendar.DAY_OF_YEAR, offsetDays)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis
        return start..end
    }

    private fun getLastWeekRange(epochMillis: Long): LongRange {
        val cal = Calendar.getInstance().apply {
            timeInMillis = epochMillis
            firstDayOfWeek = Calendar.MONDAY
            add(Calendar.WEEK_OF_YEAR, -1)
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_WEEK, 6)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis
        return start..end
    }

    private fun getThisWeekRange(epochMillis: Long): LongRange {
        val cal = Calendar.getInstance().apply {
            timeInMillis = epochMillis
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis..epochMillis
    }

    private fun getLastMonthRange(epochMillis: Long): LongRange {
        val cal = Calendar.getInstance().apply {
            timeInMillis = epochMillis
            add(Calendar.MONTH, -1)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        cal.set(Calendar.DAY_OF_MONTH, maxDay)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis
        return start..end
    }

    private fun getThisMonthRange(epochMillis: Long): LongRange {
        val cal = Calendar.getInstance().apply {
            timeInMillis = epochMillis
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis..epochMillis
    }

    private fun getLastYearRange(epochMillis: Long): LongRange {
        val cal = Calendar.getInstance().apply {
            timeInMillis = epochMillis
            add(Calendar.YEAR, -1)
            set(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_YEAR)
        cal.set(Calendar.DAY_OF_YEAR, maxDay)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis
        return start..end
    }
}

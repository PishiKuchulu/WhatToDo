package com.example.pfptodo.domain.usecase.task

import com.example.pfptodo.R
import com.example.pfptodo.core.util.DigitNormalizer
import com.example.pfptodo.core.util.PersianNormalizer
import com.example.pfptodo.domain.model.ParsedSearchQuery
import com.example.pfptodo.domain.model.Priority
import com.example.pfptodo.domain.model.SearchChipType
import com.example.pfptodo.domain.model.SearchFilterChip
import com.example.pfptodo.domain.model.TaskFilter
import java.time.Clock
import java.util.Locale

/**
 * Deterministic offline rule-based parser for Natural Language Search queries.
 * Decomposes Persian and English search queries into structured [TaskFilter] dimensions,
 * visual [SearchFilterChip] badges, and clean residual search keywords.
 */
class ParseNaturalLanguageSearchUseCase(
    private val clock: Clock = Clock.systemDefaultZone()
) {

    operator fun invoke(rawQuery: String): ParsedSearchQuery {
        val trimmed = rawQuery.trim()
        if (trimmed.isEmpty()) {
            return ParsedSearchQuery(rawQuery = rawQuery)
        }

        val hasPersian = trimmed.any { it in '\u0600'..'\u06FF' }

        return if (hasPersian) {
            parsePersian(rawQuery, trimmed)
        } else {
            parseEnglish(rawQuery, trimmed)
        }
    }

    private fun parsePersian(rawQuery: String, trimmed: String): ParsedSearchQuery {
        val normalized = DigitNormalizer.toAsciiDigits(PersianNormalizer.normalize(trimmed))
        var workingText = " $normalized "
        val chips = mutableListOf<SearchFilterChip>()

        var priority: Priority? = null
        var status: TaskFilter.Status = TaskFilter.Status.ALL
        var dateFilter: TaskFilter.DateFilter = TaskFilter.DateFilter.ALL
        var isRecurringOnly = false

        // 1. Priority
        val highPriorityMatches = listOf("خیلی مهم", "با اولویت بالا", "اولویت بالا", "فوری", "مهم", "ضروری", "اورژانسی")
        val lowPriorityMatches = listOf("با اولویت پایین", "اولویت پایین", "کم اهمیت", "سر فرصت")

        for (phrase in highPriorityMatches) {
            if (containsWordOrPhrase(workingText, phrase)) {
                priority = Priority.HIGH
                chips.add(SearchFilterChip("priority_high", R.string.search_chip_important, null, SearchChipType.PRIORITY))
                workingText = removePhrase(workingText, phrase)
                break
            }
        }
        if (priority == null) {
            for (phrase in lowPriorityMatches) {
                if (containsWordOrPhrase(workingText, phrase)) {
                    priority = Priority.LOW
                    chips.add(SearchFilterChip("priority_low", R.string.search_chip_low_priority, null, SearchChipType.PRIORITY))
                    workingText = removePhrase(workingText, phrase)
                    break
                }
            }
        }

        // 2. Status
        val overdueMatches = listOf("عقب افتاده", "عقب‌افتاده", "عقب مونده", "عقب‌مونده", "موعد گذشته", "گذشته")
        val completedMatches = listOf("انجام شده", "انجام‌شده", "تکمیل شده", "تکمیل‌شده", "تمام شده", "تمام‌شده", "پایان یافته")
        val incompleteMatches = listOf("انجام نشده", "انجام‌نشده", "تکمیل نشده", "تکمیل‌نشده", "ناتمام", "انجام‌نشد", "باقیمانده", "باقی‌مانده")

        for (phrase in overdueMatches) {
            if (containsWordOrPhrase(workingText, phrase)) {
                status = TaskFilter.Status.OVERDUE
                chips.add(SearchFilterChip("status_overdue", R.string.search_chip_overdue, null, SearchChipType.STATUS))
                workingText = removePhrase(workingText, phrase)
                break
            }
        }
        if (status == TaskFilter.Status.ALL) {
            for (phrase in incompleteMatches) {
                if (containsWordOrPhrase(workingText, phrase)) {
                    status = TaskFilter.Status.ACTIVE
                    chips.add(SearchFilterChip("status_incomplete", R.string.search_chip_incomplete, null, SearchChipType.STATUS))
                    workingText = removePhrase(workingText, phrase)
                    break
                }
            }
        }
        if (status == TaskFilter.Status.ALL) {
            for (phrase in completedMatches) {
                if (containsWordOrPhrase(workingText, phrase)) {
                    status = TaskFilter.Status.COMPLETED
                    chips.add(SearchFilterChip("status_completed", R.string.search_chip_completed, null, SearchChipType.STATUS))
                    workingText = removePhrase(workingText, phrase)
                    break
                }
            }
        }

        // 3. Date Scope
        val thisWeekMatches = listOf("این هفته", "هفته جاری", "هفته فعلی")
        val thisMonthMatches = listOf("این ماه", "ماه جاری", "ماه فعلی")
        val tomorrowMatches = listOf("فردا", "پس فردا", "پس‌فردا")
        val todayMatches = listOf("امروز", "امروزها", "امروزی")

        for (phrase in thisWeekMatches) {
            if (containsWordOrPhrase(workingText, phrase)) {
                dateFilter = TaskFilter.DateFilter.THIS_WEEK
                chips.add(SearchFilterChip("date_this_week", R.string.search_chip_this_week, null, SearchChipType.DATE))
                workingText = removePhrase(workingText, phrase)
                break
            }
        }
        if (dateFilter == TaskFilter.DateFilter.ALL) {
            for (phrase in thisMonthMatches) {
                if (containsWordOrPhrase(workingText, phrase)) {
                    dateFilter = TaskFilter.DateFilter.THIS_MONTH
                    chips.add(SearchFilterChip("date_this_month", R.string.search_chip_this_month, null, SearchChipType.DATE))
                    workingText = removePhrase(workingText, phrase)
                    break
                }
            }
        }
        if (dateFilter == TaskFilter.DateFilter.ALL) {
            for (phrase in tomorrowMatches) {
                if (containsWordOrPhrase(workingText, phrase)) {
                    dateFilter = TaskFilter.DateFilter.TOMORROW
                    chips.add(SearchFilterChip("date_tomorrow", R.string.search_chip_tomorrow, null, SearchChipType.DATE))
                    workingText = removePhrase(workingText, phrase)
                    break
                }
            }
        }
        if (dateFilter == TaskFilter.DateFilter.ALL) {
            for (phrase in todayMatches) {
                if (containsWordOrPhrase(workingText, phrase)) {
                    dateFilter = TaskFilter.DateFilter.TODAY
                    chips.add(SearchFilterChip("date_today", R.string.search_chip_today, null, SearchChipType.DATE))
                    workingText = removePhrase(workingText, phrase)
                    break
                }
            }
        }

        // 4. Recurrence
        val recurringMatches = listOf("تکرارشونده", "تکرار شونده", "تکراری", "دوره‌ای", "دوره ای")
        for (phrase in recurringMatches) {
            if (containsWordOrPhrase(workingText, phrase)) {
                isRecurringOnly = true
                chips.add(SearchFilterChip("recurring", R.string.search_chip_recurring, null, SearchChipType.RECURRING))
                workingText = removePhrase(workingText, phrase)
                break
            }
        }

        // 5. Strip Container Phrases & Stopwords
        val containerPhrases = listOf(
            "کارهای مربوط به", "کار های مربوط به", "کارهای مربوط", "کار های مربوط",
            "کارهای", "کار های", "کارها", "کار ها", "کار", "مربوط به", "درباره", "مورد"
        )
        for (phrase in containerPhrases) {
            workingText = removePhrase(workingText, phrase)
        }

        // 6. Clean Residual Keyword
        val cleanKeyword = sanitizeKeyword(workingText)

        // If structured chips were found or container phrases were stripped, attach a keyword chip
        if (cleanKeyword.isNotBlank() && (chips.isNotEmpty() || trimmed != cleanKeyword)) {
            chips.add(SearchFilterChip("keyword_$cleanKeyword", 0, cleanKeyword, SearchChipType.KEYWORD))
        }

        val taskFilter = TaskFilter(
            status = status,
            priorities = if (priority != null) setOf(priority) else emptySet(),
            dateFilter = dateFilter,
            isRecurringOnly = isRecurringOnly
        )

        return ParsedSearchQuery(
            rawQuery = rawQuery,
            keyword = cleanKeyword,
            filter = taskFilter,
            chips = chips
        )
    }

    private fun parseEnglish(rawQuery: String, trimmed: String): ParsedSearchQuery {
        var workingText = " $trimmed "
        val chips = mutableListOf<SearchFilterChip>()

        var priority: Priority? = null
        var status: TaskFilter.Status = TaskFilter.Status.ALL
        var dateFilter: TaskFilter.DateFilter = TaskFilter.DateFilter.ALL
        var isRecurringOnly = false

        // 1. Priority
        val highPriorityRegex = Regex("""\b(urgent|asap|high priority|important|critical)\b""", RegexOption.IGNORE_CASE)
        val lowPriorityRegex = Regex("""\b(low priority|whenever|not urgent)\b""", RegexOption.IGNORE_CASE)

        if (highPriorityRegex.containsMatchIn(workingText)) {
            priority = Priority.HIGH
            chips.add(SearchFilterChip("priority_high", R.string.search_chip_important, null, SearchChipType.PRIORITY))
            workingText = highPriorityRegex.replace(workingText, " ")
        } else if (lowPriorityRegex.containsMatchIn(workingText)) {
            priority = Priority.LOW
            chips.add(SearchFilterChip("priority_low", R.string.search_chip_low_priority, null, SearchChipType.PRIORITY))
            workingText = lowPriorityRegex.replace(workingText, " ")
        }

        // 2. Status
        val overdueRegex = Regex("""\b(overdue|late|past due)\b""", RegexOption.IGNORE_CASE)
        val incompleteRegex = Regex("""\b(incomplete|unfinished|not completed|pending|active)\b""", RegexOption.IGNORE_CASE)
        val completedRegex = Regex("""\b(completed|done|finished)\b""", RegexOption.IGNORE_CASE)

        if (overdueRegex.containsMatchIn(workingText)) {
            status = TaskFilter.Status.OVERDUE
            chips.add(SearchFilterChip("status_overdue", R.string.search_chip_overdue, null, SearchChipType.STATUS))
            workingText = overdueRegex.replace(workingText, " ")
        } else if (incompleteRegex.containsMatchIn(workingText)) {
            status = TaskFilter.Status.ACTIVE
            chips.add(SearchFilterChip("status_incomplete", R.string.search_chip_incomplete, null, SearchChipType.STATUS))
            workingText = incompleteRegex.replace(workingText, " ")
        } else if (completedRegex.containsMatchIn(workingText)) {
            status = TaskFilter.Status.COMPLETED
            chips.add(SearchFilterChip("status_completed", R.string.search_chip_completed, null, SearchChipType.STATUS))
            workingText = completedRegex.replace(workingText, " ")
        }

        // 3. Date Scope
        val thisWeekRegex = Regex("""\b(this week|current week)\b""", RegexOption.IGNORE_CASE)
        val thisMonthRegex = Regex("""\b(this month|current month)\b""", RegexOption.IGNORE_CASE)
        val tomorrowRegex = Regex("""\b(tomorrow's|tomorrow)\b""", RegexOption.IGNORE_CASE)
        val todayRegex = Regex("""\b(today's|today|tonight)\b""", RegexOption.IGNORE_CASE)

        if (thisWeekRegex.containsMatchIn(workingText)) {
            dateFilter = TaskFilter.DateFilter.THIS_WEEK
            chips.add(SearchFilterChip("date_this_week", R.string.search_chip_this_week, null, SearchChipType.DATE))
            workingText = thisWeekRegex.replace(workingText, " ")
        } else if (thisMonthRegex.containsMatchIn(workingText)) {
            dateFilter = TaskFilter.DateFilter.THIS_MONTH
            chips.add(SearchFilterChip("date_this_month", R.string.search_chip_this_month, null, SearchChipType.DATE))
            workingText = thisMonthRegex.replace(workingText, " ")
        } else if (tomorrowRegex.containsMatchIn(workingText)) {
            dateFilter = TaskFilter.DateFilter.TOMORROW
            chips.add(SearchFilterChip("date_tomorrow", R.string.search_chip_tomorrow, null, SearchChipType.DATE))
            workingText = tomorrowRegex.replace(workingText, " ")
        } else if (todayRegex.containsMatchIn(workingText)) {
            dateFilter = TaskFilter.DateFilter.TODAY
            chips.add(SearchFilterChip("date_today", R.string.search_chip_today, null, SearchChipType.DATE))
            workingText = todayRegex.replace(workingText, " ")
        }

        // 4. Recurrence
        val recurringRegex = Regex("""\b(recurring|repeating|periodic|daily|weekly|monthly)\b""", RegexOption.IGNORE_CASE)
        if (recurringRegex.containsMatchIn(workingText)) {
            isRecurringOnly = true
            chips.add(SearchFilterChip("recurring", R.string.search_chip_recurring, null, SearchChipType.RECURRING))
            workingText = recurringRegex.replace(workingText, " ")
        }

        // 5. Strip Container Phrases & Stopwords
        val containerRegex = Regex("""\b(tasks?\s+related\s+to|tasks?\s+about|related\s+to|tasks?)\b""", RegexOption.IGNORE_CASE)
        workingText = containerRegex.replace(workingText, " ")

        // 6. Clean Residual Keyword
        val cleanKeyword = sanitizeKeyword(workingText)

        if (cleanKeyword.isNotBlank() && (chips.isNotEmpty() || trimmed != cleanKeyword)) {
            chips.add(SearchFilterChip("keyword_$cleanKeyword", 0, cleanKeyword, SearchChipType.KEYWORD))
        }

        val taskFilter = TaskFilter(
            status = status,
            priorities = if (priority != null) setOf(priority) else emptySet(),
            dateFilter = dateFilter,
            isRecurringOnly = isRecurringOnly
        )

        return ParsedSearchQuery(
            rawQuery = rawQuery,
            keyword = cleanKeyword,
            filter = taskFilter,
            chips = chips
        )
    }

    private fun containsWordOrPhrase(text: String, phrase: String): Boolean {
        val pattern = Regex("""(?<=^|\s)${Regex.escape(phrase)}(?=\s|$)""")
        return pattern.containsMatchIn(text)
    }

    private fun removePhrase(text: String, phrase: String): String {
        val pattern = Regex("""(?<=^|\s)${Regex.escape(phrase)}(?=\s|$)""")
        return pattern.replaceFirst(text, " ")
    }

    private fun sanitizeKeyword(input: String): String {
        return input
            .replace(Regex("""[?,.:;!"'#\(\)\[\]\{\}]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}

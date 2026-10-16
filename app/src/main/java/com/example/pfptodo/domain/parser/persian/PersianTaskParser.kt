package com.example.pfptodo.domain.parser.persian

import com.example.pfptodo.core.util.DigitNormalizer
import com.example.pfptodo.core.util.JalaliCalendarConverter
import com.example.pfptodo.core.util.PersianNormalizer
import com.example.pfptodo.domain.model.ExtractedToken
import com.example.pfptodo.domain.model.ParsedTask
import com.example.pfptodo.domain.model.Priority
import com.example.pfptodo.domain.model.RecurrenceRule
import com.example.pfptodo.domain.model.TokenType
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

object PersianTaskParser {

    private val TAG_REGEX = Regex("""#([\p{L}\p{N}_]+)""")
    private val JALALI_DATE_REGEX = Regex("""\b(\d{4})[/-](\d{1,2})[/-](\d{1,2})\b""")
    private val TIME_HALF_REGEX = Regex("""ساعت\s*(\d{1,2})\s*و\s*نیم(?:\s*(صبح|عصر|غروب|شب|بعدازظهر|بعد از ظهر))?""")

    fun parse(rawInput: String, clock: Clock): ParsedTask? {
        val zone = clock.zone ?: ZoneId.systemDefault()
        val now = LocalDate.now(clock)
        val tokens = mutableListOf<ExtractedToken>()

        val lines = rawInput.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val firstLine = if (lines.isNotEmpty()) lines.first() else rawInput
        val subtaskTitles = if (lines.size > 1) {
            lines.drop(1).map { line ->
                line.replace(Regex("""^([\-*•+]|\d+[\.\)])\s*"""), "").trim()
            }.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }

        // 1. Harmonize characters, ZWNJ, and digits
        val normalized = DigitNormalizer.toAsciiDigits(PersianNormalizer.normalize(firstLine.trim()))

        var workingText = normalized

        // Check if there's any Persian character or structure
        val hasPersian = rawInput.any { it in '\u0600'..'\u06FF' }
        if (!hasPersian && !rawInput.contains("#")) {
            return null
        }

        // 2. Extract Tags
        val tags = mutableListOf<String>()
        TAG_REGEX.findAll(workingText).forEach { match ->
            val tag = match.groupValues[1].trim()
            if (tag.isNotEmpty()) {
                tags.add(tag)
                tokens.add(ExtractedToken(TokenType.TAG, match.value, match.range))
            }
        }
        workingText = TAG_REGEX.replace(workingText, " ")

        // 3. Extract Priority
        var priority = Priority.MEDIUM
        val priorityHighMatches = listOf("خیلی مهم", "با اولویت بالا", "اولویت بالا", "فوری", "مهم", "ضروری", "اورژانسی")
        val priorityLowMatches = listOf("با اولویت پایین", "اولویت پایین", "کم اهمیت", "سر فرصت")
        val priorityMedMatches = listOf("با اولویت متوسط", "اولویت متوسط", "معمولی")

        for (phrase in priorityHighMatches) {
            val idx = workingText.indexOf(phrase)
            if (idx != -1) {
                priority = Priority.HIGH
                tokens.add(ExtractedToken(TokenType.PRIORITY, phrase, idx until (idx + phrase.length)))
                workingText = workingText.replaceFirst(phrase, " ")
                break
            }
        }
        if (priority == Priority.MEDIUM) {
            for (phrase in priorityLowMatches) {
                val idx = workingText.indexOf(phrase)
                if (idx != -1) {
                    priority = Priority.LOW
                    tokens.add(ExtractedToken(TokenType.PRIORITY, phrase, idx until (idx + phrase.length)))
                    workingText = workingText.replaceFirst(phrase, " ")
                    break
                }
            }
        }
        if (priority == Priority.MEDIUM) {
            for (phrase in priorityMedMatches) {
                val idx = workingText.indexOf(phrase)
                if (idx != -1) {
                    priority = Priority.MEDIUM
                    tokens.add(ExtractedToken(TokenType.PRIORITY, phrase, idx until (idx + phrase.length)))
                    workingText = workingText.replaceFirst(phrase, " ")
                    break
                }
            }
        }

        // 4. Extract Recurrence
        var recurrenceRule: RecurrenceRule = RecurrenceRule.None
        val dailyMatches = listOf("هر روز", "روزانه", "روزی یکبار")
        val weeklyMatches = listOf("هر هفته", "هفتگی", "هفته ای یکبار")
        val monthlyMatches = listOf("هر ماه", "ماهانه", "ماهی یکبار")
        val yearlyMatches = listOf("هر سال", "سالانه", "سالی یکبار")

        for (phrase in dailyMatches) {
            if (workingText.contains(phrase)) {
                recurrenceRule = RecurrenceRule.Daily
                tokens.add(ExtractedToken(TokenType.RECURRENCE, phrase, 0..0))
                workingText = workingText.replaceFirst(phrase, " ")
                break
            }
        }
        if (recurrenceRule == RecurrenceRule.None) {
            for (phrase in weeklyMatches) {
                if (workingText.contains(phrase)) {
                    recurrenceRule = RecurrenceRule.Weekly
                    tokens.add(ExtractedToken(TokenType.RECURRENCE, phrase, 0..0))
                    workingText = workingText.replaceFirst(phrase, " ")
                    break
                }
            }
        }
        if (recurrenceRule == RecurrenceRule.None) {
            for (phrase in monthlyMatches) {
                if (workingText.contains(phrase)) {
                    recurrenceRule = RecurrenceRule.Monthly
                    tokens.add(ExtractedToken(TokenType.RECURRENCE, phrase, 0..0))
                    workingText = workingText.replaceFirst(phrase, " ")
                    break
                }
            }
        }
        if (recurrenceRule == RecurrenceRule.None) {
            for (phrase in yearlyMatches) {
                if (workingText.contains(phrase)) {
                    recurrenceRule = RecurrenceRule.Yearly
                    tokens.add(ExtractedToken(TokenType.RECURRENCE, phrase, 0..0))
                    workingText = workingText.replaceFirst(phrase, " ")
                    break
                }
            }
        }

        // 5. Extract Explicit Dates (Jalali or ISO)
        var parsedDate: LocalDate? = null
        val jalaliMatch = JALALI_DATE_REGEX.find(workingText)
        if (jalaliMatch != null) {
            val y = jalaliMatch.groupValues[1].toInt()
            val m = jalaliMatch.groupValues[2].toInt()
            val d = jalaliMatch.groupValues[3].toInt()
            parsedDate = if (y in 1300..1500) {
                JalaliCalendarConverter.jalaliToGregorian(y, m, d)
            } else if (y >= 2000) {
                try { LocalDate.of(y, m, d) } catch (e: Exception) { null }
            } else null

            if (parsedDate != null) {
                tokens.add(ExtractedToken(TokenType.DATE, jalaliMatch.value, jalaliMatch.range))
                workingText = workingText.replaceFirst(jalaliMatch.value, " ")
            }
        }

        // Combined Relative Day + Period Keywords (e.g. فردا شب, فردا صبح, فردا عصر)
        var parsedTime: LocalTime? = null

        if (parsedDate == null) {
            when {
                workingText.contains("فردا شب") -> {
                    parsedDate = now.plusDays(1)
                    parsedTime = LocalTime.of(20, 0)
                    workingText = workingText.replaceFirst("فردا شب", " ")
                }
                workingText.contains("فردا صبح") -> {
                    parsedDate = now.plusDays(1)
                    parsedTime = LocalTime.of(9, 0)
                    workingText = workingText.replaceFirst("فردا صبح", " ")
                }
                workingText.contains("فردا ظهر") -> {
                    parsedDate = now.plusDays(1)
                    parsedTime = LocalTime.of(12, 0)
                    workingText = workingText.replaceFirst("فردا ظهر", " ")
                }
                workingText.contains("فردا عصر") || workingText.contains("فردا بعدازظهر") || workingText.contains("فردا بعد از ظهر") -> {
                    parsedDate = now.plusDays(1)
                    parsedTime = LocalTime.of(17, 0)
                    workingText = workingText.replaceFirst("فردا عصر", " ").replaceFirst("فردا بعدازظهر", " ").replaceFirst("فردا بعد از ظهر", " ")
                }
                workingText.contains("امشب") -> {
                    parsedDate = now
                    parsedTime = LocalTime.of(20, 0)
                    workingText = workingText.replaceFirst("امشب", " ")
                }
                workingText.contains("امروز صبح") -> {
                    parsedDate = now
                    parsedTime = LocalTime.of(9, 0)
                    workingText = workingText.replaceFirst("امروز صبح", " ")
                }
                workingText.contains("امروز عصر") -> {
                    parsedDate = now
                    parsedTime = LocalTime.of(17, 0)
                    workingText = workingText.replaceFirst("امروز عصر", " ")
                }
                workingText.contains("پس فردا") || workingText.contains("پس‌فردا") -> {
                    parsedDate = now.plusDays(2)
                    workingText = workingText.replaceFirst("پس فردا", " ").replaceFirst("پس‌فردا", " ")
                }
                workingText.contains("فردا") -> {
                    parsedDate = now.plusDays(1)
                    workingText = workingText.replaceFirst("فردا", " ")
                }
                workingText.contains("امروز") -> {
                    parsedDate = now
                    workingText = workingText.replaceFirst("امروز", " ")
                }
                workingText.contains("هفته بعد") || workingText.contains("هفته آینده") -> {
                    parsedDate = now.plusWeeks(1)
                    workingText = workingText.replaceFirst("هفته بعد", " ").replaceFirst("هفته آینده", " ")
                }
                workingText.contains("اول هفته") -> {
                    parsedDate = now.with(TemporalAdjusters.next(DayOfWeek.SATURDAY))
                    workingText = workingText.replaceFirst("اول هفته", " ")
                }
                workingText.contains("وسط هفته") -> {
                    parsedDate = now.with(TemporalAdjusters.next(DayOfWeek.TUESDAY))
                    workingText = workingText.replaceFirst("وسط هفته", " ")
                }
                workingText.contains("آخر هفته") -> {
                    // Next Thursday in Iran weekend
                    parsedDate = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.THURSDAY))
                    workingText = workingText.replaceFirst("آخر هفته", " ")
                }
            }
        }

        // Persian Weekdays
        if (parsedDate == null) {
            val weekdayMap = listOf(
                "چهارشنبه" to DayOfWeek.WEDNESDAY,
                "چهار شنبه" to DayOfWeek.WEDNESDAY,
                "پنجشنبه" to DayOfWeek.THURSDAY,
                "پنج شنبه" to DayOfWeek.THURSDAY,
                "سه‌شنبه" to DayOfWeek.TUESDAY,
                "سه شنبه" to DayOfWeek.TUESDAY,
                "دوشنبه" to DayOfWeek.MONDAY,
                "دو شنبه" to DayOfWeek.MONDAY,
                "یکشنبه" to DayOfWeek.SUNDAY,
                "یک شنبه" to DayOfWeek.SUNDAY,
                "جمعه" to DayOfWeek.FRIDAY,
                "شنبه" to DayOfWeek.SATURDAY
            )
            for ((name, dow) in weekdayMap) {
                val regex = Regex("""(?<=^|\s)$name(?=\s|$)""")
                if (regex.containsMatchIn(workingText)) {
                    parsedDate = now.with(TemporalAdjusters.next(dow))
                    workingText = regex.replaceFirst(workingText, " ")
                    break
                }
            }
        }

        // 6. Extract Time (if not already set by period)
        // Half hour matcher: "ساعت ۵ و نیم عصر"
        val halfMatch = TIME_HALF_REGEX.find(workingText)
        if (halfMatch != null) {
            var hour = halfMatch.groupValues[1].toIntOrNull() ?: 0
            val modifier = halfMatch.groupValues[2]
            if (modifier in listOf("عصر", "غروب", "شب", "بعدازظهر", "بعد از ظهر") && hour in 1..11) {
                hour += 12
            } else if (modifier.isEmpty() && hour in 1..6) {
                hour += 12
            }
            parsedTime = LocalTime.of(hour.coerceIn(0, 23), 30)
            workingText = workingText.replaceFirst(halfMatch.value, " ")
        }

        // Standard time matcher: "ساعت ۵", "ساعت ۱۷:۳۰", "ساعت ۹ شب"
        if (parsedTime == null) {
            val explicitTimeRegex = Regex("""(?:ساعت\s*)(\d{1,2})(?::(\d{2}))?(?:\s*(صبح|عصر|غروب|شب|بعدازظهر|بعد از ظهر|ظهر))?""")
            val timeMatch = explicitTimeRegex.find(workingText)
            if (timeMatch != null) {
                var hour = timeMatch.groupValues[1].toIntOrNull() ?: 0
                val minute = timeMatch.groupValues[2].toIntOrNull() ?: 0
                val modifier = timeMatch.groupValues[3]
                if (modifier in listOf("عصر", "غروب", "شب", "بعدازظهر", "بعد از ظهر") && hour in 1..11) {
                    hour += 12
                } else if (modifier.isEmpty() && hour in 1..6) {
                    hour += 12
                }
                parsedTime = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
                workingText = workingText.replaceFirst(timeMatch.value, " ")
            }
        }

        // Check for standalone general period words if no exact time was extracted
        if (parsedTime == null) {
            when {
                workingText.contains("صبح") -> {
                    parsedTime = LocalTime.of(9, 0)
                    workingText = workingText.replaceFirst("صبح", " ")
                }
                workingText.contains("ظهر") -> {
                    parsedTime = LocalTime.of(12, 0)
                    workingText = workingText.replaceFirst("ظهر", " ")
                }
                workingText.contains("عصر") || workingText.contains("بعدازظهر") || workingText.contains("بعد از ظهر") -> {
                    parsedTime = LocalTime.of(17, 0)
                    workingText = workingText.replaceFirst("عصر", " ").replaceFirst("بعدازظهر", " ").replaceFirst("بعد از ظهر", " ")
                }
                workingText.contains("غروب") -> {
                    parsedTime = LocalTime.of(19, 0)
                    workingText = workingText.replaceFirst("غروب", " ")
                }
                workingText.contains("شب") -> {
                    parsedTime = LocalTime.of(20, 0)
                    workingText = workingText.replaceFirst("شب", " ")
                }
            }
        }

        // 7. Check Reminder Keywords
        var hasExplicitReminder = false
        val reminderKeywords = listOf("یادم بنداز", "یادآوری کن", "هشدار بده", "یادآوری", "هشدار")
        for (kw in reminderKeywords) {
            if (workingText.contains(kw)) {
                hasExplicitReminder = true
                workingText = workingText.replaceFirst(kw, " ")
            }
        }

        // 8. Clean Title
        var cleanedTitle = workingText
        val stopPhrases = listOf(
            "با اولویت بالا", "با اولویت متوسط", "با اولویت پایین",
            "اولویت بالا", "اولویت متوسط", "اولویت پایین",
            "با اولویت", "اولویت", "تاریخ", "روز", "ساعت"
        )
        for (sw in stopPhrases) {
            cleanedTitle = cleanedTitle.replace(Regex("""(?<=^|\s)$sw(?=\s|$)"""), " ")
        }
        // Strip dangling prepositions left at start or end
        cleanedTitle = cleanedTitle.replace(Regex("""^\s*(در|برای|به|و)\s+"""), "")
        cleanedTitle = cleanedTitle.replace(Regex("""\s+(در|برای|به|و)\s*$"""), "")
        // Strip dangling punctuation (dashes, colons, Persian/English commas, periods)
        cleanedTitle = cleanedTitle.replace(Regex("""^[\s\-:،,.;؛!؟?]+|[\s\-:،,.;؛!؟?]+$"""), "")
        cleanedTitle = cleanedTitle.replace(Regex("""\s+"""), " ").trim()

        if (cleanedTitle.isEmpty()) {
            cleanedTitle = rawInput.trim()
        }

        val dueInstant = if (parsedDate != null) {
            val time = parsedTime ?: LocalTime.of(9, 0)
            parsedDate.atTime(time).atZone(zone).toInstant()
        } else null

        return ParsedTask(
            title = cleanedTitle,
            date = parsedDate,
            time = parsedTime,
            dueInstant = dueInstant,
            priority = priority,
            tags = tags,
            recurrenceRule = recurrenceRule,
            hasExplicitReminder = hasExplicitReminder,
            subtaskTitles = subtaskTitles,
            rawInput = rawInput
        )
    }
}

package com.example.pfptodo.domain.parser.english

import com.example.pfptodo.core.util.DigitNormalizer
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
import java.util.Locale

object EnglishTaskParser {

    private val TAG_REGEX = Regex("""#([A-Za-z0-9_]+)""")
    private val ISO_DATE_REGEX = Regex("""\b(\d{4})-(\d{1,2})-(\d{1,2})\b""")
    private val TIME_12H_REGEX = Regex("""\b(?:at\s*)?(\d{1,2})(?::(\d{2}))?\s*(am|pm)\b""", RegexOption.IGNORE_CASE)
    private val TIME_24H_REGEX = Regex("""\b(?:at\s*)(\d{1,2}):(\d{2})\b""", RegexOption.IGNORE_CASE)

    fun parse(rawInput: String, clock: Clock): ParsedTask {
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

        var workingText = DigitNormalizer.toAsciiDigits(firstLine.trim())

        // 1. Extract Tags
        val tags = mutableListOf<String>()
        TAG_REGEX.findAll(workingText).forEach { match ->
            val tag = match.groupValues[1].trim()
            if (tag.isNotEmpty()) {
                tags.add(tag)
                tokens.add(ExtractedToken(TokenType.TAG, match.value, match.range))
            }
        }
        workingText = TAG_REGEX.replace(workingText, " ")

        // 2. Extract Priority
        var priority = Priority.MEDIUM
        val priorityHighMatches = listOf("urgent", "asap", "high priority", "important", "critical")
        val priorityLowMatches = listOf("low priority", "whenever", "not urgent")
        val priorityMedMatches = listOf("medium priority", "normal priority")

        for (phrase in priorityHighMatches) {
            val regex = Regex("""\b$phrase\b""", RegexOption.IGNORE_CASE)
            if (regex.containsMatchIn(workingText)) {
                priority = Priority.HIGH
                tokens.add(ExtractedToken(TokenType.PRIORITY, phrase, 0..0))
                workingText = regex.replace(workingText, " ")
                break
            }
        }
        if (priority == Priority.MEDIUM) {
            for (phrase in priorityLowMatches) {
                val regex = Regex("""\b$phrase\b""", RegexOption.IGNORE_CASE)
                if (regex.containsMatchIn(workingText)) {
                    priority = Priority.LOW
                    tokens.add(ExtractedToken(TokenType.PRIORITY, phrase, 0..0))
                    workingText = regex.replace(workingText, " ")
                    break
                }
            }
        }
        if (priority == Priority.MEDIUM) {
            for (phrase in priorityMedMatches) {
                val regex = Regex("""\b$phrase\b""", RegexOption.IGNORE_CASE)
                if (regex.containsMatchIn(workingText)) {
                    priority = Priority.MEDIUM
                    tokens.add(ExtractedToken(TokenType.PRIORITY, phrase, 0..0))
                    workingText = regex.replace(workingText, " ")
                    break
                }
            }
        }

        // 3. Extract Recurrence
        var recurrenceRule: RecurrenceRule = RecurrenceRule.None
        val dailyRegex = Regex("""\b(every day|daily)\b""", RegexOption.IGNORE_CASE)
        val weeklyRegex = Regex("""\b(every week|weekly|every (monday|tuesday|wednesday|thursday|friday|saturday|sunday))\b""", RegexOption.IGNORE_CASE)
        val monthlyRegex = Regex("""\b(every month|monthly)\b""", RegexOption.IGNORE_CASE)
        val yearlyRegex = Regex("""\b(every year|yearly|annually)\b""", RegexOption.IGNORE_CASE)

        if (dailyRegex.containsMatchIn(workingText)) {
            recurrenceRule = RecurrenceRule.Daily
            workingText = dailyRegex.replace(workingText, " ")
        } else if (weeklyRegex.containsMatchIn(workingText)) {
            recurrenceRule = RecurrenceRule.Weekly
            workingText = weeklyRegex.replace(workingText, " ")
        } else if (monthlyRegex.containsMatchIn(workingText)) {
            recurrenceRule = RecurrenceRule.Monthly
            workingText = monthlyRegex.replace(workingText, " ")
        } else if (yearlyRegex.containsMatchIn(workingText)) {
            recurrenceRule = RecurrenceRule.Yearly
            workingText = yearlyRegex.replace(workingText, " ")
        }

        // 4. Extract Explicit Dates (ISO: YYYY-MM-DD)
        var parsedDate: LocalDate? = null
        val isoMatch = ISO_DATE_REGEX.find(workingText)
        if (isoMatch != null) {
            val y = isoMatch.groupValues[1].toInt()
            val m = isoMatch.groupValues[2].toInt()
            val d = isoMatch.groupValues[3].toInt()
            try {
                parsedDate = LocalDate.of(y, m, d)
                tokens.add(ExtractedToken(TokenType.DATE, isoMatch.value, isoMatch.range))
                workingText = workingText.replaceFirst(isoMatch.value, " ")
            } catch (e: Exception) {
                // Ignore invalid dates
            }
        }

        // Relative Dates
        if (parsedDate == null) {
            val dayAfterTomorrowRegex = Regex("""\bday after tomorrow\b""", RegexOption.IGNORE_CASE)
            val tomorrowRegex = Regex("""\btomorrow\b""", RegexOption.IGNORE_CASE)
            val todayRegex = Regex("""\btoday\b""", RegexOption.IGNORE_CASE)
            val tonightRegex = Regex("""\btonight\b""", RegexOption.IGNORE_CASE)
            val nextWeekRegex = Regex("""\bnext week\b""", RegexOption.IGNORE_CASE)
            val weekendRegex = Regex("""\b(this weekend|next weekend)\b""", RegexOption.IGNORE_CASE)

            when {
                dayAfterTomorrowRegex.containsMatchIn(workingText) -> {
                    parsedDate = now.plusDays(2)
                    workingText = dayAfterTomorrowRegex.replace(workingText, " ")
                }
                tomorrowRegex.containsMatchIn(workingText) -> {
                    parsedDate = now.plusDays(1)
                    workingText = tomorrowRegex.replace(workingText, " ")
                }
                todayRegex.containsMatchIn(workingText) -> {
                    parsedDate = now
                    workingText = todayRegex.replace(workingText, " ")
                }
                tonightRegex.containsMatchIn(workingText) -> {
                    parsedDate = now
                    workingText = tonightRegex.replace(workingText, " ")
                }
                nextWeekRegex.containsMatchIn(workingText) -> {
                    parsedDate = now.plusWeeks(1)
                    workingText = nextWeekRegex.replace(workingText, " ")
                }
                weekendRegex.containsMatchIn(workingText) -> {
                    parsedDate = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
                    workingText = weekendRegex.replace(workingText, " ")
                }
            }
        }

        // English Weekdays
        if (parsedDate == null) {
            val weekdayRegex = Regex("""\b(?:next\s+|this\s+)?(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""", RegexOption.IGNORE_CASE)
            val match = weekdayRegex.find(workingText)
            if (match != null) {
                val dowName = match.groupValues[1].uppercase(Locale.US)
                val dow = DayOfWeek.valueOf(dowName)
                parsedDate = now.with(TemporalAdjusters.next(dow))
                workingText = workingText.replaceRange(match.range, " ")
            }
        }

        // 5. Extract Time
        var parsedTime: LocalTime? = null

        // 12h time (e.g. 5 PM, 9:30 am, at 11pm)
        val time12Match = TIME_12H_REGEX.find(workingText)
        if (time12Match != null) {
            var hour = time12Match.groupValues[1].toIntOrNull() ?: 0
            val minute = time12Match.groupValues[2].toIntOrNull() ?: 0
            val amPm = time12Match.groupValues[3].uppercase(Locale.US)
            if (amPm == "PM" && hour in 1..11) {
                hour += 12
            } else if (amPm == "AM" && hour == 12) {
                hour = 0
            }
            parsedTime = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
            workingText = workingText.replaceRange(time12Match.range, " ")
        }

        // 24h time (e.g. at 17:00)
        if (parsedTime == null) {
            val time24Match = TIME_24H_REGEX.find(workingText)
            if (time24Match != null) {
                val hour = time24Match.groupValues[1].toIntOrNull() ?: 0
                val minute = time24Match.groupValues[2].toIntOrNull() ?: 0
                parsedTime = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
                workingText = workingText.replaceRange(time24Match.range, " ")
            }
        }

        // General Periods (e.g. in the morning, in the evening)
        if (parsedTime == null) {
            val morningRegex = Regex("""\bin the morning\b""", RegexOption.IGNORE_CASE)
            val noonRegex = Regex("""\b(at noon|noon)\b""", RegexOption.IGNORE_CASE)
            val afternoonRegex = Regex("""\bin the afternoon\b""", RegexOption.IGNORE_CASE)
            val eveningRegex = Regex("""\bin the evening\b""", RegexOption.IGNORE_CASE)
            val nightRegex = Regex("""\bat night\b""", RegexOption.IGNORE_CASE)

            when {
                morningRegex.containsMatchIn(workingText) -> {
                    parsedTime = LocalTime.of(9, 0)
                    workingText = morningRegex.replace(workingText, " ")
                }
                noonRegex.containsMatchIn(workingText) -> {
                    parsedTime = LocalTime.of(12, 0)
                    workingText = noonRegex.replace(workingText, " ")
                }
                afternoonRegex.containsMatchIn(workingText) -> {
                    parsedTime = LocalTime.of(14, 0)
                    workingText = afternoonRegex.replace(workingText, " ")
                }
                eveningRegex.containsMatchIn(workingText) -> {
                    parsedTime = LocalTime.of(18, 0)
                    workingText = eveningRegex.replace(workingText, " ")
                }
                nightRegex.containsMatchIn(workingText) || rawInput.contains("tonight", ignoreCase = true) -> {
                    parsedTime = LocalTime.of(20, 0)
                    workingText = nightRegex.replace(workingText, " ")
                }
            }
        }

        // 6. Check Reminder Keywords
        var hasExplicitReminder = false
        val reminderRegex = Regex("""\b(remind me|set a reminder|reminder)\b""", RegexOption.IGNORE_CASE)
        if (reminderRegex.containsMatchIn(workingText)) {
            hasExplicitReminder = true
            workingText = reminderRegex.replace(workingText, " ")
        }

        // 7. Clean Title
        var cleanedTitle = workingText
            .replace(Regex("""\b(at|on|by|for|with priority|with|priority)\b""", RegexOption.IGNORE_CASE), " ")
            .trim()

        // Strip dangling prepositions & punctuation at start/end
        cleanedTitle = cleanedTitle.replace(Regex("""^[\s\-:;,.]+|[\s\-:;,.]+$"""), "")
        cleanedTitle = cleanedTitle.replace(Regex("""\s+"""), " ").trim()

        if (cleanedTitle.isEmpty()) {
            cleanedTitle = rawInput.trim()
        } else {
            // Capitalize first character
            cleanedTitle = cleanedTitle.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
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

package com.example.pfptodo.domain.model

sealed class RecurrenceRule {
    object None : RecurrenceRule()
    object Daily : RecurrenceRule()
    object Weekly : RecurrenceRule()
    object Monthly : RecurrenceRule()
    object Yearly : RecurrenceRule()
    data class Interval(val days: Int) : RecurrenceRule()

    companion object {
        fun fromString(rule: String?): RecurrenceRule {
            return when (rule?.uppercase()) {
                null, "NONE", "" -> None
                "DAILY" -> Daily
                "WEEKLY" -> Weekly
                "MONTHLY" -> Monthly
                "YEARLY" -> Yearly
                else -> {
                    if (rule.startsWith("INTERVAL=")) {
                        val interval = rule.substringAfter("=").toIntOrNull() ?: 1
                        // An interval below 1 day is not a usable rule: treat it
                        // as no recurrence instead of a rule that can never advance.
                        if (interval < 1) None else Interval(interval)
                    } else None
                }
            }
        }
    }

    override fun toString(): String {
        return when (this) {
            None -> "NONE"
            Daily -> "DAILY"
            Weekly -> "WEEKLY"
            Monthly -> "MONTHLY"
            Yearly -> "YEARLY"
            is Interval -> "INTERVAL=$days"
        }
    }
}

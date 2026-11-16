package com.isotjs.todosian.utils

import java.time.DayOfWeek

enum class RepeatUnit { DAY, WEEK, MONTH, YEAR }

/** Which day a monthly rule lands on. */
sealed interface MonthDay {
    /** The same day of the month as the current date. */
    data object Same : MonthDay

    data class Nth(val day: Int) : MonthDay

    data object Last : MonthDay
}

/**
 * A recurrence the Repeat picker can show, written as Obsidian Tasks text that [RecurrenceEngine]
 * understands. Rules outside this shape (e.g. "every weekday") are edited as free text.
 */
data class RepeatRule(
    val unit: RepeatUnit,
    val interval: Int = 1,
    /** Only for [RepeatUnit.WEEK]; empty means the same weekday as the current date. */
    val weekdays: Set<DayOfWeek> = emptySet(),
    /** Only for [RepeatUnit.MONTH] with an interval of 1; the engine has no "every N months on the …". */
    val monthDay: MonthDay = MonthDay.Same,
    val whenDone: Boolean = false,
) {
    fun toText(): String {
        val every = when (unit) {
            RepeatUnit.DAY -> plural("day")
            RepeatUnit.WEEK -> plural("week")
            RepeatUnit.MONTH -> plural("month")
            RepeatUnit.YEAR -> plural("year")
        }
        val on = when {
            unit == RepeatUnit.WEEK && weekdays.isNotEmpty() ->
                " on " + weekdays.sorted().joinToString(", ") { it.englishName() }
            unit == RepeatUnit.MONTH && interval == 1 -> when (val day = monthDay) {
                MonthDay.Same -> ""
                is MonthDay.Nth -> " on the ${ordinal(day.day)}"
                MonthDay.Last -> " on the last"
            }
            else -> ""
        }
        return "every $every$on" + if (whenDone) " when done" else ""
    }

    private fun plural(noun: String) = if (interval == 1) noun else "$interval ${noun}s"

    companion object {
        private val WHEN_DONE = Regex("""\s+when\s+done$""", RegexOption.IGNORE_CASE)
        private val BASE = Regex("""^every\s+(?:(\d+)\s+)?(day|week|month|year)s?(?:\s+on\s+(.+))?$""", RegexOption.IGNORE_CASE)
        private val NTH = Regex("""^the\s+(\d{1,2})(?:st|nd|rd|th)$""", RegexOption.IGNORE_CASE)
        private val LAST = Regex("""^the\s+last$""", RegexOption.IGNORE_CASE)

        /** Reads [text] back into a rule, or null when the picker can't represent it exactly. */
        fun parse(text: String?): RepeatRule? {
            val trimmed = text?.trim().orEmpty()
            if (trimmed.isEmpty()) return null
            val whenDone = WHEN_DONE.containsMatchIn(trimmed)
            val match = BASE.matchEntire(trimmed.replace(WHEN_DONE, "")) ?: return null
            val interval = match.groupValues[1].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 1
            if (interval < 1) return null
            val unit = RepeatUnit.valueOf(match.groupValues[2].uppercase())
            val on = match.groupValues[3].trim()
            val rule = RepeatRule(unit = unit, interval = interval, whenDone = whenDone)
            if (on.isEmpty()) return rule
            return when (unit) {
                RepeatUnit.WEEK -> {
                    val days = on.split(',').map { name -> dayFromName(name.trim()) ?: return null }
                    rule.copy(weekdays = days.toSet())
                }
                RepeatUnit.MONTH -> {
                    if (interval != 1) return null
                    val nth = NTH.matchEntire(on)?.groupValues?.get(1)?.toInt()
                    when {
                        nth != null && nth in 1..31 -> rule.copy(monthDay = MonthDay.Nth(nth))
                        LAST.matches(on) -> rule.copy(monthDay = MonthDay.Last)
                        else -> null
                    }
                }
                else -> null
            }
        }

        private fun dayFromName(name: String): DayOfWeek? =
            DayOfWeek.entries.firstOrNull {
                name.equals(it.englishName(), ignoreCase = true) ||
                    name.equals(it.englishName().take(3), ignoreCase = true)
            }

        private fun DayOfWeek.englishName(): String = name.lowercase().replaceFirstChar { it.uppercase() }

        private fun ordinal(day: Int): String {
            val suffix = when {
                day % 100 in 11..13 -> "th"
                day % 10 == 1 -> "st"
                day % 10 == 2 -> "nd"
                day % 10 == 3 -> "rd"
                else -> "th"
            }
            return "$day$suffix"
        }
    }
}

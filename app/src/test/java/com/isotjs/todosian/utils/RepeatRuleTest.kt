package com.isotjs.todosian.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class RepeatRuleTest {
    @Test
    fun toText_writes_the_obsidian_tasks_phrasing() {
        assertEquals("every day", RepeatRule(RepeatUnit.DAY).toText())
        assertEquals("every 2 days when done", RepeatRule(RepeatUnit.DAY, interval = 2, whenDone = true).toText())
        assertEquals(
            "every week on Monday, Saturday",
            RepeatRule(RepeatUnit.WEEK, weekdays = setOf(DayOfWeek.SATURDAY, DayOfWeek.MONDAY)).toText(),
        )
        assertEquals("every 3 months", RepeatRule(RepeatUnit.MONTH, interval = 3).toText())
        assertEquals("every month on the 22nd", RepeatRule(RepeatUnit.MONTH, monthDay = MonthDay.Nth(22)).toText())
        assertEquals("every month on the 11th", RepeatRule(RepeatUnit.MONTH, monthDay = MonthDay.Nth(11)).toText())
        assertEquals("every month on the last", RepeatRule(RepeatUnit.MONTH, monthDay = MonthDay.Last).toText())
        assertEquals("every year", RepeatRule(RepeatUnit.YEAR).toText())
    }

    @Test
    fun parse_reads_rules_from_the_vault() {
        assertEquals(RepeatRule(RepeatUnit.WEEK, whenDone = true), RepeatRule.parse("every week when done"))
        assertEquals(RepeatRule(RepeatUnit.DAY, interval = 2, whenDone = true), RepeatRule.parse("every 2 days when done"))
        assertEquals(
            RepeatRule(RepeatUnit.WEEK, weekdays = setOf(DayOfWeek.SATURDAY)),
            RepeatRule.parse("every week on Saturday"),
        )
        assertEquals(RepeatRule(RepeatUnit.MONTH, interval = 3), RepeatRule.parse("every 3 months"))
        assertEquals(RepeatRule(RepeatUnit.MONTH, monthDay = MonthDay.Nth(1)), RepeatRule.parse("Every month on the 1st"))
    }

    @Test
    fun parse_returns_null_for_rules_the_picker_cannot_show() {
        assertNull(RepeatRule.parse("every weekday"))
        assertNull(RepeatRule.parse("every 2 months on the 15th"))
        assertNull(RepeatRule.parse("every week on Funday"))
        assertNull(RepeatRule.parse("every month on the last friday"))
        assertNull(RepeatRule.parse(""))
        assertNull(RepeatRule.parse(null))
    }

    @Test
    fun every_rule_the_picker_writes_round_trips_and_is_understood_by_the_engine() {
        val rules = listOf(
            RepeatRule(RepeatUnit.DAY, interval = 5),
            RepeatRule(RepeatUnit.WEEK, interval = 2, weekdays = setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY)),
            RepeatRule(RepeatUnit.MONTH, monthDay = MonthDay.Nth(31), whenDone = true),
            RepeatRule(RepeatUnit.MONTH, monthDay = MonthDay.Last),
            RepeatRule(RepeatUnit.YEAR, interval = 2),
        )
        for (rule in rules) {
            assertEquals(rule, RepeatRule.parse(rule.toText()))
            assertNotNull(
                rule.toText(),
                RecurrenceEngine.nextOccurrence(
                    ruleText = rule.toText(),
                    dueDate = "2026-11-16",
                    scheduledDate = null,
                    startDate = null,
                    today = LocalDate.of(2026, 11, 16),
                ),
            )
        }
    }
}

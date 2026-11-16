package com.isotjs.todosian.ui.dailyfocus

import com.isotjs.todosian.data.model.Todo
import com.isotjs.todosian.data.settings.DailyFocusMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class AgendaRangeTest {
    private val monday = LocalDate.of(2026, 11, 16)
    private val sunday = LocalDate.of(2026, 11, 22)

    private fun task(due: String, status: Char = ' ') =
        Todo(id = due, text = "t", isDone = false, lineIndex = 0, dueDate = due, status = status)

    private fun inWeek(todo: Todo, today: LocalDate, firstDay: DayOfWeek = DayOfWeek.MONDAY) =
        matchesAgenda(todo, AgendaRange.WEEK, DailyFocusMode.TODAY, today, firstDay)

    @Test
    fun endOfWeek_respects_first_day_of_week() {
        assertEquals(sunday, endOfWeek(monday, DayOfWeek.MONDAY))
        assertEquals(sunday, endOfWeek(sunday, DayOfWeek.MONDAY))
        assertEquals(LocalDate.of(2026, 11, 21), endOfWeek(monday, DayOfWeek.SUNDAY))
        assertEquals(LocalDate.of(2026, 11, 28), endOfWeek(sunday, DayOfWeek.SUNDAY))
    }

    @Test
    fun week_covers_tomorrow_through_end_of_next_week() {
        assertFalse(inWeek(task("2026-11-16"), monday))
        assertTrue(inWeek(task("2026-11-17"), monday))
        assertTrue(inWeek(task("2026-11-29"), monday))
        assertFalse(inWeek(task("2026-11-30"), monday))
    }

    @Test
    fun week_on_sunday_still_reaches_into_next_week() {
        assertTrue(inWeek(task("2026-11-23"), sunday))
        assertTrue(inWeek(task("2026-11-29"), sunday))
        assertFalse(inWeek(task("2026-11-30"), sunday))
    }

    @Test
    fun parked_statuses_and_undated_tasks_are_left_out() {
        assertFalse(inWeek(task("2026-11-17", status = 'w'), monday))
        assertFalse(inWeek(task("2026-11-17", status = 's'), monday))
        assertTrue(inWeek(task("2026-11-17", status = '/'), monday))
        assertFalse(inWeek(Todo(id = "x", text = "t", isDone = false, lineIndex = 0), monday))
    }

    @Test
    fun today_range_follows_focus_mode() {
        val overdue = task("2026-11-15")
        val dueToday = task("2026-11-16")
        assertFalse(matchesAgenda(overdue, AgendaRange.TODAY, DailyFocusMode.TODAY, monday, DayOfWeek.MONDAY))
        assertTrue(matchesAgenda(dueToday, AgendaRange.TODAY, DailyFocusMode.TODAY, monday, DayOfWeek.MONDAY))
        assertTrue(matchesAgenda(overdue, AgendaRange.TODAY, DailyFocusMode.TODAY_AND_OVERDUE, monday, DayOfWeek.MONDAY))
        assertFalse(matchesAgenda(dueToday, AgendaRange.TODAY, DailyFocusMode.OVERDUE, monday, DayOfWeek.MONDAY))
    }
}

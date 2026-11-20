package com.sumon.calendar.ui.month

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class MonthGridTest {

    private val october2026 = YearMonth.of(2026, 10)

    private fun leadingBlankCount(cells: List<MonthCell>) = cells.takeWhile { it.date == null }.count()

    @Test
    fun `anchor - first day of october 2026 is a thursday`() {
        assertEquals(DayOfWeek.THURSDAY, october2026.atDay(1).dayOfWeek)
    }

    @Test
    fun `sunday start pads four leading blanks and packs october into five whole weeks`() {
        val cells = buildMonthCells(october2026, DayOfWeek.SUNDAY)
        assertEquals(35, cells.size)
        assertEquals(4, leadingBlankCount(cells))
        assertEquals(LocalDate.of(2026, 10, 1), cells[4].date)
        assertEquals(LocalDate.of(2026, 10, 31), cells[34].date)
    }

    @Test
    fun `monday start shifts the calendar by one blank`() {
        val cells = buildMonthCells(october2026, DayOfWeek.MONDAY)
        assertEquals(3, leadingBlankCount(cells))
        assertEquals(LocalDate.of(2026, 10, 1), cells[3].date)
    }

    @Test
    fun `every result is a whole number of weeks`() {
        // Feb 2027 starts on a Monday with 28 days — Monday start: exactly 4 rows, no padding.
        val fourWeeks = buildMonthCells(YearMonth.of(2027, 2), DayOfWeek.MONDAY)
        assertEquals(0, leadingBlankCount(fourWeeks))
        assertEquals(28, fourWeeks.size)
        // Sunday start inserts one leading blank: 29 cells get trailing-padded to 35.
        val padded = buildMonthCells(YearMonth.of(2027, 2), DayOfWeek.SUNDAY)
        assertEquals(1, leadingBlankCount(padded))
        assertEquals(35, padded.size)
        assertEquals(0, padded.size % 7)
    }

    @Test
    fun `event colors mapping retains distinct colors and exact counts per day`() {
        val date1 = LocalDate.of(2026, 10, 15)
        val date2 = LocalDate.of(2026, 10, 16)
        val workColor = 0xFF4CAF50.toInt() // Green
        val personalColor = 0xFF2196F3.toInt() // Blue
        val familyColor = 0xFFFF9800.toInt() // Orange

        val events = listOf(
            com.sumon.calendar.data.Event(id = 1, title = "Team Standup", startDate = date1, color = workColor),
            com.sumon.calendar.data.Event(id = 2, title = "Dentist", startDate = date1, color = personalColor),
            com.sumon.calendar.data.Event(id = 3, title = "Dinner with Family", startDate = date1, color = familyColor),
            com.sumon.calendar.data.Event(id = 4, title = "Work Review", startDate = date2, color = workColor),
        )

        val map = mutableMapOf<LocalDate, MutableList<Int>>()
        events.forEach { event ->
            val color = if (event.color != null && event.color != 0) event.color else 0xFF4285F4.toInt()
            map.getOrPut(event.startDate) { mutableListOf() }.add(color)
        }

        // date1 has exactly 3 events with 3 distinct colors
        assertEquals(3, map[date1]?.size)
        assertEquals(listOf(workColor, personalColor, familyColor), map[date1])

        // date2 has exactly 1 event with workColor
        assertEquals(1, map[date2]?.size)
        assertEquals(listOf(workColor), map[date2])
    }
}

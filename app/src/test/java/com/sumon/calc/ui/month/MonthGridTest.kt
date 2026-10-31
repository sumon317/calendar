package com.sumon.calc.ui.month

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
}

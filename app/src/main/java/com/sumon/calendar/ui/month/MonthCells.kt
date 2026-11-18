package com.sumon.calendar.ui.month

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * One cell of the month grid. `date == null` marks a placeholder cell
 * (leading blanks before the 1st, padding after the last day).
 */
data class MonthCell(val date: LocalDate?)

/**
 * The visible structure of [month]: leading blanks according to
 * [firstDayOfWeek], then every day of the month, trailing-padded so the
 * result is always a whole number of 7-cell weeks.
 */
fun buildMonthCells(month: YearMonth, firstDayOfWeek: DayOfWeek): List<MonthCell> {
    val firstDay = month.atDay(1)
    val leadingBlanks = (firstDay.dayOfWeek.value - firstDayOfWeek.value + 7) % 7

    val cells = buildList {
        repeat(leadingBlanks) { add(MonthCell(date = null)) }
        for (dayOfMonth in 1..month.lengthOfMonth()) {
            add(MonthCell(date = month.atDay(dayOfMonth)))
        }
        while (size % 7 != 0) add(MonthCell(date = null))
    }
    return cells
}

/** Localized one-or-two-letter weekday labels in calendar order (e.g. Mon…Sun). */
fun weekdayLabels(firstDayOfWeek: DayOfWeek, locale: Locale): List<String> =
    (0..6).map { offset ->
        firstDayOfWeek.plus(offset.toLong())
            .getDisplayName(TextStyle.SHORT, locale)
    }

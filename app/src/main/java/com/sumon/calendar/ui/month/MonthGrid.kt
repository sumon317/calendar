package com.sumon.calendar.ui.month

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/**
 * Always renders every cell in [cells]; callers feed a full-week-multiple list
 * from [buildMonthCells], so rows never come up short.
 */
@Composable
fun MonthGrid(
    cells: List<MonthCell>,
    selectedDay: LocalDate,
    today: LocalDate,
    eventDays: Set<LocalDate>,
    onSelectDay: (LocalDate) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { cell ->
                    DayCell(
                        date = cell.date,
                        isSelected = cell.date == selectedDay,
                        isToday = cell.date == today,
                        hasEvents = cell.date in eventDays,
                        onSelectDay = onSelectDay,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate?,
    isSelected: Boolean,
    isToday: Boolean,
    hasEvents: Boolean,
    onSelectDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val circleColor = if (isSelected) colorScheme.primary else Color.Transparent
    val dayTextColor = when {
        isSelected -> colorScheme.onPrimary
        isToday -> colorScheme.primary
        else -> colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            // Today is marked with a primary ring so the current date is
            // identifiable at a glance, even when another day is selected.
            .then(
                if (isToday && !isSelected) {
                    Modifier.border(width = 2.dp, color = colorScheme.primary, shape = CircleShape)
                } else {
                    Modifier
                },
            )
            .background(circleColor)
            .clickable(enabled = date != null) { date?.let(onSelectDay) },
        contentAlignment = Alignment.Center,
    ) {
        if (date != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = dayTextColor,
                )
                if (hasEvents) {
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) colorScheme.onPrimary else colorScheme.primary,
                            ),
                    )
                }
            }
        }
    }
}

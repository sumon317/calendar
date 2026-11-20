package com.sumon.calendar.ui.month

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/**
 * Backward-compatible overload for callers passing a boolean [eventDays] set.
 */
@Composable
fun MonthGrid(
    cells: List<MonthCell>,
    selectedDay: LocalDate,
    today: LocalDate,
    eventDays: Set<LocalDate>,
    onSelectDay: (LocalDate) -> Unit,
) {
    val fallbackColors = remember(eventDays) {
        eventDays.associateWith { listOf(0xFF4285F4.toInt()) }
    }
    MonthGrid(
        cells = cells,
        selectedDay = selectedDay,
        today = today,
        eventColorsByDay = fallbackColors,
        onSelectDay = onSelectDay,
    )
}

/**
 * Always renders every cell in [cells]; callers feed a full-week-multiple list
 * from [buildMonthCells], so rows never come up short.
 * Each day shows the exact number of event/task dots matching their respective calendar colors.
 */
@Composable
fun MonthGrid(
    cells: List<MonthCell>,
    selectedDay: LocalDate,
    today: LocalDate,
    eventColorsByDay: Map<LocalDate, List<Int>>,
    onSelectDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { cell ->
                    val date = cell.date
                    DayCell(
                        date = date,
                        isSelected = date == selectedDay,
                        isToday = date == today,
                        eventColors = if (date != null) eventColorsByDay[date].orEmpty() else emptyList(),
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
    eventColors: List<Int>,
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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(2.dp),
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                    ),
                    color = dayTextColor,
                )
                if (eventColors.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    EventDots(
                        colors = eventColors,
                        isSelected = isSelected,
                    )
                }
            }
        }
    }
}

/**
 * Renders the exact number of dots corresponding to events/tasks on this day.
 * Each dot reflects its calendar or account color.
 * For multiple events, dots are neatly arranged into rows and scaled gracefully
 * so they fit comfortably inside the day cell.
 */
@Composable
private fun EventDots(
    colors: List<Int>,
    isSelected: Boolean,
) {
    val colorScheme = MaterialTheme.colorScheme
    val borderModifier = if (isSelected) {
        Modifier.border(0.5.dp, colorScheme.onPrimary.copy(alpha = 0.8f), CircleShape)
    } else {
        Modifier
    }

    when {
        // 1 to 4 dots: single horizontal row
        colors.size <= 4 -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                colors.forEach { colorInt ->
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .then(borderModifier)
                            .background(Color(colorInt)),
                    )
                }
            }
        }
        // 5 to 8 dots: two clean balanced rows
        colors.size <= 8 -> {
            val half = (colors.size + 1) / 2
            val row1 = colors.take(half)
            val row2 = colors.drop(half)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.5.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    row1.forEach { colorInt ->
                        Box(
                            modifier = Modifier
                                .size(3.5.dp)
                                .clip(CircleShape)
                                .then(borderModifier)
                                .background(Color(colorInt)),
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    row2.forEach { colorInt ->
                        Box(
                            modifier = Modifier
                                .size(3.5.dp)
                                .clip(CircleShape)
                                .then(borderModifier)
                                .background(Color(colorInt)),
                        )
                    }
                }
            }
        }
        // 9+ dots: two rows of up to 5 dots each (max 10 visible)
        else -> {
            val visible = colors.take(10)
            val half = (visible.size + 1) / 2
            val row1 = visible.take(half)
            val row2 = visible.drop(half)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    row1.forEach { colorInt ->
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .clip(CircleShape)
                                .then(borderModifier)
                                .background(Color(colorInt)),
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    row2.forEach { colorInt ->
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .clip(CircleShape)
                                .then(borderModifier)
                                .background(Color(colorInt)),
                        )
                    }
                }
            }
        }
    }
}

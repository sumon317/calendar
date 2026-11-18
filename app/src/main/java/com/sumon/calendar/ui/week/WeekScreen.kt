package com.sumon.calendar.ui.week

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sumon.calendar.domain.EventInstance
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val HOUR_HEIGHT = 60.dp

@Composable
fun WeekScreen(
    selectedDate: LocalDate,
    instances: List<EventInstance>,
    onEventClick: (EventInstance) -> Unit,
    onTimeSlotClick: (LocalDate, LocalTime) -> Unit,
    onSelectDay: (LocalDate) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now() }
    val scrollState = rememberScrollState()

    // Determine 7 days of the week starting from Monday
    val weekDays = remember(selectedDate) {
        val monday = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        (0..6).map { monday.plusDays(it.toLong()) }
    }

    val density = LocalDensity.current
    LaunchedEffect(selectedDate) {
        val targetScrollY = with(density) { (8 * HOUR_HEIGHT.toPx()).toInt() }
        scrollState.animateScrollTo(targetScrollY)
    }

    // Filter events for this week
    val weekStart = weekDays.first()
    val weekEnd = weekDays.last()

    val weekEvents = remember(instances, weekStart, weekEnd) {
        instances.filter { inst ->
            val instStart = if (inst.isAllDay) {
                Instant.ofEpochMilli(inst.startEpochMillis).atZone(ZoneOffset.UTC).toLocalDate()
            } else {
                Instant.ofEpochMilli(inst.startEpochMillis).atZone(zone).toLocalDate()
            }
            val instEnd = if (inst.isAllDay) {
                Instant.ofEpochMilli(inst.endEpochMillis - 1).atZone(ZoneOffset.UTC).toLocalDate()
            } else {
                Instant.ofEpochMilli(inst.endEpochMillis - 1).atZone(zone).toLocalDate()
            }
            !instEnd.isBefore(weekStart) && !instStart.isAfter(weekEnd)
        }
    }

    val (allDayWeekEvents, timedWeekEvents) = remember(weekEvents) {
        weekEvents.partition { it.isAllDay }
    }

    val monthYearFormatter = remember { DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()) }

    Column(modifier = modifier.fillMaxSize()) {
        // Week Navigation Header
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPreviousWeek) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Week")
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onToday),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "${weekDays.first().dayOfMonth} – ${weekDays.last().dayOfMonth} ${monthYearFormatter.format(weekDays.first())}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }
                IconButton(onClick = onNextWeek) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Week")
                }
            }
        }

        // Weekday Strip Columns Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Spacer for ruler width
            Spacer(Modifier.width(44.dp))

            weekDays.forEach { date ->
                val isToday = (date == today)
                val isSelected = (date == selectedDate)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelectDay(date) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = date.dayOfWeek.name.take(3),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(2.dp))
                    Surface(
                        shape = CircleShape,
                        color = if (isToday) MaterialTheme.colorScheme.primary else if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                                ),
                                color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = DividerDefaults.color.copy(alpha = 0.5f))

        // All Day Events Row
        if (allDayWeekEvents.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "ALL\nDAY",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(44.dp),
                        textAlign = TextAlign.Center,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        allDayWeekEvents.forEach { event ->
                            Card(
                                shape = RoundedCornerShape(4.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(event.color).copy(alpha = 0.3f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 1.dp)
                                    .clickable { onEventClick(event) },
                            ) {
                                Text(
                                    text = event.title.ifBlank { "(Untitled Event)" },
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
            HorizontalDivider()
        }

        // 24-Hour Vertical Grid
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
        ) {
            // Horizontal hour dividers and hour labels
            Column(modifier = Modifier.fillMaxWidth()) {
                for (hour in 0..23) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(HOUR_HEIGHT),
                    ) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = String.format(Locale.getDefault(), "%02d:00", hour),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                modifier = Modifier
                                    .width(44.dp)
                                    .padding(start = 6.dp, top = 2.dp),
                            )
                            Box(modifier = Modifier.weight(1f)) {
                                HorizontalDivider(
                                    color = DividerDefaults.color.copy(alpha = 0.25f),
                                    modifier = Modifier.align(Alignment.TopStart),
                                )
                            }
                        }
                    }
                }
            }

            // 7 Day Columns
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 44.dp),
            ) {
                weekDays.forEach { dayDate ->
                    val isDayToday = (dayDate == today)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(HOUR_HEIGHT * 24),
                    ) {
                        // Slot click detectors per hour
                        Column(modifier = Modifier.fillMaxSize()) {
                            for (h in 0..23) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(HOUR_HEIGHT)
                                        .clickable {
                                            onTimeSlotClick(dayDate, LocalTime.of(h, 0))
                                        },
                                )
                            }
                        }

                        // Day border line
                        VerticalDivider(
                            color = DividerDefaults.color.copy(alpha = 0.15f),
                            modifier = Modifier.align(Alignment.CenterEnd),
                        )

                        // Events for this day
                        val dayEvents = timedWeekEvents.filter { event ->
                            val startLocal = Instant.ofEpochMilli(event.startEpochMillis).atZone(zone).toLocalDate()
                            val endLocal = Instant.ofEpochMilli(event.endEpochMillis - 1).atZone(zone).toLocalDate()
                            !dayDate.isBefore(startLocal) && !dayDate.isAfter(endLocal)
                        }

                        dayEvents.forEach { event ->
                            val startZoned = Instant.ofEpochMilli(event.startEpochMillis).atZone(zone)
                            val endZoned = Instant.ofEpochMilli(event.endEpochMillis).atZone(zone)

                            val startMinute = if (startZoned.toLocalDate().isBefore(dayDate)) 0 else (startZoned.hour * 60 + startZoned.minute)
                            val endMinute = if (endZoned.toLocalDate().isAfter(dayDate)) 1440 else (endZoned.hour * 60 + endZoned.minute)
                            val duration = (endMinute - startMinute).coerceAtLeast(18)

                            val topOffset = HOUR_HEIGHT * (startMinute / 60f)
                            val blockHeight = HOUR_HEIGHT * (duration / 60f)

                            Card(
                                shape = RoundedCornerShape(4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(event.color).copy(alpha = 0.35f),
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset(y = topOffset)
                                    .height(blockHeight)
                                    .padding(horizontal = 1.dp, vertical = 0.5.dp)
                                    .clickable { onEventClick(event) },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(2.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(2.5.dp)
                                            .fillMaxHeight()
                                            .background(Color(event.color)),
                                    )
                                    Spacer(Modifier.width(2.dp))
                                    Text(
                                        text = event.title.ifBlank { "Event" },
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textDecoration = if (event.isCrossedOut) TextDecoration.LineThrough else null,
                                    )
                                }
                            }
                        }

                        // Current Time Red Line Indicator on Today
                        if (isDayToday) {
                            val now = LocalTime.now()
                            val nowMin = now.hour * 60 + now.minute
                            val nowOffset = HOUR_HEIGHT * (nowMin / 60f)
                            HorizontalDivider(
                                color = Color.Red,
                                thickness = 2.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset(y = nowOffset),
                            )
                        }
                    }
                }
            }
        }
    }
}

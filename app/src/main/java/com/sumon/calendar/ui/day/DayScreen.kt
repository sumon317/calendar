package com.sumon.calendar.ui.day

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sumon.calendar.domain.EventInstance
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HOUR_HEIGHT = 64.dp

@Composable
fun DayScreen(
    date: LocalDate,
    instances: List<EventInstance>,
    onEventClick: (EventInstance) -> Unit,
    onTimeSlotClick: (LocalDate, LocalTime) -> Unit,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now() }
    val scrollState = rememberScrollState()

    // Pre-scroll to 8:00 AM on initial load
    val density = LocalDensity.current
    LaunchedEffect(date) {
        val targetScrollY = with(density) { (8 * HOUR_HEIGHT.toPx()).toInt() }
        scrollState.animateScrollTo(targetScrollY)
    }

    // Filter events for this specific date
    val dayInstances = remember(instances, date) {
        instances.filter { inst ->
            if (inst.isAllDay) {
                val startUtc = Instant.ofEpochMilli(inst.startEpochMillis).atZone(ZoneOffset.UTC).toLocalDate()
                val endUtc = Instant.ofEpochMilli(inst.endEpochMillis - 1).atZone(ZoneOffset.UTC).toLocalDate()
                !date.isBefore(startUtc) && !date.isAfter(endUtc)
            } else {
                val startLocal = Instant.ofEpochMilli(inst.startEpochMillis).atZone(zone).toLocalDate()
                val endLocal = Instant.ofEpochMilli(inst.endEpochMillis - 1).atZone(zone).toLocalDate()
                !date.isBefore(startLocal) && !date.isAfter(endLocal)
            }
        }
    }

    val (allDayEvents, timedEvents) = remember(dayInstances) {
        dayInstances.partition { it.isAllDay }
    }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault()) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isCompact = maxWidth < 380.dp
        val rulerWidth = if (isCompact) 42.dp else 52.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // Day Navigation Header
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (isCompact) 6.dp else 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onPreviousDay,
                        modifier = if (isCompact) Modifier.size(36.dp) else Modifier.size(44.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous Day",
                            modifier = if (isCompact) Modifier.size(20.dp) else Modifier.size(24.dp),
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onToday),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = if (date == today) "Today" else date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = (if (isCompact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium).copy(fontWeight = FontWeight.Bold),
                            color = if (date == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = dateFormatter.format(date),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(
                        onClick = onNextDay,
                        modifier = if (isCompact) Modifier.size(36.dp) else Modifier.size(44.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next Day",
                            modifier = if (isCompact) Modifier.size(20.dp) else Modifier.size(24.dp),
                        )
                    }
                }
            }

            // All Day Events Bar
            if (allDayEvents.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = if (isCompact) 10.dp else 16.dp, vertical = 8.dp)) {
                        Text(
                            text = "ALL DAY",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        allDayEvents.forEach { event ->
                            Card(
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(event.color).copy(alpha = 0.25f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clickable { onEventClick(event) },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(event.color)),
                                    )
                                    Text(
                                        text = event.title.ifBlank { "(Untitled Event)" },
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textDecoration = if (event.isCrossedOut) TextDecoration.LineThrough else null,
                                    )
                                }
                            }
                        }
                    }
                }
                HorizontalDivider()
            }

            // 24-Hour Timeline Grid
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    for (hour in 0..23) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(HOUR_HEIGHT)
                                .clickable {
                                    onTimeSlotClick(date, LocalTime.of(hour, 0))
                                },
                        ) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                // Hour Ruler Label
                                Text(
                                    text = String.format(Locale.getDefault(), "%02d:00", hour),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = if (isCompact) 10.sp else 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier
                                        .width(rulerWidth)
                                        .padding(start = if (isCompact) 4.dp else 12.dp, top = 2.dp),
                                )
                                // Slot Horizontal Divider
                                Box(modifier = Modifier.weight(1f)) {
                                    HorizontalDivider(
                                        color = DividerDefaults.color.copy(alpha = 0.35f),
                                        modifier = Modifier.align(Alignment.TopStart),
                                    )
                                }
                            }
                        }
                    }
                }

                // Timed Event Blocks Layer
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = rulerWidth + 2.dp, end = if (isCompact) 6.dp else 12.dp),
                ) {
                    timedEvents.forEach { event ->
                        val startZoned = Instant.ofEpochMilli(event.startEpochMillis).atZone(zone)
                        val endZoned = Instant.ofEpochMilli(event.endEpochMillis).atZone(zone)

                        val startMinuteOfDay = if (startZoned.toLocalDate().isBefore(date)) 0 else (startZoned.hour * 60 + startZoned.minute)
                        val endMinuteOfDay = if (endZoned.toLocalDate().isAfter(date)) 1440 else (endZoned.hour * 60 + endZoned.minute)
                        val durationMinutes = (endMinuteOfDay - startMinuteOfDay).coerceAtLeast(20)

                        val topOffset = HOUR_HEIGHT * (startMinuteOfDay / 60f)
                        val blockHeight = HOUR_HEIGHT * (durationMinutes / 60f)

                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(event.color).copy(alpha = 0.22f),
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(y = topOffset)
                                .height(blockHeight)
                                .padding(vertical = 1.dp)
                                .clickable { onEventClick(event) },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color(event.color)),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = event.title.ifBlank { "(Untitled Event)" },
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textDecoration = if (event.isCrossedOut) TextDecoration.LineThrough else null,
                                    )
                                    val timeText = "${String.format(Locale.getDefault(), "%02d:%02d", startZoned.hour, startZoned.minute)} - ${String.format(Locale.getDefault(), "%02d:%02d", endZoned.hour, endZoned.minute)}"
                                    Text(
                                        text = if (!event.location.isNullOrBlank()) "$timeText · ${event.location}" else timeText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }

                    // Current Time Red Line Indicator
                    if (date == today) {
                        val now = LocalTime.now()
                        val nowMinute = now.hour * 60 + now.minute
                        val nowTopOffset = HOUR_HEIGHT * (nowMinute / 60f)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(y = nowTopOffset),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color.Red),
                            )
                            HorizontalDivider(
                                color = Color.Red,
                                thickness = 2.dp,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

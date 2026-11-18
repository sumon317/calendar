package com.sumon.calendar.ui.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sumon.calendar.domain.EventInstance
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AgendaScreen(
    instances: List<EventInstance>,
    selectedDay: LocalDate,
    onEventClick: (EventInstance) -> Unit,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now() }

    // Group events by local date
    val dayMap = remember(instances, selectedDay) {
        val map = sortedMapOf<LocalDate, MutableList<EventInstance>>()
        // Ensure at least 14 days from selectedDay exist in the agenda
        val startAnchor = if (selectedDay.isBefore(today)) selectedDay else today
        for (i in 0..30) {
            val d = startAnchor.plusDays(i.toLong())
            map[d] = mutableListOf()
        }

        instances.forEach { inst ->
            val eventDate = if (inst.isAllDay) {
                Instant.ofEpochMilli(inst.startEpochMillis).atZone(ZoneOffset.UTC).toLocalDate()
            } else {
                Instant.ofEpochMilli(inst.startEpochMillis).atZone(zone).toLocalDate()
            }
            if (map.containsKey(eventDate)) {
                map[eventDate]?.add(inst)
            } else if (!eventDate.isBefore(startAnchor) && !eventDate.isAfter(startAnchor.plusDays(60))) {
                map.getOrPut(eventDate) { mutableListOf() }.add(inst)
            }
        }
        map
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        dayMap.forEach { (date, events) ->
            item(key = "header-$date") {
                AgendaDayHeader(
                    date = date,
                    today = today,
                    onClick = { onDayClick(date) },
                )
            }

            if (events.isEmpty()) {
                item(key = "empty-$date") {
                    AgendaEmptyDayRow(
                        onClick = { onDayClick(date) },
                    )
                }
            } else {
                items(events, key = { "${it.eventId}-${it.startEpochMillis}" }) { event ->
                    AgendaEventRow(
                        event = event,
                        onClick = { onEventClick(event) },
                    )
                }
            }

            item(key = "spacer-$date") {
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun AgendaDayHeader(
    date: LocalDate,
    today: LocalDate,
    onClick: () -> Unit,
) {
    val relativeLabel = when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> null
    }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault()) }
    val formattedDate = dateFormatter.format(date)
    val text = if (relativeLabel != null) "$relativeLabel · $formattedDate" else formattedDate

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = if (date == today) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun AgendaEmptyDayRow(onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Coffee,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "No events · Tap to add",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun AgendaEventRow(
    event: EventInstance,
    onClick: () -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()) }

    val timeText = if (event.isAllDay) {
        "All day"
    } else {
        val startLocal = Instant.ofEpochMilli(event.startEpochMillis).atZone(zone).toLocalTime()
        val endLocal = Instant.ofEpochMilli(event.endEpochMillis).atZone(zone).toLocalTime()
        "${timeFormatter.format(startLocal)} – ${timeFormatter.format(endLocal)}"
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 5.dp, height = 36.dp)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(Color(event.color)),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title.ifBlank { "(Untitled Event)" },
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (event.isCrossedOut) TextDecoration.LineThrough else null,
                )
                Spacer(Modifier.height(2.dp))
                val subText = if (!event.location.isNullOrBlank()) "$timeText · ${event.location}" else timeText
                Text(
                    text = subText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

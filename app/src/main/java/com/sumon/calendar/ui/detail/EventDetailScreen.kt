package com.sumon.calendar.ui.detail

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.sumon.calendar.domain.Attendee
import com.sumon.calendar.domain.AttendeeStatus
import com.sumon.calendar.domain.EventDetail
import com.sumon.calendar.domain.RecurringWriteScope
import com.sumon.calendar.domain.recurrenceToHumanReadable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    detail: EventDetail,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (RecurringWriteScope) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    // Share
                    IconButton(onClick = { shareEventText(context, detail) }) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Share")
                    }
                    if (detail.canModify) {
                        IconButton(onClick = onEdit) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Title & Calendar Banner
            val eventColor = Color(detail.instance.color)
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(eventColor),
                    )
                    Text(
                        text = detail.calendarName ?: "Calendar",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = detail.instance.title.ifBlank { "(Untitled Event)" },
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (detail.instance.isCrossedOut) TextDecoration.LineThrough else null,
                )
            }

            // Time & Date Section
            DetailCard(
                icon = Icons.Default.Schedule,
                title = formatDetailTimeSpan(detail),
                subtitle = if (detail.instance.isAllDay) "All day" else null,
            )

            // Recurrence Section
            val recurrenceText = recurrenceToHumanReadable(detail.rrule)
            if (recurrenceText != null) {
                DetailCard(
                    icon = Icons.Default.Repeat,
                    title = recurrenceText,
                )
            }

            // Reminders Section
            if (detail.reminders.isNotEmpty()) {
                val reminderLabels = detail.reminders.joinToString(", ") { reminder ->
                    formatReminderLeadTime(reminder.minutes)
                }
                DetailCard(
                    icon = Icons.Default.Notifications,
                    title = reminderLabels,
                    subtitle = "Notification reminder",
                )
            }

            // Location Section
            if (!detail.instance.location.isNullOrBlank()) {
                val loc = detail.instance.location
                DetailCard(
                    icon = Icons.Default.Place,
                    title = loc,
                    subtitle = "Tap to open in Maps",
                    onClick = { openInMaps(context, loc) },
                )
            }

            // Description / Notes Section
            if (!detail.description.isNullOrBlank()) {
                DetailCard(
                    icon = Icons.AutoMirrored.Filled.Notes,
                    title = detail.description,
                    subtitle = "Description",
                )
            }

            // Attendees Section
            if (detail.attendees.isNotEmpty()) {
                AttendeesCard(attendees = detail.attendees)
            }
        }
    }

    if (showDeleteDialog) {
        DeleteEventDialog(
            isRecurring = detail.instance.isRecurring,
            onDismiss = { showDeleteDialog = false },
            onConfirm = { scope ->
                showDeleteDialog = false
                onDelete(scope)
            },
        )
    }
}

@Composable
private fun DetailCard(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AttendeesCard(attendees: List<Attendee>) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.People,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = "Guests (${attendees.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                attendees.forEach { guest ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = guest.name,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            )
                            val statusLabel = when (guest.status) {
                                AttendeeStatus.Accepted -> "✓ Going"
                                AttendeeStatus.Declined -> "✕ Declined"
                                AttendeeStatus.Tentative -> "? Maybe"
                                AttendeeStatus.NeedsAction, AttendeeStatus.Unknown -> "Pending"
                            }
                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = when (guest.status) {
                                    AttendeeStatus.Accepted -> Color(0xFF2E7D32)
                                    AttendeeStatus.Declined -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeleteEventDialog(
    isRecurring: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (RecurringWriteScope) -> Unit,
) {
    var selectedScope by remember { mutableStateOf(RecurringWriteScope.ThisEvent) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Delete Event") },
        text = {
            if (isRecurring) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This is a recurring event. Which events would you like to delete?")

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedScope = RecurringWriteScope.ThisEvent },
                    ) {
                        RadioButton(
                            selected = (selectedScope == RecurringWriteScope.ThisEvent),
                            onClick = { selectedScope = RecurringWriteScope.ThisEvent },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Only this event")
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedScope = RecurringWriteScope.ThisAndFollowing },
                    ) {
                        RadioButton(
                            selected = (selectedScope == RecurringWriteScope.ThisAndFollowing),
                            onClick = { selectedScope = RecurringWriteScope.ThisAndFollowing },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("This and following events")
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedScope = RecurringWriteScope.AllEvents },
                    ) {
                        RadioButton(
                            selected = (selectedScope == RecurringWriteScope.AllEvents),
                            onClick = { selectedScope = RecurringWriteScope.AllEvents },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("All events in series")
                    }
                }
            } else {
                Text("Are you sure you want to delete this event?")
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(if (isRecurring) selectedScope else RecurringWriteScope.AllEvents)
                },
            ) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun formatDetailTimeSpan(detail: EventDetail): String {
    val zone = ZoneId.systemDefault()
    val isAllDay = detail.instance.isAllDay

    val startInstant = Instant.ofEpochMilli(detail.instance.startEpochMillis)
    val endInstant = Instant.ofEpochMilli(detail.instance.endEpochMillis)

    val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy", Locale.getDefault())
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

    return if (isAllDay) {
        val startDate = startInstant.atZone(ZoneOffset.UTC).toLocalDate()
        val rawEndDate = endInstant.atZone(ZoneOffset.UTC).toLocalDate()
        val endDate = if (rawEndDate.isAfter(startDate)) rawEndDate.minusDays(1) else startDate
        if (startDate == endDate) {
            dateFormatter.format(startDate)
        } else {
            "${dateFormatter.format(startDate)} – ${dateFormatter.format(endDate)}"
        }
    } else {
        val startZoned = startInstant.atZone(zone)
        val endZoned = endInstant.atZone(zone)
        if (startZoned.toLocalDate() == endZoned.toLocalDate()) {
            "${dateFormatter.format(startZoned.toLocalDate())}\n${timeFormatter.format(startZoned.toLocalTime())} – ${timeFormatter.format(endZoned.toLocalTime())}"
        } else {
            "${dateFormatter.format(startZoned.toLocalDate())} ${timeFormatter.format(startZoned.toLocalTime())} –\n${dateFormatter.format(endZoned.toLocalDate())} ${timeFormatter.format(endZoned.toLocalTime())}"
        }
    }
}

private fun formatReminderLeadTime(minutes: Int): String = when {
    minutes <= 0 -> "At time of event"
    minutes < 60 -> "$minutes minutes before"
    minutes == 60 -> "1 hour before"
    minutes < 1440 && minutes % 60 == 0 -> "${minutes / 60} hours before"
    minutes == 1440 -> "1 day before"
    minutes % 1440 == 0 -> "${minutes / 1440} days before"
    else -> "$minutes minutes before"
}

private fun openInMaps(context: Context, location: String) {
    try {
        val uri = Uri.parse("geo:0,0?q=" + Uri.encode(location))
        val mapIntent = Intent(Intent.ACTION_VIEW, uri)
        context.startActivity(mapIntent)
    } catch (_: Exception) {
        try {
            val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(location))
            context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
        } catch (_: Exception) {}
    }
}

private fun shareEventText(context: Context, detail: EventDetail) {
    val text = buildString {
        appendLine(detail.instance.title)
        appendLine(formatDetailTimeSpan(detail))
        if (!detail.instance.location.isNullOrBlank()) {
            appendLine("Location: ${detail.instance.location}")
        }
        if (!detail.description.isNullOrBlank()) {
            appendLine()
            appendLine(detail.description)
        }
    }
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, detail.instance.title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share Event"))
}

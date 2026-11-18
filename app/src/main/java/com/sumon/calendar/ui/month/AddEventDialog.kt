package com.sumon.calendar.ui.month

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.sumon.calendar.R
import com.sumon.calendar.data.DeviceCalendar
import com.sumon.calendar.data.Event
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private const val MILLIS_PER_DAY = 86_400_000L

private fun LocalDate.toPickerMillis(): Long = toEpochDay() * MILLIS_PER_DAY

private fun pickerMillisToLocalDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC).toLocalDate()

data class ReminderOption(val minutes: Int?, val labelResId: Int)

val REMINDER_OPTIONS = listOf(
    ReminderOption(null, R.string.reminder_none),
    ReminderOption(5, R.string.reminder_5_min),
    ReminderOption(10, R.string.reminder_10_min),
    ReminderOption(15, R.string.reminder_15_min),
    ReminderOption(30, R.string.reminder_30_min),
    ReminderOption(60, R.string.reminder_1_hour),
    ReminderOption(1440, R.string.reminder_1_day),
)

/**
 * Full-featured Add or Edit Event dialog.
 * Connects directly to local calendar events, supporting title, description,
 * location, date/time ranges, all-day toggle, calendar selection, and reminders.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEventDialog(
    initialDate: LocalDate,
    is24Hour: Boolean,
    dateFormatter: DateTimeFormatter,
    timeFormatter: DateTimeFormatter,
    initialEvent: Event? = null,
    availableCalendars: List<DeviceCalendar> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (Event) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var title by remember { mutableStateOf(initialEvent?.title.orEmpty()) }
    var description by remember { mutableStateOf(initialEvent?.description.orEmpty()) }
    var location by remember { mutableStateOf(initialEvent?.location.orEmpty()) }

    var isAllDay by remember {
        mutableStateOf(initialEvent?.isAllDay ?: (initialEvent?.startTime == null))
    }
    var startDate by remember { mutableStateOf(initialEvent?.startDate ?: initialDate) }
    var endDate by remember { mutableStateOf(initialEvent?.endDate ?: initialDate) }

    val defaultStartTime = remember {
        LocalTime.now().plusHours(1).truncatedTo(ChronoUnit.HOURS)
    }
    var startTime by remember {
        mutableStateOf(initialEvent?.startTime ?: defaultStartTime)
    }
    var endTime by remember {
        mutableStateOf(initialEvent?.endTime ?: defaultStartTime.plusHours(1))
    }

    var selectedCalendarId by remember {
        mutableStateOf(
            initialEvent?.calendarId?.takeIf { it > 0 }
                ?: availableCalendars.firstOrNull { it.isPrimary }?.id
                ?: availableCalendars.firstOrNull()?.id
                ?: 0L,
        )
    }

    var selectedReminderMinutes by remember {
        mutableStateOf(initialEvent?.reminderMinutes)
    }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showCalendarDropdown by remember { mutableStateOf(false) }
    var showReminderDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (initialEvent == null) R.string.add_event else R.string.edit_event,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.title_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Calendar selection (if available)
                if (availableCalendars.isNotEmpty()) {
                    val currentCalendar = availableCalendars.firstOrNull { it.id == selectedCalendarId }
                        ?: availableCalendars.first()

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { showCalendarDropdown = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (currentCalendar.color != 0) Color(currentCalendar.color)
                                            else MaterialTheme.colorScheme.primary,
                                        ),
                                )
                                Text(
                                    text = "${stringResource(R.string.calendar_label)}: ${currentCalendar.displayName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showCalendarDropdown,
                            onDismissRequest = { showCalendarDropdown = false },
                        ) {
                            availableCalendars.forEach { cal ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (cal.color != 0) Color(cal.color)
                                                        else MaterialTheme.colorScheme.primary,
                                                    ),
                                            )
                                            Column {
                                                val accountSuffix = if (cal.accountType == "com.google") " (Google)" else ""
                                                Text("${cal.displayName}$accountSuffix", style = MaterialTheme.typography.bodyMedium)
                                                if (cal.accountName.isNotBlank()) {
                                                    Text(
                                                        if (cal.accountType == "com.google") "Google: ${cal.accountName}" else cal.accountName,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    onClick = {
                                        selectedCalendarId = cal.id
                                        showCalendarDropdown = false
                                    },
                                )
                            }
                        }
                    }
                }

                // All day switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Switch(
                        checked = isAllDay,
                        onCheckedChange = { isAllDay = it },
                    )
                    Text(
                        text = stringResource(R.string.all_day),
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                // Date & Time pickers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { showStartDatePicker = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(startDate.format(dateFormatter))
                    }

                    if (!isAllDay) {
                        OutlinedButton(
                            onClick = { showStartTimePicker = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(startTime.format(timeFormatter))
                        }
                    }
                }

                if (!isAllDay) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "To",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = { showEndTimePicker = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(endTime.format(timeFormatter))
                        }
                    }
                }

                // Location
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text(stringResource(R.string.location_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.description_label)) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Reminder selector
                Box(modifier = Modifier.fillMaxWidth()) {
                    val currentOption = REMINDER_OPTIONS.firstOrNull { it.minutes == selectedReminderMinutes }
                        ?: REMINDER_OPTIONS.first()

                    OutlinedButton(
                        onClick = { showReminderDropdown = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("${stringResource(R.string.reminder_label)}: ${stringResource(currentOption.labelResId)}")
                    }

                    DropdownMenu(
                        expanded = showReminderDropdown,
                        onDismissRequest = { showReminderDropdown = false },
                    ) {
                        REMINDER_OPTIONS.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(stringResource(option.labelResId)) },
                                onClick = {
                                    selectedReminderMinutes = option.minutes
                                    showReminderDropdown = false
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    val updatedEvent = Event(
                        id = initialEvent?.id ?: 0L,
                        calendarId = selectedCalendarId,
                        title = title.trim(),
                        description = description.takeIf { it.isNotBlank() },
                        location = location.takeIf { it.isNotBlank() },
                        startDate = startDate,
                        endDate = endDate,
                        startTime = if (!isAllDay) startTime else null,
                        endTime = if (!isAllDay) endTime else null,
                        isAllDay = isAllDay,
                        reminderMinutes = selectedReminderMinutes,
                    )
                    onSave(updatedEvent)
                },
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            Row(modifier = Modifier.fillMaxWidth()) {
                onDelete?.let { delete ->
                    TextButton(
                        onClick = delete,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(),
                    ) {
                        Text(
                            text = stringResource(R.string.delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = onDismiss,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(),
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
    )

    // Start Date Picker Dialog
    if (showStartDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = startDate.toPickerMillis())
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        val newDate = pickerMillisToLocalDate(it)
                        startDate = newDate
                        if (endDate.isBefore(newDate)) {
                            endDate = newDate
                        }
                    }
                    showStartDatePicker = false
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    // Start Time Picker Dialog
    if (showStartTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = startTime.hour,
            initialMinute = startTime.minute,
            is24Hour = is24Hour,
        )
        Dialog(onDismissRequest = { showStartTimePicker = false }) {
            Surface(shape = MaterialTheme.shapes.large) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.start_time),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    TimePicker(state = timeState)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { showStartTimePicker = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                        TextButton(onClick = {
                            val picked = LocalTime.of(timeState.hour, timeState.minute)
                            startTime = picked
                            if (endTime.isBefore(picked)) {
                                endTime = picked.plusHours(1)
                            }
                            showStartTimePicker = false
                        }) {
                            Text(stringResource(R.string.ok))
                        }
                    }
                }
            }
        }
    }

    // End Time Picker Dialog
    if (showEndTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = endTime.hour,
            initialMinute = endTime.minute,
            is24Hour = is24Hour,
        )
        Dialog(onDismissRequest = { showEndTimePicker = false }) {
            Surface(shape = MaterialTheme.shapes.large) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.end_time),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    TimePicker(state = timeState)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { showEndTimePicker = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                        TextButton(onClick = {
                            endTime = LocalTime.of(timeState.hour, timeState.minute)
                            showEndTimePicker = false
                        }) {
                            Text(stringResource(R.string.ok))
                        }
                    }
                }
            }
        }
    }
}

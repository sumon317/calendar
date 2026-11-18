package com.sumon.calendar.ui.edit

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.sumon.calendar.data.DeviceCalendar
import com.sumon.calendar.domain.EventForm
import com.sumon.calendar.domain.RecurrenceEnd
import com.sumon.calendar.domain.RecurrenceFreq
import com.sumon.calendar.domain.RecurringWriteScope
import com.sumon.calendar.domain.SimpleRecurrence
import com.sumon.calendar.domain.parseSimpleRecurrence
import com.sumon.calendar.domain.toRRule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MILLIS_PER_DAY = 86_400_000L

private fun LocalDate.toPickerMillis(): Long = toEpochDay() * MILLIS_PER_DAY

private fun pickerMillisToLocalDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EventEditScreen(
    initialForm: EventForm,
    calendars: List<DeviceCalendar>,
    onDismiss: () -> Unit,
    onSave: (EventForm, RecurringWriteScope) -> Unit,
    modifier: Modifier = Modifier,
) {
    var title by remember { mutableStateOf(initialForm.title) }
    var description by remember { mutableStateOf(initialForm.description) }
    var location by remember { mutableStateOf(initialForm.location) }

    var isAllDay by remember { mutableStateOf(initialForm.isAllDay) }
    var startDate by remember { mutableStateOf(initialForm.startDateTime.toLocalDate()) }
    var startTime by remember { mutableStateOf(initialForm.startDateTime.toLocalTime()) }
    var endDate by remember { mutableStateOf(initialForm.endDateTime.toLocalDate()) }
    var endTime by remember { mutableStateOf(initialForm.endDateTime.toLocalTime()) }

    var selectedCalendarId by remember {
        mutableStateOf(
            if (initialForm.calendarId > 0) initialForm.calendarId
            else calendars.firstOrNull()?.id ?: 1L,
        )
    }

    var reminders by remember { mutableStateOf(initialForm.reminders.ifEmpty { listOf(10) }) }

    // Recurrence parsing
    val initialSimple = remember(initialForm.rrule) {
        initialForm.rrule?.let { parseSimpleRecurrence(it) }
    }
    var repeatFreq by remember { mutableStateOf(initialSimple?.freq) }
    var repeatInterval by remember { mutableIntStateOf(initialSimple?.interval ?: 1) }
    var weeklyDays by remember { mutableStateOf(initialSimple?.byDays ?: emptySet()) }
    var repeatEnd by remember { mutableStateOf<RecurrenceEnd>(initialSimple?.end ?: RecurrenceEnd.Never) }

    // Dialog state
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showCalendarDropdown by remember { mutableStateOf(false) }
    var showRepeatDropdown by remember { mutableStateOf(false) }
    var showAddReminderDropdown by remember { mutableStateOf(false) }
    var showScopeDialog by remember { mutableStateOf(false) }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault()) }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()) }

    val isEditingExisting = initialForm.eventId != null && initialForm.eventId > 0
    val isRecurringSeries = isEditingExisting && !initialForm.rrule.isNullOrBlank()

    fun buildCurrentForm(): EventForm {
        val rruleString = if (repeatFreq != null) {
            SimpleRecurrence(
                freq = repeatFreq!!,
                interval = repeatInterval,
                end = repeatEnd,
                byDays = if (repeatFreq == RecurrenceFreq.Weekly) weeklyDays else emptySet(),
            ).toRRule()
        } else {
            null
        }

        return initialForm.copy(
            calendarId = selectedCalendarId,
            title = title,
            description = description,
            location = location,
            startDateTime = LocalDateTime.of(startDate, if (isAllDay) LocalTime.MIDNIGHT else startTime),
            endDateTime = LocalDateTime.of(endDate, if (isAllDay) LocalTime.MIDNIGHT else endTime),
            isAllDay = isAllDay,
            rrule = rruleString,
            reminders = reminders,
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditingExisting) "Edit event" else "New event",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            if (isRecurringSeries) {
                                showScopeDialog = true
                            } else {
                                onSave(buildCurrentForm(), RecurringWriteScope.AllEvents)
                            }
                        },
                        enabled = title.isNotBlank(),
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text("Save")
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
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Title Field
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                placeholder = { Text("Add title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )

            // Calendar Selector Row
            val selectedCalendar = calendars.firstOrNull { it.id == selectedCalendarId }
                ?: calendars.firstOrNull()
            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCalendarDropdown = true },
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color(selectedCalendar?.color ?: 0xFF4285F4.toInt())),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedCalendar?.displayName ?: "Calendar",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            )
                            if (!selectedCalendar?.accountName.isNullOrBlank()) {
                                Text(
                                    text = selectedCalendar!!.accountName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                }

                DropdownMenu(
                    expanded = showCalendarDropdown,
                    onDismissRequest = { showCalendarDropdown = false },
                ) {
                    calendars.forEach { cal ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(Color(cal.color)),
                                    )
                                    Column {
                                        Text(cal.displayName, fontWeight = FontWeight.Medium)
                                        Text(cal.accountName, style = MaterialTheme.typography.labelSmall)
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

            // All-day Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text("All day", style = MaterialTheme.typography.bodyLarge)
                }
                Switch(
                    checked = isAllDay,
                    onCheckedChange = { checked ->
                        isAllDay = checked
                    },
                )
            }

            // Date & Time Selectors
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Starts row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { showStartDatePicker = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(dateFormatter.format(startDate))
                    }
                    if (!isAllDay) {
                        OutlinedButton(
                            onClick = { showStartTimePicker = true },
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(timeFormatter.format(startTime))
                        }
                    }
                }

                // Ends row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { showEndDatePicker = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(dateFormatter.format(endDate))
                    }
                    if (!isAllDay) {
                        OutlinedButton(
                            onClick = { showEndTimePicker = true },
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(timeFormatter.format(endTime))
                        }
                    }
                }
            }

            // Recurrence Section
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text("Repeat", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium))
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { showRepeatDropdown = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val repeatLabel = when (repeatFreq) {
                                null -> "Does not repeat"
                                RecurrenceFreq.Daily -> if (repeatInterval == 1) "Daily" else "Every $repeatInterval days"
                                RecurrenceFreq.Weekly -> if (repeatInterval == 1) "Weekly" else "Every $repeatInterval weeks"
                                RecurrenceFreq.Monthly -> if (repeatInterval == 1) "Monthly" else "Every $repeatInterval months"
                                RecurrenceFreq.Yearly -> if (repeatInterval == 1) "Yearly" else "Every $repeatInterval years"
                            }
                            Text(repeatLabel)
                            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    }

                    DropdownMenu(
                        expanded = showRepeatDropdown,
                        onDismissRequest = { showRepeatDropdown = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Does not repeat") },
                            onClick = {
                                repeatFreq = null
                                showRepeatDropdown = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Daily") },
                            onClick = {
                                repeatFreq = RecurrenceFreq.Daily
                                repeatInterval = 1
                                showRepeatDropdown = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Weekly") },
                            onClick = {
                                repeatFreq = RecurrenceFreq.Weekly
                                repeatInterval = 1
                                if (weeklyDays.isEmpty()) {
                                    weeklyDays = setOf(startDate.dayOfWeek)
                                }
                                showRepeatDropdown = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Monthly") },
                            onClick = {
                                repeatFreq = RecurrenceFreq.Monthly
                                repeatInterval = 1
                                showRepeatDropdown = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Yearly") },
                            onClick = {
                                repeatFreq = RecurrenceFreq.Yearly
                                repeatInterval = 1
                                showRepeatDropdown = false
                            },
                        )
                    }
                }

                // If Weekly: Weekday buttons
                if (repeatFreq == RecurrenceFreq.Weekly) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        val weekdays = listOf(
                            DayOfWeek.MONDAY to "M",
                            DayOfWeek.TUESDAY to "T",
                            DayOfWeek.WEDNESDAY to "W",
                            DayOfWeek.THURSDAY to "T",
                            DayOfWeek.FRIDAY to "F",
                            DayOfWeek.SATURDAY to "S",
                            DayOfWeek.SUNDAY to "S",
                        )
                        weekdays.forEach { (dow, label) ->
                            val isSelected = dow in weeklyDays
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clickable {
                                        weeklyDays = if (isSelected) {
                                            if (weeklyDays.size > 1) weeklyDays - dow else weeklyDays
                                        } else {
                                            weeklyDays + dow
                                        }
                                    },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Reminders Section
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text("Reminders", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium))
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    reminders.forEach { min ->
                        FilterChip(
                            selected = true,
                            onClick = {
                                reminders = reminders - min
                            },
                            label = { Text(reminderLabel(min)) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove",
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    }

                    Box {
                        OutlinedButton(
                            onClick = { showAddReminderDropdown = true },
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add")
                        }

                        DropdownMenu(
                            expanded = showAddReminderDropdown,
                            onDismissRequest = { showAddReminderDropdown = false },
                        ) {
                            val options = listOf(
                                0 to "At time of event",
                                5 to "5 minutes before",
                                10 to "10 minutes before",
                                15 to "15 minutes before",
                                30 to "30 minutes before",
                                60 to "1 hour before",
                                1440 to "1 day before",
                            )
                            options.forEach { (mins, text) ->
                                if (mins !in reminders) {
                                    DropdownMenuItem(
                                        text = { Text(text) },
                                        onClick = {
                                            reminders = (reminders + mins).sorted()
                                            showAddReminderDropdown = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Location Field
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Location") },
                placeholder = { Text("Add location") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )

            // Description Field
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                placeholder = { Text("Add notes or description") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )
        }
    }

    // Pickers
    if (showStartDatePicker) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = startDate.toPickerMillis())
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        dateState.selectedDateMillis?.let {
                            val picked = pickerMillisToLocalDate(it)
                            startDate = picked
                            if (endDate.isBefore(picked)) endDate = picked
                        }
                        showStartDatePicker = false
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = dateState)
        }
    }

    if (showEndDatePicker) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = endDate.toPickerMillis())
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        dateState.selectedDateMillis?.let {
                            val picked = pickerMillisToLocalDate(it)
                            endDate = picked
                            if (startDate.isAfter(picked)) startDate = picked
                        }
                        showEndDatePicker = false
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = dateState)
        }
    }

    if (showStartTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = startTime.hour,
            initialMinute = startTime.minute,
            is24Hour = true,
        )
        Dialog(onDismissRequest = { showStartTimePicker = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.padding(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TimePicker(state = timeState)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { showStartTimePicker = false }) { Text("Cancel") }
                        TextButton(
                            onClick = {
                                startTime = LocalTime.of(timeState.hour, timeState.minute)
                                if (startDate == endDate && endTime.isBefore(startTime)) {
                                    endTime = startTime.plusHours(1)
                                }
                                showStartTimePicker = false
                            },
                        ) { Text("OK") }
                    }
                }
            }
        }
    }

    if (showEndTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = endTime.hour,
            initialMinute = endTime.minute,
            is24Hour = true,
        )
        Dialog(onDismissRequest = { showEndTimePicker = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.padding(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TimePicker(state = timeState)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { showEndTimePicker = false }) { Text("Cancel") }
                        TextButton(
                            onClick = {
                                endTime = LocalTime.of(timeState.hour, timeState.minute)
                                showEndTimePicker = false
                            },
                        ) { Text("OK") }
                    }
                }
            }
        }
    }

    if (showScopeDialog) {
        var chosenScope by remember { mutableStateOf(RecurringWriteScope.ThisEvent) }
        AlertDialog(
            onDismissRequest = { showScopeDialog = false },
            title = { Text("Save recurring event") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Which occurrences would you like to update?")
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { chosenScope = RecurringWriteScope.ThisEvent },
                    ) {
                        RadioButton(
                            selected = chosenScope == RecurringWriteScope.ThisEvent,
                            onClick = { chosenScope = RecurringWriteScope.ThisEvent },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Only this event")
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { chosenScope = RecurringWriteScope.ThisAndFollowing },
                    ) {
                        RadioButton(
                            selected = chosenScope == RecurringWriteScope.ThisAndFollowing,
                            onClick = { chosenScope = RecurringWriteScope.ThisAndFollowing },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("This and following events")
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { chosenScope = RecurringWriteScope.AllEvents },
                    ) {
                        RadioButton(
                            selected = chosenScope == RecurringWriteScope.AllEvents,
                            onClick = { chosenScope = RecurringWriteScope.AllEvents },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("All events in series")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showScopeDialog = false
                        onSave(buildCurrentForm(), chosenScope)
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showScopeDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

private fun reminderLabel(minutes: Int): String = when (minutes) {
    0 -> "At time of event"
    5 -> "5m before"
    10 -> "10m before"
    15 -> "15m before"
    30 -> "30m before"
    60 -> "1h before"
    1440 -> "1d before"
    else -> "${minutes}m before"
}

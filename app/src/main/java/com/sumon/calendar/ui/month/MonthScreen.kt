package com.sumon.calendar.ui.month

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sumon.calendar.R
import com.sumon.calendar.data.DeviceCalendar
import com.sumon.calendar.data.Event
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

@Composable
fun MonthScreen(
    viewModel: CalendarViewModel,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }
    val weekStart = remember { WeekFields.of(Locale.getDefault()).firstDayOfWeek }
    val cells = remember(state.month, weekStart) { buildMonthCells(state.month, weekStart) }
    val weekdayNames = remember(weekStart) { weekdayLabels(weekStart, Locale.getDefault()) }
    val eventDays = remember(state.monthEvents) { state.monthEvents.map { it.startDate }.toSet() }
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val timeFormatter = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    val context = LocalContext.current
    val is24Hour = remember(context) { android.text.format.DateFormat.is24HourFormat(context) }

    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Event?>(null) }
    var pickingMonth by remember { mutableStateOf(false) }
    var pickingYear by remember { mutableStateOf(false) }

    // Runtime Permission Launcher for Android Calendar Provider
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val granted = results[Manifest.permission.READ_CALENDAR] == true &&
            results[Manifest.permission.WRITE_CALENDAR] == true
        viewModel.onPermissionResult(granted)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (state.hasPermission) {
                val notOnToday =
                    state.selectedDay != today || state.month != YearMonth.from(today)
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (notOnToday) {
                        ExtendedFloatingActionButton(
                            onClick = viewModel::goToToday,
                        ) {
                            Text(stringResource(R.string.today))
                        }
                    }
                    FloatingActionButton(onClick = { showAdd = true }) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.add_event),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 4.dp),
        ) {
            // Calendar filter, Refresh, and Settings bar
            TopCalendarBar(
                calendars = state.calendars,
                selectedCalendarId = state.selectedCalendarId,
                isSyncing = state.isSyncing,
                onSelectCalendar = viewModel::filterByCalendar,
                onOpenSettings = onOpenSettings,
                onRefresh = {
                    viewModel.refresh()
                    viewModel.syncWithGoogle()
                },
            )

            // Permission Request Banner if permissions are not yet granted
            if (!state.hasPermission) {
                PermissionBanner(
                    onRequestPermission = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_CALENDAR,
                                Manifest.permission.WRITE_CALENDAR,
                            ),
                        )
                    },
                )
            }

            // Month navigation header
            MonthHeader(
                month = state.month,
                onPrevious = viewModel::previousMonth,
                onNext = viewModel::nextMonth,
                onSelectMonth = { pickingMonth = true },
                onSelectYear = { pickingYear = true },
            )

            // Day-of-week header row (Mon, Tue, ...)
            WeekdayLabelRow(weekdayNames)

            // 7xN Calendar grid
            MonthGrid(
                cells = cells,
                selectedDay = state.selectedDay,
                today = today,
                eventDays = eventDays,
                onSelectDay = viewModel::select,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            val allCalendarsDisabled = remember(state.calendars) {
                state.calendars.isNotEmpty() && state.calendars.none { it.isEnabled }
            }

            // Day events view
            DaySection(
                date = state.selectedDay,
                events = state.dayEvents,
                dateFormatter = dateFormatter,
                timeFormatter = timeFormatter,
                allCalendarsDisabled = allCalendarsDisabled,
                onOpenSettings = onOpenSettings,
                onEventClick = { event ->
                    viewModel.loadEventForEditing(event) { fullEvent ->
                        editing = fullEvent
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (showAdd) {
        val enabledCalendars = remember(state.calendars) {
            state.calendars.filter { it.isEnabled }.ifEmpty { state.calendars }
        }
        AddEventDialog(
            initialDate = state.selectedDay,
            is24Hour = is24Hour,
            dateFormatter = dateFormatter,
            timeFormatter = timeFormatter,
            availableCalendars = enabledCalendars,
            onDismiss = { showAdd = false },
            onSave = { newEvent ->
                viewModel.save(newEvent)
                showAdd = false
            },
        )
    }

    editing?.let { target ->
        AddEventDialog(
            initialEvent = target,
            initialDate = target.startDate,
            is24Hour = is24Hour,
            dateFormatter = dateFormatter,
            timeFormatter = timeFormatter,
            availableCalendars = state.calendars,
            onDismiss = { editing = null },
            onSave = { updatedEvent ->
                viewModel.save(updatedEvent)
                editing = null
            },
            onDelete = {
                viewModel.delete(target.id)
                editing = null
            },
        )
    }

    if (pickingMonth) {
        MonthPickerDialog(
            selectedMonth = state.month.monthValue,
            onSelect = { monthValue ->
                viewModel.selectMonth(monthValue)
                pickingMonth = false
            },
            onDismiss = { pickingMonth = false },
        )
    }

    if (pickingYear) {
        YearPickerDialog(
            selectedYear = state.month.year,
            onSelect = { year ->
                viewModel.selectYear(year)
                pickingYear = false
            },
            onDismiss = { pickingYear = false },
        )
    }
}

@Composable
private fun TopCalendarBar(
    calendars: List<DeviceCalendar>,
    selectedCalendarId: Long?,
    isSyncing: Boolean,
    onSelectCalendar: (Long?) -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
) {
    var showDropdown by remember { mutableStateOf(false) }
    val enabledCalendars = remember(calendars) { calendars.filter { it.isEnabled } }
    val disabledCalendars = remember(calendars) { calendars.filterNot { it.isEnabled } }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Calendar selection dropdown
        Box {
            val selectedCalendar = calendars.firstOrNull { it.id == selectedCalendarId }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clickable { showDropdown = true },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (selectedCalendar != null && selectedCalendar.color != 0) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(selectedCalendar.color)),
                        )
                    }
                    val labelText = when {
                        selectedCalendar != null -> selectedCalendar.displayName
                        calendars.isEmpty() -> stringResource(R.string.all_calendars)
                        disabledCalendars.isNotEmpty() -> stringResource(
                            R.string.calendars_enabled_count,
                            enabledCalendars.size,
                            calendars.size,
                        )
                        else -> stringResource(R.string.all_calendars)
                    }
                    Text(
                        text = labelText,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            DropdownMenu(
                expanded = showDropdown,
                onDismissRequest = { showDropdown = false },
            ) {
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = if (disabledCalendars.isNotEmpty()) {
                                    stringResource(R.string.all_enabled_calendars)
                                } else {
                                    stringResource(R.string.all_calendars)
                                },
                                fontWeight = if (selectedCalendarId == null) FontWeight.Bold else FontWeight.Normal,
                            )
                            if (disabledCalendars.isNotEmpty()) {
                                Text(
                                    text = "${enabledCalendars.size} of ${calendars.size} active",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    onClick = {
                        onSelectCalendar(null)
                        showDropdown = false
                    },
                )

                if (calendars.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }

                enabledCalendars.forEach { cal ->
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
                                    val isGoogle = cal.accountType == "com.google"
                                    Text(
                                        text = "${cal.displayName}${if (isGoogle) " (Google)" else ""}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (cal.id == selectedCalendarId) FontWeight.Bold else FontWeight.Normal,
                                    )
                                    if (cal.accountName.isNotBlank()) {
                                        Text(
                                            text = cal.accountName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        },
                        onClick = {
                            onSelectCalendar(cal.id)
                            showDropdown = false
                        },
                    )
                }

                if (disabledCalendars.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    disabledCalendars.forEach { cal ->
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
                                                (if (cal.color != 0) Color(cal.color) else MaterialTheme.colorScheme.primary)
                                                    .copy(alpha = 0.4f),
                                            ),
                                    )
                                    Column {
                                        Text(
                                            text = "${cal.displayName} (${stringResource(R.string.calendar_disabled_tag)})",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        )
                                        if (cal.accountName.isNotBlank()) {
                                            Text(
                                                text = cal.accountName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            )
                                        }
                                    }
                                }
                            },
                            onClick = {
                                onSelectCalendar(cal.id)
                                showDropdown = false
                            },
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = stringResource(R.string.manage_calendars),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    },
                    onClick = {
                        showDropdown = false
                        onOpenSettings()
                    },
                )
            }
        }

        // Action buttons: Refresh and Settings
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "refreshSpin")
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "rotation",
            )

            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.refresh),
                    modifier = if (isSyncing) Modifier.rotate(rotation) else Modifier,
                )
            }

            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.settings),
                )
            }
        }
    }
}

@Composable
private fun PermissionBanner(onRequestPermission: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.permission_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.permission_message),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onRequestPermission) {
                Text(stringResource(R.string.grant_permission))
            }
        }
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectMonth: () -> Unit,
    onSelectYear: () -> Unit,
) {
    val monthName = remember(month) {
        month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    }
    val yearText = remember(month) { month.year.toString() }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.previous_month),
            )
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onSelectMonth) {
                Text(
                    text = monthName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = stringResource(R.string.select_month),
                )
            }
            TextButton(onClick = onSelectYear) {
                Text(
                    text = yearText,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = stringResource(R.string.select_year),
                )
            }
        }
        IconButton(onClick = onNext) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.next_month),
            )
        }
    }
}

@Composable
private fun WeekdayLabelRow(labels: List<String>) {
    Row(modifier = Modifier.fillMaxWidth()) {
        labels.forEach { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DaySection(
    date: LocalDate,
    events: List<Event>,
    dateFormatter: DateTimeFormatter,
    timeFormatter: DateTimeFormatter,
    allCalendarsDisabled: Boolean,
    onOpenSettings: () -> Unit,
    onEventClick: (Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = date.format(dateFormatter),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (events.isNotEmpty()) {
                Text(
                    text = "${events.size} event${if (events.size > 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (allCalendarsDisabled) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.all_calendars_disabled_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                OutlinedButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.manage_calendars))
                }
            }
        } else if (events.isEmpty()) {
            Text(
                text = stringResource(R.string.no_events_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(events, key = { it.id }) { event ->
                    EventCard(
                        event = event,
                        timeFormatter = timeFormatter,
                        onClick = { onEventClick(event) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventCard(
    event: Event,
    timeFormatter: DateTimeFormatter,
    onClick: () -> Unit,
) {
    val accentColor = remember(event.color) {
        if (event.color != null && event.color != 0) Color(event.color) else null
    }

    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Calendar accent color bar
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(64.dp)
                    .background(accentColor ?: MaterialTheme.colorScheme.primary),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    event.calendarName?.let { calName ->
                        Text(
                            text = calName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }

                val timeText = if (event.isAllDay || event.startTime == null) {
                    stringResource(R.string.all_day)
                } else {
                    val startStr = event.startTime.format(timeFormatter)
                    val endStr = event.endTime?.format(timeFormatter)
                    if (endStr != null) "$startStr – $endStr" else startStr
                }
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (!event.location.isNullOrBlank()) {
                    Text(
                        text = "📍 ${event.location}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (!event.description.isNullOrBlank()) {
                    Text(
                        text = event.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

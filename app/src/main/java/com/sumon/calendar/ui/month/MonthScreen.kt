package com.sumon.calendar.ui.month

import android.Manifest
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sumon.calendar.R
import com.sumon.calendar.data.DeviceCalendar
import com.sumon.calendar.data.Event
import com.sumon.calendar.domain.CalendarViewMode
import com.sumon.calendar.domain.EventDetail
import com.sumon.calendar.domain.EventForm
import com.sumon.calendar.domain.EventInstance
import com.sumon.calendar.ui.agenda.AgendaScreen
import com.sumon.calendar.ui.day.DayScreen
import com.sumon.calendar.ui.detail.EventDetailScreen
import com.sumon.calendar.ui.edit.EventEditScreen
import com.sumon.calendar.ui.search.SearchScreen
import com.sumon.calendar.ui.week.WeekScreen
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
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
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val activeDetail by viewModel.activeDetail.collectAsStateWithLifecycle()
    val isSearchOpen by viewModel.isSearchOpen.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isEditOpen by viewModel.isEditOpen.collectAsStateWithLifecycle()
    val editingForm by viewModel.editingForm.collectAsStateWithLifecycle()

    val today = remember { LocalDate.now() }
    val weekStart = remember { WeekFields.of(Locale.getDefault()).firstDayOfWeek }
    val cells = remember(state.month, weekStart) { buildMonthCells(state.month, weekStart) }
    val weekdayNames = remember(weekStart) { weekdayLabels(weekStart, Locale.getDefault()) }
    val eventColorsByDay = remember(state.monthEvents) {
        val map = mutableMapOf<LocalDate, MutableList<Int>>()
        state.monthEvents.forEach { event ->
            val color = if (event.color != null && event.color != 0) event.color else 0xFF4285F4.toInt()
            val start = event.startDate
            val effectiveEndDate = if (!event.isAllDay && event.endTime == LocalTime.MIN && event.endDate.isAfter(event.startDate)) {
                event.endDate.minusDays(1)
            } else {
                event.endDate
            }
            val end = if (effectiveEndDate.isBefore(start)) start else effectiveEndDate
            val cappedEnd = if (start.plusDays(90).isBefore(end)) start.plusDays(90) else end
            var curr = start
            while (!curr.isAfter(cappedEnd)) {
                map.getOrPut(curr) { mutableListOf() }.add(color)
                curr = curr.plusDays(1)
            }
        }
        map
    }
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val timeFormatter = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    val context = LocalContext.current
    val is24Hour = remember(context) { android.text.format.DateFormat.is24HourFormat(context) }

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

    // Overlay Screen: Search
    if (isSearchOpen) {
        BackHandler { viewModel.closeSearch() }
        SearchScreen(
            query = searchQuery,
            results = searchResults,
            onQueryChanged = viewModel::onSearchQueryChanged,
            onEventClick = { inst ->
                viewModel.openEventDetail(inst.eventId, inst.startEpochMillis, inst.endEpochMillis)
            },
            onBack = viewModel::closeSearch,
        )
        return
    }

    // Overlay Screen: Event Detail
    if (activeDetail != null) {
        BackHandler { viewModel.closeEventDetail() }
        EventDetailScreen(
            detail = activeDetail!!,
            onBack = viewModel::closeEventDetail,
            onEdit = { viewModel.openEditEvent(activeDetail!!) },
            onDelete = { scope ->
                viewModel.deleteEventScoped(
                    activeDetail!!.instance.eventId,
                    scope,
                    activeDetail!!.instance.startEpochMillis,
                )
            },
        )
        return
    }

    // Overlay Screen: Event Edit / Create
    if (isEditOpen && editingForm != null) {
        BackHandler { viewModel.closeEditEvent() }
        EventEditScreen(
            initialForm = editingForm!!,
            calendars = state.calendars,
            onDismiss = viewModel::closeEditEvent,
            onSave = { form, scope ->
                viewModel.saveEventForm(form, scope)
            },
        )
        return
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
                    FloatingActionButton(onClick = { viewModel.openCreateEvent(state.selectedDay, null) }) {
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
            // Calendar filter, View Mode switcher, Search, Refresh, and Settings bar
            TopCalendarBar(
                currentViewMode = viewMode,
                calendars = state.calendars,
                selectedCalendarId = state.selectedCalendarId,
                isSyncing = state.isSyncing,
                onSelectViewMode = viewModel::setViewMode,
                onSelectCalendar = viewModel::filterByCalendar,
                onOpenSearch = viewModel::openSearch,
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

            // View Content Switcher
            when (viewMode) {
                CalendarViewMode.Month -> {
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
                        eventColorsByDay = eventColorsByDay,
                        onSelectDay = viewModel::select,
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    val allCalendarsDisabled = remember(state.calendars) {
                        state.calendars.isNotEmpty() && state.calendars.none { it.isEnabled }
                    }

                    // Day events list
                    DaySection(
                        date = state.selectedDay,
                        events = state.dayEvents,
                        dateFormatter = dateFormatter,
                        timeFormatter = timeFormatter,
                        allCalendarsDisabled = allCalendarsDisabled,
                        onOpenSettings = onOpenSettings,
                        onEventClick = { event ->
                            val zone = ZoneId.systemDefault()
                            val begin = if (event.isAllDay) {
                                event.startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                            } else {
                                event.startDate.atTime(event.startTime ?: LocalTime.of(9, 0)).atZone(zone).toInstant().toEpochMilli()
                            }
                            val end = if (event.isAllDay) {
                                event.endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                            } else {
                                event.endDate.atTime(event.endTime ?: LocalTime.of(10, 0)).atZone(zone).toInstant().toEpochMilli()
                            }
                            viewModel.openEventDetail(event.id, begin, end)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                CalendarViewMode.Week -> {
                    WeekScreen(
                        selectedDate = state.selectedDay,
                        instances = state.allInstances,
                        onEventClick = { inst ->
                            viewModel.openEventDetail(inst.eventId, inst.startEpochMillis, inst.endEpochMillis)
                        },
                        onTimeSlotClick = { date, time ->
                            viewModel.openCreateEvent(date, time)
                        },
                        onSelectDay = viewModel::select,
                        onPreviousWeek = viewModel::previousWeek,
                        onNextWeek = viewModel::nextWeek,
                        onToday = viewModel::goToToday,
                        modifier = Modifier.weight(1f),
                    )
                }

                CalendarViewMode.Day -> {
                    DayScreen(
                        date = state.selectedDay,
                        instances = state.allInstances,
                        onEventClick = { inst ->
                            viewModel.openEventDetail(inst.eventId, inst.startEpochMillis, inst.endEpochMillis)
                        },
                        onTimeSlotClick = { date, time ->
                            viewModel.openCreateEvent(date, time)
                        },
                        onPreviousDay = viewModel::previousDay,
                        onNextDay = viewModel::nextDay,
                        onToday = viewModel::goToToday,
                        modifier = Modifier.weight(1f),
                    )
                }

                CalendarViewMode.Agenda -> {
                    AgendaScreen(
                        instances = state.allInstances,
                        selectedDay = state.selectedDay,
                        onEventClick = { inst ->
                            viewModel.openEventDetail(inst.eventId, inst.startEpochMillis, inst.endEpochMillis)
                        },
                        onDayClick = { date ->
                            viewModel.select(date)
                            viewModel.setViewMode(CalendarViewMode.Day)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
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
    currentViewMode: CalendarViewMode,
    calendars: List<DeviceCalendar>,
    selectedCalendarId: Long?,
    isSyncing: Boolean,
    onSelectViewMode: (CalendarViewMode) -> Unit,
    onSelectCalendar: (Long?) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
) {
    var showDropdown by remember { mutableStateOf(false) }
    var showViewDropdown by remember { mutableStateOf(false) }
    val enabledCalendars = remember(calendars) { calendars.filter { it.isEnabled } }
    val disabledCalendars = remember(calendars) { calendars.filterNot { it.isEnabled } }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val isCompact = maxWidth < 380.dp
        val isVeryNarrow = maxWidth < 330.dp

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isCompact) 4.dp else 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (isCompact) 4.dp else 6.dp),
            ) {
                // View Mode Dropdown
                Box {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.clickable { showViewDropdown = true },
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = if (isCompact) 8.dp else 10.dp,
                                vertical = 6.dp,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            val modeIcon = when (currentViewMode) {
                                CalendarViewMode.Month -> Icons.Default.ViewModule
                                CalendarViewMode.Week -> Icons.Default.ViewWeek
                                CalendarViewMode.Day -> Icons.Default.ViewDay
                                CalendarViewMode.Agenda -> Icons.Default.ViewAgenda
                            }
                            Icon(
                                imageVector = modeIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp),
                            )
                            if (!isVeryNarrow) {
                                Text(
                                    text = currentViewMode.label,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                            Icon(
                                imageVector = Icons.Filled.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showViewDropdown,
                        onDismissRequest = { showViewDropdown = false },
                    ) {
                        CalendarViewMode.entries.forEach { mode ->
                            val itemIcon = when (mode) {
                                CalendarViewMode.Month -> Icons.Default.ViewModule
                                CalendarViewMode.Week -> Icons.Default.ViewWeek
                                CalendarViewMode.Day -> Icons.Default.ViewDay
                                CalendarViewMode.Agenda -> Icons.Default.ViewAgenda
                            }
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(itemIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                text = {
                                    Text(
                                        text = mode.label,
                                        fontWeight = if (mode == currentViewMode) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                                onClick = {
                                    onSelectViewMode(mode)
                                    showViewDropdown = false
                                },
                            )
                        }
                    }
                }

                // Calendar selection dropdown
                Box(modifier = Modifier.weight(1f, fill = false)) {
                    val selectedCalendar = calendars.firstOrNull { it.id == selectedCalendarId }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable { showDropdown = true },
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = if (isCompact) 8.dp else 10.dp,
                                vertical = 6.dp,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                                else -> stringResource(R.string.all_calendars)
                            }
                            Text(
                                text = labelText,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
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
                                Text(
                                    text = if (disabledCalendars.isNotEmpty()) {
                                        stringResource(R.string.all_enabled_calendars)
                                    } else {
                                        stringResource(R.string.all_calendars)
                                    },
                                    fontWeight = if (selectedCalendarId == null) FontWeight.Bold else FontWeight.Normal,
                                )
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
            }

            // Action buttons: Search, Refresh and Settings
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (isCompact) 0.dp else 2.dp),
            ) {
                IconButton(
                    onClick = onOpenSearch,
                    modifier = if (isCompact) Modifier.size(36.dp) else Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        modifier = if (isCompact) Modifier.size(18.dp) else Modifier.size(24.dp),
                    )
                }

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

                IconButton(
                    onClick = onRefresh,
                    modifier = if (isCompact) Modifier.size(36.dp) else Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.refresh),
                        modifier = (if (isCompact) Modifier.size(18.dp) else Modifier.size(24.dp))
                            .then(if (isSyncing) Modifier.rotate(rotation) else Modifier),
                    )
                }

                IconButton(
                    onClick = onOpenSettings,
                    modifier = if (isCompact) Modifier.size(36.dp) else Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.settings),
                        modifier = if (isCompact) Modifier.size(18.dp) else Modifier.size(24.dp),
                    )
                }
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
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val isCompact = maxWidth < 360.dp
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isCompact) 4.dp else 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                onClick = onPrevious,
                modifier = if (isCompact) Modifier.size(36.dp) else Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.previous_month),
                    modifier = if (isCompact) Modifier.size(20.dp) else Modifier.size(24.dp),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Transparent,
                    modifier = Modifier.clickable(onClick = onSelectMonth),
                ) {
                    Text(
                        text = month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                        style = if (isCompact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Transparent,
                    modifier = Modifier.clickable(onClick = onSelectYear),
                ) {
                    Text(
                        text = month.year.toString(),
                        style = if (isCompact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }
            IconButton(
                onClick = onNext,
                modifier = if (isCompact) Modifier.size(36.dp) else Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.next_month),
                    modifier = if (isCompact) Modifier.size(20.dp) else Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun WeekdayLabelRow(labels: List<String>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        labels.forEach { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (events.isNotEmpty()) {
                Text(
                    text = "${events.size} event${if (events.size > 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
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
                        text = event.title.ifBlank { "(Untitled Event)" },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    event.calendarName?.let { calName ->
                        Text(
                            text = calName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val timeText = if (event.isAllDay) {
                        stringResource(R.string.all_day)
                    } else if (event.startTime != null && event.endTime != null) {
                        "${event.startTime.format(timeFormatter)} – ${event.endTime.format(timeFormatter)}"
                    } else if (event.startTime != null) {
                        event.startTime.format(timeFormatter)
                    } else {
                        stringResource(R.string.all_day)
                    }

                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (!event.location.isNullOrBlank()) {
                        Text(
                            text = event.location,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionBanner(
    onRequestPermission: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.permission_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = stringResource(R.string.permission_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.grant_permission))
            }
        }
    }
}

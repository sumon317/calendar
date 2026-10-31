package com.sumon.calc.ui.month

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sumon.calc.R
import com.sumon.calc.data.Event
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

@Composable
fun MonthScreen(viewModel: CalendarViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }
    val weekStart = remember { WeekFields.of(Locale.getDefault()).firstDayOfWeek }
    val cells = remember(state.month, weekStart) { buildMonthCells(state.month, weekStart) }
    val weekdayNames = remember(weekStart) { weekdayLabels(weekStart, Locale.getDefault()) }
    val eventDays = remember(state.monthEvents) { state.monthEvents.map { it.date }.toSet() }
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val timeFormatter = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    val context = LocalContext.current
    val is24Hour = remember(context) { android.text.format.DateFormat.is24HourFormat(context) }

    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Event?>(null) }
    var pickingMonth by remember { mutableStateOf(false) }
    var pickingYear by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            // "Today" sits above the add-FAB, but only while the calendar is
            // somewhere else — on today it vanishes to keep the corner clean.
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
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 4.dp),
        ) {
            MonthHeader(
                month = state.month,
                onPrevious = viewModel::previousMonth,
                onNext = viewModel::nextMonth,
                onSelectMonth = { pickingMonth = true },
                onSelectYear = { pickingYear = true },
            )
            WeekdayLabelRow(weekdayNames)
            MonthGrid(
                cells = cells,
                selectedDay = state.selectedDay,
                today = today,
                eventDays = eventDays,
                onSelectDay = viewModel::select,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            DaySection(
                date = state.selectedDay,
                events = state.dayEvents,
                dateFormatter = dateFormatter,
                timeFormatter = timeFormatter,
                onEventClick = { editing = it },
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (showAdd) {
        AddEventDialog(
            initialDate = state.selectedDay,
            is24Hour = is24Hour,
            dateFormatter = dateFormatter,
            timeFormatter = timeFormatter,
            onDismiss = { showAdd = false },
            onSave = { title, date, time ->
                viewModel.save(Event(title = title, date = date, time = time))
                showAdd = false
            },
        )
    }

    editing?.let { target ->
        AddEventDialog(
            initialEvent = target,
            initialDate = target.date,
            is24Hour = is24Hour,
            dateFormatter = dateFormatter,
            timeFormatter = timeFormatter,
            onDismiss = { editing = null },
            onSave = { title, date, time ->
                viewModel.save(Event(id = target.id, title = title, date = date, time = time))
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
                imageVector = Icons.Filled.KeyboardArrowLeft,
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
                imageVector = Icons.Filled.KeyboardArrowRight,
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
    onEventClick: (Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = date.format(dateFormatter),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
        if (events.isEmpty()) {
            Text(
                text = stringResource(R.string.no_events_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(events, key = { it.id }) { event ->
                    EventRow(
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
private fun EventRow(
    event: Event,
    timeFormatter: DateTimeFormatter,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = event.time?.format(timeFormatter) ?: stringResource(R.string.all_day),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

package com.sumon.calc.ui.month

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.sumon.calc.R
import com.sumon.calc.data.Event
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private const val MILLIS_PER_DAY = 86_400_000L

/** Dates cross into the picker as UTC-midnight millis and back — see toPickerMillis(). */
private fun LocalDate.toPickerMillis(): Long = toEpochDay() * MILLIS_PER_DAY

private fun pickerMillisToLocalDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC).toLocalDate()

/**
 * Add-or-edit dialog. Pass [initialEvent] to edit an existing event; null adds
 * a new one on [initialDate]. [onDelete] is only supplied for existing events.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEventDialog(
    initialDate: LocalDate,
    is24Hour: Boolean,
    dateFormatter: DateTimeFormatter,
    timeFormatter: DateTimeFormatter,
    initialEvent: Event? = null,
    onDismiss: () -> Unit,
    onSave: (title: String, date: LocalDate, time: LocalTime?) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var title by remember { mutableStateOf(initialEvent?.title.orEmpty()) }
    var date by remember { mutableStateOf(initialEvent?.date ?: initialDate) }
    var time by remember {
        mutableStateOf(initialEvent?.time ?: LocalTime.now().truncatedTo(ChronoUnit.MINUTES))
    }
    var hasTime by remember { mutableStateOf(initialEvent?.time != null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.title_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { showDatePicker = true }) {
                    Text(stringResource(R.string.date_label, date.format(dateFormatter)))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = hasTime,
                        onCheckedChange = { checked ->
                            hasTime = checked
                            if (checked) time = LocalTime.now().truncatedTo(ChronoUnit.MINUTES)
                        },
                    )
                    Text(
                        text = stringResource(R.string.add_time),
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                    TextButton(
                        enabled = hasTime,
                        onClick = { showTimePicker = true },
                    ) {
                        Text(
                            text = time.format(timeFormatter),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = { onSave(title.trim(), date, time.takeIf { hasTime }) },
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

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = date.toPickerMillis())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { date = pickerMillisToLocalDate(it) }
                    showDatePicker = false
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = time.hour,
            initialMinute = time.minute,
            is24Hour = is24Hour,
        )
        Dialog(onDismissRequest = { showTimePicker = false }) {
            Surface(shape = MaterialTheme.shapes.large) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.pick_time),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    TimePicker(state = timeState)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { showTimePicker = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                        TextButton(onClick = {
                            time = LocalTime.of(timeState.hour, timeState.minute)
                            showTimePicker = false
                        }) {
                            Text(stringResource(R.string.ok))
                        }
                    }
                }
            }
        }
    }
}

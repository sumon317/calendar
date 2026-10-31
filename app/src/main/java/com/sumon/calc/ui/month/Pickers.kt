package com.sumon.calc.ui.month

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sumon.calc.R
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/**
 * Scroll-and-tap month chooser, showing the twelve months of the currently
 * displayed year. Opens pre-scrolled to [selectedMonth] (1–12).
 */
@Composable
fun MonthPickerDialog(
    selectedMonth: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val monthNames = remember {
        Month.values().map { it.getDisplayName(TextStyle.FULL, Locale.getDefault()) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_month)) },
        text = {
            val listState = rememberLazyListState(
                initialFirstVisibleItemIndex = (selectedMonth - 1).coerceIn(0, 11),
            )
            LazyColumn(state = listState, modifier = Modifier.height(320.dp)) {
                itemsIndexed(monthNames) { index, name ->
                    SelectRow(
                        label = name,
                        isSelected = index + 1 == selectedMonth,
                        onClick = { onSelect(index + 1) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

private const val FIRST_YEAR = 1900
private const val LAST_YEAR = 2100

/**
 * Scroll-and-tap year chooser spanning [FIRST_YEAR]–[LAST_YEAR], opened
 * pre-scrolled to [selectedYear].
 */
@Composable
fun YearPickerDialog(
    selectedYear: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val years = remember { (FIRST_YEAR..LAST_YEAR).toList() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_year)) },
        text = {
            val listState = rememberLazyListState(
                initialFirstVisibleItemIndex =
                    (selectedYear - FIRST_YEAR).coerceIn(0, years.lastIndex),
            )
            LazyColumn(state = listState, modifier = Modifier.height(320.dp)) {
                items(years) { year ->
                    SelectRow(
                        label = year.toString(),
                        isSelected = year == selectedYear,
                        onClick = { onSelect(year) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun SelectRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isSelected) colorScheme.primary else colorScheme.onSurface,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

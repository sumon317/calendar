package com.sumon.calc.ui.month

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumon.calc.data.Event
import com.sumon.calc.data.EventRepository
import com.sumon.calc.data.toEvent
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(private val repository: EventRepository) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())
    private val selectedDay = MutableStateFlow(LocalDate.now())

    private val monthEvents = month
        .flatMapLatest { repository.observeEventsIn(it) }
        .map { entities -> entities.map { it.toEvent() } }

    private val dayEvents = selectedDay
        .flatMapLatest { repository.observeEventsOn(it) }
        .map { entities -> entities.map { it.toEvent() } }

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(month, selectedDay, monthEvents, dayEvents) { month, day, monthEvents, dayEvents ->
                CalendarUiState(month, day, monthEvents, dayEvents)
            }.collect { _uiState.value = it }
        }
    }

    fun previousMonth() = month.update { it.minusMonths(1) }

    fun nextMonth() = month.update { it.plusMonths(1) }

    fun selectMonth(monthValue: Int) = month.update { it.withMonth(monthValue) }

    fun selectYear(year: Int) = month.update { it.withYear(year) }

    fun goToToday() {
        val today = LocalDate.now()
        selectedDay.value = today
        month.update { YearMonth.from(today) }
    }

    fun select(day: LocalDate) {
        selectedDay.value = day
    }

    fun save(event: Event) = viewModelScope.launch { repository.save(event) }

    fun delete(eventId: Long) = viewModelScope.launch { repository.delete(eventId) }
}

data class CalendarUiState(
    val month: YearMonth = YearMonth.now(),
    val selectedDay: LocalDate = LocalDate.now(),
    val monthEvents: List<Event> = emptyList(),
    val dayEvents: List<Event> = emptyList(),
)

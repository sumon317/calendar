package com.sumon.calendar.ui.month

import android.accounts.Account
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumon.calendar.data.DeviceCalendar
import com.sumon.calendar.data.Event
import com.sumon.calendar.data.LocalCalendarRepository
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CalendarViewModel(
    private val repository: LocalCalendarRepository,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())
    private val selectedDay = MutableStateFlow(LocalDate.now())
    private val hasPermission = MutableStateFlow(repository.hasPermissions())
    private val selectedCalendarId = MutableStateFlow<Long?>(null)
    private val manualRefreshTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val _uiState = MutableStateFlow(
        CalendarUiState(
            month = month.value,
            selectedDay = selectedDay.value,
            hasPermission = hasPermission.value,
        ),
    )
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    init {
        checkPermissions()
        observeCalendar()
    }

    fun checkPermissions() {
        val granted = repository.hasPermissions()
        hasPermission.value = granted
        _uiState.update { it.copy(hasPermission = granted) }
    }

    fun onPermissionResult(isGranted: Boolean) {
        hasPermission.value = isGranted
        _uiState.update { it.copy(hasPermission = isGranted) }
        if (isGranted) {
            refresh()
        }
    }

    private fun observeCalendar() {
        viewModelScope.launch {
            // Re-run whenever permissions change, month/day change, calendar filter changes,
            // or ContentObserver emits a change from this app or Google Calendar sync.
            val calendarChanges = repository.observeCalendarChanges()
            val manualChanges = manualRefreshTrigger.onStart { emit(Unit) }

            // combine() only has built-in overloads up to 5 flows; nest two calls.
            val baseParams = combine(hasPermission, month, selectedDay, selectedCalendarId) {
                permitted, currentMonth, currentDay, calFilter ->
                CalendarQueryParameters(permitted, currentMonth, currentDay, calFilter)
            }

            combine(baseParams, calendarChanges, manualChanges) { params, _, _ -> params }
                .distinctUntilChanged()
                .collect { params ->
                    if (!params.hasPermission) {
                        _uiState.update {
                            it.copy(
                                month = params.month,
                                selectedDay = params.selectedDay,
                                monthEvents = emptyList(),
                                dayEvents = emptyList(),
                                calendars = emptyList(),
                                googleAccounts = emptyList(),
                                selectedCalendarId = params.calendarFilter,
                                hasPermission = false,
                                isLoading = false,
                            )
                        }
                        return@collect
                    }

                    _uiState.update { it.copy(isLoading = true) }

                    val calendars = repository.getCalendars()
                    val googleAccounts = repository.getGoogleAccounts()

                    val (monthStart, monthEnd) = LocalCalendarRepository.monthToMillisRange(params.month)
                    val monthEvents = repository.getEventsInRange(monthStart, monthEnd, params.calendarFilter)

                    val (dayStart, dayEnd) = LocalCalendarRepository.dayToMillisRange(params.selectedDay)
                    val dayEventsRaw = repository.getEventsInRange(dayStart, dayEnd, params.calendarFilter)

                    // Enrich day events with reminder info
                    val dayEvents = dayEventsRaw.map { event ->
                        val reminder = repository.getReminderMinutes(event.id)
                        event.copy(reminderMinutes = reminder)
                    }

                    _uiState.update {
                        it.copy(
                            month = params.month,
                            selectedDay = params.selectedDay,
                            monthEvents = monthEvents,
                            dayEvents = dayEvents,
                            calendars = calendars,
                            googleAccounts = googleAccounts,
                            selectedCalendarId = params.calendarFilter,
                            hasPermission = true,
                            isLoading = false,
                        )
                    }
                }
        }
    }

    fun refresh() {
        checkPermissions()
        manualRefreshTrigger.tryEmit(Unit)
    }

    fun syncWithGoogle(account: Account? = null) = viewModelScope.launch {
        _uiState.update { it.copy(isSyncing = true) }
        repository.syncGoogleCalendar(account)
        refresh()
        _uiState.update { it.copy(isSyncing = false) }
    }

    fun createGoogleLoginIntent(): Intent = repository.createGoogleLoginIntent()

    fun createSyncSettingsIntent(): Intent = repository.createSyncSettingsIntent()

    fun createAppSettingsIntent(): Intent = repository.createAppSettingsIntent()

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

    fun filterByCalendar(calendarId: Long?) {
        selectedCalendarId.value = calendarId
    }

    fun save(event: Event) = viewModelScope.launch {
        repository.saveEvent(event)
        refresh()
    }

    fun delete(eventId: Long) = viewModelScope.launch {
        repository.deleteEvent(eventId)
        refresh()
    }

    fun loadEventForEditing(event: Event, onLoaded: (Event) -> Unit) = viewModelScope.launch {
        val reminder = repository.getReminderMinutes(event.id)
        onLoaded(event.copy(reminderMinutes = reminder))
    }
}

private data class CalendarQueryParameters(
    val hasPermission: Boolean,
    val month: YearMonth,
    val selectedDay: LocalDate,
    val calendarFilter: Long?,
)

data class CalendarUiState(
    val month: YearMonth = YearMonth.now(),
    val selectedDay: LocalDate = LocalDate.now(),
    val monthEvents: List<Event> = emptyList(),
    val dayEvents: List<Event> = emptyList(),
    val calendars: List<DeviceCalendar> = emptyList(),
    val googleAccounts: List<Account> = emptyList(),
    val selectedCalendarId: Long? = null,
    val hasPermission: Boolean = false,
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
)

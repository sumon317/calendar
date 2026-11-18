package com.sumon.calendar.ui.month

import android.accounts.Account
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumon.calendar.data.DeviceCalendar
import com.sumon.calendar.data.Event
import com.sumon.calendar.data.LocalCalendarRepository
import com.sumon.calendar.domain.CalendarSource
import com.sumon.calendar.domain.CalendarViewMode
import com.sumon.calendar.domain.EventDetail
import com.sumon.calendar.domain.EventForm
import com.sumon.calendar.domain.EventInstance
import com.sumon.calendar.domain.RecurringWriteScope
import com.sumon.calendar.ui.theme.ColorPalettePreset
import com.sumon.calendar.ui.theme.ThemeMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CalendarViewModel(
    private val repository: LocalCalendarRepository,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())
    private val selectedDay = MutableStateFlow(LocalDate.now())
    private val hasPermission = MutableStateFlow(repository.hasPermissions())
    private val selectedCalendarId = MutableStateFlow<Long?>(null)
    private val disabledCalendarIds = MutableStateFlow(repository.getDisabledCalendarIds())
    private val refreshTrigger = MutableStateFlow(0L)

    private val _viewMode = MutableStateFlow(CalendarViewMode.Month)
    val viewMode: StateFlow<CalendarViewMode> = _viewMode.asStateFlow()

    private val _themeMode = MutableStateFlow(repository.getThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _colorPalette = MutableStateFlow(repository.getColorPalette())
    val colorPalette: StateFlow<ColorPalettePreset> = _colorPalette.asStateFlow()

    // Event Detail Screen state
    private val _activeDetail = MutableStateFlow<EventDetail?>(null)
    val activeDetail: StateFlow<EventDetail?> = _activeDetail.asStateFlow()

    // Search Screen state
    private val _isSearchOpen = MutableStateFlow(false)
    val isSearchOpen: StateFlow<Boolean> = _isSearchOpen.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<EventInstance>>(emptyList())
    val searchResults: StateFlow<List<EventInstance>> = _searchResults.asStateFlow()

    // Event Edit / Create state
    private val _isEditOpen = MutableStateFlow(false)
    val isEditOpen: StateFlow<Boolean> = _isEditOpen.asStateFlow()

    private val _editingForm = MutableStateFlow<EventForm?>(null)
    val editingForm: StateFlow<EventForm?> = _editingForm.asStateFlow()

    private val _occurrenceBeginMillis = MutableStateFlow(0L)
    val occurrenceBeginMillis: StateFlow<Long> = _occurrenceBeginMillis.asStateFlow()

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

    fun setViewMode(mode: CalendarViewMode) {
        _viewMode.value = mode
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
            val calendarChanges = repository.observeCalendarChanges()

            val baseParams = combine(
                hasPermission,
                month,
                selectedDay,
                selectedCalendarId,
                disabledCalendarIds,
            ) { permitted, currentMonth, currentDay, calFilter, disabledIds ->
                CalendarQueryParameters(permitted, currentMonth, currentDay, calFilter, disabledIds)
            }.distinctUntilChanged()

            combine(baseParams, calendarChanges, refreshTrigger) { params, _, _ -> params }
                .collect { params ->
                    if (!params.hasPermission) {
                        _uiState.update {
                            it.copy(
                                month = params.month,
                                selectedDay = params.selectedDay,
                                monthEvents = emptyList(),
                                dayEvents = emptyList(),
                                allInstances = emptyList(),
                                calendars = emptyList(),
                                calendarSources = emptyList(),
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
                    val calendarSources = repository.getCalendarSources()
                    val googleAccounts = repository.getGoogleAccounts()
                    val enabledCalendarIds = calendars.filter { it.isEnabled }.map { it.id }.toSet()

                    val effectiveFilter = if (params.calendarFilter != null && params.calendarFilter in enabledCalendarIds) {
                        params.calendarFilter
                    } else null

                    // Query wide range (-30 days to +60 days) for Agenda, Week, and Month grids
                    val rangeStart = params.month.atDay(1).minusDays(30).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val rangeEnd = params.month.atEndOfMonth().plusDays(60).atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

                    val instances = repository.getEventInstancesInRange(rangeStart, rangeEnd, effectiveFilter, enabledCalendarIds)

                    val (monthStart, monthEnd) = LocalCalendarRepository.monthToMillisRange(params.month)
                    val monthEvents = repository.getEventsInRange(monthStart, monthEnd, effectiveFilter, enabledCalendarIds)

                    val (dayStart, dayEnd) = LocalCalendarRepository.dayToMillisRange(params.selectedDay)
                    val dayEventsRaw = repository.getEventsInRange(dayStart, dayEnd, effectiveFilter, enabledCalendarIds)

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
                            allInstances = instances,
                            calendars = calendars,
                            calendarSources = calendarSources,
                            googleAccounts = googleAccounts,
                            selectedCalendarId = effectiveFilter,
                            hasPermission = true,
                            isLoading = false,
                        )
                    }
                }
        }
    }

    fun refresh() {
        checkPermissions()
        refreshTrigger.update { it + 1 }
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

    fun previousDay() {
        val prev = selectedDay.value.minusDays(1)
        selectedDay.value = prev
        month.update { YearMonth.from(prev) }
    }

    fun nextDay() {
        val next = selectedDay.value.plusDays(1)
        selectedDay.value = next
        month.update { YearMonth.from(next) }
    }

    fun previousWeek() {
        val prev = selectedDay.value.minusWeeks(1)
        selectedDay.value = prev
        month.update { YearMonth.from(prev) }
    }

    fun nextWeek() {
        val next = selectedDay.value.plusWeeks(1)
        selectedDay.value = next
        month.update { YearMonth.from(next) }
    }

    fun goToToday() {
        val today = LocalDate.now()
        selectedDay.value = today
        month.update { YearMonth.from(today) }
    }

    fun select(day: LocalDate) {
        selectedDay.value = day
        if (YearMonth.from(day) != month.value) {
            month.value = YearMonth.from(day)
        }
    }

    fun filterByCalendar(calendarId: Long?) {
        selectedCalendarId.value = calendarId
    }

    fun setThemeMode(mode: ThemeMode) {
        repository.setThemeMode(mode)
        _themeMode.value = mode
    }

    fun setColorPalette(preset: ColorPalettePreset) {
        repository.setColorPalette(preset)
        _colorPalette.value = preset
    }

    fun toggleCalendarEnabled(calendarId: Long, isEnabled: Boolean) {
        repository.setCalendarEnabled(calendarId, isEnabled)
        disabledCalendarIds.value = repository.getDisabledCalendarIds()
        _uiState.update { current ->
            val updated = current.calendars.map {
                if (it.id == calendarId) it.copy(isEnabled = isEnabled) else it
            }
            val enabledIds = updated.filter { it.isEnabled }.map { it.id }.toSet()
            val effectiveFilter = if (current.selectedCalendarId != null && current.selectedCalendarId in enabledIds) {
                current.selectedCalendarId
            } else null
            current.copy(
                calendars = updated,
                selectedCalendarId = effectiveFilter,
            )
        }
        refresh()
    }

    fun enableAllCalendars() {
        val allCals = _uiState.value.calendars
        repository.setAllCalendarsEnabled(allCals, true)
        disabledCalendarIds.value = emptySet()
        _uiState.update { current ->
            val updated = current.calendars.map { it.copy(isEnabled = true) }
            current.copy(calendars = updated)
        }
        refresh()
    }

    fun disableAllCalendars() {
        val allCals = _uiState.value.calendars
        repository.setAllCalendarsEnabled(allCals, false)
        disabledCalendarIds.value = repository.getDisabledCalendarIds()
        _uiState.update { current ->
            val updated = current.calendars.map { it.copy(isEnabled = false) }
            current.copy(
                calendars = updated,
                selectedCalendarId = null,
            )
        }
        refresh()
    }

    // Detail Screen Operations
    fun openEventDetail(eventId: Long, beginMillis: Long, endMillis: Long) = viewModelScope.launch {
        val detail = repository.queryEventDetail(eventId, beginMillis, endMillis)
        _activeDetail.value = detail
    }

    fun closeEventDetail() {
        _activeDetail.value = null
    }

    // Search Operations
    fun openSearch() {
        _isSearchOpen.value = true
        _searchQuery.value = ""
        _searchResults.value = emptyList()
    }

    fun closeSearch() {
        _isSearchOpen.value = false
        _searchQuery.value = ""
        _searchResults.value = emptyList()
    }

    fun onSearchQueryChanged(query: String) = viewModelScope.launch {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = emptyList()
        } else {
            val results = repository.searchEvents(query)
            _searchResults.value = results
        }
    }

    // Edit & Create Operations
    fun openCreateEvent(presetDate: LocalDate? = null, presetStartTime: LocalTime? = null) = viewModelScope.launch {
        val date = presetDate ?: selectedDay.value
        val startTime = presetStartTime ?: LocalTime.of(9, 0)
        val defaultCalId = repository.getOrCreateDefaultCalendarId()

        _occurrenceBeginMillis.value = 0L
        _editingForm.value = EventForm(
            eventId = null,
            calendarId = defaultCalId,
            title = "",
            startDateTime = LocalDateTime.of(date, startTime),
            endDateTime = LocalDateTime.of(date, startTime.plusHours(1)),
            isAllDay = false,
            reminders = listOf(10),
        )
        _isEditOpen.value = true
    }

    fun openEditEvent(detail: EventDetail) {
        val zone = ZoneId.systemDefault()
        val startLocal = detail.instance.startInstant().atZone(zone).toLocalDateTime()
        val endLocal = detail.instance.endInstant().atZone(zone).toLocalDateTime()

        _occurrenceBeginMillis.value = detail.instance.startEpochMillis
        _editingForm.value = EventForm(
            eventId = detail.instance.eventId,
            calendarId = detail.instance.calendarId,
            title = detail.instance.title,
            description = detail.description.orEmpty(),
            location = detail.instance.location.orEmpty(),
            startDateTime = startLocal,
            endDateTime = endLocal,
            isAllDay = detail.instance.isAllDay,
            rrule = detail.rrule,
            reminders = detail.reminders.map { it.minutes },
            availability = detail.availability,
            accessLevel = detail.accessLevel,
            status = detail.status,
            color = detail.instance.color,
            timezone = detail.eventTimezone ?: zone.id,
        )
        _isEditOpen.value = true
    }

    fun closeEditEvent() {
        _isEditOpen.value = false
        _editingForm.value = null
        _occurrenceBeginMillis.value = 0L
    }

    fun saveEventForm(
        form: EventForm,
        scope: RecurringWriteScope = RecurringWriteScope.AllEvents,
    ) = viewModelScope.launch {
        repository.saveEventAdvanced(form, scope, _occurrenceBeginMillis.value)
        closeEditEvent()
        closeEventDetail()
        refresh()
    }

    fun deleteEventScoped(
        eventId: Long,
        scope: RecurringWriteScope = RecurringWriteScope.AllEvents,
        beginMillis: Long = 0L,
    ) = viewModelScope.launch {
        repository.deleteEventScoped(eventId, scope, beginMillis)
        closeEventDetail()
        refresh()
    }

    // Quick add backward compatibility
    fun save(event: Event) = viewModelScope.launch {
        repository.saveEvent(event)
        refresh()
    }

    fun delete(eventId: Long) = viewModelScope.launch {
        repository.deleteEvent(eventId)
        refresh()
    }

    suspend fun exportIcs(): String = repository.exportCalendarIcs()

    suspend fun importIcs(content: String, targetCalendarId: Long): Int {
        val count = repository.importIcs(content, targetCalendarId)
        if (count > 0) refresh()
        return count
    }
}

private data class CalendarQueryParameters(
    val hasPermission: Boolean,
    val month: YearMonth,
    val selectedDay: LocalDate,
    val calendarFilter: Long?,
    val disabledCalendarIds: Set<Long> = emptySet(),
)

data class CalendarUiState(
    val month: YearMonth = YearMonth.now(),
    val selectedDay: LocalDate = LocalDate.now(),
    val monthEvents: List<Event> = emptyList(),
    val dayEvents: List<Event> = emptyList(),
    val allInstances: List<EventInstance> = emptyList(),
    val calendars: List<DeviceCalendar> = emptyList(),
    val calendarSources: List<CalendarSource> = emptyList(),
    val googleAccounts: List<Account> = emptyList(),
    val selectedCalendarId: Long? = null,
    val hasPermission: Boolean = false,
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
)

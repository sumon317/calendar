package com.sumon.calc.data

import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow

/** The only API the UI layer should know about. */
class EventRepository(private val dao: EventDao) {

    fun observeEventsIn(month: YearMonth): Flow<List<EventEntity>> {
        val range = month.toEpochDayRange()
        return dao.observeRange(range.first, range.last)
    }

    fun observeEventsOn(day: LocalDate): Flow<List<EventEntity>> =
        dao.observeDay(day.toEpochDay())

    /** Inserts [event] as new when its id is 0, or rewrites the existing row. */
    suspend fun save(event: Event): Long = dao.upsert(event.toEntity())

    suspend fun delete(eventId: Long) = dao.deleteById(eventId)
}

/**
 * Epoch days of the first and last day of [month], inclusive — the half-open
 * range problem solved once instead of at every query site.
 */
fun YearMonth.toEpochDayRange(): LongRange =
    atDay(1).toEpochDay()..atEndOfMonth().toEpochDay()

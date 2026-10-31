package com.sumon.calc.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {

    /**
     * All events with [fromEpochDay] <= event day <= [toEpochDay], soonest first.
     * Timed events sort before all-day events on the same day (`minutesOfDay
     * IS NULL` yields 0 for a time, 1 for an all-day event).
     */
    @Query(
        """
        SELECT * FROM events
        WHERE epochDay BETWEEN :fromEpochDay AND :toEpochDay
        ORDER BY epochDay, minutesOfDay IS NULL, minutesOfDay
        """,
    )
    fun observeRange(fromEpochDay: Long, toEpochDay: Long): Flow<List<EventEntity>>

    @Query(
        """
        SELECT * FROM events
        WHERE epochDay = :epochDay
        ORDER BY minutesOfDay IS NULL, minutesOfDay
        """,
    )
    fun observeDay(epochDay: Long): Flow<List<EventEntity>>

    /** Inserts a new event (id 0) or rewrites an existing one; returns the row id. */
    @Upsert
    suspend fun upsert(event: EventEntity): Long

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteById(id: Long)
}

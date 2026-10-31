package com.sumon.calc.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted form of an [Event]. Dates are stored as integer epoch days so that
 * month/day queries are simple integer ranges, free of time-zone questions.
 * The optional time of day is stored as minutes-since-midnight, so an event is
 * "all-day" iff [minutesOfDay] is null.
 */
@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val epochDay: Long,
    val minutesOfDay: Int?,
)

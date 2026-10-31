package com.sumon.calc.data

import java.time.LocalDate
import java.time.LocalTime

/** What the UI works with: human-shaped types instead of persisted integers. */
data class Event(
    val id: Long = 0,
    val title: String,
    val date: LocalDate,
    val time: LocalTime? = null,
)

fun EventEntity.toEvent(): Event = Event(
    id = id,
    title = title,
    date = LocalDate.ofEpochDay(epochDay),
    time = minutesOfDay?.let { LocalTime.of(it / 60, it % 60) },
)

fun Event.toEntity(): EventEntity = EventEntity(
    id = id,
    title = title.trim(),
    epochDay = date.toEpochDay(),
    minutesOfDay = time?.let { it.hour * 60 + it.minute },
)

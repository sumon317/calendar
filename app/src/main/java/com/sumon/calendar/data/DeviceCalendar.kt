package com.sumon.calendar.data

/**
 * Represents a calendar account or local device calendar available on the Android system.
 */
data class DeviceCalendar(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val accountType: String,
    val color: Int,
    val isPrimary: Boolean = false,
)

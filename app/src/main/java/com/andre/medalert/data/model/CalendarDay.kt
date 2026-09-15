package com.andre.medalert.data.model

enum class DayDoseStatus { TAKEN, LATE, MISSED }

data class CalendarDay(
    val day: Int?,
    val status: DayDoseStatus? = null,
    val isToday: Boolean = false
)
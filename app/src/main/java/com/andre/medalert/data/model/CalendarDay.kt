package com.andre.medalert.data.model

enum class DayDoseStatus { TAKEN, LATE, MISSED }

/**
 * [hasDoses]: o dia tem doses PREVISTAS (calculadas dos medicamentos).
 * [status] só será preenchido quando houver histórico de administração por data.
 */
data class CalendarDay(
    val day: Int?,
    val status: DayDoseStatus? = null,
    val isToday: Boolean = false,
    val hasDoses: Boolean = false
)
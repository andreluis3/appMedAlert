package com.andre.medalert.data.mock

import com.andre.medalert.data.model.CalendarDay
import com.andre.medalert.data.model.DayDoseStatus

val mockCalendarMonthLabel = "Agosto 2026"

val mockCalendarDays: List<CalendarDay> = buildList {
    // Agosto/2026 começa num sábado -> 6 células vazias antes do dia 1
    repeat(6) { add(CalendarDay(day = null)) }

    add(CalendarDay(day = 1, isToday = true))
    add(CalendarDay(day = 2, status = DayDoseStatus.TAKEN))
    add(CalendarDay(day = 3, status = DayDoseStatus.TAKEN))
    add(CalendarDay(day = 4, status = DayDoseStatus.TAKEN))
    add(CalendarDay(day = 5, status = DayDoseStatus.LATE))
    add(CalendarDay(day = 6, status = DayDoseStatus.TAKEN))
    add(CalendarDay(day = 7, status = DayDoseStatus.TAKEN))
    add(CalendarDay(day = 8, status = DayDoseStatus.TAKEN))
    add(CalendarDay(day = 9, status = DayDoseStatus.MISSED))
    add(CalendarDay(day = 10, status = DayDoseStatus.LATE))
    add(CalendarDay(day = 11, status = DayDoseStatus.TAKEN))
    add(CalendarDay(day = 12, status = DayDoseStatus.TAKEN))
    add(CalendarDay(day = 13, status = DayDoseStatus.TAKEN))
    add(CalendarDay(day = 14, status = DayDoseStatus.TAKEN))
    add(CalendarDay(day = 15, status = DayDoseStatus.LATE))
    add(CalendarDay(day = 16, status = DayDoseStatus.TAKEN))
    add(CalendarDay(day = 17, status = DayDoseStatus.TAKEN))
    add(CalendarDay(day = 18, status = DayDoseStatus.MISSED))
    add(CalendarDay(day = 19, status = DayDoseStatus.TAKEN))
    add(CalendarDay(day = 20, status = DayDoseStatus.LATE))
    add(CalendarDay(day = 21, status = DayDoseStatus.TAKEN))
    add(CalendarDay(day = 22, status = DayDoseStatus.TAKEN))
    for (d in 23..31) add(CalendarDay(day = d))
}
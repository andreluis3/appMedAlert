package com.andre.medalert.data.schedule

import com.andre.medalert.data.model.HistoryDoseStatus
import java.time.LocalDate
import java.time.YearMonth

/** Situação de um dia do calendário, resumida a partir das doses daquele dia. */
enum class CalendarDayStatus {
    /** Nenhuma dose neste dia. */
    NONE,

    /** Há doses a tomar e nenhuma atrasada ou perdida. */
    PENDING,

    /** Todas as doses do dia foram tomadas no prazo. */
    ALL_TAKEN,

    /** Há dose atrasada ou tomada com atraso (e nenhuma perdida). */
    LATE,

    /** Há pelo menos uma dose não tomada. */
    MISSED
}

/** Uma célula do calendário. [day] nulo = espaço em branco da grade. */
data class CalendarDayUi(
    val day: Int?,
    val isToday: Boolean = false,
    val status: CalendarDayStatus = CalendarDayStatus.NONE
) {
    val hasDoses: Boolean get() = status != CalendarDayStatus.NONE
}

object CalendarSummary {

    /** Prioridade: não tomada > atrasada/com atraso > pendente > tudo tomado. */
    fun statusOf(events: List<DoseEvent>): CalendarDayStatus = when {
        events.isEmpty() -> CalendarDayStatus.NONE
        events.any { it.status == HistoryDoseStatus.NAO_TOMADO } -> CalendarDayStatus.MISSED
        events.any {
            it.status == HistoryDoseStatus.ATRASADO || it.status == HistoryDoseStatus.TOMADO_COM_ATRASO
        } -> CalendarDayStatus.LATE
        events.any { it.status == HistoryDoseStatus.PENDENTE } -> CalendarDayStatus.PENDING
        else -> CalendarDayStatus.ALL_TAKEN
    }

    /** Grade do mês (domingo = coluna 0), completa até fechar a última semana. */
    fun cellsFor(month: YearMonth, events: List<DoseEvent>, today: LocalDate): List<CalendarDayUi> {
        val eventsByDay = events
            .filter { YearMonth.from(it.scheduledAt) == month }
            .groupBy { it.scheduledAt.dayOfMonth }

        return buildList {
            repeat(month.atDay(1).dayOfWeek.value % 7) { add(CalendarDayUi(day = null)) }
            for (d in 1..month.lengthOfMonth()) {
                add(
                    CalendarDayUi(
                        day = d,
                        isToday = month.atDay(d) == today,
                        status = statusOf(eventsByDay[d].orEmpty())
                    )
                )
            }
            while (size % 7 != 0) add(CalendarDayUi(day = null))
        }
    }
}

package com.andre.medalert.data.schedule

import com.andre.medalert.data.model.HistoryDoseStatus
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Motor de adesão mensal. Kotlin puro (sem Android), por isso testável em JVM.
 *
 * Entrada: as doses do mês já montadas pelo [DoseTimelineBuilder] (a mesma fonte do Histórico e
 * do Calendário, com as mesmas regras de [DoseStatusRules]) e, opcionalmente, os adiamentos.
 *
 * Regras:
 * 1. Cada dose pertence ao mês e ao dia do seu horário PREVISTO. Uma dose prevista para
 *    23:50 de 31/10 e tomada às 00:10 de 01/11 conta só em outubro.
 * 2. Status -> resultado: Tomado = pontual; Tomado com atraso = tomada atrasada;
 *    Não tomado = sem registro; Atrasado e Pendente = pendente (fora do denominador).
 * 3. Dose futura nunca é "sem registro": se vier "Não tomado" com horário depois de [now],
 *    o motor a trata como pendente.
 * 4. Dia incompleto (hoje): as doses que ainda não passaram da janela ficam pendentes e não
 *    pesam na adesão; a adesão do dia só considera doses elegíveis.
 * 5. Sem duplicar: a mesma dose (medicamento + minuto previsto) aparecendo mais de uma vez
 *    conta uma vez, preferindo a versão com registro.
 * 6. Adiamento não é tomada: não altera nenhum contador de doses. Só clique manual conta, e o
 *    mesmo clique (mesma dose, mesmo instante) não é contado duas vezes.
 */
object MonthlyAdherenceCalculator {

    fun calculate(
        month: YearMonth,
        events: List<DoseEvent>,
        now: LocalDateTime,
        snoozes: List<DoseSnoozeEvent> = emptyList()
    ): MonthlyAdherenceStats {
        val doses = classifyMonth(month, events, now)
        val monthSnoozes = uniqueSnoozes(month, snoozes)

        val dosesByDate = doses.groupBy { it.scheduledAt.toLocalDate() }
        val days = (1..month.lengthOfMonth()).map { dayOfMonth ->
            val date = month.atDay(dayOfMonth)
            val dayDoses = dosesByDate[date].orEmpty()
            DayStats(
                date = date,
                counts = dayDoses.toCounts(),
                medications = dayDoses.map { it.medicationName }.distinct().sortedBy { it.lowercase() },
                doses = dayDoses
            )
        }

        val dosesByHour = doses.groupBy { it.scheduledAt.hour }
        val snoozesByHour = monthSnoozes.groupingBy { it.scheduledAt.hour }.eachCount()
        val hours = (dosesByHour.keys + snoozesByHour.keys).sorted().map { hour ->
            HourStats(
                hour = hour,
                counts = dosesByHour[hour].orEmpty().toCounts(),
                snoozes = snoozesByHour[hour] ?: 0
            )
        }

        val dosesByMedication = doses.groupBy { medicationKey(it.medicationLocalId, it.medicationName) }
        val snoozesByMedication = monthSnoozes.groupBy { medicationKey(it.medicationLocalId, it.medicationName) }
        val medications = (dosesByMedication.keys + snoozesByMedication.keys).map { key ->
            val name = dosesByMedication[key]?.first()?.medicationName
                ?: snoozesByMedication.getValue(key).first().medicationName
            MedicationStats(
                key = key,
                name = name,
                counts = dosesByMedication[key].orEmpty().toCounts(),
                snoozes = snoozesByMedication[key]?.size ?: 0
            )
        }.sortedWith(compareBy({ it.name.lowercase() }, { it.key }))

        val dosesByWeekday = doses.groupBy { it.scheduledAt.dayOfWeek }
        val weekdays = DayOfWeek.values().map { weekday ->
            WeekdayStats(weekday, dosesByWeekday[weekday].orEmpty().toCounts())
        }

        return MonthlyAdherenceStats(
            month = month,
            counts = doses.toCounts(),
            days = days,
            hours = hours,
            medications = medications,
            weekdays = weekdays,
            snooze = snoozeStats(monthSnoozes, doses)
        )
    }

    private data class DoseKey(val medication: String, val minute: LocalDateTime)

    private fun medicationKey(localId: Long?, name: String): String =
        localId?.let { "id:$it" } ?: "nome:${name.trim().lowercase()}"

    private fun keyOf(localId: Long?, name: String, scheduledAt: LocalDateTime) =
        DoseKey(medicationKey(localId, name), scheduledAt.truncatedTo(ChronoUnit.MINUTES))

    private fun classifyMonth(
        month: YearMonth,
        events: List<DoseEvent>,
        now: LocalDateTime
    ): List<ClassifiedDose> =
        events
            .filter { YearMonth.from(it.scheduledAt) == month }
            .map { it.toClassified(now) }
            .groupBy { keyOf(it.medicationLocalId, it.medicationName, it.scheduledAt) }
            .values
            .map { sameDose -> sameDose.minBy { rank(it.outcome) } }
            .sortedWith(compareBy({ it.scheduledAt }, { it.medicationName.lowercase() }))

    /** Menor = melhor versão da mesma dose: com registro > sem registro > pendente. */
    private fun rank(outcome: DoseOutcome): Int = when (outcome) {
        DoseOutcome.ON_TIME, DoseOutcome.LATE_TAKEN -> 0
        DoseOutcome.NO_RECORD -> 1
        DoseOutcome.PENDING -> 2
    }

    private fun DoseEvent.toClassified(now: LocalDateTime): ClassifiedDose {
        val outcome = when (status) {
            HistoryDoseStatus.TOMADO -> DoseOutcome.ON_TIME
            HistoryDoseStatus.TOMADO_COM_ATRASO -> DoseOutcome.LATE_TAKEN
            HistoryDoseStatus.NAO_TOMADO ->
                if (scheduledAt.isAfter(now)) DoseOutcome.PENDING else DoseOutcome.NO_RECORD
            HistoryDoseStatus.ATRASADO, HistoryDoseStatus.PENDENTE -> DoseOutcome.PENDING
        }
        return ClassifiedDose(
            medicationLocalId = medicationLocalId,
            medicationName = medicationName,
            dosage = dosage,
            scheduledAt = scheduledAt,
            takenAt = takenAt,
            outcome = outcome,
            medicationRemoved = medicationRemoved
        )
    }

    private fun List<ClassifiedDose>.toCounts(): DoseCounts = DoseCounts(
        onTime = count { it.outcome == DoseOutcome.ON_TIME },
        lateTaken = count { it.outcome == DoseOutcome.LATE_TAKEN },
        noRecord = count { it.outcome == DoseOutcome.NO_RECORD },
        pending = count { it.outcome == DoseOutcome.PENDING }
    )

    /** Só cliques manuais do mês (pelo horário previsto da dose), sem repetir o mesmo clique. */
    private fun uniqueSnoozes(month: YearMonth, snoozes: List<DoseSnoozeEvent>): List<DoseSnoozeEvent> =
        snoozes
            .filter { it.isManual && YearMonth.from(it.scheduledAt) == month }
            .distinctBy { Pair(keyOf(it.medicationLocalId, it.medicationName, it.scheduledAt), it.snoozedAt) }

    private fun snoozeStats(snoozes: List<DoseSnoozeEvent>, doses: List<ClassifiedDose>): SnoozeStats {
        val doseByKey = doses.associateBy { keyOf(it.medicationLocalId, it.medicationName, it.scheduledAt) }

        val summaries = snoozes
            .groupBy { keyOf(it.medicationLocalId, it.medicationName, it.scheduledAt) }
            .map { (key, sameDose) ->
                val ordered = sameDose.sortedBy { it.snoozedAt }
                val dose = doseByKey[key]
                DoseSnoozeSummary(
                    medicationLocalId = ordered.first().medicationLocalId,
                    medicationName = dose?.medicationName ?: ordered.first().medicationName,
                    scheduledAt = ordered.first().scheduledAt,
                    snoozeCount = ordered.size,
                    firstSnoozedAt = ordered.first().snoozedAt,
                    lastSnoozedAt = ordered.last().snoozedAt,
                    outcome = dose?.outcome,
                    takenAt = dose?.takenAt
                )
            }
            .sortedWith(compareByDescending<DoseSnoozeSummary> { it.snoozeCount }.thenBy { it.scheduledAt })

        return SnoozeStats(total = snoozes.size, doses = summaries)
    }
}
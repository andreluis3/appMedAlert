package com.andre.medalert.data.schedule

import com.andre.medalert.data.local.entity.MedicationEntity
import com.andre.medalert.data.local.entity.horariosLista
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.YearMonth
import java.time.format.DateTimeParseException
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Uma dose prevista (calculada a partir do Room; não é um registro de administração). */
data class ScheduledDose(
    val medicationLocalId: Long,
    val medicationName: String,
    val dosage: String,
    val at: LocalDateTime
)

private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")

fun Month.namePtBr(): String = getDisplayName(TextStyle.FULL, PT_BR)

fun YearMonth.labelPtBr(): String =
    month.namePtBr().replaceFirstChar { it.uppercase() } + " " + year

/**
 * Calcula as doses previstas de medicamentos a partir de:
 * data de início, horário da primeira dose, frequência (horas), data de fim e status ativo.
 *
 * Regras:
 * - Inativo, sem data de início ou sem horário -> nenhuma dose (nada é inventado).
 * - Com frequência: dose n = início + n * frequência (horas).
 * - Sem frequência, mas com a lista "horarios": esses horários se repetem todos os dias.
 * - Nada antes do início e nada depois do fim (o dia da data de fim entra inteiro).
 */
object DoseScheduleCalculator {

    private val HORA = Regex("""(\d{1,2}):(\d{2})""")

    private sealed interface Plan {
        val start: LocalDateTime
        val endExclusive: LocalDateTime?
    }

    private data class SeriesPlan(
        override val start: LocalDateTime,
        override val endExclusive: LocalDateTime?,
        val stepSeconds: Long
    ) : Plan

    private data class DailyPlan(
        override val start: LocalDateTime,
        override val endExclusive: LocalDateTime?,
        val times: List<LocalTime>
    ) : Plan

    // ---------------------------------------------------------------- parse

    private fun parseDate(raw: String?): LocalDate? {
        val text = raw?.trim()?.take(10) ?: return null
        return try {
            LocalDate.parse(text)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun parseTime(raw: String?): LocalTime? {
        val match = HORA.find(raw ?: return null) ?: return null
        val h = match.groupValues[1].toIntOrNull() ?: return null
        val m = match.groupValues[2].toIntOrNull() ?: return null
        return if (h in 0..23 && m in 0..59) LocalTime.of(h, m) else null
    }

    private fun firstTime(med: MedicationEntity): LocalTime? {
        parseTime(med.horarioDose)?.let { return it }
        // Registros antigos guardavam "yyyy-MM-dd HH:mm:ss-03:00" em dataInicio.
        val inicio = med.dataInicio
        if (inicio != null && inicio.length >= 16 && inicio[10] == ' ') {
            return parseTime(inicio.substring(11, 16))
        }
        return null
    }

    private fun plan(med: MedicationEntity): Plan? {
        if (!med.ativo) return null
        val startDate = parseDate(med.dataInicio) ?: return null
        val endExclusive = parseDate(med.dataFim)?.plusDays(1)?.atStartOfDay()

        val first = firstTime(med)
        val freq = med.frequencia
        if (freq != null && freq in 1..24 && first != null) {
            return SeriesPlan(startDate.atTime(first), endExclusive, freq * 3600L)
        }

        val daily = med.horariosLista().mapNotNull { parseTime(it) }.distinct().sorted()
        if (daily.isNotEmpty()) {
            return DailyPlan(startDate.atStartOfDay(), endExclusive, daily)
        }
        return null
    }

    // ------------------------------------------------------------- consultas

    /** Doses previstas em [from, toExclusive), ordenadas por horário e nome. */
    fun dosesBetween(
        medications: List<MedicationEntity>,
        from: LocalDateTime,
        toExclusive: LocalDateTime
    ): List<ScheduledDose> {
        val result = mutableListOf<ScheduledDose>()

        medications.forEach { med ->
            val plan = plan(med) ?: return@forEach
            val end = plan.endExclusive

            fun add(at: LocalDateTime) {
                result += ScheduledDose(med.id, med.nome, med.dosagem, at)
            }

            when (plan) {
                is SeriesPlan -> {
                    val lower = maxOf(from, plan.start)
                    val elapsed = ChronoUnit.SECONDS.between(plan.start, lower)
                    var n = (elapsed + plan.stepSeconds - 1) / plan.stepSeconds
                    var at = plan.start.plusSeconds(n * plan.stepSeconds)
                    while (at < toExclusive && (end == null || at < end)) {
                        add(at)
                        n++
                        at = plan.start.plusSeconds(n * plan.stepSeconds)
                    }
                }

                is DailyPlan -> {
                    var day = maxOf(from, plan.start).toLocalDate()
                    val lastDay = toExclusive.minusNanos(1).toLocalDate()
                    while (!day.isAfter(lastDay)) {
                        plan.times.forEach { time ->
                            val at = day.atTime(time)
                            if (at >= from && at >= plan.start && at < toExclusive &&
                                (end == null || at < end)
                            ) {
                                add(at)
                            }
                        }
                        day = day.plusDays(1)
                    }
                }
            }
        }

        return result.sortedWith(compareBy({ it.at }, { it.medicationName.lowercase() }))
    }

    fun dosesOnDay(medications: List<MedicationEntity>, day: LocalDate): List<ScheduledDose> =
        dosesBetween(medications, day.atStartOfDay(), day.plusDays(1).atStartOfDay())

    /** Primeira dose ESTRITAMENTE depois de [after], ou null se não houver mais nenhuma. */
    fun nextDose(med: MedicationEntity, after: LocalDateTime): LocalDateTime? {
        val plan = plan(med) ?: return null
        val end = plan.endExclusive

        return when (plan) {
            is SeriesPlan -> {
                val at = if (after < plan.start) {
                    plan.start
                } else {
                    val n = ChronoUnit.SECONDS.between(plan.start, after) / plan.stepSeconds + 1
                    plan.start.plusSeconds(n * plan.stepSeconds)
                }
                at.takeIf { end == null || it < end }
            }

            is DailyPlan -> {
                val base = maxOf(after, plan.start)
                val limit = base.toLocalDate().plusDays(3).atStartOfDay()
                dosesBetween(listOf(med), after.plusNanos(1), limit).firstOrNull()?.at
            }
        }
    }

    /** Próxima dose entre todos os medicamentos. */
    fun nextDoseOverall(medications: List<MedicationEntity>, after: LocalDateTime): ScheduledDose? =
        medications.mapNotNull { med ->
            nextDose(med, after)?.let { ScheduledDose(med.id, med.nome, med.dosagem, it) }
        }.minByOrNull { it.at }
}
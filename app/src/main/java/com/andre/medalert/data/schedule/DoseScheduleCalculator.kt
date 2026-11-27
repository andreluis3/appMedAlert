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
 * - Inativo, ou sem nenhum horário utilizável -> nenhuma dose (nada é inventado).
 * - Com data de início válida e frequência: dose n = início + n * frequência (horas).
 * - Com data de início válida e sem frequência, mas com a lista "horarios": esses horários se
 *   repetem todos os dias a partir do início.
 * - SEM data de início utilizável (ausente ou em formato inválido): os horários vêm da regra do
 *   próprio servidor ([ServerDoseRule] a partir de horarioDose + frequencia, ou a lista
 *   "horarios") e se repetem todos os dias, sem limite de início. A data de início nunca é
 *   inventada, e a adesão local continua limitada por acompanhadoDesde. É a mesma regra que o
 *   cuidador já usa ([CaregiverDoseSchedule]).
 * - Nada depois do fim, quando a data de fim é válida (o dia da data de fim entra inteiro).
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

    /** Como o cálculo trata um medicamento. Mesma decisão usada por [plan] e pelo diagnóstico. */
    data class PlanDiagnosis(
        /** SERIE, HORARIOS_DIARIOS, REGRA_DO_SERVIDOR (sem data de início) ou NENHUM. */
        val plano: String,
        /** Por que NÃO há plano (só quando [plano] = NENHUM). */
        val motivo: String?,
        /** Dados ausentes/inválidos que o cálculo encontrou, mesmo quando ainda gera doses. */
        val observacoes: List<String>
    )

    const val PLANO_SERIE = "SERIE"
    const val PLANO_HORARIOS_DIARIOS = "HORARIOS_DIARIOS"
    const val PLANO_REGRA_DO_SERVIDOR = "REGRA_DO_SERVIDOR"
    const val PLANO_NENHUM = "NENHUM"

    /** Início "sem limite": usado só quando o medicamento não tem data de início utilizável. */
    private val SEM_INICIO: LocalDateTime = LocalDateTime.of(1970, 1, 1, 0, 0)

    private class Resolution(val plan: Plan?, val diagnosis: PlanDiagnosis)

    private fun none(motivo: String, notas: List<String>) =
        Resolution(null, PlanDiagnosis(PLANO_NENHUM, motivo, notas))

    private fun resolve(med: MedicationEntity): Resolution {
        if (!med.ativo) return none("medicamento inativo (ativo = false)", emptyList())

        val notas = mutableListOf<String>()

        val inicioTexto = med.dataInicio
        val startDate = parseDate(inicioTexto)
        when {
            inicioTexto.isNullOrBlank() -> notas += "dataInicio ausente"
            startDate == null -> notas += "dataInicio inválida (formato não reconhecido)"
        }

        val endDate = parseDate(med.dataFim)
        if (!med.dataFim.isNullOrBlank() && endDate == null) notas += "dataFim inválida (ignorada)"
        val endExclusive = endDate?.plusDays(1)?.atStartOfDay()

        val first = firstTime(med)
        if (first == null) notas += "horarioDose ausente ou inválido"

        val freqBruta = med.frequencia
        val freq = freqBruta?.takeIf { it in 1..24 }
        if (freqBruta == null) notas += "frequência ausente"
        else if (freq == null) notas += "frequência $freqBruta fora de 1..24"

        val daily = med.horariosLista().mapNotNull { parseTime(it) }.distinct().sorted()

        if (startDate != null) {
            if (freq != null && first != null) {
                return Resolution(
                    SeriesPlan(startDate.atTime(first), endExclusive, freq * 3600L),
                    PlanDiagnosis(PLANO_SERIE, null, notas)
                )
            }
            if (daily.isNotEmpty()) {
                return Resolution(
                    DailyPlan(startDate.atStartOfDay(), endExclusive, daily),
                    PlanDiagnosis(PLANO_HORARIOS_DIARIOS, null, notas)
                )
            }
            return none("sem horário da primeira dose + frequência válida e sem lista de horários", notas)
        }

        // Sem data de início utilizável: regra do servidor, sem limite de início.
        val times = if (freq != null && first != null) {
            ServerDoseRule.timesOfDay("%02d:%02d".format(first.hour, first.minute), freq)
        } else {
            daily
        }
        if (times.isNotEmpty()) {
            return Resolution(
                DailyPlan(SEM_INICIO, endExclusive, times),
                PlanDiagnosis(PLANO_REGRA_DO_SERVIDOR, null, notas)
            )
        }
        return none("sem data de início e sem horário utilizável (horarioDose + frequência ou lista de horários)", notas)
    }

    private fun plan(med: MedicationEntity): Plan? = resolve(med).plan

    /** Explica, sem calcular doses, qual plano o medicamento recebe (ou por que não recebe nenhum). */
    fun diagnose(med: MedicationEntity): PlanDiagnosis = resolve(med).diagnosis

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
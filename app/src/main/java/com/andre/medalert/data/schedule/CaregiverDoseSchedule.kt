package com.andre.medalert.data.schedule

import com.andre.medalert.data.local.entity.MedicationEntity
import com.andre.medalert.data.mapper.HorarioDose
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val HORA_HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Horários do dia de um medicamento, pela MESMA regra do servidor (GET /api/meds/{id}, campo
 * doseDiaria, e utils/defstatus.ts): a partir do horário da primeira dose, uma dose a cada
 * [frequencia] horas, enquanto `frequencia × i < 24`, repetindo todos os dias.
 *
 * Exemplo: primeira dose 23:25, frequência 8 -> 07:25, 15:25, 23:25.
 *
 * Nada é inventado: sem horário válido ou com frequência fora de 1..24 devolve lista vazia.
 */
object ServerDoseRule {

    fun timesOfDay(horarioDose: String?, frequencia: Int?): List<LocalTime> {
        val freq = frequencia ?: return emptyList()
        if (freq !in 1..24) return emptyList()

        val first = parseHora(HorarioDose.normalizar(horarioDose)) ?: return emptyList()
        val startMinutes = first.hour * 60 + first.minute

        val times = mutableListOf<LocalTime>()
        var i = 0
        while (freq * i < 24) {
            val minutes = (startMinutes + i * freq * 60) % (24 * 60)
            times += LocalTime.of(minutes / 60, minutes % 60)
            i++
        }
        return times.distinct().sorted()
    }

    fun format(time: LocalTime): String = time.format(HORA_HHMM)

    private fun parseHora(texto: String?): LocalTime? =
        try {
            if (texto == null) null else LocalTime.parse(texto)
        } catch (_: DateTimeParseException) {
            null
        }
}

/**
 * Só para EXIBIÇÃO (lista de Remédios): quando o medicamento não traz a lista de horários (o GET
 * do cuidador não envia doseDiaria), monta a lista a partir de horarioDose + frequencia, pela
 * regra do servidor. Não grava nada no Room; a linha original continua como está.
 */
fun MedicationEntity.withServerHorarios(): MedicationEntity {
    if (horarios.isNotBlank()) return this
    val times = ServerDoseRule.timesOfDay(horarioDose, frequencia)
    if (times.isEmpty()) return this
    return copy(horarios = times.joinToString(",") { ServerDoseRule.format(it) })
}

/**
 * Doses previstas na visão do CUIDADOR.
 *
 * O GET /api/meds/cuid/{id} traz só nome, dosagem, horario_dose e frequencia: NÃO traz
 * data_inicio, data_fim nem ativo. O [DoseScheduleCalculator] (usado pelo paciente) exige
 * data_inicio e por isso não gera nada. Aqui os horários vêm da regra do servidor
 * ([ServerDoseRule]) e as datas só limitam quando existem:
 * - dataInicio presente: não há dose antes dela; ausente: sem limite (nunca é inventada);
 * - dataFim presente: não há dose depois dela; ausente: sem limite;
 * - ativo = false: sem doses (a API ainda não informa; o padrão do Room é true).
 *
 * Limitação conhecida: sem data_inicio/data_fim o app não sabe se o tratamento já começou ou já
 * terminou, e o servidor também ignora essas datas nos próprios cálculos.
 *
 * O cálculo do paciente ([DoseScheduleCalculator]) NÃO é alterado.
 */
object CaregiverDoseSchedule {

    /** Doses do dia [day], ordenadas por horário e nome. */
    fun dosesOnDay(medications: List<MedicationEntity>, day: LocalDate): List<ScheduledDose> {
        val result = mutableListOf<ScheduledDose>()

        medications.forEach { med ->
            if (!med.ativo || !coversDay(med, day)) return@forEach

            ServerDoseRule.timesOfDay(med.horarioDose, med.frequencia).forEach { time ->
                result += ScheduledDose(med.id, med.nome, med.dosagem, day.atTime(time))
            }
        }

        return result.sortedWith(compareBy({ it.at }, { it.medicationName.lowercase() }))
    }

    /** Primeira dose ESTRITAMENTE depois de [after] (procura hoje e nos 2 dias seguintes). */
    fun nextDoseOverall(medications: List<MedicationEntity>, after: LocalDateTime): ScheduledDose? {
        val firstDay = after.toLocalDate()
        for (offset in 0L..2L) {
            val next = dosesOnDay(medications, firstDay.plusDays(offset))
                .firstOrNull { it.at > after }
            if (next != null) return next
        }
        return null
    }

    private fun coversDay(med: MedicationEntity, day: LocalDate): Boolean {
        val start = parseDate(med.dataInicio)
        if (start != null && day.isBefore(start)) return false

        val end = parseDate(med.dataFim)
        if (end != null && day.isAfter(end)) return false

        return true
    }

    private fun parseDate(raw: String?): LocalDate? {
        val text = raw?.trim()?.take(10) ?: return null
        return try {
            LocalDate.parse(text)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
package com.andre.medalert.data.schedule

import com.andre.medalert.data.local.entity.MedicationEntity
import com.andre.medalert.data.remote.dto.AdesaoDiaDto
import com.andre.medalert.data.remote.dto.AdesaoRemedioDto
import com.andre.medalert.data.remote.dto.DashboardResponse
import com.andre.medalert.data.remote.dto.HorarioEsquecidoDto
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Uma dose registrada como tomada (vem do histórico local no Room). */
data class TakenDose(
    val medicationLocalId: Long,
    val scheduledAtMillis: Long,
    val takenAtMillis: Long
)

data class MedicationAdherence(
    val medicationName: String,
    val expected: Int,
    val taken: Int
)

/**
 * Adesão calculada SÓ com o que o aparelho sabe (Room), sem internet.
 *
 * - [expected]: doses previstas cujo horário JÁ passou na janela.
 * - [takenOnTime]: registradas até [AdherenceCalculator.LATE_AFTER_MINUTES] depois do horário.
 * - [takenLate]: registradas com mais atraso que isso ("atrasadas").
 * - [notRegistered]: horário passou e nenhum registro (o app não sabe se foi tomada ou não).
 * Doses futuras não entram na conta.
 */
data class AdherenceStats(
    val expected: Int,
    val takenOnTime: Int,
    val takenLate: Int,
    val notRegistered: Int,
    val byMedication: List<MedicationAdherence>,
    /** (previstas, tomadas) por dia da semana. */
    val byWeekday: Map<DayOfWeek, Pair<Int, Int>>,
    /** Horário "HH:mm" -> quantidade de doses não registradas. */
    val missedByTime: Map<String, Int>
) {
    val taken: Int get() = takenOnTime + takenLate

    /** 0..100, ou null quando ainda não há doses previstas para avaliar. */
    val percent: Int? get() = if (expected == 0) null else (taken * 100.0 / expected).roundToInt()
}

object AdherenceCalculator {

    const val LATE_AFTER_MINUTES = 60L
    const val DEFAULT_WINDOW_DAYS = 7

    fun calculate(
        medications: List<MedicationEntity>,
        taken: List<TakenDose>,
        now: LocalDateTime,
        windowDays: Int = DEFAULT_WINDOW_DAYS,
        zone: ZoneId = ZoneId.systemDefault()
    ): AdherenceStats {
        val windowStart = now.minusDays(windowDays.toLong())
        val takenByDose = taken.associateBy { it.medicationLocalId to it.scheduledAtMillis }

        var expected = 0
        var onTime = 0
        var late = 0
        var missed = 0
        val perMed = mutableListOf<MedicationAdherence>()
        val perDay = mutableMapOf<DayOfWeek, Pair<Int, Int>>()
        val missedTimes = mutableMapOf<String, Int>()

        medications.forEach { med ->
            // Só conta a partir de quando o aparelho passou a acompanhar o medicamento.
            val since = med.acompanhadoDesde
                ?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone) }
                ?.let { maxOf(it, windowStart) }
                ?: windowStart

            var medExpected = 0
            var medTaken = 0

            DoseScheduleCalculator
                .dosesBetween(listOf(med), since, now.plusNanos(1))
                .forEach { dose ->
                    medExpected++
                    val scheduledMillis = dose.at.atZone(zone).toInstant().toEpochMilli()
                    val record = takenByDose[med.id to scheduledMillis]
                    val day = dose.at.dayOfWeek
                    val (dayExpected, dayTaken) = perDay[day] ?: (0 to 0)

                    if (record == null) {
                        missed++
                        val hhmm = "%02d:%02d".format(dose.at.hour, dose.at.minute)
                        missedTimes[hhmm] = (missedTimes[hhmm] ?: 0) + 1
                        perDay[day] = (dayExpected + 1) to dayTaken
                    } else {
                        medTaken++
                        val delayMinutes = (record.takenAtMillis - scheduledMillis) / 60_000
                        if (delayMinutes > LATE_AFTER_MINUTES) late++ else onTime++
                        perDay[day] = (dayExpected + 1) to (dayTaken + 1)
                    }
                }

            expected += medExpected
            if (medExpected > 0) perMed += MedicationAdherence(med.nome, medExpected, medTaken)
        }

        return AdherenceStats(
            expected = expected,
            takenOnTime = onTime,
            takenLate = late,
            notRegistered = missed,
            byMedication = perMed,
            byWeekday = perDay,
            missedByTime = missedTimes
        )
    }
}

private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")

private fun percentText(taken: Int, expected: Int): String =
    if (expected == 0) "0" else (taken * 100.0 / expected).roundToInt().toString()

/**
 * Adapta a adesão local ao formato que a tela de Adesão já sabe desenhar. Não é uma resposta da
 * API: é o cálculo local no mesmo formato. Tomadas = no horário, Atrasadas = tomadas com atraso,
 * Não tomadas = horário passou sem registro.
 */
fun AdherenceStats.toDashboardResponse(): DashboardResponse = DashboardResponse(
    adesao = percentText(taken, expected),
    dosesEsp = expected,
    dosesTom = takenOnTime,
    dosesAtr = takenLate,
    dosesNT = notRegistered,
    adesaoD = byWeekday.entries.sortedBy { it.key.value }.map { (day, counts) ->
        AdesaoDiaDto(
            adesao = percentText(counts.second, counts.first),
            diaSem = day.getDisplayName(TextStyle.FULL, PT_BR)
        )
    },
    adesaoR = byMedication.map {
        AdesaoRemedioDto(
            remedio = it.medicationName,
            dosesEsp = it.expected,
            dosesTom = it.taken,
            adesao = percentText(it.taken, it.expected)
        )
    },
    horariosEsq = missedByTime.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(3)
        .map { HorarioEsquecidoDto(it.key, it.value, percentText(it.value, notRegistered)) }
)

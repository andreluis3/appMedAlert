package com.andre.medalert.data.schedule

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * Modelos do motor de adesão mensal ([MonthlyAdherenceCalculator]). Kotlin puro (sem Android).
 */

/** Resultado de uma dose depois de aplicadas as regras do motor mensal. */
enum class DoseOutcome {
    /** Tomada no horário (dentro da tolerância). */
    ON_TIME,

    /** Tomada, mas depois da tolerância. */
    LATE_TAKEN,

    /** Já passou da janela e não há registro. */
    NO_RECORD,

    /** Ainda não venceu a janela (futura, ou atrasada sem ser "não tomada"). Fora do denominador. */
    PENDING
}

/** Uma dose única (já sem duplicatas) com o resultado calculado. */
data class ClassifiedDose(
    val medicationLocalId: Long?,
    val medicationName: String,
    val dosage: String,
    val scheduledAt: LocalDateTime,
    val takenAt: LocalDateTime?,
    val outcome: DoseOutcome,
    val medicationRemoved: Boolean = false
)

/**
 * Contadores de doses. Fórmulas:
 * - adesão = tomadas / previstas elegíveis x 100 (pendentes ficam fora do denominador);
 * - pontualidade = tomadas pontualmente / tomadas x 100 (atrasada conta como tomada, não como pontual).
 */
data class DoseCounts(
    val onTime: Int = 0,
    val lateTaken: Int = 0,
    val noRecord: Int = 0,
    val pending: Int = 0
) {
    /** Tomadas (pontuais + atrasadas). */
    val taken: Int get() = onTime + lateTaken

    /** Previstas elegíveis: tomadas + sem registro. Pendentes não entram. */
    val eligible: Int get() = taken + noRecord

    val total: Int get() = eligible + pending

    /** Adesão em %, ou null quando ainda não há dose elegível (nunca inventa 0% nem 100%). */
    val adherencePercent: Double? get() = if (eligible == 0) null else taken * 100.0 / eligible

    /** Pontualidade em %, ou null quando nada foi tomado. */
    val punctualityPercent: Double? get() = if (taken == 0) null else onTime * 100.0 / taken
}

/** Um dia do mês. */
data class DayStats(
    val date: LocalDate,
    val counts: DoseCounts,
    val medications: List<String>,
    val doses: List<ClassifiedDose>
)

/** Doses e adiamentos agrupados pela hora PREVISTA (0 a 23). */
data class HourStats(
    val hour: Int,
    val counts: DoseCounts,
    val snoozes: Int
)

/** Doses e adiamentos de um medicamento no mês. */
data class MedicationStats(
    val key: String,
    val name: String,
    val counts: DoseCounts,
    val snoozes: Int
)

/** Doses de um dia da semana no mês. */
data class WeekdayStats(
    val weekday: DayOfWeek,
    val counts: DoseCounts
)

/**
 * Um clique de adiamento. Adiar NÃO é tomar: não altera nenhum contador de doses.
 * Só conta o adiamento manual ([isManual]); o mesmo clique (mesma dose, mesmo [snoozedAt]) não duplica.
 */
data class DoseSnoozeEvent(
    val medicationLocalId: Long?,
    val medicationName: String,
    /** Horário PREVISTO da dose que foi adiada. */
    val scheduledAt: LocalDateTime,
    /** Instante em que o adiamento foi pedido. */
    val snoozedAt: LocalDateTime,
    val isManual: Boolean = true
)

/** Adiamentos de uma mesma dose. */
data class DoseSnoozeSummary(
    val medicationLocalId: Long?,
    val medicationName: String,
    val scheduledAt: LocalDateTime,
    val snoozeCount: Int,
    val firstSnoozedAt: LocalDateTime,
    val lastSnoozedAt: LocalDateTime,
    /** Resultado da dose adiada; null se a dose não está nas doses do mês. */
    val outcome: DoseOutcome?,
    val takenAt: LocalDateTime?
)

data class SnoozeStats(
    val total: Int,
    /** Mais adiadas primeiro. */
    val doses: List<DoseSnoozeSummary>
)

/** Tudo o que a tela de Adesão precisa para um mês. */
data class MonthlyAdherenceStats(
    val month: YearMonth,
    val counts: DoseCounts,
    val days: List<DayStats>,
    val hours: List<HourStats>,
    val medications: List<MedicationStats>,
    val weekdays: List<WeekdayStats>,
    val snooze: SnoozeStats
)
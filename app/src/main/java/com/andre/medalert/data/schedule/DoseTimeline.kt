package com.andre.medalert.data.schedule

import com.andre.medalert.data.local.entity.DoseHistoryEntity
import com.andre.medalert.data.local.entity.MedicationEntity
import com.andre.medalert.data.model.HistoryDoseStatus
import com.andre.medalert.data.model.HistoryPeriod
import com.andre.medalert.data.model.HistoryUiModel
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Até este tempo depois do horário previsto, a dose tomada conta como "Tomado". */
const val ON_TIME_TOLERANCE_MINUTES = 30

/** Sem registro depois deste tempo, a dose passa de "Atrasado" para "Não tomado". */
const val MISSED_DOSE_THRESHOLD_MINUTES = 120

private const val MAX_HISTORY_DAYS = 365L

/** Uma dose (prevista, tomada ou ambas) já com o status calculado. */
data class DoseEvent(
    val medicationLocalId: Long?,
    val medicationName: String,
    val dosage: String,
    val scheduledAt: LocalDateTime,
    val takenAt: LocalDateTime?,
    val status: HistoryDoseStatus,
    val medicationRemoved: Boolean = false,
    /** Quem registrou a administração: "paciente", "cuidador" ou null (desconhecido). */
    val registeredBy: String? = null
)

/** Única fonte das regras de status. Histórico e Calendário usam esta mesma função. */
object DoseStatusRules {

    fun classify(
        scheduledAt: LocalDateTime,
        takenAt: LocalDateTime?,
        now: LocalDateTime
    ): HistoryDoseStatus {
        val toleranceSeconds = ON_TIME_TOLERANCE_MINUTES * 60L
        val missedSeconds = MISSED_DOSE_THRESHOLD_MINUTES * 60L

        if (takenAt != null) {
            val lateSeconds = Duration.between(scheduledAt, takenAt).seconds
            return if (lateSeconds > toleranceSeconds) {
                HistoryDoseStatus.TOMADO_COM_ATRASO
            } else {
                HistoryDoseStatus.TOMADO
            }
        }

        if (!now.isAfter(scheduledAt)) return HistoryDoseStatus.PENDENTE

        val overdueSeconds = Duration.between(scheduledAt, now).seconds
        return when {
            overdueSeconds <= toleranceSeconds -> HistoryDoseStatus.PENDENTE
            overdueSeconds <= missedSeconds -> HistoryDoseStatus.ATRASADO
            else -> HistoryDoseStatus.NAO_TOMADO
        }
    }

    /**
     * Dose que a API informa como tomada mas sem horário interpretável.
     * Só "atrasado" é tratado como tomada com atraso; qualquer outro valor conta como tomada.
     */
    fun fromRemoteStatus(raw: String?): HistoryDoseStatus =
        if (raw?.trim()?.lowercase() == "atrasado") {
            HistoryDoseStatus.TOMADO_COM_ATRASO
        } else {
            HistoryDoseStatus.TOMADO
        }
}

private data class DoseKey(val medicationLocalId: Long, val minute: LocalDateTime)

private data class PlacedRecord(
    val entity: DoseHistoryEntity,
    val scheduledAt: LocalDateTime,
    val takenAt: LocalDateTime?
)

private fun toLocal(millis: Long, zone: ZoneId): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)

/**
 * Cruza as doses PREVISTAS (DoseScheduleCalculator, a partir dos medicamentos do Room) com os
 * REGISTROS de administração (DoseHistoryEntity).
 *
 * - Medicamento sem registro continua aparecendo com as doses previstas.
 * - Dose prevista com registro vira Tomado / Tomado com atraso.
 * - Dose prevista sem registro vira Pendente / Atrasado / Não tomado conforme o horário atual.
 * - Doses de antes de o aparelho passar a acompanhar o medicamento (acompanhadoDesde) e sem
 *   registro não são cobradas (mesma regra da adesão offline).
 * - Registro sem dose prevista correspondente (medicamento excluído, esquema editado) é
 *   preservado e exibido como foi registrado.
 * - Medicamento excluído não gera novas doses previstas (ele já não está na lista).
 */
object DoseTimelineBuilder {

    fun build(
        medications: List<MedicationEntity>,
        records: List<DoseHistoryEntity>,
        from: LocalDateTime,
        toExclusive: LocalDateTime,
        now: LocalDateTime,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<DoseEvent> {
        val medicationsById = medications.associateBy { it.id }
        val placed = records.mapNotNull { placeRecord(it, zone) }

        val byKey = HashMap<DoseKey, PlacedRecord>()
        placed.forEach { record ->
            val localId = record.entity.medicationLocalId
            if (localId != null) {
                byKey.putIfAbsent(
                    DoseKey(localId, record.scheduledAt.truncatedTo(ChronoUnit.MINUTES)),
                    record
                )
            }
        }

        val matched = HashSet<DoseKey>()
        val events = mutableListOf<DoseEvent>()

        DoseScheduleCalculator.dosesBetween(medications, from, toExclusive).forEach { dose ->
            val key = DoseKey(dose.medicationLocalId, dose.at.truncatedTo(ChronoUnit.MINUTES))
            val record = byKey[key]

            if (record != null) {
                matched += key
                events += eventFor(record, dose.medicationName, dose.dosage, dose.at, now)
            } else {
                val since = medicationsById[dose.medicationLocalId]
                    ?.acompanhadoDesde
                    ?.let { toLocal(it, zone) }
                if (since != null && dose.at.isBefore(since)) return@forEach

                events += DoseEvent(
                    medicationLocalId = dose.medicationLocalId,
                    medicationName = dose.medicationName,
                    dosage = dose.dosage,
                    scheduledAt = dose.at,
                    takenAt = null,
                    status = DoseStatusRules.classify(dose.at, null, now)
                )
            }
        }

        placed.forEach { record ->
            val localId = record.entity.medicationLocalId
            val key = localId?.let { DoseKey(it, record.scheduledAt.truncatedTo(ChronoUnit.MINUTES)) }
            if (key != null && key in matched) return@forEach
            if (record.scheduledAt.isBefore(from) || !record.scheduledAt.isBefore(toExclusive)) {
                return@forEach
            }
            events += eventFor(
                record,
                record.entity.medicamentoNome,
                record.entity.dosagem,
                record.scheduledAt,
                now
            )
        }

        return events.sortedWith(compareBy({ it.scheduledAt }, { it.medicationName.lowercase() }))
    }

    /** Só registros de dose TOMADA que conseguimos situar no tempo entram na linha do tempo. */
    private fun placeRecord(entity: DoseHistoryEntity, zone: ZoneId): PlacedRecord? {
        val isTaken = entity.tomadoEm != null || !entity.tomado.isNullOrBlank()
        if (!isTaken) return null

        val scheduledAt = entity.previstoEm?.let { toLocal(it, zone) }
            ?: scheduledFromDayAndTime(entity)
            ?: return null

        val takenAt = entity.tomadoEm?.let { toLocal(it, zone) }
            ?: entity.tomado?.let { raw ->
                DoseReconciler.parseTakenMillis(raw, scheduledAt.toLocalDate(), zone)
                    ?.let { toLocal(it, zone) }
            }

        return PlacedRecord(entity, scheduledAt, takenAt)
    }

    private fun scheduledFromDayAndTime(entity: DoseHistoryEntity): LocalDateTime? {
        val day = DoseReconciler.parseDay(entity.dia) ?: return null
        val time = DoseReconciler.parseHorario(entity.horario) ?: return null
        return day.atTime(time)
    }

    private fun eventFor(
        record: PlacedRecord,
        name: String,
        dosage: String,
        scheduledAt: LocalDateTime,
        now: LocalDateTime
    ): DoseEvent {
        val status = if (record.takenAt != null) {
            DoseStatusRules.classify(scheduledAt, record.takenAt, now)
        } else {
            DoseStatusRules.fromRemoteStatus(record.entity.status)
        }
        return DoseEvent(
            medicationLocalId = record.entity.medicationLocalId,
            medicationName = name,
            dosage = dosage,
            scheduledAt = scheduledAt,
            takenAt = record.takenAt,
            status = status,
            medicationRemoved = record.entity.medicamentoRemovido,
            registeredBy = record.entity.origem
        )
    }
}

/** Intervalos de cada período do Histórico. Nada é inventado antes do que o aparelho conhece. */
object HistoryRange {

    /** Fim do intervalo = amanhã 00:00, para que Hoje inclua as doses pendentes mais tarde. */
    fun of(
        period: HistoryPeriod,
        today: LocalDate,
        earliest: LocalDate?
    ): Pair<LocalDateTime, LocalDateTime> {
        val end = today.plusDays(1).atStartOfDay()
        val start = when (period) {
            HistoryPeriod.HOJE -> today
            HistoryPeriod.SEMANA -> today.minusDays(6)
            HistoryPeriod.MES -> today.withDayOfMonth(1)
            HistoryPeriod.TODOS -> maxOf(earliest ?: today, today.minusDays(MAX_HISTORY_DAYS))
        }
        return start.atStartOfDay() to end
    }

    /** Dia mais antigo que o aparelho conhece (acompanhamento dos medicamentos e registros). */
    fun earliestDate(
        medications: List<MedicationEntity>,
        records: List<DoseHistoryEntity>,
        zone: ZoneId = ZoneId.systemDefault()
    ): LocalDate? {
        val fromMedications = medications.mapNotNull { medication ->
            medication.acompanhadoDesde?.let { toLocal(it, zone).toLocalDate() }
        }
        val fromRecords = records.mapNotNull { record ->
            record.previstoEm?.let { toLocal(it, zone).toLocalDate() }
                ?: DoseReconciler.parseDay(record.dia)
        }
        return (fromMedications + fromRecords).minOrNull()
    }
}

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Evento -> modelo da tela de Histórico. */
fun DoseEvent.toUiModel(): HistoryUiModel =
    HistoryUiModel(
        medicationName = medicationName,
        dosage = dosage,
        scheduledTime = TIME_FORMAT.format(scheduledAt),
        takenAt = takenAt?.let { TIME_FORMAT.format(it) },
        status = status,
        medicationRemoved = medicationRemoved,
        date = scheduledAt.toLocalDate(),
        registeredBy = registeredBy
    )

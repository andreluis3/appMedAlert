package com.andre.medalert.data.repository

import com.andre.medalert.data.local.entity.DoseHistoryEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val STATUS_TOMADO = "tomado"
private val HORA: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Monta a linha de histórico de uma dose tomada. Função pura (testável em JVM): o dia e os
 * horários vêm do fuso informado, o status é sempre "tomado" e a linha nasce não sincronizada.
 * [origin] ("paciente" ou "cuidador"), [registeredById] e [clientEventId] identificam quem
 * registrou e dão ao registro um ID estável gerado no aparelho.
 */
internal fun buildTakenEntity(
    userId: Int,
    medicationLocalId: Long,
    medicationRemoteId: Int?,
    medicationName: String,
    dosage: String,
    scheduledAtMillis: Long,
    takenAtMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    origin: String? = null,
    registeredById: Int? = null,
    clientEventId: String? = null
): DoseHistoryEntity {
    val scheduled = Instant.ofEpochMilli(scheduledAtMillis).atZone(zone)
    val taken = Instant.ofEpochMilli(takenAtMillis).atZone(zone)

    return DoseHistoryEntity(
        userId = userId,
        medicamentoId = medicationRemoteId,
        medicamentoNome = medicationName,
        dosagem = dosage,
        horario = scheduled.format(HORA),
        tomado = taken.format(HORA),
        status = STATUS_TOMADO,
        isSynced = false,
        medicationLocalId = medicationLocalId,
        previstoEm = scheduledAtMillis,
        tomadoEm = takenAtMillis,
        dia = scheduled.toLocalDate().toString(),
        clientEventId = clientEventId,
        origem = origin,
        registradoPorId = registeredById
    )
}

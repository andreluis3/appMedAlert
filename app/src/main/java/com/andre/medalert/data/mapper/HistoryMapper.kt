package com.andre.medalert.data.mapper

import com.andre.medalert.data.local.entity.DoseHistoryEntity
import com.andre.medalert.data.model.HistoryDoseStatus
import com.andre.medalert.data.model.HistoryUiModel
import com.andre.medalert.data.remote.dto.HistoryRecordDto
import java.time.LocalDate

/**
 * API -> banco local: um registro (medicamento + várias doses) vira várias linhas.
 * A API devolve as doses de HOJE, então o dia gravado é o dia atual.
 */
fun HistoryRecordDto.toEntities(userId: Int): List<DoseHistoryEntity> {
    val hoje = LocalDate.now().toString()
    return doses.map { dose ->
        DoseHistoryEntity(
            userId = userId,
            medicamentoNome = nome,
            dosagem = dosagem,
            horario = dose.horario,
            tomado = dose.tomado,
            status = dose.status,
            isSynced = true,
            dia = hoje
        )
    }
}

/** API -> UI: achata medicamento + doses em uma lista de doses para a tela. */
fun List<HistoryRecordDto>.toHistoryUiModels(): List<HistoryUiModel> =
    flatMap { record ->
        record.doses.map { dose ->
            HistoryUiModel(
                medicationName = record.nome,
                dosage = record.dosagem,
                scheduledTime = dose.horario,
                takenAt = dose.tomado,
                status = dose.status.toHistoryDoseStatus()
            )
        }
    }

/** Banco local -> UI. */
fun DoseHistoryEntity.toHistoryUiModel(): HistoryUiModel =
    HistoryUiModel(
        medicationName = medicamentoNome,
        dosage = dosagem,
        scheduledTime = horario,
        takenAt = tomado,
        status = status.toHistoryDoseStatus()
    )

/** "tomado" | "atrasado" | "não tomado" | "pendente" (valores literais da API). */
private fun String.toHistoryDoseStatus(): HistoryDoseStatus =
    when (trim().lowercase()) {
        "tomado" -> HistoryDoseStatus.TOMADO
        "atrasado" -> HistoryDoseStatus.ATRASADO
        "não tomado", "nao tomado" -> HistoryDoseStatus.NAO_TOMADO
        else -> HistoryDoseStatus.PENDENTE
    }
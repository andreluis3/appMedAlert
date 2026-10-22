package com.andre.medalert.data.mapper

import com.andre.medalert.data.local.entity.MedicationEntity
import com.andre.medalert.data.remote.dto.MedicamentoData
import com.andre.medalert.data.remote.dto.MedicamentoResponse

/** API -> banco local (já sincronizado). */
fun MedicamentoResponse.toEntity(userId: Int): MedicationEntity =
    MedicationEntity(
        userId = userId,
        nome = nome,
        dosagem = dosagem,
        horarios = doseDiaria.joinToString(","),
        isSynced = true
    )

/** Dados do formulário -> banco local, ainda NÃO enviado à API. */
fun MedicamentoData.toEntity(): MedicationEntity =
    MedicationEntity(
        userId = userId,
        nome = nome,
        dosagem = "$dosagem $unidadeDosagem",
        dataInicio = dataInicio,
        dataFim = dataFim,
        dosagemValor = dosagem,
        frequencia = frequencia,
        quantidade = quantidade,
        unidadeDosagem = unidadeDosagem,
        unidadeQuantidade = unidadeQuantidade,
        isSynced = false
    )
/**
 * Banco local -> corpo do POST /api/meds (JSON flat).
 * Retorna null se faltar algum campo (ex.: medicamento que veio da API).
 */
fun MedicationEntity.toMedicamentoDataOrNull(): MedicamentoData? {
    return MedicamentoData(
        nome = nome,
        dataInicio = dataInicio ?: return null,
        dataFim = dataFim ?: return null,
        dosagem = dosagemValor ?: return null,
        frequencia = frequencia ?: return null,
        quantidade = quantidade ?: return null,
        unidadeDosagem = unidadeDosagem ?: return null,
        unidadeQuantidade = unidadeQuantidade ?: return null,
        userId = userId
    )
}
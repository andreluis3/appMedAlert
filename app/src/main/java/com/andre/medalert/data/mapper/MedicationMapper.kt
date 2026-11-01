package com.andre.medalert.data.mapper

import com.andre.medalert.data.local.entity.MedicationEntity
import com.andre.medalert.data.remote.dto.MedicamentoData
import com.andre.medalert.data.remote.dto.MedicamentoResponse

/** API -> banco local (já sincronizado). Guarda o ID do medicamento em remoteId. */
fun MedicamentoResponse.toEntity(userId: Int): MedicationEntity =
    MedicationEntity(
        userId = userId,
        nome = nome,
        dosagem = dosagem,
        horarios = doseDiaria.orEmpty().joinToString(","),
        isSynced = true,
        remoteId = id
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
 * Retorna null se faltar algum campo obrigatório (ex.: medicamento que veio da API).
 * dataFim é opcional na API, então NÃO é mais exigida.
 */
fun MedicationEntity.toMedicamentoDataOrNull(): MedicamentoData? {
    return MedicamentoData(
        nome = nome,
        dataInicio = dataInicio ?: return null,
        dataFim = dataFim,
        dosagem = dosagemValor ?: return null,
        frequencia = frequencia ?: return null,
        quantidade = quantidade ?: return null,
        unidadeDosagem = unidadeDosagem ?: return null,
        unidadeQuantidade = unidadeQuantidade ?: return null,
        userId = userId
    )
}

/**
 * Converte as datas do formulário para o formato da API ("yyyy-MM-ddT00:00:00.000Z").
 * A API guarda só a DATA (a hora enviada é descartada), então mandamos o dia local do formulário
 * sem converter fuso, para um horário à noite não "pular" para o dia seguinte em UTC.
 */
fun MedicamentoData.withIsoDates(): MedicamentoData =
    copy(dataInicio = dataInicio.toIsoUtc(), dataFim = dataFim?.toIsoUtc())

private val SOMENTE_DATA = Regex("""\d{4}-\d{2}-\d{2}""")

/** "2026-10-28 08:00:00-03:00" -> "2026-10-28T00:00:00.000Z". Se não começar com yyyy-MM-dd, devolve igual. */
fun String.toIsoUtc(): String {
    val dia = take(10)
    return if (SOMENTE_DATA.matches(dia)) "${dia}T00:00:00.000Z" else this
}
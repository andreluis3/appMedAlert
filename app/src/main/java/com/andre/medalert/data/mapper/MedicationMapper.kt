package com.andre.medalert.data.mapper

import com.andre.medalert.data.local.entity.MedicationEntity
import com.andre.medalert.data.remote.dto.MedicamentoData
import com.andre.medalert.data.remote.dto.MedicamentoResponse
import java.math.BigDecimal

/**
 * Número sem zeros inúteis e sem notação científica, independente do idioma do aparelho:
 * 50.0 -> "50", 2.5 -> "2.5", 0.5 -> "0.5".
 */
fun Double.formatarNumero(): String =
    BigDecimal(toString()).stripTrailingZeros().toPlainString()

/** "50 mg" -> (50.0, "mg"); "2,5" -> (2.5, null). null se o texto não começar com número. */
private val DOSAGEM_TEXTO = Regex("""^\s*(\d+(?:[.,]\d+)?)\s*(.*)$""")

private fun interpretarDosagem(texto: String): Pair<Double, String?>? {
    val partes = DOSAGEM_TEXTO.matchEntire(texto) ?: return null
    val valor = partes.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
    val unidade = partes.groupValues[2].trim().takeIf { it.isNotEmpty() }
    return valor to unidade
}

/**
 * API -> banco local (já sincronizado). Guarda o ID do medicamento em remoteId.
 * Com o GET completo traz também datas, frequência, quantidade, unidades, estoque, observações
 * e ativo. Se o servidor não mandar algum (versão antiga), fica null/padrão e o
 * preservingDetailsFrom (no DAO) mantém o que o aparelho já sabia.
 *
 * Dosagem: usa dosagem_valor (Double) se vier; senão extrai o número (e a unidade) do texto
 * "50 mg" / "50". Decimais como 2.5 são preservados. Se o texto vier só com o número e a
 * unidade chegar separada, o texto vira "50 mg".
 *
 * Horário: o ISO UTC da API é convertido para "HH:mm" (ver HorarioDose). Se não for reconhecido
 * fica null e o preservingDetailsFrom mantém o horário que o aparelho já tinha.
 */
fun MedicamentoResponse.toEntity(userId: Int): MedicationEntity {
    val interpretada = interpretarDosagem(dosagem)
    val valor = dosagemValor ?: interpretada?.first
    val unidade = unidadeDosagem ?: interpretada?.second

    val textoDosagem =
        if (interpretada != null && interpretada.second == null && unidade != null) {
            "${interpretada.first.formatarNumero()} $unidade"
        } else {
            dosagem
        }

    return MedicationEntity(
        userId = userId,
        nome = nome,
        dosagem = textoDosagem,
        horarios = doseDiaria.orEmpty().joinToString(","),
        horarioDose = HorarioDose.normalizar(horarioDose),
        dataInicio = dataInicio,
        dataFim = dataFim,
        dosagemValor = valor,
        frequencia = frequencia,
        quantidade = quantidade,
        unidadeDosagem = unidade,
        unidadeQuantidade = unidadeQuantidade,
        estoque = estoque,
        estoqueMinimo = estoqueMinimo,
        observacoes = observacoes,
        ativo = ativo ?: true,
        isSynced = true,
        remoteId = id
    )
}

/** Dados do formulário -> banco local, ainda NÃO enviado à API. */
fun MedicamentoData.toEntity(): MedicationEntity =
    MedicationEntity(
        userId = userId,
        nome = nome,
        dosagem = "${dosagem.formatarNumero()} $unidadeDosagem",
        horarioDose = HorarioDose.normalizar(horarioDose) ?: horarioDose,
        dataInicio = dataInicio,
        dataFim = dataFim,
        dosagemValor = dosagem,
        frequencia = frequencia,
        quantidade = quantidade,
        unidadeDosagem = unidadeDosagem,
        unidadeQuantidade = unidadeQuantidade,
        estoque = estoque,
        observacoes = observacoes,
        isSynced = false
    )

/**
 * Banco local -> corpo do POST /api/meds (JSON flat).
 * Retorna null se faltar algum campo obrigatório (ex.: medicamento que veio da API).
 * dataFim, estoque e observacoes são opcionais na API.
 * O horário sempre sai como "HH:mm", mesmo que um registro antigo tenha o ISO da API.
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
        horarioDose = HorarioDose.normalizar(horarioDose) ?: return null,
        estoque = estoque,
        observacoes = observacoes,
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
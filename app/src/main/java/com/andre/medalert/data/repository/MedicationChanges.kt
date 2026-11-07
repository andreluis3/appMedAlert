package com.andre.medalert.data.repository

import com.andre.medalert.data.local.entity.MedicationEntity
import com.andre.medalert.data.mapper.HorarioDose
import com.andre.medalert.data.mapper.formatarNumero

// ---------------------------------------------------------------------------
// Aplica no Room as mesmas mudanças que a API acabou de confirmar
// ---------------------------------------------------------------------------

private fun Map<String, Any?>.int(key: String): Int? = (this[key] as? Number)?.toInt()

/** Aceita Int, Long ou Double no mapa (ex.: "dosagem" = 2.5); preserva os decimais. */
private fun Map<String, Any?>.double(key: String): Double? = (this[key] as? Number)?.toDouble()

/**
 * Data nova vem da API como "yyyy-MM-ddT00:00:00.000Z". Se o registro local guarda a hora da
 * primeira dose ("yyyy-MM-dd HH:mm:ss-03:00"), troca só o dia e mantém a hora.
 */
private fun mergeDate(localAtual: String?, novoIso: String): String {
    val dia = novoIso.take(10)
    val sufixo = localAtual?.takeIf { it.length > 10 && it[10] == ' ' }?.substring(10)
    return if (sufixo != null) dia + sufixo else novoIso
}

/** Aplica no Room as mudanças confirmadas pela API (internal para ser testada em JVM). */
internal fun MedicationEntity.applyChanges(c: Map<String, Any?>): MedicationEntity {
    var e = this
    (c["nome"] as? String)?.let { e = e.copy(nome = it) }
    c.double("dosagem")?.let { e = e.copy(dosagemValor = it) }
    (c["unidade_dosagem"] as? String)?.let { e = e.copy(unidadeDosagem = it) }
    if (c.containsKey("dosagem") || c.containsKey("unidade_dosagem")) {
        val v = e.dosagemValor
        val u = e.unidadeDosagem
        if (v != null && u != null) e = e.copy(dosagem = "${v.formatarNumero()} $u")
    }
    c.int("quantidade_por_dose")?.let { e = e.copy(quantidade = it) }
    (c["unidade_quantidade"] as? String)?.let { e = e.copy(unidadeQuantidade = it) }
    c.int("frequencia")?.let { e = e.copy(frequencia = it) }
    (c["data_inicio"] as? String)?.let { e = e.copy(dataInicio = mergeDate(e.dataInicio, it)) }
    (c["data_fim"] as? String)?.let { e = e.copy(dataFim = mergeDate(e.dataFim, it)) }
    (c["horario_dose"] as? String)?.let { e = e.copy(horarioDose = HorarioDose.normalizar(it) ?: it) }
    // Estoque informado pelo usuário vira a nova BASE: o consumo local anterior deixa de valer.
    if (c.containsKey("estoque")) e = e.copy(estoque = c.int("estoque"), estoqueConsumido = 0)
    c.int("estoque_minimo")?.let { e = e.copy(estoqueMinimo = it) }
    if (c.containsKey("observacoes")) e = e.copy(observacoes = c["observacoes"] as? String)
    (c["ativo"] as? Boolean)?.let { e = e.copy(ativo = it) }
    return e
}

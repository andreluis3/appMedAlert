package com.andre.medalert.data.mapper

import com.andre.medalert.data.local.entity.MedicationEntity
import com.andre.medalert.data.local.entity.estoqueAtual
import com.andre.medalert.data.local.entity.horariosLista
import com.andre.medalert.data.model.Medication

/**
 * Banco local -> model usado pela UI.
 * Campos que o aparelho ainda não conhece (quantidade, frequência) ficam vazios
 * e a tela simplesmente não mostra.
 *
 * localId = id da linha no Room: é o que a tela devolve ao pedir editar/excluir.
 * O ID do backend (remoteId) NÃO sai do repository.
 *
 * Os horários vêm de [horariosLista] (doseDiaria do backend). Quando os alarmes forem
 * implementados, a fonte dos horários deve ser trocada AQUI/nessa função, sem mexer nas telas.
 */
fun MedicationEntity.toMedication(): Medication {
    val horarios = horariosLista()

    val quantidadeTexto = if (quantidade != null) {
        listOfNotNull(quantidade.toString(), unidadeQuantidade).joinToString(" ")
    } else {
        ""
    }

    return Medication(
        id = id.toString(),
        name = nome,
        dosage = dosagem,
        quantity = quantidadeTexto,
        schedules = horarios,
        frequency = if (horarios.isNotEmpty()) "${horarios.size}x ao dia" else "",
        startDate = dataInicio,
        endDate = dataFim,
        notes = observacoes,
        active = ativo,
        localId = id,
        stock = estoqueAtual,
        stockMinimum = estoqueMinimo
    )
}

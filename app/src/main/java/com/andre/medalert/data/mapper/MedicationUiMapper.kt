package com.andre.medalert.data.mapper

import com.andre.medalert.data.local.entity.MedicationEntity
import com.andre.medalert.data.local.entity.horariosLista
import com.andre.medalert.data.model.Medication

/**
 * Banco local -> model usado pela UI.
 * Campos que a API não devolve (quantidade, frequência) ficam vazios
 * e a tela simplesmente não mostra.
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
        active = true
    )
}
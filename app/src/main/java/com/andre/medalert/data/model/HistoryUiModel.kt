package com.andre.medalert.data.model

import java.time.LocalDate

/**
 * Estados de uma dose no Histórico e no Calendário.
 *
 * - TOMADO: registrada até [com.andre.medalert.data.schedule.ON_TIME_TOLERANCE_MINUTES] depois do horário.
 * - TOMADO_COM_ATRASO: registrada depois da tolerância.
 * - PENDENTE: ainda não chegou o horário, ou está dentro da tolerância e sem registro.
 * - ATRASADO: sem registro, fora da tolerância e antes do limite de "não tomado".
 * - NAO_TOMADO: sem registro depois de [com.andre.medalert.data.schedule.MISSED_DOSE_THRESHOLD_MINUTES].
 */
enum class HistoryDoseStatus { TOMADO, TOMADO_COM_ATRASO, ATRASADO, NAO_TOMADO, PENDENTE }

/** Período exibido no Histórico. */
enum class HistoryPeriod(val label: String) {
    HOJE("Hoje"),
    SEMANA("Semana"),
    MES("Mês"),
    TODOS("Todos")
}

/** Filtro por status da lista do Histórico. */
enum class HistoryStatusFilter(val label: String) {
    TODOS("Todos"),
    TOMADOS("Tomados"),
    ATRASADOS("Atrasados"),
    NAO_TOMADOS("Não tomados")
}

/**
 * Uma dose específica de um medicamento em um dia, já pronta para a UI.
 * [date] é o dia previsto da dose (usado para agrupar Semana/Mês/Todos).
 */
data class HistoryUiModel(
    val medicationName: String,
    val dosage: String,
    val scheduledTime: String,
    val takenAt: String?,
    val status: HistoryDoseStatus,
    /** O medicamento foi excluído depois da dose; o registro da dose foi preservado. */
    val medicationRemoved: Boolean = false,
    val date: LocalDate? = null,
    /** "paciente", "cuidador" ou null quando a origem do registro não é conhecida. */
    val registeredBy: String? = null
)

/**
 * Regras do filtro por status:
 * - Tomados: tomado no prazo + tomado com atraso.
 * - Atrasados: atrasado (ainda não tomado) + tomado com atraso.
 * - Não tomados: somente doses não registradas depois do limite.
 */
fun HistoryUiModel.matches(filter: HistoryStatusFilter): Boolean = when (filter) {
    HistoryStatusFilter.TODOS -> true
    HistoryStatusFilter.TOMADOS ->
        status == HistoryDoseStatus.TOMADO || status == HistoryDoseStatus.TOMADO_COM_ATRASO
    HistoryStatusFilter.ATRASADOS ->
        status == HistoryDoseStatus.ATRASADO || status == HistoryDoseStatus.TOMADO_COM_ATRASO
    HistoryStatusFilter.NAO_TOMADOS -> status == HistoryDoseStatus.NAO_TOMADO
}


package com.andre.medalert.data.schedule

import com.andre.medalert.data.model.HistoryDoseStatus

/**
 * Decide quais doses merecem uma notificação de "atrasada" ou "não registrada".
 *
 * NÃO calcula status: usa o que o [DoseTimelineBuilder] já calculou com as regras únicas de
 * [DoseStatusRules] (as mesmas do Histórico e do Calendário). Assim a caixa de notificações nunca
 * discorda das outras telas sobre o que é atrasado ou não tomado.
 *
 * Só entram doses sem registro de administração, de medicamento que ainda existe.
 */
object OverdueDoseDetector {

    fun overdue(events: List<DoseEvent>): List<DoseEvent> =
        events.filter { event ->
            event.takenAt == null &&
                    !event.medicationRemoved &&
                    event.medicationLocalId != null &&
                    (event.status == HistoryDoseStatus.ATRASADO ||
                            event.status == HistoryDoseStatus.NAO_TOMADO)
        }
}
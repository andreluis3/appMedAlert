package com.andre.medalert.notification

import com.andre.medalert.data.local.entity.MedicationEntity
import com.andre.medalert.data.schedule.DoseScheduleCalculator
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Decisão PURA (sem Android) de quais alarmes agendar e quais cancelar. O
 * [MedicationReminderManager.apply] só executa o que este planejador decide, então a regra
 * (próxima dose, medicamento encerrado/inativo/removido) é testável em JVM.
 */
object ReminderPlanner {

    data class ScheduledDose(
        val medicationLocalId: Long,
        val medicationName: String,
        val triggerAtMillis: Long
    )

    data class Plan(
        /** Um alarme por medicamento, sempre para a PRÓXIMA dose depois de [after]. */
        val schedule: List<ScheduledDose>,
        /** Medicamentos que tinham alarme e não devem mais ter (removidos, inativos, encerrados). */
        val cancel: Set<Long>
    )

    /**
     * @param medications lista atual do paciente
     * @param previouslyScheduledIds ids que tinham alarme na última aplicação
     * @param after nunca agenda dose igual ou anterior a este instante (agora, ou a dose que acabou de tocar)
     */
    fun plan(
        medications: List<MedicationEntity>,
        previouslyScheduledIds: Set<Long>,
        after: LocalDateTime,
        zone: ZoneId = ZoneId.systemDefault()
    ): Plan {
        val schedule = medications.mapNotNull { med ->
            val next = DoseScheduleCalculator.nextDose(med, after) ?: return@mapNotNull null
            ScheduledDose(med.id, med.nome, next.atZone(zone).toInstant().toEpochMilli())
        }
        val keep = schedule.map { it.medicationLocalId }.toSet()
        return Plan(schedule = schedule, cancel = previouslyScheduledIds - keep)
    }
}

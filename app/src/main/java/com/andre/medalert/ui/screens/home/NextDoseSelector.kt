package com.andre.medalert.ui.screens.home

import com.andre.medalert.viewmodel.HomeDoseUi

/** Fase visual do card "Próximo medicamento". */
enum class NextDosePhase {
    /** Falta mais de 30 min. */
    WAITING,

    /** Faltam 30 min ou menos. */
    APPROACHING,

    /** Faltam 5 min ou menos. */
    IMMINENT,

    /** O horário chegou (primeiros minutos). */
    TIME_TO_TAKE,

    /** O horário passou e a dose não foi registrada. */
    OVERDUE
}

sealed interface NextDoseCardState {
    /** Nenhuma dose prevista para hoje. */
    object NoDoses : NextDoseCardState

    /** Todas as doses de hoje já foram registradas. */
    object AllTaken : NextDoseCardState

    /** [millisUntil] é negativo quando a dose já passou. */
    data class Pending(
        val dose: HomeDoseUi,
        val phase: NextDosePhase,
        val millisUntil: Long
    ) : NextDoseCardState
}

/**
 * Escolhe a dose em destaque a partir das doses de hoje que a Home JÁ calcula
 * (DoseScheduleCalculator + histórico do Room, via HomeViewModel). Não cria lógica paralela de
 * medicamentos: só ordena e classifica o que o UiState entrega.
 *
 * Prioridade:
 * 1. dose pendente que passou há no máximo [RECENT_OVERDUE_MS] (a mais antiga delas);
 * 2. a próxima dose pendente no futuro;
 * 3. se só restam pendentes antigas, a mais recente delas (continua pedindo registro).
 */
object NextDoseSelector {
    const val APPROACHING_MS = 30 * 60_000L
    const val IMMINENT_MS = 5 * 60_000L
    const val TIME_TO_TAKE_MS = 5 * 60_000L
    const val RECENT_OVERDUE_MS = 2 * 60 * 60_000L

    fun select(doses: List<HomeDoseUi>, nowMillis: Long): NextDoseCardState {
        if (doses.isEmpty()) return NextDoseCardState.NoDoses

        val pending = doses.filter { !it.isTaken }.sortedBy { it.scheduledAtMillis }
        if (pending.isEmpty()) return NextDoseCardState.AllTaken

        val recentOverdue = pending.firstOrNull {
            it.scheduledAtMillis <= nowMillis && nowMillis - it.scheduledAtMillis <= RECENT_OVERDUE_MS
        }
        val dose = recentOverdue
            ?: pending.firstOrNull { it.scheduledAtMillis > nowMillis }
            ?: pending.last()

        val delta = dose.scheduledAtMillis - nowMillis
        val phase = when {
            delta > APPROACHING_MS -> NextDosePhase.WAITING
            delta > IMMINENT_MS -> NextDosePhase.APPROACHING
            delta > 0 -> NextDosePhase.IMMINENT
            -delta < TIME_TO_TAKE_MS -> NextDosePhase.TIME_TO_TAKE
            else -> NextDosePhase.OVERDUE
        }
        return NextDoseCardState.Pending(dose, phase, delta)
    }
}
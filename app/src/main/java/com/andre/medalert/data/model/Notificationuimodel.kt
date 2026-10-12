package com.andre.medalert.data.model

/**
 * Categorias de notificação. Não existe endpoint de notificações na API
 * ainda, então esses valores são só do frontend por enquanto.
 */
enum class NotificationType {
    MEDICATION_REMINDER,
    MEDICATION_MISSED,
    MEDICATION_DELAYED,
    RESPONSIBLE_ADDED,
    RESPONSIBLE_ACCEPTED,
    NEW_MEDICATION,
    ADHERENCE_SUMMARY,
    SYSTEM
}

// TODO: substituir quando existir contrato da API de notificações.
data class NotificationUiModel(
    val id: Int,
    val titulo: String,
    val descricao: String,
    val horario: String, // texto livre por enquanto: "Hoje - 14:30", "Ontem"
    val tipo: NotificationType,
    val lida: Boolean,
    // Preenchidos só quando a notificação é sobre um medicamento específico,
    // pra habilitar as ações de "marcar como tomado" / "lembrar mais tarde".
    val medicationName: String? = null,
    val scheduledTime: String? = null
)
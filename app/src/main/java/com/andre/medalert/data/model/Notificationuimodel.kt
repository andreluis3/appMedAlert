package com.andre.medalert.data.model

/**
 * Categorias de notificação da caixa de entrada.
 *
 * O Room guarda o NOME do valor (texto). Um valor que este app não conhece (por exemplo, criado
 * por uma versão mais nova) é lido como [SYSTEM], então o app nunca quebra por causa de um tipo novo.
 */
enum class NotificationType {
    MEDICATION_REMINDER,
    MEDICATION_MISSED,
    MEDICATION_DELAYED,
    DOSE_TAKEN,
    NEW_MEDICATION,
    MEDICATION_EDITED,
    MEDICATION_REMOVED,
    RESPONSIBLE_ADDED,
    RESPONSIBLE_ACCEPTED,
    ADHERENCE_SUMMARY,
    SYSTEM;

    companion object {
        fun fromStored(raw: String?): NotificationType =
            entries.firstOrNull { it.name == raw } ?: SYSTEM
    }
}

/**
 * Notificação como a tela a exibe.
 *
 * - [horario]: "HH:mm" para hoje, "dd/MM HH:mm" para dias anteriores.
 * - [criadaEm]: epoch millis (a tela usa para agrupar em Hoje / Ontem / Anteriores).
 * - [acoesDisponiveis]: true só quando "Já tomei" / "Lembrar depois" podem agir de verdade:
 *   o usuário pode registrar dose, a dose é a mais recente do medicamento, está dentro da janela
 *   de ação e ainda não foi registrada.
 */
data class NotificationUiModel(
    val id: Long,
    val titulo: String,
    val descricao: String,
    val horario: String,
    val criadaEm: Long,
    val tipo: NotificationType,
    val lida: Boolean,
    val medicationName: String? = null,
    val acoesDisponiveis: Boolean = false
)
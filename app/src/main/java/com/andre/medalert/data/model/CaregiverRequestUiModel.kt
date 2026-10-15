package com.andre.medalert.data.model

/** Estado da solicitação de vínculo. PENDENTE é o estado inicial. */
enum class CaregiverRequestStatus { PENDENTE, ACEITA, RECUSADA }

/** Decisão do cuidador. */
enum class CaregiverDecision { ACEITAR, RECUSAR }

/**
 * Solicitação de vínculo paciente -> cuidador, como a tela de confirmação enxerga.
 *
 * PENDENTE DE CONTRATO COM O BACKEND (nada disso existe na API hoje):
 * - Só usamos nome e e-mail do paciente porque são os campos que existem no cadastro
 *   de usuário (UserData). Não inventar outros (foto, relação, data) sem confirmar.
 * - Os valores de status (PENDENTE/ACEITA/RECUSADA) vêm da proposta, não do backend.
 * - Quando o backend definir o formato, criar um DTO + mapper para este modelo,
 *   em vez de alterar a classe às pressas.
 */
data class CaregiverRequestUiModel(
    val id: String,
    val pacienteNome: String,
    val pacienteEmail: String,
    val status: CaregiverRequestStatus
)
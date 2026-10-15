package com.andre.medalert.data.mock

import com.andre.medalert.data.model.CaregiverRequestStatus
import com.andre.medalert.data.model.CaregiverRequestUiModel

/**
 * MOCK: não existe endpoint de solicitações de cuidador ainda.
 * Estes dados são de exemplo, só para desenvolver e testar a tela.
 */
val mockCaregiverRequestsIniciais = listOf(
    CaregiverRequestUiModel(
        id = "1",
        pacienteNome = "João (dado de exemplo)",
        pacienteEmail = "joao@exemplo.com",
        status = CaregiverRequestStatus.PENDENTE
    )
)
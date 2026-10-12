package com.andre.medalert.data.mock

import com.andre.medalert.data.model.HistoryDoseStatus
import com.andre.medalert.data.model.HistoryUiModel

/**
 * Mock por dia, só para visualizar a tela de detalhes enquanto a API não
 * tiver como buscar o histórico de um dia específico — hoje
 * GET /api/historys só aceita `id` (userId), sem parâmetro de data.
 *
 * TODO: quando o backend adicionar um parâmetro de data em
 * HistoryQueryRequest, trocar essa consulta por HistoryRepository.getHistory
 * também para os dias que não são "hoje".
 *
 * Chave = dia do mês (o calendário mockado é Agosto/2026).
 * Os status aqui batem com os status de mockCalendarDays (dia 2 = verde,
 * dia 5 = laranja, dia 9 = vermelho, dia 18 = vermelho).
 */
val mockDayDetails: Map<Int, List<HistoryUiModel>> = mapOf(
    2 to listOf(
        HistoryUiModel("Losartana", "50 mg", "08:00", "08:03", HistoryDoseStatus.TOMADO),
        HistoryUiModel("Vitamina D", "1000 UI", "12:00", "12:05", HistoryDoseStatus.TOMADO)
    ),
    5 to listOf(
        HistoryUiModel("Losartana", "50 mg", "08:00", "08:40", HistoryDoseStatus.ATRASADO),
        HistoryUiModel("Omeprazol", "20 mg", "20:00", "20:02", HistoryDoseStatus.TOMADO)
    ),
    9 to listOf(
        HistoryUiModel("Losartana", "50 mg", "08:00", null, HistoryDoseStatus.NAO_TOMADO),
        HistoryUiModel("Omeprazol", "20 mg", "20:00", "20:05", HistoryDoseStatus.TOMADO)
    ),
    18 to listOf(
        HistoryUiModel("Losartana", "50 mg", "08:00", null, HistoryDoseStatus.NAO_TOMADO)
    )
)
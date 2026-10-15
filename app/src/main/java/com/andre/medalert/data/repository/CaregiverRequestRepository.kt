package com.andre.medalert.data.repository

import com.andre.medalert.data.mock.mockCaregiverRequestsIniciais
import com.andre.medalert.data.model.CaregiverDecision
import com.andre.medalert.data.model.CaregiverRequestStatus
import com.andre.medalert.data.model.CaregiverRequestUiModel
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap

/**
 * Dados da solicitação de vínculo vista pelo cuidador.
 *
 * Não existe nenhum endpoint disso na API hoje (só POST /api/users/cuid,
 * sem corpo de resposta). PENDENTE DE CONTRATO COM O BACKEND:
 *  - Buscar uma solicitação pelo id e listar as pendentes do cuidador.
 *  - Aceitar/recusar (ex.: PATCH com o novo status). Resposta deve trazer o status final.
 *  - Como o cuidador é identificado: hoje o CreateCuidadorRequest usa email + nome;
 *    a proposta é cuidadorId. E um campo "role" (PACIENTE/CUIDADOR) nos usuários.
 *  - Já respondida: o backend deve rejeitar uma segunda resposta (ex.: 409).
 *  - Autenticação (JWT) para saber quem é o cuidador logado.
 *
 * Quando existir, criar CaregiverRequestRepositoryApi e trocar só o valor padrão
 * no ViewModel. Nenhuma tela precisa mudar.
 */
interface CaregiverRequestRepository {
    suspend fun obter(id: String): CaregiverRequestUiModel
    suspend fun responder(id: String, decisao: CaregiverDecision): CaregiverRequestUiModel
}

/** MOCK em memória, com atraso simulado para dar para ver o estado de carregamento. */
object MockCaregiverRequestRepository : CaregiverRequestRepository {

    private val solicitacoes = ConcurrentHashMap<String, CaregiverRequestUiModel>().apply {
        mockCaregiverRequestsIniciais.forEach { put(it.id, it) }
    }

    override suspend fun obter(id: String): CaregiverRequestUiModel {
        delay(500)
        return solicitacoes[id] ?: throw NoSuchElementException("Solicitação não encontrada.")
    }

    override suspend fun responder(id: String, decisao: CaregiverDecision): CaregiverRequestUiModel {
        delay(700)
        val atual = solicitacoes[id] ?: throw NoSuchElementException("Solicitação não encontrada.")

        // Já respondida: não muda nada (o backend real deve recusar a segunda resposta).
        if (atual.status != CaregiverRequestStatus.PENDENTE) return atual

        val novo = atual.copy(
            status = if (decisao == CaregiverDecision.ACEITAR) {
                CaregiverRequestStatus.ACEITA
            } else {
                CaregiverRequestStatus.RECUSADA
            }
        )
        solicitacoes[id] = novo
        return novo
    }
}
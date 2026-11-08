package com.andre.medalert.data.repository

import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.model.ResponsavelUiModel
import com.andre.medalert.data.remote.ApiService
import com.andre.medalert.data.remote.RetrofitClient
import com.andre.medalert.data.remote.dto.RequestCuidadorRequest
import com.andre.medalert.data.session.AccessPolicy
import kotlinx.coroutines.flow.first

/** Falta endpoint/contrato no backend para a operação pedida. */
class BackendDependencyException(message: String) : Exception(message)

/**
 * Abstração dos dados de Responsáveis.
 *
 * DEPENDÊNCIA DO BACKEND: não existe endpoint de listagem nem de remoção de responsáveis.
 * Só existe o convite (POST /api/users/requestCuidador), ligado em [ResponsavelRepositoryApi].
 */
interface ResponsavelRepository {
    suspend fun listar(): List<ResponsavelUiModel>
    suspend fun adicionar(nome: String, email: String): ResponsavelUiModel
    suspend fun remover(id: String)
}

/**
 * Convite do paciente ao cuidador: POST /api/users/requestCuidador com
 * {email do CUIDADOR, nome, userId do PACIENTE}; o backend envia o push ao cuidador (que precisa
 * ter um token FCM registrado, senão a API responde erro).
 *
 * O campo "nome" segue o comentário do contrato no projeto (nome do PACIENTE, exibido no convite);
 * se a API esperar o nome do cuidador, trocar só em [adicionar]. O nome digitado na tela aparece
 * no resultado local apenas.
 *
 * Sem endpoint de listagem/remoção: [listar] devolve vazio (sem inventar dados) e [remover]
 * avisa a dependência.
 */
class ResponsavelRepositoryApi(
    private val prefs: UserPreferencesRepository,
    private val api: ApiService = RetrofitClient.instance
) : ResponsavelRepository {

    override suspend fun listar(): List<ResponsavelUiModel> = emptyList()

    override suspend fun adicionar(nome: String, email: String): ResponsavelUiModel {
        val session = prefs.session.first()
        if (AccessPolicy.isCaregiver(session.tipo)) {
            throw IllegalStateException("Somente o paciente pode convidar responsáveis.")
        }
        val patientId = session.userId?.takeIf { it > 0 }
            ?: throw IllegalStateException("Sessão expirada. Faça login novamente.")
        val patientName = prefs.userName.first() ?: "Paciente"

        api.requestCuidador(
            RequestCuidadorRequest(email = email, nome = patientName, userId = patientId)
        ).close()

        return ResponsavelUiModel(
            id = email,
            nome = nome,
            email = email,
            relacao = "",
            status = "Convite enviado"
        )
    }

    override suspend fun remover(id: String) {
        throw BackendDependencyException("Não existe endpoint para remover responsáveis.")
    }
}

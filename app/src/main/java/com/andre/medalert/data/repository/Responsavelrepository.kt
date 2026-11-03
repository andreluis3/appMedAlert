package com.andre.medalert.data.repository

import com.andre.medalert.data.model.ResponsavelUiModel

/** Falta endpoint/contrato no backend para a operação pedida. */
class BackendDependencyException(message: String) : Exception(message)

/**
 * Abstração dos dados de Responsáveis.
 *
 * DEPENDÊNCIA DO BACKEND: não existe endpoint de listagem nem de remoção de responsáveis.
 * Existem apenas POST /api/users/cuidador (createCuidador) e POST /api/users/requestCuidador
 * (requestCuidador, convite do paciente). Quando o contrato de listagem existir, criar
 * ResponsavelRepositoryApi e trocar só o valor padrão nas telas.
 */
interface ResponsavelRepository {
    suspend fun listar(): List<ResponsavelUiModel>
    suspend fun adicionar(nome: String, email: String): ResponsavelUiModel
    suspend fun remover(id: String)
}

/** Sem dados inventados: lista vazia, e as operações sem endpoint avisam a dependência. */
object ResponsavelRepositoryPendente : ResponsavelRepository {

    override suspend fun listar(): List<ResponsavelUiModel> = emptyList()

    // TODO: ligar a ApiService.requestCuidador(...) quando eu tiver os DTOs RequestCuidadorRequest/CreateCuidadorRequest.
    override suspend fun adicionar(nome: String, email: String): ResponsavelUiModel =
        throw BackendDependencyException("Convite de responsável ainda não está ligado ao backend.")

    override suspend fun remover(id: String) {
        throw BackendDependencyException("Não existe endpoint para remover responsáveis.")
    }
}
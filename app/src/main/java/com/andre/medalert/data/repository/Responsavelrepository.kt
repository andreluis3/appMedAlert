package com.andre.medalert.data.repository

import androidx.compose.runtime.mutableStateListOf
import com.andre.medalert.data.mock.mockResponsaveisIniciais
import com.andre.medalert.data.model.ResponsavelUiModel
import java.util.UUID

/**
 * Abstração dos dados de Responsáveis. Métodos suspend para já ficar
 * compatível com uma futura implementação via API.
 *
 * Hoje só existe POST /api/users/cuid (usado por `adicionar`). Não há
 * GET nem DELETE de responsáveis na documentação, então `listar` e
 * `remover` continuam mock até o backend definir esses endpoints.
 */
interface ResponsavelRepository {
    suspend fun listar(): List<ResponsavelUiModel>
    suspend fun adicionar(nome: String, email: String): ResponsavelUiModel
    suspend fun remover(id: String)
}

/**
 * Implementação em memória. Quando o backend tiver os endpoints
 * completos, criar uma ResponsavelRepositoryApi (usando
 * ApiService.createCuidador para `adicionar`) e trocar apenas o valor
 * default usado pelas telas — nenhuma tela precisa mudar.
 */
object MockResponsavelRepository : ResponsavelRepository {

    private val responsaveis = mutableStateListOf<ResponsavelUiModel>().apply {
        addAll(mockResponsaveisIniciais)
    }

    override suspend fun listar(): List<ResponsavelUiModel> = responsaveis.toList()

    override suspend fun adicionar(nome: String, email: String): ResponsavelUiModel {
        val novo = ResponsavelUiModel(
            id = UUID.randomUUID().toString(),
            nome = nome,
            email = email,
            relacao = "Não informado", // TODO: Campo aguardando definição do contrato da API
            status = "Ativo"           // TODO: Campo aguardando definição do contrato da API
        )
        responsaveis.add(novo)
        return novo
    }

    override suspend fun remover(id: String) {
        responsaveis.removeAll { it.id == id }
    }
}
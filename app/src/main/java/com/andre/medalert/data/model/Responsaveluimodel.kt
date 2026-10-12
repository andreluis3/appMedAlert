package com.andre.medalert.data.model

/**
 * Modelo de exibição da tela de Responsáveis.
 *
 * `nome` e `email` correspondem aos campos que futuramente serão enviados
 * em POST /api/users/cuid (ver CreateCuidadorRequest). `id` é só um
 * identificador local, usado pela lista/remoção mock — não existe na API.
 *
 * `relacao` e `status` não têm contrato definido ainda. Quando o backend
 * confirmar esses campos, criar ResponsavelDto + um mapper para
 * ResponsavelUiModel, em vez de alterar esta classe às pressas.
 */
data class ResponsavelUiModel(
    val id: String,
    val nome: String,
    val email: String,
    val relacao: String, // TODO: Campo aguardando definição do contrato da API
    val status: String   // TODO: Campo aguardando definição do contrato da API
)
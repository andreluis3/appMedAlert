package com.andre.medalert.data.mock

import com.andre.medalert.data.model.ResponsavelUiModel

/** Lista inicial usada pelo MockResponsavelRepository. */
val mockResponsaveisIniciais = listOf(
    ResponsavelUiModel(
        id = "1",
        nome = "Maria Souza",
        email = "maria.souza@email.com",
        relacao = "Cônjuge",
        status = "Ativo"
    ),
    ResponsavelUiModel(
        id = "2",
        nome = "João Souza",
        email = "joao.souza@email.com",
        relacao = "Filho(a)",
        status = "Ativo"
    )
)
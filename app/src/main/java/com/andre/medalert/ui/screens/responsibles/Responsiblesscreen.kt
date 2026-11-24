package com.andre.medalert.ui.screens.responsibles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext

import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.model.ResponsavelUiModel
import com.andre.medalert.data.repository.ResponsavelRepository
import com.andre.medalert.data.repository.ResponsavelRepositoryApi

import com.andre.medalert.ui.theme.*

import kotlinx.coroutines.launch

@Composable
fun ResponsiblesScreen(
    reloadKey: Int = 0,
    repository: ResponsavelRepository = LocalContext.current.let { context ->
        remember {
            ResponsavelRepositoryApi(
                UserPreferencesRepository(context.applicationContext)
            )
        }
    },
    onBackClick: () -> Unit = {},
    onAddClick: () -> Unit = {}
) {

    val scope = rememberCoroutineScope()

    var responsaveis by remember {
        mutableStateOf<List<ResponsavelUiModel>>(emptyList())
    }

    var carregando by remember {
        mutableStateOf(true)
    }

    var erro by remember {
        mutableStateOf(false)
    }

    var responsavelParaRemover by remember {
        mutableStateOf<ResponsavelUiModel?>(null)
    }

    suspend fun carregar() {
        carregando = true
        erro = false

        runCatching {
            repository.listar()
        }.onSuccess {
            responsaveis = it
        }.onFailure {
            erro = true
        }

        carregando = false
    }

    LaunchedEffect(reloadKey) {
        carregar()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MedAlertBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {

        // Cabeçalho
        Column(
            modifier = Modifier.padding(
                horizontal = 20.dp
            )
        ) {

            TextButton(
                onClick = onBackClick,
                contentPadding = PaddingValues(0.dp)
            ) {

                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = null,
                    tint = MedAlertTextSecondary
                )

                Spacer(Modifier.width(6.dp))

                Text(
                    "Voltar",
                    color = MedAlertTextSecondary
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Responsáveis",
                color = MedAlertTextPrimary,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(5.dp))

            Text(
                text = "Pessoas que acompanham seu tratamento.",
                color = MedAlertTextSecondary,
                fontSize = 14.sp
            )
        }

        Spacer(Modifier.height(26.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {

            when {

                carregando -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MedAlertPrimary
                    )
                }

                erro -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {

                        Text(
                            "Não foi possível carregar os responsáveis.",
                            color = MedAlertTextSecondary
                        )

                        TextButton(
                            onClick = {
                                scope.launch { carregar() }
                            }
                        ) {
                            Text(
                                "Tentar novamente",
                                color = MedAlertPrimary
                            )
                        }
                    }
                }

                responsaveis.isEmpty() -> {

                    EmptyResponsibleState()
                }

                else -> {

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp)
                    ) {

                        Text(
                            "Seu responsável",
                            color = MedAlertTextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(14.dp))

                        responsaveis.forEach { responsavel ->

                            ResponsibleProfileCard(
                                responsavel = responsavel,
                                onRemove = {
                                    responsavelParaRemover = responsavel
                                }
                            )

                            Spacer(Modifier.height(24.dp))
                        }

                        ResponsibleInformation()

                        Spacer(Modifier.height(20.dp))
                    }
                }
            }
        }

        // Botão inferior
        Button(
            onClick = onAddClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MedAlertPrimary,
                contentColor = MedAlertBackground
            )
        ) {

            Text(
                text = if (responsaveis.isEmpty()) {
                    "Adicionar responsável"
                } else {
                    "Adicionar outro responsável"
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    // Confirmação de remoção
    responsavelParaRemover?.let { responsavel ->

        AlertDialog(
            onDismissRequest = {
                responsavelParaRemover = null
            },
            containerColor = MedAlertCard,
            title = {
                Text(
                    "Remover convite?",
                    color = MedAlertTextPrimary
                )
            },
            text = {
                Text(
                    "O registro de ${responsavel.nome} será removido deste aparelho. Isso não desfaz um vínculo já aceito.",
                    color = MedAlertTextSecondary
                )
            },
            confirmButton = {

                TextButton(
                    onClick = {
                        scope.launch {
                            runCatching {
                                repository.remover(responsavel.id)
                            }

                            responsavelParaRemover = null
                            carregar()
                        }
                    }
                ) {
                    Text(
                        "Remover",
                        color = MedAlertStatusRed
                    )
                }
            },
            dismissButton = {

                TextButton(
                    onClick = {
                        responsavelParaRemover = null
                    }
                ) {
                    Text(
                        "Cancelar",
                        color = MedAlertTextSecondary
                    )
                }
            }
        )
    }
}

@Composable
private fun ResponsibleProfileCard(
    responsavel: ResponsavelUiModel,
    onRemove: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MedAlertCard,
                RoundedCornerShape(22.dp)
            )
            .padding(18.dp)
    ) {

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {

            // Avatar
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .background(
                        MedAlertPrimary.copy(alpha = 0.16f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {

                Text(
                    text = responsavel.nome
                        .firstOrNull()
                        ?.uppercase()
                        ?: "?",
                    color = MedAlertPrimary,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    responsavel.nome,
                    color = MedAlertTextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    responsavel.email,
                    color = MedAlertTextSecondary,
                    fontSize = 13.sp
                )
            }

            IconButton(onClick = onRemove) {

                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Remover convite",
                    tint = MedAlertTextSecondary
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // Status
        Row(
            modifier = Modifier
                .background(
                    MedAlertPrimary.copy(alpha = 0.12f),
                    RoundedCornerShape(20.dp)
                )
                .padding(
                    horizontal = 12.dp,
                    vertical = 7.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MedAlertPrimary,
                modifier = Modifier.size(16.dp)
            )

            Spacer(Modifier.width(6.dp))

            Text(
                text = responsavel.status,
                color = MedAlertPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(16.dp))

        HorizontalDivider(
            color = MedAlertBackground
        )

        Spacer(Modifier.height(14.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {

            Icon(
                Icons.Filled.CalendarMonth,
                contentDescription = null,
                tint = MedAlertTextSecondary,
                modifier = Modifier.size(21.dp)
            )

            Spacer(Modifier.width(12.dp))

            Column {

                Text(
                    "Data do convite",
                    color = MedAlertTextSecondary,
                    fontSize = 12.sp
                )

                Text(
                    responsavel.relacao.removePrefix("Enviado em "),
                    color = MedAlertTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ResponsibleInformation() {

    Column {

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {

            Icon(
                Icons.Filled.People,
                contentDescription = null,
                tint = MedAlertPrimary,
                modifier = Modifier.size(25.dp)
            )

            Spacer(Modifier.width(12.dp))

            Text(
                "Acompanhamento",
                color = MedAlertTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Seu responsável poderá acompanhar informações sobre seus medicamentos e receber notificações após a confirmação do vínculo.",
            color = MedAlertTextSecondary,
            fontSize = 14.sp,
            lineHeight = 22.sp
        )

        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MedAlertCard,
                    RoundedCornerShape(16.dp)
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Icon(
                Icons.Filled.Shield,
                contentDescription = null,
                tint = MedAlertPrimary,
                modifier = Modifier.size(24.dp)
            )

            Spacer(Modifier.width(12.dp))

            Column {

                Text(
                    "Sua privacidade importa",
                    color = MedAlertTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )

                Text(
                    "O acompanhamento depende da confirmação do convite.",
                    color = MedAlertTextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun EmptyResponsibleState() {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 35.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Box(
            modifier = Modifier
                .size(100.dp)
                .background(
                    MedAlertCard,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {

            Icon(
                Icons.Filled.People,
                contentDescription = null,
                tint = MedAlertPrimary,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "Tenha alguém acompanhando você.",
            color = MedAlertTextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(10.dp))

        Text(
            "Adicione uma pessoa de confiança para convidá-la a acompanhar seu tratamento.",
            color = MedAlertTextSecondary,
            fontSize = 14.sp,
            lineHeight = 21.sp
        )
    }
}
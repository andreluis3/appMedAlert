package com.andre.medalert.ui.screens.responsibles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andre.medalert.data.model.ResponsavelUiModel
import com.andre.medalert.data.repository.MockResponsavelRepository
import com.andre.medalert.data.repository.ResponsavelRepository
import com.andre.medalert.ui.components.EmptyState
import com.andre.medalert.ui.components.ErrorState
import com.andre.medalert.ui.components.LoadingState
import com.andre.medalert.ui.theme.MedAlertBackground
import com.andre.medalert.ui.theme.MedAlertCard
import com.andre.medalert.ui.theme.MedAlertPrimary
import com.andre.medalert.ui.theme.MedAlertStatusGreen
import com.andre.medalert.ui.theme.MedAlertStatusRed
import com.andre.medalert.ui.theme.MedAlertTextPrimary
import com.andre.medalert.ui.theme.MedAlertTextSecondary
import kotlinx.coroutines.launch

private sealed interface ResponsiblesUiState {
    object Loading : ResponsiblesUiState
    data class Error(val message: String = "Não foi possível carregar os responsáveis.") : ResponsiblesUiState
    data class Success(val responsaveis: List<ResponsavelUiModel>) : ResponsiblesUiState
}

/**
 * Tela "Responsáveis". Lista e remoção são mock (não há GET/DELETE na
 * API ainda); "Adicionar" já monta os dados no formato de
 * POST /api/users/cuid (ver [ResponsavelRepository.adicionar]).
 *
 * `reloadKey`: incremente esse valor de fora (ex.: ao voltar da tela de
 * adicionar) para forçar a tela a recarregar a lista.
 */
@Composable
fun ResponsiblesScreen(
    reloadKey: Int = 0,
    repository: ResponsavelRepository = MockResponsavelRepository,
    onBackClick: () -> Unit = {},
    onAddClick: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var uiState by remember { mutableStateOf<ResponsiblesUiState>(ResponsiblesUiState.Loading) }
    var responsavelParaRemover by remember { mutableStateOf<ResponsavelUiModel?>(null) }

    suspend fun carregar() {
        uiState = ResponsiblesUiState.Loading
        runCatching { repository.listar() }
            .onSuccess { uiState = ResponsiblesUiState.Success(it) }
            .onFailure { uiState = ResponsiblesUiState.Error() }
    }

    LaunchedEffect(reloadKey) { carregar() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MedAlertBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            TextButton(onClick = onBackClick, contentPadding = PaddingValues(0.dp)) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = null,
                    tint = MedAlertTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Voltar", color = MedAlertTextSecondary, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Responsáveis",
                color = MedAlertTextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Pessoas que acompanham seu tratamento e recebem alertas sobre suas doses.",
                color = MedAlertTextSecondary,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (val state = uiState) {
                is ResponsiblesUiState.Loading -> LoadingState()

                is ResponsiblesUiState.Error -> ErrorState(
                    title = state.message,
                    onRetryClick = { scope.launch { carregar() } }
                )

                is ResponsiblesUiState.Success -> {
                    if (state.responsaveis.isEmpty()) {
                        EmptyState(
                            icon = Icons.Filled.People,
                            title = "Nenhum responsável cadastrado.",
                            message = "Adicione alguém de confiança para acompanhar seu tratamento.",
                            actionLabel = "Adicionar responsável",
                            onActionClick = onAddClick
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp)
                        ) {
                            state.responsaveis.forEach { responsavel ->
                                ResponsavelCard(
                                    responsavel = responsavel,
                                    onRemoveClick = { responsavelParaRemover = responsavel }
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        }
                    }
                }
            }
        }

        // Botão fixo, sempre visível (igual ao padrão das outras telas de formulário).
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
            Text("Adicionar responsável", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }

    responsavelParaRemover?.let { responsavel ->
        AlertDialog(
            onDismissRequest = { responsavelParaRemover = null },
            containerColor = MedAlertCard,
            title = {
                Text("Remover responsável?", color = MedAlertTextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "${responsavel.nome} deixará de acompanhar seu tratamento.",
                    color = MedAlertTextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repository.remover(responsavel.id)
                        responsavelParaRemover = null
                        carregar()
                    }
                }) {
                    Text("Remover", color = MedAlertStatusRed, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { responsavelParaRemover = null }) {
                    Text("Cancelar", color = MedAlertTextSecondary)
                }
            }
        )
    }
}

@Composable
private fun ResponsavelCard(
    responsavel: ResponsavelUiModel,
    onRemoveClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MedAlertCard, shape = RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(MedAlertPrimary.copy(alpha = 0.18f), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = responsavel.nome.firstOrNull()?.uppercase() ?: "?",
                color = MedAlertPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(responsavel.nome, color = MedAlertTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(responsavel.email, color = MedAlertTextSecondary, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(MedAlertStatusGreen.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = responsavel.status,
                        color = MedAlertStatusGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(responsavel.relacao, color = MedAlertTextSecondary, fontSize = 11.sp)
            }
        }

        IconButton(onClick = onRemoveClick) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Remover responsável",
                tint = MedAlertTextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
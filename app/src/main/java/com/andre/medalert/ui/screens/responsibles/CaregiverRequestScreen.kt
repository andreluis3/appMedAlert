package com.andre.medalert.ui.screens.responsibles
import com.andre.medalert.viewmodel.CaregiverRequestViewModel
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andre.medalert.data.model.CaregiverDecision
import com.andre.medalert.data.model.CaregiverRequestStatus
import com.andre.medalert.state.CaregiverRequestUiState
import com.andre.medalert.ui.components.ErrorState
import com.andre.medalert.ui.components.LoadingState
import com.andre.medalert.ui.theme.MedAlertBackground
import com.andre.medalert.ui.theme.MedAlertCard
import com.andre.medalert.ui.theme.MedAlertPrimary
import com.andre.medalert.ui.theme.MedAlertStatusGreen
import com.andre.medalert.ui.theme.MedAlertStatusOrange
import com.andre.medalert.ui.theme.MedAlertStatusRed
import com.andre.medalert.ui.theme.MedAlertTextPrimary
import com.andre.medalert.ui.theme.MedAlertTextSecondary

/**
 * Tela em que o cuidador vê a solicitação do paciente e aceita ou recusa.
 * Screen -> CaregiverRequestViewModel -> CaregiverRequestRepository -> (hoje) Mock.
 */
@Composable
fun CaregiverRequestScreen(
    onBackClick: () -> Unit = {},
    viewModel: CaregiverRequestViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MedAlertBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
            Spacer(modifier = Modifier.width(8.dp))
            Text("Solicitação", color = MedAlertTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (val state = uiState) {
                is CaregiverRequestUiState.Loading -> LoadingState()

                is CaregiverRequestUiState.Error -> ErrorState(
                    title = state.message,
                    onRetryClick = { viewModel.carregar() }
                )

                is CaregiverRequestUiState.Content -> RequestContent(
                    state = state,
                    onDecision = { viewModel.responder(it) },
                    onBackClick = onBackClick
                )
            }
        }
    }
}

@Composable
private fun RequestContent(
    state: CaregiverRequestUiState.Content,
    onDecision: (CaregiverDecision) -> Unit,
    onBackClick: () -> Unit
) {
    val request = state.request
    val enviando = state.submitting != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MedAlertCard, shape = RoundedCornerShape(16.dp))
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(MedAlertPrimary.copy(alpha = 0.15f), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.People,
                        contentDescription = null,
                        tint = MedAlertPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "Solicitação de acompanhamento",
                    color = MedAlertTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                StatusBadge(request.status)
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text("Enviada por", color = MedAlertTextSecondary, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = request.pacienteNome,
                color = MedAlertTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(request.pacienteEmail, color = MedAlertTextSecondary, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (request.status) {
            CaregiverRequestStatus.PENDENTE -> {
                Text(
                    text = "Ao aceitar, você passa a acompanhar o tratamento de ${request.pacienteNome}.",
                    color = MedAlertTextSecondary,
                    fontSize = 14.sp
                )

                if (state.actionError != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(state.actionError, color = MedAlertStatusRed, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { onDecision(CaregiverDecision.ACEITAR) },
                    enabled = !enviando,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MedAlertPrimary,
                        contentColor = MedAlertBackground
                    )
                ) {
                    Text(
                        text = if (state.submitting == CaregiverDecision.ACEITAR) "Aceitando..." else "Aceitar",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = { onDecision(CaregiverDecision.RECUSAR) },
                    enabled = !enviando,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MedAlertStatusRed)
                ) {
                    Text(
                        text = if (state.submitting == CaregiverDecision.RECUSAR) "Recusando..." else "Recusar",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            CaregiverRequestStatus.ACEITA, CaregiverRequestStatus.RECUSADA -> {
                val aceita = request.status == CaregiverRequestStatus.ACEITA
                val cor = if (aceita) MedAlertStatusGreen else MedAlertStatusRed

                val mensagem = when {
                    state.respondedNow && aceita -> "Solicitação aceita."
                    state.respondedNow -> "Solicitação recusada."
                    else -> "Esta solicitação já foi respondida."
                }

                Text(
                    text = mensagem,
                    color = cor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onBackClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MedAlertPrimary,
                        contentColor = MedAlertBackground
                    )
                ) {
                    Text("Voltar", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: CaregiverRequestStatus) {
    val (cor, rotulo) = when (status) {
        CaregiverRequestStatus.PENDENTE -> MedAlertStatusOrange to "Pendente"
        CaregiverRequestStatus.ACEITA -> MedAlertStatusGreen to "Aceita"
        CaregiverRequestStatus.RECUSADA -> MedAlertStatusRed to "Recusada"
    }

    Box(
        modifier = Modifier
            .background(cor.copy(alpha = 0.15f), shape = RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(rotulo, color = cor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
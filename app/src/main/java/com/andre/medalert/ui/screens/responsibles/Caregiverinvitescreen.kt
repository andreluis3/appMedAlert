package com.andre.medalert.ui.screens.responsibles

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andre.medalert.data.model.PendingInvite
import com.andre.medalert.state.CaregiverInviteUiState
import com.andre.medalert.ui.components.EmptyState
import com.andre.medalert.ui.components.LoadingState
import com.andre.medalert.ui.theme.MedAlertBackground
import com.andre.medalert.ui.theme.MedAlertCard
import com.andre.medalert.ui.theme.MedAlertPrimary
import com.andre.medalert.ui.theme.MedAlertStatusOrange
import com.andre.medalert.ui.theme.MedAlertStatusRed
import com.andre.medalert.ui.theme.MedAlertTextPrimary
import com.andre.medalert.ui.theme.MedAlertTextSecondary
import com.andre.medalert.viewmodel.CaregiverInviteViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Convite pendente visto pelo cuidador (veio do push de requestCuidador e ficou guardado).
 * Mesmo padrão visual das telas de Responsáveis/Solicitação: fundo escuro, card MedAlertCard,
 * turquesa MedAlertPrimary e botões arredondados fixos na base.
 *
 * ETAPA 1: os botões ainda NÃO chamam a API; quem usa a tela decide o que fazer em
 * [onAcceptClick] / [onDeclineClick].
 */
@Composable
fun CaregiverInviteScreen(
    onBackClick: () -> Unit = {},
    onAcceptClick: (PendingInvite) -> Unit = {},
    onDeclineClick: (PendingInvite) -> Unit = {},
    viewModel: CaregiverInviteViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

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
                text = "Convite de acompanhamento",
                color = MedAlertTextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Um paciente quer você como cuidador.",
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
                is CaregiverInviteUiState.Loading -> LoadingState()

                is CaregiverInviteUiState.NotFound -> EmptyState(
                    icon = Icons.Filled.People,
                    title = "Convite não encontrado",
                    message = "Esse convite já foi tratado ou não está mais disponível neste aparelho.",
                    actionLabel = "Voltar",
                    onActionClick = onBackClick
                )

                is CaregiverInviteUiState.Found -> InviteContent(state.invite)
            }
        }

        val found = uiState as? CaregiverInviteUiState.Found
        if (found != null) {
            Button(
                onClick = { onAcceptClick(found.invite) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MedAlertPrimary,
                    contentColor = MedAlertBackground
                )
            ) {
                Text("Aceitar vínculo", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = { onDeclineClick(found.invite) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MedAlertStatusRed)
            ) {
                Text("Recusar", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun InviteContent(invite: PendingInvite) {
    val recebidoEm = remember(invite.receivedAtMillis) {
        SimpleDateFormat("dd/MM 'às' HH:mm", Locale("pt", "BR")).format(Date(invite.receivedAtMillis))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
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

                Box(
                    modifier = Modifier
                        .background(
                            MedAlertStatusOrange.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        "Pendente",
                        color = MedAlertStatusOrange,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // O push só traz o ID do paciente: o nome NÃO é inventado.
            Text("Enviada por", color = MedAlertTextSecondary, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Um paciente do aplicativo",
                color = MedAlertTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Código do paciente: ${invite.pacienteId}",
                color = MedAlertTextSecondary,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Recebido em $recebidoEm",
                color = MedAlertTextSecondary,
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Esse paciente deseja vincular você como cuidador. Ao aceitar, você poderá " +
                    "acompanhar os medicamentos e o histórico dele, somente para consulta.",
            color = MedAlertTextSecondary,
            fontSize = 14.sp
        )
    }
}
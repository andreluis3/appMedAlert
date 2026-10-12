package com.andre.medalert.ui.screens.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andre.medalert.data.model.NotificationType
import com.andre.medalert.data.model.NotificationUiModel
import com.andre.medalert.ui.components.EmptyState
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
import com.andre.medalert.viewmodel.NotificationUiState
import com.andre.medalert.viewmodel.NotificationViewModel

/**
 * Tela "Notificações", aberta só pelo sininho da Home.
 * Screen -> NotificationViewModel -> NotificationRepository -> Mock
 * (troca fácil pra API real quando o endpoint existir, sem mexer na tela).
 */
@Composable
fun NotificationScreen(
    onBackClick: () -> Unit = {},
    viewModel: NotificationViewModel = viewModel()
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
            Text("Notificações", color = MedAlertTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (val state = uiState) {
                is NotificationUiState.Loading -> LoadingState()

                is NotificationUiState.Error -> ErrorState(
                    title = state.message,
                    onRetryClick = { viewModel.carregar() }
                )

                is NotificationUiState.Success -> {
                    if (state.notifications.isEmpty()) {
                        EmptyState(
                            icon = Icons.Filled.NotificationsNone,
                            title = "Nenhuma notificação",
                            message = "Você está em dia com seus medicamentos."
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp)
                                .padding(bottom = 20.dp)
                        ) {
                            state.notifications.forEach { notification ->
                                NotificationCard(
                                    notification = notification,
                                    onClick = { viewModel.marcarComoLida(notification.id) },
                                    onMarkTakenClick = { viewModel.marcarComoTomado(notification.id) },
                                    onRemindLaterClick = { viewModel.lembrarMaisTarde(notification.id) }
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class NotificationVisuals(val icon: ImageVector, val color: Color)

@Composable
private fun visualsFor(tipo: NotificationType): NotificationVisuals = when (tipo) {
    NotificationType.MEDICATION_REMINDER ->
        NotificationVisuals(Icons.Filled.Notifications, MedAlertPrimary)
    NotificationType.MEDICATION_MISSED ->
        NotificationVisuals(Icons.Filled.Cancel, MedAlertStatusRed)
    NotificationType.MEDICATION_DELAYED ->
        NotificationVisuals(Icons.Filled.PriorityHigh, MedAlertStatusOrange)
    NotificationType.RESPONSIBLE_ADDED, NotificationType.RESPONSIBLE_ACCEPTED ->
        NotificationVisuals(Icons.Filled.People, MedAlertStatusGreen)
    NotificationType.NEW_MEDICATION ->
        NotificationVisuals(Icons.Filled.Medication, MedAlertPrimary)
    NotificationType.ADHERENCE_SUMMARY ->
        NotificationVisuals(Icons.Filled.ShowChart, MedAlertPrimary)
    NotificationType.SYSTEM ->
        NotificationVisuals(Icons.Filled.Notifications, MedAlertTextSecondary)
}

@Composable
private fun NotificationCard(
    notification: NotificationUiModel,
    onClick: () -> Unit,
    onMarkTakenClick: () -> Unit,
    onRemindLaterClick: () -> Unit
) {
    val visuals = visualsFor(notification.tipo)
    val showActions = notification.tipo == NotificationType.MEDICATION_REMINDER ||
            notification.tipo == NotificationType.MEDICATION_DELAYED ||
            notification.tipo == NotificationType.MEDICATION_MISSED

    val (markLabel, remindLabel) = when (notification.tipo) {
        NotificationType.MEDICATION_REMINDER -> "Já tomei" to "Lembrar depois"
        NotificationType.MEDICATION_DELAYED -> "Confirmar tomada" to "Lembrar depois"
        else -> "Marcar como tomado" to "Me lembrar mais tarde"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MedAlertCard, shape = RoundedCornerShape(16.dp))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(visuals.color.copy(alpha = 0.15f), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(visuals.icon, contentDescription = null, tint = visuals.color, modifier = Modifier.size(18.dp))
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = notification.titulo,
                        color = MedAlertTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!notification.lida) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(MedAlertPrimary, shape = CircleShape)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(notification.descricao, color = MedAlertTextSecondary, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(notification.horario, color = MedAlertTextSecondary, fontSize = 11.sp)
            }
        }

        if (showActions) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onMarkTakenClick,
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MedAlertPrimary,
                        contentColor = MedAlertBackground
                    )
                ) {
                    Text(markLabel, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onRemindLaterClick,
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MedAlertTextSecondary)
                ) {
                    Text(remindLabel, fontSize = 13.sp)
                }
            }
        }
    }
}
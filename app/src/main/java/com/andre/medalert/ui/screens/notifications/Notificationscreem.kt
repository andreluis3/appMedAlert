package com.andre.medalert.ui.screens.notifications

import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.andre.medalert.viewmodel.NotificationFilter
import com.andre.medalert.viewmodel.NotificationUiState
import com.andre.medalert.viewmodel.NotificationViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Tela "Notificações", aberta só pelo sininho da Home: caixa de entrada com as últimas
 * atualizações do aplicativo, mais recentes primeiro.
 * Screen -> NotificationViewModel -> NotificationRepository -> Room (NotificationEntity).
 */
@Composable
fun NotificationScreen(
    onBackClick: () -> Unit = {},
    viewModel: NotificationViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

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
            Text(
                "Notificações",
                color = MedAlertTextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            val success = uiState as? NotificationUiState.Success
            if (success != null && success.unreadCount > 0) {
                TextButton(
                    onClick = { viewModel.marcarTodasComoLidas() },
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(
                        Icons.Filled.DoneAll,
                        contentDescription = null,
                        tint = MedAlertPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ler todas", color = MedAlertPrimary, fontSize = 13.sp)
                }
            }
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
                    Column(modifier = Modifier.fillMaxSize()) {
                        FilterRow(
                            selected = state.filter,
                            unreadCount = state.unreadCount,
                            onSelect = { viewModel.selecionarFiltro(it) }
                        )

                        if (state.notifications.isEmpty()) {
                            val (title, message) = emptyTexts(state.filter)
                            EmptyState(
                                icon = Icons.Filled.NotificationsNone,
                                title = title,
                                message = message
                            )
                        } else {
                            val items = remember(state.notifications) {
                                buildItems(state.notifications)
                            }
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 20.dp,
                                    end = 20.dp,
                                    top = 4.dp,
                                    bottom = 20.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(items, key = { it.key }) { item ->
                                    when (item) {
                                        is ListItem.Header -> Text(
                                            text = item.label,
                                            color = MedAlertTextSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(top = 6.dp)
                                        )

                                        is ListItem.Entry -> NotificationCard(
                                            notification = item.notification,
                                            onClick = {
                                                if (!item.notification.lida) {
                                                    viewModel.marcarComoLida(item.notification.id)
                                                }
                                            },
                                            onMarkTakenClick = {
                                                viewModel.marcarComoTomado(item.notification.id)
                                            },
                                            onRemindLaterClick = {
                                                viewModel.lembrarMaisTarde(item.notification.id)
                                            },
                                            onDeleteClick = {
                                                viewModel.excluir(item.notification.id)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ lista agrupada por dia

private sealed interface ListItem {
    val key: String

    data class Header(val label: String) : ListItem {
        override val key: String get() = "h:$label"
    }

    data class Entry(val notification: NotificationUiModel) : ListItem {
        override val key: String get() = "n:${notification.id}"
    }
}

/** A lista chega ordenada da mais recente para a mais antiga, então os grupos ficam contíguos. */
private fun buildItems(list: List<NotificationUiModel>): List<ListItem> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val out = mutableListOf<ListItem>()
    var currentLabel: String? = null

    list.forEach { n ->
        val day = Instant.ofEpochMilli(n.criadaEm).atZone(zone).toLocalDate()
        val label = when (day) {
            today -> "Hoje"
            today.minusDays(1) -> "Ontem"
            else -> "Anteriores"
        }
        if (label != currentLabel) {
            out += ListItem.Header(label)
            currentLabel = label
        }
        out += ListItem.Entry(n)
    }
    return out
}

private fun emptyTexts(filter: NotificationFilter): Pair<String, String> = when (filter) {
    NotificationFilter.TODAS ->
        "Nenhuma notificação" to "As atualizações do aplicativo vão aparecer aqui."

    NotificationFilter.NAO_LIDAS ->
        "Nenhuma notificação não lida" to "Você já viu todas as atualizações."

    NotificationFilter.LIDAS ->
        "Nenhuma notificação lida" to "As notificações que você abrir aparecem aqui."
}

// ------------------------------------------------------------------ filtros

@Composable
private fun FilterRow(
    selected: NotificationFilter,
    unreadCount: Int,
    onSelect: (NotificationFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterPill(
            label = "Todas",
            selected = selected == NotificationFilter.TODAS,
            onClick = { onSelect(NotificationFilter.TODAS) }
        )
        FilterPill(
            label = if (unreadCount > 0) "Não lidas ($unreadCount)" else "Não lidas",
            selected = selected == NotificationFilter.NAO_LIDAS,
            onClick = { onSelect(NotificationFilter.NAO_LIDAS) }
        )
        FilterPill(
            label = "Lidas",
            selected = selected == NotificationFilter.LIDAS,
            onClick = { onSelect(NotificationFilter.LIDAS) }
        )
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                if (selected) MedAlertPrimary else MedAlertCard,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            color = if (selected) MedAlertBackground else MedAlertTextSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// ------------------------------------------------------------------ cartão

private data class NotificationVisuals(val icon: ImageVector, val color: Color)

@Composable
private fun visualsFor(tipo: NotificationType): NotificationVisuals = when (tipo) {
    NotificationType.MEDICATION_REMINDER ->
        NotificationVisuals(Icons.Filled.Notifications, MedAlertPrimary)
    NotificationType.MEDICATION_MISSED ->
        NotificationVisuals(Icons.Filled.Cancel, MedAlertStatusRed)
    NotificationType.MEDICATION_DELAYED ->
        NotificationVisuals(Icons.Filled.PriorityHigh, MedAlertStatusOrange)
    NotificationType.DOSE_TAKEN ->
        NotificationVisuals(Icons.Filled.CheckCircle, MedAlertStatusGreen)
    NotificationType.NEW_MEDICATION ->
        NotificationVisuals(Icons.Filled.Medication, MedAlertPrimary)
    NotificationType.MEDICATION_EDITED ->
        NotificationVisuals(Icons.Filled.Edit, MedAlertPrimary)
    NotificationType.MEDICATION_REMOVED ->
        NotificationVisuals(Icons.Filled.Delete, MedAlertTextSecondary)
    NotificationType.RESPONSIBLE_ADDED, NotificationType.RESPONSIBLE_ACCEPTED ->
        NotificationVisuals(Icons.Filled.People, MedAlertStatusGreen)
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
    onRemindLaterClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val visuals = visualsFor(notification.tipo)

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
                        fontWeight = if (notification.lida) FontWeight.Medium else FontWeight.Bold,
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

            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Excluir notificação",
                    tint = MedAlertTextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Só aparecem quando a ação é real: dose recente, ainda não registrada e permitida ao usuário.
        if (notification.acoesDisponiveis) {
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
package com.andre.medalert.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andre.medalert.data.model.HistoryDoseStatus
import com.andre.medalert.data.model.HistoryUiModel
import com.andre.medalert.ui.components.BottomNavigationBar
import com.andre.medalert.ui.components.EmptyState
import com.andre.medalert.ui.components.ErrorState
import com.andre.medalert.ui.components.LoadingState
import com.andre.medalert.ui.theme.MedAlertBackground
import com.andre.medalert.ui.theme.MedAlertCard
import com.andre.medalert.ui.theme.MedAlertPrimary
import com.andre.medalert.ui.theme.MedAlertStatusGreen
import com.andre.medalert.ui.theme.MedAlertStatusOrange
import com.andre.medalert.ui.theme.MedAlertStatusRed
import com.andre.medalert.ui.theme.MedAlertTextSecondary
import com.andre.medalert.viewmodel.HistoryUiState
import com.andre.medalert.viewmodel.HistoryViewModel

private const val FILTER_ALL = "Todos"

/**
 * ATENÇÃO: esta tela AINDA NÃO usa offline-first de verdade.
 *
 * - O refresh chama api.getHistory (GET com body), rota que não existe na API; ele falha
 *   e a tela cai no que está no Room.
 * - O Room só tem as doses registradas localmente pela notificação ("Tomar agora"),
 *   marcadas com isSynced = false e NUNCA enviadas à API.
 * - A consulta é só do dia atual (HistoryRepository.observeHistory -> observeByUserOnDay).
 *
 * PENDENTE: ver com o backend a integração real do histórico (contrato do POST /api/historys
 * e do GET de doses) antes de tratar esta tela como offline-first.
 */


@Composable
fun HistoryScreen(
    onNavigate: (String) -> Unit = {},
    onAdherenceClick: () -> Unit = {},
    viewModel: HistoryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = MedAlertBackground,
        bottomBar = {
            BottomNavigationBar(selectedRoute = "history", onItemClick = onNavigate)
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MedAlertBackground)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 24.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Histórico", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("Doses de hoje", color = MedAlertPrimary, fontSize = 14.sp)
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MedAlertCard)
                        .clickable { onAdherenceClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ShowChart, contentDescription = "Ver adesão", tint = MedAlertPrimary)
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (val state = uiState) {
                    is HistoryUiState.Loading -> LoadingState()

                    is HistoryUiState.Error -> ErrorState(
                        title = state.message,
                        onRetryClick = { viewModel.load() }
                    )

                    is HistoryUiState.Content -> {
                        if (state.records.isEmpty()) {
                            EmptyState(
                                icon = Icons.Filled.History,
                                title = "Nenhum registro ainda",
                                message = "Quando houver doses registradas hoje, elas aparecem aqui."
                            )
                        } else {
                            HistoryContent(records = state.records, onAdherenceClick = onAdherenceClick)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryContent(records: List<HistoryUiModel>, onAdherenceClick: () -> Unit) {
    var selectedFilter by remember { mutableStateOf(FILTER_ALL) }
    val filters = remember(records) {
        listOf(FILTER_ALL) + records.map { it.medicationName }.distinct().sorted()
    }
    val filtered = records.filter { selectedFilter == FILTER_ALL || it.medicationName == selectedFilter }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filters.forEach { filter ->
                MedFilterChip(
                    text = filter,
                    selected = filter == selectedFilter,
                    onClick = { selectedFilter = filter }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Hoje • ${filtered.size} ${if (filtered.size == 1) "registro" else "registros"}",
            color = MedAlertTextSecondary,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        filtered.forEach { record ->
            HistoryRecordRow(record)
            Spacer(modifier = Modifier.height(10.dp))
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MedAlertCard)
                .clickable { onAdherenceClick() }
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.ShowChart, contentDescription = null, tint = MedAlertPrimary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Ver minha adesão", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun MedFilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) MedAlertPrimary.copy(alpha = 0.2f) else MedAlertCard)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) MedAlertPrimary else MedAlertTextSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun HistoryRecordRow(record: HistoryUiModel) {
    val (statusColor, statusIcon, statusLabel) = when (record.status) {
        HistoryDoseStatus.TOMADO -> Triple(MedAlertStatusGreen, Icons.Default.Check, "Tomado")
        HistoryDoseStatus.ATRASADO -> Triple(MedAlertStatusOrange, Icons.Default.PriorityHigh, "Atrasado")
        HistoryDoseStatus.NAO_TOMADO -> Triple(MedAlertStatusRed, Icons.Default.Close, "Não tomado")
        HistoryDoseStatus.PENDENTE -> Triple(MedAlertTextSecondary, Icons.Default.RadioButtonUnchecked, "Pendente")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MedAlertCard)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(statusColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(statusIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(record.medicationName, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            val subtitle = buildString {
                append(record.scheduledTime)
                append(" • ")
                append(record.dosage)
                if (record.takenAt != null) {
                    append(" • às ")
                    append(record.takenAt)
                }
            }
            Text(subtitle, color = MedAlertTextSecondary, fontSize = 12.sp)
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(statusColor.copy(alpha = 0.15f))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(statusLabel, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
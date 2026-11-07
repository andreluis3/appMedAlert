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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andre.medalert.data.model.HistoryDoseStatus
import com.andre.medalert.data.model.HistoryPeriod
import com.andre.medalert.data.model.HistoryStatusFilter
import com.andre.medalert.data.model.HistoryUiModel
import com.andre.medalert.data.model.matches
import com.andre.medalert.ui.components.BottomNavigationBar
import com.andre.medalert.ui.components.CaregiverAwaitingLinkState
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val FILTER_ALL = "Todos"

private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
private val DAY_HEADER_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, dd/MM", PT_BR)

/**
 * Histórico real: doses previstas (agenda dos medicamentos) cruzadas com os registros de
 * administração do Room. Funciona offline e sem MockData.
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
                    Text("Registros das suas doses", color = MedAlertPrimary, fontSize = 14.sp)
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

                    is HistoryUiState.AwaitingLink -> CaregiverAwaitingLinkState()

                    is HistoryUiState.Content -> HistoryContent(
                        period = state.period,
                        records = state.records,
                        onPeriodSelected = { viewModel.selectPeriod(it) },
                        onAdherenceClick = onAdherenceClick
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryContent(
    period: HistoryPeriod,
    records: List<HistoryUiModel>,
    onPeriodSelected: (HistoryPeriod) -> Unit,
    onAdherenceClick: () -> Unit
) {
    var selectedStatus by remember { mutableStateOf(HistoryStatusFilter.TODOS) }
    var selectedMedication by remember { mutableStateOf(FILTER_ALL) }

    val medicationFilters = remember(records) {
        listOf(FILTER_ALL) + records.map { it.medicationName }.distinct().sorted()
    }
    // Se o medicamento escolhido não existe mais no período atual, volta para "Todos".
    val activeMedication = if (selectedMedication in medicationFilters) selectedMedication else FILTER_ALL

    val filtered = remember(records, selectedStatus, activeMedication) {
        records.filter { record ->
            record.matches(selectedStatus) &&
                    (activeMedication == FILTER_ALL || record.medicationName == activeMedication)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
    ) {
        PeriodTabs(selected = period, onSelected = onPeriodSelected)

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HistoryStatusFilter.values().forEach { filter ->
                MedFilterChip(
                    text = filter.label,
                    selected = filter == selectedStatus,
                    onClick = { selectedStatus = filter }
                )
            }
        }

        // Filtro por medicamento só aparece quando há mais de um no período.
        if (medicationFilters.size > 2) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                medicationFilters.forEach { name ->
                    MedFilterChip(
                        text = name,
                        selected = name == activeMedication,
                        onClick = { selectedMedication = name }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "${period.label} • ${filtered.size} ${if (filtered.size == 1) "registro" else "registros"}",
            color = MedAlertTextSecondary,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        when {
            filtered.isEmpty() -> EmptyMessage(
                if (records.isEmpty()) {
                    "Nenhuma dose prevista neste período. Cadastre um medicamento para acompanhar suas doses."
                } else {
                    "Nenhuma dose corresponde aos filtros escolhidos."
                }
            )

            period == HistoryPeriod.HOJE -> filtered.forEach { record ->
                HistoryRecordRow(record)
                Spacer(modifier = Modifier.height(10.dp))
            }

            else -> {
                val today = LocalDate.now()
                filtered
                    .groupBy { it.date ?: LocalDate.MIN }
                    .toList()
                    .sortedByDescending { it.first }
                    .forEach { (date, items) ->
                        Text(
                            text = dayLabel(date, today),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        items.forEach { record ->
                            HistoryRecordRow(record)
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
            }
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

private fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Hoje"
    today.minusDays(1) -> "Ontem"
    today.plusDays(1) -> "Amanhã"
    else -> DAY_HEADER_FORMAT.format(date).replaceFirstChar { it.uppercase() }
}

@Composable
private fun PeriodTabs(selected: HistoryPeriod, onSelected: (HistoryPeriod) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MedAlertCard)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        HistoryPeriod.values().forEach { period ->
            val isSelected = period == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) MedAlertPrimary else Color.Transparent)
                    .clickable { onSelected(period) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = period.label,
                    color = if (isSelected) MedAlertBackground else MedAlertTextSecondary,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun EmptyMessage(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MedAlertCard)
            .padding(horizontal = 16.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = MedAlertTextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
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
        HistoryDoseStatus.TOMADO_COM_ATRASO -> Triple(MedAlertStatusOrange, Icons.Default.Check, "Com atraso")
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
            Text(
                if (record.medicationRemoved) "${record.medicationName} (medicamento excluído)" else record.medicationName,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
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
package com.andre.medalert.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.andre.medalert.data.mock.mockHistoryMedicationFilters
import com.andre.medalert.data.mock.mockHistoryRecords
import com.andre.medalert.data.model.DoseHistoryRecord
import com.andre.medalert.data.model.DoseHistoryStatus
import com.andre.medalert.ui.components.BottomNavigationBar
import com.andre.medalert.ui.theme.MedAlertBackground
import com.andre.medalert.ui.theme.MedAlertCard
import com.andre.medalert.ui.theme.MedAlertPrimary
import com.andre.medalert.ui.theme.MedAlertStatusGreen
import com.andre.medalert.ui.theme.MedAlertStatusOrange
import com.andre.medalert.ui.theme.MedAlertStatusRed
import com.andre.medalert.ui.theme.MedAlertTextSecondary

private val periodOptions = listOf("Hoje", "Semana", "Mês")

@Composable
fun HistoryScreen(
    onNavigate: (String) -> Unit = {},
    onAdherenceClick: () -> Unit = {}
) {
    var selectedPeriod by remember { mutableStateOf("Hoje") }
    var selectedMedFilter by remember { mutableStateOf("Todos") }

    val filteredRecords = mockHistoryRecords.filter {
        selectedMedFilter == "Todos" || it.medicationName == selectedMedFilter
    }

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                periodOptions.forEach { period ->
                    PeriodPill(
                        text = period,
                        selected = period == selectedPeriod,
                        onClick = { selectedPeriod = period },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                mockHistoryMedicationFilters.forEach { filter ->
                    MedFilterChip(
                        text = filter,
                        selected = filter == selectedMedFilter,
                        onClick = { selectedMedFilter = filter }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "$selectedPeriod • ${filteredRecords.size} registros",
                color = MedAlertTextSecondary,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            filteredRecords.forEach { record ->
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
}

@Composable
private fun PeriodPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MedAlertPrimary else MedAlertCard)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) MedAlertBackground else MedAlertTextSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun MedFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MedAlertCard)
            .then(
                if (selected) Modifier.background(MedAlertCard) else Modifier
            )
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
private fun HistoryRecordRow(record: DoseHistoryRecord) {
    val (statusColor, statusIcon, statusLabel) = when (record.status) {
        DoseHistoryStatus.TOMADO -> Triple(MedAlertStatusGreen, Icons.Default.Check, "Tomado")
        DoseHistoryStatus.ATRASADO -> Triple(MedAlertStatusOrange, Icons.Default.PriorityHigh, "Atrasado")
        DoseHistoryStatus.NAO_TOMADO -> Triple(MedAlertStatusRed, Icons.Default.Close, "Não tomado")
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
                if (record.actualTime != null) {
                    append(" • às ")
                    append(record.actualTime)
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
package com.andre.medalert.ui.screens.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andre.medalert.data.schedule.CalendarDayStatus
import com.andre.medalert.data.schedule.CalendarDayUi
import com.andre.medalert.data.schedule.labelPtBr
import com.andre.medalert.ui.components.BottomNavigationBar
import com.andre.medalert.ui.theme.MedAlertBackground
import com.andre.medalert.ui.theme.MedAlertCard
import com.andre.medalert.ui.theme.MedAlertPrimary
import com.andre.medalert.ui.theme.MedAlertStatusGreen
import com.andre.medalert.ui.theme.MedAlertStatusOrange
import com.andre.medalert.ui.theme.MedAlertStatusRed
import com.andre.medalert.ui.theme.MedAlertTextSecondary
import com.andre.medalert.viewmodel.CalendarViewModel

private val weekDayLabels = listOf("D", "S", "T", "Q", "Q", "S", "S")

private fun statusColor(status: CalendarDayStatus): Color = when (status) {
    CalendarDayStatus.NONE -> Color.Transparent
    CalendarDayStatus.PENDING -> MedAlertPrimary.copy(alpha = 0.25f)
    CalendarDayStatus.ALL_TAKEN -> MedAlertStatusGreen.copy(alpha = 0.40f)
    CalendarDayStatus.LATE -> MedAlertStatusOrange.copy(alpha = 0.40f)
    CalendarDayStatus.MISSED -> MedAlertStatusRed.copy(alpha = 0.40f)
}

/**
 * Calendário com doses PREVISTAS (agenda dos medicamentos) cruzadas com os registros de
 * administração. O dia aparece mesmo sem nenhum registro; a cor mostra a situação das doses.
 */
@Composable
fun CalendarScreen(
    onNavigate: (String) -> Unit = {},
    onDayClick: (year: Int, month: Int, day: Int) -> Unit = { _, _, _ -> },
    viewModel: CalendarViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val month = state.month

    Scaffold(
        containerColor = MedAlertBackground,
        bottomBar = {
            BottomNavigationBar(selectedRoute = "calendar", onItemClick = onNavigate)
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
            Text("Calendário", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = month.labelPtBr(),
                    color = MedAlertPrimary,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { viewModel.showPreviousMonth() }) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Mês anterior", tint = MedAlertPrimary)
                }
                IconButton(onClick = { viewModel.showNextMonth() }) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Próximo mês", tint = MedAlertPrimary)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MedAlertCard)
                    .padding(12.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    weekDayLabels.forEach { label ->
                        Text(
                            text = label,
                            color = MedAlertTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                state.cells.chunked(7).forEach { week ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        week.forEach { dayUi ->
                            CalendarDayCell(
                                dayUi = dayUi,
                                onClick = {
                                    dayUi.day?.let { onDayClick(month.year, month.monthValue, it) }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            LegendRow(fill = MedAlertPrimary.copy(alpha = 0.25f), label = "Doses a tomar")
            LegendRow(fill = MedAlertStatusGreen.copy(alpha = 0.40f), label = "Todas as doses tomadas")
            LegendRow(fill = MedAlertStatusOrange.copy(alpha = 0.40f), label = "Dose atrasada ou tomada com atraso")
            LegendRow(fill = MedAlertStatusRed.copy(alpha = 0.40f), label = "Dose não tomada")
            LegendRow(fill = Color.Transparent, ring = true, label = "Hoje")

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "As doses previstas aparecem mesmo sem registro. A cor do dia mostra a situação das doses: pendente, tomada, atrasada ou não tomada. Toque em um dia para ver as doses previstas.",
                color = MedAlertTextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun CalendarDayCell(
    dayUi: CalendarDayUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.padding(3.dp),
        contentAlignment = Alignment.Center
    ) {
        if (dayUi.day == null) {
            Spacer(modifier = Modifier.size(36.dp))
            return@Box
        }

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(statusColor(dayUi.status))
                .then(
                    if (dayUi.isToday) Modifier.border(BorderStroke(1.5.dp, MedAlertPrimary), CircleShape)
                    else Modifier
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = dayUi.day.toString(),
                color = if (dayUi.hasDoses) Color.White else MedAlertTextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun LegendRow(fill: Color, label: String, ring: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MedAlertCard)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(fill)
                .then(if (ring) Modifier.border(BorderStroke(1.5.dp, MedAlertPrimary), CircleShape) else Modifier)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(label, color = Color.White, fontSize = 14.sp)
    }
}
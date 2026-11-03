package com.andre.medalert.ui.screens.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.andre.medalert.data.model.CalendarDay
import com.andre.medalert.data.schedule.DoseScheduleCalculator
import com.andre.medalert.data.schedule.labelPtBr
import com.andre.medalert.ui.components.BottomNavigationBar
import com.andre.medalert.ui.theme.MedAlertBackground
import com.andre.medalert.ui.theme.MedAlertCard
import com.andre.medalert.ui.theme.MedAlertPrimary
import com.andre.medalert.ui.theme.MedAlertTextSecondary
import com.andre.medalert.viewmodel.ScheduleViewModel
import java.time.LocalDate
import java.time.YearMonth

private val weekDayLabels = listOf("D", "S", "T", "Q", "Q", "S", "S")

/**
 * Calendário com doses PREVISTAS (calculadas dos medicamentos do Room).
 * Status por dia (tomado/atrasado/não tomado) depende de histórico por data, que a API ainda não oferece.
 */
@Composable
fun CalendarScreen(
    onNavigate: (String) -> Unit = {},
    onDayClick: (year: Int, month: Int, day: Int) -> Unit = { _, _, _ -> },
    viewModel: ScheduleViewModel = viewModel()
) {
    val medications by viewModel.medications.collectAsState()
    var visibleMonth by remember { mutableStateOf(YearMonth.now()) }
    val today = remember { LocalDate.now() }

    val cells = remember(medications, visibleMonth) {
        val daysWithDoses = DoseScheduleCalculator.dosesBetween(
            medications,
            visibleMonth.atDay(1).atStartOfDay(),
            visibleMonth.plusMonths(1).atDay(1).atStartOfDay()
        ).map { it.at.dayOfMonth }.toSet()

        buildList {
            // Domingo = coluna 0.
            repeat(visibleMonth.atDay(1).dayOfWeek.value % 7) { add(CalendarDay(day = null)) }
            for (d in 1..visibleMonth.lengthOfMonth()) {
                add(
                    CalendarDay(
                        day = d,
                        isToday = visibleMonth.atDay(d) == today,
                        hasDoses = d in daysWithDoses
                    )
                )
            }
            while (size % 7 != 0) add(CalendarDay(day = null))
        }
    }

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
                    text = visibleMonth.labelPtBr(),
                    color = MedAlertPrimary,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { visibleMonth = visibleMonth.minusMonths(1) }) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Mês anterior", tint = MedAlertPrimary)
                }
                IconButton(onClick = { visibleMonth = visibleMonth.plusMonths(1) }) {
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

                cells.chunked(7).forEach { week ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        week.forEach { dayUi ->
                            CalendarDayCell(
                                dayUi = dayUi,
                                onClick = {
                                    dayUi.day?.let { onDayClick(visibleMonth.year, visibleMonth.monthValue, it) }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            LegendRow(fill = MedAlertPrimary.copy(alpha = 0.25f), label = "Dia com doses previstas")
            LegendRow(fill = Color.Transparent, ring = true, label = "Hoje")

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Toque em um dia para ver as doses previstas. O status de cada dose (tomada, atrasada ou não tomada) aparece quando houver registro de administração.",
                color = MedAlertTextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun CalendarDayCell(
    dayUi: CalendarDay,
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
                .background(if (dayUi.hasDoses) MedAlertPrimary.copy(alpha = 0.25f) else Color.Transparent)
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
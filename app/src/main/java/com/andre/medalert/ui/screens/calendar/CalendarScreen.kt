package com.andre.medalert.ui.screens.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andre.medalert.data.mock.mockCalendarDays
import com.andre.medalert.data.mock.mockCalendarMonthLabel
import com.andre.medalert.data.model.CalendarDay
import com.andre.medalert.data.model.DayDoseStatus
import com.andre.medalert.ui.components.BottomNavigationBar
import com.andre.medalert.ui.theme.MedAlertBackground
import com.andre.medalert.ui.theme.MedAlertCard
import com.andre.medalert.ui.theme.MedAlertPrimary
import com.andre.medalert.ui.theme.MedAlertStatusGreen
import com.andre.medalert.ui.theme.MedAlertStatusOrange
import com.andre.medalert.ui.theme.MedAlertStatusRed
import com.andre.medalert.ui.theme.MedAlertTextSecondary
import androidx.compose.foundation.border

private val weekDayLabels = listOf("D", "S", "T", "Q", "Q", "S", "S")

@Composable
fun CalendarScreen(
    onNavigate: (String) -> Unit = {},
    onDayClick: (Int) -> Unit = {}
) {
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
            Text(mockCalendarMonthLabel, color = MedAlertPrimary, fontSize = 14.sp)

            Spacer(modifier = Modifier.height(20.dp))

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
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                mockCalendarDays.chunked(7).forEach { week ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        week.forEach { dayUi ->
                            CalendarDayCell(
                                dayUi = dayUi,
                                onClick = { dayUi.day?.let(onDayClick) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            LegendRow(icon = Icons.Default.CheckCircle, tint = MedAlertStatusGreen, label = "Todas as doses tomadas")
            LegendRow(icon = Icons.Default.RadioButtonUnchecked, tint = MedAlertTextSecondary, label = "Pendente / futuro")
            LegendRow(icon = Icons.Default.Cancel, tint = MedAlertStatusRed, label = "Dose não tomada")

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Dias em laranja indicam doses tomadas com atraso. Toque em um dia para ver o resumo.",
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

        val fillColor = when (dayUi.status) {
            DayDoseStatus.TAKEN -> MedAlertStatusGreen
            DayDoseStatus.LATE -> MedAlertStatusOrange
            DayDoseStatus.MISSED -> MedAlertStatusRed
            null -> Color.Transparent
        }
        val textColor = if (dayUi.status != null) Color.White else MedAlertTextSecondary

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(fillColor)
                .then(
                    if (dayUi.isToday) Modifier.border(
                        BorderStroke(1.5.dp, MedAlertPrimary),
                        CircleShape
                    ) else Modifier
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = dayUi.day.toString(),
                color = textColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun LegendRow(icon: ImageVector, tint: Color, label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MedAlertCard)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(label, color = Color.White, fontSize = 14.sp)
    }
}
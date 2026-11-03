package com.andre.medalert.ui.screens.calendar

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
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material3.Icon
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
import com.andre.medalert.data.schedule.DoseScheduleCalculator
import com.andre.medalert.data.schedule.ScheduledDose
import com.andre.medalert.data.schedule.namePtBr
import com.andre.medalert.ui.components.EmptyState
import com.andre.medalert.ui.theme.MedAlertBackground
import com.andre.medalert.ui.theme.MedAlertCard
import com.andre.medalert.ui.theme.MedAlertPrimary
import com.andre.medalert.ui.theme.MedAlertTextPrimary
import com.andre.medalert.ui.theme.MedAlertTextSecondary
import com.andre.medalert.viewmodel.ScheduleViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val HORA_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

/** Doses PREVISTAS de um dia, calculadas dos medicamentos do Room. */
@Composable
fun DayDetailsScreen(
    year: Int,
    month: Int,
    day: Int,
    onBackClick: () -> Unit = {},
    viewModel: ScheduleViewModel = viewModel()
) {
    val medications by viewModel.medications.collectAsState()
    val date = remember(year, month, day) { LocalDate.of(year, month, day) }
    val isToday = date == LocalDate.now()
    val now = remember { LocalDateTime.now() }

    val doses = remember(medications, date) { DoseScheduleCalculator.dosesOnDay(medications, date) }

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
                text = "$day de ${date.month.namePtBr()} de $year",
                color = MedAlertTextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            if (isToday) {
                Spacer(modifier = Modifier.height(2.dp))
                Text("Hoje", color = MedAlertPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (doses.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Medication,
                    title = "Nenhuma dose prevista neste dia.",
                    message = "As doses aparecem de acordo com o início, o fim e a frequência dos seus medicamentos."
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 24.dp)
                ) {
                    Text(
                        "Doses previstas",
                        color = MedAlertTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    doses.forEach { dose ->
                        DoseCard(dose = dose, isPast = dose.at.isBefore(now))
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DoseCard(dose: ScheduledDose, isPast: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MedAlertCard, shape = RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    (if (isPast) MedAlertTextSecondary else MedAlertPrimary).copy(alpha = 0.15f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Medication,
                contentDescription = null,
                tint = if (isPast) MedAlertTextSecondary else MedAlertPrimary,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(dose.medicationName, color = MedAlertTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(dose.dosage, color = MedAlertTextSecondary, fontSize = 12.sp)
        }

        Text(
            text = dose.at.format(HORA_FORMAT),
            color = if (isPast) MedAlertTextSecondary else MedAlertTextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
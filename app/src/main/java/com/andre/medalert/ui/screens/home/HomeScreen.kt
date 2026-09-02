package com.andre.medalert.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andre.medalert.data.mock.MockData
import com.andre.medalert.data.model.DoseStatus
import com.andre.medalert.data.model.MedicationDose
import com.andre.medalert.ui.components.BottomNavigationBar
import com.andre.medalert.ui.theme.MedAlertBackground
import com.andre.medalert.ui.theme.MedAlertCard
import com.andre.medalert.ui.theme.MedAlertPrimary
import com.andre.medalert.ui.theme.MedAlertTextPrimary
import com.andre.medalert.ui.theme.MedAlertTextSecondary

@Composable
fun HomeScreen(
    userName: String = "Usuário",
    medicationsToday: List<MedicationDose> = MockData.medicationsToday,
    adherencePercentage: Int = MockData.adherencePercentage,
    responsibleCount: Int = MockData.responsibleCount,
    onNavigate: (String) -> Unit = {},
    onBellClick: () -> Unit = {},
    onAdherenceClick: () -> Unit = {},
    onResponsiblesClick: () -> Unit = {}
) {
    Scaffold(
        containerColor = MedAlertBackground,
        bottomBar = {
            BottomNavigationBar(selectedRoute = "home", onItemClick = onNavigate)
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp, bottom = 24.dp)
        ) {
            HomeHeader(userName = userName, onBellClick = onBellClick)

            Spacer(modifier = Modifier.height(20.dp))

            val pendingTimes = medicationsToday
                .filter { it.status == DoseStatus.PENDING }
                .map { it.scheduledTime }
                .distinct()
                .sorted()

            if (pendingTimes.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(pendingTimes) { time ->
                        UpcomingTimeChip(time)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            Text(
                text = "Medicamentos de hoje",
                color = MedAlertTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            medicationsToday.forEach { dose ->
                MedicationDoseRow(dose)
                Spacer(modifier = Modifier.height(10.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.ShowChart,
                    value = "$adherencePercentage%",
                    label = "Adesão ao tratamento",
                    onClick = onAdherenceClick
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.People,
                    value = responsibleCount.toString(),
                    label = "Responsáveis acompanhando",
                    onClick = onResponsiblesClick
                )
            }
        }
    }
}

@Composable
private fun HomeHeader(userName: String, onBellClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column {
            Text(
                text = "Olá, $userName 👋",
                color = MedAlertTextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Seu próximo medicamento",
                color = MedAlertTextSecondary,
                fontSize = 14.sp
            )
        }

        Box(
            modifier = Modifier
                .size(44.dp)
                .background(MedAlertCard, shape = CircleShape)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onBellClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = "Notificações",
                tint = MedAlertPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun UpcomingTimeChip(time: String) {
    Box(
        modifier = Modifier
            .background(MedAlertCard, shape = RoundedCornerShape(16.dp))
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Text(
            text = time,
            color = MedAlertPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun MedicationDoseRow(dose: MedicationDose) {
    val taken = dose.status == DoseStatus.TAKEN

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MedAlertCard, shape = RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    color = if (taken) MedAlertPrimary.copy(alpha = 0.15f) else MedAlertBackground,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (taken) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = MedAlertPrimary, modifier = Modifier.size(18.dp))
            } else {
                Icon(Icons.Filled.Medication, contentDescription = null, tint = MedAlertTextSecondary, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(dose.medicationName, color = MedAlertTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)

            val subtitle = if (taken && dose.takenAt != null) {
                "${dose.dosagem()} • tomado às ${dose.takenAt}"
            } else {
                dose.dosagem()
            }

            Text(subtitle, color = MedAlertTextSecondary, fontSize = 12.sp)
        }

        Text(
            text = dose.scheduledTime,
            color = if (taken) MedAlertTextSecondary else MedAlertTextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// pequena função auxiliar só pra não repetir "dose.dosage" em dois lugares
private fun MedicationDose.dosagem(): String = this.dosage

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .background(MedAlertCard, shape = RoundedCornerShape(16.dp))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .padding(16.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MedAlertPrimary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.height(10.dp))
        Text(value, color = MedAlertTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, color = MedAlertTextSecondary, fontSize = 12.sp)
    }
}
package com.andre.medalert.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andre.medalert.ui.components.BottomNavigationBar
import com.andre.medalert.ui.components.DosyLogo
import com.andre.medalert.ui.components.NotificationPermissionDialog
import com.andre.medalert.ui.theme.MedAlertBackground
import com.andre.medalert.ui.theme.MedAlertCard
import com.andre.medalert.ui.theme.MedAlertPrimary
import com.andre.medalert.ui.theme.MedAlertStatusGreen
import com.andre.medalert.ui.theme.MedAlertTextPrimary
import com.andre.medalert.ui.theme.MedAlertTextSecondary
import com.andre.medalert.viewmodel.HomeDoseUi
import com.andre.medalert.viewmodel.HomeViewModel

/**
 * Home com dados reais: nome salvo na sessão e doses previstas calculadas a partir do Room.
 * Uma dose aparece como "Tomado" quando foi registrada pela notificação ("Tomar agora").
 * O número de responsáveis depende de um endpoint de listagem que ainda não existe no backend.
 * As permissões de notificação/alarme exato são pedidas por um popup ao entrar no app.
 */
@Composable
fun HomeScreen(
    permissionRefreshKey: Int = 0,
    onNavigate: (String) -> Unit = {},
    onBellClick: () -> Unit = {},
    onAdherenceClick: () -> Unit = {},
    onResponsiblesClick: () -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    // Popup de permissões (aparece uma vez por abertura do app, se faltar algo).
    NotificationPermissionDialog(refreshKey = permissionRefreshKey)

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
            HomeHeader(
                userName = state.userName,
                subtitle = state.nextDoseLabel,
                onBellClick = onBellClick
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (state.upcomingTimes.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.upcomingTimes) { time -> UpcomingTimeChip(time) }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            Text(
                text = "Medicamentos de hoje",
                color = MedAlertTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${state.dosesToday.size} " +
                        (if (state.dosesToday.size == 1) "dose prevista" else "doses previstas") +
                        " • ${state.medicationsToday} " +
                        (if (state.medicationsToday == 1) "medicamento" else "medicamentos"),
                color = MedAlertTextSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (state.dosesToday.isEmpty()) {
                Text(
                    text = "Nenhuma dose prevista para hoje.",
                    color = MedAlertTextSecondary,
                    fontSize = 14.sp
                )
            } else {
                state.dosesToday.forEach { dose ->
                    MedicationDoseRow(dose)
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.ShowChart,
                    value = state.adherenceLabel,
                    label = "Adesão ao tratamento",
                    onClick = onAdherenceClick
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.People,
                    value = "—",
                    label = "Responsáveis",
                    onClick = onResponsiblesClick
                )
            }
        }
    }
}

@Composable
private fun HomeHeader(userName: String?, subtitle: String, onBellClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DosyLogo(height = 18.dp)

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

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (userName.isNullOrBlank()) "Olá 👋" else "Olá, $userName 👋",
            color = MedAlertTextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            color = MedAlertTextSecondary,
            fontSize = 14.sp
        )
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
private fun MedicationDoseRow(dose: HomeDoseUi) {
    val iconColor = when {
        dose.isTaken -> MedAlertStatusGreen
        dose.isPast -> MedAlertTextSecondary
        else -> MedAlertPrimary
    }
    val iconBackground = when {
        dose.isTaken -> MedAlertStatusGreen.copy(alpha = 0.15f)
        dose.isPast -> MedAlertBackground
        else -> MedAlertPrimary.copy(alpha = 0.15f)
    }

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
                .background(color = iconBackground, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (dose.isTaken) Icons.Filled.Check else Icons.Filled.Medication,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(dose.medicationName, color = MedAlertTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(dose.dosage, color = MedAlertTextSecondary, fontSize = 12.sp)
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = dose.time,
                color = if (dose.isPast) MedAlertTextSecondary else MedAlertTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            if (dose.isTaken) {
                Text(
                    text = "Tomado",
                    color = MedAlertStatusGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
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
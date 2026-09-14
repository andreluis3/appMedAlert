package com.andre.medalert.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andre.medalert.ui.components.BottomNavigationBar
import com.andre.medalert.ui.theme.MedAlertBackground
import com.andre.medalert.ui.theme.MedAlertCard
import com.andre.medalert.ui.theme.MedAlertPrimary
import com.andre.medalert.ui.theme.MedAlertTextSecondary
import com.andre.medalert.viewmodel.SettingsViewModel

private val TextPrimary = Color.White

@Composable
fun SettingsScreen(
    onNavigate: (String) -> Unit = {},
    onProfileClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = MedAlertBackground,
        bottomBar = {
            BottomNavigationBar(
                selectedRoute = "settings",
                onItemClick = onNavigate
            )
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
            Text(
                text = "Configurações",
                color = TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Preferências do aplicativo",
                color = MedAlertPrimary,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Card de perfil
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MedAlertCard)
                    .clickable { onProfileClick() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MedAlertPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("A", color = MedAlertPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(uiState.userName, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(uiState.userEmail, color = MedAlertTextSecondary, fontSize = 13.sp)
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MedAlertTextSecondary)
            }

            SectionTitle("Notificações")

            SettingsToggleRow(
                icon = Icons.Default.Notifications,
                label = "Lembretes de medicamentos",
                checked = uiState.medicationReminders,
                onCheckedChange = { viewModel.toggleMedicationReminders() }
            )
            SettingsToggleRow(
                icon = Icons.Default.VolumeUp,
                label = "Som dos alertas",
                checked = uiState.alertSound,
                onCheckedChange = { viewModel.toggleAlertSound() }
            )

            SectionTitle("Acessibilidade")

            SettingsToggleRow(
                icon = Icons.Default.TextFields,
                label = "Texto ampliado",
                checked = uiState.largeText,
                onCheckedChange = { viewModel.toggleLargeText() }
            )
            SettingsToggleRow(
                icon = Icons.Default.DarkMode,
                label = "Tema escuro",
                checked = uiState.darkTheme,
                onCheckedChange = { viewModel.toggleDarkTheme() }
            )

            SectionTitle("Conta")

            SettingsNavigationRow(
                icon = Icons.Default.Person,
                label = "Perfil",
                onClick = onProfileClick
            )
            SettingsNavigationRow(
                icon = Icons.Default.Logout,
                label = "Sair",
                onClick = onLogoutClick,
                tint = Color(0xFFE05B5B)
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = TextPrimary,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        modifier = Modifier.padding(top = 24.dp, bottom = 10.dp)
    )
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MedAlertCard)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MedAlertPrimary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MedAlertPrimary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFF3A4248)
            )
        )
    }
}

@Composable
private fun SettingsNavigationRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = TextPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MedAlertCard)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (tint == TextPrimary) MedAlertPrimary else tint, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, color = tint, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MedAlertTextSecondary)
    }
}
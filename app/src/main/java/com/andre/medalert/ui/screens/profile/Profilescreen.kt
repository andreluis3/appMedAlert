package com.andre.medalert.ui.screens.profile

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
import com.andre.medalert.ui.theme.MedAlertBackground
import com.andre.medalert.ui.theme.MedAlertCard
import com.andre.medalert.ui.theme.MedAlertPrimary
import com.andre.medalert.ui.theme.MedAlertTextSecondary
import com.andre.medalert.viewmodel.ProfileViewModel

// Mesma abordagem do SettingsScreen: cor fixa para tema escuro atual,
// já isolada aqui para facilitar a troca quando o tema claro existir.
private val TextPrimary = Color.White

/**
 * Tela de Perfil do usuário/paciente.
 *
 * Acesso: NÃO fica na Bottom Navigation. É aberta a partir do card de
 * nome/avatar em SettingsScreen (onProfileClick), por isso não recebe
 * onNavigate/BottomNavigationBar aqui.
 */
@Composable
fun ProfileScreen(
    onBackClick: () -> Unit = {},
    onChangePasswordClick: () -> Unit = {},
    onForgotPasswordClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    viewModel: ProfileViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = MedAlertBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MedAlertBackground)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            // Cabeçalho: ← Perfil
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Voltar",
                        tint = TextPrimary
                    )
                }
                Text(
                    text = "Perfil",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Avatar + nome + email centralizados
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(MedAlertPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = MedAlertPrimary,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        color = MedAlertPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        text = uiState.nome,
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = uiState.email,
                        color = MedAlertTextSecondary,
                        fontSize = 13.sp
                    )
                }
            }

            ProfileSectionTitle("Dados pessoais")

            ProfileInfoCard {
                ProfileInfoRow(label = "Nome", value = uiState.nome)
                ProfileInfoRow(label = "Email", value = uiState.email)
                ProfileInfoRow(
                    label = "Idade do paciente",
                    // TODO: Campo aguardando definição do contrato da API (exibido, não enviado)
                    value = uiState.idade?.let { "$it anos" } ?: "-",
                    showDivider = false
                )
            }

            ProfileSectionTitle("Segurança")

            ProfileNavigationRow(
                icon = Icons.Default.Lock,
                label = "Alterar senha",
                onClick = onChangePasswordClick
            )
            ProfileNavigationRow(
                icon = Icons.Default.Email,
                label = "Esqueci minha senha",
                onClick = onForgotPasswordClick
            )

            ProfileSectionTitle("Conta")

            ProfileNavigationRow(
                icon = Icons.Default.Logout,
                label = "Sair da conta",
                onClick = {
                    viewModel.onLogout()
                    onLogoutClick()
                },
                tint = Color(0xFFE05B5B)
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ProfileSectionTitle(text: String) {
    Text(
        text = text,
        color = TextPrimary,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        modifier = Modifier.padding(top = 24.dp, bottom = 10.dp)
    )
}

@Composable
private fun ProfileInfoCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MedAlertCard)
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        content()
    }
}

@Composable
private fun ProfileInfoRow(
    label: String,
    value: String,
    showDivider: Boolean = true
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Text(text = label, color = MedAlertTextSecondary, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
    if (showDivider) {
        // Divisor manual (em vez de HorizontalDivider/Divider) para não depender
        // da versão exata do material3 usada no projeto.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MedAlertBackground)
        )
    }
}

@Composable
private fun ProfileNavigationRow(
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
        Icon(
            icon,
            contentDescription = null,
            tint = if (tint == TextPrimary) MedAlertPrimary else tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, color = tint, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MedAlertTextSecondary)
    }
}
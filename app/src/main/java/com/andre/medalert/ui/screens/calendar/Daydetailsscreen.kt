package com.andre.medalert.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andre.medalert.data.local.AppDatabase
import com.andre.medalert.data.mapper.toHistoryUiModel
import com.andre.medalert.data.mock.mockCalendarMonthLabel
import com.andre.medalert.data.mock.mockDayDetails
import com.andre.medalert.data.model.HistoryDoseStatus
import com.andre.medalert.data.model.HistoryUiModel
import com.andre.medalert.data.repository.HistoryRepository
import com.andre.medalert.ui.components.EmptyState
import com.andre.medalert.ui.components.ErrorState
import com.andre.medalert.ui.components.LoadingState
import com.andre.medalert.ui.theme.MedAlertBackground
import com.andre.medalert.ui.theme.MedAlertCard
import com.andre.medalert.ui.theme.MedAlertPrimary
import com.andre.medalert.ui.theme.MedAlertStatusGreen
import com.andre.medalert.ui.theme.MedAlertStatusOrange
import com.andre.medalert.ui.theme.MedAlertStatusRed
import com.andre.medalert.ui.theme.MedAlertTextPrimary
import com.andre.medalert.ui.theme.MedAlertTextSecondary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private sealed interface DayDetailsUiState {
    object Loading : DayDetailsUiState
    data class Error(val message: String = "Não foi possível carregar o histórico.") : DayDetailsUiState
    data class Success(val doses: List<HistoryUiModel>) : DayDetailsUiState
}

/**
 * Tela de detalhes de um dia do calendário.
 *
 * Só o dia de "hoje" usa dados reais: atualiza o Room a partir da API
 * (HistoryRepository.refresh) e lê do Room, então funciona offline com o
 * que já estava salvo. GET /api/historys ainda não aceita filtrar por data
 * — só por userId. Os outros dias usam mockDayDetails até o backend ganhar
 * esse parâmetro (ver TODO em MockDayDetailsData.kt).
 */
@Composable
fun DayDetailsScreen(
    day: Int,
    isToday: Boolean,
    monthLabel: String = mockCalendarMonthLabel,
    userId: Int = 1, // TEMPORÁRIO: trocar pelo userId do usuário logado (Etapa 3)
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current.applicationContext
    val historyRepository = remember {
        HistoryRepository(AppDatabase.getInstance(context).doseHistoryDao())
    }

    val scope = rememberCoroutineScope()
    var uiState by remember { mutableStateOf<DayDetailsUiState>(DayDetailsUiState.Loading) }

    suspend fun carregar() {
        uiState = DayDetailsUiState.Loading
        if (isToday) {
            val refreshResult = historyRepository.refresh(userId)
            val salvos = historyRepository.observeHistory(userId).first()

            uiState = if (salvos.isEmpty() && refreshResult.isFailure) {
                DayDetailsUiState.Error()
            } else {
                DayDetailsUiState.Success(salvos.map { it.toHistoryUiModel() })
            }
        } else {
            // TODO: sem parâmetro de data na API ainda - usando mock local.
            uiState = DayDetailsUiState.Success(mockDayDetails[day].orEmpty())
        }
    }

    LaunchedEffect(day, isToday) { carregar() }

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
                text = "$day de $monthLabel",
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
            when (val state = uiState) {
                is DayDetailsUiState.Loading -> LoadingState()

                is DayDetailsUiState.Error -> ErrorState(
                    title = state.message,
                    onRetryClick = { scope.launch { carregar() } }
                )

                is DayDetailsUiState.Success -> {
                    if (state.doses.isEmpty()) {
                        EmptyState(
                            icon = Icons.Filled.Medication,
                            title = "Nenhum medicamento registrado neste dia.",
                            message = ""
                        )
                    } else {
                        DayDetailsContent(doses = state.doses)
                    }
                }
            }
        }
    }
}

@Composable
private fun DayDetailsContent(doses: List<HistoryUiModel>) {
    val total = doses.size
    val tomadas = doses.count { it.status == HistoryDoseStatus.TOMADO }
    val adesao = if (total > 0) (tomadas * 100 / total) else 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
    ) {
        DaySummaryCard(adesao = adesao, tomadas = tomadas, total = total)

        Spacer(modifier = Modifier.height(24.dp))

        Text("Medicamentos", color = MedAlertTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))

        doses.forEach { dose ->
            DoseCard(dose = dose)
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun DaySummaryCard(adesao: Int, tomadas: Int, total: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MedAlertCard, shape = RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        Text("Resumo do dia", color = MedAlertTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$adesao%",
                color = MedAlertPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text("Adesão do dia", color = MedAlertTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("$tomadas de $total doses tomadas", color = MedAlertTextSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DoseCard(dose: HistoryUiModel) {
    val (statusColor, statusIcon, statusLabel) = when (dose.status) {
        HistoryDoseStatus.TOMADO -> Triple(MedAlertStatusGreen, Icons.Default.CheckCircle, "Tomado")
        HistoryDoseStatus.ATRASADO -> Triple(MedAlertStatusOrange, Icons.Default.PriorityHigh, "Atrasado")
        HistoryDoseStatus.NAO_TOMADO -> Triple(MedAlertStatusRed, Icons.Default.Cancel, "Não tomado")
        HistoryDoseStatus.PENDENTE -> Triple(MedAlertTextSecondary, Icons.Default.RadioButtonUnchecked, "Pendente")
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
                .size(40.dp)
                .background(statusColor.copy(alpha = 0.15f), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Medication, contentDescription = null, tint = statusColor, modifier = Modifier.size(18.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(dose.medicationName, color = MedAlertTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("${dose.dosage} • ${dose.scheduledTime}", color = MedAlertTextSecondary, fontSize = 12.sp)
            if (dose.takenAt != null) {
                Text("Tomado às ${dose.takenAt}", color = MedAlertTextSecondary, fontSize = 12.sp)
            }
        }

        Box(
            modifier = Modifier
                .background(statusColor.copy(alpha = 0.15f), shape = RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(statusLabel, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
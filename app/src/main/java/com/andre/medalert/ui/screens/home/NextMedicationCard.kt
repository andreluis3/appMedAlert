package com.andre.medalert.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andre.medalert.ui.theme.MedAlertBackground
import com.andre.medalert.ui.theme.MedAlertCard
import com.andre.medalert.ui.theme.MedAlertPrimary
import com.andre.medalert.ui.theme.MedAlertStatusGreen
import com.andre.medalert.ui.theme.MedAlertTextPrimary
import com.andre.medalert.ui.theme.MedAlertTextSecondary
import com.andre.medalert.viewmodel.HomeDoseUi
import kotlinx.coroutines.delay
import java.util.Locale

/** Mesmo vermelho já usado em MedicationsScreen (excluir). */
private val AlertRed = Color(0xFFE5484D)

/**
 * Card em destaque com a próxima dose. Recebe as doses de hoje (já calculadas pelo HomeViewModel)
 * e usa um relógio próprio de 1 s, que só recompõe este card (o resto da Home continua com o
 * ticker de 30 s do ViewModel).
 *
 * [canRegister]: só o PACIENTE registra dose. Para o cuidador (somente leitura) o botão
 * "Registrar dose" não aparece. Começa false de propósito: quem chama decide explicitamente.
 * O HomeViewModel.registerDose e o DoseIntakeRegistrar também recusam o cuidador.
 *
 * [caregiverView]: visão do cuidador. Ele ainda não recebe a confirmação de que a dose foi
 * tomada, então o card NÃO afirma "atrasada" nem usa vermelho: diz que o horário passou e que
 * não há confirmação de tomada neste aparelho.
 */
@Composable
fun NextMedicationCard(
    doses: List<HomeDoseUi>,
    onRegister: (HomeDoseUi) -> Unit,
    modifier: Modifier = Modifier,
    canRegister: Boolean = false,
    caregiverView: Boolean = false
) {
    val now = rememberNowMillis()

    when (val state = NextDoseSelector.select(doses, now)) {
        NextDoseCardState.NoDoses -> MessageCard(
            modifier = modifier,
            icon = Icons.Filled.Medication,
            iconColor = MedAlertPrimary,
            title = "Nenhuma dose prevista para hoje",
            subtitle = null
        )

        NextDoseCardState.AllTaken -> MessageCard(
            modifier = modifier,
            icon = Icons.Filled.Check,
            iconColor = MedAlertStatusGreen,
            title = "Tudo certo por hoje",
            subtitle = "Todas as doses de hoje foram registradas."
        )

        is NextDoseCardState.Pending -> PendingContent(
            modifier = modifier,
            state = state,
            canRegister = canRegister,
            caregiverView = caregiverView,
            onRegister = onRegister
        )
    }
}

@Composable
private fun rememberNowMillis(): Long {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000L - (now % 1_000L))
        }
    }
    return now
}

@Composable
private fun CardShell(
    modifier: Modifier,
    borderColor: Color?,
    tint: Color?,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    var m = modifier
        .fillMaxWidth()
        .background(MedAlertCard, shape)
    if (tint != null) m = m.background(tint, shape)
    if (borderColor != null) m = m.border(1.dp, borderColor, shape)
    Column(modifier = m.padding(18.dp), content = content)
}

@Composable
private fun MessageCard(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String?
) {
    CardShell(modifier = modifier, borderColor = null, tint = null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(iconColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = MedAlertTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(subtitle, color = MedAlertTextSecondary, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun PendingContent(
    modifier: Modifier,
    state: NextDoseCardState.Pending,
    canRegister: Boolean,
    caregiverView: Boolean,
    onRegister: (HomeDoseUi) -> Unit
) {
    val dose = state.dose
    val phase = state.phase
    val overdue = phase == NextDosePhase.OVERDUE
    val due = overdue || phase == NextDosePhase.TIME_TO_TAKE
    val accent = if (overdue && !caregiverView) AlertRed else MedAlertPrimary

    val borderColor = when (phase) {
        NextDosePhase.WAITING -> null
        NextDosePhase.APPROACHING -> MedAlertPrimary.copy(alpha = 0.35f)
        NextDosePhase.IMMINENT -> MedAlertPrimary.copy(alpha = 0.8f)
        NextDosePhase.TIME_TO_TAKE -> MedAlertPrimary
        NextDosePhase.OVERDUE -> if (caregiverView) null else AlertRed.copy(alpha = 0.7f)
    }
    val tint = when (phase) {
        NextDosePhase.IMMINENT, NextDosePhase.TIME_TO_TAKE -> MedAlertPrimary.copy(alpha = 0.07f)
        NextDosePhase.OVERDUE -> if (caregiverView) null else AlertRed.copy(alpha = 0.06f)
        else -> null
    }
    val chipText = when (phase) {
        NextDosePhase.WAITING -> "Hoje"
        NextDosePhase.APPROACHING, NextDosePhase.IMMINENT -> "Em breve"
        NextDosePhase.TIME_TO_TAKE -> if (caregiverView) "No horário" else "Agora"
        NextDosePhase.OVERDUE -> if (caregiverView) "Já passou" else "Atrasada"
    }

    CardShell(modifier = modifier, borderColor = borderColor, tint = tint) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AccessTime, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "PRÓXIMA MEDICAÇÃO",
                color = MedAlertTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .background(accent.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(chipText, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            when (phase) {
                NextDosePhase.WAITING, NextDosePhase.APPROACHING, NextDosePhase.IMMINENT -> {
                    Text(
                        if (caregiverView) "Próxima dose do paciente em:" else "Seu próximo medicamento será em:",
                        color = MedAlertTextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatCountdown(state.millisUntil),
                        color = if (phase == NextDosePhase.WAITING) MedAlertTextPrimary else MedAlertPrimary,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        style = TextStyle(fontFeatureSettings = "tnum")
                    )
                }

                NextDosePhase.TIME_TO_TAKE -> {
                    Text(
                        if (caregiverView) "Dose no horário" else "Hora de tomar!",
                        color = MedAlertPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Dose prevista para ${dose.time}", color = MedAlertTextSecondary, fontSize = 13.sp)
                }

                NextDosePhase.OVERDUE -> {
                    if (caregiverView) {
                        // Sem confirmação de tomada, o app não pode afirmar que a dose está atrasada.
                        Text("Horário já passou", color = MedAlertTextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "Prevista para ${dose.time} • ${formatElapsed(-state.millisUntil)}",
                            color = MedAlertTextSecondary,
                            fontSize = 13.sp
                        )
                        Text(
                            "Sem confirmação de tomada neste aparelho",
                            color = MedAlertTextSecondary,
                            fontSize = 12.sp
                        )
                    } else {
                        Text("Dose atrasada", color = AlertRed, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "Prevista para ${dose.time} • ${formatElapsed(-state.millisUntil)}",
                            color = MedAlertTextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        val details = listOf(dose.dosage, dose.quantity)
            .filter { it.isNotBlank() }
            .joinToString(" · ")

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MedAlertBackground, RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MedAlertPrimary.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Medication,
                    contentDescription = null,
                    tint = MedAlertPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(dose.medicationName, color = MedAlertTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (details.isNotBlank()) {
                    Text(details, color = MedAlertTextSecondary, fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Hoje às ${dose.time}",
                    color = MedAlertTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                if (!due) {
                    Text("Próxima dose programada", color = MedAlertTextSecondary, fontSize = 12.sp)
                }
            }

            // Só registra dose cujo horário já chegou, e só o paciente (cuidador é somente leitura).
            if (due && canRegister) {
                Button(
                    onClick = { onRegister(dose) },
                    modifier = Modifier.height(44.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MedAlertPrimary,
                        contentColor = MedAlertBackground
                    )
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Registrar dose", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** HH:mm:ss, arredondando para cima para zerar junto com o horário da dose. */
private fun formatCountdown(millis: Long): String {
    val total = (millis.coerceAtLeast(0L) + 999L) / 1_000L
    return String.format(Locale.ROOT, "%02d:%02d:%02d", total / 3_600, (total % 3_600) / 60, total % 60)
}

private fun formatElapsed(millis: Long): String {
    val minutes = (millis / 60_000L).coerceAtLeast(0L)
    return if (minutes < 60) "há $minutes min" else "há ${minutes / 60}h ${minutes % 60}min"
}
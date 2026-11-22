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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andre.medalert.ui.components.BottomNavigationBar
import com.andre.medalert.ui.components.DosyLogo
import com.andre.medalert.ui.components.CaregiverAwaitingLinkState
import com.andre.medalert.ui.components.NotificationPermissionDialog
import com.andre.medalert.ui.theme.MedAlertBackground
import com.andre.medalert.ui.theme.MedAlertCard
import com.andre.medalert.ui.theme.MedAlertPrimary
import com.andre.medalert.ui.theme.MedAlertStatusGreen
import com.andre.medalert.ui.theme.MedAlertTextPrimary
import com.andre.medalert.ui.theme.MedAlertTextSecondary
import com.andre.medalert.viewmodel.HomeDoseUi
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.andre.medalert.viewmodel.HomeViewModel

/** Quantas doses a lista compacta mostra antes do "Ver todas". */
private const val COMPACT_LIMIT = 4

/**
 * Home com dados reais: nome salvo na sessão e doses previstas calculadas a partir do Room.
 * Uma dose aparece como "Tomado" quando foi registrada pela notificação ("Tomar agora") ou pela Home.
 * O número de responsáveis depende de um endpoint de listagem que ainda não existe no backend.
 * As permissões de notificação/alarme exato são pedidas por um popup ao entrar no app.
 *
 * Registrar dose: só o PACIENTE ([HomeUiState.canRegisterDoses]). Para o cuidador (somente
 * leitura) nenhum botão "Registrar" aparece; o HomeViewModel e o DoseIntakeRegistrar também
 * recusam o registro.
 *
 * Estrutura: cabeçalho, (banner do cuidador), NextMedicationCard, resumo do dia, lista compacta
 * de próximas doses (com "Ver todas as doses de hoje") e os cards de estatística.
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
    val context = LocalContext.current
    var showAll by rememberSaveable { mutableStateOf(false) }

    // Resultado de "Registrar" (dose registrada, já registrada, erro).
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // Ao VOLTAR para a Home (do app em segundo plano ou de outra tela) o card de adesão consulta
    // de novo. A primeira entrada já é carregada pelo ViewModel, então o primeiro ON_RESUME é ignorado.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        var firstResume = true
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (firstResume) firstResume = false else viewModel.refreshAdherence()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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

            if (state.awaitingLink) {
                // Cuidador sem vínculo: explica a situação (altura fixa, pois estamos numa coluna rolável).
                Box(modifier = Modifier.fillMaxWidth().height(360.dp)) {
                    CaregiverAwaitingLinkState()
                }
                return@Column
            }

            // Cuidador vinculado: identifica o paciente acompanhado.
            if (state.linkedPatientId != null) {
                LinkedPatientBanner(patientName = state.linkedPatientName)
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Card principal: próxima dose com cronômetro.
            NextMedicationCard(
                doses = state.dosesToday,
                onRegister = { dose -> viewModel.registerDose(dose) },
                canRegister = state.canRegisterDoses,
                caregiverView = state.isCaregiver
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (state.dosesToday.isNotEmpty()) {
                TodaySummary(
                    taken = state.dosesToday.count { it.isTaken },
                    total = state.dosesToday.size,
                    medications = state.medicationsToday,
                    // Cuidador: sem confirmação de tomada, "0 de N" daria a entender que nada foi tomado.
                    showProgress = !state.isCaregiver
                )
                Spacer(modifier = Modifier.height(20.dp))

                // A dose em destaque no card não se repete na lista (granularidade do ticker de 30 s).
                val featuredKey = remember(state.dosesToday) {
                    (NextDoseSelector.select(state.dosesToday, System.currentTimeMillis())
                            as? NextDoseCardState.Pending)?.dose?.key
                }
                val upcoming = remember(state.dosesToday, featuredKey) {
                    state.dosesToday
                        .filter { !it.isTaken && it.key != featuredKey }
                        .sortedBy { it.scheduledAtMillis }
                }
                val visible = upcoming.take(COMPACT_LIMIT)

                Text(
                    text = if (showAll) "Doses de hoje" else "Próximas doses",
                    color = MedAlertTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (showAll) {
                    state.dosesToday.forEach { dose ->
                        MedicationDoseRow(
                            dose = dose,
                            canRegister = state.canRegisterDoses,
                            onRegister = { viewModel.registerDose(dose) }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                } else if (visible.isEmpty()) {
                    Text(
                        text = "Nenhuma outra dose pendente hoje.",
                        color = MedAlertTextSecondary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                } else {
                    visible.forEach { dose ->
                        CompactDoseRow(
                            dose = dose,
                            canRegister = state.canRegisterDoses,
                            onRegister = { viewModel.registerDose(dose) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                if (showAll || state.dosesToday.size > visible.size) {
                    TextButton(onClick = { showAll = !showAll }) {
                        Text(
                            text = if (showAll) "Mostrar menos"
                            else "Ver todas as doses de hoje (${state.dosesToday.size})",
                            color = MedAlertPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
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
                if (!state.isCaregiver) {
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
}

@Composable
private fun HomeHeader(
    userName: String?,
    subtitle: String,
    onBellClick: () -> Unit
) {
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

/** Cuidador vinculado: deixa claro de quem são os dados. O nome vem do GET /api/meds/cuid/. */
@Composable
private fun LinkedPatientBanner(patientName: String?) {
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
                .background(MedAlertPrimary.copy(alpha = 0.15f), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.People,
                contentDescription = null,
                tint = MedAlertPrimary,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (patientName.isNullOrBlank()) "Monitorando seu paciente"
                else "Monitorando: $patientName",
                color = MedAlertTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Vínculo ativo",
                color = MedAlertStatusGreen,
                fontSize = 12.sp
            )
        }
    }
}

/** Resumo do dia: doses registradas / previstas, com barra de progresso discreta. */
@Composable
private fun TodaySummary(taken: Int, total: Int, medications: Int, showProgress: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MedAlertCard, shape = RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Resumo de hoje",
                    color = MedAlertTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$total " + (if (total == 1) "dose prevista" else "doses previstas") +
                            " • $medications " + (if (medications == 1) "medicamento" else "medicamentos"),
                    color = MedAlertTextSecondary,
                    fontSize = 12.sp
                )
            }
            if (showProgress) {
                Text(
                    text = "$taken de $total",
                    color = MedAlertPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (showProgress) {
            Spacer(modifier = Modifier.height(12.dp))

            val fraction = if (total > 0) taken.toFloat() / total else 0f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(MedAlertBackground, RoundedCornerShape(3.dp))
            ) {
                if (fraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(6.dp)
                            .background(MedAlertPrimary, RoundedCornerShape(3.dp))
                    )
                }
            }
        }
    }
}

/**
 * Linha compacta das próximas doses. Doses que já passaram mostram o "Registrar", mas só para
 * quem pode registrar ([canRegister]: o paciente).
 */
@Composable
private fun CompactDoseRow(dose: HomeDoseUi, canRegister: Boolean, onRegister: () -> Unit) {
    val details = listOf(dose.dosage, dose.quantity)
        .filter { it.isNotBlank() }
        .joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MedAlertCard, shape = RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    if (dose.isPast) MedAlertBackground else MedAlertPrimary.copy(alpha = 0.15f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Medication,
                contentDescription = null,
                tint = if (dose.isPast) MedAlertTextSecondary else MedAlertPrimary,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                dose.medicationName,
                color = MedAlertTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            if (details.isNotBlank()) {
                Text(details, color = MedAlertTextSecondary, fontSize = 12.sp)
            }
        }

        Text(
            text = dose.time,
            color = if (dose.isPast) MedAlertTextSecondary else MedAlertTextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        if (dose.isPast && canRegister) {
            TextButton(onClick = onRegister) {
                Text(
                    text = "Registrar",
                    color = MedAlertPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/** Linha completa (a original), usada em "Ver todas as doses de hoje". */
@Composable
private fun MedicationDoseRow(dose: HomeDoseUi, canRegister: Boolean, onRegister: () -> Unit) {
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
            Text(
                dose.medicationName,
                color = MedAlertTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                dose.dosage,
                color = MedAlertTextSecondary,
                fontSize = 12.sp
            )
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
            } else if (dose.isPast && canRegister) {
                // Só o paciente registra a administração de uma dose já no horário.
                TextButton(onClick = onRegister) {
                    Text(
                        text = "Registrar",
                        color = MedAlertPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
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
        Icon(
            icon,
            contentDescription = null,
            tint = MedAlertPrimary,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            value,
            color = MedAlertTextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            label,
            color = MedAlertTextSecondary,
            fontSize = 12.sp
        )
    }
}
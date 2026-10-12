package com.andre.medalert.ui.screens.adherence

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andre.medalert.data.mock.mockDashboardResponse
import com.andre.medalert.data.remote.dto.AdesaoDiaDto
import com.andre.medalert.data.remote.dto.AdesaoRemedioDto
import com.andre.medalert.data.remote.dto.DashboardResponse
import com.andre.medalert.data.remote.dto.HorarioEsquecidoDto
import com.andre.medalert.state.AdherenceUiState
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

/**
 * Tela "Adesão ao tratamento".
 *
 * Alimentada por GET /api/historys/dashboard?id=userId (ver [DashboardResponse]).
 * A tela não sabe de onde vêm os dados: recebe apenas o [uiState].
 */
@Composable
fun AdherenceScreen(
    uiState: AdherenceUiState = AdherenceUiState.Success(mockDashboardResponse),
    onBackClick: () -> Unit = {},
    onRetryClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MedAlertBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp)
        ) {
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
                text = "Adesão ao tratamento",
                color = MedAlertTextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Veja como está sua rotina",
                color = MedAlertTextSecondary,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (uiState) {
                is AdherenceUiState.Loading -> LoadingState()

                is AdherenceUiState.Error -> ErrorState(
                    title = uiState.message,
                    onRetryClick = onRetryClick
                )

                is AdherenceUiState.Success -> {
                    if (uiState.data.dosesEsp == 0) {
                        EmptyState(
                            icon = Icons.Filled.ShowChart,
                            title = "Ainda não há dados de adesão",
                            message = "Assim que você registrar suas primeiras doses, seu acompanhamento aparece aqui."
                        )
                    } else {
                        AdherenceContent(data = uiState.data)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Conteúdo principal
// ---------------------------------------------------------------------------

@Composable
private fun AdherenceContent(data: DashboardResponse) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
    ) {
        // 1. Adesão geral -> adesao
        OverallAdherenceCard(adesao = data.adesao)

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Resumo das doses -> dosesEsp, dosesTom, dosesAtr, dosesNT
        DoseSummaryGrid(data = data)

        Spacer(modifier = Modifier.height(28.dp))

        // 3. Adesão por dia da semana -> adesaoD
        SectionTitle(title = "Adesão por dia da semana")
        Spacer(modifier = Modifier.height(12.dp))
        WeekdayAdherenceChart(items = data.adesaoD)

        Spacer(modifier = Modifier.height(28.dp))

        // 4. Adesão por medicamento -> adesaoR
        SectionTitle(title = "Adesão por medicamento")
        Spacer(modifier = Modifier.height(12.dp))
        if (data.adesaoR.isEmpty()) {
            SectionEmptyMessage("Ainda não há dados de adesão por medicamento.")
        } else {
            data.adesaoR.forEach { item ->
                MedicationAdherenceCard(item = item)
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 5. Horários com mais esquecimentos -> horariosEsq
        SectionTitle(
            title = "Horários com mais esquecimentos",
            subtitle = "Horários em que você mais esquece de tomar seus medicamentos"
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (data.horariosEsq.isEmpty()) {
            SectionEmptyMessage("Nenhum horário com esquecimentos registrado.")
        } else {
            data.horariosEsq.forEach { item ->
                ForgottenTimeCard(item = item)
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 1. Adesão geral
// ---------------------------------------------------------------------------

@Composable
private fun OverallAdherenceCard(adesao: String) {
    val fraction = percentToFraction(adesao)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MedAlertCard, shape = RoundedCornerShape(20.dp))
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(170.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 14.dp.toPx()
                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

                // trilho de fundo
                drawArc(
                    color = MedAlertBackground,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth)
                )

                // progresso
                if (fraction > 0f) {
                    drawArc(
                        color = MedAlertPrimary,
                        startAngle = -90f,
                        sweepAngle = 360f * fraction,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatPercent(adesao),
                    color = MedAlertTextPrimary,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "de adesão",
                    color = MedAlertTextSecondary,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Adesão ao tratamento",
            color = MedAlertTextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ---------------------------------------------------------------------------
// 2. Resumo das doses
// ---------------------------------------------------------------------------

@Composable
private fun DoseSummaryGrid(data: DashboardResponse) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DoseStatCard(
                modifier = Modifier.weight(1f),
                value = data.dosesEsp.toString(),
                label = "Esperadas",
                valueColor = MedAlertTextPrimary
            )
            DoseStatCard(
                modifier = Modifier.weight(1f),
                value = data.dosesTom.toString(),
                label = "Tomadas",
                valueColor = MedAlertStatusGreen
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DoseStatCard(
                modifier = Modifier.weight(1f),
                value = data.dosesAtr.toString(),
                label = "Atrasadas",
                valueColor = MedAlertStatusOrange
            )
            DoseStatCard(
                modifier = Modifier.weight(1f),
                value = data.dosesNT.toString(),
                label = "Não tomadas",
                valueColor = MedAlertStatusRed
            )
        }
    }
}

@Composable
private fun DoseStatCard(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    valueColor: Color
) {
    Column(
        modifier = modifier
            .background(MedAlertCard, shape = RoundedCornerShape(16.dp))
            .padding(vertical = 18.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            color = valueColor,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = MedAlertTextSecondary,
            fontSize = 13.sp
        )
    }
}

// ---------------------------------------------------------------------------
// 3. Gráfico de adesão por dia da semana
// ---------------------------------------------------------------------------

@Composable
private fun WeekdayAdherenceChart(items: List<AdesaoDiaDto>) {
    val chartHeight = 140.dp
    val verticalInset = 7.dp
    val axisWidth = 34.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MedAlertCard, shape = RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        if (items.isEmpty()) {
            SectionEmptyMessage("Ainda não há dados por dia da semana.")
            return@Column
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            // Eixo Y
            Column(
                modifier = Modifier
                    .width(axisWidth)
                    .height(chartHeight),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("100%", "50%", "0%").forEach { label ->
                    Text(text = label, color = MedAlertTextSecondary, fontSize = 10.sp)
                }
            }

            // Linhas de grade + linha do gráfico
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(chartHeight)
            ) {
                val inset = verticalInset.toPx()
                val top = inset
                val bottom = size.height - inset
                val plotHeight = bottom - top

                val gridColor = MedAlertTextSecondary.copy(alpha = 0.2f)
                listOf(0f, 0.5f, 1f).forEach { f ->
                    val y = top + plotHeight * f
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                val count = items.size
                val points = items.mapIndexed { index, item ->
                    val x = size.width * (index + 0.5f) / count
                    val y = bottom - plotHeight * percentToFraction(item.adesao)
                    Offset(x, y)
                }

                val path = Path().apply {
                    points.forEachIndexed { index, point ->
                        if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                    }
                }

                drawPath(
                    path = path,
                    color = MedAlertPrimary,
                    style = Stroke(
                        width = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                points.forEach { point ->
                    drawCircle(color = MedAlertCard, radius = 6.dp.toPx(), center = point)
                    drawCircle(color = MedAlertPrimary, radius = 4.dp.toPx(), center = point)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Rótulos do eixo X (dia + valor), alinhados aos pontos do gráfico
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.width(axisWidth))
            Row(modifier = Modifier.weight(1f)) {
                items.forEach { item ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = abbreviateWeekday(item.diaSem),
                            color = MedAlertTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = formatPercent(item.adesao),
                            color = MedAlertTextSecondary,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 4. Adesão por medicamento
// ---------------------------------------------------------------------------

@Composable
private fun MedicationAdherenceCard(item: AdesaoRemedioDto) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MedAlertCard, shape = RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.remedio,
                    color = MedAlertTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${item.dosesTom} de ${item.dosesEsp} doses tomadas",
                    color = MedAlertTextSecondary,
                    fontSize = 12.sp
                )
            }
            Text(
                text = formatPercent(item.adesao),
                color = MedAlertPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        AdherenceProgressBar(fraction = percentToFraction(item.adesao))
    }
}

// ---------------------------------------------------------------------------
// 5. Horários com mais esquecimentos
// ---------------------------------------------------------------------------

@Composable
private fun ForgottenTimeCard(item: HorarioEsquecidoDto) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MedAlertCard, shape = RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MedAlertStatusRed.copy(alpha = 0.15f), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Schedule,
                    contentDescription = null,
                    tint = MedAlertStatusRed,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.horario,
                    color = MedAlertTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = forgottenDosesLabel(item.qtd),
                    color = MedAlertTextSecondary,
                    fontSize = 12.sp
                )
            }

            Text(
                text = formatPercent(item.porcentagem),
                color = MedAlertStatusRed,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        AdherenceProgressBar(
            fraction = percentToFraction(item.porcentagem),
            color = MedAlertStatusRed
        )
    }
}

// ---------------------------------------------------------------------------
// Componentes e funções auxiliares
// ---------------------------------------------------------------------------

@Composable
private fun SectionTitle(title: String, subtitle: String? = null) {
    Column {
        Text(
            text = title,
            color = MedAlertTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = MedAlertTextSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun SectionEmptyMessage(text: String) {
    Text(
        text = text,
        color = MedAlertTextSecondary,
        fontSize = 13.sp
    )
}

@Composable
private fun AdherenceProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = MedAlertPrimary
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MedAlertBackground)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .background(color)
        )
    }
}

/**
 * Formata o percentual que veio da API apenas para exibição:
 * "23.6" -> "23,6%", "50.0" -> "50%", "22" -> "22%".
 * Não recalcula nada.
 */
private fun formatPercent(raw: String): String {
    val clean = raw.trim().removeSuffix("%")
    val withoutZeroDecimal = clean.removeSuffix(".0")
    return withoutZeroDecimal.replace('.', ',') + "%"
}

/** Converte o percentual da API (String) em 0f..1f só para desenhar barras/arcos. */
private fun percentToFraction(raw: String): Float {
    val value = raw.trim().removeSuffix("%").toFloatOrNull() ?: 0f
    return (value / 100f).coerceIn(0f, 1f)
}

/** "segunda-feira" -> "Seg", "sábado" -> "Sáb". */
private fun abbreviateWeekday(diaSem: String): String =
    diaSem.trim().take(3).replaceFirstChar { it.uppercase() }

private fun forgottenDosesLabel(qtd: Int): String =
    if (qtd == 1) "1 dose esquecida" else "$qtd doses esquecidas"
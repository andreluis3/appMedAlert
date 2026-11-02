package com.andre.medalert.ui.screens.meds

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andre.medalert.ui.theme.MedAlertCard
import com.andre.medalert.ui.theme.MedAlertPrimary
import com.andre.medalert.ui.theme.MedAlertTextPrimary
import com.andre.medalert.ui.theme.MedAlertTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// ---------------------------------------------------------------------------
// Componentes e constantes COMPARTILHADOS entre CadastroMedScreen e EditarMedicamentoScreen.
// Foram movidos sem alteração de visual ou comportamento (só ganharam o parâmetro opcional
// "enabled", que por padrão é true e mantém o cadastro exatamente como era).
// ---------------------------------------------------------------------------

internal val horariosRapidos = listOf("06:00", "08:00", "12:00", "14:00", "18:00", "20:00", "22:00")
internal val frequenciasHoras = listOf(4, 6, 8, 12, 24)
internal val unidadesDosagem = listOf("mg", "ml")
internal val unidadesQuantidade = listOf("comprimido", "cápsula", "gotas", "ml")

internal enum class DataPicker { INICIO, FIM }

@Composable
internal fun ChipGrid(
    options: List<String>,
    selected: String,
    columns: Int,
    onSelect: (String) -> Unit,
    verticalPadding: Dp = 16.dp,
    fontSize: TextUnit = 13.sp,
    enabled: Boolean = true
) {
    options.chunked(columns).forEach { linha ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            linha.forEach { option ->
                SelectChip(
                    modifier = Modifier.weight(1f),
                    label = option,
                    selected = option == selected,
                    verticalPadding = verticalPadding,
                    fontSize = fontSize,
                    enabled = enabled,
                    onClick = { onSelect(option) }
                )
            }
            // preenche espaço vazio se a última linha tiver menos itens
            repeat(columns - linha.size) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
internal fun SelectChip(
    modifier: Modifier = Modifier,
    label: String,
    selected: Boolean,
    verticalPadding: Dp,
    fontSize: TextUnit,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .background(
                color = if (selected) MedAlertPrimary.copy(alpha = 0.18f) else MedAlertCard,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .padding(vertical = verticalPadding, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = (if (selected) MedAlertPrimary else MedAlertTextPrimary)
                .copy(alpha = if (enabled) 1f else 0.5f),
            fontSize = fontSize,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
internal fun PickerField(
    text: String,
    isPlaceholder: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MedAlertCard, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Text(
            text = text,
            color = if (isPlaceholder) MedAlertTextSecondary else MedAlertTextPrimary,
            fontSize = 14.sp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DatePickerSimples(
    initialIso: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialIso?.let(::isoParaMillis)
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = { state.selectedDateMillis?.let { onConfirm(millisParaIso(it)) } }
            ) { Text("Confirmar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    ) {
        DatePicker(state = state)
    }
}

// ---------- Datas (sem java.time, funciona em qualquer minSdk) ----------

private fun formatoIso() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

/** O DatePicker devolve milissegundos em UTC à meia-noite do dia escolhido. */
internal fun millisParaIso(millis: Long): String = formatoIso().format(Date(millis))

internal fun isoParaMillis(iso: String): Long? = formatoIso().parse(iso)?.time

internal fun isoParaBr(iso: String): String {
    val partes = iso.split("-")
    return if (partes.size == 3) "${partes[2]}/${partes[1]}/${partes[0]}" else iso
}

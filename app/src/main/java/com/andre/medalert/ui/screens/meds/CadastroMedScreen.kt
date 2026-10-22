package com.andre.medalert.ui.screens.meds

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andre.medalert.data.remote.dto.MedicamentoData
import com.andre.medalert.ui.components.DosyLogo
import com.andre.medalert.ui.screens.login.FieldLabel
import com.andre.medalert.ui.screens.login.MedAlertTextField
import com.andre.medalert.ui.theme.MedAlertBackground
import com.andre.medalert.ui.theme.MedAlertCard
import com.andre.medalert.ui.theme.MedAlertPrimary
import com.andre.medalert.ui.theme.MedAlertTextPrimary
import com.andre.medalert.ui.theme.MedAlertTextSecondary
import com.andre.medalert.ui.components.CustomTimePickerDialog
import com.andre.medalert.viewmodel.MedUiState
import com.andre.medalert.viewmodel.MedViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val horariosRapidos = listOf("06:00", "08:00", "12:00", "14:00", "18:00", "20:00", "22:00")
private val frequenciasHoras = listOf(4, 6, 8, 12, 24)
private val unidadesDosagem = listOf("mg", "ml")
private val unidadesQuantidade = listOf("comprimido", "cápsula", "gotas", "ml")

private enum class DataPicker { INICIO, FIM }

// ---------------------------------------------------------------------------
// Conversão do reloginho para o formato da API
// ---------------------------------------------------------------------------


/**
 * Junta a data escolhida ("yyyy-MM-dd") com o horário do reloginho ("HH:mm")
 * no formato que o backend exige: "yyyy-MM-dd HH:mm:ss-03:00".
 * Ex.: ("2026-10-17", "08:00") -> "2026-10-17 08:00:00-03:00"
 */
/**
 * Junta a data ("yyyy-MM-dd") com o horário ("HH:mm") no formato da API:
 * "yyyy-MM-dd HH:mm:ss-03:00". Ex.: ("2026-10-17", "08:00") -> "2026-10-17 08:00:00-03:00"
 */
fun montarDataHoraApi(dataIso: String, horario: String): String =
    "$dataIso $horario:00-03:00"

// ---------------------------------------------------------------------------
// Tela
// ---------------------------------------------------------------------------

@Composable
fun CadastroMedScreen(
    onSuccess: () -> Unit,
    onBackClick: () -> Unit,
    viewModel: MedViewModel = viewModel(factory = MedViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()
    val state = uiState
    val isLoading = state is MedUiState.Loading
    val context = LocalContext.current

    var nome by remember { mutableStateOf("") }
    var dosagem by remember { mutableStateOf("") }
    var quantidade by remember { mutableStateOf("") }
    var unidadeDosagem by remember { mutableStateOf(unidadesDosagem.first()) }
    var unidadeQuantidade by remember { mutableStateOf(unidadesQuantidade.first()) }

    // Horário da primeira dose (único) + horários criados pelo reloginho
    var horarioSelecionado by remember { mutableStateOf("08:00") }
    var horariosPersonalizados by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showTimePicker by remember { mutableStateOf(false) }

    // Intervalo entre doses, em horas
    var frequenciaHoras by remember { mutableStateOf(8) }

    // Datas em ISO "yyyy-MM-dd" (null = ainda não escolhida)
    var dataInicio by remember { mutableStateOf<String?>(null) }
    var dataFim by remember { mutableStateOf<String?>(null) }
    var dataPickerAberto by remember { mutableStateOf<DataPicker?>(null) }

    var validationError by remember { mutableStateOf<String?>(null) }

    val todosHorarios = (horariosRapidos + horariosPersonalizados).distinct().sorted()

    // Sucesso: avisa se foi offline, limpa o estado e navega.
    LaunchedEffect(state) {
        if (state is MedUiState.Success) {
            if (state.savedOffline) {
                Toast.makeText(
                    context,
                    "Sem internet: medicamento salvo no celular e será enviado depois.",
                    Toast.LENGTH_LONG
                ).show()
            }
            viewModel.resetState()
            onSuccess()
        }
    }

    fun salvar() {
        val dosagemInt = dosagem.toIntOrNull()
        val quantidadeInt = quantidade.toIntOrNull()
        val inicio = dataInicio
        val fim = dataFim

        val erro = when {
            nome.isBlank() -> "Informe o nome do medicamento."
            dosagemInt == null || dosagemInt <= 0 -> "Informe uma dosagem válida."
            quantidadeInt == null || quantidadeInt <= 0 -> "Informe a quantidade."
            inicio == null -> "Escolha a data de início."
            fim == null -> "Escolha a data de fim."
            fim < inicio -> "A data de fim não pode ser antes da data de início."
            else -> null
        }
        validationError = erro
        if (erro != null || dosagemInt == null || quantidadeInt == null ||
            inicio == null || fim == null
        ) return

        viewModel.saveMedication(
            MedicamentoData(
                nome = nome.trim(),
                dataInicio = montarDataHoraApi(inicio, horarioSelecionado),
                dataFim = montarDataHoraApi(fim, horarioSelecionado),
                dosagem = dosagemInt,
                frequencia = frequenciaHoras,
                quantidade = quantidadeInt,
                unidadeDosagem = unidadeDosagem,
                unidadeQuantidade = unidadeQuantidade,
                userId = 0 // ignorado: o repositório usa o userId real do DataStore
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MedAlertBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 32.dp)
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

        Spacer(modifier = Modifier.height(8.dp))

        DosyLogo(withBackground = false)

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Novo medicamento",
            color = MedAlertTextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text("Preencha as informações", color = MedAlertTextSecondary, fontSize = 14.sp)

        Spacer(modifier = Modifier.height(24.dp))

        FieldLabel("Nome do medicamento")
        MedAlertTextField(
            value = nome,
            onValueChange = { nome = it },
            placeholder = "Ex.: Paracetamol"
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                FieldLabel("Dosagem")
                MedAlertTextField(
                    value = dosagem,
                    onValueChange = { dosagem = it.filter(Char::isDigit) },
                    placeholder = "500",
                    keyboardType = KeyboardType.Number
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                FieldLabel("Quantidade")
                MedAlertTextField(
                    value = quantidade,
                    onValueChange = { quantidade = it.filter(Char::isDigit) },
                    placeholder = "20",
                    keyboardType = KeyboardType.Number
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        FieldLabel("Unidade da dosagem")
        ChipGrid(
            options = unidadesDosagem,
            selected = unidadeDosagem,
            columns = 2,
            onSelect = { unidadeDosagem = it }
        )

        FieldLabel("Unidade da quantidade")
        ChipGrid(
            options = unidadesQuantidade,
            selected = unidadeQuantidade,
            columns = 2,
            onSelect = { unidadeQuantidade = it }
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text("Horário da primeira dose", color = MedAlertTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(10.dp))

        ChipGrid(
            options = todosHorarios,
            selected = horarioSelecionado,
            columns = 3,
            onSelect = { horarioSelecionado = it },
            verticalPadding = 14.dp,
            fontSize = 14.sp
        )

        OutlinedButton(
            onClick = { showTimePicker = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MedAlertPrimary)
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Horário personalizado", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            "Toque para escolher o horário da primeira dose.",
            color = MedAlertTextSecondary,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text("Frequência", color = MedAlertTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(10.dp))

        ChipGrid(
            options = frequenciasHoras.map { "A cada ${it}h" },
            selected = "A cada ${frequenciaHoras}h",
            columns = 2,
            onSelect = { label -> frequenciaHoras = label.filter(Char::isDigit).toInt() }
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                FieldLabel("Data início")
                PickerField(
                    text = dataInicio?.let(::isoParaBr) ?: "Escolher",
                    isPlaceholder = dataInicio == null,
                    onClick = { dataPickerAberto = DataPicker.INICIO }
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                FieldLabel("Data fim")
                PickerField(
                    text = dataFim?.let(::isoParaBr) ?: "Escolher",
                    isPlaceholder = dataFim == null,
                    onClick = { dataPickerAberto = DataPicker.FIM }
                )
            }
        }

        validationError?.let { msg ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(msg, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = { salvar() },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MedAlertPrimary,
                contentColor = MedAlertBackground,
                disabledContainerColor = MedAlertPrimary,
                disabledContentColor = MedAlertBackground
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = MedAlertBackground,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Salvar medicamento", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    // ---- Diálogos ----

    if (showTimePicker) {
        CustomTimePickerDialog(
            onDismiss = { showTimePicker = false },
            onConfirm = { horarioEscolhido ->
                horariosPersonalizados = horariosPersonalizados + horarioEscolhido
                horarioSelecionado = horarioEscolhido
                showTimePicker = false
            }
        )
    }

    when (dataPickerAberto) {
        DataPicker.INICIO -> DatePickerSimples(
            initialIso = dataInicio,
            onDismiss = { dataPickerAberto = null },
            onConfirm = {
                dataInicio = it
                dataPickerAberto = null
            }
        )

        DataPicker.FIM -> DatePickerSimples(
            initialIso = dataFim,
            onDismiss = { dataPickerAberto = null },
            onConfirm = {
                dataFim = it
                dataPickerAberto = null
            }
        )

        null -> Unit
    }

    if (state is MedUiState.Error) {
        AlertDialog(
            onDismissRequest = viewModel::resetState,
            containerColor = MedAlertCard,
            titleContentColor = MedAlertTextPrimary,
            textContentColor = MedAlertTextSecondary,
            title = { Text("Não foi possível salvar") },
            text = { Text(state.message) },
            confirmButton = {
                TextButton(onClick = viewModel::resetState) {
                    Text("OK", color = MedAlertPrimary)
                }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Componentes (mesmo visual da AddMedicationScreen)
// ---------------------------------------------------------------------------

@Composable
private fun ChipGrid(
    options: List<String>,
    selected: String,
    columns: Int,
    onSelect: (String) -> Unit,
    verticalPadding: Dp = 16.dp,
    fontSize: TextUnit = 13.sp
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
private fun SelectChip(
    modifier: Modifier = Modifier,
    label: String,
    selected: Boolean,
    verticalPadding: Dp,
    fontSize: TextUnit,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .background(
                color = if (selected) MedAlertPrimary.copy(alpha = 0.18f) else MedAlertCard,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .padding(vertical = verticalPadding, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) MedAlertPrimary else MedAlertTextPrimary,
            fontSize = fontSize,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun PickerField(
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
private fun DatePickerSimples(
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
private fun millisParaIso(millis: Long): String = formatoIso().format(Date(millis))

private fun isoParaMillis(iso: String): Long? = formatoIso().parse(iso)?.time

private fun isoParaBr(iso: String): String {
    val partes = iso.split("-")
    return if (partes.size == 3) "${partes[2]}/${partes[1]}/${partes[0]}" else iso
}
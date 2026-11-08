package com.andre.medalert.ui.screens.meds

import android.widget.Toast
import androidx.compose.foundation.background
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
import com.andre.medalert.sync.MedicationSyncWorker
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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

// As listas de opções (horariosRapidos, frequenciasHoras, unidadesDosagem, unidadesQuantidade),
// o enum DataPicker e os componentes ChipGrid, SelectChip, PickerField, DatePickerSimples e
// os helpers de data agora ficam em MedicationFormComponents.kt (compartilhados com a edição).
// A entrada decimal da dosagem (filtrarDecimal / parseDecimalOrNull) fica em DecimalInput.kt.

// ---------------------------------------------------------------------------
// Conversão do reloginho para o formato da API
// ---------------------------------------------------------------------------

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
                // Fica na fila do WorkManager até a conexão voltar; depois as doses que
                // esperavam o ID remoto do medicamento também seguem.
                MedicationSyncWorker.enqueueFollowUp(context)
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
        // Dosagem decimal (aceita "0,5" e "2.5"): NUNCA converter para Int.
        val dosagemValor = parseDecimalOrNull(dosagem)
        val quantidadeInt = quantidade.toIntOrNull()
        val inicio = dataInicio
        val fim = dataFim

        val erro = when {
            nome.isBlank() -> "Informe o nome do medicamento."
            dosagemValor == null || dosagemValor <= 0.0 -> "Informe uma dosagem válida."
            quantidadeInt == null || quantidadeInt <= 0 -> "Informe a quantidade."
            inicio == null -> "Escolha a data de início."
            fim == null -> "Escolha a data de fim."
            fim < inicio -> "A data de fim não pode ser antes da data de início."
            else -> null
        }
        validationError = erro
        if (erro != null || dosagemValor == null || quantidadeInt == null ||
            inicio == null || fim == null
        ) return

        viewModel.saveMedication(
            MedicamentoData(
                nome = nome.trim(),
                dataInicio = montarDataHoraApi(inicio, horarioSelecionado),
                dataFim = montarDataHoraApi(fim, horarioSelecionado),
                dosagem = dosagemValor,
                frequencia = frequenciaHoras,
                quantidade = quantidadeInt,
                unidadeDosagem = unidadeDosagem,
                unidadeQuantidade = unidadeQuantidade,
                userId = 0, // ignorado: o repositório usa o userId real do DataStore
                horarioDose = horarioSelecionado // "HH:mm": o repositório exige e envia à API
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
                    onValueChange = { dosagem = filtrarDecimal(it) },
                    placeholder = "Ex.: 0,5",
                    keyboardType = KeyboardType.Decimal
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                FieldLabel("Quantidade")
                MedAlertTextField(
                    value = quantidade,
                    onValueChange = { quantidade = it.filter(Char::isDigit) },
                    placeholder = "",
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
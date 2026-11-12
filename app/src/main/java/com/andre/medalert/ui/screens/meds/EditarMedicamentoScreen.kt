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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andre.medalert.ui.components.DosyLogo
import com.andre.medalert.ui.screens.login.FieldLabel
import com.andre.medalert.ui.screens.login.MedAlertTextField
import com.andre.medalert.ui.theme.MedAlertBackground
import com.andre.medalert.ui.theme.MedAlertCard
import com.andre.medalert.ui.theme.MedAlertPrimary
import com.andre.medalert.ui.theme.MedAlertTextPrimary
import com.andre.medalert.ui.theme.MedAlertTextSecondary
import com.andre.medalert.viewmodel.EditLoadState
import com.andre.medalert.viewmodel.EditMedicationViewModel
import com.andre.medalert.viewmodel.EditSaveState

/**
 * Tela de edição: MESMO layout, componentes e identidade visual da CadastroMedScreen, mas abre
 * com os dados atuais do medicamento ([localId] = id da linha no Room) e salva com
 * "Salvar alterações". As duas telas são independentes.
 *
 * Diferença honesta: o "Horário da primeira dose" aparece (mesmo visual) porém desabilitado,
 * porque o servidor guarda só a DATA de início e não tem como receber o horário.
 */
@Composable
fun EditarMedicamentoScreen(
    localId: Long,
    onSuccess: () -> Unit,
    onBackClick: () -> Unit,
    viewModel: EditMedicationViewModel = viewModel(
        key = "edit-medication-$localId",
        factory = EditMedicationViewModel.factory(localId)
    )
) {
    val loadState by viewModel.loadState.collectAsState()
    val form by viewModel.form.collectAsState()
    val saveState by viewModel.saveState.collectAsState()
    val validationError by viewModel.validationError.collectAsState()
    val context = LocalContext.current

    var dataPickerAberto by remember { mutableStateOf<DataPicker?>(null) }

    val isSaving = saveState is EditSaveState.Saving

    // Sucesso: avisa, limpa o estado e volta para a lista (que observa o Room e já está atualizada).
    LaunchedEffect(saveState) {
        if (saveState is EditSaveState.Success) {
            Toast.makeText(context, "Medicamento atualizado.", Toast.LENGTH_SHORT).show()
            viewModel.consumeSuccess()
            onSuccess()
        }
    }

    when (val load = loadState) {
        EditLoadState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MedAlertBackground),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MedAlertPrimary)
            }
        }

        is EditLoadState.Unavailable -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MedAlertBackground)
                    .padding(horizontal = 20.dp)
                    .padding(top = 20.dp, bottom = 32.dp)
            ) {
                VoltarButton(onBackClick)
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Não é possível editar agora",
                    color = MedAlertTextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(load.message, color = MedAlertTextSecondary, fontSize = 14.sp)
            }
        }

        is EditLoadState.Ready -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MedAlertBackground)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 20.dp, bottom = 32.dp)
            ) {
                VoltarButton(onBackClick)

                Spacer(modifier = Modifier.height(8.dp))

                DosyLogo(withBackground = false)

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Editar medicamento",
                    color = MedAlertTextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("Altere as informações", color = MedAlertTextSecondary, fontSize = 14.sp)

                Spacer(modifier = Modifier.height(24.dp))

                FieldLabel("Nome do medicamento")
                MedAlertTextField(
                    value = form.nome,
                    onValueChange = { v -> viewModel.update { it.copy(nome = v) } },
                    placeholder = "Ex.: Paracetamol"
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        FieldLabel("Dosagem")
                        MedAlertTextField(
                            value = form.dosagem,
                            onValueChange = { v ->
                                // Mesmo filtro do cadastro: aceita 0,5 / 2.5 sem perder os decimais.
                                viewModel.update { it.copy(dosagem = filtrarDecimal(v)) }
                            },
                            placeholder = "",
                            keyboardType = KeyboardType.Decimal
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        FieldLabel("Quantidade")
                        MedAlertTextField(
                            value = form.quantidade,
                            onValueChange = { v ->
                                viewModel.update { it.copy(quantidade = v.filter(Char::isDigit)) }
                            },
                            placeholder = "",
                            keyboardType = KeyboardType.Number
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                FieldLabel("Unidade da dosagem")
                ChipGrid(
                    options = (unidadesDosagem + form.unidadeDosagem).filter { it.isNotBlank() }.distinct(),
                    selected = form.unidadeDosagem,
                    columns = 2,
                    onSelect = { v -> viewModel.update { it.copy(unidadeDosagem = v) } }
                )

                FieldLabel("Unidade da quantidade")
                ChipGrid(
                    options = (unidadesQuantidade + form.unidadeQuantidade).filter { it.isNotBlank() }.distinct(),
                    selected = form.unidadeQuantidade,
                    columns = 2,
                    onSelect = { v -> viewModel.update { it.copy(unidadeQuantidade = v) } }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Horário da primeira dose: mesmo visual do cadastro, somente leitura.
                Text("Horário da primeira dose", color = MedAlertTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))

                ChipGrid(
                    options = (horariosRapidos + listOfNotNull(load.primeiraDose)).distinct().sorted(),
                    selected = load.primeiraDose.orEmpty(),
                    columns = 3,
                    onSelect = {},
                    verticalPadding = 14.dp,
                    fontSize = 14.sp,
                    enabled = false
                )

                OutlinedButton(
                    onClick = {},
                    enabled = false,
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
                    "O horário da primeira dose ainda não pode ser alterado: o servidor guarda só a data de início.",
                    color = MedAlertTextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text("Frequência", color = MedAlertTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))

                val opcoesFrequencia = (frequenciasHoras + listOfNotNull(form.frequenciaHoras)).distinct().sorted()
                ChipGrid(
                    options = opcoesFrequencia.map { "A cada ${it}h" },
                    selected = form.frequenciaHoras?.let { "A cada ${it}h" }.orEmpty(),
                    columns = 2,
                    onSelect = { label ->
                        val horas = label.filter(Char::isDigit).toInt()
                        viewModel.update { it.copy(frequenciaHoras = horas) }
                    }
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        FieldLabel("Data início")
                        PickerField(
                            text = form.dataInicio?.let(::isoParaBr) ?: "Escolher",
                            isPlaceholder = form.dataInicio == null,
                            onClick = { dataPickerAberto = DataPicker.INICIO }
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        FieldLabel("Data fim")
                        PickerField(
                            text = form.dataFim?.let(::isoParaBr) ?: "Escolher",
                            isPlaceholder = form.dataFim == null,
                            onClick = { dataPickerAberto = DataPicker.FIM }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                FieldLabel("Observações")
                MedAlertTextField(
                    value = form.observacoes,
                    onValueChange = { v -> viewModel.update { it.copy(observacoes = v) } },
                    placeholder = "Ex.: tomar após as refeições"
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MedAlertCard, RoundedCornerShape(14.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Medicamento ativo",
                        color = MedAlertTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = form.ativo,
                        onCheckedChange = { v -> viewModel.update { it.copy(ativo = v) } },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MedAlertPrimary,
                            checkedThumbColor = MedAlertBackground
                        )
                    )
                }

                validationError?.let { msg ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(msg, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = { viewModel.save() },
                    enabled = !isSaving,
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
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MedAlertBackground,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Salvar alterações", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ---- Diálogos ----

            when (dataPickerAberto) {
                DataPicker.INICIO -> DatePickerSimples(
                    initialIso = form.dataInicio,
                    onDismiss = { dataPickerAberto = null },
                    onConfirm = { v ->
                        viewModel.update { it.copy(dataInicio = v) }
                        dataPickerAberto = null
                    }
                )

                DataPicker.FIM -> DatePickerSimples(
                    initialIso = form.dataFim,
                    onDismiss = { dataPickerAberto = null },
                    onConfirm = { v ->
                        viewModel.update { it.copy(dataFim = v) }
                        dataPickerAberto = null
                    }
                )

                null -> Unit
            }

            // Erro da API: o formulário continua preenchido para o usuário tentar de novo.
            (saveState as? EditSaveState.Error)?.let { erro ->
                AlertDialog(
                    onDismissRequest = viewModel::dismissError,
                    containerColor = MedAlertCard,
                    titleContentColor = MedAlertTextPrimary,
                    textContentColor = MedAlertTextSecondary,
                    title = { Text("Não foi possível salvar") },
                    text = { Text(erro.message) },
                    confirmButton = {
                        TextButton(onClick = viewModel::dismissError) {
                            Text("OK", color = MedAlertPrimary)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun VoltarButton(onBackClick: () -> Unit) {
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
}

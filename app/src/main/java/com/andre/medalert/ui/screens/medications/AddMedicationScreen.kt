package com.andre.medalert.ui.screens.medications

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.andre.medalert.data.model.Medication
import com.andre.medalert.ui.screens.login.FieldLabel
import com.andre.medalert.ui.screens.login.MedAlertTextField
import com.andre.medalert.ui.theme.MedAlertBackground
import com.andre.medalert.ui.theme.MedAlertCard
import com.andre.medalert.ui.theme.MedAlertPrimary
import com.andre.medalert.ui.theme.MedAlertTextPrimary
import com.andre.medalert.ui.theme.MedAlertTextSecondary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember


private val horariosRapidos = listOf("06:00", "08:00", "12:00", "14:00", "18:00", "20:00", "22:00")
private val frequencias = listOf("Todos os dias", "Dias alternados", "Semanal", "Quando necessário")

/**
 * Tela de formulário de medicamento.
 *
 * Recebe um "medication" opcional: se vier null, é modo "Adicionar";
 * se vier preenchido, os campos já nascem preenchidos (modo "Editar",
 * que vamos ligar numa etapa futura reaproveitando esta mesma tela).
 */
@Composable
fun AddMedicationScreen(
    medication: Medication? = null,
    onBackClick: () -> Unit = {},
    onSaveClick: (Medication) -> Unit = {}
) {
    var nome by remember { mutableStateOf(medication?.name ?: "") }
    var dosagem by remember { mutableStateOf(medication?.dosage ?: "") }
    var quantidade by remember { mutableStateOf(medication?.quantity ?: "1 comprimido") }
    var observacoes by remember { mutableStateOf(medication?.notes ?: "") }
    var frequenciaSelecionada by remember { mutableStateOf(medication?.frequency ?: frequencias.first()) }

    // Horários "customizados" adicionados pelo usuário via seletor tipo alarme
    var horariosPersonalizados by remember {
        mutableStateOf(
            (medication?.schedules ?: emptyList()).filter { it !in horariosRapidos }.toSet()
        )
    }

    // Todos os horários atualmente marcados como selecionados (rápidos + personalizados)
    var horariosSelecionados by remember {
        mutableStateOf((medication?.schedules ?: emptyList()).toSet())
    }

    var showTimePicker by remember { mutableStateOf(false) }

    val todosHorarios = (horariosRapidos + horariosPersonalizados).distinct().sorted()

    val isEditMode = medication != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MedAlertBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 32.dp)
    ) {
        TextButton(onClick = onBackClick, contentPadding = PaddingValues(0.dp)) {
            Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = MedAlertTextSecondary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Voltar", color = MedAlertTextSecondary, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (isEditMode) "Editar medicamento" else "Novo medicamento",
            color = MedAlertTextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text("Preencha as informações", color = MedAlertTextSecondary, fontSize = 14.sp)

        Spacer(modifier = Modifier.height(24.dp))

        FieldLabel("Nome do medicamento")
        MedAlertTextField(value = nome, onValueChange = { nome = it }, placeholder = "Ex.: Losartana")

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                FieldLabel("Dosagem")
                MedAlertTextField(value = dosagem, onValueChange = { dosagem = it }, placeholder = "50 mg")
            }
            Column(modifier = Modifier.weight(1f)) {
                FieldLabel("Quantidade")
                MedAlertTextField(value = quantidade, onValueChange = { quantidade = it }, placeholder = "1 comprimido")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text("Horários", color = MedAlertTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(10.dp))

        todosHorarios.chunked(3).forEach { linha ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                linha.forEach { horario ->
                    TimeChip(
                        modifier = Modifier.weight(1f),
                        time = horario,
                        selected = horario in horariosSelecionados,
                        onClick = {
                            horariosSelecionados = if (horario in horariosSelecionados) {
                                horariosSelecionados - horario
                            } else {
                                horariosSelecionados + horario
                            }
                        }
                    )
                }
                // preenche espaço vazio se a última linha tiver menos de 3 itens
                repeat(3 - linha.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        OutlinedButton(
            onClick = { showTimePicker = true },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MedAlertPrimary)
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Horário personalizado", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            "Toque para selecionar um ou mais horários.",
            color = MedAlertTextSecondary,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text("Frequência", color = MedAlertTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(10.dp))

        frequencias.chunked(2).forEach { linha ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                linha.forEach { freq ->
                    FrequencyChip(
                        modifier = Modifier.weight(1f),
                        label = freq,
                        selected = freq == frequenciaSelecionada,
                        onClick = { frequenciaSelecionada = freq }
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        FieldLabel("Observações")
        MedAlertTextField(value = observacoes, onValueChange = { observacoes = it }, placeholder = "Opcional")

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = {
                val novoMedicamento = Medication(
                    id = medication?.id ?: System.currentTimeMillis().toString(),
                    name = nome,
                    dosage = dosagem,
                    quantity = quantidade,
                    schedules = horariosSelecionados.sorted(),
                    frequency = frequenciaSelecionada,
                    notes = observacoes.ifBlank { null },
                    active = true
                )
                onSaveClick(novoMedicamento)
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MedAlertPrimary,
                contentColor = MedAlertBackground
            )
        ) {
            Text(
                if (isEditMode) "Salvar alterações" else "Salvar medicamento",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    if (showTimePicker) {
        CustomTimePickerDialog(
            onDismiss = { showTimePicker = false },
            onConfirm = { horarioEscolhido ->
                horariosPersonalizados = horariosPersonalizados + horarioEscolhido
                horariosSelecionados = horariosSelecionados + horarioEscolhido
                showTimePicker = false
            }
        )
    }
}

@Composable
private fun TimeChip(
    modifier: Modifier = Modifier,
    time: String,
    selected: Boolean,
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
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = time,
            color = if (selected) MedAlertPrimary else MedAlertTextPrimary,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}


@Composable
private fun FrequencyChip(
    modifier: Modifier = Modifier,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .background(
                color = if (selected) MedAlertPrimary.copy(alpha = 0.18f) else MedAlertCard,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(vertical = 16.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) MedAlertPrimary else MedAlertTextPrimary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomTimePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val state = rememberTimePickerState(initialHour = 8, initialMinute = 0, is24Hour = true)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MedAlertCard
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Selecionar horário",
                    color = MedAlertTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                TimePicker(
                    state = state,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = MedAlertBackground,
                        selectorColor = MedAlertPrimary,
                        containerColor = MedAlertBackground,
                        periodSelectorSelectedContainerColor = MedAlertPrimary,
                        timeSelectorSelectedContainerColor = MedAlertPrimary,
                        timeSelectorSelectedContentColor = MedAlertBackground
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MedAlertTextPrimary)
                    ) {
                        Text("Cancelar")
                    }

                    Button(
                        onClick = {
                            val hora = state.hour.toString().padStart(2, '0')
                            val minuto = state.minute.toString().padStart(2, '0')
                            onConfirm("$hora:$minuto")
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MedAlertPrimary,
                            contentColor = MedAlertBackground
                        )
                    ) {
                        Text("Confirmar")
                    }
                }
            }
        }
    }
}
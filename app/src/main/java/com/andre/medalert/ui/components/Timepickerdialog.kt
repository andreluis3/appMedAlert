package com.andre.medalert.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.andre.medalert.ui.theme.MedAlertBackground
import com.andre.medalert.ui.theme.MedAlertCard
import com.andre.medalert.ui.theme.MedAlertPrimary
import com.andre.medalert.ui.theme.MedAlertTextPrimary

/** Resultado do reloginho (hora e minuto) -> "HH:mm". Ex.: (8, 5) -> "08:05". */
fun horaMinutoParaTexto(hora: Int, minuto: Int): String =
    "${hora.toString().padStart(2, '0')}:${minuto.toString().padStart(2, '0')}"

/** O reloginho (único, compartilhado pelas telas), devolvendo "HH:mm". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomTimePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    initialHour: Int = 8,
    initialMinute: Int = 0
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )

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
                        onClick = { onConfirm(horaMinutoParaTexto(state.hour, state.minute)) },
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
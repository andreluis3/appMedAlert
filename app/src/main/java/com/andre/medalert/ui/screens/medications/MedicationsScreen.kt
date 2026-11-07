package com.andre.medalert.ui.screens.medications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andre.medalert.data.model.Medication
import com.andre.medalert.ui.components.BottomNavigationBar
import com.andre.medalert.ui.components.CaregiverAwaitingLinkState
import com.andre.medalert.ui.theme.MedAlertBackground
import com.andre.medalert.ui.theme.MedAlertCard
import com.andre.medalert.ui.theme.MedAlertPrimary
import com.andre.medalert.ui.theme.MedAlertTextPrimary
import com.andre.medalert.ui.theme.MedAlertTextSecondary

@Composable
fun MedicationsScreen(
    medications: List<Medication> = emptyList(),
    errorMessage: String? = null,
    onNavigate: (String) -> Unit = {},
    onAddClick: () -> Unit = {},
    onEditClick: (Medication) -> Unit = {},
    // Chamado depois que o usuário confirma o diálogo de exclusão.
    onDeleteConfirm: (Medication) -> Unit = {},
    // Cuidador: só consulta (sem adicionar, editar ou excluir). O Repository também bloqueia.
    readOnly: Boolean = false,
    // Cuidador sem paciente vinculado: mostra o aviso em vez de uma lista vazia sem explicação.
    awaitingLink: Boolean = false
) {
    var medicationToDelete by remember { mutableStateOf<Medication?>(null) }

    Scaffold(
        containerColor = MedAlertBackground,
        bottomBar = {
            BottomNavigationBar(selectedRoute = "medications", onItemClick = onNavigate)
        }
    ) { innerPadding ->
        if (awaitingLink) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                CaregiverAwaitingLinkState()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp, bottom = 24.dp)
        ) {
            Text("Medicamentos", color = MedAlertTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                if (readOnly) "Medicamentos do paciente • somente consulta" else "Gerencie seus medicamentos",
                color = MedAlertTextSecondary,
                fontSize = 14.sp
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(errorMessage, color = Color(0xFFE5484D), fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (medications.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MedAlertCard, shape = RoundedCornerShape(18.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (readOnly) "O paciente ainda não tem medicamentos cadastrados" else "Nenhum medicamento cadastrado",
                        color = MedAlertTextSecondary,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            medications.forEach { medication ->
                MedicationCard(
                    medication = medication,
                    readOnly = readOnly,
                    onEditClick = { onEditClick(medication) },
                    onDeleteClick = { medicationToDelete = medication }
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (!readOnly) {
                Button(
                    onClick = onAddClick,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MedAlertPrimary,
                        contentColor = MedAlertBackground
                    )
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Adicionar medicamento", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    medicationToDelete?.let { medication ->
        DeleteConfirmationDialog(
            medicationName = medication.name,
            onCancel = { medicationToDelete = null },
            onConfirm = {
                // A exclusão real (API + Room) é feita por quem recebe o callback.
                onDeleteConfirm(medication)
                medicationToDelete = null
            }
        )
    }
}

@Composable
private fun MedicationCard(
    medication: Medication,
    readOnly: Boolean,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val detalhes = listOf(medication.dosage, medication.quantity)
        .filter { it.isNotBlank() }
        .joinToString(" • ")

    val horariosTexto = medication.schedules.joinToString(" • ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MedAlertCard, shape = RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).background(MedAlertBackground, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Medication, contentDescription = null, tint = MedAlertPrimary, modifier = Modifier.size(22.dp))
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(medication.name, color = MedAlertTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (detalhes.isNotBlank()) {
                    Text(
                        detalhes,
                        color = MedAlertTextSecondary,
                        fontSize = 13.sp
                    )
                }
            }

            if (medication.active) {
                Box(
                    modifier = Modifier
                        .background(MedAlertPrimary.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Ativo", color = MedAlertPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (horariosTexto.isNotBlank() || medication.frequency.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (horariosTexto.isNotBlank()) {
                    InfoChip(icon = Icons.Filled.AccessTime, text = horariosTexto)
                }
                if (medication.frequency.isNotBlank()) {
                    InfoChip(icon = Icons.Filled.Repeat, text = medication.frequency)
                }
            }
        }

        if (medication.stock != null) {
            Spacer(modifier = Modifier.height(8.dp))
            InfoChip(
                icon = Icons.Filled.Inventory2,
                text = when {
                    medication.stock == 0 -> "Estoque esgotado"
                    medication.isLowStock -> "Estoque baixo: ${medication.stock}"
                    else -> "Estoque: ${medication.stock}"
                }
            )
        }

        if (readOnly) return@Column

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onEditClick,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MedAlertTextPrimary)
            ) {
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Editar", fontSize = 13.sp)
            }

            OutlinedButton(
                onClick = onDeleteClick,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE5484D))
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Excluir", fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun InfoChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        modifier = Modifier
            .background(MedAlertBackground, shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MedAlertPrimary, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, color = MedAlertTextPrimary, fontSize = 12.sp)
    }
}

@Composable
private fun DeleteConfirmationDialog(
    medicationName: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = MedAlertCard,
        title = { Text("Excluir medicamento?", color = MedAlertTextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "Tem certeza que deseja excluir \"$medicationName\"? Essa ação não poderá ser desfeita.",
                color = MedAlertTextSecondary
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Excluir", color = Color(0xFFE5484D), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancelar", color = MedAlertTextSecondary)
            }
        }
    )
}

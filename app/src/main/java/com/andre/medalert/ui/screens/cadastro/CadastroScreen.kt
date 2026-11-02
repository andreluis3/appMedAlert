package com.andre.medalert.ui.screens.cadastro

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
import com.andre.medalert.viewmodel.RegisterAccountType
import com.andre.medalert.viewmodel.RegisterUiState
import com.andre.medalert.viewmodel.RegisterViewModel

private val parentescoOpcoes = listOf("Filho(a)", "Cônjuge", "Pai/Mãe", "Irmão(ã)", "Outro")

@Composable
fun CadastroScreen(
    onBackClick: () -> Unit = {},
    // Chamado quando a conta foi criada com sucesso. No MainActivity, navega para o Login.
    onRegisterSuccess: () -> Unit = {},
    onLoginClick: () -> Unit = {},
    viewModel: RegisterViewModel = viewModel(factory = RegisterViewModel.Factory)
) {
    var tipo by rememberSaveable { mutableStateOf(RegisterAccountType.PACIENTE) }
    var nome by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var confirmarSenha by remember { mutableStateOf("") }
    var telefone by remember { mutableStateOf("") }
    var parentesco by remember { mutableStateOf<String?>(null) }

    val uiState by viewModel.uiState.collectAsState()
    val isSaving = uiState is RegisterUiState.Loading
    val context = LocalContext.current

    // Quando o estado vira Success: avisa o usuário, navega para o Login e zera o ViewModel.
    LaunchedEffect(uiState) {
        val state = uiState
        if (state is RegisterUiState.Success) {
            val mensagem = if (state.tipo == RegisterAccountType.CUIDADOR) {
                "Conta de cuidador criada! Entre com seu e-mail e senha."
            } else {
                "Conta criada! Entre com seu e-mail e senha."
            }
            Toast.makeText(context, mensagem, Toast.LENGTH_LONG).show()
            onRegisterSuccess()
            viewModel.resetState()
        }
    }

    // Erro (validação, 409, 500, sem internet...) em Dialog.
    (uiState as? RegisterUiState.Error)?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.resetState() },
            title = { Text("Não foi possível criar a conta") },
            text = { Text(error.message) },
            confirmButton = {
                TextButton(onClick = { viewModel.resetState() }) {
                    Text("OK")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MedAlertBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp, bottom = 24.dp)
    ) {
        TextButton(onClick = onBackClick, contentPadding = PaddingValues(0.dp)) {
            Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = MedAlertTextSecondary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Voltar", color = MedAlertTextSecondary, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        DosyLogo(height = 36.dp)

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Criar conta",
            color = MedAlertTextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Leva menos de um minuto para começar.",
            color = MedAlertTextSecondary,
            fontSize = 15.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ---- Tipo de conta ----
        Text(
            text = "Como deseja utilizar o MedAlert?",
            color = MedAlertTextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AccountTypeCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                icon = Icons.Filled.Person,
                title = "Paciente",
                description = "Gerencie seus medicamentos e tratamentos.",
                selected = tipo == RegisterAccountType.PACIENTE,
                enabled = !isSaving,
                onClick = { tipo = RegisterAccountType.PACIENTE }
            )
            AccountTypeCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                icon = Icons.Filled.People,
                title = "Cuidador",
                description = "Acompanhe o tratamento de outra pessoa.",
                selected = tipo == RegisterAccountType.CUIDADOR,
                enabled = !isSaving,
                onClick = { tipo = RegisterAccountType.CUIDADOR }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ---- Campos comuns ----
        FieldLabel("Nome")
        MedAlertTextField(value = nome, onValueChange = { nome = it }, placeholder = "")

        Spacer(modifier = Modifier.height(16.dp))

        FieldLabel("E-mail")
        MedAlertTextField(
            value = email,
            onValueChange = { email = it },
            placeholder = "",
            keyboardType = KeyboardType.Email
        )

        Spacer(modifier = Modifier.height(16.dp))

        FieldLabel("Senha")
        MedAlertTextField(value = senha, onValueChange = { senha = it }, placeholder = "", isPassword = true)

        Spacer(modifier = Modifier.height(16.dp))

        FieldLabel("Confirmar senha")
        MedAlertTextField(value = confirmarSenha, onValueChange = { confirmarSenha = it }, placeholder = "", isPassword = true)

        // ---- Campos só do cuidador ----
        if (tipo == RegisterAccountType.CUIDADOR) {
            Spacer(modifier = Modifier.height(16.dp))

            FieldLabel("Telefone (opcional)")
            MedAlertTextField(
                value = telefone,
                onValueChange = { telefone = it.filter { c -> c.isDigit() }.take(11) },
                placeholder = "",
                keyboardType = KeyboardType.Phone
            )

            Spacer(modifier = Modifier.height(16.dp))

            FieldLabel("Parentesco (opcional)")
            ParentescoDropdown(
                selected = parentesco,
                onSelected = { parentesco = it }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                viewModel.register(
                    tipo = tipo,
                    nome = nome,
                    email = email,
                    senha = senha,
                    confirmarSenha = confirmarSenha,
                    telefone = telefone,
                    parentesco = parentesco
                )
            },
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth().height(56.dp),
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
                Text("Criar conta", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "O MedAlert é uma ferramenta de organização e lembretes. Ele não substitui a orientação de um profissional de saúde.",
            color = MedAlertTextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text("Já tem conta? ", color = MedAlertTextSecondary, fontSize = 14.sp)
            Text(
                text = "Entrar",
                color = MedAlertPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onLoginClick() }
            )
        }
    }
}

@Composable
private fun AccountTypeCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)

    Column(
        modifier = modifier
            .background(
                color = if (selected) MedAlertPrimary.copy(alpha = 0.12f) else MedAlertCard,
                shape = shape
            )
            .border(
                width = 1.dp,
                color = if (selected) MedAlertPrimary else Color.Transparent,
                shape = shape
            )
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .padding(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MedAlertPrimary else MedAlertTextSecondary,
            modifier = Modifier.size(22.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(title, color = MedAlertTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = description,
            color = MedAlertTextSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.weight(1f, fill = false)
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (selected) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MedAlertPrimary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Selecionado", color = MedAlertPrimary, fontSize = 12.sp)
            }
        } else {
            Text("Selecionar", color = MedAlertTextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ParentescoDropdown(
    selected: String?,
    onSelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(MedAlertCard, shape = RoundedCornerShape(16.dp))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { expanded = true }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selected ?: "Selecione",
                color = if (selected == null) MedAlertTextSecondary else MedAlertTextPrimary,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = MedAlertTextSecondary
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Não informar") },
                onClick = {
                    onSelected(null)
                    expanded = false
                }
            )
            parentescoOpcoes.forEach { opcao ->
                DropdownMenuItem(
                    text = { Text(opcao) },
                    onClick = {
                        onSelected(opcao)
                        expanded = false
                    }
                )
            }
        }
    }
}
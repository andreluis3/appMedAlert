package com.andre.medalert.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andre.medalert.ui.components.FriendlyErrorDialog
import com.andre.medalert.ui.theme.MedAlertBackground
import com.andre.medalert.ui.theme.MedAlertCard
import com.andre.medalert.ui.theme.MedAlertPrimary
import com.andre.medalert.ui.theme.MedAlertStatusRed
import com.andre.medalert.ui.theme.MedAlertTextPrimary
import com.andre.medalert.ui.theme.MedAlertTextSecondary
import com.andre.medalert.viewmodel.ChangePasswordViewModel

private const val TAMANHO_MINIMO_SENHA = 6

@Composable
fun ChangePasswordScreen(
    onBackClick: () -> Unit = {},
    onSavedClick: () -> Unit = {},
    viewModel: ChangePasswordViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var senhaAtual by remember { mutableStateOf("") }
    var novaSenha by remember { mutableStateOf("") }
    var confirmarSenha by remember { mutableStateOf("") }

    var senhaAtualError by remember { mutableStateOf<String?>(null) }
    var novaSenhaError by remember { mutableStateOf<String?>(null) }
    var confirmarSenhaError by remember { mutableStateOf<String?>(null) }

    var mostrarErro by remember { mutableStateOf(false) }
    var mensagemErro by remember { mutableStateOf("") }

    fun validar(): Boolean {
        senhaAtualError = if (senhaAtual.isBlank()) "Informe a senha atual." else null
        novaSenhaError = when {
            novaSenha.isBlank() -> "Informe a nova senha."
            novaSenha.length < TAMANHO_MINIMO_SENHA -> "A nova senha deve ter pelo menos $TAMANHO_MINIMO_SENHA caracteres."
            novaSenha == senhaAtual -> "A nova senha deve ser diferente da atual."
            else -> null
        }
        confirmarSenhaError = when {
            confirmarSenha.isBlank() -> "Confirme a nova senha."
            confirmarSenha != novaSenha -> "As senhas não coincidem."
            else -> null
        }
        return senhaAtualError == null && novaSenhaError == null && confirmarSenhaError == null
    }

    fun salvar() {
        if (!validar()) return
        viewModel.changePassword(
            senhaAtual = senhaAtual,
            novaSenha = novaSenha,
            onSuccess = onSavedClick,
            onError = {
                mensagemErro = "Não foi possível alterar sua senha agora. Tente novamente mais tarde."
                mostrarErro = true
            }
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
        Row {
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

        Spacer(modifier = Modifier.height(16.dp))

        Text("Alterar senha", color = MedAlertTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Escolha uma nova senha para sua conta.",
            color = MedAlertTextSecondary,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        PasswordField(
            label = "Senha atual",
            value = senhaAtual,
            onValueChange = { senhaAtual = it; senhaAtualError = null },
            error = senhaAtualError
        )

        Spacer(modifier = Modifier.height(16.dp))

        PasswordField(
            label = "Nova senha",
            value = novaSenha,
            onValueChange = { novaSenha = it; novaSenhaError = null },
            error = novaSenhaError
        )

        Spacer(modifier = Modifier.height(16.dp))

        PasswordField(
            label = "Confirmar nova senha",
            value = confirmarSenha,
            onValueChange = { confirmarSenha = it; confirmarSenhaError = null },
            error = confirmarSenhaError
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = { salvar() },
            enabled = !uiState.isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MedAlertPrimary,
                contentColor = MedAlertBackground
            )
        ) {
            Text(
                text = if (uiState.isSaving) "Salvando..." else "Salvar",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    if (mostrarErro) {
        FriendlyErrorDialog(
            title = "Não foi possível alterar a senha",
            message = mensagemErro,
            onRetryClick = {
                mostrarErro = false
                salvar()
            },
            onDismiss = { mostrarErro = false }
        )
    }
}

@Composable
private fun PasswordField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?
) {
    Text(label, color = MedAlertTextSecondary, fontSize = 12.sp)
    Spacer(modifier = Modifier.height(4.dp))
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        shape = RoundedCornerShape(14.dp),
        isError = error != null,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MedAlertCard,
            unfocusedContainerColor = MedAlertCard,
            focusedTextColor = MedAlertTextPrimary,
            unfocusedTextColor = MedAlertTextPrimary,
            focusedBorderColor = MedAlertPrimary,
            unfocusedBorderColor = MedAlertCard,
            cursorColor = MedAlertPrimary
        )
    )
    if (error != null) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(error, color = MedAlertStatusRed, fontSize = 12.sp)
    }
}
package com.andre.medalert.ui.screens.responsibles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andre.medalert.data.repository.MockResponsavelRepository
import com.andre.medalert.data.repository.ResponsavelRepository
import com.andre.medalert.ui.components.FriendlyErrorDialog
import com.andre.medalert.ui.screens.login.FieldLabel
import com.andre.medalert.ui.screens.login.MedAlertTextField
import com.andre.medalert.ui.theme.MedAlertBackground
import com.andre.medalert.ui.theme.MedAlertPrimary
import com.andre.medalert.ui.theme.MedAlertStatusRed
import com.andre.medalert.ui.theme.MedAlertTextPrimary
import com.andre.medalert.ui.theme.MedAlertTextSecondary
import kotlinx.coroutines.launch

private val emailRegex = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

/**
 * Ao salvar, monta os dados exatamente no formato de POST /api/users/cuid
 * (email, nome, userId) através de [ResponsavelRepository.adicionar].
 * Nenhum campo extra é criado ou enviado.
 */
@Composable
fun AddResponsibleScreen(
    repository: ResponsavelRepository = MockResponsavelRepository,
    onBackClick: () -> Unit = {},
    onSaved: () -> Unit = {}
) {
    var nome by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var nomeError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var salvando by remember { mutableStateOf(false) }
    var mostrarErroSalvar by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun validar(): Boolean {
        nomeError = if (nome.isBlank()) "Informe o nome do responsável." else null
        emailError = when {
            email.isBlank() -> "Informe o e-mail do responsável."
            !emailRegex.matches(email.trim()) -> "Informe um e-mail válido."
            else -> null
        }
        return nomeError == null && emailError == null
    }

    fun salvar() {
        if (!validar()) return
        salvando = true
        scope.launch {
            runCatching { repository.adicionar(nome.trim(), email.trim()) }
                .onSuccess {
                    salvando = false
                    onSaved()
                }
                .onFailure {
                    salvando = false
                    mostrarErroSalvar = true
                }
        }
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

        Spacer(modifier = Modifier.height(16.dp))

        Text("Adicionar responsável", color = MedAlertTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Essa pessoa passa a acompanhar seu tratamento.", color = MedAlertTextSecondary, fontSize = 14.sp)

        Spacer(modifier = Modifier.height(24.dp))

        FieldLabel("Nome")
        MedAlertTextField(
            value = nome,
            onValueChange = { nome = it; nomeError = null },
            placeholder = "Ex.: Maria Souza"
        )
        if (nomeError != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(nomeError!!, color = MedAlertStatusRed, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        FieldLabel("Email")
        MedAlertTextField(
            value = email,
            onValueChange = { email = it; emailError = null },
            placeholder = "responsavel@email.com"
        )
        if (emailError != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(emailError!!, color = MedAlertStatusRed, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = { salvar() },
            enabled = !salvando,
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
                text = if (salvando) "Salvando..." else "Salvar responsável",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    if (mostrarErroSalvar) {
        FriendlyErrorDialog(
            title = "Não foi possível adicionar o responsável.",
            message = "Verifique as informações preenchidas e tente novamente.",
            onRetryClick = {
                mostrarErroSalvar = false
                salvar()
            },
            onDismiss = { mostrarErroSalvar = false }
        )
    }
}
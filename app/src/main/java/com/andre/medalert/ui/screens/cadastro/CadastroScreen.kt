package com.andre.medalert.ui.screens.cadastro

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andre.medalert.ui.screens.login.FieldLabel
import com.andre.medalert.ui.screens.login.MedAlertTextField
import com.andre.medalert.ui.theme.MedAlertBackground
import com.andre.medalert.ui.theme.MedAlertPrimary
import com.andre.medalert.ui.theme.MedAlertTextPrimary
import com.andre.medalert.ui.theme.MedAlertTextSecondary

@Composable
fun CadastroScreen(
    onBackClick: () -> Unit = {},
    onCreateAccountClick: () -> Unit = {},
    onLoginClick: () -> Unit = {}
) {
    var nome by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var confirmarSenha by remember { mutableStateOf("") }

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

        Spacer(modifier = Modifier.height(28.dp))

        FieldLabel("Nome")
        MedAlertTextField(value = nome, onValueChange = { nome = it }, placeholder = "André Santos")

        Spacer(modifier = Modifier.height(16.dp))

        FieldLabel("E-mail")
        MedAlertTextField(
            value = email,
            onValueChange = { email = it },
            placeholder = "andre.santos@email.com",
            keyboardType = KeyboardType.Email
        )

        Spacer(modifier = Modifier.height(16.dp))

        FieldLabel("Senha")
        MedAlertTextField(value = senha, onValueChange = { senha = it }, placeholder = "••••••", isPassword = true)

        Spacer(modifier = Modifier.height(16.dp))

        FieldLabel("Confirmar senha")
        MedAlertTextField(value = confirmarSenha, onValueChange = { confirmarSenha = it }, placeholder = "••••••", isPassword = true)

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onCreateAccountClick,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MedAlertPrimary,
                contentColor = MedAlertBackground
            )
        ) {
            Text("Criar conta", fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
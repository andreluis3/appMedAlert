package com.andre.medalert.ui.screens.login

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andre.medalert.ui.theme.MedAlertBackground
import com.andre.medalert.ui.theme.MedAlertCard
import com.andre.medalert.ui.theme.MedAlertPrimary
import com.andre.medalert.ui.theme.MedAlertTextPrimary
import com.andre.medalert.ui.theme.MedAlertTextSecondary

@Composable
fun LoginScreen(
    onBackClick: () -> Unit = {},
    onLoginClick: () -> Unit = {},
    onCreateAccountClick: () -> Unit = {}
) {
    var email by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }

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

        Box(
            modifier = Modifier.size(48.dp).background(MedAlertCard, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Medication, contentDescription = null, tint = MedAlertPrimary, modifier = Modifier.size(24.dp))
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Bem-vindo de volta",
            color = MedAlertTextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Entre para acompanhar seus medicamentos.",
            color = MedAlertTextSecondary,
            fontSize = 15.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        FieldLabel("E-mail")
        MedAlertTextField(
            value = email,
            onValueChange = { email = it },
            placeholder = "andre.santos@email.com",
            leadingIcon = Icons.Filled.Email,
            keyboardType = KeyboardType.Email
        )

        Spacer(modifier = Modifier.height(16.dp))

        FieldLabel("Senha")
        MedAlertTextField(
            value = senha,
            onValueChange = { senha = it },
            placeholder = "••••••",
            leadingIcon = Icons.Filled.Lock,
            isPassword = true
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onLoginClick,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MedAlertPrimary,
                contentColor = MedAlertBackground
            )
        ) {
            Text("Entrar", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Esqueci minha senha",
            color = MedAlertPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text("Não tem conta? ", color = MedAlertTextSecondary, fontSize = 14.sp)
            Text(
                text = "Criar conta",
                color = MedAlertPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onCreateAccountClick() }
            )
        }
    }
}

@Composable
internal fun FieldLabel(text: String) {
    Text(text, color = MedAlertTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    Spacer(modifier = Modifier.height(6.dp))
}

@Composable
internal fun MedAlertTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: ImageVector? = null,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = MedAlertTextSecondary) },
        leadingIcon = leadingIcon?.let {
            { Icon(it, contentDescription = null, tint = MedAlertTextSecondary) }
        },
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MedAlertCard,
            unfocusedContainerColor = MedAlertCard,
            disabledContainerColor = MedAlertCard,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = MedAlertTextPrimary,
            unfocusedTextColor = MedAlertTextPrimary,
            cursorColor = MedAlertPrimary
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
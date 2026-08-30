package com.andre.medalert.ui.screens.onboarding


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.andre.medalert.ui.theme.MedAlertBackground
import com.andre.medalert.ui.theme.MedAlertCard
import com.andre.medalert.ui.theme.MedAlertPrimary
import com.andre.medalert.ui.theme.MedAlertTextPrimary
import com.andre.medalert.ui.theme.MedAlertTextSecondary
import com.andre.medalert.ui.theme.MedAlertTheme



@Composable
fun OnboardingScreen(
    onStartClick: () -> Unit = {},
    onLoginClick: () -> Unit = {}
) {

    Column(

        modifier = Modifier
            .fillMaxSize()
            .background(MedAlertBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 32.dp, bottom = 24.dp)

    ) {


        // Ícone principal

        Box(

            modifier = Modifier
                .size(48.dp)
                .background(
                    MedAlertCard,
                    shape = RoundedCornerShape(16.dp)
                ),

            contentAlignment = Alignment.Center

        ) {

            Icon(

                imageVector = Icons.Filled.Medication,
                contentDescription = null,
                tint = MedAlertPrimary,
                modifier = Modifier.size(24.dp)

            )

        }



        Spacer(
            modifier = Modifier.height(24.dp)
        )



        // Título

        Text(

            text = "Cuide da sua rotina de medicamentos.",

            color = MedAlertTextPrimary,

            fontSize = 28.sp,

            fontWeight = FontWeight.Bold,

            lineHeight = 34.sp

        )



        Spacer(
            modifier = Modifier.height(12.dp)
        )



        // Descrição

        Text(

            text = "Organize seus medicamentos, receba lembretes e acompanhe sua adesão de forma simples.",

            color = MedAlertTextSecondary,

            fontSize = 15.sp,

            lineHeight = 21.sp

        )



        Spacer(
            modifier = Modifier.height(24.dp)
        )



        // Cards

        OnboardingFeatureCard(

            icon = Icons.Filled.Medication,

            text = "Todos os seus medicamentos em um só lugar"

        )


        Spacer(
            modifier = Modifier.height(12.dp)
        )



        OnboardingFeatureCard(

            icon = Icons.Filled.Medication,

            text = "Lembretes nos horários certos"

        )


        Spacer(
            modifier = Modifier.height(12.dp)
        )



        OnboardingFeatureCard(

            icon = Icons.Filled.Medication,

            text = "Acompanhe sua adesão ao tratamento"

        )


        Spacer(
            modifier = Modifier.height(12.dp)
        )



        OnboardingFeatureCard(

            icon = Icons.Filled.Medication,

            text = "Familiares podem acompanhar você"

        )



        Spacer(
            modifier = Modifier.height(32.dp)
        )



        // Botão principal

        Button(

            onClick = onStartClick,

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

                text = "Começar",

                fontSize = 16.sp,

                fontWeight = FontWeight.Bold

            )

        }



        Spacer(
            modifier = Modifier.height(12.dp)
        )



        // Botão secundário

        OutlinedButton(

            onClick = onLoginClick,

            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),

            shape = RoundedCornerShape(28.dp),

            colors = ButtonDefaults.outlinedButtonColors(

                contentColor = MedAlertTextPrimary

            )

        ) {


            Text(

                text = "Já tenho uma conta",

                fontSize = 16.sp,

                fontWeight = FontWeight.Medium

            )

        }

    }

}





@Composable
private fun OnboardingFeatureCard(

    icon: ImageVector,

    text: String

) {


    Row(

        modifier = Modifier
            .fillMaxWidth()
            .background(

                MedAlertCard,

                shape = RoundedCornerShape(16.dp)

            )
            .padding(16.dp),

        verticalAlignment = Alignment.CenterVertically

    ) {



        Box(

            modifier = Modifier
                .size(40.dp)
                .background(

                    MedAlertBackground,

                    shape = CircleShape

                ),

            contentAlignment = Alignment.Center

        ) {


            Icon(

                imageVector = icon,

                contentDescription = null,

                tint = MedAlertPrimary,

                modifier = Modifier.size(20.dp)

            )

        }



        Spacer(
            modifier = Modifier.width(12.dp)
        )



        Text(

            text = text,

            color = MedAlertTextPrimary,

            fontSize = 14.sp,

            fontWeight = FontWeight.Medium,

            lineHeight = 19.sp

        )

    }

}





@Preview(showBackground = true)
@Composable
private fun OnboardingScreenPreview() {


    MedAlertTheme {


        OnboardingScreen()


    }

}
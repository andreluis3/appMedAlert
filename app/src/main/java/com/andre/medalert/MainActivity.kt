package com.andre.medalert

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.andre.medalert.ui.screens.onboarding.OnboardingScreen
import com.andre.medalert.ui.theme.MedAlertTheme


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {

            MedAlertTheme {

                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) {

                    OnboardingScreen()

                }
            }
        }
    }
}
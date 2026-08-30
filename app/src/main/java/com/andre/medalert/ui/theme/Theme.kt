package com.andre.medalert.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable


private val DarkColorScheme = darkColorScheme(

    primary = MedAlertPrimary,

    background = MedAlertBackground,

    surface = MedAlertCard,

    onPrimary = MedAlertBackground,

    onBackground = MedAlertTextPrimary,

    onSurface = MedAlertTextPrimary

)


@Composable
fun MedAlertTheme(
    content: @Composable () -> Unit
) {

    MaterialTheme(

        colorScheme = DarkColorScheme,

        typography = Typography,

        content = content

    )

}
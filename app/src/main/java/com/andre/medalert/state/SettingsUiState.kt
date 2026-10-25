package com.andre.medalert.state

data class SettingsUiState(
    val userName: String = "",
    val userEmail: String = "",
    val medicationReminders: Boolean = true,
    val alertSound: Boolean = true,
    val largeText: Boolean = false,
    val darkTheme: Boolean = true
)
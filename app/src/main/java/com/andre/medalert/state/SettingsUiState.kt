package com.andre.medalert.state

data class SettingsUiState(
    val userName: String = "André Santos",
    val userEmail: String = "andre.santos@email.com",
    val medicationReminders: Boolean = true,
    val alertSound: Boolean = true,
    val largeText: Boolean = false,
    val darkTheme: Boolean = true
)
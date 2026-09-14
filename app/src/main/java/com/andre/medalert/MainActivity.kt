package com.andre.medalert

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.andre.medalert.ui.screens.cadastro.CadastroScreen
import com.andre.medalert.ui.screens.home.HomeScreen
import com.andre.medalert.ui.screens.login.LoginScreen
import com.andre.medalert.ui.screens.onboarding.OnboardingScreen
import com.andre.medalert.ui.screens.settings.SettingsScreen
import com.andre.medalert.ui.theme.MedAlertTheme
import com.andre.medalert.ui.screens.medications.AddMedicationScreen
import com.andre.medalert.ui.screens.medications.MedicationsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MedAlertTheme {
                val navController = rememberNavController()

                // Rotas que já têm tela implementada e podem ser navegadas
                // a partir da barra inferior. "calendar" e "history" ainda
                // não estão wired aqui.
                val navigateToTab: (String) -> Unit = { route ->
                    if (route == "home" || route == "medications" || route == "settings") {
                        navController.navigate(route) {
                            popUpTo("home")
                            launchSingleTop = true
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = "onboarding"
                ) {
                    composable("onboarding") {
                        OnboardingScreen(
                            onStartClick = { navController.navigate("cadastro") },
                            onLoginClick = { navController.navigate("login") }
                        )
                    }

                    composable("login") {
                        LoginScreen(
                            onBackClick = { navController.popBackStack() },
                            onLoginClick = {
                                navController.navigate("home") {
                                    popUpTo("onboarding") { inclusive = true }
                                }
                            },
                            onCreateAccountClick = { navController.navigate("cadastro") }
                        )
                    }

                    composable("cadastro") {
                        CadastroScreen(
                            onBackClick = { navController.popBackStack() },
                            onCreateAccountClick = {
                                navController.navigate("home") {
                                    popUpTo("onboarding") { inclusive = true }
                                }
                            },
                            onLoginClick = { navController.navigate("login") }
                        )
                    }

                    composable("home") {
                        HomeScreen(
                            onNavigate = navigateToTab
                        )
                    }

                    composable("medications") {
                        MedicationsScreen(
                            onNavigate = navigateToTab,
                            onAddClick = { navController.navigate("addMedication") },
                            onEditClick = {
                                // Etapa futura: navegar pra addMedication passando o medicamento
                                // pra reaproveitar a tela no modo "Editar".
                            }
                        )
                    }

                    composable("addMedication") {
                        AddMedicationScreen(
                            onBackClick = { navController.popBackStack() },
                            onSaveClick = {
                                // Simulação por enquanto: só volta pra tela de Remédios.
                                // Quando tivermos Repository, aqui entra o salvamento real.
                                navController.popBackStack()
                            }
                        )
                    }

                    composable("settings") {
                        SettingsScreen(
                            onNavigate = navigateToTab,
                            onProfileClick = {
                                // Etapa futura: tela de perfil detalhada.
                            },
                            onLogoutClick = {
                                navController.navigate("onboarding") {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
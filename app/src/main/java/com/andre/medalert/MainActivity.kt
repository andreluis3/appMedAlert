package com.andre.medalert

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.mapper.toMedication
import com.andre.medalert.data.mock.mockDashboardResponse
import com.andre.medalert.data.remote.SessionManager
import com.andre.medalert.notification.NotificationHelper
import com.andre.medalert.state.AdherenceUiState
import com.andre.medalert.ui.screens.adherence.AdherenceScreen
import com.andre.medalert.ui.screens.cadastro.CadastroScreen
import com.andre.medalert.ui.screens.calendar.CalendarScreen
import com.andre.medalert.ui.screens.calendar.DayDetailsScreen
import com.andre.medalert.ui.screens.history.HistoryScreen
import com.andre.medalert.ui.screens.home.HomeScreen
import com.andre.medalert.ui.screens.login.LoginScreen
import com.andre.medalert.ui.screens.medications.MedicationsScreen
import com.andre.medalert.ui.screens.meds.CadastroMedScreen
import com.andre.medalert.ui.screens.meds.EditarMedicamentoScreen
import com.andre.medalert.ui.screens.notifications.NotificationScreen
import com.andre.medalert.ui.screens.onboarding.OnboardingScreen
import com.andre.medalert.ui.screens.profile.ChangePasswordScreen
import com.andre.medalert.ui.screens.profile.ProfileScreen
import com.andre.medalert.ui.screens.responsibles.AddResponsibleScreen
import com.andre.medalert.ui.screens.responsibles.CaregiverRequestScreen
import com.andre.medalert.ui.screens.responsibles.ResponsiblesScreen
import com.andre.medalert.ui.screens.settings.SettingsScreen
import com.andre.medalert.ui.screens.splash.SplashScreen
import com.andre.medalert.ui.theme.MedAlertTheme
import com.andre.medalert.viewmodel.MedicationViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
            // Resultado da permissão de notificações.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        NotificationHelper.createNotificationChannel(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(
                    Manifest.permission.POST_NOTIFICATIONS
                )
            }
        }

        setContent {
            MedAlertTheme {

                val navController = rememberNavController()

                // Sessão do usuário armazenada no DataStore.
                val userPreferences = remember {
                    UserPreferencesRepository(application)
                }

                val coroutineScope = rememberCoroutineScope()
                val context = LocalContext.current

                // Controle do destino após a Splash.
                var sessionLoaded by remember { mutableStateOf(false) }
                var videoFinished by remember { mutableStateOf(false) }
                var destinationAfterSplash by remember {
                    mutableStateOf("onboarding")
                }

                // Verifica se existe uma sessão salva.
                LaunchedEffect(Unit) {
                    val savedUserId = userPreferences.userId.first()

                    destinationAfterSplash =
                        if (savedUserId != null && savedUserId > 0) {
                            "home"
                        } else {
                            "onboarding"
                        }

                    sessionLoaded = true
                }

                // Só sai da Splash quando o vídeo terminou E a sessão foi lida.
                // Também confere se ainda estamos na splash (evita navegação dupla).
                LaunchedEffect(videoFinished, sessionLoaded) {
                    if (
                        videoFinished &&
                        sessionLoaded &&
                        navController.currentDestination?.route == "splash"
                    ) {
                        navController.navigate(destinationAfterSplash) {
                            popUpTo("splash") {
                                inclusive = true
                            }
                        }
                    }
                }

                // Sessão expirada: o interceptor do Retrofit recebeu 401 numa rota protegida e já
                // limpou o DataStore. Aqui só voltamos ao login (com o onboarding atrás,
                // para o botão Voltar do login não fechar o app).
                LaunchedEffect(Unit) {
                    val rotasPublicas = setOf("splash", "onboarding", "login", "cadastro")
                    SessionManager.sessionExpired.collect {
                        val rotaAtual = navController.currentDestination?.route
                        if (rotaAtual != null && rotaAtual !in rotasPublicas) {
                            Toast.makeText(
                                context,
                                "Sessão expirada. Entre novamente.",
                                Toast.LENGTH_LONG
                            ).show()
                            navController.navigate("onboarding") {
                                popUpTo(0) {
                                    inclusive = true
                                }
                            }
                            navController.navigate("login")
                        }
                    }
                }

                // Logout: limpa a sessão e retorna ao onboarding.
                val logout: () -> Unit = {
                    coroutineScope.launch {
                        userPreferences.clear()

                        navController.navigate("onboarding") {
                            popUpTo(0) {
                                inclusive = true
                            }
                        }
                    }
                }

                // Navegação entre as abas principais.
                val navigateToTab: (String) -> Unit = { route ->
                    if (
                        route == "home" ||
                        route == "medications" ||
                        route == "calendar" ||
                        route == "history" ||
                        route == "settings"
                    ) {
                        navController.navigate(route) {
                            popUpTo("home")
                            launchSingleTop = true
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = "splash"
                ) {

                    // SplashScreen inicial.
                    composable("splash") {
                        SplashScreen(
                            onAnimationFinished = {
                                videoFinished = true
                            }
                        )
                    }

                    // Onboarding.
                    // ATENÇÃO: ajuste os parâmetros conforme a assinatura
                    // do seu OnboardingScreen.
                    composable("onboarding") {
                        OnboardingScreen(
                            onLoginClick = {
                                navController.navigate("login")
                            },
                            onStartClick = {
                                navController.navigate("cadastro")
                            }
                        )
                    }

                    // Login.
                    composable("login") {
                        LoginScreen(
                            onBackClick = {
                                navController.popBackStack()
                            },
                            onLoginSuccess = {
                                navController.navigate("home") {
                                    popUpTo("onboarding") {
                                        inclusive = true
                                    }
                                }
                            },
                            onCreateAccountClick = {
                                navController.navigate("cadastro")
                            }
                        )
                    }

                    // Cadastro de usuário.
                    composable("cadastro") {
                        CadastroScreen(
                            onBackClick = {
                                navController.popBackStack()
                            },
                            onRegisterSuccess = {
                                navController.navigate("login") {
                                    popUpTo("onboarding")
                                }
                            },
                            onLoginClick = {
                                navController.navigate("login")
                            }
                        )
                    }

                    // Home.
                    composable("home") {
                        HomeScreen(
                            onNavigate = navigateToTab,
                            onBellClick = {
                                navController.navigate("notifications")
                            },
                            onAdherenceClick = {
                                navController.navigate("adherence")
                            },
                            onResponsiblesClick = {
                                navController.navigate("responsibles")
                            }
                        )
                    }

                    // Notificações.
                    composable("notifications") {
                        NotificationScreen(
                            onBackClick = {
                                navController.popBackStack()
                            }
                        )
                    }

                    // Solicitação de cuidador.
                    composable(
                        route = "caregiverRequest/{requestId}",
                        arguments = listOf(
                            navArgument("requestId") {
                                type = NavType.StringType
                            }
                        )
                    ) {
                        CaregiverRequestScreen(
                            onBackClick = {
                                navController.popBackStack()
                            }
                        )
                    }

                    // Medicamentos.
                    composable("medications") {
                        val medicationViewModel: MedicationViewModel =
                            viewModel()

                        val savedUserId by userPreferences.userId
                            .collectAsState(initial = null)

                        LaunchedEffect(savedUserId) {
                            savedUserId?.let {
                                medicationViewModel.setUser(it)
                            }
                        }

                        val medicationEntities by
                        medicationViewModel.medications.collectAsState()

                        val errorMessage by
                        medicationViewModel.error.collectAsState()

                        val medications = remember(medicationEntities) {
                            medicationEntities.map {
                                it.toMedication()
                            }
                        }

                        MedicationsScreen(
                            medications = medications,
                            errorMessage = errorMessage,
                            onNavigate = navigateToTab,
                            onAddClick = {
                                navController.navigate("cadastro_med")
                            },
                            // O argumento da rota é o ID LOCAL do Room (o remoteId fica no Repository).
                            onEditClick = { medication ->
                                navController.navigate("editar_medication/${medication.localId}")
                            },
                            onDeleteConfirm = { medication ->
                                medicationViewModel.deleteMedication(medication.localId)
                            }
                        )
                    }

                    // Cadastro de medicamento.
                    composable("cadastro_med") {
                        CadastroMedScreen(
                            onSuccess = {
                                navController.navigate("home") {
                                    popUpTo("home") {
                                        inclusive = false
                                    }
                                    launchSingleTop = true
                                }
                            },
                            onBackClick = {
                                navController.popBackStack()
                            }
                        )
                    }

                    // Edição de medicamento (medId = ID local do Room).
                    composable(
                        route = "editar_medication/{medId}",
                        arguments = listOf(
                            navArgument("medId") {
                                type = NavType.LongType
                            }
                        )
                    ) { backStackEntry ->
                        val medId =
                            backStackEntry.arguments?.getLong("medId") ?: 0L

                        EditarMedicamentoScreen(
                            localId = medId,
                            // A lista observa o Room, então ao voltar já mostra os dados novos.
                            onSuccess = {
                                navController.popBackStack()
                            },
                            onBackClick = {
                                navController.popBackStack()
                            }
                        )
                    }

                    // Calendário.
                    composable("calendar") {
                        CalendarScreen(
                            onNavigate = navigateToTab,
                            onDayClick = { dayUi ->
                                dayUi.day?.let { day ->
                                    navController.navigate(
                                        "dayDetails/$day/${dayUi.isToday}"
                                    )
                                }
                            }
                        )
                    }

                    // Detalhes do dia.
                    composable(
                        route = "dayDetails/{day}/{isToday}",
                        arguments = listOf(
                            navArgument("day") {
                                type = NavType.IntType
                            },
                            navArgument("isToday") {
                                type = NavType.BoolType
                            }
                        )
                    ) { backStackEntry ->

                        val day =
                            backStackEntry.arguments?.getInt("day") ?: 1

                        val isToday =
                            backStackEntry.arguments?.getBoolean("isToday")
                                ?: false

                        DayDetailsScreen(
                            day = day,
                            isToday = isToday,
                            onBackClick = {
                                navController.popBackStack()
                            }
                        )
                    }

                    // Histórico.
                    composable("history") {
                        HistoryScreen(
                            onNavigate = navigateToTab,
                            onAdherenceClick = {
                                navController.navigate("adherence")
                            }
                        )
                    }

                    // Adesão ao tratamento.
                    composable("adherence") {
                        var uiState by remember {
                            mutableStateOf<AdherenceUiState>(
                                AdherenceUiState.Loading
                            )
                        }

                        var reloadKey by remember {
                            mutableStateOf(0)
                        }

                        LaunchedEffect(reloadKey) {
                            uiState = AdherenceUiState.Loading

                            delay(800)

                            uiState = AdherenceUiState.Success(
                                mockDashboardResponse
                            )
                        }

                        AdherenceScreen(
                            uiState = uiState,
                            onBackClick = {
                                navController.popBackStack()
                            },
                            onRetryClick = {
                                reloadKey++
                            }
                        )
                    }

                    // Responsáveis.
                    composable("responsibles") { backStackEntry ->

                        val responsavelAdicionado by
                        backStackEntry.savedStateHandle
                            .getStateFlow(
                                "responsavelAdicionado",
                                false
                            )
                            .collectAsState()

                        var reloadKey by remember {
                            mutableStateOf(0)
                        }

                        LaunchedEffect(responsavelAdicionado) {
                            if (responsavelAdicionado) {
                                reloadKey++

                                backStackEntry.savedStateHandle[
                                    "responsavelAdicionado"
                                ] = false
                            }
                        }

                        ResponsiblesScreen(
                            reloadKey = reloadKey,
                            onBackClick = {
                                navController.popBackStack()
                            },
                            onAddClick = {
                                navController.navigate("addResponsible")
                            }
                        )
                    }

                    // Adicionar responsável.
                    composable("addResponsible") {
                        AddResponsibleScreen(
                            onBackClick = {
                                navController.popBackStack()
                            },
                            onSaved = {
                                navController.previousBackStackEntry
                                    ?.savedStateHandle
                                    ?.set("responsavelAdicionado", true)

                                navController.popBackStack()
                            }
                        )
                    }

                    // Configurações.
                    composable("settings") {
                        SettingsScreen(
                            onNavigate = navigateToTab,
                            onProfileClick = {
                                navController.navigate("profile")
                            },
                            onLogoutClick = logout
                        )
                    }

                    // Perfil.
                    composable("profile") {
                        ProfileScreen(
                            onBackClick = {
                                navController.popBackStack()
                            },
                            onChangePasswordClick = {
                                navController.navigate("changePassword")
                            },
                            onForgotPasswordClick = {
                                // Recuperação de senha ainda não implementada.
                            },
                            onLogoutClick = logout
                        )
                    }
                }
            }
        }
    }
}

package com.andre.medalert

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.andre.medalert.ui.screens.cadastro.CadastroScreen
import com.andre.medalert.ui.screens.calendar.CalendarScreen
import com.andre.medalert.ui.screens.calendar.DayDetailsScreen
import com.andre.medalert.ui.screens.history.HistoryScreen
import com.andre.medalert.ui.screens.home.HomeScreen
import com.andre.medalert.ui.screens.login.LoginScreen
import com.andre.medalert.ui.screens.onboarding.OnboardingScreen
import com.andre.medalert.ui.screens.settings.SettingsScreen
import com.andre.medalert.ui.theme.MedAlertTheme
import com.andre.medalert.ui.screens.medications.AddMedicationScreen
import com.andre.medalert.ui.screens.medications.MedicationsScreen
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.andre.medalert.notification.NotificationHelper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.mapper.toMedication
import com.andre.medalert.data.mock.mockDashboardResponse
import com.andre.medalert.state.AdherenceUiState
import com.andre.medalert.ui.screens.adherence.AdherenceScreen
import com.andre.medalert.ui.screens.notifications.NotificationScreen
import com.andre.medalert.ui.screens.responsibles.AddResponsibleScreen
import com.andre.medalert.ui.screens.responsibles.CaregiverRequestScreen
import com.andre.medalert.ui.screens.responsibles.ResponsiblesScreen
import com.andre.medalert.ui.screens.profile.ProfileScreen
import com.andre.medalert.ui.screens.profile.ChangePasswordScreen
import com.andre.medalert.ui.theme.MedAlertBackground
import com.andre.medalert.viewmodel.MedicationViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* resultado ignorado por enquanto */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationHelper.createNotificationChannel(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            MedAlertTheme {
                val navController = rememberNavController()

                // Sessão do usuário (userId e token) guardada no DataStore.
                val userPreferences = remember { UserPreferencesRepository(application) }
                val coroutineScope = rememberCoroutineScope()

                // Lê UMA vez, ao abrir o app, se já existe um usuário logado.
                // Se existir, começa direto na Home; senão, no onboarding.
                var sessionLoaded by remember { mutableStateOf(false) }
                var startDestination by remember { mutableStateOf("onboarding") }

                LaunchedEffect(Unit) {
                    val savedUserId = userPreferences.userId.first()
                    startDestination =
                        if (savedUserId != null && savedUserId > 0) "home" else "onboarding"
                    sessionLoaded = true
                }

                // Logout de verdade: limpa o DataStore e volta para o onboarding.
                val logout: () -> Unit = {
                    coroutineScope.launch {
                        userPreferences.clear()
                        navController.navigate("onboarding") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }

                // Enquanto o DataStore não responde, mostra só o fundo (evita piscar o onboarding).
                if (!sessionLoaded) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MedAlertBackground)
                    )
                    return@MedAlertTheme
                }

                // Todas as 5 abas da bottom bar já têm tela implementada.
                val navigateToTab: (String) -> Unit = { route ->
                    if (route == "home" || route == "medications" || route == "calendar" || route == "history" || route == "settings") {
                        navController.navigate(route) {
                            popUpTo("home")
                            launchSingleTop = true
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = startDestination
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
                            onLoginSuccess = {
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
                            // Conta criada: vai para o Login. O popUpTo("onboarding") tira o
                            // cadastro (e um login antigo) da pilha, então "voltar" no Login
                            // leva ao onboarding e não de volta ao cadastro.
                            onRegisterSuccess = {
                                navController.navigate("login") {
                                    popUpTo("onboarding")
                                }
                            },
                            onLoginClick = { navController.navigate("login") }
                        )
                    }

                    composable("home") {
                        HomeScreen(
                            onNavigate = navigateToTab,
                            onBellClick = { navController.navigate("notifications") },
                            onAdherenceClick = { navController.navigate("adherence") },
                            onResponsiblesClick = { navController.navigate("responsibles") }
                        )
                    }

                    composable("notifications") {
                        NotificationScreen(
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    // Tela de confirmação da solicitação de cuidador.
                    // Hoje usa dados MOCK (MockCaregiverRequestRepository).
                    // Etapa futura: abrir esta rota ao tocar na notificação (sininho / push FCM).
                    composable(
                        route = "caregiverRequest/{requestId}",
                        arguments = listOf(
                            navArgument("requestId") { type = NavType.StringType }
                        )
                    ) {
                        CaregiverRequestScreen(
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    composable("medications") {
                        // A tela observa o Room (offline-first). A API só atualiza o Room.
                        val medicationViewModel: MedicationViewModel = viewModel()

                        // userId real do usuário logado, vindo do DataStore.
                        val savedUserId by userPreferences.userId.collectAsState(initial = null)

                        LaunchedEffect(savedUserId) {
                            savedUserId?.let { medicationViewModel.setUser(it) }
                        }

                        val medicationEntities by medicationViewModel.medications.collectAsState()
                        val errorMessage by medicationViewModel.error.collectAsState()
                        val medications = remember(medicationEntities) {
                            medicationEntities.map { it.toMedication() }
                        }

                        MedicationsScreen(
                            medications = medications,
                            errorMessage = errorMessage,
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

                    composable("calendar") {
                        CalendarScreen(
                            onNavigate = navigateToTab,
                            onDayClick = { dayUi ->
                                dayUi.day?.let { day ->
                                    navController.navigate("dayDetails/$day/${dayUi.isToday}")
                                }
                            }
                        )
                    }

                    composable(
                        route = "dayDetails/{day}/{isToday}",
                        arguments = listOf(
                            navArgument("day") { type = NavType.IntType },
                            navArgument("isToday") { type = NavType.BoolType }
                        )
                    ) { backStackEntry ->
                        val day = backStackEntry.arguments?.getInt("day") ?: 1
                        val isToday = backStackEntry.arguments?.getBoolean("isToday") ?: false

                        DayDetailsScreen(
                            day = day,
                            isToday = isToday,
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    composable("history") {
                        HistoryScreen(
                            onNavigate = navigateToTab,
                            onAdherenceClick = { navController.navigate("adherence") }
                        )
                    }

                    composable("adherence") {
                        // SIMULAÇÃO enquanto não há backend: mostra "Carregando" e depois os dados mockados.
                        // Quando integrar, este bloco é substituído por um ViewModel que emite
                        // AdherenceUiState (Loading -> Success/Error) a partir de GET /api/historys/dashboard.
                        //
                        // Para testar os outros estados, troque a linha do Success por:
                        //   AdherenceUiState.Error()                          -> tela de erro
                        //   AdherenceUiState.Success(mockDashboardEmpty)      -> tela sem dados
                        //     (import com.andre.medalert.data.mock.mockDashboardEmpty)
                        var uiState by remember { mutableStateOf<AdherenceUiState>(AdherenceUiState.Loading) }
                        var reloadKey by remember { mutableStateOf(0) }

                        LaunchedEffect(reloadKey) {
                            uiState = AdherenceUiState.Loading
                            delay(800)
                            uiState = AdherenceUiState.Success(mockDashboardResponse)
                        }

                        AdherenceScreen(
                            uiState = uiState,
                            onBackClick = { navController.popBackStack() },
                            onRetryClick = { reloadKey++ }
                        )
                    }

                    composable("responsibles") { backStackEntry ->
                        // Recebe o "avisou que salvou" de volta da tela de adicionar
                        // (padrão de resultado entre telas do Navigation Compose) e
                        // usa isso pra forçar a lista a recarregar.
                        val responsavelAdicionado by backStackEntry.savedStateHandle
                            .getStateFlow("responsavelAdicionado", false)
                            .collectAsState()

                        var reloadKey by remember { mutableStateOf(0) }

                        LaunchedEffect(responsavelAdicionado) {
                            if (responsavelAdicionado) {
                                reloadKey++
                                backStackEntry.savedStateHandle["responsavelAdicionado"] = false
                            }
                        }

                        ResponsiblesScreen(
                            reloadKey = reloadKey,
                            onBackClick = { navController.popBackStack() },
                            onAddClick = { navController.navigate("addResponsible") }
                        )
                    }

                    composable("addResponsible") {
                        AddResponsibleScreen(
                            onBackClick = { navController.popBackStack() },
                            onSaved = {
                                navController.previousBackStackEntry
                                    ?.savedStateHandle
                                    ?.set("responsavelAdicionado", true)
                                navController.popBackStack()
                            }
                        )
                    }

                    composable("settings") {
                        SettingsScreen(
                            onNavigate = navigateToTab,
                            onProfileClick = {
                                navController.navigate("profile")
                            },
                            onLogoutClick = logout
                        )
                    }

                    composable("profile") {
                        ProfileScreen(
                            onBackClick = { navController.popBackStack() },
                            onChangePasswordClick = {
                                navController.navigate("changePassword")
                            },
                            onForgotPasswordClick = {
                                // Etapa futura: tela de recuperação de senha ainda não existe.
                            },
                            onLogoutClick = logout
                        )
                    }
                }
            }
        }
    }
}
package com.andre.medalert

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.andre.medalert.data.mapper.toMedication
import com.andre.medalert.data.session.AccessPolicy
import com.andre.medalert.ui.components.BlockForCaregiver
import com.andre.medalert.data.remote.SessionManager
import com.andre.medalert.notification.MedicationReminderManager
import com.andre.medalert.notification.NotificationHelper
import com.andre.medalert.sync.MedicationSyncWorker
import com.andre.medalert.ui.screens.adherence.AdherenceScreen
import com.andre.medalert.ui.screens.cadastro.CadastroScreen
import com.andre.medalert.ui.screens.calendar.CalendarScreen
import com.andre.medalert.ui.screens.calendar.DayDetailsScreen
import com.andre.medalert.ui.screens.history.HistoryScreen
import com.andre.medalert.ui.screens.home.HomeScreen
import com.andre.medalert.ui.screens.login.LoginScreen
import com.andre.medalert.ui.screens.medications.MedicationsScreen
import com.andre.medalert.ui.screens.meds.CadastroMedScreen
import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.ui.screens.meds.EditarMedicamentoScreen
import com.andre.medalert.ui.screens.notifications.NotificationScreen
import com.andre.medalert.ui.screens.onboarding.OnboardingScreen
import com.andre.medalert.ui.screens.profile.ProfileScreen
import com.andre.medalert.ui.screens.responsibles.AddResponsibleScreen
import com.andre.medalert.ui.screens.responsibles.CaregiverRequestScreen
import com.andre.medalert.ui.screens.responsibles.ResponsiblesScreen
import com.andre.medalert.ui.screens.settings.SettingsScreen
import com.andre.medalert.ui.screens.splash.SplashScreen
import com.andre.medalert.ui.theme.MedAlertTheme
import com.andre.medalert.viewmodel.AdherenceViewModel
import com.andre.medalert.viewmodel.MedicationViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // Muda a cada retorno ao app (ex.: vindo das Configurações) para a Home reavaliar as permissões.
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        NotificationHelper.createNotificationChannel(this)

        // A permissão de notificações NÃO é pedida aqui: a Home mostra o NotificationPermissionDialog.

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
                // Os alarmes são cancelados sozinhos pelo observador da Application.
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
                // Os alarmes do usuário saem sozinhos (o observador vê a sessão vazia).
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
                            permissionRefreshKey = resumeTick,
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

                        // Paciente: o próprio id. Cuidador: o paciente vinculado (nunca o id do cuidador).
                        val session by userPreferences.session
                            .collectAsState(initial = null)

                        val patientId = session?.let {
                            AccessPolicy.patientIdFor(it.tipo, it.userId, it.pacienteId)
                        }
                        val isCaregiver = session?.let { AccessPolicy.isCaregiver(it.tipo) } ?: false
                        val awaitingLink = session?.let {
                            AccessPolicy.isAwaitingLink(it.tipo, it.userId, it.pacienteId)
                        } ?: false

                        LaunchedEffect(patientId) {
                            patientId?.let {
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
                            },
                            readOnly = isCaregiver,
                            awaitingLink = awaitingLink
                        )
                    }

                    // Cadastro de medicamento.
                    composable("cadastro_med") {
                        // Cuidador é somente leitura: não cadastra medicamentos.
                        BlockForCaregiver(
                            onBlocked = { navController.popBackStack() },
                            allowed = AccessPolicy::canCreateMedication
                        ) {
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

                        // Cuidador é somente leitura: não edita medicamentos.
                        BlockForCaregiver(onBlocked = { navController.popBackStack() }) {
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
                    }

                    // Calendário.
                    composable("calendar") {
                        CalendarScreen(
                            onNavigate = navigateToTab,
                            onDayClick = { year, month, day ->
                                navController.navigate("dayDetails/$year/$month/$day")
                            }
                        )
                    }

                    // Detalhes do dia.
                    composable(
                        route = "dayDetails/{year}/{month}/{day}",
                        arguments = listOf(
                            navArgument("year") { type = NavType.IntType },
                            navArgument("month") { type = NavType.IntType },
                            navArgument("day") { type = NavType.IntType }
                        )
                    ) { backStackEntry ->
                        val args = backStackEntry.arguments

                        DayDetailsScreen(
                            year = args?.getInt("year") ?: 1970,
                            month = args?.getInt("month") ?: 1,
                            day = args?.getInt("day") ?: 1,
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

                    // Adesão ao tratamento (GET /api/historys/dashboard/{id}).
                    composable("adherence") {
                        val adherenceViewModel: AdherenceViewModel = viewModel()
                        val uiState by adherenceViewModel.uiState.collectAsState()

                        AdherenceScreen(
                            uiState = uiState,
                            onBackClick = {
                                navController.popBackStack()
                            },
                            onRetryClick = {
                                adherenceViewModel.load()
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

    override fun onResume() {
        super.onResume()
        resumeTick++
        // Ao abrir o app: garante que os alarmes batem com o Room e sincroniza com a API
        // (sem sessão a sincronização não faz nada; sem internet ela espera a conexão).
        lifecycleScope.launch {
            MedicationReminderManager.rescheduleAll(applicationContext)
        }
        MedicationSyncWorker.enqueue(applicationContext)
    }
}
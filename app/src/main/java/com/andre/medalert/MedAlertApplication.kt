package com.andre.medalert

import android.app.Application
import com.andre.medalert.data.remote.SessionManager
import com.andre.medalert.data.schedule.SessionMedications
import com.andre.medalert.data.session.SessionLifecycle
import com.andre.medalert.notification.MedicationReminderManager
import com.andre.medalert.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Observa o Room durante a vida do processo e mantém os alarmes sincronizados:
 * cadastro, edição, exclusão, refresh da API, logout e sessão expirada passam todos por aqui.
 * Sessão de cuidador não mexe nos alarmes (ver SessionMedications.observeForAlarms).
 * Também liga o token da sessão ao Retrofit e inicia o ciclo de sincronização (SessionLifecycle).
 */
class MedAlertApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Sem isto o interceptor do Retrofit não enxerga o token e nenhuma rota protegida recebe
        // o "Authorization: Bearer".
        SessionManager.init(this)
        NotificationHelper.createNotificationChannel(this)

        // Login, logout, 401 e aceite de vínculo: sincronização, token FCM e limpeza de cache.
        appScope.launch {
            SessionLifecycle.observe(this@MedAlertApplication)
        }

        appScope.launch {
            SessionMedications.observeForAlarms(this@MedAlertApplication)
                .collect { medications ->
                    MedicationReminderManager.apply(this@MedAlertApplication, medications)
                }
        }
    }
}
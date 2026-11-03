package com.andre.medalert

import android.app.Application
import com.andre.medalert.data.schedule.SessionMedications
import com.andre.medalert.notification.MedicationReminderManager
import com.andre.medalert.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Observa o Room durante a vida do processo e mantém os alarmes sincronizados:
 * cadastro, edição, exclusão, refresh da API, logout e sessão expirada passam todos por aqui.
 */
class MedAlertApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)

        appScope.launch {
            SessionMedications.observe(this@MedAlertApplication, patientOnly = true)
                .collect { medications ->
                    MedicationReminderManager.apply(this@MedAlertApplication, medications)
                }
        }
    }
}
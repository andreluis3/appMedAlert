package com.andre.medalert.data.schedule

import android.content.Context
import com.andre.medalert.data.local.AppDatabase
import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.session.AccessPolicy
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Lê o Room e calcula a adesão dos últimos dias SEM internet (ver [AdherenceCalculator]).
 *
 * Só vale para o PACIENTE: as doses tomadas são registradas no aparelho dele. Num aparelho de
 * cuidador não existe histórico do paciente, então calcular aqui daria "0%" falso. Nesse caso
 * devolve null (a tela mostra o dado do servidor ou "sem dados").
 */
object LocalAdherence {

    suspend fun compute(
        context: Context,
        patientId: Int,
        now: LocalDateTime = LocalDateTime.now()
    ): AdherenceStats? {
        val app = context.applicationContext
        val session = UserPreferencesRepository(app).session.first()
        if (AccessPolicy.isCaregiver(session.tipo)) return null

        val zone = ZoneId.systemDefault()
        val fromMillis = now.minusDays(AdherenceCalculator.DEFAULT_WINDOW_DAYS.toLong())
            .atZone(zone).toInstant().toEpochMilli()

        val medications = SessionMedications.observe(app).first()
        val taken = AppDatabase.getInstance(app).doseHistoryDao()
            .observeLocalTaken(patientId, fromMillis).first()
            .mapNotNull {
                val medId = it.medicationLocalId ?: return@mapNotNull null
                val scheduled = it.previstoEm ?: return@mapNotNull null
                TakenDose(medId, scheduled, it.tomadoEm ?: scheduled)
            }

        return AdherenceCalculator.calculate(medications, taken, now, zone = zone)
    }
}

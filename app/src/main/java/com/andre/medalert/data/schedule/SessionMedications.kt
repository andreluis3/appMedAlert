package com.andre.medalert.data.schedule

import android.content.Context
import com.andre.medalert.data.local.AppDatabase
import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.local.entity.MedicationEntity
import com.andre.medalert.data.session.AccessPolicy
import com.andre.medalert.data.session.AlarmTarget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Descobre qual paciente a sessão atual acompanha. */
object SessionUser {

    /** Paciente: o próprio id. Cuidador: o pacienteId. Sem sessão válida (ou sem vínculo): null. */
    suspend fun patientId(context: Context): Int? {
        val session = UserPreferencesRepository(context.applicationContext).session.first()
        return AccessPolicy.patientIdFor(session.tipo, session.userId, session.pacienteId)
    }
}

/** Room como única fonte de verdade: medicamentos do paciente da sessão, sempre atualizados. */
object SessionMedications {

    /** Para as TELAS: paciente vê os seus; cuidador vê os do paciente vinculado. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(context: Context): Flow<List<MedicationEntity>> {
        val app = context.applicationContext
        val prefs = UserPreferencesRepository(app)
        val dao = AppDatabase.getInstance(app).medicationDao()

        return prefs.session
            .map { AccessPolicy.patientIdFor(it.tipo, it.userId, it.pacienteId) }
            .distinctUntilChanged()
            .flatMapLatest { target ->
                if (target == null) flowOf(emptyList()) else dao.observeByUser(target)
            }
    }

    /**
     * Para os ALARMES (MedAlertApplication):
     * - paciente: emite os medicamentos dele (a cada mudança do Room);
     * - sem sessão: emite lista vazia UMA vez (cancela tudo);
     * - cuidador: NÃO emite nada, então os alarmes já agendados do paciente ficam como estão
     *   e nenhum alarme é criado para o paciente de outra pessoa.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeForAlarms(context: Context): Flow<List<MedicationEntity>> {
        val app = context.applicationContext
        val prefs = UserPreferencesRepository(app)
        val dao = AppDatabase.getInstance(app).medicationDao()

        return prefs.session
            .map { AccessPolicy.alarmTarget(it.tipo, it.userId) }
            .distinctUntilChanged()
            .flatMapLatest { target ->
                when (target) {
                    is AlarmTarget.Patient -> dao.observeByUser(target.userId)
                    AlarmTarget.KeepUntouched -> emptyFlow()
                    AlarmTarget.ClearAll -> flowOf(emptyList())
                }
            }
    }
}

package com.andre.medalert.data.schedule

import android.content.Context
import com.andre.medalert.data.local.AppDatabase
import com.andre.medalert.data.local.entity.MedicationEntity
import com.andre.medalert.data.local.UserPreferencesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/** Descobre qual paciente a sessão atual acompanha. */
object SessionUser {

    /** Paciente: o próprio id. Cuidador: o pacienteId. Sem sessão válida: null. */
    suspend fun patientId(context: Context): Int? {
        val prefs = UserPreferencesRepository(context.applicationContext)
        val tipo = prefs.userType.first()
        val id = if (tipo == "cuidador") prefs.pacienteId.first() else prefs.userId.first()
        return id?.takeIf { it > 0 }
    }
}

/** Room como única fonte de verdade: medicamentos do paciente da sessão, sempre atualizados. */
object SessionMedications {

    /**
     * @param patientOnly true = só emite medicamentos quando a sessão é de paciente
     * (usado pelos alarmes; cuidador não recebe lembretes de dose).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(context: Context, patientOnly: Boolean = false): Flow<List<MedicationEntity>> {
        val app = context.applicationContext
        val prefs = UserPreferencesRepository(app)
        val dao = AppDatabase.getInstance(app).medicationDao()

        return combine(prefs.userId, prefs.userType, prefs.pacienteId) { id, tipo, pacienteId ->
            val target = if (tipo == "cuidador") {
                if (patientOnly) null else pacienteId
            } else {
                id
            }
            target?.takeIf { it > 0 }
        }
            .distinctUntilChanged()
            .flatMapLatest { target ->
                if (target == null) flowOf(emptyList()) else dao.observeByUser(target)
            }
    }
}
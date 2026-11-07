package com.andre.medalert.data.session

/** Foto única da sessão (lida de uma só vez do DataStore, sem estados intermediários). */
data class SessionInfo(
    val userId: Int?,
    val tipo: String,
    val pacienteId: Int?
)

/** O que fazer com os alarmes do aparelho para a sessão atual. */
sealed interface AlarmTarget {
    /** Sessão de paciente: os alarmes são os dos medicamentos de [userId]. */
    data class Patient(val userId: Int) : AlarmTarget

    /** Sessão de cuidador: não cria e NÃO cancela nenhum alarme. */
    data object KeepUntouched : AlarmTarget

    /** Sem sessão (logout ou sessão expirada): cancela todos os alarmes. */
    data object ClearAll : AlarmTarget
}

/**
 * Regras de acesso paciente x cuidador, num lugar só e sem dependência de Android
 * (por isso é testável em JVM).
 *
 * - Paciente: lê e altera os PRÓPRIOS medicamentos.
 * - Cuidador: só LÊ os medicamentos do paciente vinculado (pacienteId). Nunca grava.
 * - Sessões antigas sem tipo são tratadas como paciente.
 */
object AccessPolicy {

    const val TIPO_PACIENTE = "paciente"
    const val TIPO_CUIDADOR = "cuidador"

    fun isCaregiver(tipo: String?): Boolean =
        tipo?.trim().equals(TIPO_CUIDADOR, ignoreCase = true)

    /** Criar, editar e excluir medicamentos (e enviar pendentes) só vale para paciente. */
    fun canModifyMedications(tipo: String?): Boolean = !isCaregiver(tipo)

    /**
     * De quem são os dados mostrados: paciente = ele mesmo; cuidador = o paciente vinculado.
     * Cuidador sem vínculo (pacienteId ausente) ou sem sessão: null.
     */
    fun patientIdFor(tipo: String?, userId: Int?, pacienteId: Int?): Int? {
        val id = if (isCaregiver(tipo)) pacienteId else userId
        return id?.takeIf { it > 0 }
    }

    /** Cuidador logado que ainda não tem paciente vinculado. */
    fun isAwaitingLink(tipo: String?, userId: Int?, pacienteId: Int?): Boolean =
        isCaregiver(tipo) && (userId ?: 0) > 0 && (pacienteId ?: 0) <= 0

    fun alarmTarget(tipo: String?, userId: Int?): AlarmTarget {
        val id = userId?.takeIf { it > 0 } ?: return AlarmTarget.ClearAll
        return if (isCaregiver(tipo)) AlarmTarget.KeepUntouched else AlarmTarget.Patient(id)
    }
}

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
 * - Paciente: lê e altera os PRÓPRIOS medicamentos e registra as próprias administrações.
 * - Cuidador: lê os medicamentos e o histórico do paciente vinculado (pacienteId) e REGISTRA
 *   administrações dele, mas nunca cadastra, edita ou exclui medicamentos.
 * - Sessões antigas sem tipo são tratadas como paciente.
 *
 * Esta política só decide o que a INTERFACE e a camada de dados do Android oferecem. A segurança
 * real (token + vínculo cuidador/paciente) precisa ser validada pela API em cada rota.
 */
object AccessPolicy {

    const val TIPO_PACIENTE = "paciente"
    const val TIPO_CUIDADOR = "cuidador"

    fun isCaregiver(tipo: String?): Boolean =
        tipo?.trim().equals(TIPO_CUIDADOR, ignoreCase = true)

    /** Cadastrar medicamento (e enviar os cadastros pendentes): só paciente. */
    fun canCreateMedication(tipo: String?): Boolean = !isCaregiver(tipo)

    /** Editar medicamento: só paciente. */
    fun canEditMedication(tipo: String?): Boolean = !isCaregiver(tipo)

    /** Excluir medicamento: só paciente. */
    fun canDeleteMedication(tipo: String?): Boolean = !isCaregiver(tipo)

    /**
     * Registrar administração de dose: paciente (a própria) e cuidador (do paciente vinculado).
     * Exige haver um paciente-alvo: cuidador sem vínculo e sessão vazia não registram nada.
     */
    fun canRegisterDose(tipo: String?, userId: Int?, pacienteId: Int?): Boolean =
        patientIdFor(tipo, userId, pacienteId) != null

    /** Consultar o histórico de administrações: paciente (o próprio) e cuidador vinculado. */
    fun canViewHistory(tipo: String?, userId: Int?, pacienteId: Int?): Boolean =
        patientIdFor(tipo, userId, pacienteId) != null

    /** Cuidador com paciente vinculado (único caso em que alertas do paciente fazem sentido). */
    fun canViewLinkedPatient(tipo: String?, pacienteId: Int?): Boolean =
        isCaregiver(tipo) && (pacienteId ?: 0) > 0

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

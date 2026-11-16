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
 * O PACIENTE é sempre o dono dos dados clínicos:
 * - Paciente: lê e altera (cadastra, edita, exclui) os PRÓPRIOS medicamentos e registra as
 *   próprias administrações.
 * - Cuidador COM vínculo válido (pacienteId > 0): lê, EDITA e EXCLUI os medicamentos do paciente
 *   vinculado, mas NÃO cadastra medicamentos novos. Nada é gravado com o id do cuidador: o dono é
 *   sempre o pacienteId.
 * - Registro de dose: o comportamento atual foi mantido na Etapa 2A (paciente e cuidador
 *   vinculado). A regra definitiva (cuidador não registra dose) será aplicada na etapa de histórico.
 * - Cuidador SEM vínculo, ou sem sessão: não lê nem altera nada.
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

    // ---------------------------------------------------------------------------
    // Medicamentos: cadastrar, editar, excluir
    // ---------------------------------------------------------------------------

    /**
     * CADASTRAR medicamento novo: só o PACIENTE, com sessão válida (userId > 0).
     * Cuidador, mesmo vinculado, não cadastra.
     */
    fun canAddMedications(tipo: String?, userId: Int?, pacienteId: Int?): Boolean =
        !isCaregiver(tipo) && (userId ?: 0) > 0 && patientIdFor(tipo, userId, pacienteId) != null

    /**
     * EDITAR medicamento existente.
     * - Paciente: precisa de sessão válida (userId > 0).
     * - Cuidador: precisa de sessão válida E de paciente vinculado (pacienteId > 0).
     */
    fun canEditMedications(tipo: String?, userId: Int?, pacienteId: Int?): Boolean =
        (userId ?: 0) > 0 && patientIdFor(tipo, userId, pacienteId) != null

    /** EXCLUIR medicamento existente. Mesmas condições de [canEditMedications]. */
    fun canDeleteMedications(tipo: String?, userId: Int?, pacienteId: Int?): Boolean =
        (userId ?: 0) > 0 && patientIdFor(tipo, userId, pacienteId) != null

    /**
     * Compatibilidade: alterar um medicamento JÁ EXISTENTE (editar ou excluir). Não inclui
     * cadastrar: para isso use [canAddMedications].
     */
    fun canManageMedications(tipo: String?, userId: Int?, pacienteId: Int?): Boolean =
        canEditMedications(tipo, userId, pacienteId)

    /**
     * Paciente DONO dos medicamentos que a sessão pode editar, excluir ou enviar (pendentes): o
     * próprio paciente, ou o paciente vinculado ao cuidador. null quando a sessão não pode.
     */
    fun medicationOwnerIdFor(tipo: String?, userId: Int?, pacienteId: Int?): Int? =
        if (canManageMedications(tipo, userId, pacienteId)) {
            patientIdFor(tipo, userId, pacienteId)
        } else {
            null
        }

    /**
     * Paciente dono de um medicamento NOVO que a sessão pode cadastrar. null para cuidador (mesmo
     * vinculado) e sem sessão. É este id (e nunca o do cuidador) que vai em `usuario_id`.
     */
    fun medicationAddOwnerIdFor(tipo: String?, userId: Int?, pacienteId: Int?): Int? =
        if (canAddMedications(tipo, userId, pacienteId)) {
            patientIdFor(tipo, userId, pacienteId)
        } else {
            null
        }

    /**
     * A sessão pode alterar (editar ou excluir) ESTE medicamento? Exige poder alterar medicamentos
     * E que o medicamento pertença ao paciente autorizado da sessão ([medicationOwnerId] = `userId`
     * da linha no Room). Impede, por exemplo, que um cuidador altere medicamento de outro paciente
     * que ficou em cache.
     */
    fun canManageMedication(
        tipo: String?,
        userId: Int?,
        pacienteId: Int?,
        medicationOwnerId: Int?
    ): Boolean {
        val owner = medicationOwnerIdFor(tipo, userId, pacienteId) ?: return false
        return medicationOwnerId != null && medicationOwnerId == owner
    }

    // ---------------------------------------------------------------------------
    // Doses, histórico e paciente-alvo
    // ---------------------------------------------------------------------------

    /**
     * Registrar administração de dose: paciente (a própria) e cuidador (do paciente vinculado).
     * Exige haver um paciente-alvo: cuidador sem vínculo e sessão vazia não registram nada.
     * (Mantido como estava na Etapa 2A.)
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
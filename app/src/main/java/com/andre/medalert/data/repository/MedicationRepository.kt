package com.andre.medalert.data.repository

import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.local.dao.MedicationDao
import com.andre.medalert.data.local.entity.MedicationEntity
import com.andre.medalert.data.mapper.HorarioDose
import com.andre.medalert.data.mapper.toEntity
import com.andre.medalert.data.mapper.toMedicamentoDataOrNull
import com.andre.medalert.data.mapper.withIsoDates
import com.andre.medalert.data.model.NotificationType
import com.andre.medalert.data.remote.ApiService
import com.andre.medalert.data.remote.RetrofitClient
import com.andre.medalert.data.remote.dto.EditMedicamentoRequest
import com.andre.medalert.data.remote.dto.MedicamentoData
import com.andre.medalert.data.remote.dto.MedicamentoResponse
import com.andre.medalert.data.session.AccessPolicy
import com.andre.medalert.data.session.SessionInfo
import com.andre.medalert.notification.NotificationRecorder
import com.andre.medalert.util.AppLog
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "MedicationRepo"

/**
 * Travas de PROCESSO (não de instância): o MedViewModel e o Worker criam cada um o seu
 * MedicationRepository, então a trava precisa ficar fora da classe para ser compartilhada.
 *
 * - [cadastroMutex]: protege o "procura pendente igual / insere" (curto, só Room).
 * - [envioMutex]: garante que só um fluxo por vez faça POST /api/meds de pendentes
 *   (cadastro e syncPending), evitando enviar o mesmo medicamento duas vezes.
 * - [commitMutex] + [mutationEpoch]: ordenam o refresh em relação a edição e exclusão. A trava
 *   é curta (só Room, nunca durante a rede). Toda edição/exclusão confirmada pela API grava no
 *   Room dentro dela e sobe o contador; o refresh só grava se o contador não mudou desde que
 *   ele começou a buscar na API (senão a resposta pode ser anterior à ação do usuário).
 */
private val cadastroMutex = Mutex()
private val envioMutex = Mutex()
private val commitMutex = Mutex()
private val mutationEpoch = AtomicLong(0)

/** Quantas vezes o refresh refaz a busca quando uma edição/exclusão aconteceu no meio dela. */
private const val MAX_TENTATIVAS_REFRESH = 3

/**
 * Chaves de estoque que NUNCA podem ir ao backend (a coluna não existe lá: Prisma P2022).
 * Barreira extra: o formulário de edição já não monta essas chaves.
 */
private val CAMPOS_FORA_DO_CONTRATO = setOf(
    "estoque", "estoque_minimo", "estoqueMinimo", "estoque_consumido", "estoqueConsumido"
)

/**
 * Cuidador logado que ainda não tem paciente vinculado: não consulta nem altera medicamentos.
 * Este bloqueio fica na camada de dados (e não só nos botões da tela) para que nenhum caminho de
 * código altere medicamentos sem um paciente-dono válido. A API também deve recusar (403).
 */
class NoLinkedPatientException :
    Exception("Vincule-se a um paciente para gerenciar os medicamentos.")

/**
 * Cuidador (mesmo vinculado) tentou CADASTRAR um medicamento. Só o paciente cadastra; o cuidador
 * pode editar e excluir os do paciente vinculado.
 */
class CaregiverCannotAddException :
    Exception("Somente o paciente pode cadastrar medicamentos.")

/**
 * A sessão mudou (logout, outro login, troca de paciente) entre o início e o fim de uma
 * sincronização. A resposta é descartada para não gravar dados de um paciente no outro.
 */
class StaleSessionException :
    Exception("A sessão mudou durante a sincronização; os dados recebidos foram descartados.")

/** Resultado de um cadastro bem-sucedido. */
enum class SaveOutcome {
    /** A API confirmou. */
    SYNCED,

    /** Sem internet: ficou só no celular e será reenviado depois. */
    SAVED_OFFLINE
}

/**
 * Offline-first: a UI observa SEMPRE o Room (observeMedications).
 * A API só alimenta o Room (refresh) ou recebe o que foi salvo localmente.
 *
 * IDs:
 * - patientId = ID do PACIENTE dono dos medicamentos (listar, cadastrar, conferir propriedade):
 *   o próprio paciente, ou o paciente vinculado ao cuidador. NUNCA o ID do cuidador.
 * - entity.userId = patientId do dono (é o `usuario_id` do POST /api/meds).
 * - entity.id = ID LOCAL do Room (a tela usa só este; nunca vai para a API).
 * - entity.remoteId = ID do medicamento na API (editar e excluir).
 *
 * Sincronização (refresh):
 * - paciente: GET /api/meds/{pacienteId};
 * - cuidador: GET /api/meds/cuid/{cuidadorId} (usa o id do CUIDADOR só na URL; os medicamentos
 *   são gravados com o id do PACIENTE vinculado) e guarda usuarios.nome como nome do paciente.
 *
 * Permissões (AccessPolicy):
 * - cadastrar: SÓ o paciente;
 * - editar e excluir: paciente, e cuidador COM vínculo válido, sempre sobre os medicamentos do
 *   paciente vinculado;
 * - cuidador sem vínculo e sessão vazia: nada.
 *
 * Autenticação: o token JWT é anexado pelo interceptor do RetrofitClient. Um 401 numa rota
 * protegida já limpa a sessão e leva o usuário ao login; aqui só traduzimos a mensagem.
 *
 * Caixa de notificações: depois que cadastro, edição ou exclusão são confirmados, o acontecimento
 * é gravado via [NotificationRecorder] (melhor esforço: o gravador nunca lança exceção e nunca
 * altera o resultado da operação). Os registros são deduplicados por chave.
 */
class MedicationRepository(
    private val dao: MedicationDao,
    /** Foto atual da sessão: o DataStore em produção; uma função fixa nos testes. */
    private val sessionProvider: suspend () -> SessionInfo,
    private val api: ApiService = RetrofitClient.instance,
    /** Guarda o nome do paciente vinculado (cuidador). No-op por padrão (testes). */
    private val savePatientName: suspend (patientId: Int, name: String) -> Unit = { _, _ -> }
) {

    constructor(
        dao: MedicationDao,
        userPrefs: UserPreferencesRepository,
        api: ApiService = RetrofitClient.instance
    ) : this(
        dao,
        { userPrefs.session.first() },
        api,
        { patientId, name -> userPrefs.saveLinkedPatientName(patientId, name) }
    )

    /** O que o usuário quer fazer com um medicamento (cada ação tem a sua permissão). */
    private enum class Acao { CADASTRAR, EDITAR, EXCLUIR }

    /** Sessão autorizada para a ação e o paciente dono dos medicamentos. */
    private class WriteAccess(val session: SessionInfo, val patientId: Int)

    /** O que o servidor devolveu, já independente de paciente/cuidador. */
    private class RemoteSnapshot(
        val medications: List<MedicamentoResponse>,
        /** Nome do paciente (só vem no GET do cuidador). */
        val patientName: String?
    )

    /** Resultado interno de uma tentativa de envio de UM pendente. */
    private sealed class Envio {
        /** API confirmou (ou outro fluxo já tinha confirmado). */
        object Enviado : Envio()

        /** Falha de rede: o registro continua pendente. */
        object SemConexao : Envio()

        /** Faltam dados obrigatórios para montar o POST. */
        class Invalido(val motivo: String) : Envio()

        /** O servidor respondeu com erro HTTP. O registro continua pendente. */
        class Rejeitado(val code: Int, val errorBody: String?) : Envio()

        /** Erro inesperado. O registro continua pendente. */
        class Falha(val erro: Exception) : Envio()
    }

    /** A tela observa isto. Funciona sem internet. */
    fun observeMedications(patientId: Int): Flow<List<MedicationEntity>> =
        dao.observeByUser(patientId)

    /**
     * Abre um medicamento (ID LOCAL do Room) para a tela de edição. Falha se a sessão não pode
     * editar medicamentos (cuidador sem vínculo, sem sessão), se o medicamento não existe, se ele
     * é de OUTRO paciente que não o autorizado na sessão, ou se ainda não tem remoteId.
     */
    suspend fun loadForEdit(localId: Long): Result<MedicationEntity> {
        val access = writeAccess(Acao.EDITAR).getOrElse { return Result.failure(it) }
        val entity = dao.getById(localId)
            ?: return Result.failure(Exception("Medicamento não encontrado."))
        if (!podeAlterar(access.session, entity)) {
            return Result.failure(Exception(NAO_PERTENCE_MESSAGE))
        }
        editBlockedReason(entity)?.let { return Result.failure(Exception(it)) }
        return Result.success(entity)
    }

    /** Motivo pelo qual o medicamento ainda não pode ser editado (sem remoteId), ou null se pode. */
    private fun editBlockedReason(entity: MedicationEntity): String? =
        if (entity.remoteId == null) semRemoteIdMessage(entity) else null

    /**
     * Atualiza o Room com os medicamentos do paciente [patientId], pela estratégia da SESSÃO
     * (paciente ou cuidador). Se falhar, o Room fica como está.
     *
     * Recusa ([StaleSessionException]) quando [patientId] não é o paciente da sessão atual, e
     * descarta a resposta se a sessão mudou enquanto a requisição estava em andamento.
     * Uma resposta sem "usuarios"/"medicamentos" (cuidador) é falha, nunca "lista vazia".
     *
     * Ordem com edição/exclusão: se uma edição ou exclusão foi confirmada pela API enquanto este
     * refresh buscava a lista, a resposta pode ser anterior à ação (o medicamento excluído
     * reapareceria, a edição seria desfeita). Nesse caso ela é descartada e a busca é refeita;
     * esgotadas as tentativas, o Room fica como está (ele já reflete a ação do usuário).
     */
    suspend fun refresh(patientId: Int): Result<Unit> {
        val session = sessionProvider()
        val owner = AccessPolicy.patientIdFor(session.tipo, session.userId, session.pacienteId)
        if (owner == null || owner != patientId) {
            return Result.failure(StaleSessionException())
        }

        return try {
            for (tentativa in 1..MAX_TENTATIVAS_REFRESH) {
                val epochAntes = mutationEpoch.get()
                val snapshot = fetchSnapshot(session, patientId)

                // A resposta pode chegar depois de um logout ou de uma troca de vínculo.
                val after = sessionProvider()
                if (after.userId != session.userId ||
                    AccessPolicy.patientIdFor(after.tipo, after.userId, after.pacienteId) != patientId
                ) {
                    return Result.failure(StaleSessionException())
                }

                val gravou = commitMutex.withLock {
                    if (mutationEpoch.get() != epochAntes) {
                        false
                    } else {
                        dao.replaceSynced(patientId, entitiesFrom(snapshot, patientId))
                        true
                    }
                }

                if (gravou) {
                    snapshot.patientName?.let { rememberPatientName(patientId, it) }
                    return Result.success(Unit)
                }

                AppLog.d(TAG, "refresh descartado (edição/exclusão no meio), tentativa $tentativa")
            }

            AppLog.w(TAG, "refresh não gravou: houve alterações a cada tentativa; Room mantido")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(TAG, "Falha ao atualizar a lista", e)
            Result.failure(e)
        }
    }

    /** Atalho: atualiza a lista do PACIENTE da sessão (o próprio paciente, ou o vinculado ao cuidador). */
    suspend fun refreshForCurrentUser(): Result<Unit> {
        val session = sessionProvider()
        val patientId = AccessPolicy.patientIdFor(session.tipo, session.userId, session.pacienteId)

        if (patientId == null) {
            return Result.failure(
                Exception("Sessão expirada ou sem paciente vinculado. Faça login novamente.")
            )
        }

        AppLog.d(TAG, "refreshForCurrentUser($patientId)")

        return refresh(patientId)
    }

    private suspend fun fetchSnapshot(session: SessionInfo, patientId: Int): RemoteSnapshot =
        if (AccessPolicy.isCaregiver(session.tipo)) {
            val cuidadorId = session.userId?.takeIf { it > 0 } ?: throw StaleSessionException()
            val data = api.getMedicamentosByCuidador(cuidadorId).usuarios
                ?: throw IllegalStateException("Resposta do cuidador sem 'usuarios'.")
            val meds = data.medicamentos
                ?: throw IllegalStateException("Resposta do cuidador sem 'medicamentos'.")
            RemoteSnapshot(meds, data.nome)
        } else {
            RemoteSnapshot(api.getMedicamentos(patientId), null)
        }

    /**
     * Resposta -> linhas do Room do PACIENTE. Quando a API não manda "ativo" (o GET do cuidador
     * traz só um subconjunto), mantém o valor que o aparelho já tinha em vez de reativar.
     */
    private suspend fun entitiesFrom(snapshot: RemoteSnapshot, patientId: Int): List<MedicationEntity> {
        val existing = if (snapshot.medications.any { it.ativo == null }) {
            dao.getSyncedByUser(patientId).associateBy { it.remoteId }
        } else {
            emptyMap()
        }

        return snapshot.medications.map { item ->
            val entity = item.toEntity(patientId)
            val previous = item.id?.let { existing[it] }
            if (item.ativo == null && previous != null) entity.copy(ativo = previous.ativo) else entity
        }
    }

    private suspend fun rememberPatientName(patientId: Int, name: String) {
        try {
            savePatientName(patientId, name)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(TAG, "Não foi possível guardar o nome do paciente", e)
        }
    }

    /**
     * Sessão autorizada para a [acao] e o PACIENTE dono dos medicamentos:
     * - CADASTRAR: só paciente logado (ele mesmo); cuidador recebe [CaregiverCannotAddException];
     * - EDITAR / EXCLUIR: paciente logado (ele mesmo) ou cuidador logado com paciente vinculado
     *   (o vinculado).
     * Cuidador sem vínculo recebe [NoLinkedPatientException]; sem sessão, "sessão expirada".
     */
    private suspend fun writeAccess(acao: Acao): Result<WriteAccess> {
        val session = sessionProvider()
        val patientId = when (acao) {
            Acao.CADASTRAR ->
                AccessPolicy.medicationAddOwnerIdFor(session.tipo, session.userId, session.pacienteId)

            Acao.EDITAR ->
                if (AccessPolicy.canEditMedications(session.tipo, session.userId, session.pacienteId)) {
                    AccessPolicy.patientIdFor(session.tipo, session.userId, session.pacienteId)
                } else {
                    null
                }

            Acao.EXCLUIR ->
                if (AccessPolicy.canDeleteMedications(session.tipo, session.userId, session.pacienteId)) {
                    AccessPolicy.patientIdFor(session.tipo, session.userId, session.pacienteId)
                } else {
                    null
                }
        }
        if (patientId != null) return Result.success(WriteAccess(session, patientId))

        return Result.failure(
            when {
                AccessPolicy.isAwaitingLink(session.tipo, session.userId, session.pacienteId) ->
                    NoLinkedPatientException()

                acao == Acao.CADASTRAR &&
                        AccessPolicy.isCaregiver(session.tipo) &&
                        (session.userId ?: 0) > 0 ->
                    CaregiverCannotAddException()

                else -> Exception("Sessão expirada. Faça login novamente.")
            }
        )
    }

    /** O medicamento pertence ao paciente autorizado da sessão (não ao cuidador, nem a outro paciente). */
    private fun podeAlterar(session: SessionInfo, entity: MedicationEntity): Boolean =
        AccessPolicy.canManageMedication(session.tipo, session.userId, session.pacienteId, entity.userId)

    /**
     * Grava no Room o efeito de uma edição/exclusão JÁ confirmada pela API. Roda sob [commitMutex]
     * e sobe [mutationEpoch] ao terminar, para qualquer refresh que começou antes descartar a
     * resposta que buscou. Só Room dentro do bloco: nunca rede (a trava tem que ser curta).
     */
    private suspend fun <T> commitLocal(block: suspend () -> T): T =
        commitMutex.withLock {
            try {
                block()
            } finally {
                mutationEpoch.incrementAndGet()
            }
        }

    // ---------------------------------------------------------------------------
    // Caixa de notificações (melhor esforço; nunca altera o resultado da operação)
    // ---------------------------------------------------------------------------

    private fun doseLine(entity: MedicationEntity): String =
        if (entity.dosagem.isBlank()) entity.nome else "${entity.nome} (${entity.dosagem})"

    /**
     * "Medicamento cadastrado". A chave usa o ID LOCAL (estável), então o reenvio de um pendente
     * idêntico e a sincronização posterior não geram um segundo aviso.
     */
    private suspend fun recordMedicationCreated(localId: Long) {
        val med = dao.getById(localId) ?: return
        NotificationRecorder.record(
            context = null,
            tipo = NotificationType.NEW_MEDICATION,
            titulo = "Medicamento cadastrado",
            descricao = "${doseLine(med)} foi cadastrado.",
            dedupeKey = NotificationRecorder.medicationCreatedKey(med.userId, med.id),
            patientId = med.userId,
            medicationLocalId = med.id,
            medicationRemoteId = med.remoteId,
            medicationNome = med.nome
        )
    }

    /** "Medicamento atualizado". Chamado só depois que a API confirmou e o Room recebeu a edição. */
    private suspend fun recordMedicationEdited(
        localId: Long,
        patientId: Int,
        changes: Map<String, Any?>
    ) {
        val med = dao.getById(localId) ?: return
        NotificationRecorder.record(
            context = null,
            tipo = NotificationType.MEDICATION_EDITED,
            titulo = "Medicamento atualizado",
            descricao = "${doseLine(med)} foi atualizado.",
            dedupeKey = NotificationRecorder.medicationEditedKey(
                patientId = patientId,
                medicationRemoteId = med.remoteId,
                medicationLocalId = med.id,
                changesHash = changes.toSortedMap().toString().hashCode(),
                minuteBucket = System.currentTimeMillis() / 60_000L
            ),
            patientId = patientId,
            medicationLocalId = med.id,
            medicationRemoteId = med.remoteId,
            medicationNome = med.nome
        )
    }

    /** "Medicamento removido". [entity] é a linha lida ANTES da exclusão. */
    private suspend fun recordMedicationRemoved(entity: MedicationEntity) {
        NotificationRecorder.record(
            context = null,
            tipo = NotificationType.MEDICATION_REMOVED,
            titulo = "Medicamento removido",
            descricao = "${doseLine(entity)} foi removido. O histórico de doses foi preservado.",
            dedupeKey = NotificationRecorder.medicationRemovedKey(
                entity.userId, entity.remoteId, entity.id
            ),
            patientId = entity.userId,
            medicationNome = entity.nome
        )
    }

    /**
     * Exclui localmente preservando o histórico (mesma operação de antes) e, só se a linha foi
     * mesmo removida, grava o aviso.
     */
    private suspend fun deleteLocalAndNotify(entity: MedicationEntity, patientId: Int) {
        val removed = commitLocal { dao.deleteKeepingHistoryForUser(entity.id, patientId) }
        if (removed) recordMedicationRemoved(entity)
    }

    /**
     * POST /api/meds (CadastroMedScreen). SÓ O PACIENTE cadastra: o cuidador (mesmo vinculado)
     * recebe [CaregiverCannotAddException] antes de qualquer gravação.
     *
     * Fluxo:
     * 1. O dono REAL é o paciente da sessão; o userId de [draft] é ignorado.
     * 2. Valida horário ("HH:mm") e dosagem (> 0, decimais permitidos).
     * 3. Grava no Room como pendente (isSynced = false). Se já existir um pendente idêntico
     *    (reenvio após falha), reaproveita a mesma linha em vez de criar outra.
     * 4. Envia à API (sob [envioMutex], junto com o syncPending) e, se confirmar, grava o
     *    remoteId na MESMA linha local.
     *
     * Em QUALQUER falha o registro local é mantido como pendente (nada digitado é apagado):
     * - sem internet: devolve SAVED_OFFLINE (sucesso; será reenviado depois);
     * - 401: mensagem de sessão expirada;
     * - outros erros HTTP / inesperados: devolve erro, mas o registro continua no Room.
     *
     * A notificação "Medicamento cadastrado" é gravada nos dois desfechos de sucesso (SYNCED e
     * SAVED_OFFLINE). Se o servidor recusar ou falhar, nada é avisado agora: o aviso sai quando o
     * [syncPending] finalmente enviar o medicamento.
     */
    suspend fun saveMedicationForCurrentUser(draft: MedicamentoData): Result<SaveOutcome> {
        val access = writeAccess(Acao.CADASTRAR).getOrElse { return Result.failure(it) }
        val patientId = access.patientId

        if (!HorarioDose.ehHoraLocal(draft.horarioDose)) {
            return Result.failure(Exception("Informe um horário válido para a primeira dose."))
        }

        if (!draft.dosagem.isFinite() || draft.dosagem <= 0.0) {
            return Result.failure(Exception("Informe uma dosagem válida."))
        }

        val data = draft.copy(userId = patientId)
        val localId = cadastroMutex.withLock { localIdParaCadastro(data.toEntity()) }

        return when (val envio = envioMutex.withLock { enviarPendente(localId) }) {
            is Envio.Enviado -> {
                recordMedicationCreated(localId)
                // Traz da API os dados reais (horários etc.); se falhar, o registro já está salvo.
                refresh(patientId)
                Result.success(SaveOutcome.SYNCED)
            }

            is Envio.SemConexao -> {
                AppLog.w(TAG, "Sem conexão ao salvar medicamento; ficou pendente (id local=$localId)")
                recordMedicationCreated(localId)
                Result.success(SaveOutcome.SAVED_OFFLINE)
            }

            is Envio.Rejeitado -> {
                val mensagem = if (envio.code == 401) {
                    "Sessão expirada. O medicamento foi guardado no celular; entre novamente para enviá-lo."
                } else {
                    mensagemParaHttp(envio.code, envio.errorBody) +
                            " O medicamento ficou salvo no celular como pendente."
                }
                AppLog.w(TAG, "HTTP ${envio.code} ao salvar; mantido como pendente (id local=$localId)")
                Result.failure(Exception(mensagem))
            }

            is Envio.Invalido -> {
                AppLog.e(TAG, "Pendente $localId inválido: ${envio.motivo}")
                Result.failure(
                    Exception("Os dados do medicamento estão incompletos. Ele ficou salvo no celular.")
                )
            }

            is Envio.Falha -> {
                AppLog.e(TAG, "Erro inesperado ao salvar medicamento (id local=$localId)", envio.erro)
                Result.failure(
                    Exception("Não foi possível enviar agora. O medicamento ficou salvo no celular como pendente.")
                )
            }
        }
    }

    /**
     * POST /api/meds/edit com {"id": remoteId, "data": changes}.
     * [changes] usa os nomes da API (snake_case), ex.: mapOf("nome" to "Novo", "frequencia" to 6),
     * e deve conter SÓ os campos alterados e NUNCA valores nulos (o Gson os omitiria).
     * "dosagem" pode ser Int ou Double (ex.: 2.5); o Room recebe como Double.
     * Chaves de estoque são descartadas (o backend não tem essa coluna).
     *
     * Paciente e cuidador vinculado editam; o medicamento precisa ser do paciente da sessão.
     *
     * Fluxo: 1) a API confirma; 2) o Room recebe as mesmas mudanças (id local e remoteId
     * preservados) sob [commitLocal], então a lista já reflete a edição mesmo que o passo 3 falhe e
     * um refresh que estava em andamento não a desfaz; 3) refresh traz da API o que só ela calcula
     * (ex.: horários). Em caso de erro da API, o Room NÃO muda (e nada é avisado).
     */
    suspend fun editMedication(localId: Long, changes: Map<String, Any?>): Result<Unit> {
        val access = writeAccess(Acao.EDITAR).getOrElse { return Result.failure(it) }
        val safeChanges = changes.filterKeys { it !in CAMPOS_FORA_DO_CONTRATO }
        if (safeChanges.isEmpty()) return Result.success(Unit)
        val entity = dao.getById(localId)
            ?: return Result.failure(Exception("Medicamento não encontrado."))
        if (!podeAlterar(access.session, entity)) {
            return Result.failure(Exception(NAO_PERTENCE_MESSAGE))
        }
        val remoteId = entity.remoteId
            ?: return Result.failure(Exception(semRemoteIdMessage(entity)))

        val body = EditMedicamentoRequest(id = remoteId, data = safeChanges)
        return try {
            api.editMedicamento(body).close()

            // Relê a linha dentro da trava: um refresh pode ter rodado enquanto a requisição
            // estava em andamento.
            commitLocal {
                dao.getByIdForUser(localId, access.patientId)?.let {
                    dao.updateForUser(it.applyChanges(safeChanges), access.patientId)
                }
            }

            recordMedicationEdited(localId, access.patientId, safeChanges)

            refresh(entity.userId) // se falhar, o Room já tem a edição; horários atualizam no próximo refresh
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            falha(e, "POST /api/meds/edit", body)
        }
    }

    /**
     * DELETE /api/meds/{remoteId}. Paciente e cuidador vinculado excluem (medicamento do paciente
     * da sessão). Apaga também histórico e horários no backend (cascade).
     * Só remove do Room depois que a API confirmar, sob [commitLocal] (um refresh em andamento não
     * traz o medicamento de volta). As doses JÁ REGISTRADAS no histórico local são preservadas e
     * marcadas como "medicamento excluído" (deleteKeepingHistoryForUser).
     * Se o medicamento nunca foi enviado (criado offline), remove só localmente.
     * 404 = já não existe no servidor: remove local também.
     * Em todos os casos de remoção efetiva, o aviso "Medicamento removido" é gravado.
     */
    suspend fun deleteMedication(localId: Long): Result<Unit> {
        val access = writeAccess(Acao.EXCLUIR).getOrElse { return Result.failure(it) }
        val entity = dao.getById(localId) ?: return Result.success(Unit)
        if (!podeAlterar(access.session, entity)) {
            return Result.failure(Exception(NAO_PERTENCE_MESSAGE))
        }
        val remoteId = entity.remoteId

        if (remoteId == null) {
            if (entity.isSynced) return Result.failure(Exception(semRemoteIdMessage(entity)))
            deleteLocalAndNotify(entity, access.patientId)
            return Result.success(Unit)
        }

        return try {
            api.deleteMedicamento(remoteId).close()
            deleteLocalAndNotify(entity, access.patientId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            if (e.code() == 404) {
                AppLog.w(TAG, "DELETE /api/meds/$remoteId -> 404; removendo também do Room")
                deleteLocalAndNotify(entity, access.patientId)
                Result.success(Unit)
            } else {
                falha(e, "DELETE /api/meds/$remoteId", null)
            }
        } catch (e: Exception) {
            falha(e, "DELETE /api/meds/$remoteId", null)
        }
    }

    /**
     * Reenvia os medicamentos pendentes (isSynced = false) do PACIENTE autorizado na sessão
     * (o próprio paciente, ou o paciente vinculado ao cuidador). Chamado pelo WorkManager.
     *
     * - Sem sessão ou cuidador sem vínculo: não há de quem sincronizar; devolve sucesso sem
     *   enviar nada (os pendentes continuam no Room).
     * - Usa o mesmo [envioMutex] e a mesma função de envio do cadastro, então um medicamento
     *   que acabou de ser enviado pelo cadastro é reconhecido como já sincronizado e pulado.
     * - Só marca como sincronizado após receber um ID remoto válido.
     * - Qualquer falha mantém o registro no Room. Sem conexão interrompe o laço (falha para o
     *   WorkManager tentar de novo); erros HTTP de um item não impedem os demais.
     * - Cada medicamento enviado com sucesso grava (ou já tinha gravado) o aviso "Medicamento
     *   cadastrado"; a chave é por ID local, então nunca duplica.
     *
     * Etapa 2A: o comportamento para pendentes antigos criados por cuidador NÃO foi alterado
     * (decisão pendente, ver relatório).
     */
    suspend fun syncPending(): Result<Unit> {
        val session = sessionProvider()
        val patientId = AccessPolicy.medicationOwnerIdFor(session.tipo, session.userId, session.pacienteId)
            ?: return Result.success(Unit)

        val pendentes = dao.getUnsyncedByUser(patientId)
        if (pendentes.isEmpty()) {
            return Result.success(Unit)
        }

        val erros = mutableListOf<String>()
        var enviados = 0
        var semConexao = false

        envioMutex.withLock {
            loop@ for (pendente in pendentes) {
                val rotulo = "Medicamento '${pendente.nome}' (ID local: ${pendente.id})"

                when (val envio = enviarPendente(pendente.id)) {
                    is Envio.Enviado -> {
                        enviados++
                        recordMedicationCreated(pendente.id)
                    }

                    is Envio.SemConexao -> {
                        semConexao = true
                        break@loop
                    }

                    is Envio.Invalido ->
                        erros.add("$rotulo: ${envio.motivo}.")

                    is Envio.Rejeitado ->
                        erros.add("$rotulo: erro HTTP ${envio.code}.")

                    is Envio.Falha ->
                        erros.add("$rotulo: ${envio.erro.localizedMessage ?: "erro desconhecido"}.")
                }
            }
        }

        if (enviados > 0) {
            refresh(patientId) // traz horários etc.; se falhar, os registros já estão sincronizados
        }

        return when {
            semConexao ->
                Result.failure(IOException("Sem conexão: ${pendentes.size - enviados} medicamento(s) continuam pendentes."))

            erros.isNotEmpty() ->
                Result.failure(
                    IllegalStateException(
                        "Falha ao sincronizar ${erros.size} de ${pendentes.size} medicamento(s):\n" +
                                erros.joinToString("\n")
                    )
                )

            else -> Result.success(Unit)
        }
    }

    // ---------------------------------------------------------------------------
    // Envio de pendentes (usado pelo cadastro e pelo syncPending)
    // ---------------------------------------------------------------------------

    /**
     * Devolve o ID local do pendente idêntico já existente ou insere [novo].
     * Deve ser chamada dentro de [cadastroMutex].
     */
    private suspend fun localIdParaCadastro(novo: MedicationEntity): Long {
        val horario = novo.horarioDose
        val valor = novo.dosagemValor
        val unidade = novo.unidadeDosagem
        val inicio = novo.dataInicio
        val frequencia = novo.frequencia

        if (horario != null && valor != null && unidade != null &&
            inicio != null && frequencia != null
        ) {
            val existente = dao.findPendingEquivalent(
                userId = novo.userId,
                nome = novo.nome,
                dosagemValor = valor,
                unidadeDosagem = unidade,
                horarioDose = horario,
                dataInicio = inicio,
                frequencia = frequencia
            )
            if (existente != null) {
                AppLog.w(TAG, "Pendente idêntico já existe (id local=${existente.id}); reaproveitando")
                return existente.id
            }
        }

        return dao.insert(novo)
    }

    /**
     * Envia UM pendente à API. Deve ser chamada dentro de [envioMutex].
     *
     * Relê a linha do Room: se outro fluxo já a sincronizou (ou ela foi apagada) enquanto
     * esperávamos a trava, não há o que enviar. Nunca apaga nada do Room.
     */
    private suspend fun enviarPendente(localId: Long): Envio {
        val atual = dao.getById(localId) ?: return Envio.Enviado
        if (atual.isSynced) return Envio.Enviado

        val data = atual.toMedicamentoDataOrNull()
            ?: return Envio.Invalido("dados obrigatórios ausentes para sincronização")

        val requestData = data.withIsoDates()

        return try {
            val created = api.createMedicamento(requestData)
            val remoteId = created?.id

            if (remoteId == null) {
                Envio.Falha(IllegalStateException("A API não retornou o ID do medicamento."))
            } else {
                // Grava o remoteId na mesma linha local e remove cópia criada por um refresh concorrente.
                val linhas = dao.confirmSynced(localId, remoteId, atual.userId)
                if (linhas == 0) {
                    Envio.Falha(IllegalStateException("Não foi possível atualizar o medicamento local."))
                } else {
                    Envio.Enviado
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            val body = logHttpError("POST /api/meds", e, requestData)
            Envio.Rejeitado(e.code(), body)
        } catch (e: IOException) {
            Envio.SemConexao
        } catch (e: Exception) {
            Envio.Falha(e)
        }
    }

    private companion object {
        const val NAO_PERTENCE_MESSAGE = "Este medicamento não pertence ao paciente acompanhado."
    }

    private fun semRemoteIdMessage(entity: MedicationEntity): String =
        if (entity.isSynced) {
            "Não foi possível identificar este medicamento no servidor. Atualize a lista e tente de novo."
        } else {
            "Este medicamento ainda não foi enviado ao servidor. Aguarde a sincronização."
        }

    private fun <T> falha(e: Exception, rota: String, enviado: Any?): Result<T> = when (e) {
        is HttpException -> {
            val body = logHttpError(rota, e, enviado)
            Result.failure(Exception(mensagemParaHttp(e.code(), body)))
        }
        is IOException -> Result.failure(Exception("Sem conexão com a internet. Tente novamente."))
        else -> {
            AppLog.e(TAG, "Erro inesperado em $rota", e)
            Result.failure(Exception("Não foi possível concluir a operação. Tente novamente."))
        }
    }

    /**
     * Loga rota, código, errorBody e o JSON enviado (filtre o Logcat por "MedicationRepo").
     * Devolve o errorBody: ele só pode ser lido UMA vez, então quem precisar da mensagem do
     * servidor deve usar este retorno.
     */
    private fun logHttpError(rota: String, e: HttpException, sent: Any?): String? {
        val errorBody = try {
            e.response()?.errorBody()?.string()
        } catch (_: Exception) {
            null
        }
        AppLog.e(TAG, "$rota -> HTTP ${e.code()} | errorBody=$errorBody | enviado=${Gson().toJson(sent)}")
        return errorBody
    }

    private fun mensagemDoServidor(errorBody: String?): String? {
        if (errorBody.isNullOrBlank()) return null
        return try {
            JsonParser.parseString(errorBody).asJsonObject.get("message")
                ?.takeIf { it.isJsonPrimitive }?.asString
        } catch (_: Exception) {
            null
        }
    }

    private fun mensagemParaHttp(code: Int, errorBody: String? = null): String = when (code) {
        401 -> "Sessão expirada. Faça login novamente."
        403 -> "Você não tem permissão para alterar este medicamento."
        404 -> "Medicamento não encontrado no servidor. Atualize a lista e tente novamente."
        400, 422 -> mensagemDoServidor(errorBody)
            ?.let { "O servidor recusou a operação: $it" }
            ?: "O servidor recusou a operação. Confira os dados e tente novamente."
        in 500..599 -> "Erro no servidor ($code). Tente novamente em instantes."
        else -> "Erro no servidor ($code). Tente novamente em instantes."
    }
}
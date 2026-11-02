package com.andre.medalert.data.repository

import android.util.Log
import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.local.dao.MedicationDao
import com.andre.medalert.data.local.entity.MedicationEntity
import com.andre.medalert.data.mapper.HorarioDose
import com.andre.medalert.data.mapper.formatarNumero
import com.andre.medalert.data.mapper.toEntity
import com.andre.medalert.data.mapper.toMedicamentoDataOrNull
import com.andre.medalert.data.mapper.withIsoDates
import com.andre.medalert.data.remote.ApiService
import com.andre.medalert.data.remote.RetrofitClient
import com.andre.medalert.data.remote.dto.EditMedicamentoRequest
import com.andre.medalert.data.remote.dto.MedicamentoData
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException

private const val TAG = "MedicationRepo"

/**
 * Travas de PROCESSO (não de instância): o MedViewModel e o Worker criam cada um o seu
 * MedicationRepository, então a trava precisa ficar fora da classe para ser compartilhada.
 *
 * - [cadastroMutex]: protege o "procura pendente igual / insere" (curto, só Room).
 * - [envioMutex]: garante que só um fluxo por vez faça POST /api/meds de pendentes
 *   (cadastro e syncPending), evitando enviar o mesmo medicamento duas vezes.
 */
private val cadastroMutex = Mutex()
private val envioMutex = Mutex()

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
 * - userId  = ID do paciente (listar e cadastrar).
 * - entity.id = ID LOCAL do Room (a tela usa só este).
 * - entity.remoteId = ID do medicamento na API (editar e excluir).
 *
 * Autenticação: o token JWT é anexado pelo interceptor do RetrofitClient. Um 401 numa rota
 * protegida já limpa a sessão e leva o usuário ao login; aqui só traduzimos a mensagem.
 */
class MedicationRepository(
    private val dao: MedicationDao,
    private val userPrefs: UserPreferencesRepository,
    private val api: ApiService = RetrofitClient.instance
) {

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
    fun observeMedications(userId: Int): Flow<List<MedicationEntity>> =
        dao.observeByUser(userId)

    /** Lê um medicamento do Room pelo ID LOCAL (usado pela tela de edição). */
    suspend fun getMedication(localId: Long): MedicationEntity? = dao.getById(localId)

    /** Motivo pelo qual o medicamento ainda não pode ser editado (sem remoteId), ou null se pode. */
    fun editBlockedReason(entity: MedicationEntity): String? =
        if (entity.remoteId == null) semRemoteIdMessage(entity) else null

    /** GET /api/meds/{userId} e atualiza o Room. Se falhar, o Room fica como está. */
    suspend fun refresh(userId: Int): Result<Unit> =
        try {
            val remote = api.getMedicamentos(userId)
            dao.replaceSynced(userId, remote.map { it.toEntity(userId) })
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao atualizar a lista", e)
            Result.failure(e)
        }

    /** Atalho: atualiza a lista do usuário que está na sessão. */
    suspend fun refreshForCurrentUser(): Result<Unit> {
        val userId = userPrefs.userId.first()

        if (userId == null || userId <= 0) {
            return Result.failure(
                Exception("Sessão expirada. Faça login novamente.")
            )
        }

        Log.d(TAG, "refreshForCurrentUser($userId)")

        return refresh(userId)
    }

    /**
     * POST /api/meds (CadastroMedScreen).
     *
     * Fluxo:
     * 1. O userId REAL vem do DataStore (o userId de [draft] é ignorado).
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
     */
    suspend fun saveMedicationForCurrentUser(draft: MedicamentoData): Result<SaveOutcome> {
        val userId = userPrefs.userId.first()
        if (userId == null || userId <= 0) {
            return Result.failure(Exception("Sessão expirada. Faça login novamente."))
        }

        if (!HorarioDose.ehHoraLocal(draft.horarioDose)) {
            return Result.failure(Exception("Informe um horário válido para a primeira dose."))
        }

        if (!draft.dosagem.isFinite() || draft.dosagem <= 0.0) {
            return Result.failure(Exception("Informe uma dosagem válida."))
        }

        val data = draft.copy(userId = userId)
        val localId = cadastroMutex.withLock { localIdParaCadastro(data.toEntity()) }

        return when (val envio = envioMutex.withLock { enviarPendente(localId) }) {
            is Envio.Enviado -> {
                // Traz da API os dados reais (horários etc.); se falhar, o registro já está salvo.
                refresh(userId)
                Result.success(SaveOutcome.SYNCED)
            }

            is Envio.SemConexao -> {
                Log.w(TAG, "Sem conexão ao salvar medicamento; ficou pendente (id local=$localId)")
                Result.success(SaveOutcome.SAVED_OFFLINE)
            }

            is Envio.Rejeitado -> {
                val mensagem = if (envio.code == 401) {
                    "Sessão expirada. O medicamento foi guardado no celular; entre novamente para enviá-lo."
                } else {
                    mensagemParaHttp(envio.code, envio.errorBody) +
                            " O medicamento ficou salvo no celular como pendente."
                }
                Log.w(TAG, "HTTP ${envio.code} ao salvar; mantido como pendente (id local=$localId)")
                Result.failure(Exception(mensagem))
            }

            is Envio.Invalido -> {
                Log.e(TAG, "Pendente $localId inválido: ${envio.motivo}")
                Result.failure(
                    Exception("Os dados do medicamento estão incompletos. Ele ficou salvo no celular.")
                )
            }

            is Envio.Falha -> {
                Log.e(TAG, "Erro inesperado ao salvar medicamento (id local=$localId)", envio.erro)
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
     *
     * Fluxo: 1) a API confirma; 2) o Room recebe as mesmas mudanças (id local e remoteId
     * preservados), então a lista já reflete a edição mesmo que o passo 3 falhe; 3) refresh
     * traz da API o que só ela calcula (ex.: horários). Em caso de erro da API, o Room NÃO muda.
     */
    suspend fun editMedication(localId: Long, changes: Map<String, Any?>): Result<Unit> {
        if (changes.isEmpty()) return Result.success(Unit)
        val entity = dao.getById(localId)
            ?: return Result.failure(Exception("Medicamento não encontrado."))
        val remoteId = entity.remoteId
            ?: return Result.failure(Exception(semRemoteIdMessage(entity)))

        val body = EditMedicamentoRequest(id = remoteId, data = changes)
        return try {
            api.editMedicamento(body).close()

            // Relê a linha: um refresh pode ter rodado enquanto a requisição estava em andamento.
            dao.getById(localId)?.let { dao.update(it.applyChanges(changes)) }

            refresh(entity.userId) // se falhar, o Room já tem a edição; horários atualizam no próximo refresh
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            falha(e, "POST /api/meds/edit", body)
        }
    }

    /**
     * DELETE /api/meds/{remoteId}. Apaga também histórico e horários no backend (cascade).
     * Só remove do Room depois que a API confirmar. Se o medicamento nunca foi enviado
     * (criado offline), remove só localmente. 404 = já não existe no servidor: remove local também.
     */
    suspend fun deleteMedication(localId: Long): Result<Unit> {
        val entity = dao.getById(localId) ?: return Result.success(Unit)
        val remoteId = entity.remoteId

        if (remoteId == null) {
            if (entity.isSynced) return Result.failure(Exception(semRemoteIdMessage(entity)))
            dao.deleteById(localId)
            return Result.success(Unit)
        }

        return try {
            api.deleteMedicamento(remoteId).close()
            dao.deleteById(localId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            if (e.code() == 404) {
                Log.w(TAG, "DELETE /api/meds/$remoteId -> 404; removendo também do Room")
                dao.deleteById(localId)
                Result.success(Unit)
            } else {
                falha(e, "DELETE /api/meds/$remoteId", null)
            }
        } catch (e: Exception) {
            falha(e, "DELETE /api/meds/$remoteId", null)
        }
    }

    /**
     * Reenvia os medicamentos pendentes (isSynced = false) DO USUÁRIO DA SESSÃO.
     * Chamado pelo WorkManager.
     *
     * - Sem sessão: não há de quem sincronizar; devolve sucesso sem enviar nada.
     * - Usa o mesmo [envioMutex] e a mesma função de envio do cadastro, então um medicamento
     *   que acabou de ser enviado pelo cadastro é reconhecido como já sincronizado e pulado.
     * - Só marca como sincronizado após receber um ID remoto válido.
     * - Qualquer falha mantém o registro no Room. Sem conexão interrompe o laço (falha para o
     *   WorkManager tentar de novo); erros HTTP de um item não impedem os demais.
     */
    suspend fun syncPending(): Result<Unit> {
        val userId = userPrefs.userId.first()
        if (userId == null || userId <= 0) {
            Log.w(TAG, "syncPending sem sessão: nada a sincronizar")
            return Result.success(Unit)
        }

        val pendentes = dao.getUnsyncedByUser(userId)
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
                    is Envio.Enviado -> enviados++

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
            refresh(userId) // traz horários etc.; se falhar, os registros já estão sincronizados
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
                Log.w(TAG, "Pendente idêntico já existe (id local=${existente.id}); reaproveitando")
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
            Log.e(TAG, "Erro inesperado em $rota", e)
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
        Log.e(TAG, "$rota -> HTTP ${e.code()} | errorBody=$errorBody | enviado=${Gson().toJson(sent)}")
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

// ---------------------------------------------------------------------------
// Aplica no Room as mesmas mudanças que a API acabou de confirmar
// ---------------------------------------------------------------------------

private fun Map<String, Any?>.int(key: String): Int? = (this[key] as? Number)?.toInt()

/** Aceita Int, Long ou Double no mapa (ex.: "dosagem" = 2.5); preserva os decimais. */
private fun Map<String, Any?>.double(key: String): Double? = (this[key] as? Number)?.toDouble()

/**
 * Data nova vem da API como "yyyy-MM-ddT00:00:00.000Z". Se o registro local guarda a hora da
 * primeira dose ("yyyy-MM-dd HH:mm:ss-03:00"), troca só o dia e mantém a hora.
 */
private fun mergeDate(localAtual: String?, novoIso: String): String {
    val dia = novoIso.take(10)
    val sufixo = localAtual?.takeIf { it.length > 10 && it[10] == ' ' }?.substring(10)
    return if (sufixo != null) dia + sufixo else novoIso
}

private fun MedicationEntity.applyChanges(c: Map<String, Any?>): MedicationEntity {
    var e = this
    (c["nome"] as? String)?.let { e = e.copy(nome = it) }
    c.double("dosagem")?.let { e = e.copy(dosagemValor = it) }
    (c["unidade_dosagem"] as? String)?.let { e = e.copy(unidadeDosagem = it) }
    if (c.containsKey("dosagem") || c.containsKey("unidade_dosagem")) {
        val v = e.dosagemValor
        val u = e.unidadeDosagem
        if (v != null && u != null) e = e.copy(dosagem = "${v.formatarNumero()} $u")
    }
    c.int("quantidade_por_dose")?.let { e = e.copy(quantidade = it) }
    (c["unidade_quantidade"] as? String)?.let { e = e.copy(unidadeQuantidade = it) }
    c.int("frequencia")?.let { e = e.copy(frequencia = it) }
    (c["data_inicio"] as? String)?.let { e = e.copy(dataInicio = mergeDate(e.dataInicio, it)) }
    (c["data_fim"] as? String)?.let { e = e.copy(dataFim = mergeDate(e.dataFim, it)) }
    (c["horario_dose"] as? String)?.let { e = e.copy(horarioDose = HorarioDose.normalizar(it) ?: it) }
    if (c.containsKey("estoque")) e = e.copy(estoque = c.int("estoque"))
    c.int("estoque_minimo")?.let { e = e.copy(estoqueMinimo = it) }
    if (c.containsKey("observacoes")) e = e.copy(observacoes = c["observacoes"] as? String)
    (c["ativo"] as? Boolean)?.let { e = e.copy(ativo = it) }
    return e
}
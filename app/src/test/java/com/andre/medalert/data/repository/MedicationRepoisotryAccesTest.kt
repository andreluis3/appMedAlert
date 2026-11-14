package com.andre.medalert.data.repository

import com.andre.medalert.data.local.dao.MedicationDao
import com.andre.medalert.data.local.entity.MedicationEntity
import com.andre.medalert.data.remote.ApiService
import com.andre.medalert.data.remote.dto.AcceptPacienteRequest
import com.andre.medalert.data.remote.dto.CreateCuidadorRequest
import com.andre.medalert.data.remote.dto.DashboardResponse
import com.andre.medalert.data.remote.dto.DoseTakenRequest
import com.andre.medalert.data.remote.dto.EditMedicamentoRequest
import com.andre.medalert.data.remote.dto.HistoryQueryRequest
import com.andre.medalert.data.remote.dto.HistoryRecordDto
import com.andre.medalert.data.remote.dto.LoginRequest
import com.andre.medalert.data.remote.dto.MedicamentoCriadoDto
import com.andre.medalert.data.remote.dto.MedicamentoData
import com.andre.medalert.data.remote.dto.MedicamentoResponse
import com.andre.medalert.data.remote.dto.RequestCuidadorRequest
import com.andre.medalert.data.remote.dto.SaveFcmTokenRequest
import com.andre.medalert.data.remote.dto.UserData
import com.andre.medalert.data.session.SessionInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

/**
 * Autorização e CRUD compartilhado paciente x cuidador no MedicationRepository, com Room e API
 * falsos em memória (sem Android, sem rede).
 */
class MedicationRepositoryAccessTest {

    // ------------------------------------------------------------------ falsos

    private class FakeMedicationDao : MedicationDao() {
        val rows = mutableListOf<MedicationEntity>()
        private var nextId = 1L

        override fun observeByUser(userId: Int): Flow<List<MedicationEntity>> =
            flowOf(rows.filter { it.userId == userId })

        override suspend fun getById(id: Long) = rows.firstOrNull { it.id == id }
        override suspend fun getUnsynced() = rows.filter { !it.isSynced }
        override suspend fun getUnsyncedByUser(userId: Int) =
            rows.filter { it.userId == userId && !it.isSynced }.sortedBy { it.id }

        override suspend fun findPendingEquivalent(
            userId: Int, nome: String, dosagemValor: Double, unidadeDosagem: String,
            horarioDose: String, dataInicio: String, frequencia: Int
        ) = rows.firstOrNull {
            it.userId == userId && !it.isSynced && it.nome.equals(nome, ignoreCase = true) &&
                    it.dosagemValor == dosagemValor && it.unidadeDosagem == unidadeDosagem &&
                    it.horarioDose == horarioDose && it.dataInicio == dataInicio &&
                    it.frequencia == frequencia
        }

        override suspend fun getSyncedByUser(userId: Int) =
            rows.filter { it.userId == userId && it.isSynced }

        override suspend fun insert(entity: MedicationEntity): Long {
            val id = nextId++
            rows += entity.copy(id = id)
            return id
        }

        override suspend fun insertAll(entities: List<MedicationEntity>) {
            entities.forEach { insert(it) }
        }

        override suspend fun update(entity: MedicationEntity) {
            val i = rows.indexOfFirst { it.id == entity.id }
            if (i >= 0) rows[i] = entity
        }

        override suspend fun upsertAll(entities: List<MedicationEntity>) {
            entities.forEach { e ->
                val i = rows.indexOfFirst { it.id == e.id }
                when {
                    e.id == 0L -> insert(e)
                    i >= 0 -> rows[i] = e
                    else -> rows += e
                }
            }
        }

        override suspend fun markSynced(id: Long) {
            val i = rows.indexOfFirst { it.id == id }
            if (i >= 0) rows[i] = rows[i].copy(isSynced = true)
        }

        override suspend fun markSyncedWithRemote(id: Long, remoteId: Int): Int {
            val i = rows.indexOfFirst { it.id == id }
            if (i < 0) return 0
            rows[i] = rows[i].copy(isSynced = true, remoteId = remoteId)
            return 1
        }

        override suspend fun deleteOthersWithRemoteId(userId: Int, remoteId: Int, keepId: Long) {
            rows.removeAll { it.userId == userId && it.remoteId == remoteId && it.id != keepId }
        }

        override suspend fun deleteById(id: Long) {
            rows.removeAll { it.id == id }
        }

        override suspend fun markHistoryRemoved(localIds: List<Long>) {}

        override suspend fun deleteByIds(ids: List<Long>) {
            rows.removeAll { it.id in ids }
        }

        override suspend fun deleteSyncedByUser(userId: Int) {
            rows.removeAll { it.userId == userId && it.isSynced }
        }
    }

    /** Servidor em memória: guarda os medicamentos por paciente (usuario_id). */
    private class FakeServer {
        val byPatient = mutableMapOf<Int, MutableList<MedicamentoResponse>>()
        val created = mutableListOf<MedicamentoData>()
        val edits = mutableListOf<EditMedicamentoRequest>()
        val deleted = mutableListOf<Int>()
        var offline = false
        private var nextRemoteId = 100

        fun api(): ApiService = object : ApiService {
            override suspend fun getMedicamentos(usuarioId: Int): List<MedicamentoResponse> {
                if (offline) throw IOException("sem rede")
                return byPatient[usuarioId].orEmpty().toList()
            }

            override suspend fun createMedicamento(request: MedicamentoData): MedicamentoCriadoDto? {
                if (offline) throw IOException("sem rede")
                created += request
                val id = nextRemoteId++
                byPatient.getOrPut(request.userId) { mutableListOf() } += MedicamentoResponse(
                    id = id,
                    nome = request.nome,
                    dosagem = "${request.dosagem.toInt()} ${request.unidadeDosagem}",
                    dosagemValor = request.dosagem,
                    unidadeDosagem = request.unidadeDosagem,
                    quantidade = request.quantidade,
                    unidadeQuantidade = request.unidadeQuantidade,
                    frequencia = request.frequencia,
                    dataInicio = request.dataInicio,
                    dataFim = request.dataFim,
                    ativo = true,
                    horarioDose = request.horarioDose
                )
                return MedicamentoCriadoDto(id = id)
            }

            override suspend fun editMedicamento(request: EditMedicamentoRequest): ResponseBody {
                if (offline) throw IOException("sem rede")
                edits += request
                byPatient.values.forEach { list ->
                    val i = list.indexOfFirst { it.id == request.id }
                    if (i >= 0) {
                        list[i] = list[i].copy(nome = (request.data["nome"] as? String) ?: list[i].nome)
                    }
                }
                return "".toResponseBody(null)
            }

            override suspend fun deleteMedicamento(medicamentoId: Int): ResponseBody {
                if (offline) throw IOException("sem rede")
                deleted += medicamentoId
                byPatient.values.forEach { list -> list.removeAll { it.id == medicamentoId } }
                return "".toResponseBody(null)
            }

            override suspend fun createUser(request: UserData): ResponseBody = TODO()
            override suspend fun login(request: LoginRequest): ResponseBody = TODO()
            override suspend fun createCuidador(request: CreateCuidadorRequest): ResponseBody = TODO()
            override suspend fun saveFcmToken(request: SaveFcmTokenRequest): ResponseBody = TODO()
            override suspend fun requestCuidador(request: RequestCuidadorRequest): ResponseBody = TODO()
            override suspend fun acceptPaciente(request: AcceptPacienteRequest): ResponseBody = TODO()
            override suspend fun createHistory(request: DoseTakenRequest): Response<ResponseBody> = TODO()
            override suspend fun getDosesDia(usuarioId: Int): List<HistoryRecordDto> = TODO()

            @Suppress("OVERRIDE_DEPRECATION")
            override suspend fun getHistory(request: HistoryQueryRequest): List<HistoryRecordDto> = TODO()
            override suspend fun getDashboard(userId: Int): DashboardResponse = TODO()
        }
    }

    // ------------------------------------------------------------------ auxiliares

    /** Roda o corpo suspenso e devolve Unit (JUnit exige métodos de teste void). */
    private fun runTest(block: suspend () -> Unit) {
        runBlocking { block() }
    }

    private val paciente9 = SessionInfo(userId = 9, tipo = "paciente", pacienteId = null)
    private val cuidador7Vinculado = SessionInfo(userId = 7, tipo = "cuidador", pacienteId = 9)
    private val cuidador7SemVinculo = SessionInfo(userId = 7, tipo = "cuidador", pacienteId = null)
    private val semSessao = SessionInfo(userId = null, tipo = "paciente", pacienteId = null)

    private fun repo(dao: FakeMedicationDao, server: FakeServer, session: SessionInfo) =
        MedicationRepository(dao, { session }, server.api())

    /** O userId do rascunho é propositalmente errado: o repositório deve ignorá-lo. */
    private fun draft(nome: String = "Dipirona") = MedicamentoData(
        nome = nome,
        dataInicio = "2026-11-13",
        dataFim = null,
        dosagem = 500.0,
        frequencia = 8,
        quantidade = 1,
        unidadeDosagem = "mg",
        unidadeQuantidade = "comprimido",
        userId = 999,
        horarioDose = "08:00"
    )

    private fun entity(userId: Int, remoteId: Int?, synced: Boolean = remoteId != null) = MedicationEntity(
        userId = userId,
        nome = "Existente",
        dosagem = "500 mg",
        horarioDose = "08:00",
        dataInicio = "2026-11-13",
        dosagemValor = 500.0,
        frequencia = 8,
        quantidade = 1,
        unidadeDosagem = "mg",
        unidadeQuantidade = "comprimido",
        isSynced = synced,
        remoteId = remoteId
    )

    private suspend fun seed(dao: FakeMedicationDao, e: MedicationEntity): Long = dao.insert(e)

    // ------------------------------------------------------------------ cadastro

    @Test
    fun paciente_cadastra_comOProprioId() = runTest {
        val dao = FakeMedicationDao(); val server = FakeServer()
        val r = repo(dao, server, paciente9).saveMedicationForCurrentUser(draft())
        assertEquals(SaveOutcome.SYNCED, r.getOrThrow())
        assertEquals(listOf(9), server.created.map { it.userId })
        assertEquals(1, dao.rows.size)
        assertEquals(9, dao.rows.single().userId)
    }

    @Test
    fun cuidadorVinculado_cadastra_comOIdDoPaciente_nuncaOIdDoCuidador() = runTest {
        val dao = FakeMedicationDao(); val server = FakeServer()
        val r = repo(dao, server, cuidador7Vinculado).saveMedicationForCurrentUser(draft())
        assertEquals(SaveOutcome.SYNCED, r.getOrThrow())
        assertEquals(listOf(9), server.created.map { it.userId })
        assertTrue(dao.rows.none { it.userId == 7 || it.userId == 999 })
        val row = dao.rows.single()
        assertEquals(9, row.userId)
        assertEquals(100, row.remoteId)
        assertTrue(row.isSynced)
    }

    @Test
    fun cuidadorSemVinculo_naoCadastra() = runTest {
        val dao = FakeMedicationDao(); val server = FakeServer()
        val r = repo(dao, server, cuidador7SemVinculo).saveMedicationForCurrentUser(draft())
        assertTrue(r.exceptionOrNull() is NoLinkedPatientException)
        assertTrue(dao.rows.isEmpty())
        assertTrue(server.created.isEmpty())
    }

    @Test
    fun semSessao_naoCadastra() = runTest {
        val dao = FakeMedicationDao(); val server = FakeServer()
        val r = repo(dao, server, semSessao).saveMedicationForCurrentUser(draft())
        assertTrue(r.isFailure)
        assertFalse(r.exceptionOrNull() is NoLinkedPatientException)
        assertTrue(dao.rows.isEmpty())
        assertTrue(server.created.isEmpty())
    }

    @Test
    fun medicamentoDoCuidador_aparecePraOPaciente_semDuplicar() = runTest {
        val server = FakeServer()
        val daoCuidador = FakeMedicationDao()
        val daoPaciente = FakeMedicationDao()
        repo(daoCuidador, server, cuidador7Vinculado).saveMedicationForCurrentUser(draft()).getOrThrow()

        val repoPaciente = repo(daoPaciente, server, paciente9)
        repoPaciente.refreshForCurrentUser().getOrThrow()
        repoPaciente.refreshForCurrentUser().getOrThrow() // refresh repetido não duplica

        assertEquals(1, daoPaciente.rows.size)
        assertEquals(9, daoPaciente.rows.single().userId)
        assertEquals(100, daoPaciente.rows.single().remoteId)
        assertEquals(1, daoCuidador.rows.size)
    }

    // ------------------------------------------------------------------ edição

    @Test
    fun cuidadorVinculado_edita_usandoORemoteId_nuncaOIdLocal() = runTest {
        val dao = FakeMedicationDao(); val server = FakeServer()
        // ID local 1 e remoto 55 diferentes de propósito.
        server.byPatient[9] = mutableListOf(MedicamentoResponse(id = 55, nome = "Antigo", dosagem = "500 mg"))
        dao.insert(entity(userId = 9, remoteId = null, synced = false)) // ocupa o id local 1
        val localId = seed(dao, entity(userId = 9, remoteId = 55))
        assertNotEquals(55L, localId)

        val r = repo(dao, server, cuidador7Vinculado).editMedication(localId, mapOf("nome" to "Novo"))
        r.getOrThrow()
        assertEquals(1, server.edits.size)
        assertEquals(55, server.edits.single().id)
        assertEquals("Novo", dao.getById(localId)!!.nome)
    }

    @Test
    fun paciente_edita_medicamentoProprio() = runTest {
        val dao = FakeMedicationDao(); val server = FakeServer()
        server.byPatient[9] = mutableListOf(MedicamentoResponse(id = 55, nome = "Antigo", dosagem = "500 mg"))
        val localId = seed(dao, entity(userId = 9, remoteId = 55))
        repo(dao, server, paciente9).editMedication(localId, mapOf("nome" to "Novo")).getOrThrow()
        assertEquals(55, server.edits.single().id)
    }

    @Test
    fun cuidadorVinculado_naoEditaMedicamentoDeOutroPaciente() = runTest {
        val dao = FakeMedicationDao(); val server = FakeServer()
        val outro = seed(dao, entity(userId = 26, remoteId = 77))
        val r = repo(dao, server, cuidador7Vinculado).editMedication(outro, mapOf("nome" to "X"))
        assertTrue(r.isFailure)
        assertTrue(server.edits.isEmpty())
        assertEquals("Existente", dao.getById(outro)!!.nome)
    }

    @Test
    fun cuidadorSemVinculo_naoEdita_nemMedicamentoQueSobrouNoRoom() = runTest {
        val dao = FakeMedicationDao(); val server = FakeServer()
        val localId = seed(dao, entity(userId = 9, remoteId = 55))
        val r = repo(dao, server, cuidador7SemVinculo).editMedication(localId, mapOf("nome" to "X"))
        assertTrue(r.exceptionOrNull() is NoLinkedPatientException)
        assertTrue(server.edits.isEmpty())
    }

    @Test
    fun edicao_semRemoteId_naoVaiParaAApi() = runTest {
        val dao = FakeMedicationDao(); val server = FakeServer()
        val localId = seed(dao, entity(userId = 9, remoteId = null, synced = false))
        val r = repo(dao, server, cuidador7Vinculado).editMedication(localId, mapOf("nome" to "X"))
        assertTrue(r.isFailure)
        assertTrue(server.edits.isEmpty())
    }

    @Test
    fun loadForEdit_respeitaVinculoPropriedadeERemoteId() = runTest {
        val dao = FakeMedicationDao(); val server = FakeServer()
        val meu = seed(dao, entity(userId = 9, remoteId = 55))
        val deOutro = seed(dao, entity(userId = 26, remoteId = 77))
        val pendente = seed(dao, entity(userId = 9, remoteId = null, synced = false))

        val vinculado = repo(dao, server, cuidador7Vinculado)
        assertEquals(meu, vinculado.loadForEdit(meu).getOrThrow().id)
        assertTrue(vinculado.loadForEdit(deOutro).isFailure)
        assertTrue(vinculado.loadForEdit(pendente).isFailure)
        assertTrue(vinculado.loadForEdit(12345L).isFailure)

        assertTrue(repo(dao, server, cuidador7SemVinculo).loadForEdit(meu).exceptionOrNull() is NoLinkedPatientException)
        assertTrue(repo(dao, server, semSessao).loadForEdit(meu).isFailure)
    }

    // ------------------------------------------------------------------ exclusão

    @Test
    fun cuidadorVinculado_exclui_usandoORemoteId() = runTest {
        val dao = FakeMedicationDao(); val server = FakeServer()
        server.byPatient[9] = mutableListOf(MedicamentoResponse(id = 55, nome = "Antigo", dosagem = "500 mg"))
        dao.insert(entity(userId = 9, remoteId = null, synced = false)) // ocupa o id local 1
        val localId = seed(dao, entity(userId = 9, remoteId = 55))

        repo(dao, server, cuidador7Vinculado).deleteMedication(localId).getOrThrow()
        assertEquals(listOf(55), server.deleted)
        assertNull(dao.getById(localId))
    }

    @Test
    fun cuidadorSemVinculo_naoExclui() = runTest {
        val dao = FakeMedicationDao(); val server = FakeServer()
        val localId = seed(dao, entity(userId = 9, remoteId = 55))
        val r = repo(dao, server, cuidador7SemVinculo).deleteMedication(localId)
        assertTrue(r.exceptionOrNull() is NoLinkedPatientException)
        assertTrue(server.deleted.isEmpty())
        assertTrue(dao.getById(localId) != null)
    }

    @Test
    fun cuidadorVinculado_naoExcluiMedicamentoDeOutroPaciente() = runTest {
        val dao = FakeMedicationDao(); val server = FakeServer()
        val outro = seed(dao, entity(userId = 26, remoteId = 77))
        val r = repo(dao, server, cuidador7Vinculado).deleteMedication(outro)
        assertTrue(r.isFailure)
        assertTrue(server.deleted.isEmpty())
        assertTrue(dao.getById(outro) != null)
    }

    @Test
    fun pendenteNuncaEnviado_excluiSoLocalmente_semChamarAApi() = runTest {
        val dao = FakeMedicationDao(); val server = FakeServer()
        val localId = seed(dao, entity(userId = 9, remoteId = null, synced = false))
        repo(dao, server, cuidador7Vinculado).deleteMedication(localId).getOrThrow()
        assertTrue(server.deleted.isEmpty())
        assertTrue(dao.rows.isEmpty())
    }

    // ------------------------------------------------------------------ offline e sincronização

    @Test
    fun offline_cuidadorCadastra_ficaPendenteComODonoPaciente_eSobeDepois_semDuplicar() = runTest {
        val dao = FakeMedicationDao(); val server = FakeServer()
        server.offline = true

        val cuidador = repo(dao, server, cuidador7Vinculado)
        assertEquals(SaveOutcome.SAVED_OFFLINE, cuidador.saveMedicationForCurrentUser(draft()).getOrThrow())

        val pendente = dao.rows.single()
        assertEquals(9, pendente.userId)
        assertFalse(pendente.isSynced)
        assertNull(pendente.remoteId)

        // Refresh sem rede falha e não mexe no Room; com rede e servidor vazio não apaga o pendente.
        assertTrue(cuidador.refresh(9).isFailure)
        server.offline = false
        cuidador.refresh(9).getOrThrow()
        assertEquals(1, dao.rows.size)
        assertFalse(dao.rows.single().isSynced)

        // Cuidador SEM vínculo não envia nem apaga nada.
        repo(dao, server, cuidador7SemVinculo).syncPending().getOrThrow()
        assertTrue(server.created.isEmpty())
        assertEquals(1, dao.rows.size)

        // Cuidador vinculado envia, com o paciente como dono, e a linha local ganha o remoteId.
        cuidador.syncPending().getOrThrow()
        assertEquals(listOf(9), server.created.map { it.userId })
        assertEquals(1, dao.rows.size)
        assertTrue(dao.rows.single().isSynced)
        assertEquals(100, dao.rows.single().remoteId)

        // Rodar de novo não cria nada.
        cuidador.syncPending().getOrThrow()
        assertEquals(1, server.created.size)
        assertEquals(1, dao.rows.size)
    }

    @Test
    fun offline_mesmoCadastroDuasVezes_geraUmSoPendente() = runTest {
        val dao = FakeMedicationDao(); val server = FakeServer()
        server.offline = true
        val r = repo(dao, server, cuidador7Vinculado)
        r.saveMedicationForCurrentUser(draft()).getOrThrow()
        r.saveMedicationForCurrentUser(draft()).getOrThrow()
        assertEquals(1, dao.rows.size)
    }

    @Test
    fun syncPending_semSessao_naoEnviaNada() = runTest {
        val dao = FakeMedicationDao(); val server = FakeServer()
        seed(dao, entity(userId = 9, remoteId = null, synced = false))
        repo(dao, server, semSessao).syncPending().getOrThrow()
        assertTrue(server.created.isEmpty())
        assertEquals(1, dao.rows.size)
    }

    @Test
    fun syncPending_soEnviaOsPendentesDoPacienteAutorizado() = runTest {
        val dao = FakeMedicationDao(); val server = FakeServer()
        seed(dao, entity(userId = 9, remoteId = null, synced = false))
        seed(dao, entity(userId = 26, remoteId = null, synced = false)) // de outro paciente
        repo(dao, server, cuidador7Vinculado).syncPending().getOrThrow()
        assertEquals(listOf(9), server.created.map { it.userId })
        assertEquals(1, dao.rows.count { !it.isSynced && it.userId == 26 })
    }
}
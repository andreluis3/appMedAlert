package com.andre.medalert.data.repository

import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.model.NotificationType
import com.andre.medalert.data.model.ResponsavelUiModel
import com.andre.medalert.data.model.SentInvite
import com.andre.medalert.data.remote.ApiService
import com.andre.medalert.data.remote.RetrofitClient
import com.andre.medalert.data.remote.dto.RequestCuidadorRequest
import com.andre.medalert.data.session.AccessPolicy
import com.andre.medalert.notification.NotificationRecorder
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Falta endpoint/contrato no backend para a operação pedida. */
class BackendDependencyException(message: String) : Exception(message)

/** Estado conhecido de um responsável na lista. Só o convite enviado é conhecido hoje. */
const val STATUS_CONVITE_ENVIADO = "Convite enviado"

/**
 * Reservado para quando existir uma rota que liste os cuidadores vinculados ao paciente.
 * Hoje nada no app produz este status: o aceite acontece no aparelho do cuidador e a API não
 * avisa o paciente.
 */
const val STATUS_VINCULO_ATIVO = "Vínculo ativo"

/**
 * Abstração dos dados de Responsáveis.
 *
 * DEPENDÊNCIA DO BACKEND: não existe endpoint que liste os cuidadores vinculados a um paciente
 * nem que remova um vínculo. Só existe o convite (POST /api/users/requestCuidador).
 */
interface ResponsavelRepository {
    suspend fun listar(): List<ResponsavelUiModel>
    suspend fun adicionar(nome: String, email: String): ResponsavelUiModel
    suspend fun remover(id: String)
}

private val DATA_ENVIO: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

private fun SentInvite.toUiModel(): ResponsavelUiModel {
    val dia = Instant.ofEpochMilli(sentAtMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    return ResponsavelUiModel(
        id = email,
        nome = nome,
        email = email,
        relacao = "Enviado em ${dia.format(DATA_ENVIO)}",
        status = STATUS_CONVITE_ENVIADO
    )
}

/**
 * Convite do paciente ao cuidador: POST /api/users/requestCuidador com
 * {email do CUIDADOR, nome, userId do PACIENTE}; o backend envia o push ao cuidador (que precisa
 * ter um token FCM registrado, senão a API responde erro).
 *
 * O campo "nome" segue o comentário do contrato no projeto (nome do PACIENTE, exibido no convite);
 * se a API esperar o nome do cuidador, trocar só em [adicionar].
 *
 * Lista: como a API não informa quem aceitou, [listar] devolve os convites ENVIADOS a partir deste
 * aparelho (guardados no DataStore, por paciente), todos com o status "Convite enviado". Nada é
 * apresentado como vínculo ativo. Trocar de aparelho ou reinstalar o app perde esse registro.
 *
 * Remover: apaga só o registro local do convite. NÃO desfaz um vínculo já aceito (não há rota).
 *
 * Caixa de notificações: depois que a API confirma o envio do convite, é gravado um aviso na caixa
 * do paciente ("Convite enviado"). Nada é gravado se o envio falhar.
 */
class ResponsavelRepositoryApi(
    private val prefs: UserPreferencesRepository,
    private val api: ApiService = RetrofitClient.instance
) : ResponsavelRepository {

    override suspend fun listar(): List<ResponsavelUiModel> {
        val session = prefs.session.first()
        if (AccessPolicy.isCaregiver(session.tipo)) return emptyList()
        val patientId = session.userId?.takeIf { it > 0 } ?: return emptyList()
        return prefs.sentInvites(patientId).first().map { it.toUiModel() }
    }

    override suspend fun adicionar(nome: String, email: String): ResponsavelUiModel {
        val session = prefs.session.first()
        if (AccessPolicy.isCaregiver(session.tipo)) {
            throw IllegalStateException("Somente o paciente pode convidar responsáveis.")
        }
        val patientId = session.userId?.takeIf { it > 0 }
            ?: throw IllegalStateException("Sessão expirada. Faça login novamente.")
        val patientName = prefs.userName.first() ?: "Paciente"

        // Falha da API (HTTP ou rede) lança exceção aqui: nada é guardado sem o envio ter dado certo.
        api.requestCuidador(
            RequestCuidadorRequest(email = email, nome = patientName, userId = patientId)
        ).close()

        val invite = SentInvite(
            nome = nome.trim(),
            email = email.trim(),
            sentAtMillis = System.currentTimeMillis()
        )
        prefs.addSentInvite(patientId, invite)

        // Melhor esforço: o gravador nunca lança exceção e não impede o convite de ser concluído.
        NotificationRecorder.record(
            context = null,
            tipo = NotificationType.RESPONSIBLE_ADDED,
            titulo = "Convite enviado",
            descricao = "Você convidou ${invite.nome.ifBlank { invite.email }} " +
                    "(${invite.email}) para acompanhar seus medicamentos.",
            dedupeKey = NotificationRecorder.inviteSentKey(patientId, invite.email, invite.sentAtMillis),
            patientId = patientId,
            criadaEm = invite.sentAtMillis
        )
        return invite.toUiModel()
    }

    override suspend fun remover(id: String) {
        val session = prefs.session.first()
        val patientId = session.userId?.takeIf { it > 0 && !AccessPolicy.isCaregiver(session.tipo) }
            ?: throw IllegalStateException("Sessão expirada. Faça login novamente.")
        prefs.removeSentInvite(patientId, id)
    }
}
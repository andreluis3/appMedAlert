package com.andre.medalert.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Uma notificação da CAIXA DE ENTRADA do usuário (tela do sininho).
 *
 * NÃO é histórico de doses: a administração continua em [DoseHistoryEntity]. Aqui só existe o
 * aviso ao usuário e o estado de leitura dele. A dose é apenas REFERENCIADA
 * (medicationLocalId + previstoEm, a mesma identidade usada em dose_history).
 *
 * - recipientUserId: quem LÊ a notificação (id do paciente ou do cuidador logado). Toda consulta
 *   filtra por ele, então paciente e cuidador têm linhas e estados de leitura independentes.
 * - patientId: de quem é o acontecimento (sempre o PACIENTE dono dos dados).
 * - tipo: nome de NotificationType guardado como texto (valor desconhecido vira SYSTEM na leitura).
 * - criadaEm: quando o acontecimento foi registrado (epoch millis). A lista ordena por ele.
 * - lidaEm: null = não lida; epoch millis = lida.
 * - dedupeKey: identidade do acontecimento para o destinatário. O índice único
 *   (recipientUserId, dedupeKey) impede notificação duplicada (sync repetido, FCM reentregue,
 *   alarme de adiamento, toque duplo).
 * - origem: "local" (gerada neste aparelho) ou "fcm" (veio de outro aparelho).
 */
@Entity(
    tableName = "notifications",
    indices = [
        Index(value = ["recipientUserId", "dedupeKey"], unique = true),
        Index(value = ["recipientUserId", "criadaEm"])
    ]
)
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipientUserId: Int,
    val patientId: Int,
    val tipo: String,
    val titulo: String,
    val descricao: String,
    val criadaEm: Long,
    val lidaEm: Long? = null,
    val dedupeKey: String,
    val medicationLocalId: Long? = null,
    val medicationRemoteId: Int? = null,
    val medicationNome: String? = null,
    val previstoEm: Long? = null,
    val origem: String = "local"
)
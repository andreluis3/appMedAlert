package com.andre.medalert.data.remote.dto

import com.google.gson.annotations.SerializedName
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Corpo do POST /api/historys (contrato confirmado no Postman pelo backend):
 *
 * {"usuario_id": 2, "medicamento_id": 1, "tomado_em": "2026-11-06T14:45:00-03:00"}
 *
 * - usuario_id: ID do PACIENTE.
 * - medicamento_id: ID REMOTO do medicamento (nunca o ID local do Room).
 * - tomado_em: horário real da tomada, ISO-8601 com offset.
 */
data class DoseTakenRequest(
    @SerializedName("usuario_id") val usuarioId: Int,
    @SerializedName("medicamento_id") val medicamentoId: Int,
    @SerializedName("tomado_em") val tomadoEm: String
)

object DoseTakenRequestFactory {

    // "xxx" sempre imprime o offset como +hh:mm / -hh:mm (nunca "Z").
    private val FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx")

    fun create(
        userId: Int,
        medicationRemoteId: Int,
        takenAtMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): DoseTakenRequest {
        val takenAt = OffsetDateTime
            .ofInstant(Instant.ofEpochMilli(takenAtMillis), zone)
            .truncatedTo(ChronoUnit.SECONDS)
            .format(FORMAT)

        return DoseTakenRequest(
            usuarioId = userId,
            medicamentoId = medicationRemoteId,
            tomadoEm = takenAt
        )
    }
}

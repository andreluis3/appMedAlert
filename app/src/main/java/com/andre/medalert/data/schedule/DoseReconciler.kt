package com.andre.medalert.data.schedule

import com.andre.medalert.data.local.entity.DoseHistoryEntity
import com.andre.medalert.data.local.entity.MedicationEntity
import com.andre.medalert.data.remote.dto.HistoryRecordDto
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Reconciliação do GET /api/historys/doseDia/{id} com o Room.
 *
 * A resposta NÃO traz medicamento_id nem identificador de dose. Por isso:
 * - a associação com o medicamento local usa nome + dosagem NORMALIZADOS e só vale quando a
 *   combinação é ÚNICA dos dois lados (um medicamento local com remoteId e um registro remoto);
 * - depois de associado, a identidade da dose passa a ser (medicationLocalId, previstoEm) e o
 *   medicamento é identificado pelo remoteId, nunca pelo nome;
 * - combinação ambígua é ignorada (nada é inventado nem sobrescrito);
 * - só doses que a API informa como TOMADAS (campo "tomado" preenchido) viram linhas locais.
 *   Ausência de informação no GET nunca é tratada como prova de que algo está errado.
 */
object DoseReconciler {

    data class Match(val medication: MedicationEntity, val remote: HistoryRecordDto)

    private val HORARIO = Regex("""^\s*(\d{1,2}):(\d{2})""")
    private val SO_HORA = Regex("""^\s*(\d{1,2}):(\d{2})(?::\d{2})?\s*$""")
    private val DOSAGEM = Regex("""^(\d+(?:[.,]\d+)?)\s*(.*)$""")
    private val ESPACOS = Regex("\\s+")
    private val ACENTOS = Regex("\\p{Mn}+")
    private val HH_MM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    // ------------------------------------------------------------ associação

    fun normalizeName(raw: String): String =
        Normalizer.normalize(raw.trim().lowercase(), Normalizer.Form.NFD)
            .replace(ACENTOS, "")
            .replace(ESPACOS, " ")

    /** "50 mg" e "50.0 mg" ficam iguais ("50|mg"); texto sem número só é normalizado. */
    fun normalizeDosage(raw: String): String {
        val text = normalizeName(raw)
        val match = DOSAGEM.find(text) ?: return text
        val number = match.groupValues[1].replace(',', '.').toBigDecimalOrNull()
            ?.stripTrailingZeros()
            ?.toPlainString()
            ?: return text
        return number + "|" + match.groupValues[2].trim()
    }

    private fun key(name: String, dosage: String): String =
        normalizeName(name) + "#" + normalizeDosage(dosage)

    /**
     * Associações CONFIÁVEIS entre os registros remotos e os medicamentos do paciente [userId].
     * Só entram medicamentos com remoteId; combinações repetidas (local ou remota) são descartadas.
     */
    fun associate(
        remote: List<HistoryRecordDto>,
        medications: List<MedicationEntity>,
        userId: Int
    ): List<Match> {
        val localByKey = medications
            .filter { it.userId == userId && it.remoteId != null }
            .groupBy { key(it.nome, it.dosagem) }
        val remoteByKey = remote.groupBy { key(it.nome, it.dosagem) }

        return remoteByKey.mapNotNull { (k, records) ->
            val locals = localByKey[k] ?: return@mapNotNull null
            if (records.size != 1 || locals.size != 1) return@mapNotNull null
            Match(medication = locals.single(), remote = records.single())
        }
    }

    // ------------------------------------------------------- linhas do Room

    /**
     * Linhas de dose TOMADA a partir de uma associação confiável, para o dia [day].
     * Doses com "tomado" nulo/vazio (pendentes) são ignoradas: o status delas é calculado
     * localmente pelo [DoseTimelineBuilder].
     */
    fun takenRows(match: Match, day: LocalDate, zone: ZoneId): List<DoseHistoryEntity> {
        val medication = match.medication
        val remoteId = medication.remoteId ?: return emptyList()

        return match.remote.doses.mapNotNull { dose ->
            val taken = dose.tomado?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val time = parseHorario(dose.horario) ?: return@mapNotNull null
            val scheduledAt = day.atTime(time)

            DoseHistoryEntity(
                userId = medication.userId,
                medicamentoId = remoteId,
                medicamentoNome = medication.nome,
                dosagem = medication.dosagem,
                horario = HH_MM.format(time),
                tomado = taken,
                status = dose.status,
                isSynced = true,
                medicationLocalId = medication.id,
                previstoEm = scheduledAt.atZone(zone).toInstant().toEpochMilli(),
                tomadoEm = parseTakenMillis(taken, day, zone),
                dia = day.toString()
            )
        }
    }

    // ---------------------------------------------------------------- parsing

    /** "HH:mm" (ou "H:mm") no início do texto; null se inválido. */
    fun parseHorario(raw: String?): LocalTime? {
        val match = HORARIO.find(raw ?: return null) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        return if (hour in 0..23 && minute in 0..59) LocalTime.of(hour, minute) else null
    }

    fun parseDay(raw: String?): LocalDate? {
        val text = raw?.trim()?.take(10) ?: return null
        return try {
            LocalDate.parse(text)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    /**
     * Interpreta o campo "tomado" da API. O formato real ainda não foi confirmado, então aceita
     * ISO com offset, ISO local ou apenas hora ("HH:mm" / "HH:mm:ss"); qualquer outro formato
     * devolve null (a dose continua marcada como tomada, só sem o horário exato).
     */
    fun parseTakenMillis(raw: String, day: LocalDate, zone: ZoneId): Long? {
        val text = raw.trim()
        try {
            return OffsetDateTime.parse(text).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
        }
        try {
            return LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
        }
        val match = SO_HORA.find(text) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    }
}

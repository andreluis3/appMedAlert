package com.andre.medalert.data.schedule

import com.andre.medalert.data.local.entity.MedicationEntity
import com.andre.medalert.data.remote.dto.DoseDto
import com.andre.medalert.data.remote.dto.DoseTakenRequestFactory
import com.andre.medalert.data.remote.dto.HistoryRecordDto
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class DoseReconcilerTest {

    private val zone: ZoneId = ZoneId.of("America/Sao_Paulo")
    private val day: LocalDate = LocalDate.of(2026, 11, 6)

    private fun medication(id: Long, name: String, dosage: String, remoteId: Int?) =
        MedicationEntity(id = id, userId = 1, nome = name, dosagem = dosage, remoteId = remoteId)

    private fun remote(name: String, dosage: String, vararg doses: DoseDto) =
        HistoryRecordDto(nome = name, dosagem = dosage, doses = doses.toList())

    private fun pending(time: String) = DoseDto(horario = time, tomado = null, status = "pendente")

    // ------------------------------------------------------------ associação

    @Test
    fun `associa quando nome e dosagem sao unicos`() {
        val medications = listOf(
            medication(10, "Losartana", "50 mg", remoteId = 1),
            medication(11, "Dipirona", "500 mg", remoteId = 2)
        )
        val response = listOf(remote("Losartana", "50 mg", pending("08:00")))

        val matches = DoseReconciler.associate(response, medications, userId = 1)

        assertEquals(1, matches.size)
        assertEquals(10L, matches.single().medication.id)
        assertEquals(1, matches.single().medication.remoteId)
    }

    @Test
    fun `ignora associacao ambigua com dois medicamentos locais iguais`() {
        val medications = listOf(
            medication(10, "Losartana", "50 mg", remoteId = 1),
            medication(11, "Losartana", "50 mg", remoteId = 2)
        )
        val response = listOf(remote("Losartana", "50 mg", pending("08:00")))

        assertTrue(DoseReconciler.associate(response, medications, userId = 1).isEmpty())
    }

    @Test
    fun `ignora associacao ambigua com dois registros remotos iguais`() {
        val medications = listOf(medication(10, "Losartana", "50 mg", remoteId = 1))
        val response = listOf(
            remote("Losartana", "50 mg", pending("08:00")),
            remote("Losartana", "50 mg", pending("20:00"))
        )

        assertTrue(DoseReconciler.associate(response, medications, userId = 1).isEmpty())
    }

    @Test
    fun `ignora medicamento local sem remoteId e de outro usuario`() {
        val medications = listOf(
            medication(10, "Losartana", "50 mg", remoteId = null),
            MedicationEntity(id = 12, userId = 2, nome = "Losartana", dosagem = "50 mg", remoteId = 5)
        )
        val response = listOf(remote("Losartana", "50 mg", pending("08:00")))

        assertTrue(DoseReconciler.associate(response, medications, userId = 1).isEmpty())
    }

    @Test
    fun `normaliza maiusculas acentos espacos e casas decimais da dosagem`() {
        val medications = listOf(medication(10, "  Vitamina  D ", "50.0 mg", remoteId = 3))
        val response = listOf(remote("vitamina d", "50 MG", pending("08:00")))

        assertEquals(1, DoseReconciler.associate(response, medications, userId = 1).size)
        assertEquals(DoseReconciler.normalizeName("Ômega 3"), DoseReconciler.normalizeName("omega 3"))
        assertEquals("2.5|mg", DoseReconciler.normalizeDosage("2,5 mg"))
        assertEquals("500|mg", DoseReconciler.normalizeDosage("500.00 mg"))
    }

    @Test
    fun `dosagens diferentes nao se associam`() {
        val medications = listOf(medication(10, "Losartana", "50 mg", remoteId = 1))
        val response = listOf(remote("Losartana", "100 mg", pending("08:00")))

        assertTrue(DoseReconciler.associate(response, medications, userId = 1).isEmpty())
    }

    // ------------------------------------------------------------ linhas

    @Test
    fun `doses pendentes do GET nao viram linhas locais`() {
        val medications = listOf(medication(10, "Losartana", "50 mg", remoteId = 1))
        val response = listOf(
            remote("Losartana", "50 mg", pending("11:00"), pending("19:00"), pending("03:00"))
        )
        val match = DoseReconciler.associate(response, medications, userId = 1).single()

        assertTrue(DoseReconciler.takenRows(match, day, zone).isEmpty())
    }

    @Test
    fun `dose tomada no GET vira linha sincronizada com remoteId e previsto`() {
        val medications = listOf(medication(10, "Losartana", "50 mg", remoteId = 1))
        val response = listOf(
            remote(
                "Losartana", "50 mg",
                DoseDto(horario = "08:00", tomado = "08:12", status = "tomado"),
                pending("20:00")
            )
        )
        val match = DoseReconciler.associate(response, medications, userId = 1).single()

        val rows = DoseReconciler.takenRows(match, day, zone)

        assertEquals(1, rows.size)
        val row = rows.single()
        assertEquals(10L, row.medicationLocalId)
        assertEquals(1, row.medicamentoId)
        assertEquals(true, row.isSynced)
        assertEquals("08:00", row.horario)
        assertEquals("2026-11-06", row.dia)
        assertEquals(
            day.atTime(8, 0).atZone(zone).toInstant().toEpochMilli(),
            row.previstoEm
        )
        assertEquals(
            day.atTime(8, 12).atZone(zone).toInstant().toEpochMilli(),
            row.tomadoEm
        )
    }

    // ------------------------------------------------------------ parsing

    @Test
    fun `parseTakenMillis aceita hora simples ISO com offset e rejeita lixo`() {
        val simple = DoseReconciler.parseTakenMillis("14:45", day, zone)
        val iso = DoseReconciler.parseTakenMillis("2026-11-06T14:45:00-03:00", day, zone)

        assertEquals(simple, iso)
        assertNull(DoseReconciler.parseTakenMillis("quando deu", day, zone))
        assertNull(DoseReconciler.parseTakenMillis("25:99", day, zone))
    }

    @Test
    fun `parseHorario valida hora e minuto`() {
        assertEquals(8, DoseReconciler.parseHorario("08:00")?.hour)
        assertEquals(3, DoseReconciler.parseHorario("3:05")?.hour)
        assertNull(DoseReconciler.parseHorario("24:00"))
        assertNull(DoseReconciler.parseHorario(null))
    }

    // ------------------------------------------------------------ POST

    @Test
    fun `request do POST segue o contrato confirmado no Postman`() {
        val takenAt = OffsetDateTime.of(2026, 11, 6, 14, 45, 0, 0, ZoneOffset.ofHours(-3))
            .toInstant().toEpochMilli()

        val request = DoseTakenRequestFactory.create(
            userId = 2,
            medicationRemoteId = 1,
            takenAtMillis = takenAt,
            zone = ZoneOffset.ofHours(-3)
        )

        assertEquals(
            """{"usuario_id":2,"medicamento_id":1,"tomado_em":"2026-11-06T14:45:00-03:00"}""",
            Gson().toJson(request)
        )
    }

    @Test
    fun `request do POST imprime offset mesmo em UTC e descarta fracao de segundo`() {
        val takenAt = OffsetDateTime.of(2026, 11, 6, 17, 45, 0, 0, ZoneOffset.UTC)
            .toInstant().toEpochMilli() + 789

        val request = DoseTakenRequestFactory.create(
            userId = 2,
            medicationRemoteId = 1,
            takenAtMillis = takenAt,
            zone = ZoneOffset.UTC
        )

        assertEquals("2026-11-06T17:45:00+00:00", request.tomadoEm)
    }
}

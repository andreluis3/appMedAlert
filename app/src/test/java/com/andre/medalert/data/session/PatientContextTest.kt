package com.andre.medalert.data.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PatientContextTest {

    private fun ctx(
        tipo: String,
        userId: Int?,
        pacienteId: Int?,
        ownName: String? = null,
        cached: LinkedPatientProfile? = null
    ) = PatientContext.from(SessionInfo(userId, tipo, pacienteId), ownName, cached)

    @Test
    fun paciente_ehODonoDosProprioDados_eONomeEDele() {
        val c = ctx("paciente", 9, null, ownName = "Maria")
        assertEquals(LinkState.PATIENT, c.linkState)
        assertEquals(9, c.userId)
        assertEquals(9, c.patientId)
        assertNull(c.caregiverId)
        assertEquals("Maria", c.patientName)
        assertTrue(c.canManageMedications)
        assertFalse(c.isCaregiver)
    }

    @Test
    fun cuidadorVinculado_donoEOPaciente_eONomeDoCuidadorNuncaViraNomeDoPaciente() {
        val c = ctx("cuidador", 7, 9, ownName = "Carlos (cuidador)")
        assertEquals(LinkState.CAREGIVER_LINKED, c.linkState)
        assertEquals(7, c.userId)
        assertEquals(9, c.patientId)
        assertEquals(7, c.caregiverId)
        assertNull(c.patientName)
        assertTrue(c.canManageMedications)
        assertTrue(c.isCaregiver)
    }

    @Test
    fun cuidadorVinculado_usaONomeSalvoSoSeForDoMesmoPaciente() {
        val ok = ctx("cuidador", 7, 9, cached = LinkedPatientProfile(9, " Maria "))
        assertEquals("Maria", ok.patientName)

        val outro = ctx("cuidador", 7, 9, cached = LinkedPatientProfile(26, "Joao"))
        assertNull(outro.patientName)
    }

    @Test
    fun cuidadorSemVinculo_naoTemPaciente_enaoAltera() {
        listOf<Int?>(null, 0, -3).forEach { vinculo ->
            val c = ctx("cuidador", 7, vinculo, cached = LinkedPatientProfile(9, "Maria"))
            assertEquals(LinkState.CAREGIVER_AWAITING_LINK, c.linkState)
            assertNull(c.patientId)
            assertNull(c.patientName)
            assertEquals(7, c.caregiverId)
            assertFalse(c.canManageMedications)
            assertTrue(c.isAwaitingLink)
        }
    }

    @Test
    fun semSessao_naoHaPacienteNemPermissao_mesmoComPacienteIdSobrando() {
        listOf(
            ctx("paciente", null, null),
            ctx("paciente", 0, null),
            ctx("cuidador", null, 9),
            ctx("cuidador", 0, 9)
        ).forEach { c ->
            assertEquals(LinkState.NO_SESSION, c.linkState)
            assertNull(c.userId)
            assertNull(c.patientId)
            assertNull(c.caregiverId)
            assertNull(c.patientName)
            assertFalse(c.canManageMedications)
        }
    }

    @Test
    fun nomeEmBrancoViraNull() {
        assertNull(ctx("paciente", 9, null, ownName = "   ").patientName)
        assertNull(ctx("cuidador", 7, 9, cached = LinkedPatientProfile(9, "  ")).patientName)
    }

    @Test
    fun sessaoAntigaSemTipoEPaciente() {
        val c = PatientContext.from(SessionInfo(9, "paciente", null), "Maria", null)
        assertEquals(9, c.patientId)
    }

    @Test
    fun canManageMedications_bateComAccessPolicy_emTodasAsCombinacoes() {
        val tipos = listOf("paciente", "cuidador", " Cuidador ", "")
        val ids = listOf<Int?>(null, -1, 0, 7)
        for (tipo in tipos) for (user in ids) for (vinculo in ids) {
            val c = PatientContext.from(SessionInfo(user, tipo, vinculo), null, null)
            assertEquals(
                "tipo=$tipo user=$user vinculo=$vinculo",
                AccessPolicy.canManageMedications(tipo, user, vinculo),
                c.canManageMedications
            )
            assertEquals(
                "dono tipo=$tipo user=$user vinculo=$vinculo",
                AccessPolicy.medicationOwnerIdFor(tipo, user, vinculo),
                if (c.canManageMedications) c.patientId else null
            )
        }
    }
}
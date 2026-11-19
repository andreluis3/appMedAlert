package com.andre.medalert.data.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regra definitiva: o PACIENTE é o dono e o único que escreve (cadastra, edita, exclui medicamentos
 * e registra doses). O CUIDADOR vinculado só visualiza (medicamentos, doses, histórico, adesão).
 */
class AccessPolicyTest {

    private val paciente = "paciente"
    private val cuidador = "cuidador"

    // ---------------------------------------------------------------- paciente

    @Test
    fun paciente_fazTodaEscrita() {
        assertTrue(AccessPolicy.canAddMedications(paciente, 9, null))
        assertTrue(AccessPolicy.canEditMedications(paciente, 9, null))
        assertTrue(AccessPolicy.canDeleteMedications(paciente, 9, null))
        assertTrue(AccessPolicy.canManageMedications(paciente, 9, null))
        assertTrue(AccessPolicy.canRegisterDose(paciente, 9, null))
        assertTrue(AccessPolicy.canViewHistory(paciente, 9, null))
    }

    @Test
    fun paciente_donoDosMedicamentosEELeMesmo() {
        assertEquals(9, AccessPolicy.patientIdFor(paciente, 9, null))
        assertEquals(9, AccessPolicy.medicationOwnerIdFor(paciente, 9, null))
        assertEquals(9, AccessPolicy.medicationAddOwnerIdFor(paciente, 9, null))
    }

    @Test
    fun paciente_soAlteraMedicamentoDele() {
        assertTrue(AccessPolicy.canManageMedication(paciente, 9, null, 9))
        assertFalse(AccessPolicy.canManageMedication(paciente, 9, null, 26))
        assertFalse(AccessPolicy.canManageMedication(paciente, 9, null, null))
    }

    @Test
    fun sessaoAntigaSemTipo_ehPaciente() {
        assertTrue(AccessPolicy.canAddMedications("", 9, null))
        assertTrue(AccessPolicy.canRegisterDose("", 9, null))
    }

    // ------------------------------------------------------ cuidador vinculado

    @Test
    fun cuidadorVinculado_naoEscreveNada() {
        assertFalse(AccessPolicy.canAddMedications(cuidador, 7, 9))
        assertFalse(AccessPolicy.canEditMedications(cuidador, 7, 9))
        assertFalse(AccessPolicy.canDeleteMedications(cuidador, 7, 9))
        assertFalse(AccessPolicy.canManageMedications(cuidador, 7, 9))
        assertFalse(AccessPolicy.canRegisterDose(cuidador, 7, 9))
    }

    @Test
    fun cuidadorVinculado_naoTemDonoDeEscrita_enemMedicamentoAlteravel() {
        assertNull(AccessPolicy.medicationOwnerIdFor(cuidador, 7, 9))
        assertNull(AccessPolicy.medicationAddOwnerIdFor(cuidador, 7, 9))
        assertFalse(AccessPolicy.canManageMedication(cuidador, 7, 9, 9))
        assertFalse(AccessPolicy.canManageMedication(cuidador, 7, 9, 7))
    }

    @Test
    fun cuidadorVinculado_acompanhaOPaciente() {
        assertEquals(9, AccessPolicy.patientIdFor(cuidador, 7, 9))
        assertTrue(AccessPolicy.canViewHistory(cuidador, 7, 9))
        assertTrue(AccessPolicy.canViewLinkedPatient(cuidador, 9))
        assertFalse(AccessPolicy.isAwaitingLink(cuidador, 7, 9))
    }

    @Test
    fun tipoComEspacosECaixaDiferente_continuaSendoCuidador() {
        assertFalse(AccessPolicy.canManageMedications(" Cuidador ", 7, 9))
        assertFalse(AccessPolicy.canRegisterDose("CUIDADOR", 7, 9))
        assertEquals(9, AccessPolicy.patientIdFor(" Cuidador ", 7, 9))
    }

    // ---------------------------------------------------------- cuidador sem vínculo

    @Test
    fun cuidadorSemVinculo_naoVeNemAltera() {
        listOf<Int?>(null, 0, -3).forEach { vinculo ->
            assertNull(AccessPolicy.patientIdFor(cuidador, 7, vinculo))
            assertFalse(AccessPolicy.canAddMedications(cuidador, 7, vinculo))
            assertFalse(AccessPolicy.canManageMedications(cuidador, 7, vinculo))
            assertFalse(AccessPolicy.canRegisterDose(cuidador, 7, vinculo))
            assertFalse(AccessPolicy.canViewHistory(cuidador, 7, vinculo))
            assertFalse(AccessPolicy.canViewLinkedPatient(cuidador, vinculo))
            assertTrue(AccessPolicy.isAwaitingLink(cuidador, 7, vinculo))
        }
    }

    // ------------------------------------------------------------------ sem sessão

    @Test
    fun semSessao_nadaEPermitido() {
        listOf<Int?>(null, 0, -1).forEach { user ->
            listOf(paciente, cuidador).forEach { tipo ->
                assertFalse(AccessPolicy.canAddMedications(tipo, user, 9))
                assertFalse(AccessPolicy.canManageMedications(tipo, user, 9))
                assertFalse(AccessPolicy.canRegisterDose(tipo, user, 9))
                assertNull(AccessPolicy.medicationOwnerIdFor(tipo, user, 9))
            }
        }
    }

    // ---------------------------------------------------------------------- alarmes

    @Test
    fun alarmes_pacienteRecebe_cuidadorNaoMexe_semSessaoLimpa() {
        assertEquals(AlarmTarget.Patient(9), AccessPolicy.alarmTarget(paciente, 9))
        assertEquals(AlarmTarget.KeepUntouched, AccessPolicy.alarmTarget(cuidador, 7))
        assertEquals(AlarmTarget.ClearAll, AccessPolicy.alarmTarget(paciente, null))
        assertEquals(AlarmTarget.ClearAll, AccessPolicy.alarmTarget(cuidador, 0))
    }

    // ------------------------------------------------- todas as combinações

    @Test
    fun escritaSoExisteParaPaciente_emTodasAsCombinacoes() {
        val tipos = listOf("paciente", "cuidador", " Cuidador ", "")
        val ids = listOf<Int?>(null, -1, 0, 7)
        for (tipo in tipos) for (user in ids) for (vinculo in ids) {
            val rotulo = "tipo=$tipo user=$user vinculo=$vinculo"
            val escreve = AccessPolicy.canAddMedications(tipo, user, vinculo)

            assertEquals(rotulo, escreve, AccessPolicy.canEditMedications(tipo, user, vinculo))
            assertEquals(rotulo, escreve, AccessPolicy.canDeleteMedications(tipo, user, vinculo))
            assertEquals(rotulo, escreve, AccessPolicy.canManageMedications(tipo, user, vinculo))
            assertEquals(rotulo, escreve, AccessPolicy.canRegisterDose(tipo, user, vinculo))

            if (AccessPolicy.isCaregiver(tipo)) {
                assertFalse("cuidador nunca escreve: $rotulo", escreve)
            }
        }
    }
}
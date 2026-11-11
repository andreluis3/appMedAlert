package com.andre.medalert.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PendingInviteCodecTest {

    @Test
    fun parsePacienteId_aceitaSomenteInteiroPositivo() {
        assertEquals(9, PendingInviteCodec.parsePacienteId("9"))
        assertEquals(9, PendingInviteCodec.parsePacienteId(" 9 "))
        assertNull(PendingInviteCodec.parsePacienteId(null))
        assertNull(PendingInviteCodec.parsePacienteId(""))
        assertNull(PendingInviteCodec.parsePacienteId("abc"))
        assertNull(PendingInviteCodec.parsePacienteId("0"))
        assertNull(PendingInviteCodec.parsePacienteId("-3"))
    }

    @Test
    fun encodeDecode_preservaOsConvites() {
        val lista = listOf(PendingInvite(9, 1000L), PendingInvite(4, 500L))
        assertEquals(lista, PendingInviteCodec.decode(PendingInviteCodec.encode(lista)))
    }

    @Test
    fun decode_ignoraLixoESemDuplicar() {
        val raw = "9:1000;lixo;9:2000;0:5;4:abc;4:500"
        assertEquals(
            listOf(PendingInvite(9, 1000L), PendingInvite(4, 500L)),
            PendingInviteCodec.decode(raw)
        )
        assertEquals(emptyList<PendingInvite>(), PendingInviteCodec.decode(null))
        assertEquals(emptyList<PendingInvite>(), PendingInviteCodec.decode("  "))
    }

    @Test
    fun add_naoDuplicaMesmoPacienteEColocaONovoNoTopo() {
        val atual = listOf(PendingInvite(9, 1000L))
        assertSame(atual, PendingInviteCodec.add(atual, PendingInvite(9, 2000L)))
        assertEquals(
            listOf(PendingInvite(4, 3000L), PendingInvite(9, 1000L)),
            PendingInviteCodec.add(atual, PendingInvite(4, 3000L))
        )
    }

    @Test
    fun remove_tiraSoOConviteDoPacienteIndicado() {
        val atual = listOf(PendingInvite(9, 1000L), PendingInvite(4, 500L))
        assertEquals(listOf(PendingInvite(4, 500L)), PendingInviteCodec.remove(atual, 9))
        assertEquals(atual, PendingInviteCodec.remove(atual, 77))
        assertEquals(emptyList<PendingInvite>(), PendingInviteCodec.remove(emptyList(), 9))
    }

    @Test
    fun add_respeitaOLimite() {
        var lista = emptyList<PendingInvite>()
        for (i in 1..(PendingInviteCodec.MAX_INVITES + 3)) {
            lista = PendingInviteCodec.add(lista, PendingInvite(i, i.toLong()))
        }
        assertEquals(PendingInviteCodec.MAX_INVITES, lista.size)
        assertEquals(PendingInviteCodec.MAX_INVITES + 3, lista.first().pacienteId)
    }
}
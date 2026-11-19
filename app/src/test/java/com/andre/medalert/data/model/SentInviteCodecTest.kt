package com.andre.medalert.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentInviteCodecTest {

    private fun invite(nome: String, email: String, millis: Long) = SentInvite(nome, email, millis)

    @Test
    fun codificarEDecodificar_devolveOMesmoConteudo() {
        val lista = listOf(
            invite("Maria Souza", "maria@email.com", 1_700_000_000_000),
            invite("Carlos", "carlos@email.com", 1_700_000_100_000)
        )
        assertEquals(lista, SentInviteCodec.decode(SentInviteCodec.encode(lista)))
    }

    @Test
    fun listaVaziaOuTextoInvalido_viraListaVazia() {
        assertTrue(SentInviteCodec.decode(null).isEmpty())
        assertTrue(SentInviteCodec.decode("").isEmpty())
        assertTrue(SentInviteCodec.decode("   ").isEmpty())
        assertTrue(SentInviteCodec.encode(emptyList()).isEmpty())
    }

    @Test
    fun linhasInvalidasSaoIgnoradas_eAsValidasSeguem() {
        val raw = "lixo sem tabs\n" +
                "Maria\tmaria@email.com\tnao-e-numero\n" +
                "Sem Email\t\t123\n" +
                "Carlos\tcarlos@email.com\t456"
        assertEquals(
            listOf(invite("Carlos", "carlos@email.com", 456)),
            SentInviteCodec.decode(raw)
        )
    }

    @Test
    fun nomeComTabOuQuebraDeLinha_naoQuebraOFormato() {
        val lista = listOf(invite("Ma\tria\nSouza", "maria@email.com", 10))
        val decodificada = SentInviteCodec.decode(SentInviteCodec.encode(lista))
        assertEquals(1, decodificada.size)
        assertEquals("Ma ria Souza", decodificada[0].nome)
        assertEquals("maria@email.com", decodificada[0].email)
    }

    @Test
    fun adicionar_colocaNoTopo_eOMesmoEmailSubstitui() {
        val antigo = invite("Maria", "maria@email.com", 1)
        val outro = invite("Carlos", "carlos@email.com", 2)
        val novoDaMaria = invite("Maria S.", "MARIA@email.com", 3)

        val resultado = SentInviteCodec.add(listOf(antigo, outro), novoDaMaria)

        assertEquals(listOf(novoDaMaria, outro), resultado)
    }

    @Test
    fun remover_ignoraMaiusculasEEspacos_enaoMexeNosOutros() {
        val maria = invite("Maria", "maria@email.com", 1)
        val carlos = invite("Carlos", "carlos@email.com", 2)

        assertEquals(listOf(carlos), SentInviteCodec.remove(listOf(maria, carlos), "  MARIA@email.com "))
        assertEquals(listOf(maria, carlos), SentInviteCodec.remove(listOf(maria, carlos), "ninguem@email.com"))
        assertTrue(SentInviteCodec.remove(listOf(maria), "maria@email.com").isEmpty())
    }
}
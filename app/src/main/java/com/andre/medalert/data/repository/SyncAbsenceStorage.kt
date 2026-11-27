package com.andre.medalert.data.repository

import com.andre.medalert.data.local.UserPreferencesRepository

/**
 * Onde o estado de [SyncAbsencePolicy] fica entre uma sincronização e outra. Sem alterar o
 * esquema do Room (nenhuma migration): o estado é pequeno e vive no DataStore.
 */
interface SyncAbsenceStorage {
    suspend fun load(userId: Int): Map<Int, SyncAbsencePolicy.Entry>
    suspend fun save(userId: Int, state: Map<Int, SyncAbsencePolicy.Entry>)

    /** Só em memória (testes, e padrão do repositório): reinicia com o processo. */
    class InMemory : SyncAbsenceStorage {
        private val data = mutableMapOf<Int, Map<Int, SyncAbsencePolicy.Entry>>()
        override suspend fun load(userId: Int) = data[userId].orEmpty()
        override suspend fun save(userId: Int, state: Map<Int, SyncAbsencePolicy.Entry>) {
            data[userId] = state
        }
    }
}

/** Versão de produção: guarda no DataStore, uma chave por paciente. */
class PreferencesSyncAbsenceStorage(
    private val prefs: UserPreferencesRepository
) : SyncAbsenceStorage {

    override suspend fun load(userId: Int): Map<Int, SyncAbsencePolicy.Entry> =
        SyncAbsencePolicy.decode(prefs.syncAbsenceRaw(userId))

    override suspend fun save(userId: Int, state: Map<Int, SyncAbsencePolicy.Entry>) {
        prefs.saveSyncAbsenceRaw(userId, SyncAbsencePolicy.encode(state))
    }
}

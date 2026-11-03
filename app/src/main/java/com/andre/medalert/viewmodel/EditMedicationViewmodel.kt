package com.andre.medalert.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.local.AppDatabase
import com.andre.medalert.data.local.entity.MedicationEntity
import com.andre.medalert.data.mapper.toIsoUtc
import com.andre.medalert.data.repository.MedicationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Valores do formulário de edição (texto como o usuário digita; datas em "yyyy-MM-dd"). */
data class EditForm(
    val nome: String = "",
    val dosagem: String = "",
    val unidadeDosagem: String = "",
    val quantidade: String = "",
    val unidadeQuantidade: String = "",
    val frequenciaHoras: Int? = null,
    val dataInicio: String? = null,
    val dataFim: String? = null,
    val estoque: String = "",
    val estoqueMinimo: String = "",
    val observacoes: String = "",
    val ativo: Boolean = true
)

sealed interface EditLoadState {
    data object Loading : EditLoadState

    /** [primeiraDose] = "HH:mm" quando o aparelho conhece o horário; null = desconhecido. */
    data class Ready(val primeiraDose: String?) : EditLoadState

    /** Não dá para editar (não encontrado ou ainda não enviado ao servidor). */
    data class Unavailable(val message: String) : EditLoadState
}

/**
 * O que mudou numa edição bem-sucedida. É o gancho para a futura reprogramação dos alarmes:
 * quando [affectsSchedule] for true (frequência, datas ou ativo/inativo mudaram), a etapa de
 * notificações poderá reagendar/cancelar os lembretes do [localId]. Nada disso é feito agora.
 */
data class EditResult(
    val localId: Long,
    val changedFields: Set<String>,
    val affectsSchedule: Boolean
)

sealed interface EditSaveState {
    data object Idle : EditSaveState
    data object Saving : EditSaveState
    data class Success(val result: EditResult) : EditSaveState
    data class Error(val message: String) : EditSaveState
}

/** Campos (nomes da API) que alteram a programação das doses. */
private val CAMPOS_DE_AGENDAMENTO = setOf("frequencia", "data_inicio", "data_fim", "ativo")

private val DATA_ISO = Regex("""\d{4}-\d{2}-\d{2}""")

class EditMedicationViewModel(
    private val localId: Long,
    private val repository: MedicationRepository
) : ViewModel() {

    private val _loadState = MutableStateFlow<EditLoadState>(EditLoadState.Loading)
    val loadState: StateFlow<EditLoadState> = _loadState.asStateFlow()

    private val _form = MutableStateFlow(EditForm())
    val form: StateFlow<EditForm> = _form.asStateFlow()

    private val _saveState = MutableStateFlow<EditSaveState>(EditSaveState.Idle)
    val saveState: StateFlow<EditSaveState> = _saveState.asStateFlow()

    private val _validationError = MutableStateFlow<String?>(null)
    val validationError: StateFlow<String?> = _validationError.asStateFlow()

    /** Valores como vieram do Room: base para descobrir o que o usuário realmente alterou. */
    private var original: EditForm? = null

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val entity = repository.getMedication(localId)
            if (entity == null) {
                _loadState.value = EditLoadState.Unavailable("Medicamento não encontrado.")
                return@launch
            }
            repository.editBlockedReason(entity)?.let {
                _loadState.value = EditLoadState.Unavailable(it)
                return@launch
            }
            val inicial = entity.toEditForm()
            original = inicial
            _form.value = inicial
            _loadState.value = EditLoadState.Ready(primeiraDose = entity.primeiraDoseOrNull())
        }
    }

    /** A tela chama a cada alteração de campo. Mantém o resto do formulário como está. */
    fun update(transform: (EditForm) -> EditForm) {
        _form.update(transform)
        _validationError.value = null
    }

    fun save() {
        if (_saveState.value is EditSaveState.Saving) return
        val base = original ?: return
        val atual = _form.value

        val erro = validate(base, atual)
        if (erro != null) {
            _validationError.value = erro
            return
        }

        val changes = buildChanges(base, atual)
        if (changes.isEmpty()) {
            _validationError.value = "Nenhuma alteração para salvar."
            return
        }

        _validationError.value = null
        _saveState.value = EditSaveState.Saving

        viewModelScope.launch {
            repository.editMedication(localId, changes).fold(
                onSuccess = {
                    original = atual // próxima edição compara com o que acabou de ser salvo
                    _saveState.value = EditSaveState.Success(
                        EditResult(
                            localId = localId,
                            changedFields = changes.keys,
                            affectsSchedule = changes.keys.any { it in CAMPOS_DE_AGENDAMENTO }
                        )
                    )
                },
                // Erro: o formulário NÃO é limpo; o usuário corrige ou tenta de novo.
                onFailure = { e ->
                    _saveState.value = EditSaveState.Error(
                        e.message ?: "Não foi possível salvar as alterações."
                    )
                }
            )
        }
    }

    fun consumeSuccess() {
        _saveState.value = EditSaveState.Idle
    }

    fun dismissError() {
        _saveState.value = EditSaveState.Idle
    }

    companion object {
        fun factory(localId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as Application
                EditMedicationViewModel(
                    localId = localId,
                    repository = MedicationRepository(
                        dao = AppDatabase.getInstance(app).medicationDao(),
                        userPrefs = UserPreferencesRepository(app)
                    )
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Entity -> formulário
// ---------------------------------------------------------------------------

/** Preenche só o que o aparelho realmente sabe; desconhecido vira vazio/null (nunca inventado). */
private fun MedicationEntity.toEditForm(): EditForm {
    // Fallback para registros antigos: lê "500 mg" do texto de exibição.
    val partes = dosagem.trim().split(" ", limit = 2)
    val valorDoTexto = partes.firstOrNull()?.takeIf { it.toIntOrNull() != null }.orEmpty()
    val unidadeDoTexto = partes.getOrNull(1).orEmpty()

    return EditForm(
        nome = nome,
        dosagem = dosagemValor?.toString() ?: valorDoTexto,
        unidadeDosagem = unidadeDosagem ?: unidadeDoTexto,
        quantidade = quantidade?.toString().orEmpty(),
        unidadeQuantidade = unidadeQuantidade.orEmpty(),
        frequenciaHoras = frequencia,
        dataInicio = dataInicio?.take(10)?.takeIf { DATA_ISO.matches(it) },
        dataFim = dataFim?.take(10)?.takeIf { DATA_ISO.matches(it) },
        estoque = estoque?.toString().orEmpty(),
        estoqueMinimo = estoqueMinimo?.toString().orEmpty(),
        observacoes = observacoes.orEmpty(),
        ativo = ativo
    )
}

/** "yyyy-MM-dd HH:mm:ss-03:00" -> "HH:mm". Só existe em medicamentos criados neste aparelho. */
private fun MedicationEntity.primeiraDoseOrNull(): String? =
    dataInicio?.takeIf { it.length >= 16 && it[10] == ' ' }?.substring(11, 16)

// ---------------------------------------------------------------------------
// Validação e diferenças
// ---------------------------------------------------------------------------

/** Valida os campos alterados (e o nome, sempre). Devolve a mensagem do primeiro problema. */
private fun validate(o: EditForm, a: EditForm): String? {
    if (a.nome.isBlank()) return "Informe o nome do medicamento."
    if (a.nome.trim().length > 200) return "O nome pode ter no máximo 200 caracteres."

    if (a.dosagem != o.dosagem || a.unidadeDosagem != o.unidadeDosagem) {
        val d = a.dosagem.toIntOrNull()
        if (d == null || d <= 0) return "Informe uma dosagem válida."
        if (a.unidadeDosagem.isBlank()) return "Escolha a unidade da dosagem."
    }

    if (a.quantidade != o.quantidade) {
        val q = a.quantidade.toIntOrNull()
        if (q == null || q !in 1..1000) return "Informe a quantidade por dose (de 1 a 1000)."
    }
    if (a.unidadeQuantidade != o.unidadeQuantidade && a.unidadeQuantidade.isBlank()) {
        return "Escolha a unidade da quantidade."
    }

    if (a.frequenciaHoras != o.frequenciaHoras) {
        val f = a.frequenciaHoras
        if (f == null || f !in 1..24) return "Escolha a frequência."
    }

    if (a.dataInicio != o.dataInicio && a.dataInicio == null) return "Escolha a data de início."
    val inicio = a.dataInicio
    val fim = a.dataFim
    if (inicio != null && fim != null && fim < inicio) {
        return "A data de fim não pode ser antes da data de início."
    }

    if (a.estoque != o.estoque) {
        // A API não aceita "limpar" o estoque: para zerar, o usuário informa 0.
        val e = a.estoque.toIntOrNull()
        if (e == null || e !in 0..1_000_000) return "Informe o estoque (use 0 para zerar)."
    }
    if (a.estoqueMinimo != o.estoqueMinimo) {
        val m = a.estoqueMinimo.toIntOrNull()
        if (m == null || m !in 0..1_000_000) return "Informe o estoque mínimo."
    }

    if (a.observacoes.trim().length > 1000) return "As observações podem ter no máximo 1000 caracteres."
    return null
}

/**
 * Monta o "data" do POST /api/meds/edit só com o que mudou, com os nomes da API.
 * Nunca inclui valores nulos (o Gson os omitiria). Dosagem e unidade da dosagem vão juntas.
 * Chame só depois de [validate] devolver null.
 */
private fun buildChanges(o: EditForm, a: EditForm): Map<String, Any?> {
    val c = linkedMapOf<String, Any?>()

    if (a.nome.trim() != o.nome.trim()) c["nome"] = a.nome.trim()

    if (a.dosagem != o.dosagem || a.unidadeDosagem != o.unidadeDosagem) {
        c["dosagem"] = a.dosagem.toInt()
        c["unidade_dosagem"] = a.unidadeDosagem
    }
    if (a.quantidade != o.quantidade) c["quantidade_por_dose"] = a.quantidade.toInt()
    if (a.unidadeQuantidade != o.unidadeQuantidade) c["unidade_quantidade"] = a.unidadeQuantidade

    if (a.frequenciaHoras != o.frequenciaHoras) c["frequencia"] = a.frequenciaHoras!!

    // A API guarda só o dia: mandamos "yyyy-MM-ddT00:00:00.000Z" (mesmo helper do cadastro).
    if (a.dataInicio != o.dataInicio) c["data_inicio"] = a.dataInicio!!.toIsoUtc()
    if (a.dataFim != o.dataFim && a.dataFim != null) c["data_fim"] = a.dataFim.toIsoUtc()

    if (a.estoque != o.estoque) c["estoque"] = a.estoque.toInt()
    if (a.estoqueMinimo != o.estoqueMinimo) c["estoque_minimo"] = a.estoqueMinimo.toInt()

    // Observação apagada vai como texto vazio (null seria omitido e a API não mudaria nada).
    if (a.observacoes.trim() != o.observacoes.trim()) c["observacoes"] = a.observacoes.trim()

    if (a.ativo != o.ativo) c["ativo"] = a.ativo

    return c
}
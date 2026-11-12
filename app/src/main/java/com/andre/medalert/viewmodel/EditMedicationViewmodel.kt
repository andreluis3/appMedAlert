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
import com.andre.medalert.ui.screens.meds.parseDecimalOrNull
import kotlinx.coroutines.launch
import java.math.BigDecimal

sealed interface EditLoadState {
    data object Loading : EditLoadState

    /** [primeiraDose] = "HH:mm" quando o aparelho conhece o horário; null = desconhecido. */
    data class Ready(val primeiraDose: String?) : EditLoadState

    /** Não dá para editar (não encontrado ou ainda não enviado ao servidor). */
    data class Unavailable(val message: String) : EditLoadState
}

/**
 * O que mudou numa edição bem-sucedida. [affectsSchedule] = true quando frequência, datas ou
 * ativo/inativo mudaram. Os alarmes se ajustam sozinhos: MedAlertApplication observa o Room e
 * reagenda/cancela os lembretes do [localId] a cada mudança.
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

package com.andre.medalert.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andre.medalert.data.model.NotificationUiModel
import com.andre.medalert.data.repository.NotificationActionResult
import com.andre.medalert.data.repository.NotificationRepository
import com.andre.medalert.data.repository.RoomNotificationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class NotificationFilter { TODAS, NAO_LIDAS, LIDAS }

sealed interface NotificationUiState {
    object Loading : NotificationUiState
    data class Error(val message: String = "Não foi possível carregar notificações.") : NotificationUiState

    /**
     * [notifications] já vem filtrada; [unreadCount] conta TODAS as não lidas (independe do filtro).
     */
    data class Success(
        val notifications: List<NotificationUiModel>,
        val unreadCount: Int,
        val filter: NotificationFilter
    ) : NotificationUiState
}

/**
 * Observa o Room (via repositório): a lista atualiza sozinha quando chega uma notificação nova ou
 * o estado de leitura muda, sem recarregar manualmente depois de cada ação.
 */
class NotificationViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: NotificationRepository = RoomNotificationRepository(application)
) : AndroidViewModel(application) {

    private val filter = MutableStateFlow(NotificationFilter.TODAS)
    private val retry = MutableStateFlow(0)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** Mensagens curtas de resultado das ações (a tela mostra como Toast). */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val selectedFilter: StateFlow<NotificationFilter> = filter.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<NotificationUiState> = retry
        .flatMapLatest {
            combine(repository.observe(), filter) { all, selected -> buildState(all, selected) }
                .onStart { emit(NotificationUiState.Loading) }
                .catch { emit(NotificationUiState.Error()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationUiState.Loading)

    private fun buildState(
        all: List<NotificationUiModel>,
        selected: NotificationFilter
    ): NotificationUiState {
        val visible = when (selected) {
            NotificationFilter.TODAS -> all
            NotificationFilter.NAO_LIDAS -> all.filter { !it.lida }
            NotificationFilter.LIDAS -> all.filter { it.lida }
        }
        return NotificationUiState.Success(
            notifications = visible,
            unreadCount = all.count { !it.lida },
            filter = selected
        )
    }

    /** "Tentar novamente" do estado de erro: reinscreve na lista. */
    fun carregar() {
        retry.value += 1
    }

    fun selecionarFiltro(novo: NotificationFilter) {
        filter.value = novo
    }

    fun marcarComoLida(id: Long) {
        launchSafely { repository.marcarComoLida(id) }
    }

    fun marcarTodasComoLidas() {
        launchSafely { repository.marcarTodasComoLidas() }
    }

    fun excluir(id: Long) {
        launchSafely { repository.excluir(id) }
    }

    /** A interação mais importante da tela: reutiliza o registro de dose existente. */
    fun marcarComoTomado(id: Long) {
        viewModelScope.launch {
            val message = try {
                when (repository.marcarMedicamentoComoTomado(id)) {
                    NotificationActionResult.DONE -> "Dose registrada."
                    NotificationActionResult.ALREADY_DONE -> "Essa dose já estava registrada."
                    NotificationActionResult.UNAVAILABLE ->
                        "Não foi possível registrar: esta dose não está mais disponível."

                    NotificationActionResult.ERROR ->
                        "Não foi possível registrar a dose. Tente novamente."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "Não foi possível registrar a dose. Tente novamente."
            }
            _messages.tryEmit(message)
        }
    }

    fun lembrarMaisTarde(id: Long) {
        viewModelScope.launch {
            val message = try {
                when (repository.lembrarMaisTarde(id)) {
                    NotificationActionResult.DONE -> "Vamos lembrar de novo em 5 minutos."
                    NotificationActionResult.ALREADY_DONE -> "Essa dose já estava registrada."
                    NotificationActionResult.UNAVAILABLE ->
                        "Não foi possível adiar: esta dose não está mais disponível."

                    NotificationActionResult.ERROR ->
                        "Não foi possível adiar o lembrete. Tente novamente."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "Não foi possível adiar o lembrete. Tente novamente."
            }
            _messages.tryEmit(message)
        }
    }

    private fun launchSafely(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A lista continua refletindo o Room; nada a desfazer.
            }
        }
    }
}
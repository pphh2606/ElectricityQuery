package edu.cqwu.electricity.speakup.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import edu.cqwu.electricity.speakup.data.ConsultationMessage
import edu.cqwu.electricity.speakup.data.SpeakUpApi
import edu.cqwu.electricity.common.net.SessionExpiredException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 留言列表 ViewModel。
 *
 * 管理指定咨询区的留言列表加载、分页和刷新状态。
 */
class MessageListViewModel(
    private val areaCode: String,
    val areaName: String
) : ViewModel() {

    /** UI 状态 */
    sealed class UiState {
        data object Loading : UiState()
        data class Success(
            val messages: List<ConsultationMessage>,
            val hasMore: Boolean
        ) : UiState()
        data class Error(
            val message: String,
            val requiresReLogin: Boolean = false,
        ) : UiState()
    }

    private val api = SpeakUpApi()
    private val pageSize = 10
    private var currentPage = 1
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** 用于标记是否正在加载更多 */
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val allMessages = mutableListOf<ConsultationMessage>()

    init {
        loadMessages()
    }

    /** 首次加载留言列表 */
    fun loadMessages() {
        _uiState.value = UiState.Loading
        currentPage = 1
        allMessages.clear()
        viewModelScope.launch {
            val result = api.fetchMessages(areaCode, currentPage, pageSize)
            result.fold(
                onSuccess = { messages ->
                    allMessages.addAll(messages)
                    _uiState.value = UiState.Success(
                        messages = allMessages.toList(),
                        hasMore = messages.size >= pageSize
                    )
                },
                onFailure = { error ->
                    _uiState.value = UiState.Error(
                        message = error.message ?: "",
                        requiresReLogin = error is SessionExpiredException,
                    )
                }
            )
        }
    }

    /** 加载下一页 */
    fun loadMore() {
        if (_isLoadingMore.value || _uiState.value !is UiState.Success) return
        val currentState = _uiState.value as UiState.Success
        if (!currentState.hasMore) return

        _isLoadingMore.value = true
        currentPage++
        viewModelScope.launch {
            val result = api.fetchMessages(areaCode, currentPage, pageSize)
            result.fold(
                onSuccess = { messages ->
                    allMessages.addAll(messages)
                    _uiState.value = UiState.Success(
                        messages = allMessages.toList(),
                        hasMore = messages.size >= pageSize
                    )
                },
                onFailure = { error ->
                    currentPage-- // 回退页码
                    // 保持当前列表，仅提示错误
                }
            )
            _isLoadingMore.value = false
        }
    }

    /** 下拉刷新 */
    fun refresh() {
        loadMessages()
    }

    /** ViewModel 工厂 */
    class Factory(
        private val areaCode: String,
        private val areaName: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MessageListViewModel(areaCode, areaName) as T
        }
    }
}

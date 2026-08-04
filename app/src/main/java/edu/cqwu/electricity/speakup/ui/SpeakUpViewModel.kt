package edu.cqwu.electricity.speakup.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.cqwu.electricity.speakup.data.ConsultationArea
import edu.cqwu.electricity.speakup.data.SpeakUpApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 「有话要说」页面 ViewModel。
 *
 * 管理咨询区列表的加载状态，参照项目中其他 ViewModel 的模式。
 */
class SpeakUpViewModel : ViewModel() {

    /** UI 状态 */
    sealed class UiState {
        /** 加载中 */
        data object Loading : UiState()

        /** 加载成功 */
        data class Success(val areas: List<ConsultationArea>) : UiState()

        /** 加载失败 */
        data class Error(val message: String) : UiState()
    }

    private val api = SpeakUpApi()

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadAreas()
    }

    /** 加载咨询区列表 */
    fun loadAreas() {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            val result = api.fetchConsultationAreas()
            result.fold(
                onSuccess = { areas ->
                    _uiState.value = UiState.Success(areas)
                },
                onFailure = { error ->
                    _uiState.value = UiState.Error(error.message ?: "")
                }
            )
        }
    }

    /** 下拉刷新 */
    fun refresh() {
        loadAreas()
    }

    /**
     * 预设 ehall 角色，确保 WebView 加载时能正确显示页面。
     * 在用户点击「发布留言」后、打开 WebView 前调用。
     *
     * @return 成功返回 true，失败返回 false
     */
    suspend fun preSetupRole(): Boolean {
        val result = api.preSetupEhallRole()
        return result.isSuccess
    }
}

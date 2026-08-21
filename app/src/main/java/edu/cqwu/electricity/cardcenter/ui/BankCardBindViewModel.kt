package edu.cqwu.electricity.cardcenter.ui

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import edu.cqwu.electricity.R
import edu.cqwu.electricity.cardcenter.data.BankCardBindApi
import edu.cqwu.electricity.cardcenter.data.BankCardBindResult
import edu.cqwu.electricity.cardcenter.data.BankCardBindStatus
import edu.cqwu.electricity.cardcenter.data.BankOption
import edu.cqwu.electricity.login.data.SessionExpiredException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 学生绑定银行卡页面状态。 */
data class BankCardBindUiState(
    val isRefreshing: Boolean = false,
    val loadError: String? = null,
    val requiresReLogin: Boolean = false,
    val banks: List<BankOption> = emptyList(),
    val selectedBankCode: String? = null,
    val status: BankCardBindStatus? = null,
    val cardNo: String = "",
    val isSubmitting: Boolean = false,
    val submitError: String? = null,
    val result: BankCardBindResult? = null,
)

/** 学生绑定银行卡 ViewModel。 */
class BankCardBindViewModel(application: Application) : AndroidViewModel(application) {

    private val api = BankCardBindApi()

    private val _uiState = MutableStateFlow(BankCardBindUiState())
    val uiState: StateFlow<BankCardBindUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /** 下拉刷新：保留当前内容，只显示 PullToRefreshBox 顶部动画。 */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, loadError = null) }
            fetchAndApplyBanks()
        }
    }

    private suspend fun fetchAndApplyBanks() {
        api.fetchBankOptions()
            .onSuccess { banks ->
                val selected = banks.firstOrNull { it.checked }?.code
                    ?: banks.firstOrNull()?.code
                if (selected == null) {
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            loadError = getString(R.string.bank_card_no_banks),
                        )
                    }
                    return@onSuccess
                }
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        banks = banks,
                        selectedBankCode = selected,
                    )
                }
                refreshStatus(selected)
            }
            .onFailure { e ->
                handleLoadFailure(e)
            }
    }

    fun selectBank(code: String) {
        if (code == _uiState.value.selectedBankCode) return
        _uiState.update {
            it.copy(
                selectedBankCode = code,
                cardNo = "",
                status = null,
                submitError = null,
                result = null,
            )
        }
        refreshStatus(code)
    }

    fun setCardNo(text: String) {
        _uiState.update {
            it.copy(
                cardNo = text,
                submitError = null,
            )
        }
    }

    fun bind() {
        val bankCode = _uiState.value.selectedBankCode ?: return
        val cardNo = _uiState.value.cardNo.trim()

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSubmitting = true,
                    submitError = null,
                    result = null,
                )
            }
            api.bindBankCard(cardNo, bankCode)
                .onSuccess { result ->
                    _uiState.update { it.copy(isSubmitting = false, result = result) }
                    if (result.isSuccess) refreshStatus(bankCode)
                }
                .onFailure { e ->
                    handleSubmitFailure(e)
                }
        }
    }

    fun unbind() {
        val bankCode = _uiState.value.selectedBankCode ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSubmitting = true,
                    submitError = null,
                    result = null,
                )
            }
            api.unbindBankCard(bankCode)
                .onSuccess { result ->
                    _uiState.update { it.copy(isSubmitting = false, result = result) }
                    if (result.isSuccess) refreshStatus(bankCode)
                }
                .onFailure { e ->
                    handleSubmitFailure(e)
                }
        }
    }

    fun clearLoadError() {
        _uiState.update { it.copy(loadError = null) }
    }

    fun clearSubmitError() {
        _uiState.update { it.copy(submitError = null) }
    }

    fun clearResult() {
        _uiState.update { it.copy(result = null) }
    }

    private fun refreshStatus(bankCode: String) {
        viewModelScope.launch {
            api.checkBankStatus(bankCode)
                .onSuccess { status ->
                    _uiState.update { state ->
                        if (state.selectedBankCode == bankCode) {
                            state.copy(status = status)
                        } else {
                            state
                        }
                    }
                }
                .onFailure { e ->
                    _uiState.update { state ->
                        if (state.selectedBankCode == bankCode) {
                            state.copy(
                                requiresReLogin = e is SessionExpiredException,
                                loadError = if (e is SessionExpiredException) {
                                    null
                                } else {
                                    e.message ?: getString(R.string.bank_card_fetch_failed)
                                },
                            )
                        } else {
                            state
                        }
                    }
                }
        }
    }

    private fun handleLoadFailure(e: Throwable) {
        _uiState.update {
            it.copy(
                isRefreshing = false,
                requiresReLogin = e is SessionExpiredException,
                loadError = if (e is SessionExpiredException) {
                    null
                } else {
                    e.message ?: getString(R.string.bank_card_fetch_failed)
                },
            )
        }
    }

    private fun handleSubmitFailure(e: Throwable) {
        _uiState.update {
            it.copy(
                isSubmitting = false,
                requiresReLogin = e is SessionExpiredException,
                submitError = if (e is SessionExpiredException) {
                    null
                } else {
                    e.message ?: getString(R.string.bank_card_request_failed)
                },
            )
        }
    }

    private fun getString(@StringRes resId: Int): String {
        return getApplication<Application>().getString(resId)
    }
}

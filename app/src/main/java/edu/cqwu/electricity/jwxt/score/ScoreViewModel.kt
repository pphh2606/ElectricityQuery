package edu.cqwu.electricity.jwxt.score

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.cqwu.electricity.common.net.SessionExpiredException
import edu.cqwu.electricity.jwxt.data.JwxtApi
import edu.cqwu.electricity.jwxt.data.JwxtScore
import edu.cqwu.electricity.jwxt.data.JwxtScoreTerm
import edu.cqwu.electricity.logging.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 成绩列表的筛选档位（对应网页的三个 Tab；网页也是拿到一份成绩在前端拆分，没有对应接口） */
enum class JwxtScoreFilter { ALL, PASSED, NOT_PASSED }

/**
 * 成绩页状态。
 *
 * [terms] 与 [scores] 直接用接口模型：字段就是页面要展示的内容，没有需要清洗或改名的项。
 *
 * 这里**不记录「当前是哪个 Tab」**——那个由页面的 Pager 自己持有（它能跨页面保存），
 * 三个 Tab 只是 [scores] 的三种本地切片，见 [scoresFor]。
 */
data class JwxtScoreUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    /** 学期胶囊的选项，顺序即接口顺序（第一项是接口下发的「全部学期」） */
    val terms: List<JwxtScoreTerm> = emptyList(),
    val selectedTermCode: String = "",
    /** 当前学期的全部成绩（已铺平学期分组） */
    val scores: List<JwxtScore> = emptyList(),
    /** 会话过期（CAS 票据失效或业务码 401），UI 显示「重新登录」 */
    val requiresReLogin: Boolean = false,
    val errorMessage: String? = null,
) {
    /**
     * 取某个 Tab 要显示的成绩。
     *
     * `passFlag` 为 null 的记录（接口没给通过状态，网页此时也不着色）只出现在「全部」里，
     * 与网页的拆分口径一致。
     */
    fun scoresFor(filter: JwxtScoreFilter): List<JwxtScore> = when (filter) {
        JwxtScoreFilter.ALL -> scores
        JwxtScoreFilter.PASSED -> scores.filter { it.passFlag == true }
        JwxtScoreFilter.NOT_PASSED -> scores.filter { it.passFlag == false }
    }
}

/**
 * 成绩查询页 ViewModel。
 *
 * 进页面先取学期列表，载入第一项（接口下发的「全部学期」）的成绩；切换学期会**清空重新请求**、
 * 不做缓存——与网页一致（抓包中来回切学期每次都重发请求）。
 * 失败分别落进 [JwxtScoreUiState.requiresReLogin] 与 [JwxtScoreUiState.errorMessage]，
 * 不做自动重试——重新登录后由页面的 ON_RESUME 触发一次 [load]。
 */
class JwxtScoreViewModel(
    private val api: JwxtApi = JwxtApi(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(JwxtScoreUiState())
    val uiState: StateFlow<JwxtScoreUiState> = _uiState.asStateFlow()

    /** 正在进行的成绩请求：切换学期时取消上一笔，避免旧学期的结果后到、盖住新学期 */
    private var scoreJob: Job? = null

    init {
        load()
    }

    /** 首次进入 / 重新登录后：取学期列表并载入默认学期的成绩 */
    fun load() {
        _uiState.update {
            it.copy(isLoading = true, scores = emptyList(), requiresReLogin = false, errorMessage = null)
        }

        viewModelScope.launch {
            try {
                val terms = api.fetchScoreTerms().getOrThrow()
                val termCode = terms.firstOrNull()?.termCode
                _uiState.update { it.copy(terms = terms, selectedTermCode = termCode.orEmpty()) }
                // 接口至少会给一项「全部学期」；真给空列表时收尾，避免页面一直停在加载态
                if (termCode == null) _uiState.update { it.copy(isLoading = false) } else loadScores(termCode)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e, "成绩查询加载失败")
            }
        }
    }

    /** 切换学期：换学期码并清空列表，Tab 复位由页面负责 */
    fun selectTerm(termCode: String) {
        if (termCode == _uiState.value.selectedTermCode) return

        _uiState.update { it.copy(selectedTermCode = termCode, scores = emptyList()) }
        loadScores(termCode)
    }

    /** 下拉刷新：只重拉当前学期的成绩（学期列表不会变，不必再取一次） */
    fun refresh() {
        val termCode = _uiState.value.selectedTermCode
        if (termCode.isBlank()) load() else loadScores(termCode, isRefresh = true)
    }

    /** 取某学期的成绩；[isRefresh] 为 true 时保留下拉指示器而不是整页加载态 */
    private fun loadScores(termCode: String, isRefresh: Boolean = false) {
        scoreJob?.cancel()
        scoreJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = !isRefresh,
                    isRefreshing = isRefresh,
                    requiresReLogin = false,
                    errorMessage = null,
                )
            }

            try {
                val scores = api.fetchTermScores(termCode).getOrThrow()
                _uiState.update { it.copy(isLoading = false, isRefreshing = false, scores = scores) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e, "学期成绩加载失败")
            }
        }
    }

    /** 失败归类：会话过期交给界面显示「重新登录」，其余原样显示错误信息 */
    private fun onError(e: Exception, message: String) {
        AppLog.e(TAG, message, e)
        val needLogin = e is SessionExpiredException
        _uiState.update {
            it.copy(
                isLoading = false,
                isRefreshing = false,
                requiresReLogin = needLogin,
                errorMessage = if (needLogin) null else e.message,
            )
        }
    }

    private companion object {
        const val TAG = "JwxtScoreViewModel"
    }
}

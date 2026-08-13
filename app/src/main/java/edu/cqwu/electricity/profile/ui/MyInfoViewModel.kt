package edu.cqwu.electricity.profile.ui

import android.app.Application
import edu.cqwu.electricity.logging.AppLog
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import edu.cqwu.electricity.R
import edu.cqwu.electricity.profile.data.StudentInfo
import edu.cqwu.electricity.profile.data.CampusphereApi
import edu.cqwu.electricity.profile.data.MenuCategory
import edu.cqwu.electricity.login.data.SessionExpiredException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MyInfoUiState(
    val studentInfo: StudentInfo? = null,
    val menuCategories: List<MenuCategory> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val needsLogin: Boolean = false,
)

class MyInfoViewModel(application: Application) : AndroidViewModel(application) {

    private val api = CampusphereApi()

    private val _uiState = MutableStateFlow(MyInfoUiState())
    val uiState: StateFlow<MyInfoUiState> = _uiState.asStateFlow()

    fun loadIfNeeded() {
        val current = _uiState.value
        if (current.studentInfo == null && !current.isLoading && !current.isRefreshing) {
            loadStudentInfo()
        }
    }

    fun loadStudentInfo() {
        if (_uiState.value.isRefreshing) return

        viewModelScope.launch {
            val isInitialLoad = _uiState.value.studentInfo == null
            _uiState.update {
                it.copy(
                    isLoading = isInitialLoad,
                    isRefreshing = true,
                    error = null,
                    needsLogin = false,
                )
            }

            // 并行请求学生信息和菜单列表，互不阻塞
            kotlinx.coroutines.coroutineScope {
                val infoDeferred = async { api.fetchStudentInfo() }
                val menuDeferred = async { api.fetchMenuList() }

                infoDeferred.await()
                    .onSuccess { info ->
                        _uiState.update {
                            it.copy(
                                studentInfo = info,
                                isLoading = false,
                                isRefreshing = false,
                                error = null,
                                needsLogin = false,
                            )
                        }
                    }
                    .onFailure { e ->
                        val needsLogin = e is SessionExpiredException
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                error = e.message ?: getApplication<Application>().getString(R.string.common_load_failed),
                                needsLogin = needsLogin,
                            )
                        }
                    }

                menuDeferred.await()
                    .onSuccess { categories ->
                        _uiState.update { it.copy(menuCategories = categories) }
                    }
                    .onFailure { e ->
                        AppLog.w("MyInfoViewModel", "加载菜单列表失败", e)
                    }
            }
        }
    }

}

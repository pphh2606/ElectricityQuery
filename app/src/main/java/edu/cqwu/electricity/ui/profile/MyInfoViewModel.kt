package edu.cqwu.electricity.ui.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.cqwu.electricity.data.model.StudentInfo
import edu.cqwu.electricity.data.network.CampusphereApi
import edu.cqwu.electricity.data.network.MenuCategory
import edu.cqwu.electricity.data.network.SessionExpiredException
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

class MyInfoViewModel : ViewModel() {

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
                                error = e.message ?: "加载失败",
                                needsLogin = needsLogin,
                            )
                        }
                    }

                menuDeferred.await()
                    .onSuccess { categories ->
                        _uiState.update { it.copy(menuCategories = categories) }
                    }
                    .onFailure { e ->
                        Log.w("MyInfoViewModel", "加载菜单列表失败", e)
                    }
            }
        }
    }

    fun reset() {
        _uiState.value = MyInfoUiState()
        loadStudentInfo()
    }
}

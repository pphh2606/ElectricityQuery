package edu.cqwu.electricity.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.cqwu.electricity.data.model.StudentInfo
import edu.cqwu.electricity.data.network.CampusphereApi
import edu.cqwu.electricity.data.network.MenuCategory
import edu.cqwu.electricity.data.network.NotLoggedInException
import edu.cqwu.electricity.data.network.SessionExpiredException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MyInfoUiState(
    val studentInfo: StudentInfo? = null,
    val menuCategories: List<MenuCategory> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val needsLogin: Boolean = false,
)

class MyInfoViewModel : ViewModel() {

    private val api = CampusphereApi()

    private val _uiState = MutableStateFlow(MyInfoUiState())
    val uiState: StateFlow<MyInfoUiState> = _uiState.asStateFlow()

    private var hasLoaded = false

    fun loadIfNeeded() {
        if (!hasLoaded) {
            hasLoaded = true
            loadStudentInfo()
        }
    }

    fun loadStudentInfo() {
        if (_uiState.value.isLoading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, needsLogin = false) }

            api.fetchStudentInfo()
                .onSuccess { info ->
                    _uiState.update {
                        it.copy(studentInfo = info, isLoading = false, error = null, needsLogin = false)
                    }
                    loadMenuCategories()
                }
                .onFailure { e ->
                    val needsLogin = e is SessionExpiredException
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "加载失败", needsLogin = needsLogin) }
                }
        }
    }

    private fun loadMenuCategories() {
        viewModelScope.launch {
            api.fetchMenuList().onSuccess { categories ->
                _uiState.update { it.copy(menuCategories = categories) }
            }.onFailure { e ->
                android.util.Log.w("MyInfoViewModel", "加载菜单列表失败", e)
            }
        }
    }

    fun reset() {
        hasLoaded = false
        _uiState.value = MyInfoUiState()
        loadIfNeeded()
    }
}

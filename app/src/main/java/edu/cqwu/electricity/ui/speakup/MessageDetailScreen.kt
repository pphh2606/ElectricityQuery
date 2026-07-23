package edu.cqwu.electricity.ui.speakup

import androidx.compose.foundation.background
import android.content.ClipData
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.cqwu.electricity.R
import edu.cqwu.electricity.data.model.ConsultationMessage
import edu.cqwu.electricity.data.repository.SpeakUpApi
import edu.cqwu.electricity.ui.components.LocalSnackbarController
import edu.cqwu.electricity.ui.theme.LocalTopBarState
import edu.cqwu.electricity.ui.theme.toTopAppBarColors
import edu.cqwu.electricity.util.ToastUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 留言详情 ViewModel。
 */
class MessageDetailViewModel(
    private val wid: String
) : ViewModel() {

    sealed class UiState {
        data object Loading : UiState()
        data class Success(val message: ConsultationMessage) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val api = SpeakUpApi()
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadDetail()
    }

    fun loadDetail() {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            val result = api.fetchMessages(wid, 1, 1) // 用 WID 过滤
            // 由于 API 不支持按 WID 查询列表，直接用分页查询中 WID 匹配
            // 但实际上 getZxxx.do 也支持 {"WID":"xxx"} 参数获取单条详情
            // 让我们直接用 WID 参数调用
        }
    }

    /** 通过 WID 获取单条留言详情 */
    fun loadDetailDirect() {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            // 复用 fetchMessages，但传入特殊参数
            // 实际上 getZxxx.do 支持 {"WID":"xxx"} 参数
            // 由于 SpeakUpApi 封装的是分页查询，这里直接用底层调用
            val result = api.fetchMessageDetail(wid)
            result.fold(
                onSuccess = { message ->
                    _uiState.value = UiState.Success(message)
                },
                onFailure = { error ->
                    _uiState.value = UiState.Error(error.message ?: "")
                }
            )
        }
    }

    class Factory(private val wid: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MessageDetailViewModel(wid) as T
        }
    }
}

/**
 * 留言详情页面。
 *
 * 布局：
 * - TopAppBar：返回箭头 + 标题「留言详情」
 * - 留言区域：标题（加粗）+ 信息行（灰色淡化）+ 分隔线 + 正文（可复制）
 * - 斜线分隔
 * - 回复区域：信息行（灰色淡化）+ 回复正文
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageDetailScreen(
    wid: String,
    onBack: () -> Unit,
    viewModel: MessageDetailViewModel = viewModel(
        factory = MessageDetailViewModel.Factory(wid)
    ),
) {
    val context = LocalContext.current
    val topBarColors = LocalTopBarState.current.style.toTopAppBarColors(MaterialTheme.colorScheme)
    val snackbar = LocalSnackbarController.current
    val uiState by viewModel.uiState.collectAsState()

    // 初始化时加载详情
    androidx.compose.runtime.LaunchedEffect(wid) {
        viewModel.loadDetailDirect()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.speakup_detail_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    // 分享按钮：复制留言链接到剪切板
                    val detailMessage = (uiState as? MessageDetailViewModel.UiState.Success)?.message
                    IconButton(onClick = {
                        if (detailMessage != null) {
                            val url = "https://ehall.cqwu.edu.cn/qljfwappnew/sys/lwPsZxzxApp/*default/index.do#/zxylxq?WID=${detailMessage.wid}"
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("speakup_link", url))
                            snackbar.show(context.getString(R.string.speakup_link_copied), ToastUtils.Type.SUCCESS)
                        }
                    }) {
                        Icon(Icons.Outlined.Share, contentDescription = null)
                    }
                },
                colors = topBarColors,
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState is MessageDetailViewModel.UiState.Loading,
            onRefresh = { viewModel.loadDetailDirect() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is MessageDetailViewModel.UiState.Loading -> {}

                is MessageDetailViewModel.UiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.loadDetailDirect() }) {
                                Text(stringResource(R.string.common_retry))
                            }
                        }
                    }
                }

                is MessageDetailViewModel.UiState.Success -> {
                    MessageDetailContent(message = state.message)
                }
            }
        }
    }
}

@Composable
private fun MessageDetailContent(message: ConsultationMessage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // ── 留言区域 ──

        // 标题（加粗）
        Text(
            text = message.zxbt,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 信息行（灰色淡化）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
        ) {
            Text(
                text = "${message.zxrdwdmDisplay} ${message.zxrxm} ${stringResource(R.string.speakup_message_label)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = message.zxsj,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 分隔线
        HorizontalDivider()

        Spacer(modifier = Modifier.height(12.dp))

        // 留言正文（可复制）
        SelectionContainer {
            Text(
                text = message.zxnr,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }

        // 留言图片
        if (message.zxImageList.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            val context = LocalContext.current
            message.zxImageList.filter { it.isImage }.forEach { img ->
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(img.fileUrlFull.ifBlank { img.middleUrlFull })
                        .crossfade(true)
                        .build(),
                    contentDescription = img.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.FillWidth
                )
            }
        }

        // ── 回复区域（仅在有回复时显示）──
        if (message.isAnswer && message.hfnr.isNotBlank()) {
            Spacer(modifier = Modifier.height(16.dp))

            Spacer(modifier = Modifier.height(12.dp))

            // 分隔线（与标题-正文之间的分隔线一致）
            HorizontalDivider()

            Spacer(modifier = Modifier.height(12.dp))

            // 回复信息行（灰色淡化）：咨询区名 + 回复人 + 时间
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${message.zxqdmDisplay} ${message.hfrxm} ${stringResource(R.string.speakup_reply_label)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = message.hfsj,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 回复正文
            SelectionContainer {
                Text(
                    text = message.hfnr,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }

            // 回复图片
            if (message.hfImagesList.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                val context = LocalContext.current
                message.hfImagesList.filter { it.isImage }.forEach { img ->
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(img.fileUrlFull.ifBlank { img.middleUrlFull })
                            .crossfade(true)
                            .build(),
                        contentDescription = img.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.FillWidth
                    )
                }
            }
        }

        // ── 评价区域（仅在已评价时显示）──
        if (!message.isNotJudge && message.score != null) {
            Spacer(modifier = Modifier.height(16.dp))

            // 分隔线
            HorizontalDivider()

            Spacer(modifier = Modifier.height(12.dp))

            // 评价内容（灰色标题）
            Text(
                text = stringResource(R.string.speakup_evaluation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 是否已解决
            if (message.sfjyjwtDisplay.isNotBlank()) {
                Text(
                    text = "${stringResource(R.string.speakup_problem_solved)}${message.sfjyjwtDisplay}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // 评分：星星 + 分数
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${stringResource(R.string.speakup_score)}  ",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                val score = message.score ?: 0
                repeat(5) { index ->
                    Icon(
                        imageVector = if (index < score) Icons.Filled.Star else Icons.Outlined.Star,
                        contentDescription = null,
                        tint = if (index < score) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = "  ${score}${stringResource(R.string.speakup_score_unit)}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

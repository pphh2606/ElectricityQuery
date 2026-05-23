package edu.cqwu.electricity.ui.profile

import androidx.compose.animation.AnimatedVisibility
import edu.cqwu.electricity.ui.components.ReLoginContent
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.cqwu.electricity.data.model.StudentInfo
import edu.cqwu.electricity.data.network.MenuCategory
import edu.cqwu.electricity.ui.theme.LocalTopBarState
import edu.cqwu.electricity.ui.theme.toTopAppBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyInfoScreen(
    onBack: () -> Unit,
    onReLogin: () -> Unit = {},
    onNavigateToWebView: (url: String, title: String) -> Unit = { _, _ -> },
    viewModel: MyInfoViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val topBarColors = LocalTopBarState.current.style.toTopAppBarColors(MaterialTheme.colorScheme)

    LaunchedEffect(Unit) {
        viewModel.loadIfNeeded()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的信息", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = topBarColors,
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.loadStudentInfo() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                uiState.needsLogin -> {
                    ReLoginContent(
                        errorMessage = "登录已过期，请重新登录",
                        requiresReLogin = true,
                        onReLogin = onReLogin,
                        onRetry = { viewModel.loadStudentInfo() },
                    )
                }
                uiState.error != null && uiState.studentInfo == null -> {
                    ReLoginContent(
                        errorMessage = uiState.error,
                        requiresReLogin = false,
                        onReLogin = {},
                        onRetry = { viewModel.loadStudentInfo() },
                    )
                }
                uiState.studentInfo != null -> {
                    MyInfoContent(
                        studentInfo = uiState.studentInfo!!,
                        menuCategories = uiState.menuCategories,
                        onNavigateToWebView = onNavigateToWebView,
                    )
                }
                else -> {
                    // 初次加载无数据：空的可滚动容器，PullToRefreshBox 指示器展示加载动画
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
        }
    }
}

@Composable
private fun MyInfoContent(
    studentInfo: StudentInfo,
    menuCategories: List<MenuCategory>,
    onNavigateToWebView: (url: String, title: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // ── 顶部头像 ──
        Box(
            modifier = Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 32.dp, bottom = 24.dp),
            ) {
                val initial = studentInfo.userName.firstOrNull()?.toString() ?: "?"
                Box(
                    modifier = Modifier.size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = initial, style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                }
                Spacer(Modifier.height(12.dp))
                Text(text = studentInfo.userName, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(text = studentInfo.userId, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp))
            }
        }

        // ── 详细信息列表 ──
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(12.dp))

            // 使用列表循环代替手动重复的 InfoRow + Divider
            val infoItems = remember(studentInfo) {
                listOf(
                    "性别" to studentInfo.sex,
                    "年级" to studentInfo.grade,
                    "院系" to studentInfo.dwmc,
                    "专业" to studentInfo.zymc,
                    "班级" to studentInfo.bjmc,
                    "手机号" to studentInfo.mobile,
                    "校区" to studentInfo.schoolZone.ifBlank { "-" },
                    "学生类别" to studentInfo.degree,
                    "在校情况" to studentInfo.userType,
                )
            }

            infoItems.forEachIndexed { index, (label, value) ->
                InfoRow(label, value)
                if (index < infoItems.lastIndex) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        // ── 可折叠菜单分类 ──
        if (menuCategories.isNotEmpty()) {
            CollapsibleMenuSection(
                categories = menuCategories,
                onNavigateToWebView = onNavigateToWebView,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ═══════════════════════════════════════════
//  可折叠菜单分类组件
// ═══════════════════════════════════════════

private fun categoryIcon(formCode: String): ImageVector = when {
    formCode.startsWith("basic") -> Icons.Outlined.Person
    formCode.startsWith("expand") -> Icons.Outlined.Extension
    formCode.startsWith("reward2punish") -> Icons.Outlined.EmojiEvents
    formCode.startsWith("support") -> Icons.Outlined.AccountBalanceWallet
    formCode.startsWith("gradeInfo") -> Icons.Outlined.School
    formCode.startsWith("FDMSJ") -> Icons.Filled.Extension
    formCode.startsWith("XXSJ") -> Icons.Outlined.Inventory2
    else -> Icons.Outlined.Person
}

/** H5 页面基础 URL，拼接到各小类的入口 */
private const val H5_BASE_URL =
    "https://cqwu.campusphere.net/wec-counselor-stuinfo-apps/student/mobile/index.html#/personalBaseInfo"

@Composable
private fun CollapsibleMenuSection(
    categories: List<MenuCategory>,
    modifier: Modifier = Modifier,
    onNavigateToWebView: (url: String, title: String) -> Unit = { _, _ -> },
) {
    val withChildren = categories.filter { !it.children.isNullOrEmpty() && it.formName.isNotBlank() }
    if (withChildren.isEmpty()) return

    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        withChildren.forEach { category ->
            key(category.formCode) {
                CollapsibleCategoryCard(
                    category = category,
                    onNavigateToWebView = onNavigateToWebView,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CollapsibleCategoryCard(
    category: MenuCategory,
    onNavigateToWebView: (url: String, title: String) -> Unit = { _, _ -> },
) {
    var expanded by remember { mutableStateOf(false) }
    val hasChildren = !category.children.isNullOrEmpty()
    val icon = categoryIcon(category.formCode)

    Column(modifier = Modifier.fillMaxWidth()) {
        // ── 大类标题行（点击展开/折叠） ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = hasChildren) { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = category.formName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (hasChildren) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess
                        else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ── 小类列表（展开时显示，可点击跳转 H5） ──
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column {
                HorizontalDivider(thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(horizontal = 16.dp))

                category.children?.forEachIndexed { index, child ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // 构建 H5 详情 URL
                                // formName=1 表示需要渲染完整表单（含字段名），0=只渲染值
                                val url = "$H5_BASE_URL?formCode=${child.formCode}&formName=1"
                                onNavigateToWebView(url, child.formName)
                            }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = child.formName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    if (index < (category.children?.size ?: 0) - 1) {
                        HorizontalDivider(thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(start = 20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value.ifBlank { "-" },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

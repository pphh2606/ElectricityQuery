package edu.cqwu.electricity.campusnetwork.campusnetworkinfo.ui

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.cqwu.electricity.R
import edu.cqwu.electricity.campusnetwork.campusnetworkinfo.data.ClientContextData
import edu.cqwu.electricity.common.ui.ReLoginContent
import edu.cqwu.electricity.theme.ui.currentTopBarColors
import edu.cqwu.electricity.theme.ui.resolve

/**
 * 接入者信息页面（校园网络 - 接入者信息）。
 *
 * 访问 GET /api/speedlyst/client-context 并把返回的全部字段分组展示，
 * 行样式参考「电表实时状态」界面（common InfoRow 左标签右值 + 细分隔线）。
 *
 * 注意：响应含个人档案（姓名/手机号/学号等），仅界面展示，不写日志、不做缓存。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientContextScreen(
    onBack: () -> Unit,
    viewModel: ClientContextViewModel = viewModel(),
) {
    val uiState by viewModel.state.collectAsState()
    val resources = LocalResources.current
    val topBarColors = currentTopBarColors()

    // 首次进入自动加载；VM 生命周期随导航栈条目，重新进入会新建并重新拉取
    LaunchedEffect(Unit) {
        viewModel.load(refresh = false)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.campus_network_accessor_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = topBarColors,
            )
        },
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.load(refresh = true) },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            val errorText = uiState.error?.resolve(resources)
            when {
                uiState.isLoading -> {
                    // 首次整页加载
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.campus_network_fetching),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                errorText != null -> {
                    // 错误态：可读文案 + 重试按钮（详情已由 API/VM 层记入 AppLog）
                    ReLoginContent(
                        errorMessage = errorText,
                        requiresReLogin = false,
                        onReLogin = {},
                        onRetry = { viewModel.load(refresh = false) },
                    )
                }

                uiState.data == null -> {
                    EmptyContent(stringResource(R.string.campus_network_empty))
                }

                else -> {
                    ClientContextContent(data = uiState.data!!)
                }
            }
        }
    }
}

// ====================================================================
//  内容区：分组字段列表（参考电表实时状态：InfoRow + 细分隔线）
// ====================================================================

/** 展示分组：标题 + 若干「左标签 - 右值」行 */
private data class InfoSection(
    val title: String,
    val rows: List<InfoField>,
)

/** 单行字段；值统一支持换行完整展示（不省略截断） */
private data class InfoField(
    val label: String,
    val value: String,
)

@Composable
private fun ClientContextContent(
    data: ClientContextData,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val sections = remember(data) { buildSections(data, resources) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "client_context_sections") {
            Column {
                sections.forEach { section ->
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
                    )
                    section.rows.forEachIndexed { index, field ->
                        // 所有值统一可换行完整展示，避免长文本被省略截断
                        FieldRow(
                            label = field.label,
                            value = field.value,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                        )
                        if (index < section.rows.size - 1) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 字段行：左标签右值，值允许多行换行完整展示（不省略）。
 * 视觉对齐「电表实时状态」的 InfoRow，但值不受单行省略限制。
 */
@Composable
private fun FieldRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(104.dp),
        )
        Text(
            text = value.ifBlank { "-" },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun EmptyContent(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}

// ====================================================================
//  字段分组构建
// ====================================================================

/**
 * 把 client-context 响应转成展示分组。字段全集对照 API 文档，逐行固定顺序，
 * 缺失值为空（InfoRow 统一显示 "-"），保证"所有字段都有位置"。
 */
private fun buildSections(data: ClientContextData, resources: Resources): List<InfoSection> {
    val sections = mutableListOf<InfoSection>()
    val s = { res: Int -> resources.getString(res) }

    // 构造单行字段（lambda 不支持默认参数，故用局部函数）
    fun f(res: Int, value: String?): InfoField =
        InfoField(label = s(res), value = value.orEmpty())

    // ── 识别概览 ──
    val overviewRows = mutableListOf<InfoField>()
    overviewRows += f(R.string.cn_label_request_ip, data.ip)
    overviewRows += f(R.string.cn_label_source, displaySource(data.source))
    overviewRows += f(R.string.cn_label_processed, data.processedString)
    overviewRows += f(R.string.cn_label_matched, displayMatchedBy(data.matchedBy))
    if (data.samError != null) {
        overviewRows += f(R.string.cn_label_sam_error, data.samError)
    }
    // 未命中 SAM 档案时的提示（理论上校园网在线时会命中）
    if (data.row == null) {
        overviewRows += f(
            R.string.cn_label_identity_hit,
            resources.getString(R.string.campus_network_sam_not_hit),
        )
    }
    sections += InfoSection(title = s(R.string.cn_group_overview), rows = overviewRows)

    // ── 网络运营商信息（rawIspInfo）──
    data.rawIspInfo?.let { isp ->
        sections += InfoSection(
            title = s(R.string.cn_group_isp),
            rows = listOfNotNull(
                f(R.string.cn_label_isp_source, isp.source),
                f(R.string.cn_label_region, isp.region),
                f(R.string.cn_label_city, isp.city),
                f(R.string.cn_label_isp_name, isp.isp),
                f(R.string.cn_label_isp_provider, isp.provider),
                f(R.string.cn_label_isp_org, isp.organization),
                f(R.string.cn_label_label, isp.label),
            ),
        )
    }

    // ── 用户档案（SAM，row）──
    data.row?.let { row ->
        sections += InfoSection(
            title = s(R.string.cn_group_profile),
            rows = listOfNotNull(
                f(R.string.cn_label_user_type, displayUserType(row.userType)),
                f(R.string.cn_label_user_no, row.userNo),
                f(R.string.cn_label_name, row.name),
                f(R.string.cn_label_sex, displaySex(row.sex)),
                f(R.string.cn_label_phone, row.phone),
                f(R.string.cn_label_dept_id, row.deptId),
                f(R.string.cn_label_dept_name, row.deptName),
                f(R.string.cn_label_source_id, row.sourceId),
                f(R.string.cn_label_title, row.title),
                f(R.string.cn_label_major_code, row.majorCode),
                f(R.string.cn_label_major_name, row.majorName),
                f(R.string.cn_label_grade, row.grade),
                f(R.string.cn_label_class, row.className),
                f(R.string.cn_label_archive_user_id, row.archiveUserId),
                f(R.string.cn_label_archive_user_name, row.archiveUserName),
                f(R.string.cn_label_archive_user_group, row.archiveUserGroupName),
                f(R.string.cn_label_archive_template, row.archiveUserTemplateName),
                f(R.string.cn_label_archive_package, row.archiveUserPackageName),
                f(R.string.cn_label_archive_policy, row.archivePolicyId),
                f(R.string.cn_label_archive_state, row.archiveStateFlag?.toString()),
                f(R.string.cn_label_archive_online_state, displayOnlineState(row.archiveOnlineState)),
                f(R.string.cn_label_archive_created, row.archiveCreatedAt),
                f(R.string.cn_label_archive_logout, row.archiveLastLogoutAt),
                f(R.string.cn_label_archive_next_billing, row.archiveNextBillingAt),
                f(R.string.cn_label_archive_free_auth, displayYesNo(row.archiveFreeAuth)),
                f(R.string.cn_label_archive_ip, row.archiveIp),
                f(R.string.cn_label_archive_self_permission, row.archiveSelfServicePermission),
                f(R.string.cn_label_online_mac, row.onlineMac),
                f(R.string.cn_label_online_ipv4, row.onlineIpv4),
                f(R.string.cn_label_online_nas_ip, row.onlineNasIp),
                f(R.string.cn_label_online_nas_port, row.onlineNasPort?.toString()),
                f(R.string.cn_label_online_connected, row.onlineConnectedAt),
                f(R.string.cn_label_online_access_type, row.onlineAccessType?.toString()),
                f(R.string.cn_label_online_group_id, row.onlineGroupId),
                f(R.string.cn_label_online_template_id, row.onlineTemplateId),
                f(R.string.cn_label_online_package, row.onlinePackageName),
                f(R.string.cn_label_online_policy, row.onlinePolicyId),
                f(R.string.cn_label_online_service_id, row.onlineServiceId),
                f(R.string.cn_label_online_area, row.onlineAreaName),
            ),
        )
    }

    // ── 公网归属地（region；校园网在线时通常不出现，作为健壮性兜底）──
    data.region?.let { region ->
        sections += InfoSection(
            title = s(R.string.cn_group_region),
            rows = listOfNotNull(
                f(R.string.cn_label_country, region.country),
                f(R.string.cn_label_province, region.province),
                f(R.string.cn_label_city, region.city),
                f(R.string.cn_label_region, region.region),
                f(R.string.cn_label_isp_name, region.isp),
                f(R.string.cn_label_country_code, region.countryCode),
                f(R.string.cn_label_label, region.label),
                f(R.string.cn_label_is_public, displayYesNoBoolean(region.isPublic)),
            ),
        )
    }

    return sections
}

// ====================================================================
//  字段值展示辅助（枚举/状态 → 中文可读文案）
// ====================================================================

private fun displaySource(source: String?): String? = when (source) {
    "sam" -> "校园网在线（SAM）"
    "ip2region" -> "公网归属地"
    else -> source
}

private fun displayMatchedBy(matchedBy: String?): String? = when (matchedBy) {
    "onlineIpv4" -> "SAM 在线命中"
    "archiveIp" -> "SAM 档案命中"
    else -> matchedBy
}

private fun displayUserType(userType: String?): String? = when (userType) {
    "student" -> "学生"
    "teacher" -> "教师"
    else -> userType
}

private fun displaySex(sex: Int?): String? = when (sex) {
    1 -> "男"
    2 -> "女"
    else -> null
}

/** archiveOnlineState：1=在线，0=离线（与网页端映射一致） */
private fun displayOnlineState(state: Int?): String? = when (state) {
    1 -> "在线"
    0 -> "离线"
    else -> null
}

/** 0/1 标志位 → 是/否 */
private fun displayYesNo(flag: Int?): String? = when (flag) {
    1 -> "是"
    0 -> "否"
    else -> null
}

private fun displayYesNoBoolean(flag: Boolean?): String? = when (flag) {
    true -> "是"
    false -> "否"
    else -> null
}

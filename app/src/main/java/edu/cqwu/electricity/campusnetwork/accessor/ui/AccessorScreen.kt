package edu.cqwu.electricity.campusnetwork.accessor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import edu.cqwu.electricity.campusnetwork.accessor.data.AccessorData
import edu.cqwu.electricity.campusnetwork.ui.Field
import edu.cqwu.electricity.campusnetwork.ui.FieldSection
import edu.cqwu.electricity.campusnetwork.ui.FieldSectionList
import edu.cqwu.electricity.campusnetwork.ui.field
import edu.cqwu.electricity.common.ui.ReLoginContent
import edu.cqwu.electricity.theme.ui.currentTopBarColors
import edu.cqwu.electricity.theme.ui.resolve

/**
 * 接入者信息页面（校园网络 - 接入者信息）。
 *
 * 访问 GET /api/speedlyst/client-context 并把返回的全部字段分组展示，
 * 行样式沿用「电表实时状态」界面（`common/ui` InfoRow 左标签右值 + 细分隔线，行距 8dp），
 * 字段表由 [buildSections] 以纯数据列举，渲染交给 `campusnetwork/ui` 的
 * [edu.cqwu.electricity.campusnetwork.ui.FieldSectionList]。
 *
 * 注意：响应含个人档案（姓名/手机号/学号等），仅界面展示，不写日志、不做缓存。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessorScreen(
    onBack: () -> Unit,
    viewModel: AccessorViewModel = viewModel(),
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
                    AccessorContent(data = uiState.data!!)
                }
            }
        }
    }
}

// ====================================================================
//  内容区：分组字段列表（参考电表实时状态：InfoRow + 细分隔线）
// ====================================================================

@Composable
private fun AccessorContent(
    data: AccessorData,
    modifier: Modifier = Modifier,
) {
    // 字段表是纯数据，用 remember 避免每次重组重建
    val sections = remember(data) { buildSections(data) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "client_context_sections") {
            FieldSectionList(sections = sections)
        }
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
 * 缺失值传 null（[Field] 统一显示 "-"），保证"所有字段都有位置"。
 *
 * 纯函数、不依赖 `Resources` 与 Composable —— 字段表可脱离 UI 单测，
 * 渲染由 [edu.cqwu.electricity.campusnetwork.ui.FieldSectionList] 承担。
 */
private fun buildSections(data: AccessorData): List<FieldSection> = buildList {
    // ── 识别概览 ──
    add(
        FieldSection(
            title = R.string.cn_group_overview,
            fields = buildList {
                add(field(R.string.cn_label_request_ip, data.ip))
                add(field(R.string.cn_label_source, displaySource(data.source)))
                add(field(R.string.cn_label_processed, data.processedString))
                add(field(R.string.cn_label_matched, displayMatchedBy(data.matchedBy)))
                if (data.samError != null) add(field(R.string.cn_label_sam_error, data.samError))
                // 未命中 SAM 档案时的提示（理论上校园网在线时会命中）
                if (data.row == null) {
                    add(Field(R.string.cn_label_identity_hit, null, R.string.campus_network_sam_not_hit))
                }
            },
        ),
    )

    // ── 网络运营商信息（rawIspInfo）──
    data.rawIspInfo?.let { isp ->
        add(
            FieldSection(
                title = R.string.cn_group_isp,
                fields = listOf(
                    field(R.string.cn_label_isp_source, isp.source),
                    field(R.string.cn_label_region, isp.region),
                    field(R.string.cn_label_city, isp.city),
                    field(R.string.cn_label_isp_name, isp.isp),
                    field(R.string.cn_label_isp_provider, isp.provider),
                    field(R.string.cn_label_isp_org, isp.organization),
                    field(R.string.cn_label_label, isp.label),
                ),
            ),
        )
    }

    // ── 用户档案（SAM，row）──
    data.row?.let { row ->
        add(
            FieldSection(
                title = R.string.cn_group_profile,
                fields = listOf(
                    field(R.string.cn_label_user_type, displayUserType(row.userType)),
                    field(R.string.cn_label_user_no, row.userNo),
                    field(R.string.cn_label_name, row.name),
                    field(R.string.cn_label_sex, displaySex(row.sex)),
                    field(R.string.cn_label_phone, row.phone),
                    field(R.string.cn_label_dept_id, row.deptId),
                    field(R.string.cn_label_dept_name, row.deptName),
                    field(R.string.cn_label_source_id, row.sourceId),
                    field(R.string.cn_label_title, row.title),
                    field(R.string.cn_label_major_code, row.majorCode),
                    field(R.string.cn_label_major_name, row.majorName),
                    field(R.string.cn_label_grade, row.grade),
                    field(R.string.cn_label_class, row.className),
                    field(R.string.cn_label_archive_user_id, row.archiveUserId),
                    field(R.string.cn_label_archive_user_name, row.archiveUserName),
                    field(R.string.cn_label_archive_user_group, row.archiveUserGroupName),
                    field(R.string.cn_label_archive_template, row.archiveUserTemplateName),
                    field(R.string.cn_label_archive_package, row.archiveUserPackageName),
                    field(R.string.cn_label_archive_policy, row.archivePolicyId),
                    field(R.string.cn_label_archive_state, row.archiveStateFlag?.toString()),
                    field(R.string.cn_label_archive_online_state, displayOnlineState(row.archiveOnlineState)),
                    field(R.string.cn_label_archive_created, row.archiveCreatedAt),
                    field(R.string.cn_label_archive_logout, row.archiveLastLogoutAt),
                    field(R.string.cn_label_archive_next_billing, row.archiveNextBillingAt),
                    field(R.string.cn_label_archive_free_auth, displayYesNo(row.archiveFreeAuth)),
                    field(R.string.cn_label_archive_ip, row.archiveIp),
                    field(R.string.cn_label_archive_self_permission, row.archiveSelfServicePermission),
                    field(R.string.cn_label_online_mac, row.onlineMac),
                    field(R.string.cn_label_online_ipv4, row.onlineIpv4),
                    field(R.string.cn_label_online_nas_ip, row.onlineNasIp),
                    field(R.string.cn_label_online_nas_port, row.onlineNasPort?.toString()),
                    field(R.string.cn_label_online_connected, row.onlineConnectedAt),
                    field(R.string.cn_label_online_access_type, row.onlineAccessType?.toString()),
                    field(R.string.cn_label_online_group_id, row.onlineGroupId),
                    field(R.string.cn_label_online_template_id, row.onlineTemplateId),
                    field(R.string.cn_label_online_package, row.onlinePackageName),
                    field(R.string.cn_label_online_policy, row.onlinePolicyId),
                    field(R.string.cn_label_online_service_id, row.onlineServiceId),
                    field(R.string.cn_label_online_area, row.onlineAreaName),
                ),
            ),
        )
    }

    // ── 公网归属地（region；校园网在线时通常不出现，作为健壮性兜底）──
    data.region?.let { region ->
        add(
            FieldSection(
                title = R.string.cn_group_region,
                fields = listOf(
                    field(R.string.cn_label_country, region.country),
                    field(R.string.cn_label_province, region.province),
                    field(R.string.cn_label_city, region.city),
                    field(R.string.cn_label_region, region.region),
                    field(R.string.cn_label_isp_name, region.isp),
                    field(R.string.cn_label_country_code, region.countryCode),
                    field(R.string.cn_label_label, region.label),
                    field(R.string.cn_label_is_public, displayYesNoBoolean(region.isPublic)),
                ),
            ),
        )
    }
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

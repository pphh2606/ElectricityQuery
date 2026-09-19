@file:OptIn(ExperimentalMaterial3Api::class)

package edu.cqwu.electricity.jwxt.schedulequery.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import edu.cqwu.electricity.common.navigation.LocalNavController
import edu.cqwu.electricity.common.navigation.Routes
import edu.cqwu.electricity.common.ui.InfoRowDivider
import edu.cqwu.electricity.jwxt.core.JwxtConstants
import edu.cqwu.electricity.jwxt.core.model.ScheduleTargetType
import edu.cqwu.electricity.theme.ui.currentTopBarColors

/** 课表类型的标题文案；入口页与选择页共用（data 层的枚举不带资源，映射放在这里） */
@get:StringRes
internal val ScheduleTargetType.titleRes: Int
    get() = when (this) {
        ScheduleTargetType.CLASSROOM -> R.string.jwxt_schedule_type_classroom
        ScheduleTargetType.TEACHER -> R.string.jwxt_schedule_type_teacher
        ScheduleTargetType.CLASS -> R.string.jwxt_schedule_type_class
    }

/**
 * 「全校课表查询」入口页：列出三种课表（教室 / 教师 / 班级）。
 *
 * 对应网页 pkApp 的 `#/qxkbcx/index`（首页九宫格服务 `serviceKey` 前缀 `PK.QXKBCX`）。
 * 三种课表各自的**选择界面**是同一个页面（见 [JwxtScheduleTargetScreen]），只按类型渲染不同的筛选条件——
 * 网页端也是"同一套页面组件 + 不同样式类"（抓包实证：三个 chunk 的 21 个模块里 20 个完全相同）。
 */
@Composable
fun JwxtScheduleQueryScreen(
    onBack: () -> Unit,
    onSelectType: (ScheduleTargetType) -> Unit,
) {
    val nav = LocalNavController.current
    val title = stringResource(R.string.jwxt_schedule_query_title)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    // 打开网页版：保留一条回到教务原页面的退路（与其它教务页面顶栏一致）
                    IconButton(onClick = { nav.navigate(Routes.unifiedWebViewRoute(JwxtConstants.SCHEDULE_QUERY_URL, title)) }) {
                        Icon(
                            imageVector = Icons.Outlined.OpenInBrowser,
                            contentDescription = stringResource(R.string.common_web_version),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = currentTopBarColors(),
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            ScheduleTargetType.entries.forEachIndexed { index, type ->
                if (index > 0) InfoRowDivider()
                EntryRow(title = stringResource(type.titleRes), onClick = { onSelectType(type) })
            }
            // 「我的课表」：不用选对象，从教务首页那张卡的「更多课程」过来看自己的课表
            InfoRowDivider()
            EntryRow(
                title = stringResource(R.string.jwxt_my_timetable),
                onClick = { nav.navigate(Routes.JWXT_TIMETABLE) },
            )
        }
    }
}

/** 入口页的一行：名称 + 右箭头。三种课表查询与「我的课表」共用这一套样式 */
@Composable
private fun EntryRow(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

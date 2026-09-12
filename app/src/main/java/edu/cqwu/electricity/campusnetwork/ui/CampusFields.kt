package edu.cqwu.electricity.campusnetwork.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.common.ui.InfoLabelWidth
import edu.cqwu.electricity.common.ui.InfoRow
import edu.cqwu.electricity.common.ui.InfoRowDivider
import edu.cqwu.electricity.common.ui.InfoSectionTitle

/**
 * 校园网络模块的字段列表数据模型与渲染器。
 *
 * 存在的理由：`common/ui` 已有 [InfoRow] 三件套（行级组件），但没有"一列字段"这层容器，
 * 导致接入者信息与网络服务两页各自手写 `InfoRowDivider()` + `InfoRow(...)` 展开
 * （网络服务页写 7 遍），且接入者信息页的字段分组构建与 `Resources` 绑定、无法单测。
 *
 * 本文件**不新增行组件**，只把已有的 [InfoRow] 组装成可声明式列举的列表：
 * 调用方只需给出字段表，分隔线、标签宽度、换行策略由 [KeyValueRows] 统一施加，
 * 与「电表实时状态」「订单详情」等页面的既有效果一致。
 *
 * 字段值一律保留原始可空类型，空值由 [InfoRow] 自身渲染为 `-`，
 * 与手工展开时的 `value.orEmpty()` 行为相同。
 */

/**
 * 单个展示字段：标签资源 + 取值。
 *
 * 刻意携带 [StringRes] 而不是已解析的字符串，原因有二：
 * - `stringResource` 只能在 @Composable 中调用，资源 ID 才能让字段表在 Composable 外构建（可单测）；
 * - 资源 ID 保留静态可查性，纳入项目的硬编码字符串检查范围。
 *
 * [valueRes] 用于"值本身是固定文案资源"的行（如接入者信息页的「身份命中：校园网未命中」提示）：
 * 它优先于 [value]，使固定文案也走资源解析，避免在纯函数里写死已解析字符串。
 */
data class Field(
    @param:StringRes val label: Int,
    val value: String?,
    @param:StringRes val valueRes: Int? = null,
)

/** 一个字段分组：可选标题 + 若干字段（标题为 null 时不渲染标题，仅渲染字段） */
data class FieldSection(
    @param:StringRes val title: Int? = null,
    val fields: List<Field>,
)

/** 单行字段的便捷构造：`Field(R.string.x, value)` 语义等价于原 `f(res, value)` */
fun field(@StringRes label: Int, value: String?): Field = Field(label, value)

/**
 * 字段行的统一内边距：水平 16dp（与分组标题、分隔线左缩进对齐）、垂直 8dp。
 *
 * 两个页面原本各用一套（网络服务页 10dp、接入者信息页 8dp），为减少"同一组件不同数字"
 * 已统一到 8dp；差异仅影响行高约 2dp。
 */
val FIELD_ROW_PADDING: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp)

/**
 * 字段行渲染器：逐行渲染 [fields]，行间插入细分隔线。
 *
 * 分隔线、[InfoLabelWidth] 固定标签宽度、`maxLines = Int.MAX_VALUE` 完整换行
 * 三项策略集中在此，调用方无需重复书写——这是本项目 `common/ui/InfoRow` 的既定用法。
 */
@Composable
fun KeyValueRows(
    fields: List<Field>,
    modifier: Modifier = Modifier,
    rowPadding: PaddingValues = FIELD_ROW_PADDING,
) {
    Column(modifier = modifier) {
        fields.forEachIndexed { index, f ->
            if (index > 0) InfoRowDivider()
            InfoRow(
                label = stringResource(f.label),
                value = f.valueRes?.let { stringResource(it) } ?: f.value.orEmpty(),
                modifier = Modifier.padding(rowPadding),
                labelWidth = InfoLabelWidth,
                maxLines = Int.MAX_VALUE,
            )
        }
    }
}

/**
 * 分组字段列表渲染器：逐组渲染「标题 + 字段行」。
 *
 * 供接入者信息页（识别概览 / 运营商 / 用户档案 / 公网归属地四组）使用。
 * 分组之间不插分隔线——标题自带 20dp 上间距，与手工展开时的效果一致。
 */
@Composable
fun FieldSectionList(
    sections: List<FieldSection>,
    modifier: Modifier = Modifier,
    rowPadding: PaddingValues = FIELD_ROW_PADDING,
) {
    Column(modifier = modifier) {
        sections.forEach { section ->
            section.title?.let { InfoSectionTitle(text = stringResource(it)) }
            KeyValueRows(fields = section.fields, rowPadding = rowPadding)
        }
    }
}

package edu.cqwu.electricity.electricity.ui

import java.util.Locale

/**
 * 用量报表导出/复制文本所需的全部文案（由界面层从多语言资源翻译后传入）。
 *
 * 把文案与拼装逻辑解耦：本类只存"显示什么字"，[buildUsageReportTextV2] 只负责"怎么排"，
 * 因此生成函数不依赖 Android 资源，可直接做 JVM 单元测试。
 */
data class UsageReportTextLabelsV2(
    val title: String,
    val filterDescription: String,
    val headerTime: String,
    val headerUsage: String,
    val headerCost: String,
    val total: String,
    val noData: String,
)

/**
 * 补助记录导出/复制文本所需的全部文案（由界面层从多语言资源翻译后传入）。
 */
data class SubsidyReportTextLabelsV2(
    val title: String,
    val filterDescription: String,
    val headerTime: String,
    val headerType: String,
    val headerQuantity: String,
    val headerCost: String,
    val total: String,
    val noData: String,
)

/**
 * 生成用量报表的纯文本内容（用于复制和导出）。
 *
 * @param state 用量报表页面状态（时间范围、粒度、记录与总计）
 * @param labels 预翻译文案（见 [UsageReportTextLabelsV2]）
 */
fun buildUsageReportTextV2(
    state: UsageRecordV2UiState,
    labels: UsageReportTextLabelsV2,
): String {
    val tab = state.tabs[state.granularity] ?: UsageTabContentV2()
    val sb = StringBuilder()
    sb.appendLine(labels.title)
    sb.appendLine("=".repeat(40))
    sb.appendLine(labels.filterDescription)
    sb.appendLine("-".repeat(40))

    if (tab.records.isEmpty()) {
        sb.appendLine(labels.noData)
    } else {
        sb.appendLine(
            String.format(
                "%-20s %-12s %-12s",
                labels.headerTime,
                labels.headerUsage,
                labels.headerCost
            )
        )
        sb.appendLine("-".repeat(40))
        tab.records.forEach { record ->
            sb.appendLine(
                String.format(Locale.US, "%-20s %-12.2f %-12.2f", record.costTime, record.consumeTotal, record.costTotal)
            )
        }
        sb.appendLine("-".repeat(40))
        sb.appendLine(
            String.format(
                Locale.US,
                "%-20s %-12.2f %-12.2f",
                labels.total,
                tab.totalConsume,
                tab.totalCost
            )
        )
    }

    return sb.toString()
}

/**
 * 生成补助记录的纯文本内容（用于复制和导出）。
 *
 * @param state 补助记录页面状态（时间范围、记录与总计）
 * @param labels 预翻译文案（见 [SubsidyReportTextLabelsV2]）
 * @param feeTypeName 补助类型数字 → 显示文案的映射（界面层传入，如 0/20=电费）
 */
fun buildSubsidyReportTextV2(
    state: SubsidyRecordUiState,
    labels: SubsidyReportTextLabelsV2,
    feeTypeName: (Int) -> String,
): String {
    val sb = StringBuilder()
    sb.appendLine(labels.title)
    sb.appendLine("=".repeat(40))
    sb.appendLine(labels.filterDescription)
    sb.appendLine("-".repeat(40))

    if (state.records.isEmpty()) {
        sb.appendLine(labels.noData)
    } else {
        sb.appendLine(
            String.format(
                "%-20s %-10s %-12s %-12s",
                labels.headerTime,
                labels.headerType,
                labels.headerQuantity,
                labels.headerCost
            )
        )
        sb.appendLine("-".repeat(40))
        state.records.forEach { record ->
            sb.appendLine(
                String.format(
                    Locale.US,
                    "%-20s %-10s %-12.2f %-12.2f",
                    record.subsidyTime,
                    feeTypeName(record.feeType),
                    record.quantity,
                    record.amount
                )
            )
        }
        sb.appendLine("-".repeat(40))
        sb.appendLine(
            String.format(
                Locale.US,
                "%-20s %-10s %-12.2f %-12.2f",
                labels.total,
                "",
                state.totalQuantity,
                state.totalAmount
            )
        )
    }

    return sb.toString()
}

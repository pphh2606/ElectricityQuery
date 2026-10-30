package edu.cqwu.electricity.common.settings

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 当前界面风格：Material 3 还是 MIUI。
 *
 * 由主题层（`电费查询Theme`）在顶部 provide，组件层（顶栏、底栏、列表、弹窗等）
 * 读它决定渲染哪套实现，页面代码不必在每个调用点写 `if (uiStyle == MIUI)`。
 *
 * 之所以不直接读 [LocalAppSettingsState]，是想让「组件要知道当前风格」这件事只有一个入口：
 * 以后即便风格来源变了（比如跟随系统、按页面强制），也只改主题层的 provide。
 */
val LocalUiStyle = staticCompositionLocalOf { UiStyle.MATERIAL3 }

package edu.cqwu.electricity.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.vector.ImageVector
import edu.cqwu.electricity.R

/**
 * 底栏 Tab 数据模型
 * 未来增加 Tab 只需在此列表中添加一条
 */
data class BottomNavTab(
    @StringRes val labelRes: Int,
    val route: String,
    val icon: ImageVector,
)

/** 底栏 Tab 定义列表：新增 Tab 只需在此添加一项 */
val bottomNavTabs = listOf(
    BottomNavTab(labelRes = R.string.home_title, route = Routes.MAIN_TABS, icon = Icons.Outlined.Home),
    BottomNavTab(labelRes = R.string.hall_title, route = Routes.MAIN_TABS, icon = Icons.Outlined.Apps),
    BottomNavTab(labelRes = R.string.profile_title, route = Routes.MAIN_TABS, icon = Icons.Outlined.Person),
)

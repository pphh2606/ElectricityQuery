package edu.cqwu.electricity.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
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
    BottomNavTab(labelRes = R.string.home_title, route = Routes.MAIN_TABS, icon = Icons.Default.Home),
    BottomNavTab(labelRes = R.string.hall_title, route = Routes.MAIN_TABS, icon = Icons.Default.Apps),
    BottomNavTab(labelRes = R.string.profile_title, route = Routes.MAIN_TABS, icon = Icons.Default.Person),
)

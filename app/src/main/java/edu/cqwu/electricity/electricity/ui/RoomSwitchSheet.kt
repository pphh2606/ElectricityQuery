package edu.cqwu.electricity.electricity.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import edu.cqwu.electricity.common.navigation.LocalNavController
import edu.cqwu.electricity.common.navigation.Routes
import edu.cqwu.electricity.common.ui.BottomSheetDialogV2
import edu.cqwu.electricity.common.ui.BottomSheetItem
import edu.cqwu.electricity.common.ui.SheetItemGroup
import edu.cqwu.electricity.electricity.data.UserRoomInfo

/**
 * 房间选择弹窗：「我的寝室」页面与充值 Tab 共用。
 *
 * 两处原本各抄一份（标题、提示、绑定/解绑入口、房间列表），只有标题与提示文案不同，
 * 所以文案由调用方传入，绑定/解绑入口固定在弹窗两侧。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoomSwitchSheet(    visible: Boolean,
    title: String,
    hint: String,
    rooms: List<UserRoomInfo>,
    onSelect: (UserRoomInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val nav = LocalNavController.current
    val bindLabel = stringResource(R.string.electricity_bind_account)
    val unbindLabel = stringResource(R.string.electricity_unbind_account)

    /** 打开绑定/解绑页：与原先两处实现一致，先关弹窗再进内置浏览器 */
    fun openAccountPage(path: String, label: String) {
        onDismiss()
        nav.navigate(Routes.unifiedWebViewRoute(ACCOUNT_PAGE_BASE + path, label))
    }

    BottomSheetDialogV2(
        visible = visible,
        onDismissRequest = onDismiss,
        title = title,
        leadingButton = {
            TextButton(
                shapes = ButtonDefaults.shapes(),
                onClick = { openAccountPage("user-add", bindLabel) },
            ) {
                Text(bindLabel)
            }
        },
        trailingButton = {
            TextButton(
                shapes = ButtonDefaults.shapes(),
                onClick = { openAccountPage("user-del", unbindLabel) },
            ) {
                Text(unbindLabel)
            }
        },
    ) {
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        SheetItemGroup {
            rooms.forEach { room ->
                item {
                    BottomSheetItem(
                        icon = Icons.Outlined.Home,
                        title = room.fullName.ifBlank { room.roomName },
                        onClick = { onSelect(room) },
                    )
                }
            }
        }
    }
}

/** 绑定/解绑账号的 H5 页面前缀 */
private const val ACCOUNT_PAGE_BASE = "https://electricitypay.cqwu.edu.cn/wxms/pages/user/"

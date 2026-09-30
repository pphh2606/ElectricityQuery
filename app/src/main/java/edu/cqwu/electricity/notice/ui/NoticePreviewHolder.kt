package edu.cqwu.electricity.notice.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import edu.cqwu.electricity.notice.data.NoticeItem

/**
 * 「待打开的那条通知」的预览数据，用于详情页在详情接口返回前预渲染头部。
 *
 * 为什么需要它：通知列表页与详情页是两个独立的导航目的地，各自持有自己的 ViewModel。
 * 列表 ViewModel 挂在列表目的地的 `NavBackStackEntry` 作用域上，出栈即销毁
 * （Navigation 的默认行为，也是「退出再进入会重新加载」的实现方式），
 * 因此它没有机会把「用户点的是哪一条」交给详情页。
 *
 * 于是把这一条数据单独提升出来：它是通知模块**唯一**跨页面存活的状态，
 * 只保存一个 [NoticeItem]，**不含列表数据与分页状态**，避免把列表的生命周期一起带上。
 * 在 `AppNavGraph` 中创建，与其它模块级共享状态一样装配进导航图。
 *
 * 进程重建后这里会变回 null，详情页自然退化为「整屏加载中」，不会显示错误的旧数据。
 */
class NoticePreviewHolder : ViewModel() {

    var preview by mutableStateOf<NoticeItem?>(null)
        private set

    /** 记录用户点击的通知（导航到详情页之前调用） */
    fun select(item: NoticeItem) {
        preview = item
    }

    /** 取出与 [wid] 匹配的预览数据；不匹配（例如换了一条通知）则返回 null */
    fun previewFor(wid: String): NoticeItem? = preview?.takeIf { it.wid == wid }
}

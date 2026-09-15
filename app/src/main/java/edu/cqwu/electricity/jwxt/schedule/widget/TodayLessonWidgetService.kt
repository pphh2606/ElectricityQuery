package edu.cqwu.electricity.jwxt.schedule.widget

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import edu.cqwu.electricity.R
import edu.cqwu.electricity.jwxt.schedule.data.JwxtLessonUi
import edu.cqwu.electricity.jwxt.schedule.data.TodayLessonRepository

/**
 * 集合小组件的列表数据源。
 *
 * 小组件要能上下滑动，只能用集合组件（ListView）——RemoteViews 里的其它控件收不到滑动手势。
 * launcher 通过本 Service 取列表数据；Service 跑在我们自己的进程里，所以可以直接读缓存，
 * 不需要用 Intent 搬运数据。
 */
class TodayLessonWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        TodayLessonWidgetFactory(applicationContext)
}

/** 列表数据工厂：把缓存里的今日课程逐条转成 RemoteViews */
private class TodayLessonWidgetFactory(private val context: Context) :
    RemoteViewsService.RemoteViewsFactory {

    /** 单条课程正文最多显示的行数（接口里「上课班级」那一行可能很长） */
    private val maxBodyLines = 2

    /** 在 [onDataSetChanged] 里读一次缓存，滑动过程中不再碰文件 */
    private var lessons: List<JwxtLessonUi> = emptyList()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        lessons = TodayLessonRepository.cached()?.lessons.orEmpty()
    }

    override fun onDestroy() {
        lessons = emptyList()
    }

    override fun getCount(): Int = lessons.size

    override fun getViewAt(position: Int): RemoteViews {
        val item = RemoteViews(context.packageName, R.layout.widget_today_lesson_item)
        val lesson = lessons.getOrNull(position) ?: return item

        item.setTextViewText(
            R.id.tv_lesson_title,
            context.getString(R.string.jwxt_lesson_section_time, lesson.sectionRange, lesson.timeRange),
        )
        item.setTextViewText(
            R.id.tv_lesson_body,
            lesson.lines.take(maxBodyLines).joinToString("\n") { it.text },
        )

        val tagLabel = transferTagLabel(context, lesson.transferTag)
        item.setViewVisibility(R.id.tv_lesson_tag, if (tagLabel == null) View.GONE else View.VISIBLE)
        if (tagLabel != null) item.setTextViewText(R.id.tv_lesson_tag, tagLabel)

        // 列表项也要能点进 App：真正的跳转 PendingIntent 由列表的 template 提供，
        // 这里只给一个空的填充 Intent，launcher 会把两者合并
        item.setOnClickFillInIntent(R.id.item_root, Intent())
        return item
    }

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true

    override fun getViewTypeCount(): Int = 1

    override fun getLoadingView(): RemoteViews? = null
}

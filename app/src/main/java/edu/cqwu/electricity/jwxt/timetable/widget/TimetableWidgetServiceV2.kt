package edu.cqwu.electricity.jwxt.timetable.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import edu.cqwu.electricity.R
import edu.cqwu.electricity.jwxt.timetable.data.TimetableWeekLesson
import edu.cqwu.electricity.jwxt.timetable.data.TimetableWidgetRepositoryV2
import edu.cqwu.electricity.jwxt.timetable.domain.TimetableToday

/**
 * 集合小组件的列表数据源。
 *
 * 小组件要能上下滑动，只能用集合组件（ListView）——RemoteViews 里的其它控件收不到滑动手势。
 * launcher 通过本 Service 取列表数据；Service 跑在我们自己的进程里，所以可以直接读缓存，
 * 不需要用 Intent 搬运数据。
 */
class TimetableWidgetServiceV2 : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        TimetableWidgetFactoryV2(applicationContext)
}

/**
 * 列表数据工厂：把缓存里**今天的**课逐条转成 RemoteViews（课程 / 教室·老师 / 节次·时间 三行）。
 *
 * 注意它自己筛"今天"而不是直接用缓存内容——缓存存的是整周，
 * 而集合列表可能在跨天之后被系统重新拉取（这份数据必须与缓存同一口径）。
 */
private class TimetableWidgetFactoryV2(private val context: Context) :
    RemoteViewsService.RemoteViewsFactory {

    /** 在 [onDataSetChanged] 里读一次缓存，滑动过程中不再碰文件 */
    private var lessons: List<TimetableWeekLesson> = emptyList()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        val payload = TimetableWidgetRepositoryV2.cached()
        lessons = payload
            ?.let { TimetableWidgetRepositoryV2.lessonsOf(it, TimetableToday.dayOfWeek()) }
            .orEmpty()
    }

    override fun onDestroy() = Unit

    override fun getCount(): Int = lessons.size

    override fun getViewAt(position: Int): RemoteViews {
        val item = RemoteViews(context.packageName, R.layout.widget_timetable_item_v2)
        val lesson = lessons.getOrNull(position) ?: return item

        item.setTextViewText(R.id.tv_lesson_course, lesson.courseName)
        item.setTextViewText(R.id.tv_lesson_meta, metaText(lesson))
        item.setTextViewText(R.id.tv_lesson_time, timeText(lesson))

        // 列表项也要能点进 App：真正的跳转 PendingIntent 由列表的 template 提供，
        // 这里只给一个空的填充 Intent，launcher 会把两者合并
        item.setOnClickFillInIntent(R.id.item_root, Intent())
        return item
    }

    /** 第二行「教室 · 老师」；接口偶尔缺其中之一，缺谁就只显示另一个，不留孤零零的分隔符 */
    private fun metaText(lesson: TimetableWeekLesson): String = when {
        lesson.classroom.isEmpty() -> lesson.teacher
        lesson.teacher.isEmpty() -> lesson.classroom
        else -> context.getString(R.string.widget_lesson_room_teacher, lesson.classroom, lesson.teacher)
    }

    /**
     * 第三行「1-2 节（08:10-09:50）」。
     *
     * 课表接口不给节次时间，时间是从正文里抠的（见 `timeRangeOf`）；
     * 抠不到就退化成只显示节次，而不是留一对空括号。
     */
    private fun timeText(lesson: TimetableWeekLesson): String {
        val sections = "${lesson.beginSection}-${lesson.endSection}"
        return if (lesson.timeRange.isEmpty()) {
            context.getString(R.string.widget_timetable_v2_section_only, sections)
        } else {
            context.getString(R.string.jwxt_lesson_section_time, sections, lesson.timeRange)
        }
    }

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true

    override fun getViewTypeCount(): Int = 1

    override fun getLoadingView(): RemoteViews? = null
}

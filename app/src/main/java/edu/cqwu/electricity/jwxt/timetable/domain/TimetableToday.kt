package edu.cqwu.electricity.jwxt.timetable.domain

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 「今天」在课表里的含义。
 *
 * 课表接口给的是「某一周的整周课表」，**没有"今天"这个概念**，所以下面两件事要本地算：
 * 今天是星期几（对应课表第几列）、今天属于哪一个自然周（决定缓存还新不新）。
 *
 * 全是纯函数：不联网、不碰 Android API（只用 Calendar / SimpleDateFormat），可直接 JVM 单测。
 * minSdk 21 用不了 `java.time`，故用 Calendar。
 */
object TimetableToday {

    private const val DATE_PATTERN = "yyyy-MM-dd"

    /** 设备当前日期 `yyyy-MM-dd` */
    fun dateString(now: Date = Date()): String = formatter().format(now)

    /**
     * 今天是课表的第几列：周一 = 1 … 周日 = 7（与接口 `dayOfWeek` 同一套下标）。
     *
     * `Calendar` 的 `DAY_OF_WEEK` 是「周日 = 1 … 周六 = 7」，与课表既差一天、起点也不同，
     * 故用 `(dayOfWeek + 5) % 7 + 1` 换算：周日(1)→7、周一(2)→1、周六(7)→6。
     */
    fun dayOfWeek(now: Date = Date()): Int {
        val calendarDay = Calendar.getInstance().apply { time = now }.get(Calendar.DAY_OF_WEEK)
        return (calendarDay + 5) % 7 + 1
    }

    /**
     * [now] 所在自然周的周一日期 `yyyy-MM-dd`。
     *
     * 缓存里存的是某一周的课表，而小组件在桌面上跑、**不能联网问"现在是第几周"**；
     * 所以缓存记下那一周的周一，之后在本地比较这个字符串就能判断有没有跨周——
     * 同一周内跨天（比如周一存、周三看）照样能正确显示今天的课。
     * 周一为一周之首，与课表下标一致（周日属于它前面那个周一开头的这一周）。
     */
    fun weekStartDateString(now: Date = Date()): String {
        val calendar = Calendar.getInstance().apply {
            time = now
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }
        return formatter().format(calendar.time)
    }

    /** 每次新建：SimpleDateFormat 不是线程安全的，而这里的调用频率极低（一次刷新一次） */
    private fun formatter() = SimpleDateFormat(DATE_PATTERN, Locale.US)
}

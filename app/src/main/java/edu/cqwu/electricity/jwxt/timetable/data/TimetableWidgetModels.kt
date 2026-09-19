package edu.cqwu.electricity.jwxt.timetable.data

/**
 * 「今日课表」小组件的一条课。
 *
 * 注意它带 [dayOfWeek]——缓存里存的是**整周**的课而不是"筛好的今天"，因为小组件在桌面上跑，
 * 跨天时不一定有机会联网重取；存整周就能在本地重新筛出"今天"。
 */
data class TimetableWeekLesson(
    /** 课表第几列：周一 = 1 … 周日 = 7 */
    val dayOfWeek: Int,
    val beginSection: Int,
    val endSection: Int,
    /** 形如 `08:10-09:50`；课表接口没有时间字段，从正文抠，抠不到是空串（那时只显示节次） */
    val timeRange: String,
    /** 课程 / 教学班名（课表接口直接给，不必像「今日课程」那样从正文猜） */
    val courseName: String,
    /** 任课教师（正文里 `data-kblx="02"` 的锚文本）；空串表示接口没给 */
    val teacher: String,
    /** 上课教室（正文里 `data-kblx="01"` 的锚文本，没有则退回接口的 `placeName`） */
    val classroom: String,
)

/**
 * 「今日课表」小组件的缓存载荷。
 *
 * [weekStartDate] 是这一周的**周一**日期：小组件据此在本地判断缓存是否还属于"当前这一周"
 * （同一周内跨天继续可用，跨周即失效），**不需要联网问"现在是第几周"**。
 */
data class TimetableWidgetPayload(
    val version: Int = TimetableWidgetCacheV2.VERSION,
    /** 保存时激活的账号 id：换账号后视为过期，避免把别人的课表显示在自己桌面上 */
    val accountId: String = "",
    val termCode: String = "",
    /** 教学周序号（`curWeek` 为 true 的那一周的 `serialNumber`） */
    val weekSerial: Int = 0,
    /** 这一周的周一日期 `yyyy-MM-dd` */
    val weekStartDate: String = "",
    /**
     * 写缓存的时刻（epoch millis）。桌面顶部显示成 `HH:mm 更新`，用来判断
     * 「桌面上的课表是多久以前取的」——因为缓存整周、同一周内不重取，它会真实地变旧。
     * 0 表示还没有缓存（或旧版本缓存没有这个字段）。
     */
    val savedAt: Long = 0L,
    /** 整周的课（已按天、按开始节次排序） */
    val lessons: List<TimetableWeekLesson> = emptyList(),
)

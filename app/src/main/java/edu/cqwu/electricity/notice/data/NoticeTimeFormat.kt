package edu.cqwu.electricity.notice.data

/**
 * 通知公告时间显示的统一格式化。
 *
 * 两个接口对同一条通知给出的时间字段并不一致：
 * - 列表接口 `getUseNoticePage.do` → [NoticeItem.sendTimeDesc] 有值，形如 `2026年9月24日 13:03`；
 * - 详情接口 `getOneNoticeInfo.do` → 实测 `sendTimeDesc` 为空，只有 [NoticeDetailQp.sendTime]，
 *   形如 `2026-09-24 13:03:00`。
 *
 * 若两处各自兜底（旧实现就是列表直接用 `sendTimeDesc`、详情用 `sendTime.take(16)`），
 * 详情页会出现「列表中文格式 → 详情短横线格式」的跳变；而详情页现在会先用列表数据预渲染
 * 再用详情数据替换，这个跳变会从「页面加载前后各看一次」变成「同一屏上肉眼可见地闪一下」，
 * 因此两个页面必须共用本函数，保证同一条通知在两个接口下输出逐字符相同。
 *
 * 规则：
 * 1. [sendTime] 能解析出日期 → 统一输出 `yyyy年M月d日 HH:mm`（无时分时只输出日期）；
 * 2. 解析不出 → 退回 [sendTimeDesc] 原文（服务端可能给出无法反解的相对描述）；
 * 3. 都为空 → 空串。
 *
 * 之所以**优先 [sendTime] 而不是 [sendTimeDesc]**：详情接口的 `sendTimeDesc` 为空，
 * 只有以 `sendTime` 为准，两侧才能得到同一个结果；反过来则必然跳变。
 *
 * 不用 `SimpleDateFormat`：输入格式由服务端决定（可能是 `-`、`/`、`.` 分隔或带 `T`），
 * 正则容错更省事，也避免多语言环境下 `Locale` 影响输出。
 */
private val SEND_TIME_REGEX =
    Regex("""(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})(?:[ T](\d{1,2}):(\d{2}))?""")

fun formatNoticeTime(sendTime: String?, sendTimeDesc: String?): String {
    val match = sendTime?.let { SEND_TIME_REGEX.find(it.trim()) }
    if (match != null) {
        val year = match.groupValues[1]
        val month = match.groupValues[2].toIntOrNull()
        val day = match.groupValues[3].toIntOrNull()
        if (month != null && day != null) {
            val hour = match.groupValues[4]
            val minute = match.groupValues[5]
            val date = "${year}年${month}月${day}日"
            return if (hour.isEmpty()) date else "$date ${hour.padStart(2, '0')}:$minute"
        }
    }
    return sendTimeDesc?.trim().orEmpty()
}

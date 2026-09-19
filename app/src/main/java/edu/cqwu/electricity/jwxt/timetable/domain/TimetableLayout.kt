package edu.cqwu.electricity.jwxt.timetable.domain

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.cqwu.electricity.jwxt.core.model.JwxtTimetableCourse

/**
 * 课表网格的布局算法：撞课分轨、每节行高、一个格子能放几行正文。
 *
 * 从 `TimetableScreen.kt` 抽到 domain 层：这些是**纯计算**（不碰 Compose 运行时，只用 Dp / TextUnit
 * 这类数值类型），所以能直接写 JVM 单测；此前它们埋在 770 行的界面文件里，零测试覆盖。
 * 界面从此只负责"按算好的位置画出来"。
 */

// ── 布局参数（下面的算法与界面渲染共用同一份，避免两处各写一个数）──

/** 每天一列的基准宽度：照网页版「一屏约 5 天」，其余靠横向滑动——这样中文课名才放得下 */
internal val DAY_COLUMN_WIDTH = 64.dp

/** 卡片与格子边缘的间隙 */
internal val CARD_GAP = 1.dp

/** 卡片左右内边距（算正文可用宽度时要用） */
internal val CARD_HORIZONTAL_PADDING = 4.dp

/** 卡片上下内边距（算行高时要用） */
internal val CARD_CONTENT_PADDING = 3.dp

/** 卡片正文行高（网页版是 11px 字配 16px 行高）：用来算一个格子里能放几行 */
internal val CARD_LINE_HEIGHT = 16.sp

/** 每一节最少能放几行正文：内容少时行高按它来，保证空行也有基本高度 */
internal const val BASE_CARD_LINES = 3

/** 一门课的位置：在第几条轨道上，以及**它所在那一组**共有几条轨道（组内平分列宽用） */
internal data class LaneSlot(val course: JwxtTimetableCourse, val lane: Int, val laneCount: Int)

/** 一天的排布结果：[slots] 是每门课的位置，[laneCount] 是当天最挤的一组有几条轨道（= 列宽倍数） */
internal class DayLanes(val slots: List<LaneSlot>, val laneCount: Int) {
    /** 这一列的宽度：同一天最挤的那一组有几门课并排，这一列就宽几倍 */
    val width: Dp get() = DAY_COLUMN_WIDTH * laneCount
}

/**
 * 一天的排布：按开始节次排序后切成若干「重叠组」（互相重叠的课连成一片），**每组各自分轨**。
 *
 * 组内逐门放进「第一条不与已放课程重叠的轨道」，每门课记下自己那组的轨道数——
 * 页面据此让组内平分列宽（3 门各占 1/3、2 门各占 1/2、单独一门占满），与网页版一致。
 * 整列宽度取「所有组里轨道数最多的那组」，所以别的组再挤也不会把这一组拖窄。
 */
internal fun layoutDay(courses: List<JwxtTimetableCourse>): DayLanes {
    val slots = mutableListOf<LaneSlot>()
    var laneCount = 1

    val cluster = mutableListOf<JwxtTimetableCourse>()
    var clusterEnd = 0

    fun flushCluster() {
        if (cluster.isEmpty()) return

        val lanes = mutableListOf<MutableList<JwxtTimetableCourse>>()
        cluster.forEach { course ->
            // 轨道内课程按开始节次递增放入，结束节次随之严格递增，所以只比最后一个即可
            val lane = lanes.firstOrNull { it.last().endSection < course.beginSection }
                ?: mutableListOf<JwxtTimetableCourse>().also { lanes += it }
            lane += course
        }
        lanes.forEachIndexed { index, lane ->
            lane.forEach { slots += LaneSlot(it, index, lanes.size) }
        }
        laneCount = maxOf(laneCount, lanes.size)
        cluster.clear()
    }

    courses.sortedBy { it.beginSection }.forEach { course ->
        // 与当前这组完全不重叠，就收束这一组、另起一组
        if (cluster.isNotEmpty() && course.beginSection > clusterEnd) flushCluster()
        cluster += course
        clusterEnd = maxOf(clusterEnd, course.endSection)
    }
    flushCluster()

    return DayLanes(slots, laneCount)
}

/** 这个高度能放几行正文（行高已按字体缩放换算过） */
internal fun cardMaxLines(height: Dp, lineHeight: Dp): Int =
    ((height - (CARD_GAP + CARD_CONTENT_PADDING) * 2) / lineHeight).toInt().coerceAtLeast(1)

/**
 * 每一节的行高：取「这一节里内容最长的那个格子需要几行」。
 *
 * [lineCounts] 是每门课的正文在该列宽下会折几行（由界面用 TextMeasurer 量出来，与 [courses]
 * 一一对应）：用**下标**对应而不是建 Map —— Map 会调用课程对象的 hashCode()，
 * 万一某个字段混进 null（Gson 塞 null 是本项目踩过的坑）就会崩。
 *
 * **只抬高确实需要的那一行**——不像网页版那样取全局最高、把整张表一起撑高留出大片空白。
 * 高度既然按内容算够了，卡片上就不会出现省略号。
 */
internal fun sectionRowHeights(
    courses: List<JwxtTimetableCourse>,
    lineCounts: List<Int>,
    sectionCount: Int,
    lineHeight: Dp,
    verticalPadding: Dp,
): List<Dp> = List(sectionCount) { index ->
    val section = index + 1
    var neededLines = 0
    courses.forEachIndexed { courseIndex, course ->
        if (section in course.beginSection..course.endSection) {
            val span = (course.endSection - course.beginSection + 1).coerceAtLeast(1)
            // 跨多节的课：它的总行数摊到跨越的每一节上（向上取整）
            neededLines = maxOf(neededLines, (lineCounts[courseIndex] + span - 1) / span)
        }
    }
    lineHeight * maxOf(neededLines, BASE_CARD_LINES) + verticalPadding
}

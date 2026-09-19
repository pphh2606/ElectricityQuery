package edu.cqwu.electricity.jwxt.core.model

/**
 * 接口正文里的一行富文本（`cellDetail` / `titleDetail` 的来源行）。
 *
 * 被「今日课程」与「周课表」两套接口共用（两组接口这一层结构完全一致），所以放在共享层。
 * [color] 非空表示网页用红字标出（教师 / 时间 / 地点行）；[text] 里可能含 `<a data-kblx=…>` 标签。
 */
data class JwxtCellLine(
    val color: String? = null,
    val text: String = "",
)

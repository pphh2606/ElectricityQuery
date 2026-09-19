package edu.cqwu.electricity.jwxt.moreservice.data

import edu.cqwu.electricity.jwxt.core.model.JwxtService

/**
 * 「更多服务」接口（`biz/home/listLabelServiceSet`）的数据模型。
 *
 * 字段取自 `fortest/教务系统.har.json` 的真实响应，**页面用不到的字段不声明**。
 */

/** `GET biz/home/listLabelServiceSet` 响应（单层信封，与首页两个接口同款） */
data class LabelServiceSetResponse(
    val code: Int = 0,
    val msg: String = "",
    val data: List<JwxtServiceGroup>? = null,
)

/**
 * 一个分组（全部 / 查询 / 申请）。
 *
 * 注意：本接口的 `serviceList` 装的是**分类**而不是服务——比首页 `listLabelService` 多一层，
 * 所以类型是 [JwxtServiceCategory]。
 */
data class JwxtServiceGroup(
    val labelName: String = "",
    val labelId: String = "",
    val serviceNum: Int = 0,
    val serviceList: List<JwxtServiceCategory>? = null,
)

/** 分组下的一个分类（学籍 / 选课 / 考务 / 其他 / 评教）；服务字段与首页一致，复用 [JwxtService] */
data class JwxtServiceCategory(
    val categoryName: String = "",
    val serviceList: List<JwxtService>? = null,
)

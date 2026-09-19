package edu.cqwu.electricity.jwxt.moreservice.data

import edu.cqwu.electricity.jwxt.core.net.JwxtApiBase

/** 「更多服务」的接口声明；发请求的规矩在 [JwxtApiBase] / `JwxtHttpClient` */
class MoreServiceApi : JwxtApiBase() {

    /**
     * 全部分组（单层信封；`data` 是分组数组，分组里再按分类装服务）。
     *
     * 与首页 `HomeApi.fetchLabelServices` 相比只多了一层分类，请求方式完全一致。
     */
    suspend fun fetchServiceSets(): Result<List<JwxtServiceGroup>> =
        request("biz/home/listLabelServiceSet", isPost = false) { json ->
            val resp = gson.fromJson(json, LabelServiceSetResponse::class.java)
            checkBusinessCode(resp.code, resp.msg, "listLabelServiceSet")
            resp.data ?: emptyList()
        }
}

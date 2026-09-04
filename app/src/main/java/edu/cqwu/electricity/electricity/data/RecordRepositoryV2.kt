package edu.cqwu.electricity.electricity.data

/**
 * 用量报表数据粒度 v2（替代魔法数字 0/1/2）。
 *
 * - [HOURLY]：小时数据（接口 dataType=0）
 * - [DAILY]：每日数据（接口 dataType=1，页面默认）
 * - [MONTHLY]：每月数据（接口 dataType=2）
 *
 * 枚举顺序同时是顶部 Tab 的排列顺序，可用 [ordinal] 对应 Tab 下标。
 */
enum class UsageGranularityV2(val apiValue: Int) {
    HOURLY(0),
    DAILY(1),
    MONTHLY(2),
    ;

    companion object {
        /** 由顶部 Tab 下标取粒度；越界时回退到默认"每日" */
        fun fromTabIndex(index: Int): UsageGranularityV2 =
            entries.getOrElse(index) { DAILY }
    }
}

/**
 * 记录查询结果 v2（仓库统一出口）。
 *
 * 把“服务器是否成功 / 失败原因”归一为两种结果，ViewModel 只处理两个分支：
 * - [Success]：查询成功，携带记录列表（空列表表示该区间无数据）
 * - [Failure]：查询失败，携带可直接展示给用户的错误文案
 *
 * @param T 单条记录类型（如 [UsageRecordV2] / [SubsidyRecord]）
 */
sealed interface RecordQueryResultV2<out T> {
    data class Success<T>(val records: List<T>) : RecordQueryResultV2<T>
    /**
     * 失败：携带可直接展示的文案（服务器返回或网络层文本，可空）。
     * 空文案表示"通用失败"，由 ViewModel 映射为本地资源文案（多语言）。
     */
    data class Failure(val message: String?) : RecordQueryResultV2<Nothing>
}

/**
 * 房间记录查询仓库 v2（规范分层：Repository 隔离网络细节）。
 *
 * 用途：按“房间 + 数据粒度 + 起止日期”查询用量报表 / 补助记录，
 * ViewModel 只依赖本仓库、不再直接接触 [ElectricityApi]，
 * 因此单元测试时可用假仓库注入 ViewModel，无需真实联网。
 */
class RecordRepositoryV2(
    private val api: ElectricityApi = ElectricityApi(),
) {

    /**
     * 查询用量报表（用电明细）记录。
     *
     * @param roomId 房间号
     * @param granularity 数据粒度（小时/每日/每月，见 [UsageGranularityV2]）
     * @param beginTime 起始日期 yyyy-MM-dd
     * @param endTime 结束日期 yyyy-MM-dd
     */
    suspend fun queryUsageRecordsV2(
        roomId: String,
        granularity: UsageGranularityV2,
        beginTime: String,
        endTime: String,
    ): RecordQueryResultV2<UsageRecordV2> {
        return api.queryRoomUsageDataV2(
            roomId = roomId,
            dataType = granularity.apiValue,
            beginTime = beginTime,
            endTime = endTime,
        ).fold(
            onSuccess = { resp ->
                if (resp.ifSuccess != "Y") {
                    // 服务器业务失败：原文案直接透传（可能为空 → 界面显示通用失败）
                    RecordQueryResultV2.Failure(resp.resultMsg)
                } else {
                    RecordQueryResultV2.Success(resp.costObj ?: emptyList())
                }
            },
            onFailure = { e ->
                RecordQueryResultV2.Failure(e.localizedMessage)
            },
        )
    }

    /**
     * 查询补助记录。
     *
     * @param roomId 房间号
     * @param beginTime 起始日期 yyyy-MM-dd
     * @param endTime 结束日期 yyyy-MM-dd
     */
    suspend fun querySubsidyRecordsV2(
        roomId: String,
        beginTime: String,
        endTime: String,
    ): RecordQueryResultV2<SubsidyRecord> {
        return api.queryRoomSubsidyData(
            roomId = roomId,
            beginTime = beginTime,
            endTime = endTime,
        ).fold(
            onSuccess = { resp ->
                if (resp.ifSuccess != "Y") {
                    // 服务器业务失败：原文案直接透传（可能为空 → 界面显示通用失败）
                    RecordQueryResultV2.Failure(resp.resultMsg)
                } else {
                    RecordQueryResultV2.Success(resp.subsidyObj ?: emptyList())
                }
            },
            onFailure = { e ->
                RecordQueryResultV2.Failure(e.localizedMessage)
            },
        )
    }
}

package edu.cqwu.electricity.profile.data

import com.google.gson.annotations.SerializedName

/**
 * 学生个人信息数据类。
 *
 * 对应 campusphere.net 的 getStuMainMustInfos API 响应中的 JSON 字段。
 *
 * JSON 示例：
 * ```json
 * {
 *   "code": "0",
 *   "message": "SUCCESS",
 *   "datas": [{
 *     "dwmc": "信息学院",
 *     "sex": "男",
 *     "mobile": "138****8888",
 *     "degree": "本科生",
 *     "photo": "",
 *     "schoolZone": "",
 *     "userName": "张三",
 *     "zymc": "计算机科学与技术",
 *     "userId": "202400000001",
 *     "bjmc": "24计算机1班",
 *     "grade": "2024",
 *     "accessCampus": 1,
 *     "userType": "在校生",
 *     "userWid": 1000000000,
 *     "classWid": 200000000
 *   }]
 * }
 * ```
 */
data class StudentInfo(
    /** 姓名 */
    @SerializedName("userName") val userName: String = "",
    /** 学号 */
    @SerializedName("userId") val userId: String = "",
    /** 学院名称 */
    @SerializedName("dwmc") val dwmc: String = "",
    /** 专业名称 */
    @SerializedName("zymc") val zymc: String = "",
    /** 班级名称 */
    @SerializedName("bjmc") val bjmc: String = "",
    /** 年级 */
    @SerializedName("grade") val grade: String = "",
    /** 性别 */
    @SerializedName("sex") val sex: String = "",
    /** 手机号 */
    @SerializedName("mobile") val mobile: String = "",
    /** 学历（如 "本科生"） */
    @SerializedName("degree") val degree: String = "",
    /** 用户类型（如 "在校生"） */
    @SerializedName("userType") val userType: String = "",
    /** 照片 URL */
    @SerializedName("photo") val photo: String = "",
    /** 校区 */
    @SerializedName("schoolZone") val schoolZone: String = "",
    /** 访问校区标识 */
    @SerializedName("accessCampus") val accessCampus: Int = 0,
    /** 用户 WID */
    @SerializedName("userWid") val userWid: Long = 0,
    /** 班级 WID */
    @SerializedName("classWid") val classWid: Long = 0,
)

/**
 * 学生信息 API 响应外层结构
 */
internal data class StudentInfoResponse(
    @SerializedName("code") val code: String = "",
    @SerializedName("message") val message: String = "",
    @SerializedName("datas") val datas: List<StudentInfo>? = null,
)

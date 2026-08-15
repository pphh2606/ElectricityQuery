package edu.cqwu.electricity.update.data

data class UpdateInfo(
    val app: App,
) {
    data class App(
        val version: String?,
        val versionCode: Long,
        val extra: Extra?,
        val link: String?,
        val note: String?,
    )

    data class Extra(
        val target: Int?,
        val min: Int?,
        val compile: Int?,
        val packageSize: Long?,
    )
}

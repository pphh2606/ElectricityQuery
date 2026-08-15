package edu.cqwu.electricity.update.data

data class UpdateDownloadLink(
    val label: String,
    val url: String,
)

object UpdateDownloadLinks {
    fun create(originalLink: String): List<UpdateDownloadLink> {
        val fileName = originalLink.substringAfterLast('/')
        if (fileName.isBlank()) return emptyList()
        return UpdateMirrorSources.downloadLinks(fileName)
    }
}

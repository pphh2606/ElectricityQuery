package edu.cqwu.electricity.update.data

import edu.cqwu.electricity.R

data class UpdateDownloadLink(
    val labelRes: Int,
    val url: String,
)

object UpdateDownloadLinks {
    private const val ASSETS_OWNER = "pphh2606"
    private const val ASSETS_REPO = "ElectricityQuery-assets"
    private const val ASSETS_BRANCH = "main"

    fun create(originalLink: String): List<UpdateDownloadLink> {
        val fileName = originalLink.substringAfterLast('/')
        if (fileName.isBlank()) return emptyList()

        return listOf(
            UpdateDownloadLink(
                labelRes = R.string.update_download_source_github_raw,
                url = "https://raw.githubusercontent.com/$ASSETS_OWNER/$ASSETS_REPO/$ASSETS_BRANCH/$fileName",
            ),
            UpdateDownloadLink(
                labelRes = R.string.update_download_source_original,
                url = originalLink,
            ),
            UpdateDownloadLink(
                labelRes = R.string.update_download_source_ghproxy,
                url = "https://gh-proxy.org/https://github.com/$ASSETS_OWNER/$ASSETS_REPO/blob/$ASSETS_BRANCH/$fileName",
            ),
            UpdateDownloadLink(
                labelRes = R.string.update_download_source_fastgit,
                url = "https://fastgit.cc/https://github.com/$ASSETS_OWNER/$ASSETS_REPO/blob/$ASSETS_BRANCH/$fileName",
            ),
        )
    }
}

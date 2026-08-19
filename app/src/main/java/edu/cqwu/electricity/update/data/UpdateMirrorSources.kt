package edu.cqwu.electricity.update.data

data class UpdateDownloadLink(
    val label: String,
    val url: String,
)

internal object UpdateMirrorSources {
    private const val ASSETS_OWNER = "pphh2606"
    private const val ASSETS_REPO = "ElectricityQuery-assets"
    private const val ASSETS_BRANCH = "main"

    private fun githubRaw(fileName: String): String =
        "https://raw.githubusercontent.com/$ASSETS_OWNER/$ASSETS_REPO/$ASSETS_BRANCH/$fileName"

    private fun githubBlob(fileName: String): String =
        "https://github.com/$ASSETS_OWNER/$ASSETS_REPO/blob/$ASSETS_BRANCH/$fileName"

    private data class MirrorSource(
        val label: String,
        val url: (String) -> String,
    )

    private val sources = listOf(
        MirrorSource("GitHub Raw", ::githubRaw),
        MirrorSource("gh-proxy.org") { "https://gh-proxy.org/${githubBlob(it)}" },
        MirrorSource("fastgit.cc") { "https://fastgit.cc/${githubBlob(it)}" },
        MirrorSource("ghfast.top") { "https://ghfast.top/${githubRaw(it)}" },
        MirrorSource("gh.chjina.com") { "https://gh.chjina.com/${githubRaw(it)}" },
        MirrorSource("github.boki.moe") { "https://github.boki.moe/${githubRaw(it)}" },
    )

    fun metadataUrls(fileName: String): List<String> =
        sources.map { it.url(fileName) }

    fun downloadLinks(originalLink: String): List<UpdateDownloadLink> {
        val fileName = originalLink.substringAfterLast('/')
        if (fileName.isBlank()) return emptyList()
        return sources.map { source ->
            UpdateDownloadLink(
                label = source.label,
                url = source.url(fileName),
            )
        }
    }
}

package edu.cqwu.electricity.update.data

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
        val metadataUrl: (String) -> String,
        val downloadUrl: (String) -> String,
    )

    private val sources = listOf(
        MirrorSource("GitHub Raw", ::githubRaw, ::githubRaw),
        MirrorSource(
            label = "gh-proxy.org",
            metadataUrl = { "https://gh-proxy.org/${githubBlob(it)}" },
            downloadUrl = { "https://gh-proxy.org/${githubBlob(it)}" },
        ),
        MirrorSource(
            label = "fastgit.cc",
            metadataUrl = { "https://fastgit.cc/${githubBlob(it)}" },
            downloadUrl = { "https://fastgit.cc/${githubBlob(it)}" },
        ),
        MirrorSource(
            label = "ghfast.top",
            metadataUrl = { "https://ghfast.top/${githubRaw(it)}" },
            downloadUrl = { "https://ghfast.top/${githubRaw(it)}" },
        ),
        MirrorSource(
            label = "gh.chjina.com",
            metadataUrl = { "https://gh.chjina.com/${githubRaw(it)}" },
            downloadUrl = { "https://gh.chjina.com/${githubRaw(it)}" },
        ),
        MirrorSource(
            label = "github.boki.moe",
            metadataUrl = { "https://github.boki.moe/${githubRaw(it)}" },
            downloadUrl = { "https://github.boki.moe/${githubRaw(it)}" },
        ),
    )

    fun metadataUrls(fileName: String): List<String> =
        sources.map { it.metadataUrl(fileName) }

    fun downloadLinks(fileName: String): List<UpdateDownloadLink> =
        sources.map { source ->
            UpdateDownloadLink(
                label = source.label,
                url = source.downloadUrl(fileName),
            )
        }
}

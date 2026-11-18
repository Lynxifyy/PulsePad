package com.pulsepad.controller.domain.engine

import com.pulsepad.controller.domain.model.AppVersion
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The subset of the GitHub releases API PulsePad needs, and the logic that turns
 * it into an update decision.
 *
 * Kept free of Android types so the decision - "is there a newer release, and
 * which asset should we download" - is unit-testable against captured JSON.
 */
@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubAsset> = emptyList()
)

@Serializable
data class GitHubAsset(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    val size: Long = 0L,
    @SerialName("content_type") val contentType: String? = null
)

/** A release that PulsePad can actually install. */
data class AvailableUpdate(
    val version: AppVersion,
    val tagName: String,
    val releaseName: String,
    val releaseNotes: String,
    val releasePageUrl: String,
    val apkUrl: String,
    val apkSizeBytes: Long
)

object ReleaseParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    /**
     * GitHub serves a single object from `/releases/latest` and an array from
     * `/releases`, so both shapes are accepted here rather than at the call site.
     */
    fun parseReleases(payload: String): List<GitHubRelease> {
        val trimmed = payload.trim()
        if (trimmed.isEmpty()) return emptyList()
        return runCatching {
            if (trimmed.startsWith("[")) {
                json.decodeFromString<List<GitHubRelease>>(trimmed)
            } else {
                listOf(json.decodeFromString<GitHubRelease>(trimmed))
            }
        }.getOrElse { emptyList() }
    }

    /**
     * Picks the newest installable release that is actually newer than [current].
     *
     * Drafts are never offered. Prereleases are skipped unless [allowPrerelease],
     * because a user pressing Update expects the stable build. A release with no
     * `.apk` asset is not installable and is ignored rather than offered and then
     * failing at download time.
     */
    fun findUpdate(
        releases: List<GitHubRelease>,
        current: AppVersion,
        allowPrerelease: Boolean = false
    ): AvailableUpdate? = releases
        .asSequence()
        .filter { !it.draft }
        .filter { allowPrerelease || !it.prerelease }
        .mapNotNull { release -> toUpdate(release) }
        .filter { it.version.isNewerThan(current) }
        .maxByOrNull { it.version }

    /** An asset is installable when it is an APK; release zips and checksums are not. */
    fun isInstallableApk(asset: GitHubAsset): Boolean =
        asset.name.endsWith(".apk", ignoreCase = true) &&
            asset.downloadUrl.startsWith("https://")

    private fun toUpdate(release: GitHubRelease): AvailableUpdate? {
        val version = AppVersion.parse(release.tagName) ?: return null
        val apk = release.assets.firstOrNull { isInstallableApk(it) } ?: return null
        return AvailableUpdate(
            version = version,
            tagName = release.tagName,
            releaseName = release.name?.takeIf { it.isNotBlank() } ?: release.tagName,
            releaseNotes = release.body.orEmpty(),
            releasePageUrl = release.htmlUrl,
            apkUrl = apk.downloadUrl,
            apkSizeBytes = apk.size
        )
    }
}
